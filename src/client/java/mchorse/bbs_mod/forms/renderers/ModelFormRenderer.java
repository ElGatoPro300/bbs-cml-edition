package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.ItemUseRenderState;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.cubic.constraints.JointLimitEnforcer;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.LimbConstraintProcessor;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsDebug;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.physics.DynamicBoneOrchestrator;
import mchorse.bbs_mod.cubic.render.ShapeKeyGlowPass;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.TextureBlend;
import mchorse.bbs_mod.forms.renderers.utils.BbsHeadItemSpace;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.framework.elements.utils.UILoader;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.iris.FormColorGradePatch;
import mchorse.bbs_mod.utils.iris.IrisArmorHooks;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModelFormRenderer extends FormRenderer<ModelForm> implements ITickable
{
    private static Matrix4f uiMatrix = new Matrix4f();
    private static final ThreadLocal<Float> UI_ANGLE_OVERRIDE = new ThreadLocal<>();
    private static Map<SkullBlock.SkullType, SkullBlockEntityModel> skullModels;

    private MatrixCache bones = new MatrixCache();

    private ActionsConfig lastConfigs;
    private IAnimator animator;
    private ModelInstance lastModel;
    /** Per-form live copy so pose/IK/physics do not mutate the shared ModelManager instance. */
    private ModelInstance cachedModel;
    private String cachedModelId;
    /** Global manager instance the cache was built from; replaced on model editor save/reload. */
    private ModelInstance cachedGlobalSource;
    private boolean ikAppliedThisRender;
    private boolean physicsAppliedThisRender;
    private boolean constraintsAppliedThisRender;

    private int lastAge = -1;
    private int lastUiAnimTick = Integer.MIN_VALUE;

    private IEntity entity = new StubEntity();

    /* Transient additive pose applied by the film "Look at" constraint */
    private Pose lookAtPose;

    @Override
    protected void applyTransforms(MatrixStack stack, boolean origin, float transition)
    {
        super.applyTransforms(stack, origin, transition);

        ModelInstance model = this.getModel();

        if (model != null)
        {
            stack.scale(model.scale.x, model.scale.y, model.scale.z);
        }
    }

    @Override
    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        super.applyTransforms(matrix, transition);

        ModelInstance model = this.getModel();

        if (model != null)
        {
            matrix.scale(model.scale.x, model.scale.y, model.scale.z);
        }
    }

    /**
     * When non-null, {@link #getUIMatrix} uses this yaw so FormUIPreviewCache
     * scratch-FBO fills match the intended orbit bucket instead of screen mouseX.
     */
    public static void setUIAngleOverride(Float angleRadians)
    {
        if (angleRadians == null)
        {
            UI_ANGLE_OVERRIDE.remove();
        }
        else
        {
            UI_ANGLE_OVERRIDE.set(angleRadians);
        }
    }

    public static Matrix4f getUIMatrix(UIContext context, int x1, int y1, int x2, int y2)
    {
        float scale = (y2 - y1) / 2.5F;
        int x = x1 + (x2 - x1) / 2;
        float y = y1 + (y2 - y1) * 0.85F;
        Float override = UI_ANGLE_OVERRIDE.get();
        float angle;

        if (override != null)
        {
            angle = override;
        }
        else
        {
            /* +PI aligns model north toward the UI camera (same as world render flip). */
            angle = MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI;

            if (BBSSettings.freezeModels.get())
            {
                angle = -MathUtils.PI + MathUtils.PI / 8F;
            }
        }

        uiMatrix.identity();
        uiMatrix.translate(x, y, 40);
        uiMatrix.scale(scale, -scale, scale);
        uiMatrix.rotateX(MathUtils.PI / 8);
        uiMatrix.rotateY(angle);

        return uiMatrix;
    }

    public static ModelInstance getModel(ModelForm form)
    {
        return BBSModClient.getModels().getModel(form.model.get());
    }

    public static boolean isBobjModel(ModelForm form)
    {
        ModelInstance instance = getModel(form);

        return instance != null && instance.model instanceof BOBJModel;
    }

    public static boolean isBobjModel(IModel model)
    {
        return model instanceof BOBJModel;
    }

    public ModelFormRenderer(ModelForm form)
    {
        super(form);
    }

    public IAnimator getAnimator()
    {
        return this.animator;
    }

    public void invalidateCachedModel()
    {
        if (this.cachedModel != null)
        {
            this.cachedModel.delete();
            this.cachedModel = null;
        }

        this.cachedModelId = null;
        this.cachedGlobalSource = null;
        this.lastModel = null;
        this.animator = null;
        this.lastConfigs = null;
    }

    public ModelInstance getModel()
    {
        String modelId = this.form.model.get();

        if (modelId == null || modelId.isEmpty())
        {
            this.invalidateCachedModel();

            return null;
        }

        ModelInstance global = BBSModClient.getModels().getModel(modelId);

        if (global == null)
        {
            this.invalidateCachedModel();

            return null;
        }

        /* ModelManager.loadModel() replaces the global instance (and deletes its VAOs).
         * Keep the cache only while that same instance is still current. */
        if (this.cachedModel != null && modelId.equals(this.cachedModelId) && global == this.cachedGlobalSource)
        {
            return this.cachedModel;
        }

        this.invalidateCachedModel();

        /* Deep-copy CPU/pose graph; borrow GPU VAOs from the manager instance. */
        this.cachedModel = global.copy();
        this.cachedModelId = modelId;
        this.cachedGlobalSource = global;

        if (global.model instanceof BOBJModel)
        {
            /* BOBJModel.copy() already builds its own armature VAO. */
        }
        else if (global.isVAORendered())
        {
            this.cachedModel.borrowVaosFrom(global);
        }
        else if (!global.hasShapeKeys())
        {
            this.cachedModel.setup();
        }

        return this.cachedModel;
    }

    public Pose getPose()
    {
        Pose pose = this.form.pose.get().copy();
        Pose overlay = this.form.poseOverlay.get();

        ModelInstance model = this.getModel();

        if (model != null)
        {
            this.applyPose(pose, model.parts);
        }

        this.applyPose(pose, overlay);

        for (ValuePose newPose : this.form.additionalOverlays)
        {
            this.applyPose(pose, newPose.get());
        }

        if (this.lookAtPose != null)
        {
            this.applyPose(pose, this.lookAtPose);
        }

        return pose;
    }

    /**
     * Sets a transient additive pose used by the film controller's "Look at"
     * constraint (per bone lock weights). It's set right before rendering an
     * entity and cleared right after, so it never gets serialized.
     */
    public void setLookAtPose(Pose pose)
    {
        this.lookAtPose = pose;
    }

    private void applyPose(Pose targetPose, Pose pose)
    {
        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform poseTransform = targetPose.get(entry.getKey());
            PoseTransform value = entry.getValue();

            if (value.fix != 0)
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

            if (value.fix != 0)
            {
                poseTransform.color.lerp(value.color, value.fix);
                poseTransform.paintColor.lerp(value.paintColor, value.fix);
                poseTransform.glowingColor.lerp(value.glowingColor, value.fix);
                poseTransform.glowIntensity = Lerps.lerp(poseTransform.glowIntensity, value.glowIntensity, value.fix);
                poseTransform.glowRadius = Lerps.lerp(poseTransform.glowRadius, value.glowRadius, value.fix);
                poseTransform.lighting = Lerps.lerp(poseTransform.lighting, value.lighting, value.fix);
                poseTransform.noshadingOpacity = value.fix >= 0.5F ? value.noshadingOpacity : poseTransform.noshadingOpacity;
            }
            else
            {
                poseTransform.color.mul(value.color);
                poseTransform.paintColor.lerp(value.paintColor, value.paintColor.a);
                poseTransform.glowingColor.lerp(value.glowingColor, Math.abs(value.glowIntensity));
                poseTransform.glowIntensity = Lerps.lerp(poseTransform.glowIntensity, value.glowIntensity, Math.abs(value.glowIntensity));
                poseTransform.glowRadius = Lerps.lerp(poseTransform.glowRadius, value.glowRadius, Math.abs(value.glowRadius) > 0F ? Math.abs(value.glowRadius) : 1F);
                poseTransform.lighting += value.lighting;
                poseTransform.noshadingOpacity = poseTransform.noshadingOpacity || value.noshadingOpacity;
            }

            if (value.texture != null)
            {
                poseTransform.texture = LinkUtils.copy(value.texture);
                poseTransform.textureBlend = value.textureBlend;
            }
        }
    }

    public void resetAnimator()
    {
        this.animator = null;
        this.lastModel = null;
    }

    private void applyPBRTextureIntensity()
    {
        BBSRendering.setPBRTextureIntensity(this.form.pbrNormalIntensity.get(), this.form.pbrSpecularIntensity.get());
    }

    private void clearPBRTextureIntensity()
    {
        BBSRendering.clearPBRTextureIntensity();
    }

    public void ensureAnimator(float transition)
    {
        ModelInstance model = this.getModel();
        ActionsConfig actionsConfig = this.resolveActionsConfig(model);

        if (model == null)
        {
            return;
        }

        if (this.lastModel == model && this.animator != null)
        {
            /* Update the config */
            if (!Objects.equals(actionsConfig, this.lastConfigs))
            {
                this.animator.setup(model, actionsConfig, true);

                this.lastConfigs = new ActionsConfig();
                this.lastConfigs.copy(actionsConfig);
            }

            return;
        }

        this.animator = model.procedural ? new ProceduralAnimator() : new Animator();
        this.animator.setup(model, actionsConfig, false);

        this.lastConfigs = new ActionsConfig();
        this.lastConfigs.copy(actionsConfig);
        this.lastModel = model;
    }

    private ActionsConfig resolveActionsConfig(ModelInstance model)
    {
        ActionsConfig output = new ActionsConfig();
        ActionsConfig formActions = this.form.actions.get();

        if (formActions != null)
        {
            output.copy(formActions);
        }

        if (model == null || model.actions == null)
        {
            return output;
        }

        if (output.geckoAnimations.isDefault() && !model.actions.geckoAnimations.isDefault())
        {
            output.geckoAnimations.copy(model.actions.geckoAnimations);

            if ((output.geckoAnimationsJavascript == null || output.geckoAnimationsJavascript.isBlank()) && model.actions.geckoAnimationsJavascript != null)
            {
                output.geckoAnimationsJavascript = model.actions.geckoAnimationsJavascript;
            }
        }

        return output;
    }

    @Override
    public List<String> getBones()
    {
        ModelInstance model = this.getModel();

        return model == null ? Collections.emptyList() : new ArrayList<>(model.model.getAllGroupKeys());
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        this.ensureAnimator(context.getTransition());

        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();

            stack.push();

            Matrix4f uiMatrix = getUIMatrix(context, x1, y1, x2, y2);

            this.applyTransforms(uiMatrix, context.getTransition());

            Link link = this.form.texture.get();
            Link texture = link == null ? model.texture : link;
            Color color = Color.white();

            if (this.shouldBakeFormColor(model))
            {
                color.mul(this.resolveBakeFormColor(model, true));
            }
            else
            {
                Color storedFormColor = this.form.color.get();

                if (storedFormColor != null)
                {
                    color.a *= storedFormColor.a;
                }
            }

            this.form.applyFormOpacity(color);

            float scale = this.form.uiScale.get() * model.uiScale;

            model.model.resetPose();

            /* Morph / form-list thumbnails stay on bind pose until the form is selected
             * (clicked); then idle plays. Mouse orbit is separate. */
            if (FormUtilsClient.isUIPreviewAnimate() && this.animator != null)
            {
                MinecraftClient client = MinecraftClient.getInstance();
                int tick = client.world != null ? (int) (client.world.getTime() & 0x7FFFFFFF) : this.lastUiAnimTick + 1;

                /* Advance animator once per game tick — apply every frame for smooth blend. */
                if (tick != this.lastUiAnimTick)
                {
                    this.lastUiAnimTick = tick;

                    /* Recent / applied forms often share this renderer with the world tick.
                     * Sync movement tracking so UI never inherits a fake "running" action. */
                    if (this.animator instanceof Animator keyframeAnimator)
                    {
                        keyframeAnimator.syncUIPreviewEntity(this.entity);
                    }

                    this.entity.update();
                    this.animator.update(this.entity);
                }

                this.animator.applyActions(null, model, context.getTransition());
            }

            model.model.applyPose(this.getPose());

            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.scale(scale, scale, scale);

            this.applyPBRTextureIntensity();
            BBSModClient.getTextures().bindTexture(texture);
            this.clearPBRTextureIntensity();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            Vector3f light0 = new Vector3f(0.85F, 0.85F, -1F).normalize();
            Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1F).normalize();
            RenderSystem.setupLevelDiffuseLighting(light0, light1);

            Supplier<ShaderProgram> mainShader = this.getModelShader(model);

            this.renderModel(this.entity, mainShader, stack, model, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, color, true, null, context.getTransition(), true, null, null);

            /* Render body parts */
            stack.push();
            MatrixStackUtils.invertUiNormalY(stack);

            this.renderBodyParts(new FormRenderingContext()
                .set(FormRenderType.ENTITY, this.entity, stack, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
                .inUI());

            stack.pop();
            stack.pop();

            DiffuseLighting.disableGuiDepthLighting();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            BBSRendering.restoreGuiRenderState();
        }
        else
        {
            String modelId = this.form.model.get();
            if (modelId != null && BBSModClient.getModels().isLoading(modelId))
            {
                float cx = x1 + (x2 - x1) / 2.0F;
                float cy = y1 + (y2 - y1) / 2.0F;
                UILoader.draw(context, cx, cy, 1.25F, null);
            }
        }
    }

    private void renderModel(IEntity target, Supplier<ShaderProgram> program, MatrixStack stack, ModelInstance model, int light, int overlay, Color color, boolean ui, StencilMap stencilMap, float transition, boolean renderEquipment, MatrixStack world, FormRenderingContext renderContext)
    {
        this.ikAppliedThisRender = false;
        this.physicsAppliedThisRender = false;
        this.constraintsAppliedThisRender = false;

        if (!model.culling)
        {
            RenderSystem.disableCull();
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        MatrixStack newStack = new MatrixStack();

        MatrixStackUtils.multiply(newStack, stack.peek().getPositionMatrix());
        newStack.peek().getNormalMatrix().set(stack.peek().getNormalMatrix());

        if (ui)
        {
            MatrixStackUtils.invertUiNormalY(newStack);
        }

        Matrix4f baseTransform = ui ? null : new Matrix4f((world != null ? world : stack).peek().getPositionMatrix());

        this.ikAppliedThisRender = false;
        this.physicsAppliedThisRender = false;
        this.constraintsAppliedThisRender = false;
        this.applyIKOnce(model, baseTransform);
        this.applyPhysicsOnce(target, model, transition, baseTransform);
        this.applyConstraintsOnce(model);

        /* Pass form-level texture so VAO renderer can respect it */
        Link link = this.form.texture.get();
        Link defaultTexture = link == null ? model.texture : link;

        if (renderContext != null && renderContext.textureOverride != null)
        {
            defaultTexture = renderContext.textureOverride;
        }

        TextureBlend textureBlend = this.form.textureBlend;

        if (renderContext != null && renderContext.textureBlendOverride != null)
        {
            textureBlend = renderContext.textureBlendOverride;
        }

        this.applyPBRTextureIntensity();

        if (stencilMap != null)
        {
            Color stencilFormColor = this.form.color.get().copyBakingColorGrade();
            boolean stencilColorTransformActive = this.canApplyColorTransformMask(model);

            try
            {
                if (stencilColorTransformActive)
                {
                    Vector3f colorMaskHalf = new Vector3f();

                    EffectTransformMath.resolveModelMaskHalfExtents(stencilFormColor.transform, colorMaskHalf);
                    ModelVAORenderer.setColorEffectTransform(new Matrix4f(), stencilFormColor.transform, colorMaskHalf);
                    ModelVAORenderer.setFormColorTint(stencilFormColor.r, stencilFormColor.g, stencilFormColor.b, stencilFormColor.a);
                }

                /* Picking must write depth so the closest limb along the cursor ray wins.
                 * Glow/paint passes can leave depthMask false; without this, later-drawn
                 * bones (often torso) overwrite nearer ones (head) in the pick FBO. */
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);

                ModelVAORenderer.clearPaint();
                ModelVAORenderer.clearGlowing();
                this.renderModelGeometry(newStack, program, model, light, overlay, stencilMap, color, defaultTexture, textureBlend);
            }
            finally
            {
                this.clearPBRTextureIntensity();
                ModelVAORenderer.clearColorEffectTransform();
                ModelVAORenderer.clearFormColorTint();
                ModelVAORenderer.clearPaint();
                ModelVAORenderer.clearGlowing();
            }

            gameRenderer.getLightmapTextureManager().disable();
            gameRenderer.getOverlayTexture().teardownOverlayColor();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();

            if (!model.culling)
            {
                RenderSystem.enableCull();
            }

            this.captureMatrices(model);

            if (!ui && BBSRendering.isRenderingWorld())
            {
                BBSRendering.restoreWorldRenderState();
            }

            return;
        }

        PaintSettings paint = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();
        Color paintColor = new Color();

        paint.resolveColor(legacyPaint, paintColor);

        float paintStrength = paint.resolveIntensity(legacyPaint);

        paintColor.a = paintStrength;
        GlowSettings glow = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        Color glowColor = new Color();

        glow.resolveColor(legacyGlow, glowColor);

        ModelVAORenderer.setGlow(glow, glowColor.r, glowColor.g, glowColor.b, legacyGlow);

        boolean shadowPass = (renderContext != null && renderContext.isShadowPass) || BBSRendering.isIrisShadowPass();
        /* Orbit UI, form/model-block pickable preview, and inventory GUI items: draw live.
         * World post-deferred / Iris queues are never flushed for those passes — soft limbs
         * and translucent forms would vanish (inventory slots draw after world flush). */
        boolean localPreview = ui || (renderContext != null && renderContext.isLocalPreview());
        boolean irisWorldPaintDeferral = BBSRendering.isIrisWorldPaintDeferral();
        boolean paintActive = this.hasAnyPaint(model);
        boolean bbsModelShader = this.usesBbsModelShader(model);
        Color storedFormColor = this.form.color.get();
        boolean hasBoneColorGrade = this.hasAnyBoneColorGrade(model);
        boolean hasColorAdjustments = (storedFormColor != null && storedFormColor.hasColorAdjustments()) || hasBoneColorGrade;
        /* Iris entity shaders have no ColorEffect uniforms — keep the live Iris lighting pass
         * and multiply FormColorTint via a BBS color-tint overlay (same idea as paint overlay). */
        boolean colorTransformWanted = this.canApplyColorTransformMask(model);
        boolean hasGlow = this.hasAnyGlow(model);
        boolean syncedGlow = hasGlow && glow.resolveSync();
        boolean paintOnlyGlow = glow.resolvePaintOnly();
        boolean hasEmissiveGlow = hasGlow && !paintOnlyGlow && FormColorEffects.hasPositiveGlow(glow, legacyGlow);
        /* Do not gate on supportsBbsModelShaderEffects — Iris entity_translucent discards
         * below alphaTestRef (~0.1); deferred BBS redraw is Iris-only (no-shader models keep
         * the normal BBS path so mesh depth / shading stay correct). */
        /* Positive glow stays on the Iris entity pass for pack emission/bloom.
         * Gates use form × softest-bone alpha so limb-only fades enter the same
         * deferred / post-deferred paths as form-wide opacity. Draw tint keeps
         * form alpha only — CubicVAORenderer still multiplies group.color.a once. */
        float formOpacityAlpha = color.a;
        float boneOpacityAlpha = this.getMinBoneOpacityAlpha(model);
        boolean hasInvisibleBones = this.hasFullyTransparentDrawableBones(model);
        /* Limb-only soft: keep opaque bones on the live path; soft bones are drawn sorted.
         * World + film (ENTITY): post-deferred queue so soft depth stamps land after
         * translucent terrain/clouds (immediate soft in AFTER_ENTITIES erased them).
         * UI / form / model-block edit preview: immediate sorted draws (queues never flush). */
        boolean hasPerBoneNoshading = !this.form.noshadingOpacity.get() && this.hasAnyBoneNoshadingOpacity(model);
        boolean limbOnlySoftCapable = !shadowPass
            && formOpacityAlpha >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA
            && (boneOpacityAlpha < ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA || hasPerBoneNoshading);
        boolean limbOnlySoftImmediate = limbOnlySoftCapable && localPreview;
        boolean limbOnlySoftDeferred = limbOnlySoftCapable && !localPreview;
        boolean limbOnlySoft = limbOnlySoftImmediate || limbOnlySoftDeferred;
        float opacityAlpha = limbOnlySoft ? formOpacityAlpha : formOpacityAlpha * boneOpacityAlpha;
        Map<ModelGroup, Boolean> limbVisibilitySave = null;

        /* Hide fully transparent bones always (alpha 0 must not depth-stamp). Soft bones are
         * hidden on the live pass and redrawn post-deferred below. */
        if (!shadowPass && (limbOnlySoft || hasInvisibleBones))
        {
            limbVisibilitySave = this.saveGroupVisibility(model);

            if (limbOnlySoft)
            {
                this.applyLimbSoftVisibility(model, false, true);
            }
            else
            {
                this.hideFullyTransparentBones(model);
            }
        }

        boolean lowAlphaDefer = !localPreview && !shadowPass && BBSRendering.needsIrisTranslucentModelDeferral(opacityAlpha);
        boolean noshadingOpacityDefer = !localPreview && !shadowPass
            && BBSRendering.needsIrisNoshadingOpacityDeferral(opacityAlpha,
                this.form.noshadingOpacity.get() || (!limbOnlySoft && this.hasAnyBoneNoshadingOpacity(model)));
        boolean opacityDefer = lowAlphaDefer || noshadingOpacityDefer;

        boolean deferTranslucentModel = opacityDefer;
        /* Soft Opacity + Noshading off stays on Iris post-deferred (pack body shadows).
         * Frame-end color-tint overlays use DST_COLOR and ignore form alpha (opaque mask).
         * Bake Blend into vertex RGB on that path instead; Noshading still uses the BBS queue. */
        boolean softOpacityIrisPath = !localPreview && !shadowPass
            && irisWorldPaintDeferral
            && opacityAlpha > 0.001F
            && opacityAlpha < ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA
            && !deferTranslucentModel;
        /* Iris live: ColorGradeOverlay keeps pack lighting. No-shader / UI / deferred BBS:
         * FormColorGrade in model.fsh after texture sample (same as Billboard). Never bake
         * form grade into vertex tint — contrast/hue on white (#fff, intensity 0) is a no-op. */
        boolean formHasGrade = storedFormColor != null && storedFormColor.hasColorAdjustments();
        boolean useColorGradeOverlay = hasColorAdjustments
            && !shadowPass
            && !deferTranslucentModel
            && irisWorldPaintDeferral
            && model.supportsBbsModelShaderEffects();
        /* useColorGradeOverlay already covers Iris live; any other VAO draw uses model.fsh. */
        boolean uploadFormGradeToShader = formHasGrade
            && !useColorGradeOverlay
            && !shadowPass
            && model.supportsBbsModelShaderEffects();
        boolean uploadGrade = (uploadFormGradeToShader || hasBoneColorGrade)
            && !useColorGradeOverlay
            && !shadowPass
            && model.supportsBbsModelShaderEffects();
        Color formColor = (uploadFormGradeToShader || useColorGradeOverlay)
            ? storedFormColor.copyDeferringColorGrade()
            : storedFormColor.copyBakingColorGrade();
        /* Multiply tint for color spatial mask only — Color Grade uses FormColorGrade / overlay. */
        boolean deferColorTintToOverlay = colorTransformWanted && irisWorldPaintDeferral && !deferTranslucentModel;
        boolean colorTransformActive = colorTransformWanted && (bbsModelShader || deferTranslucentModel || deferColorTintToOverlay);

        EffectTransform glowEffectTransform = this.resolveGlowEffectTransform(glow, legacyGlow);
        boolean hasGlowTransform = (glowEffectTransform != null && glowEffectTransform.isActive()) || this.hasAnyBoneGlowTransform(model);
        boolean glowHasSpatialMask = hasGlowTransform;

        /* Paint stays on the Iris frame-end overlay (keeps pack body shadows with Noshading off).
         * Do not redraw soft+paint with model.fsh on the Iris post-deferred path — wrong MVP
         * made actors fully invisible. Overlay outAlpha already multiplies form vertex alpha. */
        boolean deferPaintToOverlay = model.supportsBbsModelShaderEffects() && paintActive && irisWorldPaintDeferral && !deferTranslucentModel;
        boolean shaderOverlay = model.supportsBbsModelShaderEffects() && irisWorldPaintDeferral && (syncedGlow || glowHasSpatialMask) && !paintActive && !deferTranslucentModel;

        /* Low-alpha Iris redraw: albedo deferred; additive overlay if somehow deferred with glow. */
        boolean emitGlowAfterDeferred = deferTranslucentModel && model.supportsBbsModelShaderEffects() && hasEmissiveGlow;
        boolean deferGlowToOverlay = shaderOverlay || (irisWorldPaintDeferral && glowHasSpatialMask && model.supportsBbsModelShaderEffects());
        boolean shapeKeyPositiveOverlay = false;
        boolean glowDeferredToOverlay = deferGlowToOverlay || emitGlowAfterDeferred || (deferPaintToOverlay && hasGlow && !paintOnlyGlow);
        boolean stripMainPassGlow = deferGlowToOverlay || emitGlowAfterDeferred || (deferPaintToOverlay && hasGlow && paintOnlyGlow);
        GlowSettings mainPassGlow = this.resolveMainPassGlow(glow, legacyGlow, stripMainPassGlow, false);
        /* Opacity defer replaces the live Iris mesh. Color-grade overlay keeps Iris live. */
        boolean drawIrisLive = !deferTranslucentModel;

        if (!deferTranslucentModel && !localPreview && !shadowPass)
        {
            color.a = BBSRendering.easeIrisModelAlpha(formOpacityAlpha);
        }

        if (irisWorldPaintDeferral && hasEmissiveGlow && !deferTranslucentModel && !glowHasSpatialMask)
        {
            /* Must hit the Iris entity/gbuffer pass — post-composite BBS additive never blooms. */
            FormColorEffects.blendFormGlowBrighten(color, glow, legacyGlow);
        }
        else if (!bbsModelShader && !shaderOverlay && !deferPaintToOverlay && !paintOnlyGlow && !deferTranslucentModel && !glowHasSpatialMask)
        {
            FormColorEffects.blendFormGlowBrighten(color, glow, legacyGlow);
        }

        /* Position attributes are already in form/model space; camera/UI matrices must not
         * be baked into FormRootInverse or paint masks break in the orbit editor. */
        Matrix4f formRootInverse = new Matrix4f();
        Vector3f paintMaskHalf = new Vector3f();
        Vector3f colorMaskHalf = new Vector3f();
        Vector3f glowMaskHalf = new Vector3f();

        EffectTransformMath.resolveModelMaskHalfExtents(paint.transform, paintMaskHalf);
        EffectTransformMath.resolveModelMaskHalfExtents(formColor.transform, colorMaskHalf);
        EffectTransformMath.resolveModelMaskHalfExtents(glowEffectTransform, glowMaskHalf);

        EffectTransform paintTransformSnapshot = paint.transform.copy();
        Vector3f paintMaskHalfSnapshot = new Vector3f(paintMaskHalf);
        EffectTransform colorTransformSnapshot = formColor.transform.copy();
        Vector3f colorMaskHalfSnapshot = new Vector3f(colorMaskHalf);
        EffectTransform glowTransformSnapshot = glowEffectTransform.copy();
        Vector3f glowMaskHalfSnapshot = new Vector3f(glowMaskHalf);
        Color formColorSnapshot = formColor.copy();
        /* Deferred BBS redraws only — Iris live uploads via setFormColorGrade above. */
        boolean gradeActiveSnapshot = uploadFormGradeToShader;
        float gradeBrightnessSnapshot = storedFormColor.brightness;
        float gradeContrastSnapshot = storedFormColor.contrast;
        float gradeHueSnapshot = storedFormColor.hue;
        float gradeSaturationSnapshot = storedFormColor.saturation;
        EffectTransform gradeBrightnessTransformSnapshot = storedFormColor.brightnessTransform == null ? new EffectTransform() : storedFormColor.brightnessTransform.copy();
        EffectTransform gradeContrastTransformSnapshot = storedFormColor.contrastTransform == null ? new EffectTransform() : storedFormColor.contrastTransform.copy();
        EffectTransform gradeHueTransformSnapshot = storedFormColor.hueTransform == null ? new EffectTransform() : storedFormColor.hueTransform.copy();
        EffectTransform gradeSaturationTransformSnapshot = storedFormColor.saturationTransform == null ? new EffectTransform() : storedFormColor.saturationTransform.copy();

        boolean uploadMainPassEffectUniforms = this.usesMainPassModelEffectUniforms(model, deferTranslucentModel);

        if (paintActive && uploadMainPassEffectUniforms && !deferPaintToOverlay)
        {
            ModelVAORenderer.setPaintEffectTransform(formRootInverse, paint.transform, paintMaskHalf);
        }
        else if (!deferPaintToOverlay)
        {
            ModelVAORenderer.clearPaintEffectTransform();
        }

        if (hasGlow && uploadMainPassEffectUniforms && !deferGlowToOverlay)
        {
            ModelVAORenderer.setGlowEffectTransform(formRootInverse, glowEffectTransform, glowMaskHalf);
        }
        else if (!deferGlowToOverlay)
        {
            ModelVAORenderer.clearGlowEffectTransform();
        }

        /* Apply ColorEffect only on BBS model draws. Iris live uses a multiply overlay instead. */
        if (colorTransformWanted && uploadMainPassEffectUniforms && !deferColorTintToOverlay)
        {
            ModelVAORenderer.setColorEffectTransform(formRootInverse, formColor.transform, colorMaskHalf);
            ModelVAORenderer.setFormColorTint(formColor.r, formColor.g, formColor.b, formColor.a);
        }
        else if (!deferColorTintToOverlay)
        {
            ModelVAORenderer.clearColorEffectTransform();
            ModelVAORenderer.clearFormColorTint();
        }

        if (uploadGrade)
        {
            if (uploadFormGradeToShader)
            {
                ModelVAORenderer.setFormColorGrade(
                    storedFormColor.brightness,
                    storedFormColor.contrast,
                    storedFormColor.hue,
                    storedFormColor.saturation
                );
                ModelVAORenderer.setGradeEffectTransforms(storedFormColor);
            }
            else
            {
                /* Form grade via ColorGradeOverlay; keep base neutral so only bone grades apply. */
                ModelVAORenderer.setFormColorGrade(0F, 0F, 0F, 0F);
                ModelVAORenderer.setGradeEffectTransforms((Color) null);
            }
        }
        else
        {
            ModelVAORenderer.clearFormColorGrade();
        }

        try
        {
            TextureBlend textureBlendSnapshot = textureBlend == null ? null : new TextureBlend(textureBlend.from, textureBlend.to, textureBlend.blend);

            if (deferTranslucentModel)
            {
                /* Soft opacity: no depth stamp so water/lava/portals stay visible.
                 * Self X-ray on soft fades is preferable to punching fluids. */
                Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(newStack.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(newStack.peek().getNormalMatrix());
                Matrix4f baseTransformSnapshot = baseTransform == null ? null : new Matrix4f(baseTransform);
                Color colorSnapshot = color.copy();

                /* Keep form alpha for the redraw — bone alpha is applied per group.
                 * Only ease / black-handoff when the *form* itself is below Iris discard. */
                if (lowAlphaDefer && formOpacityAlpha < BBSRendering.TRANSLUCENT_ALPHA_DISCARD_REF)
                {
                    colorSnapshot.a = BBSRendering.easeDeferredModelAlpha(formOpacityAlpha);
                }
                else
                {
                    colorSnapshot.a = formOpacityAlpha;
                }

                /* Noshading opacity keeps user RGB (white). Auto low-alpha handoff may still
                 * remap when the toggle is off (form-driven ultra-low alpha only). */
                if (lowAlphaDefer && !noshadingOpacityDefer && formOpacityAlpha < BBSRendering.TRANSLUCENT_ALPHA_DISCARD_REF)
                {
                    BBSRendering.applyDeferredModelHandoffRgb(colorSnapshot);
                }

                Color paintSnapshot = paintColor.copy();
                Pose poseSnapshot = this.getPose().copy();
                float transitionSnapshot = transition;
                int overlayLight = light;
                int overlayOverlay = overlay;
                Link defaultTextureSnapshot = defaultTexture;
                TextureBlend textureBlendSnapshotFinal = textureBlendSnapshot;
                boolean paintActiveSnapshot = paintActive;
                boolean hasGlowSnapshot = hasGlow;
                boolean emitGlowSnapshot = emitGlowAfterDeferred;
                GlowSettings albedoGlow = emitGlowAfterDeferred ? mainPassGlow : glow;
                /* Soft opacity redraw (frame-end / noshading): write depth so limbs do not X-ray.
                 * Noshading soft forms share this queue with paint; flushPaintOverlayQueue sorts
                 * paint overlays before fullModel so depth write does not clip paint behind. */
                boolean deferredDepthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(opacityAlpha);

                if (colorSnapshot.a > 0.001F)
                {
                    ModelVAORenderer.submitDeferredTranslucentModel(() ->
                    {
                        this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);

                        try
                        {
                            if (colorTransformActive)
                            {
                                ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                                ModelVAORenderer.setFormColorTint(formColorSnapshot.r, formColorSnapshot.g, formColorSnapshot.b, formColorSnapshot.a);
                            }

                            if (gradeActiveSnapshot)
                            {
                                ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                                ModelVAORenderer.setGradeEffectTransforms(gradeBrightnessTransformSnapshot, gradeContrastTransformSnapshot, gradeHueTransformSnapshot, gradeSaturationTransformSnapshot);
                            }

                            if (paintActiveSnapshot)
                            {
                                ModelVAORenderer.setPaintEffectTransform(new Matrix4f().identity(), paintTransformSnapshot, paintMaskHalfSnapshot);
                                ModelVAORenderer.setPaint(paintSnapshot.r, paintSnapshot.g, paintSnapshot.b, paintSnapshot.a);
                            }
                            else
                            {
                                ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
                            }

                            if (hasGlowSnapshot)
                            {
                                ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot);
                                ModelVAORenderer.setGlow(albedoGlow, glowColor.r, glowColor.g, glowColor.b, legacyGlow);
                            }
                            else
                            {
                                ModelVAORenderer.clearGlowing();
                            }

                            /* Iris may leave a non-skin Sampler0 bound after composite; rebind before geometry. */
                            if (defaultTextureSnapshot != null)
                            {
                                BBSModClient.getTextures().bindTexture(defaultTextureSnapshot);
                            }

                            MatrixStack overlayStack = new MatrixStack();

                            overlayStack.peek().getPositionMatrix().set(positionMatrix);
                            overlayStack.peek().getNormalMatrix().set(normalMatrix);

                            this.renderSoftTransparencyGeometry(overlayStack, BBSShaders::getModel, model, overlayLight, overlayOverlay, colorSnapshot, defaultTextureSnapshot, textureBlendSnapshotFinal, albedoGlow, glowColor, legacyGlow, paintSnapshot, true, positionMatrix);
                        }
                        finally
                        {
                            ModelVAORenderer.clearColorEffectTransform();
                            ModelVAORenderer.clearFormColorTint();
                            ModelVAORenderer.clearFormColorGrade();
                            ModelVAORenderer.clearPaintEffectTransform();
                            ModelVAORenderer.clearGlowEffectTransform();
                            ModelVAORenderer.clearPaint();
                            ModelVAORenderer.clearGlowing();
                        }
                    }, deferredDepthWrite);

                    if (emitGlowSnapshot)
                    {
                        ModelVAORenderer.submitPaintOverlay(false, () ->
                        {
                            this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);

                            try
                            {
                                if (colorTransformActive)
                                {
                                    ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                                    ModelVAORenderer.setFormColorTint(formColorSnapshot.r, formColorSnapshot.g, formColorSnapshot.b, formColorSnapshot.a);
                                }

                                ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot);

                                MatrixStack overlayStack = new MatrixStack();

                                overlayStack.peek().getPositionMatrix().set(positionMatrix);
                                overlayStack.peek().getNormalMatrix().set(normalMatrix);

                                this.renderDeferredGlowEmission(overlayStack, model, overlayLight, overlayOverlay, null, colorSnapshot, defaultTextureSnapshot, textureBlendSnapshotFinal, glow, glowColor, legacyGlow);
                            }
                            finally
                            {
                                ModelVAORenderer.clearColorEffectTransform();
                                ModelVAORenderer.clearFormColorTint();
                                ModelVAORenderer.clearGlowEffectTransform();
                                ModelVAORenderer.clearPaint();
                                ModelVAORenderer.clearGlowing();
                            }
                        });
                    }
                }
            }
            else if (deferPaintToOverlay)
            {
                /* Iris base pass uses the vanilla entity shader; paint is applied only in the
                 * deferred BBS model overlay so the base pass cannot leak a root-origin pixel. */
                ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
            }
            else if (paintActive)
            {
                ModelVAORenderer.setPaint(paintColor.r, paintColor.g, paintColor.b, paintStrength);
            }
            else
            {
                ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
            }

            if (drawIrisLive)
            {
                /* Complementary VL: soft opacity waits until after translucent terrain
                 * (water/lava/portals). Near-opaque stays live with depth for pack shading.
                 * Skip in local preview — post-deferred flush is world-only. */
                if (!localPreview && ShaderOpacityPatch.shouldDelayUntilPostDeferred(opacityAlpha))
                {
                    /* Iris: entity-local matrices + restore camera ModelView.
                     * No-shader: camera-baked matrices + identity ModelView (BBS path). */
                    boolean irisCamera = BBSRendering.isIrisWorldModelPass() && !bbsModelShader;
                    Matrix4f positionMatrix = irisCamera
                        ? new Matrix4f(newStack.peek().getPositionMatrix())
                        : ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(newStack.peek().getPositionMatrix()));
                    Matrix3f normalMatrix = new Matrix3f(newStack.peek().getNormalMatrix());
                    Matrix4f baseTransformSnapshot = baseTransform == null ? null : new Matrix4f(baseTransform);
                    Color colorSnapshot = color.copy();
                    Color paintSnapshot = paintColor.copy();
                    Pose poseSnapshot = this.getPose().copy();
                    float transitionSnapshot = transition;
                    float paintStrengthSnapshot = paintStrength;
                    /* Iris soft mesh ignores PaintColor; only apply in-mesh when this draw is
                     * BBS (e.g. grade) and paint is not already a frame-end overlay. */
                    boolean paintInDeferredMeshSnapshot = paintActive && !deferPaintToOverlay;
                    boolean stripGlowSnapshot = stripMainPassGlow || shapeKeyPositiveOverlay;
                    boolean hasGlowSnapshot = hasGlow;
                    boolean glowDeferredSnapshot = glowDeferredToOverlay;
                    GlowSettings mainPassGlowSnapshot = mainPassGlow.copy();
                    GlowSettings glowSnapshot = glow.copy();
                    Color glowColorSnapshot = glowColor.copy();
                    Color legacyGlowSnapshot = legacyGlow.copy();
                    Link defaultTextureSnapshot = defaultTexture;
                    TextureBlend textureBlendSnapshotFinal = textureBlendSnapshot;
                    int overlayLight = light;
                    int overlayOverlay = overlay;
                    Supplier<ShaderProgram> programSnapshot = (irisCamera && !gradeActiveSnapshot)
                        ? program
                        : BBSShaders::getModel;
                    EffectTransform paintTransformQueued = paintTransformSnapshot;
                    Vector3f paintMaskHalfQueued = paintMaskHalfSnapshot;
                    double distanceSq = 0D;

                    if (renderContext != null && renderContext.entity != null)
                    {
                        double x = Lerps.lerp(renderContext.entity.getPrevX(), renderContext.entity.getX(), transition);
                        double y = Lerps.lerp(renderContext.entity.getPrevY(), renderContext.entity.getY(), transition);
                        double z = Lerps.lerp(renderContext.entity.getPrevZ(), renderContext.entity.getZ(), transition);
                        double dx = x - renderContext.camera.position.x;
                        double dy = y - renderContext.camera.position.y;
                        double dz = z - renderContext.camera.position.z;

                        distanceSq = dx * dx + dy * dy + dz * dz;
                    }

                    /* After fluids + depth write: water stays, limbs do not X-ray. */
                    boolean depthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(opacityAlpha);
                    boolean afterFluids = ShaderOpacityPatch.shouldFlushAfterFluids(opacityAlpha);
                    Runnable deferredDraw = () ->
                    {
                        /* Shared ModelInstance bones are overwritten by later draws — re-apply
                         * the captured pose like other deferred Iris paths. */
                        this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);

                        MatrixStack overlayStack = new MatrixStack();

                        overlayStack.peek().getPositionMatrix().set(positionMatrix);
                        overlayStack.peek().getNormalMatrix().set(normalMatrix);

                        try
                        {
                            if (colorTransformActive)
                            {
                                ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                                ModelVAORenderer.setFormColorTint(formColorSnapshot.r, formColorSnapshot.g, formColorSnapshot.b, formColorSnapshot.a);
                            }

                            /* Outer finally clears FormColorGrade before this post-deferred
                             * redraw — re-upload so no-shader / BBS film actors stay graded. */
                            if (gradeActiveSnapshot)
                            {
                                ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                                ModelVAORenderer.setGradeEffectTransforms(gradeBrightnessTransformSnapshot, gradeContrastTransformSnapshot, gradeHueTransformSnapshot, gradeSaturationTransformSnapshot);
                            }

                            if (paintInDeferredMeshSnapshot)
                            {
                                ModelVAORenderer.setPaintEffectTransform(new Matrix4f().identity(), paintTransformQueued, paintMaskHalfQueued);
                                ModelVAORenderer.setPaint(paintSnapshot.r, paintSnapshot.g, paintSnapshot.b, paintStrengthSnapshot);
                            }
                            else
                            {
                                ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
                            }

                            if (stripGlowSnapshot)
                            {
                                ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot);
                                ModelVAORenderer.setGlow(mainPassGlowSnapshot, glowColorSnapshot.r, glowColorSnapshot.g, glowColorSnapshot.b, legacyGlowSnapshot);
                            }
                            else if (hasGlowSnapshot)
                            {
                                ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot);
                                ModelVAORenderer.setGlow(glowSnapshot, glowColorSnapshot.r, glowColorSnapshot.g, glowColorSnapshot.b, legacyGlowSnapshot);
                            }
                            else
                            {
                                ModelVAORenderer.clearGlowing();
                            }

                            if (defaultTextureSnapshot != null)
                            {
                                BBSModClient.getTextures().bindTexture(defaultTextureSnapshot);
                            }

                            this.renderSoftTransparencyGeometry(overlayStack, programSnapshot, model, overlayLight, overlayOverlay, colorSnapshot, defaultTextureSnapshot, textureBlendSnapshotFinal, glowSnapshot, glowColorSnapshot, legacyGlowSnapshot, paintSnapshot, glowDeferredSnapshot, positionMatrix);
                        }
                        finally
                        {
                            ModelVAORenderer.clearColorEffectTransform();
                            ModelVAORenderer.clearFormColorTint();
                            ModelVAORenderer.clearFormColorGrade();
                            ModelVAORenderer.clearPaintEffectTransform();
                            ModelVAORenderer.clearGlowEffectTransform();
                            ModelVAORenderer.clearPaint();
                            ModelVAORenderer.clearGlowing();
                        }
                    };

                    if (irisCamera)
                    {
                        ShaderOpacityPatch.submitPostDeferredForm(0D, distanceSq, depthWrite, afterFluids, deferredDraw);
                    }
                    else
                    {
                        ShaderOpacityPatch.submitPostDeferredBbsForm(0D, distanceSq, depthWrite, afterFluids, deferredDraw);
                    }

                    ModelVAORenderer.clearPaintEffectTransform();
                    ModelVAORenderer.clearGlowEffectTransform();
                    ModelVAORenderer.clearPaint();
                    ModelVAORenderer.clearGlowing();
                }
                else
                {
                    if (stripMainPassGlow || shapeKeyPositiveOverlay)
                    {
                        ModelVAORenderer.setGlow(mainPassGlow, glowColor.r, glowColor.g, glowColor.b, legacyGlow);
                    }
                    else if (hasGlow)
                    {
                        ModelVAORenderer.setGlow(glow, glowColor.r, glowColor.g, glowColor.b, legacyGlow);
                    }

                    /* Live near-opaque only. Translucents were queued post-deferred above. */
                    boolean forceDepth = ShaderOpacityPatch.shouldForceLiveDepthWrite(opacityAlpha);
                    boolean suppressDepth = ShaderOpacityPatch.shouldSuppressDepthWrite(opacityAlpha);
                    boolean savedDepthMask = false;

                    if (forceDepth || suppressDepth)
                    {
                        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                        RenderSystem.enableDepthTest();

                        if (forceDepth)
                        {
                            ShaderOpacityPatch.setForceLiveDepthWrite(true);
                            RenderSystem.depthMask(true);
                        }
                        else
                        {
                            ShaderOpacityPatch.setSuppressLiveDepthWrite(true);
                            RenderSystem.depthMask(false);
                        }
                    }

                    try
                    {
                        /* Color Grade is applied in model.fsh — never draw graded models with
                         * vanilla entity_translucent (no FormColorGrade uniforms). */
                        Supplier<ShaderProgram> geometryProgram = uploadFormGradeToShader
                            ? BBSShaders::getModel
                            : program;

                        this.renderModelGeometryWithEmission(newStack, geometryProgram, model, light, overlay, stencilMap, color, defaultTexture, textureBlend, glow, glowColor, legacyGlow, paintColor, glowDeferredToOverlay);
                    }
                    finally
                    {
                        if (forceDepth)
                        {
                            ShaderOpacityPatch.setForceLiveDepthWrite(false);
                            RenderSystem.depthMask(savedDepthMask);
                        }
                        else if (suppressDepth)
                        {
                            ShaderOpacityPatch.setSuppressLiveDepthWrite(false);
                            RenderSystem.depthMask(savedDepthMask);
                        }
                    }
                }
            }

            if (limbOnlySoft)
            {
                /* Soft bones only — one draw per soft bone, farther first. World: post-deferred
                 * queue. UI/preview: run immediately (those passes never flush soft queues). */
                List<ModelGroup> softBones = this.collectSoftDrawableBones(model);

                if (!softBones.isEmpty())
                {
                    float softGateAlpha = formOpacityAlpha * boneOpacityAlpha;
                    /* Capture both Iris (entity-local) and BBS (MV-baked) roots — mixed soft
                     * limbs may split: noshading bones → BBS queue, others → Iris post-deferred. */
                    Matrix4f softStackLocal = new Matrix4f(newStack.peek().getPositionMatrix());
                    Matrix4f softStackBbs = limbOnlySoftImmediate
                        ? softStackLocal
                        : ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(newStack.peek().getPositionMatrix()));
                    Matrix3f softNormalMatrix = new Matrix3f(newStack.peek().getNormalMatrix());
                    boolean formNoshading = this.form.noshadingOpacity.get();
                    boolean canIrisSoftPath = !limbOnlySoftImmediate
                        && BBSRendering.isIrisWorldModelPass()
                        && !bbsModelShader;
                    /* Model blocks / preview: lengthSq on the entity draw stack.
                     * Film ENTITY: look-axis depth in renderContext.world (absolute). */
                    Matrix4f softSortMatrix = softStackLocal;
                    boolean filmWorldSoftSort = !limbOnlySoftImmediate
                        && renderContext != null
                        && renderContext.type == FormRenderType.ENTITY
                        && renderContext.world != null;
                    Matrix4f filmWorldSortMatrix = filmWorldSoftSort
                        ? new Matrix4f(renderContext.world.peek().getPositionMatrix())
                        : null;
                    double cameraX = 0D;
                    double cameraY = 0D;
                    double cameraZ = 0D;
                    Vector3f cameraLook = null;
                    Matrix4f softBaseTransformSnapshot = baseTransform == null ? null : new Matrix4f(baseTransform);
                    Color softColorSnapshot = color.copy();
                    Color softPaintSnapshot = paintColor.copy();
                    Pose softPoseSnapshot = this.getPose().copy();
                    float softTransitionSnapshot = transition;
                    float softPaintStrengthSnapshot = paintStrength;
                    boolean softPaintInMesh = paintActive && !deferPaintToOverlay;
                    boolean softStripGlow = stripMainPassGlow || shapeKeyPositiveOverlay;
                    boolean softHasGlow = hasGlow;
                    boolean softGlowDeferred = glowDeferredToOverlay;
                    GlowSettings softMainPassGlow = mainPassGlow.copy();
                    GlowSettings softGlow = glow.copy();
                    Color softGlowColor = glowColor.copy();
                    Color softLegacyGlow = legacyGlow.copy();
                    Link softDefaultTexture = defaultTexture;
                    TextureBlend softTextureBlend = textureBlendSnapshot;
                    int softLight = light;
                    int softOverlay = overlay;
                    boolean softColorTransformActive = colorTransformActive;
                    EffectTransform softColorEffectTransform = colorTransformSnapshot;
                    Vector3f softColorMaskHalf = new Vector3f(colorMaskHalfSnapshot);
                    Color softFormColor = formColorSnapshot.copy();
                    boolean softGradeActive = gradeActiveSnapshot;
                    float softGradeBrightness = gradeBrightnessSnapshot;
                    float softGradeContrast = gradeContrastSnapshot;
                    float softGradeHue = gradeHueSnapshot;
                    float softGradeSaturation = gradeSaturationSnapshot;
                    EffectTransform softGradeBrightnessTransform = gradeBrightnessTransformSnapshot;
                    EffectTransform softGradeContrastTransform = gradeContrastTransformSnapshot;
                    EffectTransform softGradeHueTransform = gradeHueTransformSnapshot;
                    EffectTransform softGradeSaturationTransform = gradeSaturationTransformSnapshot;
                    EffectTransform softPaintTransform = paintTransformSnapshot;
                    Vector3f softPaintMaskHalf = new Vector3f(paintMaskHalfSnapshot);
                    EffectTransform softGlowTransform = glowTransformSnapshot;
                    Vector3f softGlowMaskHalf = new Vector3f(glowMaskHalfSnapshot);
                    /* Soft limbs need a depth stamp for Iris fog/paint (noshading off). Multi
                     * soft + depth-write in the color pass erases the far limb when film sort
                     * is imperfect — batches color with depth-write off, then depth-only stamp. */
                    boolean softDepthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(softGateAlpha);
                    boolean softAfterFluids = ShaderOpacityPatch.shouldFlushAfterFluids(softGateAlpha);
                    Supplier<ShaderProgram> softIrisProgram = (!softGradeActive) ? program : BBSShaders::getModel;
                    Supplier<ShaderProgram> softBbsProgram = BBSShaders::getModel;
                    double entityDistanceSq = 0D;

                    softColorSnapshot.a = formOpacityAlpha;

                    if (renderContext != null)
                    {
                        cameraX = renderContext.camera.position.x;
                        cameraY = renderContext.camera.position.y;
                        cameraZ = renderContext.camera.position.z;
                        /* Match Minecraft view forward (0,0,-1) through the camera rotation
                         * matrix — more reliable than getLookDirection() pitch/yaw conventions. */
                        cameraLook = new Vector3f(0F, 0F, -1F);
                        renderContext.camera.view.transformDirection(cameraLook);

                        if (renderContext.entity != null)
                        {
                            double x = Lerps.lerp(renderContext.entity.getPrevX(), renderContext.entity.getX(), transition);
                            double y = Lerps.lerp(renderContext.entity.getPrevY(), renderContext.entity.getY(), transition);
                            double z = Lerps.lerp(renderContext.entity.getPrevZ(), renderContext.entity.getZ(), transition);
                            double dx = x - cameraX;
                            double dy = y - cameraY;
                            double dz = z - cameraZ;

                            entityDistanceSq = dx * dx + dy * dy + dz * dz;
                        }
                    }

                    /* Pose is already applied from the live opaque pass. Soft bones are hidden
                     * for the live draw, so briefly show them for matrix capture (CubicRenderer
                     * skips matrix write when visible=false); origins alone can still work. */
                    Map<ModelGroup, Boolean> softMatrixVisibility = this.saveGroupVisibility(model);

                    try
                    {
                        for (ModelGroup softBone : softBones)
                        {
                            softBone.visible = true;
                        }

                        this.captureMatrices(model);
                    }
                    finally
                    {
                        this.restoreGroupVisibility(softMatrixVisibility);
                    }

                    List<SoftBoneSubmit> softSubmits = new ArrayList<>(softBones.size());

                    for (ModelGroup softBone : softBones)
                    {
                        double softDistanceSq = filmWorldSortMatrix != null
                            ? this.softBoneWorldDepthKey(softBone.id, filmWorldSortMatrix, cameraX, cameraY, cameraZ, cameraLook, entityDistanceSq)
                            : this.softBoneDistanceSq(softBone.id, softSortMatrix, entityDistanceSq);
                        boolean boneNoshading = formNoshading || softBone.noshadingOpacity;
                        float boneGateAlpha = formOpacityAlpha * (softBone.color == null ? 1F : softBone.color.a);
                        boolean boneNoshadingQueue = canIrisSoftPath
                            && BBSRendering.needsIrisNoshadingOpacityDeferral(boneGateAlpha, boneNoshading);
                        boolean boneIrisCamera = canIrisSoftPath && !boneNoshadingQueue;

                        softSubmits.add(new SoftBoneSubmit(softBone, softDistanceSq, boneNoshadingQueue, boneIrisCamera));
                    }

                    /* Farther first within each queue batch. */
                    softSubmits.sort((a, b) -> Double.compare(b.distanceSq, a.distanceSq));

                    SoftLimbDrawState softDraw = new SoftLimbDrawState();
                    softDraw.target = target;
                    softDraw.model = model;
                    softDraw.transition = softTransitionSnapshot;
                    softDraw.pose = softPoseSnapshot;
                    softDraw.baseTransform = softBaseTransformSnapshot;
                    softDraw.stackLocal = softStackLocal;
                    softDraw.stackBbs = softStackBbs;
                    softDraw.normalMatrix = softNormalMatrix;
                    softDraw.irisProgram = softIrisProgram;
                    softDraw.bbsProgram = softBbsProgram;
                    softDraw.color = softColorSnapshot;
                    softDraw.defaultTexture = softDefaultTexture;
                    softDraw.textureBlend = softTextureBlend;
                    softDraw.glow = softGlow;
                    softDraw.glowColor = softGlowColor;
                    softDraw.legacyGlow = softLegacyGlow;
                    softDraw.paint = softPaintSnapshot;
                    softDraw.glowDeferred = softGlowDeferred;
                    softDraw.light = softLight;
                    softDraw.overlay = softOverlay;
                    softDraw.colorTransformActive = softColorTransformActive;
                    softDraw.colorEffectTransform = softColorEffectTransform;
                    softDraw.colorMaskHalf = softColorMaskHalf;
                    softDraw.formColor = softFormColor;
                    softDraw.gradeActive = softGradeActive;
                    softDraw.gradeBrightness = softGradeBrightness;
                    softDraw.gradeContrast = softGradeContrast;
                    softDraw.gradeHue = softGradeHue;
                    softDraw.gradeSaturation = softGradeSaturation;
                    softDraw.gradeBrightnessTransform = softGradeBrightnessTransform;
                    softDraw.gradeContrastTransform = softGradeContrastTransform;
                    softDraw.gradeHueTransform = softGradeHueTransform;
                    softDraw.gradeSaturationTransform = softGradeSaturationTransform;
                    softDraw.paintInMesh = softPaintInMesh;
                    softDraw.paintTransform = softPaintTransform;
                    softDraw.paintMaskHalf = softPaintMaskHalf;
                    softDraw.paintStrength = softPaintStrengthSnapshot;
                    softDraw.hasGlow = softHasGlow;
                    softDraw.stripGlow = softStripGlow;
                    softDraw.mainPassGlow = softMainPassGlow;
                    softDraw.glowTransform = softGlowTransform;
                    softDraw.glowMaskHalf = softGlowMaskHalf;
                    softDraw.depthWrite = softDepthWrite;
                    softDraw.afterFluids = softAfterFluids;

                    boolean softDepthMaskSaved = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                    List<SoftBoneSubmit> immediateBatch = new ArrayList<>();
                    List<SoftBoneSubmit> noshadingBatch = new ArrayList<>();
                    List<SoftBoneSubmit> irisBatch = new ArrayList<>();
                    List<SoftBoneSubmit> bbsBatch = new ArrayList<>();

                    for (SoftBoneSubmit softSubmit : softSubmits)
                    {
                        if (limbOnlySoftImmediate)
                        {
                            immediateBatch.add(softSubmit);
                        }
                        else if (softSubmit.noshadingQueue)
                        {
                            noshadingBatch.add(softSubmit);
                        }
                        else if (softSubmit.irisCamera)
                        {
                            irisBatch.add(softSubmit);
                        }
                        else
                        {
                            bbsBatch.add(softSubmit);
                        }
                    }

                    this.enqueueSoftLimbBatch(immediateBatch, SoftLimbQueue.IMMEDIATE, softDraw, softDepthMaskSaved);
                    this.enqueueSoftLimbBatch(noshadingBatch, SoftLimbQueue.NOSHADING, softDraw, softDepthMaskSaved);
                    this.enqueueSoftLimbBatch(irisBatch, SoftLimbQueue.IRIS, softDraw, softDepthMaskSaved);
                    this.enqueueSoftLimbBatch(bbsBatch, SoftLimbQueue.BBS, softDraw, softDepthMaskSaved);
                }
            }

            if (useColorGradeOverlay)
            {
                /* Regrade already-drawn albedo from a scene copy (Block-style). Works without
                 * shaders immediately, and under Iris after composite via the paint queue. */
                Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(newStack.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(newStack.peek().getNormalMatrix());
                Matrix4f baseTransformSnapshot = baseTransform == null ? null : new Matrix4f(baseTransform);
                Color colorSnapshot = color.copy();
                Pose poseSnapshot = this.getPose().copy();
                float transitionSnapshot = transition;
                int overlayLight = light;
                int overlayOverlay = overlay;
                Link defaultTextureSnapshot = defaultTexture;

                ModelVAORenderer.submitColorGradeOverlay(() ->
                {
                    this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);

                    try
                    {
                        ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                        ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                        ModelVAORenderer.setGradeEffectTransforms(gradeBrightnessTransformSnapshot, gradeContrastTransformSnapshot, gradeHueTransformSnapshot, gradeSaturationTransformSnapshot);
                        ModelVAORenderer.clearPaint();
                        ModelVAORenderer.clearGlowing();

                        if (defaultTextureSnapshot != null)
                        {
                            BBSModClient.getTextures().bindTexture(defaultTextureSnapshot);
                        }

                        MatrixStack overlayStack = new MatrixStack();

                        overlayStack.peek().getPositionMatrix().set(positionMatrix);
                        overlayStack.peek().getNormalMatrix().set(normalMatrix);

                        this.renderModelGeometry(overlayStack, BBSShaders::getModel, model, overlayLight, overlayOverlay, null, colorSnapshot, defaultTextureSnapshot, null);
                    }
                    finally
                    {
                        ModelVAORenderer.clearColorEffectTransform();
                        ModelVAORenderer.clearFormColorGrade();
                        ModelVAORenderer.clearPaint();
                        ModelVAORenderer.clearGlowing();
                    }
                });
            }

            if (deferColorTintToOverlay)
            {
                /* Iris already drew pack-lit albedo; multiply FormColorTint inside the mask so
                 * changing Color transform numbers never leaves the Iris lighting path. */
                Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(newStack.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(newStack.peek().getNormalMatrix());
                Matrix4f baseTransformSnapshot = baseTransform == null ? null : new Matrix4f(baseTransform);
                Color colorSnapshot = color.copy();
                Pose poseSnapshot = this.getPose().copy();
                float transitionSnapshot = transition;
                int overlayLight = light;
                int overlayOverlay = overlay;
                Link defaultTextureSnapshot = defaultTexture;
                TextureBlend textureBlendSnapshotFinal = textureBlendSnapshot;

                ModelVAORenderer.submitColorTintOverlay(() ->
                {
                    this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);

                    try
                    {
                        ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                        ModelVAORenderer.setFormColorTint(formColorSnapshot.r, formColorSnapshot.g, formColorSnapshot.b, formColorSnapshot.a);

                        if (gradeActiveSnapshot)
                        {
                            ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                            ModelVAORenderer.setGradeEffectTransforms(gradeBrightnessTransformSnapshot, gradeContrastTransformSnapshot, gradeHueTransformSnapshot, gradeSaturationTransformSnapshot);
                        }

                        ModelVAORenderer.clearPaint();
                        ModelVAORenderer.clearGlowing();

                        MatrixStack overlayStack = new MatrixStack();

                        overlayStack.peek().getPositionMatrix().set(positionMatrix);
                        overlayStack.peek().getNormalMatrix().set(normalMatrix);

                        this.renderModelGeometry(overlayStack, BBSShaders::getModel, model, overlayLight, overlayOverlay, null, colorSnapshot, defaultTextureSnapshot, textureBlendSnapshotFinal);
                    }
                    finally
                    {
                        ModelVAORenderer.clearColorEffectTransform();
                        ModelVAORenderer.clearFormColorTint();
                        ModelVAORenderer.clearFormColorGrade();
                        ModelVAORenderer.clearPaint();
                        ModelVAORenderer.clearGlowing();
                    }
                });
            }

            if (deferPaintToOverlay)
            {
                Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(newStack.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(newStack.peek().getNormalMatrix());
                Matrix4f baseTransformSnapshot = baseTransform == null ? null : new Matrix4f(baseTransform);
                Color colorSnapshot = color.copy();
                Color paintSnapshot = paintColor.copy();
                Pose poseSnapshot = this.getPose().copy();
                float transitionSnapshot = transition;
                int overlayLight = light;
                int overlayOverlay = overlay;
                Link defaultTextureSnapshot = defaultTexture;
                TextureBlend textureBlendSnapshotFinal = textureBlendSnapshot;
                boolean hasGlowSnapshot = hasGlow;
                boolean paintOnlyGlowSnapshot = paintOnlyGlow;

                ModelVAORenderer.submitPaintOverlay(false, () ->
                {
                    this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);

                    try
                    {
                        if (colorTransformActive && !deferColorTintToOverlay)
                        {
                            ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                            ModelVAORenderer.setFormColorTint(formColorSnapshot.r, formColorSnapshot.g, formColorSnapshot.b, formColorSnapshot.a);
                        }

                        ModelVAORenderer.setPaintEffectTransform(new Matrix4f().identity(), paintTransformSnapshot, paintMaskHalfSnapshot);
                        ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot);

                        MatrixStack overlayStack = new MatrixStack();

                        overlayStack.peek().getPositionMatrix().set(positionMatrix);
                        overlayStack.peek().getNormalMatrix().set(normalMatrix);

                        ModelVAORenderer.setPaint(paintSnapshot.r, paintSnapshot.g, paintSnapshot.b, paintSnapshot.a);

                        if (hasGlowSnapshot)
                        {
                            ModelVAORenderer.setGlow(glow, glowColor.r, glowColor.g, glowColor.b, legacyGlow);
                        }
                        else
                        {
                            GlowSettings glowOff = glow.copy();

                            glowOff.intensity = 0F;
                            ModelVAORenderer.setGlow(glowOff, glowColor.r, glowColor.g, glowColor.b, legacyGlow);
                        }

                        this.renderModelGeometry(overlayStack, BBSShaders::getModel, model, overlayLight, overlayOverlay, stencilMap, colorSnapshot, defaultTextureSnapshot, textureBlendSnapshotFinal);
                    }
                    finally
                    {
                        ModelVAORenderer.clearColorEffectTransform();
                        ModelVAORenderer.clearFormColorTint();
                        ModelVAORenderer.clearPaintEffectTransform();
                        ModelVAORenderer.clearGlowEffectTransform();
                        ModelVAORenderer.clearPaint();
                        ModelVAORenderer.clearGlowing();
                    }
                });

                if (hasGlowSnapshot && !paintOnlyGlowSnapshot)
                {
                    ModelVAORenderer.submitPaintOverlay(false, () ->
                    {
                        this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);

                        try
                        {
                            if (colorTransformActive && !deferColorTintToOverlay)
                            {
                                ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                                ModelVAORenderer.setFormColorTint(formColorSnapshot.r, formColorSnapshot.g, formColorSnapshot.b, formColorSnapshot.a);
                            }

                            ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot);

                            MatrixStack overlayStack = new MatrixStack();

                            overlayStack.peek().getPositionMatrix().set(positionMatrix);
                            overlayStack.peek().getNormalMatrix().set(normalMatrix);

                            this.renderDeferredGlowEmission(overlayStack, model, overlayLight, overlayOverlay, stencilMap, colorSnapshot, defaultTextureSnapshot, textureBlendSnapshotFinal, glow, glowColor, legacyGlow);
                        }
                        finally
                        {
                            ModelVAORenderer.clearColorEffectTransform();
                            ModelVAORenderer.clearFormColorTint();
                            ModelVAORenderer.clearGlowEffectTransform();
                            ModelVAORenderer.clearPaint();
                            ModelVAORenderer.clearGlowing();
                        }
                    });
                }
            }
            else if (shaderOverlay)
            {
                Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(newStack.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(newStack.peek().getNormalMatrix());
                Matrix4f baseTransformSnapshot = baseTransform == null ? null : new Matrix4f(baseTransform);
                Color colorSnapshot = color.copy();
                Pose poseSnapshot = this.getPose().copy();
                float transitionSnapshot = transition;
                int overlayLight = light;
                int overlayOverlay = overlay;
                boolean applyPoseSnapshot = syncedGlow || glowHasSpatialMask;
                Link defaultTextureSnapshot = defaultTexture;

                ModelVAORenderer.submitPaintOverlay(false, () ->
                {
                    if (applyPoseSnapshot)
                    {
                        this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);
                    }

                    try
                    {
                        if (colorTransformActive)
                        {
                            ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                            ModelVAORenderer.setFormColorTint(formColorSnapshot.r, formColorSnapshot.g, formColorSnapshot.b, formColorSnapshot.a);
                        }

                        ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot);

                        MatrixStack overlayStack = new MatrixStack();

                        overlayStack.peek().getPositionMatrix().set(positionMatrix);
                        overlayStack.peek().getNormalMatrix().set(normalMatrix);

                        this.renderDeferredGlowEmission(overlayStack, model, overlayLight, overlayOverlay, stencilMap, colorSnapshot, defaultTextureSnapshot, textureBlendSnapshot, glow, glowColor, legacyGlow);
                    }
                    finally
                    {
                        ModelVAORenderer.clearColorEffectTransform();
                        ModelVAORenderer.clearFormColorTint();
                        ModelVAORenderer.clearGlowEffectTransform();
                        ModelVAORenderer.clearPaint();
                        ModelVAORenderer.clearGlowing();
                    }
                });
            }
            else if (deferColorTintToOverlay)
            {
                Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(newStack.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(newStack.peek().getNormalMatrix());
                Matrix4f baseTransformSnapshot = baseTransform == null ? null : new Matrix4f(baseTransform);
                Color colorSnapshot = color.copy();
                Pose poseSnapshot = this.getPose().copy();
                float transitionSnapshot = transition;
                int overlayLight = light;
                int overlayOverlay = overlay;
                Link defaultTextureSnapshot = defaultTexture;

                ModelVAORenderer.submitColorTintOverlay(() ->
                {
                    this.applyOverlayPosePipeline(target, model, transitionSnapshot, poseSnapshot, baseTransformSnapshot);

                    try
                    {
                        ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), colorTransformSnapshot, colorMaskHalfSnapshot);
                        ModelVAORenderer.setFormColorTint(formColorSnapshot.r, formColorSnapshot.g, formColorSnapshot.b, formColorSnapshot.a);

                        MatrixStack overlayStack = new MatrixStack();

                        overlayStack.peek().getPositionMatrix().set(positionMatrix);
                        overlayStack.peek().getNormalMatrix().set(normalMatrix);

                        this.renderModelGeometry(overlayStack, BBSShaders::getModel, model, overlayLight, overlayOverlay, stencilMap, colorSnapshot, defaultTextureSnapshot, textureBlendSnapshot);
                    }
                    finally
                    {
                        ModelVAORenderer.clearColorEffectTransform();
                        ModelVAORenderer.clearFormColorTint();
                    }
                });
            }
        }
        finally
        {
            if (limbVisibilitySave != null)
            {
                this.restoreGroupVisibility(limbVisibilitySave);
            }

            this.clearPBRTextureIntensity();
            ModelVAORenderer.clearColorEffectTransform();
            ModelVAORenderer.clearFormColorTint();
            ModelVAORenderer.clearFormColorGrade();
            FormColorGradePatch.uploadToCurrentProgram();
            ModelVAORenderer.clearPaintEffectTransform();
            ModelVAORenderer.clearGlowEffectTransform();
            ModelVAORenderer.clearPaint();
            ModelVAORenderer.clearGlowing();
        }

        /* IK overlay, over the finished geometry and only in the visible pass — the
         * picking pass gets its own markers in updateStencilMap. Drawn wherever the form
         * renders, viewport and world alike; BBSSettings.ikDebug.enabled is the switch.
         *
         * The map comes from the PROCESSOR, not from this.form.ik: a model's IK config
         * lives in instance.limbConstraints, and only the model editor copies it onto the
         * form — reading form.ik alone is why the overlay used to appear nowhere else. */
        MapType ikMap = stencilMap == null ? LimbConstraintProcessor.resolveIkMap(model) : null;

        if (ikMap != null && !ikMap.isEmpty())
        {
            ModelIKDebug.render(newStack, model.model, ikMap, "");
        }

        MapType springsMap = stencilMap == null ? DynamicBoneOrchestrator.resolveSpringsMap(model) : null;

        if (springsMap != null && !springsMap.isEmpty())
        {
            ModelPhysicsDebug.render(newStack, model.model, springsMap, target == null ? 0 : target.getAge(), "");
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();

        if (!model.culling)
        {
            RenderSystem.enableCull();
        }

        /* Render items — restore vanilla entity lighting (diffuse + lightmap + overlay)
         * like MobForm / ArmorFeatureRendererMixin so Iris pack shading matches player armor. */
        this.captureMatrices(model);

        if (stencilMap == null && renderEquipment)
        {
            /* World morphs + editor model-renderer previews (model block / form edit). Inventory
             * morphs stay on InventoryScreen.method_34742 lights (no prepareVanilla here). */
            boolean previewEquipment = renderContext != null && renderContext.modelRenderer;

            if (!ui && (BBSRendering.isRenderingWorld() || previewEquipment))
            {
                BBSRendering.prepareVanillaEntityLighting();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            }

            this.renderItems(target, model, stack, EquipmentSlot.MAINHAND, ModelTransformationMode.THIRD_PERSON_RIGHT_HAND, model.itemsMain, model.itemsMainTransform, color, overlay, light);
            this.renderItems(target, model, stack, EquipmentSlot.OFFHAND, ModelTransformationMode.THIRD_PERSON_LEFT_HAND, model.itemsOff, model.itemsOffTransform, color, overlay, light);

            for (Map.Entry<ArmorType, ArmorSlot> entry : model.armorSlots.entrySet())
            {
                this.renderArmor(target, stack, entry.getKey(), entry.getValue(), color, overlay, light);
            }

            /* Non-armor HEAD items (skulls, blocks/commands hats, etc.) — ArmorItem helmets
             * stay on ArmorRenderer above, matching vanilla HeadFeatureRenderer. */
            this.renderHeadSlotItem(target, model, stack, color, overlay, light);

            this.resetPostEquipmentRenderState();
        }

        if (!ui && BBSRendering.isRenderingWorld())
        {
            BBSRendering.restoreWorldRenderState();
        }
    }

    private void applyIKOnce(ModelInstance model, Matrix4f baseTransform)
    {
        if (this.ikAppliedThisRender)
        {
            return;
        }

        this.ikAppliedThisRender = true;
        model.form = this.form;

        boolean hasOverrides = baseTransform != null && this.form != null
            && (!this.form.ikTargetOverrides.isEmpty()
                || !this.form.poleTargetOverrides.isEmpty()
                || !this.form.ikTipRotationOverrides.isEmpty()
                || !this.form.limbParamOverrides.isEmpty());

        if (!hasOverrides)
        {
            LimbConstraintProcessor.process(model, null, null);
            return;
        }

        Matrix4f inv = new Matrix4f(baseTransform).invert();
        Map<String, Vector3f> local = toModelSpace(this.form.ikTargetOverrides, inv);
        Map<String, Vector3f> poleLocal = toModelSpace(this.form.poleTargetOverrides, inv);
        Map<String, Quaternionf> tipLocal = toModelSpaceRotation(this.form.ikTipRotationOverrides, inv);

        if (local.isEmpty() && poleLocal.isEmpty() && tipLocal.isEmpty() && this.form.limbParamOverrides.isEmpty())
        {
            LimbConstraintProcessor.process(model, null, null);
            return;
        }

        LimbConstraintProcessor.process(
            model,
            local.isEmpty() ? null : local,
            poleLocal.isEmpty() ? null : poleLocal,
            tipLocal.isEmpty() ? null : tipLocal,
            null
        );
    }

    private static Map<String, Quaternionf> toModelSpaceRotation(Map<String, Quaternionf> world, Matrix4f inv)
    {
        Map<String, Quaternionf> local = new HashMap<>(world.size() * 2);
        Quaternionf invRot = inv.getNormalizedRotation(new Quaternionf());

        for (Map.Entry<String, Quaternionf> entry : world.entrySet())
        {
            String key = entry.getKey();
            Quaternionf worldRot = entry.getValue();

            if (key == null || key.isEmpty() || worldRot == null)
            {
                continue;
            }

            local.put(key, invRot.mul(worldRot, new Quaternionf()));
        }

        return local;
    }

    /** World-space target overrides into the model's local space (the space the solver and pivot frames use). */
    private static Map<String, Vector3f> toModelSpace(Map<String, Vector3f> world, Matrix4f inv)
    {
        Map<String, Vector3f> local = new HashMap<>(world.size() * 2);

        for (Map.Entry<String, Vector3f> entry : world.entrySet())
        {
            String key = entry.getKey();
            Vector3f worldPos = entry.getValue();

            if (key == null || key.isEmpty() || worldPos == null)
            {
                continue;
            }

            Vector3f pos = new Vector3f(worldPos);
            inv.transformPosition(pos);
            local.put(key, pos);
        }

        return local;
    }

    private void applyPhysicsOnce(IEntity target, ModelInstance model, float transition, Matrix4f baseTransform)
    {
        if (this.physicsAppliedThisRender)
        {
            return;
        }

        this.physicsAppliedThisRender = true;
        model.lastBaseTransform = baseTransform;
        model.form = this.form;
        DynamicBoneOrchestrator.apply(target, model, transition, baseTransform);
    }

    private void applyConstraintsOnce(ModelInstance model)
    {
        if (this.constraintsAppliedThisRender)
        {
            return;
        }

        this.constraintsAppliedThisRender = true;
        JointLimitEnforcer.enforce(model);
    }

    /**
     * Replays the same pose pipeline used before the base pass so deferred Iris paint/glow
     * overlays match animated bones when the shared model instance was touched by other draws.
     */
    private void applyAnimatedPoseForOverlay(IEntity target, ModelInstance model, float transition, Pose poseSnapshot)
    {
        if (this.animator == null || model == null)
        {
            return;
        }

        model.model.resetPose();
        this.animator.applyActions(target, model, transition);
        model.model.applyPose(poseSnapshot);
    }

    private void applyOverlayPosePipeline(IEntity target, ModelInstance model, float transition, Pose poseSnapshot, Matrix4f baseTransform)
    {
        this.applyAnimatedPoseForOverlay(target, model, transition, poseSnapshot);

        this.ikAppliedThisRender = false;
        this.physicsAppliedThisRender = false;
        this.constraintsAppliedThisRender = false;
        this.applyIKOnce(model, baseTransform);
        this.applyPhysicsOnce(target, model, transition, baseTransform);
        this.applyConstraintsOnce(model);
    }

    private void renderDeferredGlowEmission(MatrixStack stack, ModelInstance model, int light, int overlay, StencilMap stencilMap, Color color, Link defaultTexture, TextureBlend textureBlend, GlowSettings glow, Color glowColor, Color legacyGlow)
    {
        ModelVAORenderer.runWithPaintOverlayPass(false, () ->
        {
            ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
            ModelVAORenderer.setGlow(glow, glowColor.r, glowColor.g, glowColor.b, legacyGlow);

            Color emission = color.copy();

            emission.r = 0F;
            emission.g = 0F;
            emission.b = 0F;

            boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

            RenderSystem.enableBlend();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

            try
            {
                this.renderModelGeometry(stack, BBSShaders::getModel, model, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, stencilMap, emission, defaultTexture, textureBlend);
            }
            finally
            {
                RenderSystem.depthMask(savedDepthMask);
                RenderSystem.defaultBlendFunc();
            }
        });
    }

    private void renderModelGeometryWithEmission(MatrixStack stack, Supplier<ShaderProgram> program, ModelInstance model, int light, int overlay, StencilMap stencilMap, Color color, Link defaultTexture, TextureBlend textureBlend, GlowSettings glow, Color glowColor, Color legacyGlow, Color paint, boolean glowDeferredToOverlay)
    {
        boolean shapeKeyGlowOverlay = ShapeKeyGlowPass.shouldUseGlowOverlay(model, this.hasAnyPositiveGlow(model, glow, legacyGlow), glowDeferredToOverlay);

        try
        {
            ModelVAORenderer.setSuppressShapeKeyMainPassGlow(shapeKeyGlowOverlay);

            if (shapeKeyGlowOverlay)
            {
                this.renderModelGeometry(stack, program, model, light, overlay, stencilMap, color, defaultTexture, textureBlend);
                this.renderShapeKeyGlowOverlay(stack, model, overlay, stencilMap, color, defaultTexture, textureBlend, glow, legacyGlow);

                return;
            }

            this.renderModelGeometry(stack, program, model, light, overlay, stencilMap, color, defaultTexture, textureBlend);
        }
        finally
        {
            ModelVAORenderer.setSuppressShapeKeyMainPassGlow(false);
        }
    }

    /**
     * Soft-limb translucency: draw camera-away faces first, then camera-facing faces.
     * A single {@code disableCull} pass uses mesh order, so interiors often composite on
     * top of the outer shell and look more opaque than the front (especially in film).
     */
    private void renderSoftLimbGeometryTwoSided(MatrixStack stack, Supplier<ShaderProgram> program, ModelInstance model, int light, int overlay, Color color, Link defaultTexture, TextureBlend textureBlend, GlowSettings glow, Color glowColor, Color legacyGlow, Color paint, boolean glowDeferredToOverlay, Matrix4f positionMatrix)
    {
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int savedCullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        int savedFrontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        /* Reflections / odd MV×entity stacks invert winding — keep GL_BACK = camera-facing.
         * Use live ModelView (Iris restores it before this draw) × entity root. */
        Matrix4f facingMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());

        if (positionMatrix != null)
        {
            facingMatrix.mul(positionMatrix);
        }

        boolean flipWinding = facingMatrix.determinant() < 0F;

        RenderSystem.enableCull();
        GL11.glFrontFace(flipWinding ? GL11.GL_CW : GL11.GL_CCW);

        try
        {
            GL11.glCullFace(GL11.GL_FRONT);
            this.renderModelGeometryWithEmission(stack, program, model, light, overlay, null, color, defaultTexture, textureBlend, glow, glowColor, legacyGlow, paint, glowDeferredToOverlay);

            GL11.glCullFace(GL11.GL_BACK);
            this.renderModelGeometryWithEmission(stack, program, model, light, overlay, null, color, defaultTexture, textureBlend, glow, glowColor, legacyGlow, paint, glowDeferredToOverlay);
        }
        finally
        {
            GL11.glCullFace(savedCullFace);
            GL11.glFrontFace(savedFrontFace);

            if (cullWasEnabled)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }
        }
    }

    private void renderShapeKeyGlowOverlay(MatrixStack stack, ModelInstance model, int overlay, StencilMap stencilMap, Color color, Link defaultTexture, TextureBlend textureBlend, GlowSettings glow, Color legacyGlow)
    {
        boolean formPositive = FormColorEffects.hasPositiveGlow(glow, legacyGlow);
        boolean bonePositive = this.hasBonePositiveGlow(model);

        if (!formPositive && !bonePositive)
        {
            return;
        }

        ShapeKeys shapeKeys = this.form.shapeKeys.get();

        if (formPositive)
        {
            float formIntensity = glow.resolveIntensity(legacyGlow);

            ShapeKeyGlowPass.renderOverlay(glow, legacyGlow, color.a, formIntensity, (glowLayerColor) ->
            {
                this.drawShapeKeyGlowOverlayLayer(stack, model, overlay, stencilMap, shapeKeys, defaultTexture, textureBlend, glowLayerColor, false, formIntensity, null, bonePositive);
            });
        }

        if (bonePositive && model.getModel() != null)
        {
            for (ModelGroup group : model.getModel().getAllGroups())
            {
                if (group.glowIntensity <= 0F)
                {
                    continue;
                }

                float boneIntensity = group.glowIntensity;

                ShapeKeyGlowPass.renderOverlay(glow, legacyGlow, color.a, boneIntensity, (glowLayerColor) ->
                {
                    this.drawShapeKeyGlowOverlayLayer(stack, model, overlay, stencilMap, shapeKeys, defaultTexture, textureBlend, glowLayerColor, true, boneIntensity, group.id, false);
                });
            }
        }
    }

    private void drawShapeKeyGlowOverlayLayer(MatrixStack stack, ModelInstance model, int overlay, StencilMap stencilMap, ShapeKeys shapeKeys, Link defaultTexture, TextureBlend textureBlend, Color glowLayerColor, boolean boneGlowOnly, float overlayIntensity, String targetGroupId, boolean skipBoneGlowGroups)
    {
        Link drawTexture = defaultTexture;

        if (textureBlend != null)
        {
            float blend = textureBlend.blend;
            Link fromTexture = textureBlend.from == null ? defaultTexture : textureBlend.from;
            Link toTexture = textureBlend.to == null ? defaultTexture : textureBlend.to;

            if (blend <= 0F)
            {
                drawTexture = fromTexture;
            }
            else if (blend >= 1F)
            {
                drawTexture = toTexture;
            }
            else
            {
                drawTexture = fromTexture;
            }
        }

        model.renderShapeKeyGlowOverlay(stack, glowLayerColor, overlay, stencilMap, shapeKeys, drawTexture, boneGlowOnly, overlayIntensity, targetGroupId, skipBoneGlowGroups);
    }

    private boolean hasBonePositiveGlow(ModelInstance model)
    {
        if (model == null || model.getModel() == null)
        {
            return false;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (group.glowIntensity > 0F)
            {
                return true;
            }
        }

        return false;
    }

    private GlowSettings resolveMainPassGlow(GlowSettings glow, Color legacyGlow, boolean stripDeferredGlow, boolean shapeKeyPositiveOverlay)
    {
        GlowSettings result = glow.copy();
        float intensity = result.resolveIntensity(legacyGlow);

        if (stripDeferredGlow)
        {
            result.intensity = 0F;

            return result;
        }

        if (shapeKeyPositiveOverlay && intensity > 0F)
        {
            result.intensity = 0F;
        }

        return result;
    }

    private boolean hasAnyPositiveGlow(ModelInstance model, GlowSettings glow, Color legacyGlow)
    {
        if (FormColorEffects.hasPositiveGlow(glow, legacyGlow))
        {
            return true;
        }

        if (model != null && model.getModel() != null)
        {
            for (ModelGroup group : model.getModel().getAllGroups())
            {
                if (group.glowIntensity > 0F)
                {
                    return true;
                }
            }
        }

        return false;
    }

    private void renderModelGeometry(MatrixStack stack, Supplier<ShaderProgram> program, ModelInstance model, int light, int overlay, StencilMap stencilMap, Color color, Link defaultTexture, TextureBlend textureBlend)
    {
        ShapeKeys shapeKeys = this.form.shapeKeys.get();

        if (textureBlend == null)
        {
            ModelVAORenderer.clearTextureBlend();
            model.render(stack, program, color, light, overlay, stencilMap, shapeKeys, this.getTextureResolver(model, defaultTexture));

            return;
        }

        float blend = textureBlend.blend;
        Link fromTexture = textureBlend.from == null ? defaultTexture : textureBlend.from;
        Link toTexture = textureBlend.to == null ? defaultTexture : textureBlend.to;

        if (blend <= 0F)
        {
            ModelVAORenderer.clearTextureBlend();
            model.render(stack, program, color, light, overlay, stencilMap, shapeKeys, this.getTextureResolver(model, fromTexture));
        }
        else if (blend >= 1F)
        {
            ModelVAORenderer.clearTextureBlend();
            model.render(stack, program, color, light, overlay, stencilMap, shapeKeys, this.getTextureResolver(model, toTexture));
        }
        else if (model.supportsBbsModelShaderEffects() && (program.get() == BBSShaders.getModel() || ModelVAORenderer.isPaintOverlayPass()))
        {
            /* Single-pass shader blend: per-pixel alpha crossfade avoids two-pass holes when both skins are opaque.
             * Iris world pass uses vanilla two-pass below; BBS blend is allowed during paint overlay redraws. */
            Supplier<ShaderProgram> blendProgram = BBSShaders::getModel;

            ModelVAORenderer.setTextureBlend(toTexture, blend);

            try
            {
                RenderSystem.setShader(blendProgram);
                model.render(stack, blendProgram, color, light, overlay, stencilMap, shapeKeys, this.getTextureResolver(model, fromTexture));
            }
            finally
            {
                ModelVAORenderer.clearTextureBlend();
            }
        }
        else
        {
            /* Iris VAO + cube mesh: vanilla shader has no texture-blend uniforms. */
            ModelVAORenderer.clearTextureBlend();

            Color colorFrom = color.copy();

            colorFrom.a *= 1F - blend;
            model.render(stack, program, colorFrom, light, overlay, stencilMap, shapeKeys, this.getTextureResolver(model, fromTexture));

            Color colorTo = color.copy();

            colorTo.a *= blend;
            model.render(stack, program, colorTo, light, overlay, stencilMap, shapeKeys, this.getTextureResolver(model, toTexture));
        }
    }

    private Function<String, Link> getTextureResolver(ModelInstance model, Link defaultTexture)
    {
        final Link materialFallback = model.materials.isEmpty() ? defaultTexture : model.texture;

        return (material) ->
        {
            Link override = this.form.materialTextureOverrides.get(material);

            if (override != null)
            {
                return override;
            }

            Link picked = this.form.materialTextures.getLink(material);

            if (picked != null)
            {
                return picked;
            }

            return model.getMaterialTexture(material, materialFallback);
        };
    }

    private Supplier<ShaderProgram> getModelShader(ModelInstance model)
    {
        if (!model.supportsBbsModelShaderEffects())
        {
            return GameRenderer::getRenderTypeEntityTranslucentCullProgram;
        }

        if (this.hasAnyBoneTextureBlend(model))
        {
            return BBSShaders::getModel;
        }

        /* FormColorGrade / bone grades need model.fsh; do not stay on Iris entity for those. */
        Color formColor = this.form.color.get();
        boolean needsBbsGrade = (formColor != null && formColor.hasColorAdjustments())
            || this.hasAnyBoneColorGrade(model);

        if (needsBbsGrade && !BBSRendering.isIrisWorldModelPass())
        {
            return BBSShaders::getModel;
        }

        if (BBSRendering.isIrisWorldModelPass())
        {
            return GameRenderer::getRenderTypeEntityTranslucentCullProgram;
        }

        return BBSShaders::getModel;
    }

    private boolean hasAnyBoneTextureBlend(ModelInstance model)
    {
        if (model == null || model.model == null)
        {
            return false;
        }

        if (model.model instanceof Model cubic)
        {
            for (ModelGroup group : cubic.getAllGroups())
            {
                if (group.textureOverride != null && group.textureBlend > 0F && group.textureBlend < 1F)
                {
                    return true;
                }
            }
        }
        else if (model.model instanceof BOBJModel bobj)
        {
            for (BOBJBone bone : bobj.getArmature().orderedBones)
            {
                if (bone.texture != null && bone.textureBlend > 0F && bone.textureBlend < 1F)
                {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean usesBbsModelShader(ModelInstance model)
    {
        if (model == null || !model.supportsBbsModelShaderEffects())
        {
            return false;
        }

        return !BBSRendering.isIrisWorldModelPass();
    }

    /**
     * Upload spatial paint/glow/color mask uniforms on the live draw when it already runs
     * {@link BBSShaders#getModel()}. On Iris, {@link #usesBbsModelShader} is false even if
     * {@link #getModelShader} picked model.fsh (bone texture blend on BOBJ/OBJ/VAO paths) —
     * without this, masks never reach the shader on that pass.
     */
    private boolean usesMainPassModelEffectUniforms(ModelInstance model, boolean deferTranslucentModel)
    {
        if (model == null || !model.supportsBbsModelShaderEffects())
        {
            return false;
        }

        if (deferTranslucentModel)
        {
            return true;
        }

        if (!BBSRendering.isIrisWorldPaintDeferral())
        {
            return true;
        }

        return this.hasAnyBoneTextureBlend(model)
            || this.hasAnyBoneColorTransform(model)
            || this.hasAnyBoneGlowTransform(model)
            || this.hasAnyBoneColorGrade(model)
            || this.hasBonePaint(model);
    }

    /**
     * Form color tint uses the BBS tint / Iris multiply-overlay path whenever RGB is tinted or a
     * spatial transform is active — same lighting-safe path as moving Transform numbers.
     */
    private boolean canApplyColorTransformMask(ModelInstance model)
    {
        if (model == null || !model.supportsBbsModelShaderEffects())
        {
            return false;
        }

        if (this.hasAnyBoneColorTransform(model))
        {
            return true;
        }

        Color color = this.form.color.get();

        /* Plain RGB without a spatial transform bakes into vertex tint. */
        return color != null && color.hasActiveTransform();
    }

    /**
     * Bake form color into vertex tint only when no spatial mask will be applied in-shader.
     */
    private boolean shouldBakeFormColor(ModelInstance model)
    {
        return !this.canApplyColorTransformMask(model);
    }

    /**
     * Vertex bake for form color / Color Grade. Color Grade always runs in FormColorGrade /
     * ColorGradeOverlay after the texture sample — baking contrast onto white tint is invisible.
     */
    private Color resolveBakeFormColor(ModelInstance model, boolean ui)
    {
        Color stored = this.form.color.get();

        if (!stored.hasColorAdjustments())
        {
            return stored.copyBakingColorGrade();
        }

        if (model != null && model.supportsBbsModelShaderEffects())
        {
            return stored.copyDeferringColorGrade();
        }

        /* Non-VAO fallback: no FormColorGrade uniforms available. */
        return stored.copyBakingColorGrade();
    }

    private boolean hasFullyTransparentDrawableBones(ModelInstance model)
    {
        if (model == null || model.getModel() == null)
        {
            return false;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (!this.groupHasDrawableGeometry(model, group) || group.color == null)
            {
                continue;
            }

            if (group.color.a <= 0.001F)
            {
                return true;
            }
        }

        return false;
    }

    private void hideFullyTransparentBones(ModelInstance model)
    {
        if (model == null || model.getModel() == null)
        {
            return;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (!this.groupHasDrawableGeometry(model, group))
            {
                continue;
            }

            if (group.color != null && group.color.a <= 0.001F)
            {
                group.visible = false;
            }
        }
    }

    /**
     * Softest <em>visible</em> pose-bone alpha (1 if none). Fully transparent bones are
     * ignored so empty anchors do not force opacityAlpha to 0 and skip post-deferred.
     */
    private float getMinBoneOpacityAlpha(ModelInstance model)
    {
        if (model == null || model.getModel() == null)
        {
            return 1F;
        }

        float min = 1F;
        boolean found = false;

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (!this.groupHasDrawableGeometry(model, group) || group.color == null)
            {
                continue;
            }

            float boneAlpha = group.color.a;

            if (boneAlpha <= 0.001F)
            {
                continue;
            }

            found = true;
            min = Math.min(min, boneAlpha);
        }

        return found ? min : 1F;
    }

    private boolean groupHasDrawableGeometry(ModelInstance model, ModelGroup group)
    {
        if (group == null)
        {
            return false;
        }

        if (!group.cubes.isEmpty() || !group.meshes.isEmpty())
        {
            return true;
        }

        return model != null && model.getVaos() != null && model.getVaos().get(group) != null;
    }

    private Map<ModelGroup, Boolean> saveGroupVisibility(ModelInstance model)
    {
        Map<ModelGroup, Boolean> saved = new HashMap<>();

        if (model == null || model.getModel() == null)
        {
            return saved;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            saved.put(group, group.visible);
        }

        return saved;
    }

    private void restoreGroupVisibility(Map<ModelGroup, Boolean> saved)
    {
        if (saved == null)
        {
            return;
        }

        for (Map.Entry<ModelGroup, Boolean> entry : saved.entrySet())
        {
            entry.getKey().visible = entry.getValue();
        }
    }

    /**
     * Toggle drawable groups for limb-only soft split. Fully transparent bones stay hidden.
     */
    private void applyLimbSoftVisibility(ModelInstance model, boolean showSoft, boolean showOpaque)
    {
        if (model == null || model.getModel() == null)
        {
            return;
        }

        boolean formNoshading = this.form.noshadingOpacity.get();

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (!this.groupHasDrawableGeometry(model, group))
            {
                continue;
            }

            float boneAlpha = group.color == null ? 1F : group.color.a;

            if (boneAlpha <= 0.001F)
            {
                group.visible = false;
            }
            else if (boneAlpha < ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA || (!formNoshading && group.noshadingOpacity))
            {
                group.visible = showSoft;
            }
            else
            {
                group.visible = showOpaque;
            }
        }
    }

    /**
     * Soft drawable bones for per-bone post-deferred sort (limb-only soft path).
     */
    private List<ModelGroup> collectSoftDrawableBones(ModelInstance model)
    {
        List<ModelGroup> soft = new ArrayList<>();

        if (model == null || model.getModel() == null)
        {
            return soft;
        }

        boolean formNoshading = this.form.noshadingOpacity.get();

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (!this.groupHasDrawableGeometry(model, group))
            {
                continue;
            }

            float boneAlpha = group.color == null ? 1F : group.color.a;

            if (boneAlpha <= 0.001F)
            {
                continue;
            }

            if (boneAlpha < ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA || (!formNoshading && group.noshadingOpacity))
            {
                soft.add(group);
            }
        }

        return soft;
    }

    /**
     * Deferred soft draw: only {@code only} is visible among drawable groups.
     */
    private void applyOnlySoftBoneVisible(ModelInstance model, ModelGroup only)
    {
        if (model == null || model.getModel() == null)
        {
            return;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (!this.groupHasDrawableGeometry(model, group))
            {
                continue;
            }

            float boneAlpha = group.color == null ? 1F : group.color.a;

            if (boneAlpha <= 0.001F)
            {
                group.visible = false;
            }
            else
            {
                group.visible = group == only;
            }
        }
    }

    /**
     * Soft-limb queue batch: one or more soft bones sharing Iris / BBS / noshading / preview.
     * Multi-bone batches paint with depth-write off (soft-vs-soft), then stamp depth only so
     * Iris fog/paint still occlude (noshading off).
     */
    private void enqueueSoftLimbBatch(List<SoftBoneSubmit> batch, SoftLimbQueue queue, SoftLimbDrawState draw, boolean savedDepthMask)
    {
        if (batch == null || batch.isEmpty() || draw == null)
        {
            return;
        }

        boolean irisStyle = queue == SoftLimbQueue.IRIS;
        boolean useLocalStack = queue == SoftLimbQueue.IRIS || queue == SoftLimbQueue.IMMEDIATE;
        Matrix4f softPositionMatrix = useLocalStack ? draw.stackLocal : draw.stackBbs;
        Supplier<ShaderProgram> softProgram = useLocalStack ? draw.irisProgram : draw.bbsProgram;
        boolean multiSoft = batch.size() > 1;
        boolean stampDepth = multiSoft && draw.depthWrite;
        /* Queue entry depthWrite true when we stamp (or single-bone color writes depth). */
        boolean entryDepthWrite = draw.depthWrite;
        double batchDistanceSq = batch.get(0).distanceSq;
        List<SoftBoneSubmit> batchSnapshot = new ArrayList<>(batch);
        Runnable softDeferredDraw = () -> this.runSoftLimbBatchDraw(batchSnapshot, draw, softPositionMatrix, softProgram, irisStyle, stampDepth, !multiSoft && draw.depthWrite);

        if (queue == SoftLimbQueue.IMMEDIATE)
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            try
            {
                softDeferredDraw.run();
            }
            finally
            {
                RenderSystem.depthMask(savedDepthMask);
                RenderSystem.colorMask(true, true, true, true);
            }

            return;
        }

        if (queue == SoftLimbQueue.NOSHADING)
        {
            ModelVAORenderer.submitDeferredTranslucentModel(softDeferredDraw, entryDepthWrite);

            return;
        }

        if (queue == SoftLimbQueue.IRIS)
        {
            ShaderOpacityPatch.submitPostDeferredForm(0D, batchDistanceSq, entryDepthWrite, draw.afterFluids, softDeferredDraw);

            return;
        }

        ShaderOpacityPatch.submitPostDeferredBbsForm(0D, batchDistanceSq, entryDepthWrite, draw.afterFluids, softDeferredDraw);
    }

    private void runSoftLimbBatchDraw(List<SoftBoneSubmit> batch, SoftLimbDrawState draw, Matrix4f softPositionMatrix, Supplier<ShaderProgram> softProgram, boolean irisStyle, boolean stampDepth, boolean colorWritesDepth)
    {
        this.applyOverlayPosePipeline(draw.target, draw.model, draw.transition, draw.pose, draw.baseTransform);

        Map<ModelGroup, Boolean> softVisibility = this.saveGroupVisibility(draw.model);

        try
        {
            this.bindSoftLimbDrawState(draw);

            MatrixStack softStack = new MatrixStack();

            softStack.peek().getPositionMatrix().set(softPositionMatrix);
            softStack.peek().getNormalMatrix().set(draw.normalMatrix);

            RenderSystem.depthMask(colorWritesDepth);
            this.drawSoftLimbBones(batch, draw, softStack, softProgram, softPositionMatrix);

            if (stampDepth)
            {
                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                this.drawSoftLimbBones(batch, draw, softStack, softProgram, softPositionMatrix);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.colorMask(true, true, true, true);
            }
        }
        finally
        {
            this.restoreGroupVisibility(softVisibility);
            ModelVAORenderer.clearColorEffectTransform();
            ModelVAORenderer.clearFormColorTint();
            ModelVAORenderer.clearFormColorGrade();
            ModelVAORenderer.clearPaintEffectTransform();
            ModelVAORenderer.clearGlowEffectTransform();
            ModelVAORenderer.clearPaint();
            ModelVAORenderer.clearGlowing();
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    private void bindSoftLimbDrawState(SoftLimbDrawState draw)
    {
        if (draw.colorTransformActive)
        {
            ModelVAORenderer.setColorEffectTransform(new Matrix4f().identity(), draw.colorEffectTransform, draw.colorMaskHalf);
            ModelVAORenderer.setFormColorTint(draw.formColor.r, draw.formColor.g, draw.formColor.b, draw.formColor.a);
        }

        if (draw.gradeActive)
        {
            ModelVAORenderer.setFormColorGrade(draw.gradeBrightness, draw.gradeContrast, draw.gradeHue, draw.gradeSaturation);
            ModelVAORenderer.setGradeEffectTransforms(draw.gradeBrightnessTransform, draw.gradeContrastTransform, draw.gradeHueTransform, draw.gradeSaturationTransform);
        }

        if (draw.paintInMesh)
        {
            ModelVAORenderer.setPaintEffectTransform(new Matrix4f().identity(), draw.paintTransform, draw.paintMaskHalf);
            ModelVAORenderer.setPaint(draw.paint.r, draw.paint.g, draw.paint.b, draw.paintStrength);
        }
        else
        {
            ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
        }

        if (draw.hasGlow)
        {
            ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), draw.glowTransform, draw.glowMaskHalf);
            ModelVAORenderer.setGlow(draw.stripGlow ? draw.mainPassGlow : draw.glow, draw.glowColor.r, draw.glowColor.g, draw.glowColor.b, draw.legacyGlow);
        }
        else
        {
            ModelVAORenderer.clearGlowing();
        }

        if (draw.defaultTexture != null)
        {
            BBSModClient.getTextures().bindTexture(draw.defaultTexture);
        }
    }

    private void drawSoftLimbBones(List<SoftBoneSubmit> batch, SoftLimbDrawState draw, MatrixStack softStack, Supplier<ShaderProgram> softProgram, Matrix4f softPositionMatrix)
    {
        for (SoftBoneSubmit softSubmit : batch)
        {
            this.applyOnlySoftBoneVisible(draw.model, softSubmit.group);

            this.renderSoftTransparencyGeometry(softStack, softProgram, draw.model, softSubmit.group, draw.light, draw.overlay, draw.color, draw.defaultTexture, draw.textureBlend, draw.glow, draw.glowColor, draw.legacyGlow, draw.paint, draw.glowDeferred, softPositionMatrix);
        }
    }

    /**
     * Soft form / soft limb geometry only (call sites are soft-opacity paths).
     * With Iris: {@link BBSSettings#softTransparencyBackfaces} (default ON = backfaces).
     * Without shaders: {@code model.culling} (false = show backfaces).
     */
    private static boolean showSoftTransparencyBackfaces(ModelInstance model, ModelGroup group)
    {
        float boneAlpha = (group == null || group.color == null) ? 1F : group.color.a;

        if (boneAlpha >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA)
        {
            return model != null && !model.culling;
        }

        if (BBSRendering.isIrisShadersEnabled())
        {
            return BBSSettings.softTransparencyBackfaces == null || BBSSettings.softTransparencyBackfaces.get();
        }

        return model != null && !model.culling;
    }

    private void renderSoftTransparencyGeometry(MatrixStack stack, Supplier<ShaderProgram> program, ModelInstance model, int light, int overlay, Color color, Link defaultTexture, TextureBlend textureBlend, GlowSettings glow, Color glowColor, Color legacyGlow, Color paint, boolean glowDeferredToOverlay, Matrix4f positionMatrix)
    {
        this.renderSoftTransparencyGeometry(stack, program, model, null, light, overlay, color, defaultTexture, textureBlend, glow, glowColor, legacyGlow, paint, glowDeferredToOverlay, positionMatrix);
    }

    private void renderSoftTransparencyGeometry(MatrixStack stack, Supplier<ShaderProgram> program, ModelInstance model, ModelGroup group, int light, int overlay, Color color, Link defaultTexture, TextureBlend textureBlend, GlowSettings glow, Color glowColor, Color legacyGlow, Color paint, boolean glowDeferredToOverlay, Matrix4f positionMatrix)
    {
        if (showSoftTransparencyBackfaces(model, group))
        {
            this.renderSoftLimbGeometryTwoSided(stack, program, model, light, overlay, color, defaultTexture, textureBlend, glow, glowColor, legacyGlow, paint, glowDeferredToOverlay, positionMatrix);

            return;
        }

        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int savedCullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        int savedFrontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        Matrix4f facingMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());

        if (positionMatrix != null)
        {
            facingMatrix.mul(positionMatrix);
        }

        boolean flipWinding = facingMatrix.determinant() < 0F;

        RenderSystem.enableCull();
        GL11.glFrontFace(flipWinding ? GL11.GL_CW : GL11.GL_CCW);
        GL11.glCullFace(GL11.GL_BACK);

        try
        {
            this.renderModelGeometryWithEmission(stack, program, model, light, overlay, null, color, defaultTexture, textureBlend, glow, glowColor, legacyGlow, paint, glowDeferredToOverlay);
        }
        finally
        {
            GL11.glCullFace(savedCullFace);
            GL11.glFrontFace(savedFrontFace);

            if (cullWasEnabled)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }
        }
    }

    /**
     * Camera-relative length-squared for model-block / preview soft-bone sorting
     * (larger = farther). Uses bone mesh matrix × the draw root matrix.
     */
    private double softBoneDistanceSq(String boneId, Matrix4f rootMatrix, double fallbackDistanceSq)
    {
        Vector3f translation = this.softBoneTranslation(boneId, rootMatrix);

        if (translation == null)
        {
            return fallbackDistanceSq;
        }

        return translation.lengthSquared();
    }

    /**
     * Film ENTITY soft-bone sort key (larger = farther along the camera look axis).
     * Bone sits in absolute {@code renderContext.world} space; depth is
     * {@code (bone − camera) · look}. Falls back to Euclidean distance² if look is missing.
     * Avoids view ±z sign flips from model Y180 / relative draw stacks.
     */
    private double softBoneWorldDepthKey(String boneId, Matrix4f worldRoot, double cameraX, double cameraY, double cameraZ, Vector3f cameraLook, double fallbackDistanceSq)
    {
        Vector3f translation = this.softBoneTranslation(boneId, worldRoot);

        if (translation == null)
        {
            return fallbackDistanceSq;
        }

        double dx = translation.x - cameraX;
        double dy = translation.y - cameraY;
        double dz = translation.z - cameraZ;

        if (cameraLook != null && cameraLook.lengthSquared() > 1.0E-8F)
        {
            return dx * cameraLook.x + dy * cameraLook.y + dz * cameraLook.z;
        }

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Bone position after {@code root ×} mesh matrix (preferred) or joint origin.
     */
    private Vector3f softBoneTranslation(String boneId, Matrix4f rootMatrix)
    {
        if (boneId == null || rootMatrix == null)
        {
            return null;
        }

        MatrixCacheEntry entry = this.bones.get(boneId);

        if (entry == null)
        {
            return null;
        }

        /* Mesh matrix tracks drawn limb centers better than joint origins for adjacent cubes. */
        Matrix4f boneLocal = entry.matrix() != null ? entry.matrix() : entry.origin();

        if (boneLocal == null)
        {
            return null;
        }

        Matrix4f combined = new Matrix4f(rootMatrix).mul(boneLocal);
        Vector3f translation = new Vector3f();

        combined.getTranslation(translation);

        return translation;
    }

    private static final class SoftBoneSubmit
    {
        private final ModelGroup group;
        private final double distanceSq;
        private final boolean noshadingQueue;
        private final boolean irisCamera;

        private SoftBoneSubmit(ModelGroup group, double distanceSq, boolean noshadingQueue, boolean irisCamera)
        {
            this.group = group;
            this.distanceSq = distanceSq;
            this.noshadingQueue = noshadingQueue;
            this.irisCamera = irisCamera;
        }
    }

    private enum SoftLimbQueue
    {
        IMMEDIATE,
        NOSHADING,
        IRIS,
        BBS
    }

    private static final class SoftLimbDrawState
    {
        private IEntity target;
        private ModelInstance model;
        private float transition;
        private Pose pose;
        private Matrix4f baseTransform;
        private Matrix4f stackLocal;
        private Matrix4f stackBbs;
        private Matrix3f normalMatrix;
        private Supplier<ShaderProgram> irisProgram;
        private Supplier<ShaderProgram> bbsProgram;
        private Color color;
        private Link defaultTexture;
        private TextureBlend textureBlend;
        private GlowSettings glow;
        private Color glowColor;
        private Color legacyGlow;
        private Color paint;
        private boolean glowDeferred;
        private int light;
        private int overlay;
        private boolean colorTransformActive;
        private EffectTransform colorEffectTransform;
        private Vector3f colorMaskHalf;
        private Color formColor;
        private boolean gradeActive;
        private float gradeBrightness;
        private float gradeContrast;
        private float gradeHue;
        private float gradeSaturation;
        private EffectTransform gradeBrightnessTransform;
        private EffectTransform gradeContrastTransform;
        private EffectTransform gradeHueTransform;
        private EffectTransform gradeSaturationTransform;
        private boolean paintInMesh;
        private EffectTransform paintTransform;
        private Vector3f paintMaskHalf;
        private float paintStrength;
        private boolean hasGlow;
        private boolean stripGlow;
        private GlowSettings mainPassGlow;
        private EffectTransform glowTransform;
        private Vector3f glowMaskHalf;
        private boolean depthWrite;
        private boolean afterFluids;
    }

    private boolean hasAnyBoneNoshadingOpacity(ModelInstance model)
    {
        if (model == null || model.getModel() == null)
        {
            return false;
        }

        if (model.model instanceof BOBJModel bobj)
        {
            for (BOBJBone bone : bobj.getArmature().orderedBones)
            {
                if (bone.noshadingOpacity)
                {
                    return true;
                }
            }

            return false;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (group.noshadingOpacity)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether any pose bone currently has Color Grade adjustments (brightness / contrast /
     * hue / saturation), so the FormColorGrade shader path can run even when the form-level
     * Color Grade is neutral.
     */
    private boolean hasAnyBoneColorGrade(ModelInstance model)
    {
        if (model == null || model.getModel() == null)
        {
            return false;
        }

        if (model.model instanceof BOBJModel bobj)
        {
            for (BOBJBone bone : bobj.getArmature().orderedBones)
            {
                if (bone.color != null && bone.color.hasColorAdjustments())
                {
                    return true;
                }
            }

            return false;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (group.color != null && group.color.hasColorAdjustments())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether any pose bone has an active color spatial mask (shape / offset / scale / rotate).
     */
    private boolean hasAnyBoneColorTransform(ModelInstance model)
    {
        if (model == null || model.getModel() == null)
        {
            return false;
        }

        if (model.model instanceof BOBJModel bobj)
        {
            for (BOBJBone bone : bobj.getArmature().orderedBones)
            {
                if (bone.color != null && bone.color.hasActiveTransform())
                {
                    return true;
                }
            }

            return false;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (group.color != null && group.color.hasActiveTransform())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether any pose bone has an active glow spatial mask (shape / offset / scale / rotate).
     */
    private boolean hasAnyBoneGlowTransform(ModelInstance model)
    {
        if (model == null || model.getModel() == null)
        {
            return false;
        }

        if (model.model instanceof BOBJModel bobj)
        {
            for (BOBJBone bone : bobj.getArmature().orderedBones)
            {
                if (bone.glowingColor != null && bone.glowingColor.transform != null && bone.glowingColor.transform.isActive())
                {
                    return true;
                }
            }

            return false;
        }

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            if (group.glowingColor != null && group.glowingColor.transform != null && group.glowingColor.transform.isActive())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Form glow spatial mask: prefer {@link GlowSettings#transform}, fall back to legacy
     * {@code glowingColor.transform} (older UI / pose dual-write).
     */
    private EffectTransform resolveGlowEffectTransform(GlowSettings glow, Color legacyGlow)
    {
        if (glow != null && glow.transform != null && glow.transform.isActive())
        {
            return glow.transform;
        }

        if (legacyGlow != null && legacyGlow.hasActiveTransform())
        {
            return legacyGlow.transform;
        }

        if (glow != null && glow.transform != null)
        {
            return glow.transform;
        }

        if (legacyGlow != null && legacyGlow.transform != null)
        {
            return legacyGlow.transform;
        }

        return new EffectTransform();
    }

    /**
     * Whether glow intensity is active on the whole form or any bone.
     */
    private boolean hasAnyGlow(ModelInstance model)
    {
        Color legacyGlow = this.form.glowingColor.get();

        if (this.form.glowSettings.get().resolveIntensity(legacyGlow) != 0F)
        {
            return true;
        }

        if (model != null && model.getModel() != null)
        {
            for (ModelGroup group : model.getModel().getAllGroups())
            {
                if (group.glowIntensity != 0F)
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Whether the whole-form paint or any bone (model group) paint is currently active, which decides
     * if a deferred paint overlay pass should run after Iris world rendering.
     */
    private boolean hasAnyPaint(ModelInstance model)
    {
        PaintSettings paint = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();

        if (paint.resolveIntensity(legacyPaint) != 0F)
        {
            return true;
        }

        return this.hasBonePaint(model);
    }

    private boolean hasBonePaint(ModelInstance model)
    {
        if (model != null && model.getModel() != null)
        {
            if (model.model instanceof BOBJModel bobj)
            {
                for (BOBJBone bone : bobj.getArmature().orderedBones)
                {
                    if (bone.paintColor != null && bone.paintColor.a != 0F)
                    {
                        return true;
                    }
                }

                return false;
            }

            for (ModelGroup group : model.getModel().getAllGroups())
            {
                if (group.paintColor != null && group.paintColor.a != 0F)
                {
                    return true;
                }
            }
        }

        return false;
    }

    private void resetPostEquipmentRenderState()
    {
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private void renderArmor(IEntity target, MatrixStack stack, ArmorType type, ArmorSlot armorSlot, Color color, int overlay, int light)
    {
        Matrix4f matrix = this.bones.get(armorSlot.group.get()).matrix();

        if (matrix != null)
        {
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

            stack.push();
            MatrixStackUtils.multiply(stack, matrix);
            MatrixStackUtils.applyTransform(stack, armorSlot.transform);
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180F));

            CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());

            ActorEntityRenderer.armorRenderer.renderArmorSlot(stack, consumers, target, type.slot, type, light);
            consumers.draw();

            CustomVertexConsumerProvider.clearRunnables();

            stack.pop();

            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
        }
    }

    private void renderItems(IEntity target, ModelInstance model, MatrixStack stack, EquipmentSlot slot, ModelTransformationMode mode, List<ArmorSlot> items, ArmorSlot globalTransform, Color color, int overlay, int light)
    {
        ItemStack itemStack = target.getEquipmentStack(slot);

        if (itemStack != null && itemStack.isEmpty())
        {
            return;
        }

        Hand activeHand = target.getActiveHand();
        EquipmentSlot activeSlot = activeHand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;

        /* Vanilla keeps the arm posed to the eye while parenting the spyglass item to the
         * head (clamped pitch) — that mismatch is the “slide through the hand” look. */
        if (this.isActiveSpyglass(target, itemStack, slot, activeSlot)
            && this.renderSpyglassOnHead(target, model, stack, slot, itemStack, color, overlay, light))
        {
            return;
        }

        for (ArmorSlot armorSlot : items)
        {
            Matrix4f matrix = this.bones.get(armorSlot.group.get()).matrix();

            if (matrix != null)
            {
                CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

                stack.push();
                MatrixStackUtils.multiply(stack, matrix);
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90F));
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));
                stack.translate(0F, 0.125F, 0F);

                if (globalTransform != null)
                {
                    MatrixStackUtils.applyTransform(stack, globalTransform.transform);
                }

                MatrixStackUtils.applyTransform(stack, armorSlot.transform);

                LivingEntity itemEntity = slot == activeSlot
                    ? ItemUseRenderState.prepareProxy(target.getWorld(), target, slot, itemStack)
                    : null;

                CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());

                consumers.setSubstitute(BBSRendering.getColorConsumer(color));

                /* For some reason, due to Sodium and my color consumer, in some cases items like Trident,
                 * shield, etc. not get rendered, but if in another arm there is another item, it does render...
                 * So, I render a 0 size oak button to circumvent that bug! */
                if (model.model instanceof BOBJModel)
                {
                    stack.push();
                    stack.scale(0F, 0F, 0F);
                    MinecraftClient.getInstance().getItemRenderer().renderItem(null, new ItemStack(Items.OAK_BUTTON), mode, mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND, stack, consumers, target.getWorld(), light, overlay, 0);
                    consumers.draw();
                    stack.pop();
                }

                MinecraftClient.getInstance().getItemRenderer().renderItem(itemEntity, itemStack, mode, mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND, stack, consumers, target.getWorld(), light, overlay, 0);
                consumers.draw();
                consumers.setSubstitute(null);
                CustomVertexConsumerProvider.clearRunnables();

                stack.pop();

                RenderSystem.enableDepthTest();
            }
        }
    }

    private boolean isActiveSpyglass(IEntity target, ItemStack itemStack, EquipmentSlot slot, EquipmentSlot activeSlot)
    {
        return itemStack != null
            && itemStack.isOf(Items.SPYGLASS)
            && target.isUsingItem()
            && slot == activeSlot
            && target.getHandSwingProgress(0F) == 0F;
    }

    /**
     * Vanilla {@code HeadFeatureRenderer} equivalent for ModelForms: any non-armor item in
     * {@link EquipmentSlot#HEAD} (player/mob skulls, command-equipped blocks, etc.).
     */
    private void renderHeadSlotItem(IEntity target, ModelInstance model, MatrixStack stack, Color color, int overlay, int light)
    {
        ItemStack itemStack = target.getEquipmentStack(EquipmentSlot.HEAD);

        if (itemStack == null || itemStack.isEmpty())
        {
            return;
        }

        Item item = itemStack.getItem();

        if (item instanceof ArmorItem armorItem && armorItem.getSlotType() == EquipmentSlot.HEAD)
        {
            return;
        }

        Matrix4f matrix = this.bones.get(model.getHeadBone()).matrix();

        if (matrix == null)
        {
            ArmorSlot helmet = model.armorSlots.get(ArmorType.HELMET);

            if (helmet != null)
            {
                matrix = this.bones.get(helmet.group.get()).matrix();
            }
        }

        if (matrix == null)
        {
            return;
        }

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

        stack.push();
        MatrixStackUtils.multiply(stack, matrix);

        /* Skulls bypass ItemRenderer (Iris MixinItemRenderer); bake the same block/item IDs. */
        try (IrisArmorHooks.Scope ignored = IrisArmorHooks.beginEquippedItem(target, itemStack))
        {
            if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractSkullBlock skullBlock)
            {
                float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
                float animationProgress = this.resolveSkullAnimationProgress(target, tickDelta);

                BbsHeadItemSpace.applySkull(stack);
                this.renderSkullOnHead(itemStack, skullBlock, stack, consumers, color, light, animationProgress);
            }
            else
            {
                ModelTransformationMode mode = BbsHeadItemSpace.headItemTransformationMode();
                boolean leftHanded = BbsHeadItemSpace.headItemLeftHanded();
                LivingEntity itemEntity = ItemUseRenderState.prepareProxy(target.getWorld(), target, EquipmentSlot.HEAD, itemStack);

                BbsHeadItemSpace.applyHeadItem(stack);

                CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());
                consumers.setSubstitute(BBSRendering.getColorConsumer(color));

                if (model.model instanceof BOBJModel)
                {
                    stack.push();
                    stack.scale(0F, 0F, 0F);
                    MinecraftClient.getInstance().getItemRenderer().renderItem(null, new ItemStack(Items.OAK_BUTTON), mode, leftHanded, stack, consumers, target.getWorld(), light, overlay, 0);
                    consumers.draw();
                    stack.pop();
                }

                MinecraftClient.getInstance().getItemRenderer().renderItem(itemEntity, itemStack, mode, leftHanded, stack, consumers, target.getWorld(), light, overlay, 0);
                consumers.draw();
                consumers.setSubstitute(null);
                CustomVertexConsumerProvider.clearRunnables();
            }
        }

        stack.pop();
        RenderSystem.enableDepthTest();
    }

    /**
     * Same source as vanilla {@code HeadFeatureRenderer}: {@code LimbAnimator.getPos(tickDelta)},
     * preferring the vehicle's limbs when mounted on another living entity.
     */
    private float resolveSkullAnimationProgress(IEntity target, float tickDelta)
    {
        if (target instanceof MCEntity mc && mc.getMcEntity() instanceof LivingEntity living)
        {
            if (living.getVehicle() instanceof LivingEntity vehicle)
            {
                return vehicle.limbAnimator.getPos(tickDelta);
            }

            return living.limbAnimator.getPos(tickDelta);
        }

        return target.getLimbPos(tickDelta);
    }

    private void renderSkullOnHead(ItemStack itemStack, AbstractSkullBlock skullBlock, MatrixStack stack, CustomVertexConsumerProvider consumers, Color color, int light, float animationProgress)
    {
        SkullBlock.SkullType skullType = skullBlock.getSkullType();
        SkullBlockEntityModel skullModel = this.getSkullModels().get(skullType);

        if (skullModel == null)
        {
            return;
        }

        ProfileComponent profile = itemStack.get(DataComponentTypes.PROFILE);
        RenderLayer renderLayer = SkullBlockEntityRenderer.getRenderLayer(skullType, profile);

        CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());
        consumers.setSubstitute(BBSRendering.getColorConsumer(color));
        SkullBlockEntityRenderer.renderSkull(null, 180.0F, animationProgress, stack, consumers, light, skullModel, renderLayer);
        consumers.draw();
        consumers.setSubstitute(null);
        CustomVertexConsumerProvider.clearRunnables();
    }

    private Map<SkullBlock.SkullType, SkullBlockEntityModel> getSkullModels()
    {
        if (skullModels == null)
        {
            skullModels = SkullBlockEntityRenderer.getModels(MinecraftClient.getInstance().getEntityModelLoader());
        }

        return skullModels;
    }

    /**
     * Active spyglass on player ModelForms via {@link BbsHeadItemSpace} (BBS adaptation of
     * vanilla head + {@link ModelTransformationMode#HEAD}). Arm pose stays on
     * {@code ProceduralItemUsePoses.applySpyglass}.
     */
    private boolean renderSpyglassOnHead(IEntity target, ModelInstance model, MatrixStack stack, EquipmentSlot slot, ItemStack itemStack, Color color, int overlay, int light)
    {
        Matrix4f matrix = this.bones.get(model.getHeadBone()).matrix();

        if (matrix == null)
        {
            return false;
        }

        float transition = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
        float pitch = (float) Lerps.lerp(target.getPrevPitch(), target.getPitch(), transition);
        boolean leftArm = this.getArmForEquipmentSlot(target, slot) == Arm.LEFT;
        ModelTransformationMode mode = BbsHeadItemSpace.spyglassTransformationMode();
        boolean leftHanded = BbsHeadItemSpace.spyglassLeftHanded();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        LivingEntity itemEntity = ItemUseRenderState.prepareProxy(target.getWorld(), target, slot, itemStack);

        stack.push();
        MatrixStackUtils.multiply(stack, matrix);
        BbsHeadItemSpace.applySpyglass(stack, pitch, leftArm);

        CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());
        consumers.setSubstitute(BBSRendering.getColorConsumer(color));

        if (model.model instanceof BOBJModel)
        {
            stack.push();
            stack.scale(0F, 0F, 0F);
            MinecraftClient.getInstance().getItemRenderer().renderItem(null, new ItemStack(Items.OAK_BUTTON), mode, leftHanded, stack, consumers, target.getWorld(), light, overlay, 0);
            consumers.draw();
            stack.pop();
        }

        MinecraftClient.getInstance().getItemRenderer().renderItem(itemEntity, itemStack, mode, leftHanded, stack, consumers, target.getWorld(), light, overlay, 0);
        consumers.draw();
        consumers.setSubstitute(null);
        CustomVertexConsumerProvider.clearRunnables();

        stack.pop();
        RenderSystem.enableDepthTest();

        return true;
    }

    private Arm getArmForEquipmentSlot(IEntity target, EquipmentSlot slot)
    {
        Arm main = Arm.RIGHT;

        if (target instanceof MCEntity mc && mc.getMcEntity() instanceof LivingEntity living)
        {
            main = living.getMainArm();
        }

        if (slot == EquipmentSlot.MAINHAND)
        {
            return main;
        }

        return main.getOpposite();
    }

    @Override
    public boolean renderArm(MatrixStack matrices, int light, AbstractClientPlayerEntity player, Hand hand)
    {
        this.ensureAnimator(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            ArmorSlot slot = hand == Hand.MAIN_HAND ? model.fpMain : model.fpOffhand;

            if (slot == null)
            {
                return false;
            }

            Link link = this.form.texture.get();
            Link texture = link == null ? model.texture : link;
            Color color = Color.white();

            if (this.shouldBakeFormColor(model))
            {
                color.mul(this.resolveBakeFormColor(model, false));
            }
            else
            {
                Color storedFormColor = this.form.color.get();

                if (storedFormColor != null)
                {
                    color.a *= storedFormColor.a;
                }
            }

            this.form.applyFormOpacity(color);

            for (ModelGroup group : model.getModel().getAllGroups())
            {
                ModelGroup g = group;
                boolean visible = false;

                while (g != null)
                {
                    if (g.id.equals(slot.group.get()))
                    {
                        visible = true;

                        break;
                    }

                    g = g.parent;
                }

                group.visible = visible;
            }

            model.model.resetPose();

            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            MatrixStackUtils.applyTransform(matrices, slot.transform);

            this.applyPBRTextureIntensity();
            BBSModClient.getTextures().bindTexture(texture);
            this.clearPBRTextureIntensity();

            Supplier<ShaderProgram> mainShader = this.getModelShader(model);

            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();

            this.renderModel(this.entity, mainShader, matrices, model, light, OverlayTexture.DEFAULT_UV, color, false, null, 0F, true, null, null);

            for (ModelGroup group : model.getModel().getAllGroups())
            {
                group.visible = true;
            }

            matrices.pop();

            return true;
        }

        return super.renderArm(matrices, light, player, hand);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.ensureAnimator(context.getTransition());

        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            Link link = this.form.texture.get();
            Link texture = link == null ? model.texture : link;

            if (context.textureOverride != null)
            {
                texture = context.textureOverride;
            }

            Color color = new Color().set(context.color, true);

            if (this.shouldBakeFormColor(model))
            {
                color.mul(this.resolveBakeFormColor(model, false));
            }
            else
            {
                Color storedFormColor = this.form.color.get();

                if (storedFormColor != null)
                {
                    color.a *= storedFormColor.a;
                }
            }

            this.form.applyFormOpacity(color);
            model.model.resetPose();

            this.animator.applyActions(context.entity, model, context.getTransition());
            model.model.applyPose(this.getPose());

            context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            if (context.world != null)
            {
                context.world.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            }

            if (texture != null)
            {
                this.applyPBRTextureIntensity();
                BBSModClient.getTextures().bindTexture(texture);
                this.clearPBRTextureIntensity();
            }

            Supplier<ShaderProgram> mainShader = this.getModelShader(model);
            Supplier<ShaderProgram> shader = this.getShader(context, mainShader, BBSShaders::getPickerModelsProgram);

            FormColorEffects.applyShadowPassColorFix(color, this.form.color.get(), this.form.paintSettings.get(), this.form.paintColor.get(), context.isShadowPass || BBSRendering.isIrisShadowPass(), this.hasAnyPaint(model));

            /* Opacity 0: capture bones for body parts, skip albedo so shader path leaves no halo.
             * Form alpha only — a single transparent limb must not skip the whole model. */
            if (color.a <= 0.001F && !context.isShadowPass && !BBSRendering.isIrisShadowPass() && context.stencilMap == null)
            {
                this.captureMatrices(model);

                return;
            }

            boolean shadowPass = context.isShadowPass || BBSRendering.isIrisShadowPass();

            if (shadowPass)
            {
                ShaderOpacityPatch.beginShadowForm();
            }

            try
            {
                this.renderModel(context.entity, shader, context.stack, model, context.light, context.overlay, color, false, context.stencilMap, context.getTransition(), context.renderEquipment, context.world, context);
            }
            finally
            {
                if (shadowPass)
                {
                    ShaderOpacityPatch.endShadowForm();
                }
            }
        }
    }

    @Override
    protected void updateStencilMap(FormRenderingContext context)
    {
        ModelInstance model = this.getModel();

        if (model == null || model.model == null || context.stencilMap == null)
        {
            return;
        }

        model.fillStencilMap(context.stencilMap, this.form);

        /* After the bones, so the goal markers' ids fall right after theirs — clicking
         * a controller or pole handle then selects its (usually mesh-less) bone. Same
         * merged map as the visual pass, for the same reason. */
        MapType ikMap = LimbConstraintProcessor.resolveIkMap(model);

        if (ikMap != null && !ikMap.isEmpty())
        {
            ModelIKDebug.renderStencil(context.stack, model.model, ikMap, context.stencilMap, this.form);
        }

        MapType springsMap = DynamicBoneOrchestrator.resolveSpringsMap(model);

        if (springsMap != null && !springsMap.isEmpty())
        {
            ModelPhysicsDebug.renderStencil(context.stack, model.model, springsMap, context.stencilMap, this.form);
        }
    }

    private void captureMatrices(ModelInstance model)
    {
        this.bones.clear();
        model.captureMatrices(this.bones);
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        List<BodyPart> parts = this.getSortedBodyParts(context);

        if (parts.isEmpty())
        {
            return;
        }

        context.stack.push();
        if (context.world != null)
        {
            context.world.push();
        }

        try
        {
            this.renderBodyPartLayers(context, parts);
        }
        finally
        {
            this.bones.clear();
            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }
        }
    }

    private void renderBodyPartLayers(FormRenderingContext context, List<BodyPart> parts)
    {
        for (BodyPart part : parts)
        {
            this.renderBodyPartLayer(context, part);
        }
    }

    private void renderBodyPartLayer(FormRenderingContext context, BodyPart part)
    {
        Matrix4f matrix = this.bones.get(part.bone.get()).matrix();

        context.stack.push();
        if (context.world != null)
        {
            context.world.push();
        }

        try
        {
            if (matrix != null)
            {
                MatrixStackUtils.multiply(context.stack, matrix);
                if (context.world != null)
                {
                    MatrixStackUtils.multiply(context.world, matrix);
                }
            }
            else
            {
                context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                if (context.world != null)
                {
                    context.world.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                }
            }

            this.renderBodyPart(part, context);
        }
        finally
        {
            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }
        }
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        ModelInstance model = this.getModel();
        Matrix4f mm = new Matrix4f();
        Matrix4f oo = new Matrix4f();

        stack.push();
        this.applyTransforms(stack, true, transition);
        oo.set(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        mm.set(stack.peek().getPositionMatrix());

        matrices.put(prefix, mm, oo);

        /* Collect bones and add them to matrix list */
        if (this.animator != null && model != null)
        {
            model.model.resetPose();

            this.animator.applyActions(entity, model, transition);
            model.model.applyPose(this.getPose());

            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            this.captureMatrices(model);
        }

        for (Map.Entry<String, MatrixCacheEntry> entry : this.bones.entrySet())
        {
            Matrix4f matrix = new Matrix4f();
            Matrix4f o = new Matrix4f();

            stack.push();
            MatrixStackUtils.multiply(stack, entry.getValue().matrix());
            matrix.set(stack.peek().getPositionMatrix());
            stack.pop();

            stack.push();
            MatrixStackUtils.multiply(stack, entry.getValue().origin());
            o.set(stack.peek().getPositionMatrix());
            stack.pop();

            matrices.put(StringUtils.combinePaths(prefix, entry.getKey()), matrix, o);
        }

        int i = 0;

        /* Recursively do the same thing with body parts */
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                Matrix4f matrix = this.bones.get(part.bone.get()).matrix();

                stack.push();

                if (matrix != null)
                {
                    MatrixStackUtils.multiply(stack, matrix);
                }
                else
                {
                    stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                }

                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(part.useTarget.get() ? entity : part.getEntity(), stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.pop();
            }

            i += 1;
        }

        stack.pop();

        this.bones.clear();
    }

    @Override
    public void tick(IEntity entity)
    {
        int age = entity.getAge();

        /* Only restart when age seeks backward (timeline scrub). Forward jumps from
         * film setAge()+StubEntity.age++ must not wipe ActionPlayback or emoticons freeze. */
        if (this.lastAge != -1 && age < this.lastAge)
        {
            this.resetAnimator();
        }

        this.ensureAnimator(0F);

        if (this.animator != null)
        {
            this.animator.update(entity);
        }

        this.lastAge = age;
    }
}
