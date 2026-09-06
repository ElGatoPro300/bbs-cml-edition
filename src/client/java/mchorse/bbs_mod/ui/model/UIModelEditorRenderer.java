package mchorse.bbs_mod.ui.model;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.render.ItemRenderHelper;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ModelConfig;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoController;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoMatrixUtils;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoRayFrame;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoSurface;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Pose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;

public class UIModelEditorRenderer extends UIModelRenderer implements GizmoSurface
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public UIPropTransform transform;

    public enum ViewportMode
    {
        TEXTURED,
        WIREFRAME,
        XRAY
    }

    public ViewportMode viewportMode = ViewportMode.TEXTURED;

    private final GizmoController gizmoController = new GizmoController(this);

    private ModelForm form = new ModelForm();
    private ModelFormRenderer renderer;
    private ModelConfig config;
    private Consumer<String> callback;
    private Consumer<PickedCube> cubeCallback;
    private boolean pickingEnabled = true;
    private String selectedBone;
    private ModelCube selectedCube;
    private boolean dirty = true;

    private Function<Float, Matrix4f> formTransformGizmoOrigin;

    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();

    private ModelInstance previewModel;
    private String lastModelId;
    private final Matrix4f lastGizmoMatrix = new Matrix4f();
    private boolean hasGizmoMatrix;

    /** When true, trackball drag matches the film replay transform keyframe path. */
    private boolean formTransformGizmoDrag;

    private ArmorSlot fpHandPreviewSlot;
    private boolean fpHandPreviewMainHand;
    private final Map<ModelGroup, Boolean> savedGroupVisibility = new HashMap<>();
    private int savedPreviewDistance = -1;


    public UIModelEditorRenderer()
    {
        super();
        this.renderer = new ModelFormRenderer(this.form)
        {
            @Override
            public ModelInstance getModel()
            {
                return UIModelEditorRenderer.this.getModel();
            }
        };
    }

    public void setModel(String modelId)
    {
        this.form.model.set(modelId);
    }

    public void setConfig(ModelConfig config)
    {
        this.config = config;
        this.syncSolverConfig(config);
    }

    /**
     * Pushes limb IK / spring / joint-limit blobs from {@link ModelConfig}
     * onto the live preview {@link ModelForm} so solvers stay in sync while editing.
     */
    public void syncSolverConfig(ModelConfig config)
    {
        if (config == null)
        {
            return;
        }

        BaseType ik = config.ik.get();
        this.form.ik.set(ik == null ? null : ik.copy());

        BaseType springs = config.springs.get();
        this.form.springs.set(springs == null ? null : springs.copy());

        BaseType constraints = config.constraints.get();
        this.form.constraints.set(constraints == null ? null : constraints.copy());

        /* Use previewModel directly to avoid getModel() re-entry while the preview is being built. */
        if (this.previewModel != null)
        {
            this.previewModel.applyConfig((MapType) config.toData());
            this.previewModel.form = this.form;
            this.syncPreviewParts(this.previewModel);
        }
    }

    /**
     * Prefer the live {@link ModelConfig#parts} pose over a toData/fromData round-trip so
     * bone paint / glow / grade (and their transforms) stay in sync while editing.
     */
    private void syncPreviewParts(ModelInstance model)
    {
        if (model == null || this.config == null)
        {
            return;
        }

        if (model.parts == null)
        {
            model.parts = new Pose();
        }

        model.parts.copy(this.config.parts.get());
    }

    public void setCallback(Consumer<String> callback)
    {
        this.callback = callback;
    }

    public void setCubeCallback(Consumer<PickedCube> cubeCallback)
    {
        this.cubeCallback = cubeCallback;
    }

    public void setPickingEnabled(boolean pickingEnabled)
    {
        this.pickingEnabled = pickingEnabled;

        if (!pickingEnabled)
        {
            this.stencil.clearPicking();
        }
    }
    
    public void dirty()
    {
        this.dirty = true;
    }

    public void syncAnimationsAndResetAnimator()
    {
        this.syncAnimations();
    }

    public void syncAnimationsAndRefreshAnimator()
    {
        this.syncAnimations();

        if (this.previewModel != null)
        {
            this.renderer.ensureAnimator(0F);
            LOGGER.debug("Model editor animation sync: animator refreshed for model {}", this.previewModel.id);
        }
        else
        {
            LOGGER.debug("Model editor animation sync: preview model is null, animator refresh skipped");
        }

        this.dirty();
    }

    public ModelInstance getPreviewModelInstance()
    {
        return this.getModel();
    }

    public void invalidatePreviewModel()
    {
        this.deletePreview();
        this.dirty();
    }

    public void beginFpHandPreview(ArmorSlot slot, boolean mainHand)
    {
        this.fpHandPreviewSlot = slot;
        this.fpHandPreviewMainHand = mainHand;

        if (this.savedPreviewDistance < 0)
        {
            this.savedPreviewDistance = (int) this.distance.getX();
        }

        this.distance.setX(10);

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player != null)
        {
            this.entity.setEquipmentStack(EquipmentSlot.MAINHAND, player.getMainHandStack());
            this.entity.setEquipmentStack(EquipmentSlot.OFFHAND, player.getOffHandStack());
        }

        this.dirty();
    }

    public void endFpHandPreview()
    {
        this.fpHandPreviewSlot = null;
        this.restoreGroupVisibility();

        if (this.savedPreviewDistance >= 0)
        {
            this.distance.setX(this.savedPreviewDistance);
            this.savedPreviewDistance = -1;
        }

        this.dirty();
    }

    private void applyFpHandGroupVisibility(ModelInstance model, String groupId)
    {
        this.savedGroupVisibility.clear();

        for (ModelGroup group : model.getModel().getAllGroups())
        {
            this.savedGroupVisibility.put(group, group.visible);
            group.visible = this.isGroupOrDescendant(group, groupId);
        }
    }

    private void restoreGroupVisibility()
    {
        for (Map.Entry<ModelGroup, Boolean> entry : this.savedGroupVisibility.entrySet())
        {
            entry.getKey().visible = entry.getValue();
        }

        this.savedGroupVisibility.clear();
    }

    private boolean isGroupOrDescendant(ModelGroup group, String groupId)
    {
        ModelGroup current = group;

        while (current != null)
        {
            if (current.id.equals(groupId))
            {
                return true;
            }

            current = current.parent;
        }

        return false;
    }

    private void renderFpHandItem(UIContext context, MatrixCache matrixCache, MatrixStack stack)
    {
        String groupId = this.fpHandPreviewSlot.group.get();

        if (groupId.isEmpty())
        {
            return;
        }

        MatrixCacheEntry entry = matrixCache.get(groupId);

        if (entry == null)
        {
            return;
        }

        Matrix4f matrix = entry.matrix();

        if (matrix == null)
        {
            matrix = entry.origin();
        }

        if (matrix == null)
        {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            return;
        }

        ItemStack itemStack = this.fpHandPreviewMainHand ? player.getMainHandStack() : player.getOffHandStack();

        if (itemStack == null || itemStack.isEmpty())
        {
            return;
        }

        ItemDisplayContext mode = this.fpHandPreviewMainHand
            ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        int light = LightmapTextureManager.pack(15, 15);
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

        stack.push();
        MatrixStackUtils.multiply(stack, matrix);
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90F));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));
        MatrixStackUtils.applyTransform(stack, this.fpHandPreviewSlot.transform);

        consumers.setSubstitute(BBSRendering.getColorConsumer(new Color().set(Colors.WHITE)));
        ItemRenderHelper.renderItem(
            itemStack,
            mode,
            stack,
            light,
            OverlayTexture.DEFAULT_UV,
            this.entity.getWorld(),
            null,
            true
        );
        consumers.draw();
        consumers.setSubstitute(null);
        CustomVertexConsumerProvider.clearRunnables();
        stack.pop();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void ensureFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_form"));
        this.stencil.resizeGUI(this.area.w, this.area.h);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.ensureFramebuffer();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.pickingEnabled)
        {
            return super.subMouseClicked(context);
        }

        if (this.gizmoController.tryStartHandleDrag(context, this.transform))
        {
            return true;
        }

        PickedCube pickedCube = this.pickCubeAt(context);

        if (pickedCube != null && this.cubeCallback != null)
        {
            this.cubeCallback.accept(pickedCube);
            return true;
        }

        if (this.stencil.hasPicked())
        {
            Pair<Form, String> picked = this.stencil.getPicked();

            if (picked != null && picked.a != null && this.callback != null)
            {
                this.callback.accept(picked.b);
                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        Pair<Form, String> pendingPick = this.gizmoController.consumePendingTrackballClick();

        if (pendingPick != null && pendingPick.a != null && this.callback != null)
        {
            this.callback.accept(pendingPick.b);
        }

        this.gizmoController.stop();

        return super.subMouseReleased(context);
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.stencil;
    }

    public void setFormTransformGizmoOrigin(Function<Float, Matrix4f> origin)
    {
        this.formTransformGizmoOrigin = origin;
    }

    public void setSelectedBone(String bone)
    {
        this.selectedBone = bone;
    }

    public String getSelectedBone()
    {
        return this.selectedBone;
    }

    public void setSelectedCube(ModelCube cube)
    {
        this.selectedCube = cube;
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        this.updateModel();

        ModelInstance model = this.getModel();
        boolean fpHandPreview = this.fpHandPreviewSlot != null && model != null;
        String fpGroupId = fpHandPreview ? this.fpHandPreviewSlot.group.get() : null;
        MatrixStack stack = this.createCameraStack();

        if (fpHandPreview && fpGroupId != null && !fpGroupId.isEmpty())
        {
            this.applyFpHandGroupVisibility(model, fpGroupId);
            stack.push();
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            MatrixStackUtils.applyTransform(stack, this.fpHandPreviewSlot.transform);
        }

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, stack, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer();

        if (this.viewportMode == ViewportMode.XRAY)
        {
            GlStateManager._enableBlend();
            this.renderer.render(formContext);
        }
        else if (this.viewportMode == ViewportMode.TEXTURED)
        {
            this.renderer.render(formContext);
        }

        MatrixCache matrixCache = this.renderer.collectMatrices(this.entity, context.getTransition());

        if (this.viewportMode == ViewportMode.WIREFRAME || this.viewportMode == ViewportMode.XRAY)
        {
            this.renderAllCubesWireframe(context, matrixCache, 0.4F, 0.7F, 1F, 0.75F);
        }

        this.renderSelectedCubeVisualizer(context, matrixCache);

        if (fpHandPreview && fpGroupId != null && !fpGroupId.isEmpty())
        {
            this.renderFpHandItem(context, matrixCache, stack);
        }

        Matrix4f gizmoMatrix = this.resolveGizmoMatrix(context, matrixCache);
        this.hasGizmoMatrix = gizmoMatrix != null;

        if (gizmoMatrix != null)
        {
            stack.push();
            MatrixStackUtils.multiply(stack, gizmoMatrix);
            /* Full drawn MV (editor camera × bone/origin) — same space as film drag rays. */
            this.lastGizmoMatrix.set(stack.peek().getPositionMatrix());

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Gizmo.INSTANCE.render(stack);
            GL11.glEnable(GL11.GL_DEPTH_TEST);

            stack.pop();
        }

        if (this.area.isInside(context) && this.pickingEnabled)
        {
            if (this.stencil.getFramebuffer() == null)
            {
                this.ensureFramebuffer();
            }
            else
            {
                this.stencil.resizeGUI(this.area.w, this.area.h);
            }

            Texture fboTexture = this.stencil.getFramebuffer().getMainTexture();
            int fboW = fboTexture.width;
            int fboH = fboTexture.height;

            GlStateManager._disableScissorTest();

            this.stencilMap.setup();
            this.stencil.apply();

            this.beginStencilViewport(fboW, fboH);
            this.setupViewport(context);

            /* Restore depth writes: the visual pass (glow/paint/gizmos) may have left
             * depthMask false, which makes stencil picking prefer later-drawn bones. */
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);

            this.renderer.render(formContext.stencilMap(this.stencilMap));

            if (gizmoMatrix != null && Gizmo.isInteractive())
            {
                stack.push();
                MatrixStackUtils.multiply(stack, gizmoMatrix);

                GL11.glDisable(GL11.GL_DEPTH_TEST);
                Gizmo.INSTANCE.renderStencil(stack, this.stencilMap);
                GL11.glEnable(GL11.GL_DEPTH_TEST);

                stack.pop();
            }

            this.stencil.pickGUI(context, this.area);
            this.stencil.unbind(this.stencilMap);
            this.gizmoController.updateHover();

            this.endStencilViewport();

            BBSRendering.bindMainFramebuffer(true);

            GlStateManager._enableScissorTest();
        }
        else
        {
            this.stencil.clearPicking();
            this.gizmoController.updateHover();
        }

        if (fpHandPreview && fpGroupId != null && !fpGroupId.isEmpty())
        {
            stack.pop();
            this.restoreGroupVisibility();
        }

        this.setupViewport(context);
    }

    private Matrix4f resolveGizmoMatrix(UIContext context, MatrixCache matrixCache)
    {
        Matrix4f gizmoMatrix = null;

        if (UIBaseMenu.renderAxes && this.formTransformGizmoOrigin != null)
        {
            gizmoMatrix = this.formTransformGizmoOrigin.apply(context.getTransition());
        }
        else if (UIBaseMenu.renderAxes && this.selectedBone != null && !this.selectedBone.isEmpty())
        {
            if (this.selectedCube != null)
            {
                gizmoMatrix = this.getCubePivotMatrix(matrixCache);
            }
            else
            {
                MatrixCacheEntry entry = matrixCache.get(this.selectedBone);

                if (entry != null)
                {
                    gizmoMatrix = GizmoMatrixUtils.resolveFilmPoseBoneMatrix(entry, this.transform == null ? null : this.transform.getOrientation(),
                        ModelFormRenderer.isBobjModel(this.form));
                }
            }
        }

        if (gizmoMatrix == null)
        {
            return null;
        }

        return new Matrix4f(gizmoMatrix);
    }

    public void setFormTransformGizmoDrag(boolean formTransformGizmoDrag)
    {
        this.formTransformGizmoDrag = formTransformGizmoDrag;
    }

    @Override
    public void prepareGizmoDrag(UIPropTransform transform)
    {
        if (transform == null)
        {
            return;
        }

        if (this.formTransformGizmoDrag)
        {
            /* View-ring / Y / Z process bars need a flip with editor full-MV capture; X does not
             * (global invertRotationArcSweep was reversing the red ring only-wrong). */
            transform.setInvertGizmoViewRing(false);
            transform.setInvertGizmoTrackball(false);
            transform.setInvertFilmPoseGizmoAxes(false);
            transform.setFilmArcballTrackball(false);
            transform.clearTrackballEulerInverts();
            transform.setInvertTrackballDragY(true);
            transform.setInvertFilmArcballDragY(false);
            transform.setInvertRotationArcSweep(false);
            transform.setInvertRotationArcViewRing(true);
            transform.setInvertRotationArcY(true);
            transform.setInvertRotationArcZ(true);
            transform.configurePoseRingTuning(true);
            transform.setFilmMatchPoseTrackball(true);
            transform.setGizmoRayProvider(GizmoRayFrame.fromFilmStyle(
                this.camera,
                this.area,
                () -> this.hasGizmoMatrix ? this.lastGizmoMatrix : null
            ));

            return;
        }

        /* Nested model editor Pose (also opened from Model Block → Edit → Models button).
         * Match FilmPoseGizmoDrag pose signs so .bbs.json X/Z rings, Z translate, white ring
         * and arcball match BOBJ mouse sense. */
        boolean bobjModel = ModelFormRenderer.isBobjModel(this.form);

        transform.setModel(false);
        transform.configurePoseRingTuning(bobjModel);
        transform.setInvertGizmoViewRing(true);
        transform.setInvertGizmoTrackball(false);
        transform.setInvertFilmPoseGizmoAxes(false);
        transform.clearTrackballEulerInverts();

        if (bobjModel)
        {
            transform.invertModelPoseTrackballXZ();
        }

        transform.setInvertTrackballDragY(false);
        transform.setInvertFilmArcballDragY(false);
        transform.setInvertRotationArcSweep(false);
        transform.setInvertRotationArcY(false);
        transform.setInvertRotationArcViewRing(false);
        /* Skip filmArcball X/Z process-bar undo for Z only (same as form-editor pose). */
        transform.setInvertRotationArcZ(true);
        transform.setFilmArcballTrackball(true);
        transform.setFilmMatchPoseTrackball(false);
        transform.setForceFrozenRotationArc(false);
        transform.translationScale(bobjModel ? 1F : 16F);
        transform.setAxisProjectedTranslation(bobjModel);
        transform.setGizmoRayProvider(GizmoRayFrame.fromFilmStyle(
            this.camera,
            this.area,
            () -> this.hasGizmoMatrix ? this.lastGizmoMatrix : null
        ));
    }

    private void renderSelectedCubeVisualizer(UIContext context, MatrixCache cache)
    {
        if (this.selectedCube == null || this.selectedBone == null || this.selectedBone.isEmpty())
        {
            return;
        }

        ModelInstance instance = this.getPreviewModelInstance();

        if (instance == null || !(instance.model instanceof Model model))
        {
            return;
        }

        ModelGroup group = model.getGroup(this.selectedBone);

        if (group == null)
        {
            return;
        }

        Matrix4f cubeMatrix = this.getCubePivotMatrix(cache, group, this.selectedCube);
        Matrix4f uiMatrix = this.createCameraStack().peek().getPositionMatrix();

        if (cubeMatrix == null)
        {
            return;
        }

        MatrixStack cubeStack = new MatrixStack();

        MatrixStackUtils.multiply(cubeStack, cubeMatrix);
        CubicCubeRenderer.rotate(cubeStack, this.selectedCube.rotate);
        CubicCubeRenderer.moveBackFromPivot(cubeStack, this.selectedCube.pivot);

        cubeMatrix = new Matrix4f(cubeStack.peek().getPositionMatrix());

        if (this.selectedCube.quads.isEmpty())
        {
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (ModelQuad quad : this.selectedCube.quads)
        {
            if (quad.vertices.size() != 4)
            {
                continue;
            }

            for (int i = 0; i < 4; i++)
            {
                ModelVertex va = quad.vertices.get(i);
                ModelVertex vb = quad.vertices.get((i + 1) % 4);
                Vector3f a = new Vector3f(va.vertex);
                Vector3f b = new Vector3f(vb.vertex);

                cubeMatrix.transformPosition(a);
                cubeMatrix.transformPosition(b);

                this.line(builder, uiMatrix, a, b, 1F, 0.6F, 0F, 1F);
            }
        }

        Draw.flushLines(builder);
    }

    private void renderAllCubesWireframe(UIContext context, MatrixCache cache, float r, float g, float bl, float alpha)
    {
        ModelInstance instance = this.getPreviewModelInstance();

        if (instance == null || !(instance.model instanceof Model model))
        {
            return;
        }

        Matrix4f uiMatrix = this.createCameraStack().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        GlStateManager._enableBlend();
        BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (ModelGroup group : model.getAllGroups())
        {
            if (!group.visible)
            {
                continue;
            }

            for (ModelCube cube : group.cubes)
            {
                if (!cube.visible || cube.quads.isEmpty())
                {
                    continue;
                }

                Matrix4f cubeMatrix = this.getCubePivotMatrix(cache, group, cube);

                if (cubeMatrix == null)
                {
                    continue;
                }

                MatrixStack cubeStack = new MatrixStack();
                MatrixStackUtils.multiply(cubeStack, cubeMatrix);
                CubicCubeRenderer.rotate(cubeStack, cube.rotate);
                CubicCubeRenderer.moveBackFromPivot(cubeStack, cube.pivot);
                Matrix4f finalMatrix = new Matrix4f(cubeStack.peek().getPositionMatrix());

                for (ModelQuad quad : cube.quads)
                {
                    if (quad.vertices.size() != 4)
                    {
                        continue;
                    }

                    for (int i = 0; i < 4; i++)
                    {
                        ModelVertex va = quad.vertices.get(i);
                        ModelVertex vb = quad.vertices.get((i + 1) % 4);
                        Vector3f a = new Vector3f(va.vertex);
                        Vector3f b = new Vector3f(vb.vertex);

                        finalMatrix.transformPosition(a);
                        finalMatrix.transformPosition(b);

                        this.line(builder, uiMatrix, a, b, r, g, bl, alpha);
                    }
                }
            }
        }

        Draw.flushLines(builder);
    }

    public Matrix4f getCubePivotMatrix(MatrixCache cache)
    {
        if (this.selectedCube == null || this.selectedBone == null || this.selectedBone.isEmpty())
        {
            return null;
        }

        ModelInstance instance = this.getPreviewModelInstance();

        if (instance == null || !(instance.model instanceof Model model))
        {
            return null;
        }

        ModelGroup group = model.getGroup(this.selectedBone);

        return this.getCubePivotMatrix(cache, group, this.selectedCube);
    }

    public Matrix4f getCubePivotMatrix(MatrixCache cache, ModelGroup group, ModelCube cube)
    {
        if (cube == null || group == null)
        {
            return null;
        }

        MatrixStack cubeStack = new MatrixStack();
        MatrixCacheEntry rootEntry = cache.get("");
        Matrix4f rootMatrix = rootEntry == null ? null : rootEntry.matrix();

        if (rootMatrix != null)
        {
            MatrixStackUtils.multiply(cubeStack, rootMatrix);
        }

        cubeStack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));

        List<ModelGroup> chain = new ArrayList<>();

        for (ModelGroup cursor = group; cursor != null; cursor = cursor.parent)
        {
            chain.add(0, cursor);
        }

        for (ModelGroup element : chain)
        {
            ICubicRenderer.translateGroup(cubeStack, element);
            ICubicRenderer.moveToGroupPivot(cubeStack, element);
            ICubicRenderer.rotateGroup(cubeStack, element);
            ICubicRenderer.scaleGroup(cubeStack, element);
            ICubicRenderer.moveBackFromGroupPivot(cubeStack, element);
        }

        CubicCubeRenderer.moveToPivot(cubeStack, cube.pivot);

        return new Matrix4f(cubeStack.peek().getPositionMatrix());
    }

    public static class PickedCube
    {
        public final String groupId;
        public final int cubeIndex;
        public final ModelCube cube;

        public PickedCube(String groupId, int cubeIndex, ModelCube cube)
        {
            this.groupId = groupId;
            this.cubeIndex = cubeIndex;
            this.cube = cube;
        }
    }

    public PickedCube pickCubeAt(UIContext context)
    {
        ModelInstance instance = this.getPreviewModelInstance();

        if (instance == null || !(instance.model instanceof Model model))
        {
            return null;
        }

        Vector3f rayDir = this.camera.getMouseDirection(
            context.mouseX,
            context.mouseY,
            context.globalX(this.area.x),
            context.globalY(this.area.y),
            this.area.w,
            this.area.h
        );

        if (rayDir == null || rayDir.lengthSquared() < 1e-6F)
        {
            return null;
        }

        Vector3f rayOrigin = new Vector3f(
            (float) this.camera.position.x,
            (float) this.camera.position.y,
            (float) this.camera.position.z
        );

        MatrixCache cache = this.renderer.collectMatrices(this.entity, context.getTransition());
        PickedCube closest = null;
        float minDistance = Float.MAX_VALUE;

        for (ModelGroup group : model.getAllGroups())
        {
            if (!group.visible)
            {
                continue;
            }

            for (int i = 0; i < group.cubes.size(); i++)
            {
                ModelCube cube = group.cubes.get(i);

                if (!cube.visible)
                {
                    continue;
                }

                Matrix4f cubePivotMatrix = this.getCubePivotMatrix(cache, group, cube);

                if (cubePivotMatrix == null)
                {
                    continue;
                }

                MatrixStack cubeStack = new MatrixStack();

                MatrixStackUtils.multiply(cubeStack, cubePivotMatrix);
                CubicCubeRenderer.rotate(cubeStack, cube.rotate);
                CubicCubeRenderer.moveBackFromPivot(cubeStack, cube.pivot);

                Matrix4f worldMatrix = new Matrix4f(cubeStack.peek().getPositionMatrix());
                Matrix4f invMatrix = new Matrix4f(worldMatrix).invert();

                Vector3f localOrigin = new Vector3f((float) rayOrigin.x, (float) rayOrigin.y, (float) rayOrigin.z);
                Vector3f localDir = new Vector3f(rayDir);

                invMatrix.transformPosition(localOrigin);
                invMatrix.transformDirection(localDir).normalize();

                float minX = (cube.origin.x - cube.inflate) / 16F;
                float minY = (cube.origin.y - cube.inflate) / 16F;
                float minZ = (cube.origin.z - cube.inflate) / 16F;
                float maxX = (cube.origin.x + cube.size.x + cube.inflate) / 16F;
                float maxY = (cube.origin.y + cube.size.y + cube.inflate) / 16F;
                float maxZ = (cube.origin.z + cube.size.z + cube.inflate) / 16F;

                /* Ray-AABB intersection slab test */
                float t1 = (minX - localOrigin.x) / (localDir.x != 0 ? localDir.x : 1e-6F);
                float t2 = (maxX - localOrigin.x) / (localDir.x != 0 ? localDir.x : 1e-6F);
                float t3 = (minY - localOrigin.y) / (localDir.y != 0 ? localDir.y : 1e-6F);
                float t4 = (maxY - localOrigin.y) / (localDir.y != 0 ? localDir.y : 1e-6F);
                float t5 = (minZ - localOrigin.z) / (localDir.z != 0 ? localDir.z : 1e-6F);
                float t6 = (maxZ - localOrigin.z) / (localDir.z != 0 ? localDir.z : 1e-6F);

                float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
                float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

                if (tmax >= 0 && tmin <= tmax)
                {
                    float hitDist = tmin >= 0 ? tmin : tmax;

                    if (hitDist < minDistance)
                    {
                        minDistance = hitDist;
                        closest = new PickedCube(group.id, i, cube);
                    }
                }
            }
        }

        return closest;
    }

    private void line(BufferBuilder builder, Matrix4f matrix, Vector3f a, Vector3f b, float r, float g, float bl, float alpha)
    {
        builder.vertex(matrix, a.x, a.y, a.z).color(r, g, bl, alpha);
        builder.vertex(matrix, b.x, b.y, b.z).color(r, g, bl, alpha);
    }

    private int getBoneStencilId(String bone)
    {
        for (Map.Entry<Integer, Pair<Form, String>> entry : this.stencilMap.indexMap.entrySet())
        {
            if (entry.getValue().a == this.form && entry.getValue().b.equals(bone))
            {
                return entry.getKey();
            }
        }
        return 0;
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (!this.pickingEnabled || this.stencil.getFramebuffer() == null)
        {
            return;
        }

        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        int w = texture.width;
        int h = texture.height;

        GL11.glEnable(GL11.GL_BLEND);

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();

        context.batcher.drawPickerPreview(this.stencil.getColorView(), index, BBSSettings.modelEditorHoverHighlight(), this.area.x, this.area.y, this.area.w, this.area.h);

        Pair<Form, String> pair = this.stencil.getPicked();

        if (pair != null && pair.a != null && !pair.b.isEmpty())
        {
            String label = pair.a.getFormIdOrName() + " - " + pair.b;

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }
    
    private void updateModel()
    {
        if (this.config == null)
        {
            return;
        }

        this.syncAnimations();
        this.form.color.get().set(this.config.color.get());
        this.syncSolverConfig(this.config);

        if (!this.dirty)
        {
            return;
        }

        this.dirty = false;

        try
        {
            ModelInstance model = this.getModel();

            if (model != null)
            {
                boolean wasProcedural = model.procedural;

                model.applyConfig((MapType) this.config.toData());
                model.texture = this.config.texture.get();
                model.color = this.config.color.get();
                this.syncPreviewParts(model);
                this.syncSolverConfig(this.config);

                if (wasProcedural != model.procedural)
                {
                    this.renderer.resetAnimator();
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private ModelInstance getModel()
    {
        String modelId = this.form.model.get();

        if (modelId.isEmpty())
        {
            this.deletePreview();
            return null;
        }

        if (!modelId.equals(this.lastModelId) || this.previewModel == null)
        {
            ModelInstance globalModel = BBSModClient.getModels().getModel(modelId);

            if (globalModel != null)
            {
                this.deletePreview();

                this.previewModel = globalModel.copy();

                if (globalModel.model instanceof BOBJModel)
                {
                    /* BOBJModel.copy() already builds its own armature VAO. */
                }
                else if (globalModel.isVAORendered())
                {
                    this.previewModel.borrowVaosFrom(globalModel);
                }
                else
                {
                    this.previewModel.setup();
                }

                if (this.config != null)
                {
                    try
                    {
                        this.syncAnimations();
                        this.previewModel.applyConfig((MapType) this.config.toData());
                        this.previewModel.texture = this.config.texture.get();
                        this.previewModel.color = this.config.color.get();
                        this.syncPreviewParts(this.previewModel);
                        this.syncSolverConfig(this.config);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }

                this.lastModelId = modelId;
            }
        }

        return this.previewModel;
    }

    private void syncAnimations()
    {
        if (this.config == null)
        {
            LOGGER.debug("Model editor animation sync skipped: config is null");
            return;
        }

        ActionsConfig source = this.config.animations.get();
        ActionsConfig target = this.form.actions.get();

        if (!Objects.equals(target.geckoAnimations, source.geckoAnimations))
        {
            target.geckoAnimations.copy(source.geckoAnimations);
            LOGGER.debug(
                "Model editor animation sync applied: enabled={} limbs={}",
                target.geckoAnimations.enabled,
                target.geckoAnimations.limbs.size()
            );
        }
        else
        {
            LOGGER.debug(
                "Model editor animation sync skipped: no changes (enabled={} limbs={})",
                target.geckoAnimations.enabled,
                target.geckoAnimations.limbs.size()
            );
        }
    }

    private void deletePreview()
    {
        if (this.previewModel != null)
        {
            this.previewModel.delete();
            this.previewModel = null;
        }

        this.lastModelId = null;
    }
}
