package mchorse.bbs_mod.cubic.render.vanilla;

import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.IrisArmorHooks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import com.google.common.collect.Maps;

import java.util.Map;

public class ArmorRenderer
{
    private static final Map<String, Identifier> ARMOR_TEXTURE_CACHE = Maps.newHashMap();
    private static final Identifier ELYTRA_TEXTURE = Identifier.of("minecraft", "textures/entity/elytra.png");
    /** Outward shell — avoids coplanar z-fight with armor in film/world cameras (044b2f4a6). */
    private static final float TRIM_OUTER_SCALE = 1.005F;
    /** Inward shell — uniform outer scale alone hides trim on inner armor faces. */
    private static final float TRIM_INNER_SCALE = 0.995F;
    private final BipedEntityModel innerModel;
    private final BipedEntityModel outerModel;
    private final ElytraEntityModel elytraModel;
    private final SpriteAtlasTexture armorTrimsAtlas;

    public ArmorRenderer(BipedEntityModel innerModel, BipedEntityModel outerModel, ElytraEntityModel elytraModel, BakedModelManager bakery)
    {
        this.innerModel = innerModel;
        this.outerModel = outerModel;
        this.elytraModel = elytraModel;
        this.armorTrimsAtlas = bakery.getAtlas(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE);
    }

    public void renderArmorSlot(MatrixStack matrices, VertexConsumerProvider vertexConsumers, IEntity entity, EquipmentSlot armorSlot, ArmorType type, int light)
    {
        ItemStack itemStack = entity.getEquipmentStack(armorSlot);
        Item item = itemStack.getItem();
        VertexConsumerProvider buffers = IrisArmorHooks.wrapEntityBuffers(vertexConsumers);

        try (IrisArmorHooks.Scope ignored = IrisArmorHooks.beginArmorPiece(entity, item))
        {
            this.renderArmorSlotInner(matrices, buffers, entity, armorSlot, type, light, itemStack, item);
        }
    }

    private void renderArmorSlotInner(MatrixStack matrices, VertexConsumerProvider vertexConsumers, IEntity entity, EquipmentSlot armorSlot, ArmorType type, int light, ItemStack itemStack, Item item)
    {
        if (item instanceof ElytraItem || itemStack.isOf(Items.ELYTRA))
        {
            if (type == ArmorType.CHEST && this.elytraModel != null)
            {
                matrices.push();
                /* Position Elytra at shoulder height and scale to fit back properly like Minecraft Vanilla */
                matrices.translate(0F, -1.5F, 0.125F);
                matrices.scale(2F, 2F, 2F);

                this.elytraModel.leftWing.pivotX = 5.0F;
                this.elytraModel.leftWing.pivotY = 0.0F;
                this.elytraModel.leftWing.pivotZ = 0.0F;

                this.elytraModel.rightWing.pivotX = -5.0F;
                this.elytraModel.rightWing.pivotY = 0.0F;
                this.elytraModel.rightWing.pivotZ = 0.0F;

                float transition = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
                float flyProgress = entity != null ? entity.getFallFlyingProgress(transition) : 0F;

                this.elytraModel.leftWing.pitch = MathHelper.lerp(flyProgress, 0.2617994F, 0.35F);
                this.elytraModel.leftWing.yaw = MathHelper.lerp(flyProgress, -0.015F, -0.1F);
                this.elytraModel.leftWing.roll = MathHelper.lerp(flyProgress, -0.29F, -1.55F);
                this.elytraModel.rightWing.pitch = this.elytraModel.leftWing.pitch;
                this.elytraModel.rightWing.yaw = -this.elytraModel.leftWing.yaw;
                this.elytraModel.rightWing.roll = -this.elytraModel.leftWing.roll;

                VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(ELYTRA_TEXTURE));
                this.elytraModel.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);

                if (itemStack.hasGlint())
                {
                    this.elytraModel.render(matrices, vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint()), light, OverlayTexture.DEFAULT_UV);
                }

                matrices.pop();
            }
            return;
        }

        if (item instanceof ArmorItem armorItem)
        {
            if (armorItem.getSlotType() == armorSlot)
            {
                boolean innerModel = this.usesInnerModel(armorSlot);
                BipedEntityModel bipedModel = this.getModel(armorSlot);
                ModelPart part = this.getPart(bipedModel, type);

                bipedModel.setVisible(true);

                part.pivotX = part.pivotY = part.pivotZ = 0F;
                part.pitch = part.yaw = part.roll = 0F;
                part.xScale = part.yScale = part.zScale = 1F;

                DyedColorComponent dyed = itemStack.get(DataComponentTypes.DYED_COLOR);
                if (dyed != null)
                {
                    int color = dyed.rgb();
                    float r = (float)(color >> 16 & 255) / 255.0F;
                    float g = (float)(color >> 8 & 255) / 255.0F;
                    float b = (float)(color & 255) / 255.0F;

                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, r, g, b, null);
                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, 1F, 1F, 1F, "overlay");
                }
                else
                {
                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, 1F, 1F, 1F, null);
                }

                ArmorTrim trim = itemStack.get(DataComponentTypes.TRIM);
                boolean hasTrim = trim != null;
                boolean hasGlint = itemStack.hasGlint();

                if (hasTrim)
                {
                    /* Trim glint is a separate EQUAL pass fed the same verts as the trim
                     * shells (union), not a second scaled ModelPart.render — that desync
                     * was the trim↔glint z-fight. Base armor still gets its own 1.0 glint. */
                    this.renderTrim(part, armorItem.getMaterial(), matrices, vertexConsumers, light, trim, innerModel, hasGlint);
                }

                if (hasGlint)
                {
                    this.renderGlint(part, matrices, vertexConsumers, light);
                }
            }
        }
    }

    private ModelPart getPart(BipedEntityModel bipedModel, ArmorType type)
    {
        switch (type)
        {
            case HELMET -> {
                return bipedModel.head;
            }
            case CHEST, LEGGINGS -> {
                return bipedModel.body;
            }
            case LEFT_ARM -> {
                return bipedModel.leftArm;
            }
            case RIGHT_ARM -> {
                return bipedModel.rightArm;
            }
            case LEFT_LEG, LEFT_BOOT -> {
                return bipedModel.leftLeg;
            }
            case RIGHT_LEG, RIGHT_BOOT -> {
                return bipedModel.rightLeg;
            }
        }

        return bipedModel.head;
    }

    private void renderArmorParts(ModelPart part, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ArmorItem item, boolean secondTextureLayer, float red, float green, float blue, String overlay)
    {
        VertexConsumer base = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(this.getArmorTexture(item, secondTextureLayer, overlay)));
        VertexConsumer vertexConsumer = new RecolorVertexConsumer(base, new Color(red, green, blue, 1F));

        part.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);
    }

    private void renderTrim(ModelPart part, RegistryEntry<ArmorMaterial> material, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ArmorTrim trim, boolean leggings, boolean withGlint)
    {
        Sprite sprite = this.armorTrimsAtlas.getSprite(leggings ? trim.getLeggingsModelId(material) : trim.getGenericModelId(material));
        VertexConsumer trimConsumer = sprite.getTextureSpecificVertexConsumer(vertexConsumers.getBuffer(TexturedRenderLayers.getArmorTrims(trim.getPattern().value().decal())));
        VertexConsumer vertexConsumer = withGlint
            ? VertexConsumers.union(trimConsumer, vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint()))
            : trimConsumer;

        /* Armor + trim share the same ModelPart. Uniform 1.005 alone hides inner faces
         * (shell expands away from the cavity). Dual shells keep film/world z-fight fix
         * (044b2f4a6) while restoring inside trim like vanilla coplanar NoCull geometry.
         * Scale is baked into vertices — draw is deferred, so GL polygon-offset at submit
         * would not stick through consumers.draw(). */
        IrisArmorHooks.beginTrim(trim);

        try
        {
            this.renderScaledPart(part, matrices, vertexConsumer, light, TRIM_OUTER_SCALE);
            this.renderScaledPart(part, matrices, vertexConsumer, light, TRIM_INNER_SCALE);
        }
        finally
        {
            IrisArmorHooks.endTrim();
        }
    }

    private void renderGlint(ModelPart part, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        part.render(matrices, vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint()), light, OverlayTexture.DEFAULT_UV);
    }

    private void renderScaledPart(ModelPart part, MatrixStack matrices, VertexConsumer vertexConsumer, int light, float scale)
    {
        matrices.push();
        matrices.scale(scale, scale, scale);
        part.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    private BipedEntityModel getModel(EquipmentSlot slot)
    {
        return this.usesInnerModel(slot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.LEGS;
    }

    private Identifier getArmorTexture(ArmorItem item, boolean secondLayer, String overlay)
    {
        String materialName = item.getMaterial().getKey().map(k -> k.getValue().getPath()).orElse("unknown");
        String id = "textures/models/armor/" + materialName + "_layer_" + (secondLayer ? 2 : 1) + (overlay == null ? "" : "_" + overlay) + ".png";

        Identifier found = ARMOR_TEXTURE_CACHE.get(id);
        if (found == null)
        {
            found = Identifier.of("minecraft", id);
            ARMOR_TEXTURE_CACHE.put(id, found);
        }

        return found;
    }
}