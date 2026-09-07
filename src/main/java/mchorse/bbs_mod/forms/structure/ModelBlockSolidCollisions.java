package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.FormShake;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Registers Model Blocks with structure/model {@code solidHitbox} and injects their
 * voxel shapes into entity movement collision.
 */
public final class ModelBlockSolidCollisions
{
    /** Hitboxes up to this height (e.g. 0.9) are climbable via boosted step; full 1-block ledges are not. */
    public static final float CLIMB_STEP_HEIGHT = 0.9F;

    private static final Set<ModelBlockEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Object ACTIVE_LOCK = new Object();

    private ModelBlockSolidCollisions()
    {}

    public static void updateRegistration(ModelBlockEntity entity)
    {
        if (entity == null || entity.isRemoved() || entity.getWorld() == null)
        {
            unregister(entity);

            return;
        }

        synchronized (ACTIVE_LOCK)
        {
            if (hasSolidFormHitbox(entity))
            {
                ACTIVE.add(entity);
            }
            else
            {
                ACTIVE.remove(entity);
            }
        }
    }

    public static void unregister(ModelBlockEntity entity)
    {
        if (entity != null)
        {
            synchronized (ACTIVE_LOCK)
            {
                ACTIVE.remove(entity);
            }
        }
    }

    private static List<ModelBlockEntity> snapshotActive()
    {
        synchronized (ACTIVE_LOCK)
        {
            if (ACTIVE.isEmpty())
            {
                return List.of();
            }

            return new ArrayList<>(ACTIVE);
        }
    }

    public static boolean hasSolidFormHitbox(ModelBlockEntity entity)
    {
        if (entity == null)
        {
            return false;
        }

        Form form = entity.getProperties().getForm();

        if (form instanceof StructureForm structure)
        {
            return structure.solidHitbox.get();
        }

        if (form instanceof ModelForm model)
        {
            return model.solidHitbox.get();
        }

        return false;
    }

    /** @deprecated use {@link #hasSolidFormHitbox(ModelBlockEntity)} */
    @Deprecated
    public static boolean hasSolidStructureHitbox(ModelBlockEntity entity)
    {
        return hasSolidFormHitbox(entity);
    }

    public static void appendShapes(Entity entity, Box swept, World world, List<VoxelShape> collisions)
    {
        if (entity == null || world == null || collisions == null)
        {
            return;
        }

        List<ModelBlockEntity> active = snapshotActive();

        if (active.isEmpty())
        {
            return;
        }

        Box query = swept.expand(0.25D);

        for (ModelBlockEntity model : active)
        {
            if (model.isRemoved() || model.getWorld() != world)
            {
                continue;
            }

            Form form = model.getProperties().getForm();

            if (form instanceof StructureForm structure && structure.solidHitbox.get())
            {
                appendStructureShapes(model, structure, query, collisions);
            }
            else if (form instanceof ModelForm modelForm && modelForm.solidHitbox.get())
            {
                appendModelShapes(model, modelForm, query, collisions);
            }
        }
    }

    private static void appendStructureShapes(ModelBlockEntity entity, StructureForm structure, Box query, List<VoxelShape> collisions)
    {
        StructureCollisionData data = StructureCollisionData.get(structure.structureFile.get());

        if (data == null || data.localBoxes.isEmpty())
        {
            return;
        }

        Matrix4f matrix = buildStructureWorldMatrix(entity, structure);
        Box worldBounds = transformBox(data.localBounds, matrix);

        if (!worldBounds.intersects(query))
        {
            return;
        }

        Matrix4f inverse = new Matrix4f(matrix).invert();

        /* Conservative local AABB of the world query — only nearby meshed boxes are transformed. */
        Box localQuery = transformBox(query, inverse).expand(0.05D);

        data.forEachOverlapping(localQuery, (local) ->
        {
            Box worldBox = transformBox(local, matrix);

            if (worldBox.intersects(query) && worldBox.getAverageSideLength() > 1.0E-4D)
            {
                collisions.add(VoxelShapes.cuboid(worldBox));
            }
        });
    }

    private static void appendModelShapes(ModelBlockEntity entity, ModelForm form, Box query, List<VoxelShape> collisions)
    {
        ModelCollisionData data = ModelCollisionData.get(form);

        if (data == null || !data.hasCollision())
        {
            return;
        }

        Matrix4f matrix = buildModelWorldMatrix(entity, form, data.modelScale);

        if (data.skinnedVertices != null)
        {
            Box worldBounds = transformBox(data.localBounds, matrix);

            if (!worldBounds.intersects(query))
            {
                return;
            }

            List<Box> worldSlabs = ModelCollisionData.buildWorldSpaceBobjSlabs(data.skinnedVertices, matrix);

            for (Box worldBox : worldSlabs)
            {
                if (worldBox.intersects(query) && worldBox.getAverageSideLength() > 1.0E-4D)
                {
                    collisions.add(VoxelShapes.cuboid(worldBox));
                }
            }

            return;
        }

        Box worldBounds = transformBox(data.localBounds, matrix);

        if (!worldBounds.intersects(query))
        {
            return;
        }

        appendBoxes(data.localBoxes, matrix, query, collisions);
    }

    private static void appendBoxes(List<Box> localBoxes, Matrix4f matrix, Box query, List<VoxelShape> collisions)
    {
        for (Box local : localBoxes)
        {
            Box worldBox = transformBox(local, matrix);

            if (worldBox.intersects(query) && worldBox.getAverageSideLength() > 1.0E-4D)
            {
                collisions.add(VoxelShapes.cuboid(worldBox));
            }
        }
    }

    private static Matrix4f buildStructureWorldMatrix(ModelBlockEntity entity, StructureForm structure)
    {
        float sx = Math.max(0.01F, structure.scaleX.get());
        float sy = Math.max(0.01F, structure.scaleY.get());
        float sz = Math.max(0.01F, structure.scaleZ.get());

        return buildBaseWorldMatrix(entity, structure).scale(sx, sy, sz);
    }

    private static Matrix4f buildModelWorldMatrix(ModelBlockEntity entity, ModelForm form, Vector3f modelScale)
    {
        float sx = Math.max(0.01F, modelScale.x);
        float sy = Math.max(0.01F, modelScale.y);
        float sz = Math.max(0.01F, modelScale.z);

        /* Same final Y-flip as ModelFormRenderer before drawing the mesh. */
        return buildBaseWorldMatrix(entity, form).scale(sx, sy, sz).rotateY(MathUtils.PI);
    }

    private static Matrix4f buildBaseWorldMatrix(ModelBlockEntity entity, Form form)
    {
        BlockPos pos = entity.getPos();
        ModelProperties properties = entity.getProperties();
        Transform modelTransform = properties.getTransform().copy();
        Transform formTransform = composeFormTransform(form, entity.getEntity().getAge());
        Matrix4f matrix = new Matrix4f()
            .translation(pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F);
        Matrix4f modelMat = new Matrix4f();
        Matrix4f formMat = new Matrix4f();

        modelTransform.setupMatrix(modelMat.identity());
        formTransform.setupMatrix(formMat.identity());
        matrix.mul(modelMat);
        matrix.mul(formMat);

        return matrix;
    }

    private static Transform composeFormTransform(Form form, float animTime)
    {
        Transform transform = new Transform();

        transform.copy(form.transform.get());
        applyOverlay(transform, form.transformOverlay.get());

        for (ValueTransform overlay : form.additionalTransforms)
        {
            applyOverlay(transform, overlay.get());
        }

        FormShake.apply(transform, form, animTime);

        return transform;
    }

    private static void applyOverlay(Transform transform, Transform overlay)
    {
        if (overlay == null)
        {
            return;
        }

        transform.translate.add(overlay.translate);
        transform.scale.add(overlay.scale).sub(1, 1, 1);
        transform.rotate.add(overlay.rotate);
        transform.rotate2.add(overlay.rotate2);
        transform.pivot.add(overlay.pivot);
    }

    private static Box transformBox(Box box, Matrix4f matrix)
    {
        Vector4f c000 = new Vector4f((float) box.minX, (float) box.minY, (float) box.minZ, 1F).mul(matrix);
        Vector4f c001 = new Vector4f((float) box.minX, (float) box.minY, (float) box.maxZ, 1F).mul(matrix);
        Vector4f c010 = new Vector4f((float) box.minX, (float) box.maxY, (float) box.minZ, 1F).mul(matrix);
        Vector4f c011 = new Vector4f((float) box.minX, (float) box.maxY, (float) box.maxZ, 1F).mul(matrix);
        Vector4f c100 = new Vector4f((float) box.maxX, (float) box.minY, (float) box.minZ, 1F).mul(matrix);
        Vector4f c101 = new Vector4f((float) box.maxX, (float) box.minY, (float) box.maxZ, 1F).mul(matrix);
        Vector4f c110 = new Vector4f((float) box.maxX, (float) box.maxY, (float) box.minZ, 1F).mul(matrix);
        Vector4f c111 = new Vector4f((float) box.maxX, (float) box.maxY, (float) box.maxZ, 1F).mul(matrix);
        double minX = min8(c000.x, c001.x, c010.x, c011.x, c100.x, c101.x, c110.x, c111.x);
        double minY = min8(c000.y, c001.y, c010.y, c011.y, c100.y, c101.y, c110.y, c111.y);
        double minZ = min8(c000.z, c001.z, c010.z, c011.z, c100.z, c101.z, c110.z, c111.z);
        double maxX = max8(c000.x, c001.x, c010.x, c011.x, c100.x, c101.x, c110.x, c111.x);
        double maxY = max8(c000.y, c001.y, c010.y, c011.y, c100.y, c101.y, c110.y, c111.y);
        double maxZ = max8(c000.z, c001.z, c010.z, c011.z, c100.z, c101.z, c110.z, c111.z);

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double min8(float a, float b, float c, float d, float e, float f, float g, float h)
    {
        return Math.min(a, Math.min(b, Math.min(c, Math.min(d, Math.min(e, Math.min(f, Math.min(g, h)))))));
    }

    private static double max8(float a, float b, float c, float d, float e, float f, float g, float h)
    {
        return Math.max(a, Math.max(b, Math.max(c, Math.max(d, Math.max(e, Math.max(f, Math.max(g, h)))))));
    }

    public static List<VoxelShape> wrapMutable(List<VoxelShape> collisions)
    {
        if (collisions instanceof ArrayList)
        {
            return collisions;
        }

        return new ArrayList<>(collisions);
    }

    public static Box sweptBox(Entity entity, Vec3d movement)
    {
        return entity.getBoundingBox().stretch(movement);
    }

    /**
     * Raise step height when standing against short solid model/structure hitboxes
     * (up to {@link #CLIMB_STEP_HEIGHT}) so the player auto-steps; 1-block-tall
     * ledges stay blocking.
     */
    public static float boostStepHeight(Entity entity, float stepHeight)
    {
        if (entity == null || entity.getWorld() == null)
        {
            return stepHeight;
        }

        if (stepHeight >= CLIMB_STEP_HEIGHT - 1.0E-3F)
        {
            return stepHeight;
        }

        List<ModelBlockEntity> active = snapshotActive();

        if (active.isEmpty())
        {
            return stepHeight;
        }

        Box feet = entity.getBoundingBox();
        Box probe = feet.expand(0.4D, 0D, 0.4D).stretch(0D, CLIMB_STEP_HEIGHT + 0.05D, 0D);
        World world = entity.getWorld();

        for (ModelBlockEntity model : active)
        {
            if (model.isRemoved() || model.getWorld() != world)
            {
                continue;
            }

            Form form = model.getProperties().getForm();

            if (form instanceof StructureForm structure && structure.solidHitbox.get())
            {
                if (hasClimbableBox(model, structure, null, feet, probe))
                {
                    return CLIMB_STEP_HEIGHT;
                }
            }
            else if (form instanceof ModelForm modelForm && modelForm.solidHitbox.get())
            {
                if (hasClimbableBox(model, null, modelForm, feet, probe))
                {
                    return CLIMB_STEP_HEIGHT;
                }
            }
        }

        return stepHeight;
    }

    private static boolean hasClimbableBox(ModelBlockEntity entity, StructureForm structure, ModelForm modelForm, Box feet, Box probe)
    {
        List<Box> localBoxes;
        Box localBounds;
        Matrix4f matrix;

        if (structure != null)
        {
            StructureCollisionData data = StructureCollisionData.get(structure.structureFile.get());

            if (data == null || data.localBoxes.isEmpty())
            {
                return false;
            }

            matrix = buildStructureWorldMatrix(entity, structure);
            Box worldBounds = transformBox(data.localBounds, matrix);

            if (!worldBounds.intersects(probe))
            {
                return false;
            }

            Matrix4f inverse = new Matrix4f(matrix).invert();
            Box localQuery = transformBox(probe, inverse).expand(0.05D);
            boolean[] found = new boolean[1];

            data.forEachOverlapping(localQuery, (local) ->
            {
                if (found[0])
                {
                    return;
                }

                if (isClimbableWorldBox(transformBox(local, matrix), feet, probe))
                {
                    found[0] = true;
                }
            });

            return found[0];
        }
        else if (modelForm != null)
        {
            ModelCollisionData data = ModelCollisionData.get(modelForm);

            if (data == null || !data.hasCollision())
            {
                return false;
            }

            matrix = buildModelWorldMatrix(entity, modelForm, data.modelScale);
            localBounds = data.localBounds;

            if (data.skinnedVertices != null)
            {
                Box worldBounds = transformBox(localBounds, matrix);

                if (!worldBounds.intersects(probe))
                {
                    return false;
                }

                for (Box worldBox : ModelCollisionData.buildWorldSpaceBobjSlabs(data.skinnedVertices, matrix))
                {
                    if (isClimbableWorldBox(worldBox, feet, probe))
                    {
                        return true;
                    }
                }

                return false;
            }

            localBoxes = data.localBoxes;
        }
        else
        {
            return false;
        }

        Box worldBounds = transformBox(localBounds, matrix);

        if (!worldBounds.intersects(probe))
        {
            return false;
        }

        return hasClimbableFromBoxes(localBoxes, matrix, feet, probe);
    }

    private static boolean hasClimbableFromBoxes(List<Box> localBoxes, Matrix4f matrix, Box feet, Box probe)
    {
        for (Box local : localBoxes)
        {
            if (isClimbableWorldBox(transformBox(local, matrix), feet, probe))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isClimbableWorldBox(Box worldBox, Box feet, Box probe)
    {
        /* Use ledge height above the feet, not full AABB height (rotation inflates AABB). */
        double ledge = worldBox.maxY - feet.minY;

        if (ledge <= 1.0E-3D || ledge > CLIMB_STEP_HEIGHT + 0.05D)
        {
            return false;
        }

        if (worldBox.maxY <= feet.minY + 1.0E-3D)
        {
            return false;
        }

        return worldBox.intersects(probe);
    }
}
