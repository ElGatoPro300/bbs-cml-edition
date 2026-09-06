package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.FormLightingRender;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.List;

/**
 * Fast path for many identical {@link ItemForm} body parts.
 * Applies the same transforms as the normal pipeline, but reuses one baked model
 * and flushes GPU buffers once instead of once per part.
 */
public final class ItemBodyPartBatch
{
    private static boolean active;
    private static boolean deferFlush;
    private static BakedModel cachedModel;
    private static final Transform SCRATCH_TRANSFORM = new Transform();

    private ItemBodyPartBatch()
    {}

    public static boolean isActive()
    {
        return active;
    }

    public static boolean isDeferringFlush()
    {
        return deferFlush;
    }

    public static BakedModel getCachedModel()
    {
        return cachedModel;
    }

    public static boolean renderBodyParts(FormRenderer parent, List<BodyPart> parts, FormRenderingContext context)
    {
        ItemForm template = findHomogeneousTemplate(parts);

        if (template == null || hasOverlayBodyParts(parts) || context.stencilMap != null || context.isPicking())
        {
            return false;
        }

        FormRenderer templateRenderer = FormUtilsClient.getRenderer(template);

        if (!(templateRenderer instanceof ItemFormRenderer itemRenderer))
        {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack itemStack = template.stack.get();
        World world = context.entity != null && context.entity.getWorld() != null
            ? context.entity.getWorld()
            : client.world;
        BakedModel bakedModel = client.getItemRenderer().getModels().getModel(itemStack);

        if (bakedModel != null)
        {
            ClientWorld clientWorld = world instanceof ClientWorld typed ? typed : null;

            bakedModel = bakedModel.getOverrides().apply(bakedModel, itemStack, clientWorld, null, 0);
        }

        if (bakedModel == null)
        {
            return false;
        }

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        boolean flushOnce = context.stencilMap == null;
        boolean isDropped = context.type == FormRenderType.ITEM;
        boolean useDroppedMode = itemRenderer.shouldUseDroppedMode(isDropped);
        ModelTransformationMode mode = itemRenderer.getRenderMode(useDroppedMode);
        boolean leftHand = mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND;

        PaintSettings paintSettings = template.paintSettings.get();
        Color resolvedPaint = FormColorEffects.resolvePaintColor(paintSettings, template.paintColor.get());

        active = true;
        deferFlush = flushOnce;
        cachedModel = bakedModel;

        if (flushOnce)
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            });
        }

        IEntity oldEntity = context.entity;
        int savedLight = context.light;

        try
        {
            for (BodyPart part : parts)
            {
                Form form = part.getForm();

                if (!(form instanceof ItemForm item) || !item.render.get())
                {
                    continue;
                }

                item.applyStates(context.transition);

                if (!item.visible.get())
                {
                    item.unapplyStates();

                    continue;
                }

                context.entity = part.useTarget.get() ? oldEntity : part.getEntity();
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

                    applyFormTransform(item, context);
                    applyLighting(item, context);

                    if (useDroppedMode)
                    {
                        itemRenderer.applyDroppedAnimation(context, useDroppedMode);
                    }

                    BlockFormRenderer.color.set(1F, 1F, 1F, 1F);
                    BlockFormRenderer.color.mul(context.color);
                    BlockFormRenderer.color.mul(item.color.get());

                    consumers.setSubstitute(itemRenderer.getMainConsumer(BlockFormRenderer.color, resolvedPaint));
                    client.getItemRenderer().renderItem(itemStack, mode, leftHand, context.stack, consumers, context.light, context.overlay, bakedModel);

                    if (context.isPicking())
                    {
                        context.stencilMap.addPicking(item);
                    }
                }
                finally
                {
                    context.stack.pop();

                    if (context.world != null)
                    {
                        context.world.pop();
                    }

                    context.light = savedLight;
                    item.unapplyStates();
                }
            }
        }
        finally
        {
            context.entity = oldEntity;
            context.light = savedLight;
            consumers.setSubstitute(null);

            if (flushOnce)
            {
                consumers.draw();
                CustomVertexConsumerProvider.clearRunnables();
                RenderSystem.defaultBlendFunc();
            }

            active = false;
            deferFlush = false;
            cachedModel = null;
        }

        RenderSystem.enableDepthTest();

        return true;
    }

    private static void applyFormTransform(ItemForm item, FormRenderingContext context)
    {
        SCRATCH_TRANSFORM.copy(item.transform.get());
        applyOverlay(SCRATCH_TRANSFORM, item.transformOverlay.get());

        for (ValueTransform extra : item.additionalTransforms)
        {
            applyOverlay(SCRATCH_TRANSFORM, extra.get());
        }

        MatrixStackUtils.applyTransform(context.stack, SCRATCH_TRANSFORM);

        if (context.world != null)
        {
            MatrixStackUtils.applyTransform(context.world, SCRATCH_TRANSFORM);
        }
    }

    private static void applyOverlay(Transform transform, Transform overlay)
    {
        transform.translate.add(overlay.translate);
        transform.scale.add(overlay.scale).sub(1, 1, 1);
        transform.rotate.add(overlay.rotate);
        transform.rotate2.add(overlay.rotate2);
        transform.pivot.add(overlay.pivot);
    }

    private static void applyLighting(ItemForm item, FormRenderingContext context)
    {
        context.light = FormLightingRender.apply(context.light, item.lightingSettings, item.lighting.get());
    }

    private static boolean hasOverlayBodyParts(List<BodyPart> parts)
    {
        for (BodyPart part : parts)
        {
            Form form = part.getForm();

            if (form == null || !form.render.get() || !form.visible.get())
            {
                continue;
            }

            if (!(form instanceof ItemForm item))
            {
                return true;
            }

            if (FormColorEffects.hasPositivePaint(item.paintSettings.get(), item.paintColor.get()))
            {
                return true;
            }

            GlowSettings glowSettings = item.glowSettings.get();
            float glowIntensity = glowSettings.resolveIntensity(item.glowingColor.get());

            if (glowIntensity > 0F && !glowSettings.resolvePaintOnly())
            {
                return true;
            }

            if (BBSRendering.needsIrisNoshadingOpacityDeferral(item.color.get().a, item.noshadingOpacity.get()))
            {
                return true;
            }
        }

        return false;
    }

    private static ItemForm findHomogeneousTemplate(List<BodyPart> parts)
    {
        ItemForm template = null;

        for (BodyPart part : parts)
        {
            Form form = part.getForm();

            if (form == null || !form.render.get() || !form.visible.get())
            {
                continue;
            }

            if (!(form instanceof ItemForm item))
            {
                return null;
            }

            if (template == null)
            {
                template = item;
            }
            else if (!isCompatible(template, item))
            {
                return null;
            }
        }

        return template;
    }

    private static boolean isCompatible(ItemForm a, ItemForm b)
    {
        if (!ItemStack.areEqual(a.stack.get(), b.stack.get()))
        {
            return false;
        }

        if (a.modelTransform.get() != b.modelTransform.get())
        {
            return false;
        }

        if (a.sameAnimationWhenDropped.get() != b.sameAnimationWhenDropped.get())
        {
            return false;
        }

        if (a.usingItem.get() != 0D || b.usingItem.get() != 0D)
        {
            return false;
        }

        if (a.itemUseTime.get() != 0D || b.itemUseTime.get() != 0D)
        {
            return false;
        }

        Color resolvedPaintA = FormColorEffects.resolvePaintColor(a.paintSettings.get(), a.paintColor.get());
        Color resolvedPaintB = FormColorEffects.resolvePaintColor(b.paintSettings.get(), b.paintColor.get());

        if (resolvedPaintA.a < 0F || resolvedPaintB.a < 0F)
        {
            return false;
        }

        float glowIntensityA = a.glowSettings.get().resolveIntensity(a.glowingColor.get());
        float glowIntensityB = b.glowSettings.get().resolveIntensity(b.glowingColor.get());

        return glowIntensityA == glowIntensityB && glowIntensityA >= 0F;
    }
}
