package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoController;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoMatrixUtils;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoRayFrame;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoSurface;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.function.Supplier;

public class UIPickableFormRenderer extends UIFormRenderer implements GizmoSurface
{
    public UIFormEditor formEditor;

    private boolean update;

    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();
    private final Matrix4f lastGizmoMatrix = new Matrix4f();
    private final Matrix4f unscaledGizmoMatrix = new Matrix4f();
    private boolean hasGizmoMatrix;

    private final GizmoController gizmoController = new GizmoController(this);

    private IEntity target;
    private Supplier<Boolean> renderForm;
    private Supplier<Boolean> renderFormMesh;
    /** True when the gizmo is dragging a ModelForm pose bone (not form General transform). */
    private boolean poseBoneGizmoDrag;

    public UIPickableFormRenderer(UIFormEditor formEditor)
    {
        this.formEditor = formEditor;
    }

    public void setPoseBoneGizmoDrag(boolean poseBoneGizmoDrag)
    {
        this.poseBoneGizmoDrag = poseBoneGizmoDrag;
    }

    public void updatable()
    {
        this.update = true;
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.stencil;
    }

    public GizmoController getGizmoController()
    {
        return this.gizmoController;
    }

    public void setRenderForm(Supplier<Boolean> renderForm)
    {
        this.renderForm = renderForm;
    }

    /**
     * Optional override for whether the form mesh itself is drawn in the UI preview.
     * When null, follows {@link #isPreviewVisible()}. Used by model-block F7 world
     * rendering so gizmos/picking can stay active without double-drawing the model.
     */
    public void setRenderFormMesh(Supplier<Boolean> renderFormMesh)
    {
        this.renderFormMesh = renderFormMesh;
    }

    private boolean isPreviewVisible()
    {
        return this.renderForm == null || this.renderForm.get();
    }

    private boolean shouldRenderFormMesh()
    {
        if (this.renderFormMesh != null)
        {
            return this.renderFormMesh.get();
        }

        return this.isPreviewVisible();
    }

    private void clearGizmoPickState()
    {
        this.stencil.clearPicking();
        this.gizmoController.updateHover();
        this.hasGizmoMatrix = false;
        Gizmo.INSTANCE.setHoveredIndex(-1);
    }

    public IEntity getTargetEntity()
    {
        return this.target == null ? this.entity : this.target;
    }

    public void setTarget(IEntity target)
    {
        this.target = target;
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
        if (this.formEditor.modelSettingsEditor != null && this.formEditor.modelSettingsEditor.isVisible())
        {
            return false;
        }

        if (this.formEditor.clickViewport(context, this.stencil))
        {
            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.formEditor.finishGizmoPendingClick();

        return super.subMouseReleased(context);
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        if (!this.isPreviewVisible())
        {
            this.clearGizmoPickState();

            return;
        }

        this.formEditor.preFormRender(context, this.form);

        IEntity previewEntity = this.target == null ? this.entity : this.target;
        int previewLight = BBSRendering.resolveEntityBlockLight(
            previewEntity, LightmapTextureManager.pack(15, 15));

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, previewEntity, this.createCameraStack(), previewLight, OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer()
            .equipment(BBSSettings.previewEquipment == null || BBSSettings.previewEquipment.get());

        boolean renderMesh = this.shouldRenderFormMesh();

        if (renderMesh)
        {
            FormUtilsClient.render(this.form, formContext);

            if (this.form.hitbox.get() && this.form.visible.get())
            {
                this.renderFormHitbox(context);
            }
        }

        if (this.area.w > 0 && this.area.h > 0)
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

            /* Restore depth writes so the closest bone along the cursor ray wins picking. */
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(GL11.GL_LEQUAL);
            GlStateManager._depthMask(true);

            FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));

            Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
            MatrixStack stack = this.createCameraStack();

            stack.push();

            if (matrix != null)
            {
                MatrixStackUtils.multiply(stack, matrix);
            }

            this.unscaledGizmoMatrix.set(stack.peek().getPositionMatrix());

            Matrix4f normalized = GizmoMatrixUtils.normalizeBasis(new Matrix4f(stack.peek().getPositionMatrix()));
            stack.peek().getPositionMatrix().set(normalized);

            if (Gizmo.isInteractive())
            {
                GlStateManager._disableCull();
                Gizmo.INSTANCE.renderStencil(stack, this.stencilMap);
                GlStateManager._enableCull();
            }

            stack.pop();

            if (this.area.isInside(context))
            {
                this.stencil.pickGUI(context, this.area);
            }
            else
            {
                this.stencil.clearPicking();
            }

            this.stencil.unbind(this.stencilMap);
            this.gizmoController.updateHover();

            this.endStencilViewport();

            GlStateManager._glBindFramebuffer(36160, 0);

            GlStateManager._enableScissorTest();
        }

        this.setupViewport(context);
        this.prepareGizmoRenderState();
        this.renderAxes(context);
    }

    private void prepareGizmoRenderState()
    {
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._disableBlend();
        GlStateManager._disableCull();
        // RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private void renderAxes(UIContext context)
    {
        Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
        MatrixStack stack = this.createCameraStack();
        this.hasGizmoMatrix = true;

        stack.push();

        if (matrix != null)
        {
            MatrixStackUtils.multiply(stack, matrix);
        }

        this.unscaledGizmoMatrix.set(stack.peek().getPositionMatrix());

        Matrix4f normalized = GizmoMatrixUtils.normalizeBasis(new Matrix4f(stack.peek().getPositionMatrix()));
        stack.peek().getPositionMatrix().set(normalized);

        /* Full drawn MV so drag matches film (view-space rays ↔ view-space gizmo). */
        this.lastGizmoMatrix.set(normalized);

        /* Draw axes */
        if (UIBaseMenu.renderAxes)
        {
            GlStateManager._disableCull();
            GlStateManager._disableDepthTest();
            Gizmo.INSTANCE.render(stack);
            GlStateManager._enableDepthTest();
            GlStateManager._enableCull();
        }

        stack.pop();
    }

    @Override
    public void prepareGizmoDrag(UIPropTransform transform)
    {
        if (transform == null)
        {
            return;
        }

        /* Model Block → Edit (and any form palette): pose bone gizmo must match Film Pose
         * signs for .bbs.json (Ry(180°) bone-local). General form transform keeps the
         * per-ring process-bar flips below. */
        if (this.poseBoneGizmoDrag)
        {
            boolean bobjModel = this.form instanceof ModelForm modelForm
                && ModelFormRenderer.isBobjModel(modelForm);

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
            transform.setFilmArcballTrackball(true);
            transform.setFilmMatchPoseTrackball(false);
            transform.setInvertRotationArcSweep(false);
            transform.setInvertRotationArcViewRing(false);
            transform.setInvertRotationArcY(false);
            /* Skip filmArcball X/Z process-bar undo for Z only (arc winds with value delta). */
            transform.setInvertRotationArcZ(true);
            transform.setForceFrozenRotationArc(false);
            transform.translationScale(bobjModel ? 1F : 16F);
            transform.setAxisProjectedTranslation(bobjModel);
            transform.setGizmoRayProvider(GizmoRayFrame.fromFilmStyle(
                this.camera,
                this.area,
                () -> this.hasGizmoMatrix ? this.unscaledGizmoMatrix : null
            ));

            return;
        }

        /* Same as model-editor General: per-ring process-bar flips (not global sweep — that
         * reversed the X ring incorrectly). */
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
            () -> this.hasGizmoMatrix ? this.unscaledGizmoMatrix : null
        ));
    }

    private void renderFormHitbox(UIContext context)
    {
        float hitboxW = this.form.hitboxWidth.get();
        float hitboxH = this.form.hitboxHeight.get();
        float eyeHeight = hitboxH * this.form.hitboxEyeHeight.get();

        MatrixStack stack = this.createCameraStack();

        /* Draw look vector */
        final float thickness = 0.01F;
        Draw.renderBox(stack, -thickness, -thickness + eyeHeight, -thickness, thickness, thickness, 2F, 1F, 0F, 0F);

        /* Draw hitbox */
        Draw.renderBox(stack, -hitboxW / 2, 0, -hitboxW / 2, hitboxW, hitboxH, hitboxW);
    }

    @Override
    protected void update()
    {
        super.update();

        /* Do not call form.update() here when model-block editing set a target.
         * That path shares the live Form with ModelBlockEntity, which already ticks it
         * each world tick (panel canPause=false). A second form.update() here ran
         * ParticleForm emitters at ~2x (~3–4x before the extra ITickable.tick was removed).
         * Vanilla particles looked closer to correct because MC ages them once per world
         * tick; custom emitters age on every form.update().
         *
         * Other editors leave target null and rely on Morph / film / owning systems for
         * shared forms — ticking here would double those clocks too. */
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (!this.isPreviewVisible() || this.stencil.getFramebuffer() == null)
        {
            return;
        }

        GlStateManager._enableBlend();

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPicked();
        int w = texture.width;
        int h = texture.height;

        context.batcher.drawPickerPreview(this.stencil.getColorView(), index, BBSSettings.modelEditorHoverHighlight(), this.area.x, this.area.y, this.area.w, this.area.h);

        if (pair != null && pair.a != null)
        {
            String label = pair.a.getFormIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        /* Hide the preview grid when only gizmos/picking run over world rendering. */
        if (this.isPreviewVisible() && this.shouldRenderFormMesh())
        {
            super.renderGrid(context);
        }
    }
}
