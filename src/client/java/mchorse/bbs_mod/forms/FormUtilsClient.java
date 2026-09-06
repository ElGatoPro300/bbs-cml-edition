package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.FluidForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.ShapeForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.renderers.AnchorFormRenderer;
import mchorse.bbs_mod.forms.renderers.BillboardFormRenderer;
import mchorse.bbs_mod.forms.renderers.BlockFormRenderer;
import mchorse.bbs_mod.forms.renderers.ExtrudedFormRenderer;
import mchorse.bbs_mod.forms.renderers.FluidFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormIllusionRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FramebufferFormRenderer;
import mchorse.bbs_mod.forms.renderers.ItemFormRenderer;
import mchorse.bbs_mod.forms.renderers.LabelFormRenderer;
import mchorse.bbs_mod.forms.renderers.LightFormRenderer;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.ParticleFormRenderer;
import mchorse.bbs_mod.forms.renderers.ShapeFormRenderer;
import mchorse.bbs_mod.forms.renderers.StructureFormRenderer;
import mchorse.bbs_mod.forms.renderers.TrailFormRenderer;
import mchorse.bbs_mod.forms.renderers.VanillaParticleFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;

import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.TridentEntityRenderer;
import net.minecraft.client.render.model.ModelBaker;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Stack;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

public class FormUtilsClient
{
    /**
     * Bump when {@link #createIsolatedProvider()} layer order changes so cached
     * Immediates are rebuilt (trim must draw before armor glint for EQUAL depth).
     */
    private static final int PROVIDER_LAYER_LAYOUT = 2;
    private static int activeProviderLayerLayout = -1;

    private static Map<Class, IFormRendererFactory> map = new HashMap<>();
    private static CustomVertexConsumerProvider customVertexConsumerProvider;
    /** Isolated Immediate for MobForm morph draws (clothing / held items). */
    private static CustomVertexConsumerProvider mobMorphVertexConsumerProvider;
    private static Stack<Form> currentForm = new Stack<>();
    /** Guards against recursive illusion copies spawning more illusions. */
    private static int illusionDepth;
    private static final ThreadLocal<Boolean> UI_PREVIEW_ANIMATE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    static
    {
        register(BillboardForm.class, BillboardFormRenderer::new);
        register(FluidForm.class, FluidFormRenderer::new);
        register(ExtrudedForm.class, ExtrudedFormRenderer::new);
        register(LabelForm.class, LabelFormRenderer::new);
        register(ModelForm.class, ModelFormRenderer::new);
        register(ParticleForm.class, ParticleFormRenderer::new);
        register(BlockForm.class, BlockFormRenderer::new);
        register(ItemForm.class, ItemFormRenderer::new);
        register(AnchorForm.class, AnchorFormRenderer::new);
        register(MobForm.class, MobFormRenderer::new);
        register(VanillaParticleForm.class, VanillaParticleFormRenderer::new);
        register(TrailForm.class, TrailFormRenderer::new);
        register(FramebufferForm.class, FramebufferFormRenderer::new);
        register(StructureForm.class, StructureFormRenderer::new);
        register(ShapeForm.class, ShapeFormRenderer::new);
        register(LightForm.class, LightFormRenderer::new);
    }

    /**
     * Isolated Immediate for form/item/armor/block draws — same idea as original BBS.
     * <p>
     * Must NOT wrap {@code getEntityVertexConsumers()}: {@code draw()} would flush
     * pending vanilla entity layers (enchanted armor) while the form has the lightmap
     * off. Writing builtin meshes (trident) into the world Immediate instead makes
     * Iris draw a second, scaled copy. Pre-allocate entity/glint layers so
     * {@code ModelPart} can switch solid→glint without flushing mid-mesh.
     */
    public static CustomVertexConsumerProvider getProvider()
    {
        FormUtilsClient.ensureProviderLayout();

        if (customVertexConsumerProvider == null)
        {
            customVertexConsumerProvider = FormUtilsClient.createIsolatedProvider();
        }

        return customVertexConsumerProvider;
    }

    /**
     * Isolated Immediate for MobForm morph geometry (villager clothing, piglin body,
     * held items). Separate from {@link #getProvider()} so clothing flushes do not mix
     * with form-item batches.
     */
    public static CustomVertexConsumerProvider getMobMorphProvider()
    {
        FormUtilsClient.ensureProviderLayout();

        if (mobMorphVertexConsumerProvider == null)
        {
            mobMorphVertexConsumerProvider = FormUtilsClient.createIsolatedProvider();
        }

        return mobMorphVertexConsumerProvider;
    }

    private static void ensureProviderLayout()
    {
        if (activeProviderLayerLayout == PROVIDER_LAYER_LAYOUT)
        {
            return;
        }

        customVertexConsumerProvider = null;
        mobMorphVertexConsumerProvider = null;
        activeProviderLayerLayout = PROVIDER_LAYER_LAYOUT;
    }

    /**
     * Original BBS layer map, plus the glint layers vanilla keeps on the entity
     * Immediate and the trident solid layer (per-texture, not in the atlas map).
     * <p>
     * Armor trim atlas layers must come <b>before</b> {@link RenderLayer#getArmorEntityGlint()}:
     * glint uses equal-depth and only appears where trim/armor already wrote depth.
     */
    private static CustomVertexConsumerProvider createIsolatedProvider()
    {
        SequencedMap<RenderLayer, BufferAllocator> layers = Util.make(new Object2ObjectLinkedOpenHashMap<>(), map ->
        {
            map.put(TexturedRenderLayers.getEntitySolid(), new BufferAllocator(786432));
            map.put(TexturedRenderLayers.getEntityCutout(), new BufferAllocator(786432));
            map.put(TexturedRenderLayers.getBannerPatterns(), new BufferAllocator(786432));
            map.put(TexturedRenderLayers.getItemTranslucentCull(), new BufferAllocator(786432));
            FormUtilsClient.assignBuffer(map, RenderLayers.solid());
            FormUtilsClient.assignBuffer(map, RenderLayers.cutout());
            FormUtilsClient.assignBuffer(map, TexturedRenderLayers.getItemTranslucentCull());
            FormUtilsClient.assignBuffer(map, RenderLayers.translucentMovingBlock());
            FormUtilsClient.assignBuffer(map, TexturedRenderLayers.getShieldPatterns());
            FormUtilsClient.assignBuffer(map, TexturedRenderLayers.getBeds());
            FormUtilsClient.assignBuffer(map, TexturedRenderLayers.getShulkerBoxes());
            FormUtilsClient.assignBuffer(map, TexturedRenderLayers.getSign());
            FormUtilsClient.assignBuffer(map, TexturedRenderLayers.getHangingSign());
            map.put(TexturedRenderLayers.getChest(), new BufferAllocator(786432));
            /* Trim before glint — ArmorEntityGlint is EQUAL depth (vanilla BufferBuilderStorage
             * has no trim entry; our dual-shell trim must depth-write first). */
            FormUtilsClient.assignBuffer(map, TexturedRenderLayers.getArmorTrims(false));
            FormUtilsClient.assignBuffer(map, TexturedRenderLayers.getArmorTrims(true));
            FormUtilsClient.assignBuffer(map, RenderLayers.armorEntityGlint());
            FormUtilsClient.assignBuffer(map, RenderLayers.glint());
            FormUtilsClient.assignBuffer(map, RenderLayers.glintTranslucent());
            FormUtilsClient.assignBuffer(map, RenderLayers.entityGlint());
            FormUtilsClient.assignBuffer(map, RenderLayers.waterMask());
            FormUtilsClient.assignBuffer(map, RenderLayers.entitySolid(TridentEntityRenderer.TEXTURE));
        });

        return new CustomVertexConsumerProvider(
            VertexConsumerProvider.immediate(layers, new BufferAllocator(512 * 1024))
        );
    }

    private static void assignBuffer(SequencedMap<RenderLayer, BufferAllocator> storage, RenderLayer layer)
    {
        storage.put(layer, new BufferAllocator(layer.getExpectedBufferSize()));
    }

    /**
     * Trident/shield/skulls use {@code BuiltinModelItemRenderer} (entity ModelParts).
     * Those meshes tessellate on the world entity Immediate — same path as a vanilla
     * player. Do not {@code draw()} that Immediate from here (Iris would duplicate).
     */
    public static boolean usesBuiltinItemRenderer(ItemStack stack, ItemDisplayContext mode)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }

        return stack.isOf(Items.TRIDENT)
            || stack.isOf(Items.SHIELD)
            || stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractSkullBlock;
    }

    public static VertexConsumerProvider routeMobFormBuiltinItemConsumers(ItemStack stack, ItemDisplayContext mode, VertexConsumerProvider fallback)
    {
        if (fallback == null || !BBSRendering.isRenderingWorld() || BBSRendering.isIrisShadowPass())
        {
            return fallback;
        }

        if (!(getCurrentForm() instanceof MobForm))
        {
            return fallback;
        }

        if (!usesBuiltinItemRenderer(stack, mode))
        {
            return fallback;
        }

        return MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
    }

    public static boolean isCrumblingLayer(RenderLayer layer)
    {
        if (layer == null)
        {
            return false;
        }

        if (ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.contains(layer))
        {
            return true;
        }

        String name = layer.toString();

        if (name == null || name.isEmpty())
        {
            return false;
        }

        return name.toLowerCase().contains("crumbling");
    }

    public static boolean isMobFormEquipmentLayer(RenderLayer layer)
    {
        if (layer == null)
        {
            return false;
        }

        String name = layer.toString();

        if (name == null || name.isEmpty())
        {
            return false;
        }

        String lower = name.toLowerCase();

        return lower.contains("armor")
            || lower.contains("glint")
            || lower.contains("trident")
            || lower.contains("shield");
    }

    public static boolean shouldFlushMobFormFeatureLayers()
    {
        return getCurrentForm() instanceof MobForm
            && BBSRendering.isRenderingWorld()
            && !BBSRendering.isIrisShadowPass();
    }

    /**
     * Armor/clothing use per-texture layers that live in Immediate's fallback buffer.
     * Flush after the feature so a later throw (trident) cannot skip {@code draw()}
     * and drop the last armor piece.
     */
    public static void flushMobFormFeatureLayers(Object vertexConsumers)
    {
        if (!shouldFlushMobFormFeatureLayers() || vertexConsumers == null)
        {
            return;
        }

        BBSRendering.prepareVanillaEntityLighting();

        if (vertexConsumers instanceof CustomVertexConsumerProvider custom)
        {
            custom.drawCurrentLayer();
        }
        else if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate)
        {
            immediate.drawCurrentLayer();
        }
    }

    public static <T extends Form> void register(Class<T> clazz, IFormRendererFactory<T> function)
    {
        map.put(clazz, function);
    }

    public static Form getCurrentForm()
    {
        return currentForm.isEmpty() ? null : currentForm.peek();
    }

    public static FormRenderer getRenderer(Form form)
    {
        if (form == null)
        {
            return null;
        }

        if (form.getRenderer() instanceof FormRenderer renderer)
        {
            return renderer;
        }

        IFormRendererFactory factory = map.get(form.getClass());

        if (factory != null)
        {
            FormRenderer formRenderer = factory.create(form);

            form.setRenderer(formRenderer);

            return formRenderer;
        }

        return null;
    }

    public static boolean isUIPreviewAnimate()
    {
        return Boolean.TRUE.equals(UI_PREVIEW_ANIMATE.get());
    }

    public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        /* List / morph thumbnails default to a frozen pose (no idle). Pass true to animate. */
        renderUI(form, context, x1, y1, x2, y2, false);
    }

    public static void renderUI(Form form, UIContext context, int x1, int y1, int x2, int y2, boolean animate)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            UI_PREVIEW_ANIMATE.set(animate);

            try
            {
                context.batcher.flush();
                renderer.renderUI(context, x1, y1, x2, y2);
                context.batcher.flush();
            }
            finally
            {
                UI_PREVIEW_ANIMATE.set(Boolean.FALSE);
                BBSRendering.restoreGuiRenderState();
            }
        }
    }

    /**
     * Cached variant of {@link #renderUI} for list thumbnails and HUD overlays.
     * Always renders a static pose into the cache (mouse orbit still updates via angle buckets).
     */
    public static void renderUICached(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        FormUIPreviewCache.render(form, context, x1, y1, x2, y2, true);
    }

    /**
     * Cached thumbnail at a fixed orbit angle — for category cards that must not
     * refill on every mouse move.
     */
    public static void renderUICachedStatic(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        FormUIPreviewCache.render(form, context, x1, y1, x2, y2, false);
    }

    public static boolean is3D(Form form)
    {
        FormRenderer renderer = getRenderer(form);

        return renderer != null && renderer.is3D();
    }

    public static void render(Form form, FormRenderingContext context)
    {
        render(form, context, null);
    }

    /**
     * Renders a form and, at the outermost call, any configured illusions.
     * {@code extras} carries film-only delay hooks (replay property ticks).
     */
    public static void render(Form form, FormRenderingContext context, FormIllusionRenderer.Extras extras)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            currentForm.push(form);

            try
            {
                renderer.render(context);
            }
            catch (Exception e)
            {}

            currentForm.pop();

            if (illusionDepth == 0)
            {
                illusionDepth++;

                try
                {
                    FormIllusionRenderer.render(form, context, extras);
                }
                finally
                {
                    illusionDepth--;
                }
            }
        }
    }

    public static List<String> getBones(Form form)
    {
        FormRenderer renderer = getRenderer(form);

        if (renderer != null)
        {
            return renderer.getBones();
        }

        return Collections.emptyList();
    }

    public static interface IFormRendererFactory <T extends Form>
    {
        public FormRenderer<T> create(T form);
    }
}
