package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.ProceduralDefaults;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.physics.PhysBoneDefinition;
import mchorse.bbs_mod.cubic.render.CubicCpuGlowOverlayRenderer;
import mchorse.bbs_mod.cubic.render.CubicCpuGroupDrawRenderer;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.CubicLayerRenderer;
import mchorse.bbs_mod.cubic.render.CubicMatrixRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAOBuilderRenderer;
import mchorse.bbs_mod.cubic.render.CubicVAORenderer;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModelInstance implements IModelInstance
{
    public final String id;
    public IModel model;
    public Animations animations;
    public Link texture;
    public int color = Colors.WHITE;

    /* Model's additional properties */
    public String poseGroup;
    public boolean procedural;
    public boolean culling = true;
    public boolean onCpu;
    public String anchorGroup = "";

    public View view;

    public Vector3f scale = new Vector3f(1F);
    public float uiScale = 1F;
    public Pose sneakingPose = new Pose();
    public Pose ridingPose = new Pose();
    public Pose parts = new Pose();
    public List<PhysBoneDefinition> physBones = new ArrayList<>();

    public List<ArmorSlot> itemsMain = new ArrayList<>();
    public List<ArmorSlot> itemsOff = new ArrayList<>();
    public MapType limbConstraints;
    public MapType springChains;
    public MapType jointLimits;
    public Map<String, String> flippedParts = new HashMap<>();
    public Map<ArmorType, ArmorSlot> armorSlots = new HashMap<>();

    public ArmorSlot fpMain;
    public ArmorSlot fpOffhand;

    public ArmorSlot itemsMainTransform = new ArmorSlot("items_main_transform");
    public ArmorSlot itemsOffTransform = new ArmorSlot("items_off_transform");
    public ActionsConfig actions = new ActionsConfig();

    /** Owning form at render time; set by the form renderer each frame. */
    public transient Form form;

    /** World/model base transform from the last non-UI render pass (used by physics). */
    public transient Matrix4f lastBaseTransform;

    /**
     * Per-material default textures, loaded from the model's {@code textures/<material>/}
     * folders (or synthesized as a 1x1 swatch for flat-color materials). Keyed by material
     * name; the empty key is the model's default texture. Used as the static fallback for a
     * material when no animation track overrides it - see {@link #getMaterialTexture}.
     */
    public Map<String, Link> materialTextures = new HashMap<>();

    /** Ordered, distinct list of material names present on the model (for the editor and resolution). */
    public List<String> materials = new ArrayList<>();

    /** Per group, the geometry split into one VAO per material name (empty key = default texture). */
    private Map<ModelGroup, Map<String, ModelVAO>> vaos = new HashMap<>();
    private boolean ownsVaos = true;

    public ModelInstance(String id, IModel model, Animations animations, Link texture)
    {
        this.id = id;
        this.model = model;
        this.animations = animations;
        this.texture = texture;

        this.poseGroup = id;
    }

    @Override
    public IModel getModel()
    {
        return this.model;
    }

    @Override
    public Pose getSneakingPose()
    {
        return this.sneakingPose;
    }

    @Override
    public Pose getRidingPose()
    {
        return this.ridingPose;
    }

    @Override
    public Animations getAnimations()
    {
        return this.animations;
    }

    @Override
    public String getHeadBone()
    {
        return this.view == null ? "head" : this.view.headBone;
    }

    @Override
    public List<PhysBoneDefinition> getPhysBones()
    {
        return this.physBones;
    }

    public Map<ModelGroup, Map<String, ModelVAO>> getVaos()
    {
        return this.vaos;
    }

    /**
     * Resolve a material's static default texture: the per-material texture loaded
     * from {@code textures/<material>/} if present, otherwise the supplied fallback
     * (the form/model default texture). Animation tracks layer on top of this at
     * render time (handled by the caller), so this only covers the non-animated default.
     */
    public Link getMaterialTexture(String material, Link fallback)
    {
        Link link = this.materialTextures.get(material);

        return link != null ? link : fallback;
    }

    public String getAnchor()
    {
        String anchor = this.model.getAnchor();

        if (this.anchorGroup.isEmpty() && !anchor.isEmpty())
        {
            return anchor;
        }

        return this.anchorGroup;
    }

    public void applyConfig(MapType config)
    {
        if (config == null)
        {
            return;
        }

        this.procedural = config.getBool("procedural", this.procedural);
        this.culling = config.getBool("culling", this.culling);
        this.onCpu = config.getBool("on_cpu", this.onCpu);
        this.poseGroup = config.getString("pose_group", this.poseGroup);
        if (this.poseGroup == null || this.poseGroup.isEmpty())
        {
            this.poseGroup = this.id;
        }

        if (config.has("texture"))
        {
            this.texture = LinkUtils.create(config.get("texture"));
        }
        if (config.has("color")) this.color = config.getInt("color");
        if (config.has("items_main"))
        {
            this.itemsMain.clear();

            ListType list = config.get("items_main").asList();

            for (BaseType type : list)
            {
                ArmorSlot slot = new ArmorSlot(String.valueOf(this.itemsMain.size()));

                slot.fromData(type);
                this.itemsMain.add(slot);
            }
        }
        if (config.has("phys_bones", BaseType.TYPE_LIST))
        {
            this.physBones.clear();

            ListType list = config.get("phys_bones").asList();

            for (BaseType type : list)
            {
                if (!type.isMap())
                {
                    continue;
                }

                PhysBoneDefinition definition = new PhysBoneDefinition();

                definition.fromData(type.asMap());
                this.physBones.add(definition);
            }
        }
        if (config.has("items_off"))
        {
            this.itemsOff.clear();

            ListType list = config.get("items_off").asList();

            for (BaseType type : list)
            {
                ArmorSlot slot = new ArmorSlot(String.valueOf(this.itemsOff.size()));

                slot.fromData(type);
                this.itemsOff.add(slot);
            }
        }
        if (config.has("ui_scale")) this.uiScale = config.getFloat("ui_scale");
        if (config.has("scale")) this.scale = DataStorageUtils.vector3fFromData(config.getList("scale"), new Vector3f(1F));
        if (config.has("sneaking_pose", BaseType.TYPE_MAP))
        {
            this.sneakingPose = new Pose();
            this.sneakingPose.fromData(config.getMap("sneaking_pose"));
        }
        if (config.has("riding_pose", BaseType.TYPE_MAP))
        {
            this.ridingPose = new Pose();
            this.ridingPose.fromData(config.getMap("riding_pose"));
        }
        if (config.has("parts", BaseType.TYPE_MAP))
        {
            this.parts = new Pose();
            this.parts.fromData(config.getMap("parts"));
        }
        if (config.has("anchor")) this.anchorGroup = config.getString("anchor");
        if (config.has("flipped_parts"))
        {
            MapType map = config.getMap("flipped_parts");

            for (String key : map.keys())
            {
                String string = map.getString(key);

                if (!string.trim().isEmpty())
                {
                    this.flippedParts.put(key, string);
                }
            }
        }
        if (config.has("armor_slots"))
        {
            MapType map = config.getMap("armor_slots");

            for (String key : map.keys())
            {
                try
                {
                    ArmorType type = ArmorType.valueOf(key.toUpperCase());
                    ArmorSlot slot = new ArmorSlot(key);

                    slot.fromData(map.getMap(key));
                    this.armorSlots.put(type, slot);
                }
                catch (Exception e)
                {}
            }
        }
        if (config.has("fp_main"))
        {
            this.fpMain = new ArmorSlot("fp_main");
            this.fpMain.fromData(config.get("fp_main"));
        }
        if (config.has("fp_offhand"))
        {
            this.fpOffhand = new ArmorSlot("fp_offhand");
            this.fpOffhand.fromData(config.get("fp_offhand"));
        }
        if (config.has("items_main_transform"))
        {
            this.itemsMainTransform = new ArmorSlot("items_main_transform");
            this.itemsMainTransform.fromData(config.get("items_main_transform"));
        }
        if (config.has("items_off_transform"))
        {
            this.itemsOffTransform = new ArmorSlot("items_off_transform");
            this.itemsOffTransform.fromData(config.get("items_off_transform"));
        }

        if (config.has("animations", BaseType.TYPE_MAP))
        {
            this.actions = new ActionsConfig();
            this.actions.fromData(config.getMap("animations"));
        }
        else
        {
            this.actions = new ActionsConfig();
        }

        /* Optional look-at configuration */
        if (config.has("look_at", BaseType.TYPE_MAP))
        {
            this.view = new View();

            this.view.fromData(config.getMap("look_at"));
        }

        if (config.has("ik", BaseType.TYPE_MAP))
        {
            this.limbConstraints = (MapType) config.getMap("ik").copy();
        }
        else
        {
            this.limbConstraints = null;
        }

        if (config.has("springs", BaseType.TYPE_MAP))
        {
            this.springChains = (MapType) config.getMap("springs").copy();
        }
        else
        {
            this.springChains = null;
        }

        if (config.has("constraints", BaseType.TYPE_MAP))
        {
            this.jointLimits = (MapType) config.getMap("constraints").copy();
        }
        else
        {
            this.jointLimits = null;
        }

        if (this.procedural && this.model != null)
        {
            ProceduralDefaults.ensureRidingPose(this);
            ProceduralDefaults.ensureSneakingPose(this);
        }
    }

    public MapType toConfig()
    {
        MapType config = new MapType();

        if (this.procedural) config.putBool("procedural", true);
        if (!this.culling) config.putBool("culling", false);
        if (this.onCpu) config.putBool("on_cpu", true);
        if (!this.poseGroup.equals(this.id)) config.putString("pose_group", this.poseGroup);
        if (!this.anchorGroup.isEmpty()) config.putString("anchor", this.anchorGroup);

        if (this.texture != null) config.put("texture", LinkUtils.toData(this.texture));
        if (this.color != Colors.WHITE) config.putInt("color", this.color);

        if (!this.itemsMain.isEmpty())
        {
            ListType list = new ListType();

            for (ArmorSlot slot : this.itemsMain)
            {
                BaseType data = slot.toData();

                if (data != null)
                {
                    list.add(data);
                }
            }

            if (!list.isEmpty())
            {
                config.put("items_main", list);
            }
        }

        if (!this.itemsOff.isEmpty())
        {
            ListType list = new ListType();

            for (ArmorSlot slot : this.itemsOff)
            {
                BaseType data = slot.toData();

                if (data != null)
                {
                    list.add(data);
                }
            }

            if (!list.isEmpty())
            {
                config.put("items_off", list);
            }
        }

        if (this.uiScale != 1F) config.putFloat("ui_scale", this.uiScale);
        if (this.scale.x != 1F || this.scale.y != 1F || this.scale.z != 1F)
        {
            config.put("scale", DataStorageUtils.vector3fToData(this.scale));
        }

        if (this.sneakingPose != null && !this.sneakingPose.transforms.isEmpty())
        {
            config.put("sneaking_pose", this.sneakingPose.toData());
        }

        if (this.ridingPose != null && !this.ridingPose.transforms.isEmpty())
        {
            config.put("riding_pose", this.ridingPose.toData());
        }

        if (this.parts != null && !this.parts.transforms.isEmpty())
        {
            config.put("parts", this.parts.toData());
        }

        if (!this.flippedParts.isEmpty())
        {
            MapType map = new MapType();

            for (Map.Entry<String, String> entry : this.flippedParts.entrySet())
            {
                map.putString(entry.getKey(), entry.getValue());
            }

            config.put("flipped_parts", map);
        }

        if (!this.armorSlots.isEmpty())
        {
            MapType map = new MapType();

            for (Map.Entry<ArmorType, ArmorSlot> entry : this.armorSlots.entrySet())
            {
                map.put(entry.getKey().name().toLowerCase(), entry.getValue().toData());
            }

            config.put("armor_slots", map);
        }

        if (this.fpMain != null) config.put("fp_main", this.fpMain.toData());
        if (this.fpOffhand != null) config.put("fp_offhand", this.fpOffhand.toData());
        if (this.itemsMainTransform != null) config.put("items_main_transform", this.itemsMainTransform.toData());
        if (this.itemsOffTransform != null) config.put("items_off_transform", this.itemsOffTransform.toData());

        if (this.view != null)
        {
            MapType lookAt = new MapType();

            this.view.toData(lookAt);
            config.put("look_at", lookAt);
        }

        if (!this.physBones.isEmpty())
        {
            ListType list = new ListType();

            for (PhysBoneDefinition definition : this.physBones)
            {
                MapType map = new MapType();

                definition.toData(map);
                list.add(map);
            }

            config.put("phys_bones", list);
        }

        if (this.actions != null && !this.actions.geckoAnimations.isDefault())
        {
            config.put("animations", this.actions.toData());
        }

        if (this.limbConstraints != null)
        {
            config.put("ik", this.limbConstraints.copy());
        }

        if (this.springChains != null)
        {
            config.put("springs", this.springChains.copy());
        }

        if (this.jointLimits != null)
        {
            config.put("constraints", this.jointLimits.copy());
        }

        return config;
    }
    public ModelInstance copy()
    {
        ModelInstance copy = new ModelInstance(this.id, this.model.copy(), this.animations, this.texture);

        copy.poseGroup = this.poseGroup;
        copy.procedural = this.procedural;
        copy.culling = this.culling;
        copy.onCpu = this.onCpu;
        copy.anchorGroup = this.anchorGroup;
        if (this.view != null)
        {
            MapType lookAt = new MapType();

            this.view.toData(lookAt);
            copy.view = new View();
            copy.view.fromData(lookAt);
        }

        copy.scale.set(this.scale);
        copy.uiScale = this.uiScale;
        copy.sneakingPose = this.sneakingPose.copy();
        copy.ridingPose = this.ridingPose.copy();
        copy.parts = this.parts.copy();
        copy.color = this.color;

        for (ArmorSlot slot : this.itemsMain) copy.itemsMain.add(slot.copy());
        for (ArmorSlot slot : this.itemsOff) copy.itemsOff.add(slot.copy());
        for (PhysBoneDefinition definition : this.physBones) copy.physBones.add(definition.copy());
        if (this.limbConstraints != null) copy.limbConstraints = (MapType) this.limbConstraints.copy();
        if (this.springChains != null) copy.springChains = (MapType) this.springChains.copy();
        if (this.jointLimits != null) copy.jointLimits = (MapType) this.jointLimits.copy();
        copy.flippedParts.putAll(this.flippedParts);

        if (this.fpMain != null) copy.fpMain = this.fpMain.copy();
        if (this.fpOffhand != null) copy.fpOffhand = this.fpOffhand.copy();
        if (this.itemsMainTransform != null) copy.itemsMainTransform = this.itemsMainTransform.copy();
        if (this.itemsOffTransform != null) copy.itemsOffTransform = this.itemsOffTransform.copy();

        for (Map.Entry<ArmorType, ArmorSlot> entry : this.armorSlots.entrySet())
        {
            copy.armorSlots.put(entry.getKey(), entry.getValue().copy());
        }

        copy.actions.copy(this.actions);

        return copy;
    }

    public void setup()
    {
        if (this.model instanceof BOBJModel model)
        {
            if (RenderSystem.isOnRenderThread())
            {
                model.setup();
            }
            else
            {
                MinecraftClient.getInstance().execute(model::setup);
            }
        }

        /* VAOs should be only generated if there are no shape keys */
        if (!this.model.getShapeKeys().isEmpty())
        {
            return;
        }

        if (this.model instanceof Model model && !this.onCpu)
        {
            if (RenderSystem.isOnRenderThread())
            {
                CubicRenderer.processRenderModel(new CubicVAOBuilderRenderer(this.vaos), null, new MatrixStack(), model);
            }
            else
            {
                MinecraftClient.getInstance().execute(() ->
                {
                    CubicRenderer.processRenderModel(new CubicVAOBuilderRenderer(this.vaos), null, new MatrixStack(), model);
                });
            }
        }
    }

    public boolean isVAORendered()
    {
        return !this.vaos.isEmpty() || this.model instanceof BOBJModel;
    }

    public boolean hasShapeKeys()
    {
        return this.model != null && !this.model.getShapeKeys().isEmpty();
    }

    /**
     * VAO-backed cubic models and shape-key OBJ models use the BBS model shader for paint,
     * glow, and per-bone texture blend. Shape keys skip VAO baking but still draw on the CPU path.
     */
    public boolean supportsBbsModelShaderEffects()
    {
        return this.isVAORendered() || this.hasShapeKeys();
    }

    public void delete()
    {
        if (this.ownsVaos)
        {
            for (Map<String, ModelVAO> groupVaos : this.vaos.values())
            {
                for (ModelVAO value : groupVaos.values())
                {
                    value.delete();
                }
            }
        }

        this.vaos.clear();
        this.ownsVaos = true;
    }

    /**
     * Reuse GPU buffers already baked on another instance. Prefer the same {@link IModel}
     * graph; after {@link #copy()} (independent ModelGroups) remaps VAOs by bone id so
     * pose/anim state stays private while GPU meshes stay shared.
     */
    public void borrowVaosFrom(ModelInstance source)
    {
        if (source == null || source.vaos.isEmpty())
        {
            return;
        }

        if (this.ownsVaos)
        {
            for (Map<String, ModelVAO> groupVaos : this.vaos.values())
            {
                for (ModelVAO value : groupVaos.values())
                {
                    value.delete();
                }
            }

            this.vaos.clear();
        }

        if (source.model == this.model)
        {
            this.vaos = source.vaos;
            this.ownsVaos = false;

            return;
        }

        if (this.model instanceof Model localModel && source.model instanceof Model sourceModel)
        {
            Map<ModelGroup, Map<String, ModelVAO>> remapped = new HashMap<>();

            for (ModelGroup localGroup : localModel.getAllGroups())
            {
                ModelGroup sourceGroup = sourceModel.getGroup(localGroup.id);

                if (sourceGroup == null)
                {
                    continue;
                }

                Map<String, ModelVAO> vaos = source.vaos.get(sourceGroup);

                if (vaos != null)
                {
                    remapped.put(localGroup, vaos);
                }
            }

            if (!remapped.isEmpty())
            {
                this.vaos = remapped;
                this.ownsVaos = false;
            }
        }
    }

    /* Rendering */

    public void fillStencilMap(StencilMap stencilMap, ModelForm form)
    {
        if (this.model instanceof Model model)
        {
            for (ModelGroup group : model.getOrderedGroups())
            {
                stencilMap.addPicking(form, group.id);
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            if (!stencilMap.increment)
            {
                stencilMap.addPicking(form);

                return;
            }

            int baseIndex = stencilMap.objectIndex;

            for (BOBJBone orderedBone : model.getArmature().orderedBones)
            {
                stencilMap.addPicking(baseIndex + orderedBone.index, form, orderedBone.name);
            }
        }
    }

    public void captureMatrices(MatrixCache bones)
    {
        if (this.model instanceof Model model)
        {
            MatrixStack stack = new MatrixStack();
            CubicMatrixRenderer renderer = new CubicMatrixRenderer(model);

            CubicRenderer.processRenderModel(renderer, null, stack, model);

            for (ModelGroup group : model.getAllGroups())
            {
                Matrix4f matrix = new Matrix4f(renderer.matrices.get(group.index));
                Matrix4f origin = new Matrix4f(renderer.origins.get(group.index));

                matrix.translate(
                    group.initial.translate.x / 16,
                    group.initial.translate.y / 16,
                    group.initial.translate.z / 16
                );
                matrix.rotateY(MathUtils.PI);
                origin.translate(
                    group.initial.translate.x / 8192,
                    group.initial.translate.y / 8192,
                    group.initial.translate.z / 8192
                );
                origin.rotateY(MathUtils.PI);
                
                bones.put(group.id, matrix, origin);
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            model.getArmature().setupMatrices();

            for (BOBJBone orderedBone : model.getArmature().orderedBones)
            {
                Matrix4f matrix = new Matrix4f();
                Matrix4f origin = new Matrix4f();

                matrix.rotateY(MathUtils.PI).mul(orderedBone.mat);
                origin.rotateY(MathUtils.PI).mul(orderedBone.originMat);
                bones.put(orderedBone.name, matrix, origin);
            }
        }
    }

    public void render(MatrixStack stack, Supplier<ShaderProgram> program, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver)
    {
        if (this.model instanceof Model model)
        {
            Color c = new Color().set(this.color);
            float cr = color.r * c.r;
            float cg = color.g * c.g;
            float cb = color.b * c.b;
            float ca = color.a * c.a;

            CubicLayerRenderer renderer = new CubicLayerRenderer(light, overlay, keys, textureResolver, this.texture, this.culling);
            boolean effects = stencilMap != null || ModelVAORenderer.isPaintOverlayPass()
                || ModelVAORenderer.isColorTintOverlayPass() || ModelVAORenderer.isColorGradeOverlayPass() || !BBSRendering.isIrisShadersEnabled()
                || RenderSystem.outputColorTextureOverride != null;

            if (effects)
            {
                renderer.setEffects(stencilMap != null ? BBSShaders.getPickerModelsProgram() : BBSShaders.getModel(),
                    new Matrix4f(stack.peek().getPositionMatrix()).invert(), stencilMap);
            }

            renderer.setColor(cr, cg, cb, ca);
            CubicRenderer.processRenderModel(renderer, null, stack, model);

            if (stencilMap != null)
            {
                CubicRenderer.renderStencilPickPriority(renderer, null, stack, model, CubicRenderer.STENCIL_PICK_PRIORITY_BONES);
            }
        }
        else if (this.model instanceof BOBJModel model)
        {
            List<BOBJModelVAO> vaos = model.getVaos();

            if (!vaos.isEmpty())
            {
                stack.push();
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));

                model.getArmature().setupMatrices();

                /* One draw per mesh; bind that mesh's resolved texture (mesh name = material). */
                for (BOBJModelVAO vao : vaos)
                {
                    Link texture = textureResolver != null ? textureResolver.apply(vao.data.mesh.name) : null;
                    if (texture == null)
                    {
                        texture = this.texture;
                    }

                    if (stencilMap == null && !ModelVAORenderer.isPaintOverlayPass() && !ModelVAORenderer.isColorTintOverlayPass() && !ModelVAORenderer.isColorGradeOverlayPass()
                        && BBSRendering.isIrisShadersEnabled() && RenderSystem.outputColorTextureOverride == null)
                    {
                        vao.renderLayer(stack, color, light, overlay, texture, this.culling);
                    }
                    else
                    {
                        vao.renderLayer(stack, color, light, overlay, texture, this.culling,
                            stencilMap != null ? BBSShaders.getPickerModelsProgram() : BBSShaders.getModel(), stencilMap);
                    }
                }

                stack.pop();
            }
        }
    }

    public void renderShapeKeyGlowOverlay(MatrixStack stack, Color glowLayerColor, int overlay, StencilMap stencilMap, ShapeKeys keys, Link defaultTexture, boolean boneGlowOnly, float overlayIntensity, String targetGroupId, boolean skipBoneGlowGroups)
    {
        if (!(this.model instanceof Model model) || !this.hasShapeKeys())
        {
            return;
        }

        if (!boneGlowOnly && (glowLayerColor == null || glowLayerColor.a <= 0F))
        {
            return;
        }

        if (boneGlowOnly && glowLayerColor == null)
        {
            return;
        }

        ShaderProgram shader = BBSShaders.getModel();
        Link texture = defaultTexture != null ? defaultTexture : this.texture;
        boolean disableCull = true;

        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
        }

        if (disableCull)
        {
            BBSRendering.disableCull();
        }

        CubicCpuGlowOverlayRenderer renderProcessor = new CubicCpuGlowOverlayRenderer(
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
            overlay,
            stencilMap,
            keys,
            shader,
            texture,
            glowLayerColor,
            boneGlowOnly,
            overlayIntensity,
            targetGroupId,
            skipBoneGlowGroups
        );

        try
        {
            if (targetGroupId != null)
            {
                ModelGroup target = model.getGroup(targetGroupId);

                if (target != null)
                {
                    CubicRenderer.renderGroupBranch(renderProcessor, null, stack, model, target);
                }
            }
            else
            {
                CubicRenderer.processRenderModel(renderProcessor, null, stack, model);
            }
        }
        finally
        {
            if (disableCull && this.culling)
            {
                BBSRendering.enableCull();
            }
        }
    }
}
