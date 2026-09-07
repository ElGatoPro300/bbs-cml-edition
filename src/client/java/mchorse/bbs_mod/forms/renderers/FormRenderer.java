package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormShake;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.FormLightingRender;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;

import org.joml.Matrix4f;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class FormRenderer <T extends Form>
{
    private static boolean suppressFormDisplayName;

    public static void setSuppressFormDisplayName(boolean suppress)
    {
        suppressFormDisplayName = suppress;
    }

    protected T form;
    private float animTime = 0F;

    public FormRenderer(T form)
    {
        this.form = form;
    }

    public T getForm()
    {
        return this.form;
    }

    public List<String> getBones()
    {
        return Collections.emptyList();
    }

    public final void renderUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        try
        {
            this.renderInUI(context, x1, y1, x2, y2);
        }
        finally
        {
            /* Soft GUI restore only — never unbind VAO/EBO here. Doing so blanks Batcher2D
             * chrome and can null-deref in atio6axx on the next glDrawElements. */
            BBSRendering.restoreGuiRenderState();
        }

        context.batcher.flush();
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        FontRenderer font = context.batcher.getFont();
        String name = this.form.name.get();

        if (!suppressFormDisplayName && !name.isEmpty())
        {
            name = font.limitToWidth(name, x2 - x1 - 3);

            int w = font.getWidth(name);

            context.batcher.textCard(name, (x2 + x1 - w) / 2, y1 + 6, Colors.WHITE, Colors.ACTIVE | Colors.A50);
        }

        int keybind = this.form.hotkey.get();

        if (keybind > 0)
        {
            name = KeyCodes.getName(keybind);
            name = font.limitToWidth(name, x2 - x1 - 3);

            int w = font.getWidth(name);

            context.batcher.textCard(name, (x2 + x1 - w) / 2, y2 - 6 - font.getHeight(), Colors.WHITE, Colors.A50);
        }
    }

    protected abstract void renderInUI(UIContext context, int x1, int y1, int x2, int y2);

    public boolean renderArm(MatrixStack matrices, int light, AbstractClientPlayerEntity player, Hand hand)
    {
        return false;
    }

    public final void render(FormRenderingContext context)
    {
        this.animTime = context.entity != null ? context.entity.getAge() + context.transition : context.transition;

        /* Transparent forms skip casting via opacity / vertex alpha in the shadow path.
         * Color-track paint/blend/grade must not disable Form.shaderShadow. */
        if (!this.form.shaderShadow.get() && BBSRendering.isIrisShadowPass())
        {
            return;
        }

        if (!this.form.render.get())
        {
            return;
        }

        this.form.applyStates(context.transition);

        if (!this.form.visible.get())
        {
            this.form.unapplyStates();

            return;
        }

        int light = context.light;
        int savedColor = context.color;
        boolean isPicking = context.stencilMap != null;

        context.stack.push();
        if (context.world != null)
        {
            context.world.push();
        }

        try
        {
            this.applyTransforms(context.stack, false, context.getTransition());
            if (context.world != null)
            {
                this.applyTransforms(context.world, false, context.getTransition());
            }

            context.light = FormLightingRender.apply(context.light, this.form.lightingSettings, this.form.lighting.get());

            this.render3D(context);

            if (isPicking)
            {
                this.updateStencilMap(context);
            }

            this.renderBodyParts(context);
        }
        finally
        {
            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }

            context.light = light;
            context.color = savedColor;

            this.form.unapplyStates();
        }
    }

    protected void applyTransforms(MatrixStack stack, boolean origin, float transition)
    {
        Transform transform = this.createTransform();

        if (origin)
        {
            stack.translate(transform.translate.x, transform.translate.y, transform.translate.z);
        }
        else
        {
            MatrixStackUtils.applyTransform(stack, transform);
        }
    }

    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        matrix.mul(this.createTransform().createMatrix());
    }

    protected Transform createTransform()
    {
        Transform transform = new Transform();

        transform.copy(this.form.transform.get());
        this.applyTransform(transform, this.form.transformOverlay.get());

        for (ValueTransform t : this.form.additionalTransforms)
        {
            this.applyTransform(transform, t.get());
        }

        FormShake.apply(transform, this.form, this.animTime);

        return transform;
    }

    private void applyTransform(Transform transform, Transform overlay)
    {
        transform.translate.add(overlay.translate);
        transform.scale.add(overlay.scale).sub(1, 1, 1);
        transform.rotate.add(overlay.rotate);
        transform.rotate2.add(overlay.rotate2);
        transform.pivot.add(overlay.pivot);
    }

    protected Supplier<ShaderProgram> getShader(FormRenderingContext context, Supplier<ShaderProgram> normal, Supplier<ShaderProgram> picking)
    {
        if (context.isPicking())
        {
            ShaderProgram program = picking.get();

            if (program == null)
            {
                return normal;
            }

            this.setupTarget(context, program);

            return () -> program;
        }

        return normal;
    }

    protected void setupTarget(FormRenderingContext context, ShaderProgram program)
    {
        if (program == null)
        {
            return;
        }

        GlUniform target = program.getUniform("Target");

        if (target != null)
        {
            int pickingIndex = context.getPickingIndex();

            target.set(pickingIndex);
        }
    }

    protected void updateStencilMap(FormRenderingContext context)
    {
        context.stencilMap.addPicking(this.form);
    }

    protected void render3D(FormRenderingContext context)
    {}

    public void renderBodyParts(FormRenderingContext context)
    {
        if (this.form.parts.getAllTyped().isEmpty())
        {
            return;
        }

        List<BodyPart> parts = this.getSortedBodyParts(context);

        if (ItemBodyPartBatch.renderBodyParts(this, parts, context))
        {
            return;
        }

        for (BodyPart part : parts)
        {
            this.renderBodyPart(part, context);
        }
    }

    protected List<BodyPart> getSortedBodyParts(FormRenderingContext context)
    {
        return new ArrayList<>(this.form.parts.getAllTyped());
    }

    protected void renderBodyPart(BodyPart part, FormRenderingContext context)
    {
        IEntity oldEntity = context.entity;

        context.entity = part.useTarget.get() ? oldEntity : part.getEntity();

        if (part.getForm() != null)
        {
            context.stack.push();

            if (context.world != null)
            {
                context.world.push();
            }

            try
            {
                MatrixStackUtils.applyTransform(context.stack, part.transform.get());

                if (context.world != null)
                {
                    MatrixStackUtils.applyTransform(context.world, part.transform.get());
                }

                FormUtilsClient.render(part.getForm(), context);
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

        context.entity = oldEntity;
    }

    public MatrixCache collectMatrices(IEntity entity, float transition)
    {
        MatrixCache map = new MatrixCache();
        MatrixStack stack = new MatrixStack();

        this.collectMatrices(entity, stack, map, "", transition);

        return map;
    }

    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
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

        int i = 0;

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                stack.push();
                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.pop();
            }

            i += 1;
        }

        stack.pop();
    }
}
