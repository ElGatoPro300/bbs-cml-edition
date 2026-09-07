package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.geo.GeoModelParser;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.util.math.Box;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model collision geometry. Raw models are cached; posed boxes are baked from the live
 * {@link ModelForm} pose (including caught/rotated limbs) on each query.
 */
public final class ModelCollisionData
{
    private static final int MAX_BOXES = 512;
    private static final int MAX_BOXES_BOBJ = 2048;
    /** Bump when collision bake algorithm changes so pose cache cannot serve stale boxes. */
    private static final int BAKE_VERSION = 11;
    /** World-space XZ cell size for BOBJ tops (built after the full model matrix). */
    public static final float BOBJ_GRID_CELL = 0.2F;
    /** Walkable slab thickness under the skinned world-space top. */
    public static final float BOBJ_SLAB_THICKNESS = 0.25F;
    /**
     * Pull cubic AABB tops down (rotation/subdivision inflate). Structures use exact voxels;
     * BOBJ tops are skinned world-space vertices and need almost no pull.
     */
    private static final double SURFACE_SNUG_CUBIC = 0.04D;
    public static final double SURFACE_SNUG_BOBJ = 0.01D;
    /** Sub-cell size for cubic cubes so rotated limbs keep a tight surface. */
    private static final float CUBE_CELL = 0.2F;
    private static final int CUBE_CELL_MAX = 10;
    private static final Map<String, CachedModel> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<ModelForm, PosedCache> POSE_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final MolangParser PARSER = new MolangParser();
    private static volatile LivePoseBaker liveBaker;

    public final List<Box> localBoxes;
    public final Box localBounds;
    public final Vector3f modelScale;
    /**
     * Skinned BOBJ vertices in model-local space (xyz interleaved). World-space XZ columns are
     * built after the full model matrix so horizontal / sideways rotations stay walkable.
     */
    public final float[] skinnedVertices;

    private ModelCollisionData(List<Box> localBoxes, Box localBounds, Vector3f modelScale)
    {
        this(localBoxes, localBounds, modelScale, null);
    }

    private ModelCollisionData(List<Box> localBoxes, Box localBounds, Vector3f modelScale, float[] skinnedVertices)
    {
        this.localBoxes = List.copyOf(localBoxes);
        this.localBounds = localBounds;
        this.modelScale = new Vector3f(modelScale);
        this.skinnedVertices = skinnedVertices == null || skinnedVertices.length < 3 ? null : skinnedVertices.clone();
    }

    public boolean hasCollision()
    {
        return (this.skinnedVertices != null && this.skinnedVertices.length >= 3) || !this.localBoxes.isEmpty();
    }

    /**
     * Optional client baker that uses the same posed {@link Model} instance as the renderer.
     */
    @FunctionalInterface
    public interface LivePoseBaker
    {
        ModelCollisionData bake(ModelForm form);
    }

    public static void setLivePoseBaker(LivePoseBaker baker)
    {
        liveBaker = baker;
    }

    /**
     * Bake collision boxes for the form's current pose / overlays (not a stale cached pose).
     * Caught / rotated limbs update {@link ModelForm#pose} and are reflected here.
     */
    public static ModelCollisionData get(ModelForm form)
    {
        if (form == null)
        {
            return null;
        }

        LivePoseBaker baker = liveBaker;

        if (baker != null)
        {
            ModelCollisionData live = baker.bake(form);

            if (live != null)
            {
                return live;
            }
        }

        return bakeStatic(form);
    }

    /**
     * Bake boxes from a model that already has {@code resetPose}/{@code applyPose} applied.
     */
    public static ModelCollisionData bakeFromPosedModel(Model model, Vector3f modelScale)
    {
        if (model == null)
        {
            return new ModelCollisionData(List.of(), new Box(0, 0, 0, 0, 0, 0), modelScale != null ? modelScale : new Vector3f(1F));
        }

        List<Box> boxes = new ArrayList<>();

        collectCubicBoxes(model, boxes);

        return finishBoxes(boxes, modelScale != null ? modelScale : new Vector3f(1F), SURFACE_SNUG_CUBIC);
    }

    /**
     * Bake collision from a posed BOBJ armature + rest mesh (same skinning as the VAO).
     * Stores skinned vertices so world-space tops can be built after any form rotation.
     */
    public static ModelCollisionData bakeFromSkinnedBobj(BOBJLoader.CompiledData mesh, BOBJArmature armature, Vector3f modelScale)
    {
        Vector3f scale = modelScale != null ? modelScale : new Vector3f(1F);

        if (mesh == null || armature == null || armature.matrices == null)
        {
            return new ModelCollisionData(List.of(), new Box(0, 0, 0, 0, 0, 0), scale);
        }

        float[] vertices = skinBobjVertices(mesh, armature);

        if (vertices == null || vertices.length < 3)
        {
            return new ModelCollisionData(List.of(), new Box(0, 0, 0, 0, 0, 0), scale);
        }

        Box localBounds = aabbFromPosData(vertices);

        if (localBounds == null)
        {
            localBounds = new Box(0, 0, 0, 0, 0, 0);
        }

        return new ModelCollisionData(List.of(), localBounds, scale, vertices);
    }

    private static ModelCollisionData bakeStatic(ModelForm form)
    {
        String modelId = form.model.get();

        if (modelId == null || modelId.isEmpty())
        {
            return null;
        }

        CachedModel cached = MODEL_CACHE.computeIfAbsent(modelId, ModelCollisionData::loadCachedModel);

        if (cached == null || cached.empty)
        {
            return new ModelCollisionData(List.of(), new Box(0, 0, 0, 0, 0, 0), new Vector3f(1F));
        }

        /* Match FormRenderer: state tracks temporarily merge into pose/transform. */
        form.applyStates(0F);

        try
        {
            Pose pose = composeStaticPose(form, cached.parts);
            int poseHash = poseHash(pose);
            int revision = form.getEditRevision();
            PosedCache posed = POSE_CACHE.get(form);

            if (posed != null && posed.modelId.equals(modelId) && posed.poseHash == poseHash && posed.revision == revision && posed.bakeVersion == BAKE_VERSION)
            {
                return posed.data;
            }

            List<Box> boxes = new ArrayList<>();

            if (cached.model != null)
            {
                synchronized (PARSER)
                {
                    cached.model.resetPose();
                    cached.model.applyPose(pose);
                    collectCubicBoxes(cached.model, boxes);
                }
            }
            else if (cached.bobjArmature != null && cached.bobjMesh != null)
            {
                synchronized (cached.bobjArmature)
                {
                    applyBobjPose(cached.bobjArmature, pose);
                    cached.bobjArmature.setupMatrices();

                    ModelCollisionData data = bakeFromSkinnedBobj(cached.bobjMesh, cached.bobjArmature, cached.modelScale);

                    POSE_CACHE.put(form, new PosedCache(modelId, poseHash, revision, BAKE_VERSION, data));

                    return data;
                }
            }
            else
            {
                boxes.addAll(cached.bobjBoxes);
            }

            ModelCollisionData data = finishBoxes(boxes, cached.modelScale, SURFACE_SNUG_CUBIC);

            POSE_CACHE.put(form, new PosedCache(modelId, poseHash, revision, BAKE_VERSION, data));

            return data;
        }
        finally
        {
            form.unapplyStates();
        }
    }

    private static ModelCollisionData finishBoxes(List<Box> boxes, Vector3f modelScale, double surfaceSnug)
    {
        if (boxes.isEmpty())
        {
            return new ModelCollisionData(List.of(), new Box(0, 0, 0, 0, 0, 0), modelScale);
        }

        List<Box> snug = new ArrayList<>(boxes.size());

        for (Box box : boxes)
        {
            Box fitted = snugTop(box, surfaceSnug);

            if (fitted != null)
            {
                snug.add(fitted);
            }
        }

        if (snug.isEmpty())
        {
            snug.addAll(boxes);
        }

        int maxBoxes = surfaceSnug == SURFACE_SNUG_BOBJ ? MAX_BOXES_BOBJ : MAX_BOXES;

        if (snug.size() > maxBoxes)
        {
            snug = thinBoxes(snug, maxBoxes);
        }

        return new ModelCollisionData(snug, unionBounds(snug), modelScale);
    }

    /**
     * Lower only the top of the box so the player rests on the visible surface instead of
     * floating on an inflated AABB. Sides/bottom stay put for solid contact.
     */
    private static Box snugTop(Box box, double surfaceSnug)
    {
        if (box == null)
        {
            return null;
        }

        double height = box.maxY - box.minY;

        if (height <= 1.0E-4D)
        {
            return null;
        }

        double pull = Math.min(surfaceSnug, height * 0.35D);

        return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY - pull, box.maxZ);
    }

    public static void invalidate(String modelId)
    {
        if (modelId != null && !modelId.isEmpty())
        {
            MODEL_CACHE.remove(modelId);
            POSE_CACHE.entrySet().removeIf((entry) -> entry.getValue().modelId.equals(modelId));
        }
    }

    public static void invalidateAll()
    {
        MODEL_CACHE.clear();
        POSE_CACHE.clear();
    }

    /**
     * Same merge order as {@code ModelFormRenderer.getPose()}: form pose, config parts, overlays.
     */
    private static Pose composeStaticPose(ModelForm form, Pose parts)
    {
        Pose pose = form.pose.get().copy();

        mergePose(pose, parts);
        mergePose(pose, form.poseOverlay.get());

        for (ValuePose overlay : form.additionalOverlays)
        {
            mergePose(pose, overlay.get());
        }

        return pose;
    }

    private static int poseHash(Pose pose)
    {
        int hash = 1;

        if (pose == null || pose.transforms.isEmpty())
        {
            return hash;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform t = entry.getValue();

            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + Float.floatToIntBits(t.fix);
            hash = 31 * hash + t.translate.hashCode();
            hash = 31 * hash + t.scale.hashCode();
            hash = 31 * hash + t.rotate.hashCode();
            hash = 31 * hash + t.rotate2.hashCode();
            hash = 31 * hash + t.pivot.hashCode();
        }

        return hash;
    }

    private static void mergePose(Pose target, Pose source)
    {
        if (source == null || source.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, PoseTransform> entry : source.transforms.entrySet())
        {
            PoseTransform poseTransform = target.get(entry.getKey());
            PoseTransform value = entry.getValue();

            if (value.fix != 0F)
            {
                poseTransform.fix = value.fix;
                poseTransform.translate.lerp(value.translate, value.fix);
                poseTransform.scale.lerp(value.scale, value.fix);
                poseTransform.rotate.lerp(value.rotate, value.fix);
                poseTransform.rotate2.lerp(value.rotate2, value.fix);
                poseTransform.pivot.lerp(value.pivot, value.fix);
            }
            else
            {
                poseTransform.translate.add(value.translate);
                poseTransform.scale.add(value.scale).sub(1, 1, 1);
                poseTransform.rotate.add(value.rotate);
                poseTransform.rotate2.add(value.rotate2);
                poseTransform.pivot.add(value.pivot);
            }
        }
    }

    private static CachedModel loadCachedModel(String modelId)
    {
        Vector3f scale = new Vector3f(1F);
        Pose parts = new Pose();
        Link modelLink = Link.assets("models/" + modelId);
        Collection<Link> links = BBSMod.getProvider().getLinksFromPath(modelLink, true);
        MapType config = loadConfig(modelLink);

        if (config != null)
        {
            if (config.has("scale"))
            {
                scale.set(DataStorageUtils.vector3fFromData(config.getList("scale"), new Vector3f(1F)));
            }

            if (config.has("parts") && BaseType.isMap(config.get("parts")))
            {
                parts.fromData(config.getMap("parts"));
            }
        }

        Model cubic = loadCubicModel(links);

        if (cubic != null)
        {
            /* Dedicated copy so collision baking never fights the shared loader graph. */
            return new CachedModel(cubic.copy(), null, null, List.of(), scale, parts, false);
        }

        BobjCollisionSource source = loadBobjSource(links);

        if (source == null)
        {
            return new CachedModel(null, null, null, List.of(), scale, parts, true);
        }

        List<Box> fallback = new ArrayList<>();

        if (source.mesh != null)
        {
            Box box = aabbFromPosData(source.mesh.posData);

            if (box != null)
            {
                fallback.add(box);
            }
        }

        if (source.armature == null && source.mesh == null && fallback.isEmpty())
        {
            return new CachedModel(null, null, null, List.of(), scale, parts, true);
        }

        return new CachedModel(null, source.armature, source.mesh, List.copyOf(fallback), scale, parts, false);
    }

    private static MapType loadConfig(Link modelLink)
    {
        try (InputStream asset = BBSMod.getProvider().getAsset(modelLink.combine("config.json")))
        {
            BaseType base = DataToString.fromString(IOUtils.readText(asset));

            if (BaseType.isMap(base))
            {
                return base.asMap();
            }
        }
        catch (Exception ignored)
        {}

        return null;
    }

    private static Model loadCubicModel(Collection<Link> links)
    {
        Link bbs = null;
        Link geo = null;

        for (Link link : links)
        {
            if (link == null || link.path == null)
            {
                continue;
            }

            if (link.path.endsWith(".bbs.json") && bbs == null)
            {
                bbs = link;
            }
            else if (link.path.endsWith(".geo.json") && geo == null)
            {
                geo = link;
            }
        }

        if (bbs != null)
        {
            try (InputStream stream = BBSMod.getProvider().getAsset(bbs))
            {
                synchronized (PARSER)
                {
                    CubicLoader.LoadingInfo info = new CubicLoader().load(PARSER, stream, bbs.path);

                    if (info.model != null && !info.model.topGroups.isEmpty())
                    {
                        return info.model;
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if (geo != null)
        {
            try (InputStream stream = BBSMod.getProvider().getAsset(geo))
            {
                JsonObject json = JsonParser.parseString(IOUtils.readText(stream)).getAsJsonObject();

                synchronized (PARSER)
                {
                    Model model = GeoModelParser.parse(json, PARSER);

                    if (model != null && !model.topGroups.isEmpty())
                    {
                        return model;
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return null;
    }

    private static BobjCollisionSource loadBobjSource(Collection<Link> links)
    {
        List<Link> bobjLinks = new ArrayList<>();

        for (Link link : links)
        {
            if (link != null && link.path != null && link.path.endsWith(".bobj"))
            {
                bobjLinks.add(link);
            }
        }

        if (bobjLinks.isEmpty())
        {
            return null;
        }

        /* Same preference as BOBJModelLoader — never bake collision from actions.bobj alone. */
        bobjLinks.sort((a, b) -> Integer.compare(bobjMeshPreference(b), bobjMeshPreference(a)));

        for (Link link : bobjLinks)
        {
            try (InputStream stream = BBSMod.getProvider().getAsset(link))
            {
                if (stream == null)
                {
                    continue;
                }

                BOBJLoader.BOBJData data = BOBJLoader.readData(stream);
                BOBJLoader.BOBJMesh skinned = findSkinnedBobjMesh(data);

                if (skinned == null)
                {
                    continue;
                }

                data.initiateArmatures();

                BOBJArmature armature = skinned.armature != null
                    ? skinned.armature
                    : (data.armatures.isEmpty() ? null : data.armatures.values().iterator().next());

                if (armature == null)
                {
                    continue;
                }

                armature.initArmature();
                armature.setupMatrices();

                BOBJLoader.CompiledData mesh = BOBJLoader.compileMesh(data, skinned);

                if (mesh == null || mesh.posData == null || mesh.posData.length < 3)
                {
                    continue;
                }

                return new BobjCollisionSource(armature, mesh);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return null;
    }

    private static int bobjMeshPreference(Link link)
    {
        String path = link.path.toLowerCase();
        int score = 0;

        if (path.endsWith("/model.bobj") || path.endsWith("model.bobj"))
        {
            score += 100;
        }

        if (path.contains("/default") || path.contains("/slim"))
        {
            score += 50;
        }

        if (path.contains("/animations/") || path.contains("/emotes/") || path.endsWith("actions.bobj"))
        {
            score -= 100;
        }

        return score;
    }

    private static BOBJLoader.BOBJMesh findSkinnedBobjMesh(BOBJLoader.BOBJData data)
    {
        if (data == null || data.armatures == null || data.armatures.isEmpty() || data.meshes == null)
        {
            return null;
        }

        BOBJArmature armature = data.armatures.values().iterator().next();

        for (BOBJLoader.BOBJMesh mesh : data.meshes)
        {
            if (mesh != null && mesh.armature == armature)
            {
                return mesh;
            }
        }

        return null;
    }

    private static void applyBobjPose(BOBJArmature armature, Pose pose)
    {
        for (BOBJBone bone : armature.orderedBones)
        {
            bone.reset();
        }

        if (pose == null || pose.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform transform = entry.getValue();
            BOBJBone bone = armature.bones.get(entry.getKey());

            if (bone == null)
            {
                continue;
            }

            if (transform.fix > 0F)
            {
                bone.transform.lerp(Transform.DEFAULT, transform.fix);
            }

            bone.transform.translate.add(transform.translate);
            bone.transform.scale.add(transform.scale).sub(1, 1, 1);
            bone.transform.rotate.add(transform.rotate);
            bone.transform.rotate2.add(transform.rotate2);
            bone.transform.pivot.add(transform.pivot);
        }
    }

    /**
     * Skin BOBJ vertices into model-local space (same math as BOBJModelVAO.updateMesh).
     */
    private static float[] skinBobjVertices(BOBJLoader.CompiledData mesh, BOBJArmature armature)
    {
        float[] posData = mesh.posData;
        float[] weightData = mesh.weightData;
        int[] boneIndexData = mesh.boneIndexData;
        Matrix4f[] matrices = armature != null ? armature.matrices : null;

        if (posData == null || posData.length < 3)
        {
            return null;
        }

        int vertexCount = posData.length / 3;
        float[] out = new float[vertexCount * 3];

        if (matrices == null || matrices.length == 0)
        {
            System.arraycopy(posData, 0, out, 0, out.length);

            return out;
        }

        int boneCount = matrices.length;
        Vector4f sum = new Vector4f();
        Vector4f result = new Vector4f();
        boolean hasWeights = weightData != null && boneIndexData != null
            && weightData.length >= vertexCount * 4 && boneIndexData.length >= vertexCount * 4;

        for (int i = 0; i < vertexCount; i++)
        {
            float ox = posData[i * 3];
            float oy = posData[i * 3 + 1];
            float oz = posData[i * 3 + 2];

            result.set(0F, 0F, 0F, 0F);

            if (hasWeights)
            {
                int influences = 0;

                for (int w = 0; w < 4; w++)
                {
                    float weight = weightData[i * 4 + w];

                    if (weight <= 0F)
                    {
                        continue;
                    }

                    int index = boneIndexData[i * 4 + w];

                    if (index < 0 || index >= boneCount || matrices[index] == null)
                    {
                        continue;
                    }

                    sum.set(ox, oy, oz, 1F);
                    matrices[index].transform(sum);
                    result.add(sum.mul(weight));
                    influences++;
                }

                if (influences == 0)
                {
                    result.set(ox, oy, oz, 1F);
                }
                else if (result.w != 0F && result.w != 1F)
                {
                    result.x /= result.w;
                    result.y /= result.w;
                    result.z /= result.w;
                }
            }
            else
            {
                result.set(ox, oy, oz, 1F);
            }

            out[i * 3] = result.x;
            out[i * 3 + 1] = result.y;
            out[i * 3 + 2] = result.z;
        }

        return out;
    }

    /**
     * Build world-space walkable slabs from model-local skinned vertices.
     * Projection uses world up so horizontal / sideways form rotations stay standable.
     */
    public static List<Box> buildWorldSpaceBobjSlabs(float[] localVertices, Matrix4f worldMatrix)
    {
        if (localVertices == null || localVertices.length < 3 || worldMatrix == null)
        {
            return List.of();
        }

        Map<Long, float[]> columns = new HashMap<>();
        float cell = BOBJ_GRID_CELL;
        float invCell = 1F / cell;
        Vector4f point = new Vector4f();
        int vertexCount = localVertices.length / 3;

        for (int i = 0; i < vertexCount; i++)
        {
            point.set(localVertices[i * 3], localVertices[i * 3 + 1], localVertices[i * 3 + 2], 1F);
            worldMatrix.transform(point);

            if (point.w != 0F && point.w != 1F)
            {
                point.x /= point.w;
                point.y /= point.w;
                point.z /= point.w;
            }

            int gx = (int) Math.floor(point.x * invCell);
            int gz = (int) Math.floor(point.z * invCell);

            stampBobjColumn(columns, gx, gz, point.y);
            stampBobjColumn(columns, gx + 1, gz, point.y);
            stampBobjColumn(columns, gx, gz + 1, point.y);
        }

        List<Box> boxes = new ArrayList<>(columns.size());

        for (Map.Entry<Long, float[]> entry : columns.entrySet())
        {
            long key = entry.getKey();
            int gx = (int) key;
            int gz = (int) (key >>> 32);
            float minX = gx * cell;
            float maxX = minX + cell;
            float minZ = gz * cell;
            float maxZ = minZ + cell;
            float top = entry.getValue()[1] - (float) SURFACE_SNUG_BOBJ;
            float bottom = top - BOBJ_SLAB_THICKNESS;

            boxes.add(new Box(minX, bottom, minZ, maxX, top, maxZ));
        }

        if (boxes.size() > MAX_BOXES_BOBJ)
        {
            boxes = thinBoxes(boxes, MAX_BOXES_BOBJ);
        }

        return boxes;
    }
    private static void stampBobjColumn(Map<Long, float[]> columns, int gx, int gz, float y)
    {
        long key = packColumnKey(gx, gz);
        float[] bounds = columns.get(key);

        if (bounds == null)
        {
            columns.put(key, new float[] {y, y});
        }
        else
        {
            bounds[0] = Math.min(bounds[0], y);
            bounds[1] = Math.max(bounds[1], y);
        }
    }

    private static long packColumnKey(int gx, int gz)
    {
        return ((long) gx & 0xFFFFFFFFL) | (((long) gz & 0xFFFFFFFFL) << 32);
    }

    private static Box aabbFromPosData(float[] posData)
    {
        if (posData == null || posData.length < 3)
        {
            return null;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i + 2 < posData.length; i += 3)
        {
            float x = posData[i];
            float y = posData[i + 1];
            float z = posData[i + 2];

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        if (!Float.isFinite(minX) || minX > maxX)
        {
            return null;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void collectCubicBoxes(Model model, List<Box> boxes)
    {
        Matrix4f matrix = new Matrix4f();

        for (ModelGroup root : model.topGroups)
        {
            walkGroup(root, matrix, boxes);
        }
    }

    private static void walkGroup(ModelGroup group, Matrix4f parent, List<Box> boxes)
    {
        if (group == null || !group.visible)
        {
            return;
        }

        Matrix4f local = new Matrix4f(parent);

        applyGroupTransform(local, group);

        for (ModelCube cube : group.cubes)
        {
            appendCubeBoxes(cube, local, boxes);
        }

        for (ModelMesh mesh : group.meshes)
        {
            Box box = meshBox(mesh, local);

            if (box != null)
            {
                boxes.add(box);
            }
        }

        for (ModelGroup child : group.children)
        {
            walkGroup(child, local, boxes);
        }
    }

    private static void applyGroupTransform(Matrix4f matrix, ModelGroup group)
    {
        Vector3f offset = group.offset;

        if (offset != null)
        {
            matrix.translate(offset.x, offset.y, offset.z);
        }

        Vector3f translate = group.current.translate;
        Vector3f pivot = group.current.pivot;

        matrix.translate(-(translate.x - pivot.x) / 16F, (translate.y - pivot.y) / 16F, (translate.z - pivot.z) / 16F);
        matrix.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

        if (group.orient != null)
        {
            matrix.rotate(group.orient);
        }
        else
        {
            if (group.current.rotate.z != 0F) matrix.rotateZ(MathUtils.toRad(group.current.rotate.z));
            if (group.current.rotate.y != 0F) matrix.rotateY(MathUtils.toRad(group.current.rotate.y));
            if (group.current.rotate.x != 0F) matrix.rotateX(MathUtils.toRad(group.current.rotate.x));
            if (group.current.rotate2.z != 0F) matrix.rotateZ(MathUtils.toRad(group.current.rotate2.z));
            if (group.current.rotate2.y != 0F) matrix.rotateY(MathUtils.toRad(group.current.rotate2.y));
            if (group.current.rotate2.x != 0F) matrix.rotateX(MathUtils.toRad(group.current.rotate2.x));
        }

        Vector3f scale = group.current.scale;

        matrix.scale(scale.x, scale.y, scale.z);
        matrix.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    /**
     * Emit subdivided AABBs for a cube so rotated limbs keep a surface close to the visible mesh
     * (a single transformed AABB floats/sinks differently per orientation).
     * Texture inflate is ignored — it only pads the draw and made players hover above the mesh.
     */
    private static void appendCubeBoxes(ModelCube cube, Matrix4f groupMatrix, List<Box> boxes)
    {
        float minX = cube.origin.x / 16F;
        float minY = cube.origin.y / 16F;
        float minZ = cube.origin.z / 16F;
        float maxX = (cube.origin.x + cube.size.x) / 16F;
        float maxY = (cube.origin.y + cube.size.y) / 16F;
        float maxZ = (cube.origin.z + cube.size.z) / 16F;

        if (minX >= maxX || minY >= maxY || minZ >= maxZ)
        {
            return;
        }

        Matrix4f matrix = new Matrix4f(groupMatrix);

        matrix.translate(cube.pivot.x / 16F, cube.pivot.y / 16F, cube.pivot.z / 16F);
        applyEulerZYX(matrix, cube.rotate);
        matrix.translate(-cube.pivot.x / 16F, -cube.pivot.y / 16F, -cube.pivot.z / 16F);

        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;
        int nx = cellCount(sizeX);
        int ny = cellCount(sizeY);
        int nz = cellCount(sizeZ);
        float stepX = sizeX / nx;
        float stepY = sizeY / ny;
        float stepZ = sizeZ / nz;

        for (int ix = 0; ix < nx; ix++)
        {
            float x0 = minX + ix * stepX;
            float x1 = ix == nx - 1 ? maxX : x0 + stepX;

            for (int iy = 0; iy < ny; iy++)
            {
                float y0 = minY + iy * stepY;
                float y1 = iy == ny - 1 ? maxY : y0 + stepY;

                for (int iz = 0; iz < nz; iz++)
                {
                    float z0 = minZ + iz * stepZ;
                    float z1 = iz == nz - 1 ? maxZ : z0 + stepZ;

                    boxes.add(transformAabb(x0, y0, z0, x1, y1, z1, matrix));
                }
            }
        }
    }

    private static int cellCount(float size)
    {
        if (size <= CUBE_CELL)
        {
            return 1;
        }

        return Math.min(CUBE_CELL_MAX, Math.max(1, (int) Math.ceil(size / CUBE_CELL)));
    }

    private static Box meshBox(ModelMesh mesh, Matrix4f groupMatrix)
    {
        if (mesh.baseData == null || mesh.baseData.vertices == null || mesh.baseData.vertices.isEmpty())
        {
            return null;
        }

        Matrix4f matrix = new Matrix4f(groupMatrix);

        matrix.translate(mesh.origin.x / 16F, mesh.origin.y / 16F, mesh.origin.z / 16F);
        applyEulerZYX(matrix, mesh.rotate);
        matrix.translate(-mesh.origin.x / 16F, -mesh.origin.y / 16F, -mesh.origin.z / 16F);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        Vector4f point = new Vector4f();

        for (Vector3f vertex : mesh.baseData.vertices)
        {
            point.set(vertex.x, vertex.y, vertex.z, 1F).mul(matrix);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }

        if (!Float.isFinite(minX) || minX >= maxX || minY >= maxY || minZ >= maxZ)
        {
            return null;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void applyEulerZYX(Matrix4f matrix, Vector3f rotation)
    {
        if (rotation == null)
        {
            return;
        }

        if (rotation.z != 0F) matrix.rotateZ(MathUtils.toRad(rotation.z));
        if (rotation.y != 0F) matrix.rotateY(MathUtils.toRad(rotation.y));
        if (rotation.x != 0F) matrix.rotateX(MathUtils.toRad(rotation.x));
    }

    private static Box transformAabb(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Matrix4f matrix)
    {
        Vector4f c000 = new Vector4f(minX, minY, minZ, 1F).mul(matrix);
        Vector4f c001 = new Vector4f(minX, minY, maxZ, 1F).mul(matrix);
        Vector4f c010 = new Vector4f(minX, maxY, minZ, 1F).mul(matrix);
        Vector4f c011 = new Vector4f(minX, maxY, maxZ, 1F).mul(matrix);
        Vector4f c100 = new Vector4f(maxX, minY, minZ, 1F).mul(matrix);
        Vector4f c101 = new Vector4f(maxX, minY, maxZ, 1F).mul(matrix);
        Vector4f c110 = new Vector4f(maxX, maxY, minZ, 1F).mul(matrix);
        Vector4f c111 = new Vector4f(maxX, maxY, maxZ, 1F).mul(matrix);
        float outMinX = min8(c000.x, c001.x, c010.x, c011.x, c100.x, c101.x, c110.x, c111.x);
        float outMinY = min8(c000.y, c001.y, c010.y, c011.y, c100.y, c101.y, c110.y, c111.y);
        float outMinZ = min8(c000.z, c001.z, c010.z, c011.z, c100.z, c101.z, c110.z, c111.z);
        float outMaxX = max8(c000.x, c001.x, c010.x, c011.x, c100.x, c101.x, c110.x, c111.x);
        float outMaxY = max8(c000.y, c001.y, c010.y, c011.y, c100.y, c101.y, c110.y, c111.y);
        float outMaxZ = max8(c000.z, c001.z, c010.z, c011.z, c100.z, c101.z, c110.z, c111.z);

        return new Box(outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);
    }

    private static float min8(float a, float b, float c, float d, float e, float f, float g, float h)
    {
        return Math.min(a, Math.min(b, Math.min(c, Math.min(d, Math.min(e, Math.min(f, Math.min(g, h)))))));
    }

    private static float max8(float a, float b, float c, float d, float e, float f, float g, float h)
    {
        return Math.max(a, Math.max(b, Math.max(c, Math.max(d, Math.max(e, Math.max(f, Math.max(g, h)))))));
    }

    private static Box unionBounds(List<Box> boxes)
    {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Box box : boxes)
        {
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX);
            maxY = Math.max(maxY, box.maxY);
            maxZ = Math.max(maxZ, box.maxZ);
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<Box> thinBoxes(List<Box> boxes, int max)
    {
        if (boxes.size() <= max)
        {
            return boxes;
        }

        List<Box> thinned = new ArrayList<>(max);
        double step = (double) boxes.size() / (double) max;

        for (int i = 0; i < max; i++)
        {
            thinned.add(boxes.get(Math.min(boxes.size() - 1, (int) Math.floor(i * step))));
        }

        return thinned;
    }

    private static final class CachedModel
    {
        private final Model model;
        private final BOBJArmature bobjArmature;
        private final BOBJLoader.CompiledData bobjMesh;
        private final List<Box> bobjBoxes;
        private final Vector3f modelScale;
        private final Pose parts;
        private final boolean empty;

        private CachedModel(Model model, BOBJArmature bobjArmature, BOBJLoader.CompiledData bobjMesh, List<Box> bobjBoxes, Vector3f modelScale, Pose parts, boolean empty)
        {
            this.model = model;
            this.bobjArmature = bobjArmature;
            this.bobjMesh = bobjMesh;
            this.bobjBoxes = bobjBoxes;
            this.modelScale = modelScale;
            this.parts = parts != null ? parts : new Pose();
            this.empty = empty;
        }
    }

    private static final class BobjCollisionSource
    {
        private final BOBJArmature armature;
        private final BOBJLoader.CompiledData mesh;

        private BobjCollisionSource(BOBJArmature armature, BOBJLoader.CompiledData mesh)
        {
            this.armature = armature;
            this.mesh = mesh;
        }
    }

    private static final class PosedCache
    {
        private final String modelId;
        private final int poseHash;
        private final int revision;
        private final int bakeVersion;
        private final ModelCollisionData data;

        private PosedCache(String modelId, int poseHash, int revision, int bakeVersion, ModelCollisionData data)
        {
            this.modelId = modelId;
            this.poseHash = poseHash;
            this.revision = revision;
            this.bakeVersion = bakeVersion;
            this.data = data;
        }
    }
}
