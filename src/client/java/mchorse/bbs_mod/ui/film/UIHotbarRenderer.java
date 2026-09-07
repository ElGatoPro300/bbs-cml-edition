package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.misc.HotbarState;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.List;
import java.util.Random;

public class UIHotbarRenderer
{
    private static final float REFERENCE_WIDTH = 1920F;
    private static final float REFERENCE_HEIGHT = 1080F;
    private static final int HUD_GREEN = 8453920;
    private static final int BAR_ICON_Y = -17;
    private static final int EXPERIENCE_BAR_Y = -7;
    private static final int EXPERIENCE_TEXT_Y = -13;
    private static final float SCALE_PIVOT_X = 91F;
    private static final float SCALE_PIVOT_Y = 0.5F;
    private static final int MAX_HEALTH_ROWS = 60;
    private static final float MAX_HEALTH_CONTAINER = MAX_HEALTH_ROWS * 10F * 2F;
    private static final Identifier HOTBAR = Identifier.of("minecraft", "hud/hotbar");
    private static final Identifier HOTBAR_SELECTION = Identifier.of("minecraft", "hud/hotbar_selection");
    private static final Identifier HOTBAR_OFFHAND_LEFT = Identifier.of("minecraft", "hud/hotbar_offhand_left");
    private static final Identifier HOTBAR_OFFHAND_RIGHT = Identifier.of("minecraft", "hud/hotbar_offhand_right");
    private static final Identifier HOTBAR_ATTACK_INDICATOR_BACKGROUND = Identifier.of("minecraft", "hud/hotbar_attack_indicator_background");
    private static final Identifier HOTBAR_ATTACK_INDICATOR_PROGRESS = Identifier.of("minecraft", "hud/hotbar_attack_indicator_progress");
    private static final Identifier HEART_CONTAINER = Identifier.of("minecraft", "hud/heart/container");
    private static final Identifier HEART_HARDCORE_CONTAINER = Identifier.of("minecraft", "hud/heart/container_hardcore");
    private static final Identifier HEART_VEHICLE_CONTAINER = Identifier.of("minecraft", "hud/heart/vehicle_container");
    private static final Identifier HEART_VEHICLE_FULL = Identifier.of("minecraft", "hud/heart/vehicle_full");
    private static final Identifier HEART_VEHICLE_HALF = Identifier.of("minecraft", "hud/heart/vehicle_half");
    private static final Identifier[][] HEART_HALVES = {
        {Identifier.of("minecraft", "hud/heart/half"), Identifier.of("minecraft", "hud/heart/hardcore_half")},
        {Identifier.of("minecraft", "hud/heart/poisoned_half"), Identifier.of("minecraft", "hud/heart/poisoned_hardcore_half")},
        {Identifier.of("minecraft", "hud/heart/withered_half"), Identifier.of("minecraft", "hud/heart/withered_hardcore_half")},
        {Identifier.of("minecraft", "hud/heart/absorbing_half"), Identifier.of("minecraft", "hud/heart/absorbing_hardcore_half")},
        {Identifier.of("minecraft", "hud/heart/frozen_half"), Identifier.of("minecraft", "hud/heart/frozen_hardcore_half")}
    };
    private static final Identifier[][] HEART_FULLS = {
        {Identifier.of("minecraft", "hud/heart/full"), Identifier.of("minecraft", "hud/heart/hardcore_full")},
        {Identifier.of("minecraft", "hud/heart/poisoned_full"), Identifier.of("minecraft", "hud/heart/poisoned_hardcore_full")},
        {Identifier.of("minecraft", "hud/heart/withered_full"), Identifier.of("minecraft", "hud/heart/withered_hardcore_full")},
        {Identifier.of("minecraft", "hud/heart/absorbing_full"), Identifier.of("minecraft", "hud/heart/absorbing_hardcore_full")},
        {Identifier.of("minecraft", "hud/heart/frozen_full"), Identifier.of("minecraft", "hud/heart/frozen_hardcore_full")}
    };
    private static final Identifier ARMOR_EMPTY = Identifier.of("minecraft", "hud/armor_empty");
    private static final Identifier ARMOR_FULL = Identifier.of("minecraft", "hud/armor_full");
    private static final Identifier ARMOR_HALF = Identifier.of("minecraft", "hud/armor_half");
    private static final Identifier FOOD_EMPTY = Identifier.of("minecraft", "hud/food_empty");
    private static final Identifier FOOD_FULL = Identifier.of("minecraft", "hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.of("minecraft", "hud/food_half");
    private static final Identifier FOOD_EMPTY_HUNGER = Identifier.of("minecraft", "hud/food_empty_hunger");
    private static final Identifier FOOD_FULL_HUNGER = Identifier.of("minecraft", "hud/food_full_hunger");
    private static final Identifier FOOD_HALF_HUNGER = Identifier.of("minecraft", "hud/food_half_hunger");
    private static final Identifier AIR = Identifier.of("minecraft", "hud/air");
    private static final Identifier AIR_BURSTING = Identifier.of("minecraft", "hud/air_bursting");
    private static final Identifier EXPERIENCE_BAR_BACKGROUND_TEXTURE = Identifier.of("minecraft", "textures/gui/sprites/hud/experience_bar_background.png");
    private static final Identifier EXPERIENCE_BAR_PROGRESS_TEXTURE = Identifier.of("minecraft", "textures/gui/sprites/hud/experience_bar_progress.png");
    private static final Identifier JUMP_BAR_BACKGROUND_TEXTURE = Identifier.of("minecraft", "textures/gui/sprites/hud/jump_bar_background.png");
    private static final Identifier JUMP_BAR_PROGRESS_TEXTURE = Identifier.of("minecraft", "textures/gui/sprites/hud/jump_bar_progress.png");
    private static boolean wasHeartRegenerationEnabled;
    private static long heartRegenerationStartTick;

    public static void renderHotbars(MatrixStack stack, Batcher2D batcher, List<HotbarState> hotbars)
    {
        if (hotbars == null || hotbars.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();

        renderHotbars(stack, batcher, hotbars, 0, 0, width, height);
    }

    public static void renderHotbars(MatrixStack stack, Batcher2D batcher, List<HotbarState> hotbars, int originX, int originY, int width, int height)
    {
        if (hotbars == null || hotbars.isEmpty())
        {
            return;
        }

        for (HotbarState hotbar : hotbars)
        {
            renderHotbar(stack, batcher, hotbar, originX, originY, width, height);
        }
    }

    public static void renderHotbar(MatrixStack stack, Batcher2D batcher, HotbarState hotbar, int originX, int originY, int width, int height)
    {
        float alpha = MathHelper.clamp(hotbar.alpha, 0F, 1F);

        if (alpha <= 0F)
        {
            return;
        }

        float resolutionScale = getResolutionScale(width, height);
        float scale = Math.max(0.05F, hotbar.scale) * resolutionScale;
        int hotbarWidth = 182;
        int x = originX + Math.round(width / 2F + hotbar.x * resolutionScale - hotbarWidth / 2F);
        int y = originY + Math.round(height - (22 + 9) * resolutionScale + hotbar.y * resolutionScale);

        batcher.flush();
        stack.push();
        stack.translate(x, y, 0);
        stack.translate(SCALE_PIVOT_X, SCALE_PIVOT_Y, 0F);
        stack.scale(scale, scale, 1F);
        stack.translate(-SCALE_PIVOT_X, -SCALE_PIVOT_Y, 0F);

        /* HUD layers must ignore world depth to avoid bottom clipping against terrain. */
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        batcher.getContext().setShaderColor(1F, 1F, 1F, alpha);
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);

        boolean hasOffhandItem = hotbar.offhandItem != null && !hotbar.offhandItem.isEmpty();

        if (hotbar.showHotbar)
        {
            batcher.getContext().drawGuiTexture(HOTBAR, 0, 0, 182, 22);

            if (hasOffhandItem)
            {
                Identifier offhandTex = hotbar.rightOffhand ? HOTBAR_OFFHAND_RIGHT : HOTBAR_OFFHAND_LEFT;
                int offhandBgX = hotbar.rightOffhand ? 182 : -29;
                batcher.getContext().drawGuiTexture(offhandTex, offhandBgX, -1, 29, 24);
            }

            int selectedSlot = MathHelper.clamp(hotbar.selectedSlot, 0, 8);
            batcher.getContext().drawGuiTexture(HOTBAR_SELECTION, selectedSlot * 20 - 1, -1, 24, 23);

            if (hotbar.showAttackCooldown && hotbar.attackCooldown > 0F)
            {
                int selectedX = selectedSlot * 20 + 3;
                int cooldownH = MathHelper.ceil(hotbar.attackCooldown * 18F);
                batcher.getContext().drawGuiTexture(HOTBAR_ATTACK_INDICATOR_BACKGROUND, selectedX, -19, 18, 18);
                if (cooldownH > 0)
                {
                    batcher.getContext().drawGuiTexture(HOTBAR_ATTACK_INDICATOR_PROGRESS, selectedX, -19 + (18 - cooldownH), 18, cooldownH);
                }
            }
        }

        int barsY = BAR_ICON_Y;
        int heartType = MathHelper.clamp(hotbar.heartType, HotbarState.HEART_NORMAL, HotbarState.HEART_FROZEN);
        int hardcore = hotbar.hardcore ? 1 : 0;
        Identifier container = hotbar.hardcore ? HEART_HARDCORE_CONTAINER : HEART_CONTAINER;
        Identifier heartHalf = HEART_HALVES[heartType][hardcore];
        Identifier heartFull = HEART_FULLS[heartType][hardcore];
        Identifier absorptionHalf = HEART_HALVES[HotbarState.HEART_ABSORBING][hardcore];
        Identifier absorptionFull = HEART_FULLS[HotbarState.HEART_ABSORBING][hardcore];
        int healthSlots = MathHelper.ceil(MathHelper.clamp(hotbar.healthContainer, 0F, MAX_HEALTH_CONTAINER) / 2F);
        healthSlots = MathHelper.clamp(healthSlots, 0, MAX_HEALTH_ROWS * 10);
        int healthRows = Math.max(1, Math.min(MAX_HEALTH_ROWS, (healthSlots + 9) / 10));
        int absorptionSlots = MathHelper.ceil(MathHelper.clamp(hotbar.absorptionContainer, 0F, MAX_HEALTH_CONTAINER) / 2F);
        absorptionSlots = MathHelper.clamp(absorptionSlots, 0, MAX_HEALTH_ROWS * 10);
        int absorptionRows = absorptionSlots <= 0 ? 0 : Math.max(1, Math.min(MAX_HEALTH_ROWS, (absorptionSlots + 9) / 10));
        Random heartShakeRandom = hotbar.health <= 4F ? new Random(thisTickSeed()) : null;
        Random hungerShakeRandom = hotbar.hunger <= 6F ? new Random(thisTickSeed() + 17L) : null;
        int regenerationHeartIndex = -1;
        long hudTick = currentHudTick();

        if (hotbar.heartRegeneration && healthSlots > 0 && hotbar.health > 0F)
        {
            if (!wasHeartRegenerationEnabled)
            {
                heartRegenerationStartTick = hudTick;
            }

            wasHeartRegenerationEnabled = true;

            int cycleLength = healthSlots + 5; /* Vanilla-like pacing: one sweep plus idle tail. */
            int cycleIndex = cycleLength <= 0 ? 0 : (int) Math.floorMod(hudTick - heartRegenerationStartTick, cycleLength);

            regenerationHeartIndex = cycleIndex < healthSlots ? cycleIndex : -1;
        }
        else if (wasHeartRegenerationEnabled)
        {
            wasHeartRegenerationEnabled = false;
        }

        if (hotbar.showHealth)
        {
            renderBar(batcher, hotbar.health, container, heartHalf, heartFull, 0, barsY, healthSlots, heartShakeRandom, regenerationHeartIndex);
            if (absorptionSlots > 0)
            {
                renderBar(batcher, hotbar.absorption, container, absorptionHalf, absorptionFull, 0, barsY - healthRows * 10, absorptionSlots, heartShakeRandom, -1);
            }
        }

        if (hotbar.showArmor && hotbar.armor > 0F)
        {
            int armorY = barsY - (hotbar.showHealth ? (healthRows + absorptionRows) * 10 : 0);
            renderBar(batcher, hotbar.armor, ARMOR_EMPTY, ARMOR_HALF, ARMOR_FULL, 0, armorY, 10, null, -1);
        }

        if (hotbar.mountHealthContainer > 0F)
        {
            int mountSlots = MathHelper.ceil(MathHelper.clamp(hotbar.mountHealthContainer, 0F, MAX_HEALTH_CONTAINER) / 2F);
            renderBarReverse(batcher, hotbar.mountHealth, HEART_VEHICLE_CONTAINER, HEART_VEHICLE_HALF, HEART_VEHICLE_FULL, 182 - 9, barsY, mountSlots, null);
        }
        else if (hotbar.showHunger)
        {
            Identifier foodEmpty = hotbar.hungerEffect ? FOOD_EMPTY_HUNGER : FOOD_EMPTY;
            Identifier foodHalf = hotbar.hungerEffect ? FOOD_HALF_HUNGER : FOOD_HALF;
            Identifier foodFull = hotbar.hungerEffect ? FOOD_FULL_HUNGER : FOOD_FULL;
            renderBarReverse(batcher, hotbar.hunger, foodEmpty, foodHalf, foodFull, 182 - 9, barsY, 10, hungerShakeRandom);
        }

        if (hotbar.showAir)
        {
            int airY = barsY - (hotbar.mountHealthContainer > 0F || hotbar.showHunger ? 10 : 0);
            renderAirBar(batcher, hotbar.air, 182 - 9, airY);
        }

        if (hotbar.showHorseJump)
        {
            float jumpProgress = MathHelper.clamp(hotbar.horseJump, 0F, 1F);
            int jumpPixels = MathHelper.ceil(jumpProgress * 182F);
            batcher.getContext().drawTexture(JUMP_BAR_BACKGROUND_TEXTURE, 0, EXPERIENCE_BAR_Y, 0F, 0F, 182, 5, 182, 5);
            if (jumpPixels > 0)
            {
                batcher.getContext().drawTexture(JUMP_BAR_PROGRESS_TEXTURE, 0, EXPERIENCE_BAR_Y, 0F, 0F, jumpPixels, 5, 182, 5);
            }
        }
        else if (hotbar.showExperience)
        {
            float experience = MathHelper.clamp(hotbar.experience, 0F, 1F);
            int xpPixels = MathHelper.ceil(experience * 182F);
            batcher.getContext().drawTexture(EXPERIENCE_BAR_BACKGROUND_TEXTURE, 0, EXPERIENCE_BAR_Y, 0F, 0F, 182, 5, 182, 5);
            if (xpPixels > 0)
            {
                batcher.getContext().drawTexture(EXPERIENCE_BAR_PROGRESS_TEXTURE, 0, EXPERIENCE_BAR_Y, 0F, 0F, xpPixels, 5, 182, 5);
            }

            if (hotbar.experienceLevel > 0)
            {
                String level = Integer.toString(hotbar.experienceLevel);
                int levelX = (182 - batcher.getFont().getWidth(level)) / 2;
                int outlineColor = applyAlpha(0x000000, alpha);
                int levelColor = applyAlpha(HUD_GREEN, alpha);

                /* Vanilla-like outlined XP number: no drop shadow, solid contour around glyphs. */
                batcher.text(level, levelX - 1, EXPERIENCE_TEXT_Y, outlineColor, false);
                batcher.text(level, levelX + 1, EXPERIENCE_TEXT_Y, outlineColor, false);
                batcher.text(level, levelX, EXPERIENCE_TEXT_Y - 1, outlineColor, false);
                batcher.text(level, levelX, EXPERIENCE_TEXT_Y + 1, outlineColor, false);
                batcher.text(level, levelX, EXPERIENCE_TEXT_Y, levelColor, false);
            }
        }

        if (hotbar.showHotbar)
        {
            /* Item glint (enchants) requires depth test in GUI item renderer. */
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            Vector3f light0 = new Vector3f(0.85F, 0.85F, -1.0F).normalize();
            Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1.0F).normalize();
            RenderSystem.setupGui3DDiffuseLighting(light0, light1);

            for (int i = 0; i < 9; i++)
            {
                ItemStack stackItem = hotbar.items[i];

                if (stackItem == null || stackItem.isEmpty())
                {
                    continue;
                }

                int itemX = 3 + i * 20;
                int itemY = 3;

                batcher.getContext().drawItem(stackItem, itemX, itemY);
                batcher.getContext().drawItemInSlot(batcher.getFont().getRenderer(), stackItem, itemX, itemY);
            }

            if (hasOffhandItem)
            {
                int offhandX = hotbar.rightOffhand ? 192 : -26;
                int offhandY = 3;

                batcher.getContext().drawItem(hotbar.offhandItem, offhandX, offhandY);
                batcher.getContext().drawItemInSlot(batcher.getFont().getRenderer(), hotbar.offhandItem, offhandX, offhandY);
            }
        }

        batcher.getContext().draw();

        DiffuseLighting.disableGuiDepthLighting();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();

        batcher.getContext().setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        stack.pop();
        batcher.flush();
    }

    private static float getResolutionScale(int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return 1F;
        }

        return Math.max(0.05F, Math.min(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT));
    }

    private static void renderBar(Batcher2D batcher, float value, Identifier empty, Identifier half, Identifier full, int x, int y, int slots, Random lowHealthShakeRandom, int regenerationHeartIndex)
    {
        if (slots <= 0)
        {
            return;
        }

        float normalized = MathHelper.clamp(value, 0F, slots * 2F) / 2F;

        for (int i = 0; i < slots; i++)
        {
            int row = i / 10;
            int col = i % 10;
            int iconX = x + col * 8;
            int iconY = y - row * 10;

            if (lowHealthShakeRandom != null)
            {
                iconY += lowHealthShakeRandom.nextInt(2);
            }

            if (i == regenerationHeartIndex)
            {
                iconY -= 2;
            }

            batcher.getContext().drawGuiTexture(empty, iconX, iconY, 9, 9);

            float current = normalized - i;

            if (current >= 1F)
            {
                batcher.getContext().drawGuiTexture(full, iconX, iconY, 9, 9);
            }
            else if (current >= 0.5F)
            {
                batcher.getContext().drawGuiTexture(half, iconX, iconY, 9, 9);
            }
        }
    }

    private static long thisTickSeed()
    {
        return currentHudTick() * 312871L;
    }

    private static long currentHudTick()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.world != null ? mc.world.getTime() : System.currentTimeMillis() / 50L;
    }

    private static void renderBarReverse(Batcher2D batcher, float value, Identifier empty, Identifier half, Identifier full, int x, int y, int slots, Random lowHungerShakeRandom)
    {
        if (slots <= 0)
        {
            return;
        }

        float normalized = MathHelper.clamp(value, 0F, slots * 2F) / 2F;

        for (int i = 0; i < slots; i++)
        {
            int row = i / 10;
            int col = i % 10;
            int iconX = x - col * 8;
            int iconY = y - row * 10;

            if (lowHungerShakeRandom != null)
            {
                iconY += lowHungerShakeRandom.nextInt(2);
            }

            batcher.getContext().drawGuiTexture(empty, iconX, iconY, 9, 9);

            float current = normalized - i;

            if (current >= 1F)
            {
                batcher.getContext().drawGuiTexture(full, iconX, iconY, 9, 9);
            }
            else if (current >= 0.5F)
            {
                batcher.getContext().drawGuiTexture(half, iconX, iconY, 9, 9);
            }
        }
    }

    private static int applyAlpha(int color, float alpha)
    {
        int a = MathHelper.clamp(Math.round(MathHelper.clamp(alpha, 0F, 1F) * 255F), 0, 255);

        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static void renderAirBar(Batcher2D batcher, float air, int x, int y)
    {
        if (air >= 300F)
        {
            return;
        }

        int full = MathHelper.ceil((air - 2F) * 10F / 300F);
        int popping = MathHelper.ceil(air * 10F / 300F) - full;

        full = MathHelper.clamp(full, 0, 10);
        popping = MathHelper.clamp(popping, 0, 10 - full);

        for (int i = 0; i < full + popping; i++)
        {
            int iconX = x - i * 8;
            Identifier icon = i < full ? AIR : AIR_BURSTING;

            batcher.getContext().drawGuiTexture(icon, iconX, y, 9, 9);
        }
    }
}
