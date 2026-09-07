package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.ShadowSettings;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReplayKeyframes extends ValueGroup
{
    public static final String GROUP_POSITION = "position";
    public static final String GROUP_ROTATION = "rotation";
    public static final String GROUP_LEFT_STICK = "lstick";
    public static final String GROUP_RIGHT_STICK = "rstick";
    public static final String GROUP_TRIGGERS = "triggers";
    public static final String GROUP_EXTRA1 = "extra1";
    public static final String GROUP_EXTRA2 = "extra2";

    /**
     * Viewport overlay has no dedicated pose/action buttons — vanilla pose flags and
     * action doubles are only captured when recording <b>all groups</b> ({@code null}/empty).
     */
    public static boolean wantsVanillaPoseActions(List<String> groups)
    {
        return groups == null || groups.isEmpty();
    }

    public static final List<String> CURATED_CHANNELS = Arrays.asList("x", "y", "z", "pitch", "yaw", "headYaw", "bodyYaw", "sneaking", "riding", "sprinting", "swimming", "flying", "fall_flying", "crawling", "climbing", "blocking", "sleeping", "riptide", "item_main_hand", "item_off_hand", "item_head", "item_chest", "item_legs", "item_feet", "selected_slot", "stick_lx", "stick_ly", "stick_rx", "stick_ry", "trigger_l", "trigger_r", "extra1_x", "extra1_y", "extra2_x", "extra2_y", "grounded", "damage", "invulnerable", "death_time", "using_item", "item_use_time", "fire", "particles", "active_hand", "vX", "vY", "vZ", "shadow_size", "shadow_opacity");

    public final KeyframeChannel<Double> x = new KeyframeChannel<>("x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> y = new KeyframeChannel<>("y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> z = new KeyframeChannel<>("z", KeyframeFactories.DOUBLE);

    public final KeyframeChannel<Double> vX = new KeyframeChannel<>("vX", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> vY = new KeyframeChannel<>("vY", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> vZ = new KeyframeChannel<>("vZ", KeyframeFactories.DOUBLE);

    public final KeyframeChannel<Double> yaw = new KeyframeChannel<>("yaw", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> pitch = new KeyframeChannel<>("pitch", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> headYaw = new KeyframeChannel<>("headYaw", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> bodyYaw = new KeyframeChannel<>("bodyYaw", KeyframeFactories.DOUBLE);

    public final KeyframeChannel<Double> sneaking = new KeyframeChannel<>("sneaking", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> sprinting = new KeyframeChannel<>("sprinting", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> swimming = new KeyframeChannel<>("swimming", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> flying = new KeyframeChannel<>("flying", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> fallFlying = new KeyframeChannel<>("fall_flying", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> crawling = new KeyframeChannel<>("crawling", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> climbing = new KeyframeChannel<>("climbing", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> blocking = new KeyframeChannel<>("blocking", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> sleeping = new KeyframeChannel<>("sleeping", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> riptide = new KeyframeChannel<>("riptide", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> grounded = new KeyframeChannel<>("grounded", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> fall = new KeyframeChannel<>("fall", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> damage = new KeyframeChannel<>("damage", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Boolean> invulnerable = new KeyframeChannel<>("invulnerable", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Double> deathTime = new KeyframeChannel<>("death_time", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> usingItem = new KeyframeChannel<>("using_item", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> itemUseTime = new KeyframeChannel<>("item_use_time", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> fire = new KeyframeChannel<>("fire", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> particles = new KeyframeChannel<>("particles", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> activeHand = new KeyframeChannel<>("active_hand", KeyframeFactories.DOUBLE);

    public final KeyframeChannel<Double> stickLeftX = new KeyframeChannel<>("stick_lx", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> stickLeftY = new KeyframeChannel<>("stick_ly", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> stickRightX = new KeyframeChannel<>("stick_rx", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> stickRightY = new KeyframeChannel<>("stick_ry", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> triggerLeft = new KeyframeChannel<>("trigger_l", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> triggerRight = new KeyframeChannel<>("trigger_r", KeyframeFactories.DOUBLE);

    /* Miscellaneous animatable keyframe channels */
    public final KeyframeChannel<Double> extra1X = new KeyframeChannel<>("extra1_x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> extra1Y = new KeyframeChannel<>("extra1_y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> extra2X = new KeyframeChannel<>("extra2_x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> extra2Y = new KeyframeChannel<>("extra2_y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<ShadowSettings> shadowSize = new KeyframeChannel<>("shadow_size", KeyframeFactories.SHADOW_SETTINGS);
    public final KeyframeChannel<Double> shadowOpacity = new KeyframeChannel<>("shadow_opacity", KeyframeFactories.DOUBLE);

    public final KeyframeChannel<ItemStack> mainHand = new KeyframeChannel<>("item_main_hand", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> offHand = new KeyframeChannel<>("item_off_hand", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> armorHead = new KeyframeChannel<>("item_head", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> armorChest = new KeyframeChannel<>("item_chest", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> armorLegs = new KeyframeChannel<>("item_legs", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<ItemStack> armorFeet = new KeyframeChannel<>("item_feet", KeyframeFactories.ITEM_STACK);
    public final KeyframeChannel<Integer> selectedSlot = new KeyframeChannel<>("selected_slot", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Double> riding = new KeyframeChannel<>("riding", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<MountLink> ridden = new KeyframeChannel<>("ridden", KeyframeFactories.MOUNT_LINK);

    public ReplayKeyframes(String id)
    {
        super(id);

        this.add(this.x);
        this.add(this.y);
        this.add(this.z);
        this.add(this.vX);
        this.add(this.vY);
        this.add(this.vZ);
        this.add(this.yaw);
        this.add(this.pitch);
        this.add(this.headYaw);
        this.add(this.bodyYaw);
        this.add(this.sneaking);
        this.add(this.sprinting);
        this.add(this.swimming);
        this.add(this.flying);
        this.add(this.fallFlying);
        this.add(this.crawling);
        this.add(this.climbing);
        this.add(this.blocking);
        this.add(this.sleeping);
        this.add(this.riptide);
        this.add(this.grounded);
        this.add(this.fall);
        this.add(this.damage);
        this.add(this.invulnerable);
        this.add(this.deathTime);
        this.add(this.usingItem);
        this.add(this.itemUseTime);
        this.add(this.fire);
        this.add(this.particles);
        this.add(this.activeHand);
        this.add(this.stickLeftX);
        this.add(this.stickLeftY);
        this.add(this.stickRightX);
        this.add(this.stickRightY);
        this.add(this.triggerLeft);
        this.add(this.triggerRight);
        this.add(this.extra1X);
        this.add(this.extra1Y);
        this.add(this.extra2X);
        this.add(this.extra2Y);
        this.add(this.shadowSize);
        this.add(this.shadowOpacity);

        this.add(this.mainHand);
        this.add(this.offHand);
        this.add(this.armorHead);
        this.add(this.armorChest);
        this.add(this.armorLegs);
        this.add(this.armorFeet);
        this.add(this.selectedSlot);
        this.add(this.riding);
        this.add(this.ridden);
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);
        this.migrateFireChannel();
        this.migrateLegacyFireTicks(data);
        this.migrateParticlesChannel();
        migrateLegacyRidingChannel(this.riding);
        this.migrateLegacyDoubleShadowSize(data);
        this.migrateCompoundShadowChannel(data);
    }

    /**
     * Promotes pre-compound {@code shadow_size} double keyframes (and optional
     * {@code shadow_size_z}) into {@link ShadowSettings} width/offset data.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void migrateLegacyDoubleShadowSize(BaseType data)
    {
        IKeyframeFactory<?> factory = this.shadowSize.getFactory();

        if (factory != KeyframeFactories.DOUBLE
            && factory != KeyframeFactories.FLOAT
            && factory != KeyframeFactories.INTEGER)
        {
            return;
        }

        KeyframeChannel legacySize = (KeyframeChannel) (Object) this.shadowSize;
        KeyframeChannel<Double> sizeZ = new KeyframeChannel<>("shadow_size_z", KeyframeFactories.DOUBLE);

        if (data instanceof MapType map && map.has("shadow_size_z"))
        {
            sizeZ.fromData(map.get("shadow_size_z"));
        }

        List<Float> ticks = new ArrayList<>();
        List<ShadowSettings> values = new ArrayList<>();

        for (Object object : legacySize.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) object;
            Object raw = keyframe.getValue();
            float size = raw instanceof Number ? ((Number) raw).floatValue() : 0.5F;
            float tick = keyframe.getTick();
            ShadowSettings settings = new ShadowSettings();

            settings.widthX = Math.max(0F, size);
            settings.widthZ = sizeZ.isEmpty()
                ? settings.widthX
                : Math.max(0F, sizeZ.interpolate(tick).floatValue());

            ticks.add(tick);
            values.add(settings);
        }

        this.shadowSize.removeAll();
        this.shadowSize.setFactory(KeyframeFactories.SHADOW_SETTINGS);

        for (int i = 0; i < ticks.size(); i++)
        {
            this.shadowSize.insert(ticks.get(i), values.get(i));
        }
    }

    /**
     * Splits the compound {@code shadow} ({@link ShadowSettings}) channel back into
     * {@code shadow_size} (width + offset) and {@code shadow_opacity} when loading films
     * saved after the unified shadow track was introduced.
     */
    private void migrateCompoundShadowChannel(BaseType data)
    {
        if (!(data instanceof MapType map) || !map.has("shadow"))
        {
            return;
        }

        boolean sizeEmpty = this.shadowSize.isEmpty();
        boolean opacityEmpty = this.shadowOpacity.isEmpty();

        if (!sizeEmpty && !opacityEmpty)
        {
            return;
        }

        KeyframeChannel<ShadowSettings> compound = new KeyframeChannel<>("shadow", KeyframeFactories.SHADOW_SETTINGS);

        compound.fromData(map.get("shadow"));

        if (compound.isEmpty())
        {
            return;
        }

        for (Keyframe<ShadowSettings> keyframe : compound.getKeyframes())
        {
            ShadowSettings settings = keyframe.getValue();

            if (settings == null)
            {
                settings = new ShadowSettings();
            }

            float tick = keyframe.getTick();

            if (sizeEmpty)
            {
                ShadowSettings size = new ShadowSettings();

                size.widthX = Math.max(0F, settings.widthX);
                size.widthZ = Math.max(0F, settings.widthZ);
                size.offsetX = settings.offsetX;
                size.offsetY = settings.offsetY;
                size.offsetZ = settings.offsetZ;
                size.opacity = 1F;

                this.shadowSize.insert(tick, size);
            }

            if (opacityEmpty)
            {
                this.shadowOpacity.insert(tick, (double) Math.max(0F, Math.min(1F, settings.opacity)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateFireChannel()
    {
        if (!ReplayKeyframes.isLegacyBooleanFactory(this.fire))
        {
            return;
        }

        KeyframeChannel<Boolean> legacy = (KeyframeChannel<Boolean>) (Object) this.fire;
        List<Float> ticks = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (Keyframe<?> keyframe : legacy.getKeyframes())
        {
            Object value = keyframe.getValue();

            ticks.add(keyframe.getTick());
            values.add(Boolean.TRUE.equals(value) ? 1D : 0D);
        }

        this.fire.removeAll();
        this.fire.setFactory(KeyframeFactories.DOUBLE);

        for (int i = 0; i < ticks.size(); i++)
        {
            this.fire.insert(ticks.get(i), values.get(i));
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateParticlesChannel()
    {
        if (!ReplayKeyframes.isLegacyBooleanFactory(this.particles))
        {
            return;
        }

        KeyframeChannel<Boolean> legacy = (KeyframeChannel<Boolean>) (Object) this.particles;
        List<Float> ticks = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (Keyframe<?> keyframe : legacy.getKeyframes())
        {
            Object value = keyframe.getValue();

            ticks.add(keyframe.getTick());
            values.add(Boolean.TRUE.equals(value) ? 1D : 0D);
        }

        this.particles.removeAll();
        this.particles.setFactory(KeyframeFactories.DOUBLE);

        for (int i = 0; i < ticks.size(); i++)
        {
            this.particles.insert(ticks.get(i), values.get(i));
        }
    }

    private void migrateLegacyFireTicks(BaseType data)
    {
        if (!(data instanceof MapType map) || !map.has("fire_ticks"))
        {
            return;
        }

        if (!this.fire.isEmpty())
        {
            return;
        }

        KeyframeChannel<Double> legacy = new KeyframeChannel<>("fire_ticks", KeyframeFactories.DOUBLE);

        legacy.fromData(map.get("fire_ticks"));

        for (Keyframe<Double> keyframe : legacy.getKeyframes())
        {
            Double value = keyframe.getValue();
            double enabled = value != null && value > 0D ? 1D : 0D;

            this.fire.insert(keyframe.getTick(), enabled);
        }
    }

    @SuppressWarnings("unchecked")
    private static void migrateLegacyRidingChannel(KeyframeChannel<Double> channel)
    {
        if (!ReplayKeyframes.isLegacyMountLinkFactory(channel))
        {
            return;
        }

        List<Float> ticks = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            Object value = keyframe.getValue();

            if (value instanceof MountLink link)
            {
                ticks.add(keyframe.getTick());
                values.add(link.active ? 1D : 0D);
            }
        }

        channel.removeAll();
        channel.setFactory(KeyframeFactories.DOUBLE);

        for (int i = 0; i < ticks.size(); i++)
        {
            channel.insert(ticks.get(i), values.get(i));
        }
    }

    private static boolean isLegacyBooleanFactory(KeyframeChannel<?> channel)
    {
        return channel.getFactory() == KeyframeFactories.BOOLEAN;
    }

    private static boolean isLegacyMountLinkFactory(KeyframeChannel<?> channel)
    {
        return channel.getFactory() == KeyframeFactories.MOUNT_LINK;
    }

    public List<KeyframeChannel<?>> getChannels()
    {
        ArrayList<KeyframeChannel<?>> channels = new ArrayList<>();

        for (BaseValue baseValue : this.getAll())
        {
            if (baseValue instanceof KeyframeChannel<?> channel)
            {
                channels.add(channel);
            }
        }

        return channels;
    }

    public void shift(float tick)
    {
        for (KeyframeChannel<?> channel : this.getChannels())
        {
            for (Keyframe<?> keyframe : channel.getKeyframes())
            {
                keyframe.setTick(keyframe.getTick() + tick);
            }
        }
    }

    public void copyOver(ReplayKeyframes keyframes, int tick)
    {
        for (KeyframeChannel<?> channel : this.getChannels())
        {
            BaseValue keyframe = keyframes.get(channel.getId());

            if (keyframe instanceof KeyframeChannel<?> keyframeChannel)
            {
                channel.copyOver(keyframeChannel, tick);
            }
        }
    }

    /**
     * Remove keyframes at {@code tick} and later from channels that
     * {@link #record} will write, so viewport capture does not lerp into
     * leftover future poses (especially "All groups").
     */
    public void clearFrom(float tick, List<String> groups)
    {
        boolean empty = groups == null || groups.isEmpty();
        boolean position = empty || groups.contains(GROUP_POSITION);
        boolean rotation = empty || groups.contains(GROUP_ROTATION);
        boolean leftStick = empty || groups.contains(GROUP_LEFT_STICK);
        boolean rightStick = empty || groups.contains(GROUP_RIGHT_STICK);
        boolean triggers = empty || groups.contains(GROUP_TRIGGERS);
        boolean extra1 = empty || groups.contains(GROUP_EXTRA1);
        boolean extra2 = empty || groups.contains(GROUP_EXTRA2);

        if (position)
        {
            this.x.removeFrom(tick);
            this.y.removeFrom(tick);
            this.z.removeFrom(tick);
            this.vX.removeFrom(tick);
            this.vY.removeFrom(tick);
            this.vZ.removeFrom(tick);
            this.fall.removeFrom(tick);
        }

        /* Pose/action flags + mount links: only when recording all groups (same gate as record()). */
        if (wantsVanillaPoseActions(groups))
        {
            this.sneaking.removeFrom(tick);
            this.sprinting.removeFrom(tick);
            this.swimming.removeFrom(tick);
            this.flying.removeFrom(tick);
            this.fallFlying.removeFrom(tick);
            this.crawling.removeFrom(tick);
            this.climbing.removeFrom(tick);
            this.blocking.removeFrom(tick);
            this.sleeping.removeFrom(tick);
            this.riptide.removeFrom(tick);
            this.grounded.removeFrom(tick);
            this.damage.removeFrom(tick);
            this.deathTime.removeFrom(tick);
            this.usingItem.removeFrom(tick);
            this.itemUseTime.removeFrom(tick);
            this.fire.removeFrom(tick);
            this.particles.removeFrom(tick);
            this.activeHand.removeFrom(tick);
            this.riding.removeFrom(tick);
            this.ridden.removeFrom(tick);
        }

        if (rotation)
        {
            this.yaw.removeFrom(tick);
            this.pitch.removeFrom(tick);
            this.headYaw.removeFrom(tick);
            this.bodyYaw.removeFrom(tick);
        }

        if (leftStick)
        {
            this.stickLeftX.removeFrom(tick);
            this.stickLeftY.removeFrom(tick);
        }

        if (rightStick)
        {
            this.stickRightX.removeFrom(tick);
            this.stickRightY.removeFrom(tick);
        }

        if (triggers)
        {
            this.triggerLeft.removeFrom(tick);
            this.triggerRight.removeFrom(tick);
        }

        if (extra1)
        {
            this.extra1X.removeFrom(tick);
            this.extra1Y.removeFrom(tick);
        }

        if (extra2)
        {
            this.extra2X.removeFrom(tick);
            this.extra2Y.removeFrom(tick);
        }

        if (empty)
        {
            this.mainHand.removeFrom(tick);
            this.offHand.removeFrom(tick);
            this.armorHead.removeFrom(tick);
            this.armorChest.removeFrom(tick);
            this.armorLegs.removeFrom(tick);
            this.armorFeet.removeFrom(tick);
            this.selectedSlot.removeFrom(tick);
        }
    }

    /**
     * Resume-capture bridge: freeze the pose already present on the timeline at
     * {@code tick}, then clear from {@code tick} so the new take does not lerp into
     * deleted future keys. Empty channels are left alone — never seed defaults (0° =
     * south) or from-scratch recordings would face south until the first real insert.
     */
    public void bridgeRecordingFrom(float tick, List<String> groups)
    {
        this.bridgeRecordingFrom(tick, groups, null);
    }

    /**
     * Same as {@link #bridgeRecordingFrom(float, List)}. Position channels are only
     * cleared (not restored at {@code tick}) — a freeze-at-{@code tick} for XYZ caused
     * long lerps when the first live {@code record()} replaced that same tick. The
     * hard cut is sealed after capture via {@link #sealPositionRecordingCut}.
     */
    public void bridgeRecordingFrom(float tick, List<String> groups, IEntity live)
    {
        /* {@code live} kept for call-site compatibility; position cut is sealed on stop. */
        boolean empty = groups == null || groups.isEmpty();
        boolean rotation = empty || groups.contains(GROUP_ROTATION);
        boolean leftStick = empty || groups.contains(GROUP_LEFT_STICK);
        boolean rightStick = empty || groups.contains(GROUP_RIGHT_STICK);
        boolean triggers = empty || groups.contains(GROUP_TRIGGERS);
        boolean extra1 = empty || groups.contains(GROUP_EXTRA1);
        boolean extra2 = empty || groups.contains(GROUP_EXTRA2);

        boolean poseActions = wantsVanillaPoseActions(groups);
        Double sneaking = poseActions ? this.snapshotDouble(this.sneaking, tick) : null;
        Double sprinting = poseActions ? this.snapshotDouble(this.sprinting, tick) : null;
        Double swimming = poseActions ? this.snapshotDouble(this.swimming, tick) : null;
        Double flying = poseActions ? this.snapshotDouble(this.flying, tick) : null;
        Double fallFlying = poseActions ? this.snapshotDouble(this.fallFlying, tick) : null;
        Double crawling = poseActions ? this.snapshotDouble(this.crawling, tick) : null;
        Double climbing = poseActions ? this.snapshotDouble(this.climbing, tick) : null;
        Double blocking = poseActions ? this.snapshotDouble(this.blocking, tick) : null;
        Double sleeping = poseActions ? this.snapshotDouble(this.sleeping, tick) : null;
        Double riptide = poseActions ? this.snapshotDouble(this.riptide, tick) : null;
        Double grounded = poseActions ? this.snapshotDouble(this.grounded, tick) : null;
        Double damage = poseActions ? this.snapshotDouble(this.damage, tick) : null;
        Double deathTime = poseActions ? this.snapshotDouble(this.deathTime, tick) : null;
        Double usingItem = poseActions ? this.snapshotDouble(this.usingItem, tick) : null;
        Double itemUseTime = poseActions ? this.snapshotDouble(this.itemUseTime, tick) : null;
        Double fire = poseActions ? this.snapshotDouble(this.fire, tick) : null;
        Double particles = poseActions ? this.snapshotDouble(this.particles, tick) : null;
        Double activeHand = poseActions ? this.snapshotDouble(this.activeHand, tick) : null;

        Double yaw = rotation ? this.snapshotDouble(this.yaw, tick) : null;
        Double pitch = rotation ? this.snapshotDouble(this.pitch, tick) : null;
        Double headYaw = rotation ? this.snapshotDouble(this.headYaw, tick) : null;
        Double bodyYaw = rotation ? this.snapshotDouble(this.bodyYaw, tick) : null;

        Double stickLeftX = leftStick ? this.snapshotDouble(this.stickLeftX, tick) : null;
        Double stickLeftY = leftStick ? this.snapshotDouble(this.stickLeftY, tick) : null;
        Double stickRightX = rightStick ? this.snapshotDouble(this.stickRightX, tick) : null;
        Double stickRightY = rightStick ? this.snapshotDouble(this.stickRightY, tick) : null;
        Double triggerLeft = triggers ? this.snapshotDouble(this.triggerLeft, tick) : null;
        Double triggerRight = triggers ? this.snapshotDouble(this.triggerRight, tick) : null;
        Double extra1X = extra1 ? this.snapshotDouble(this.extra1X, tick) : null;
        Double extra1Y = extra1 ? this.snapshotDouble(this.extra1Y, tick) : null;
        Double extra2X = extra2 ? this.snapshotDouble(this.extra2X, tick) : null;
        Double extra2Y = extra2 ? this.snapshotDouble(this.extra2Y, tick) : null;

        ItemStack mainHand = empty ? this.snapshotItem(this.mainHand, tick) : null;
        ItemStack offHand = empty ? this.snapshotItem(this.offHand, tick) : null;
        ItemStack armorHead = empty ? this.snapshotItem(this.armorHead, tick) : null;
        ItemStack armorChest = empty ? this.snapshotItem(this.armorChest, tick) : null;
        ItemStack armorLegs = empty ? this.snapshotItem(this.armorLegs, tick) : null;
        ItemStack armorFeet = empty ? this.snapshotItem(this.armorFeet, tick) : null;
        Integer selectedSlot = empty ? this.snapshotInteger(this.selectedSlot, tick) : null;

        this.clearFrom(tick, groups);

        /* Position: cleared only — do not restore at tick (see sealPositionRecordingCut). */

        if (poseActions)
        {
            this.restoreDouble(this.sneaking, tick, sneaking);
            this.restoreDouble(this.sprinting, tick, sprinting);
            this.restoreDouble(this.swimming, tick, swimming);
            this.restoreDouble(this.flying, tick, flying);
            this.restoreDouble(this.fallFlying, tick, fallFlying);
            this.restoreDouble(this.crawling, tick, crawling);
            this.restoreDouble(this.climbing, tick, climbing);
            this.restoreDouble(this.blocking, tick, blocking);
            this.restoreDouble(this.sleeping, tick, sleeping);
            this.restoreDouble(this.riptide, tick, riptide);
            this.restoreDouble(this.grounded, tick, grounded);
            this.restoreDouble(this.damage, tick, damage);
            this.restoreDouble(this.deathTime, tick, deathTime);
            this.restoreDouble(this.usingItem, tick, usingItem);
            this.restoreDouble(this.itemUseTime, tick, itemUseTime);
            this.restoreDouble(this.fire, tick, fire);
            this.restoreDouble(this.particles, tick, particles);
            this.restoreDouble(this.activeHand, tick, activeHand);
            /* riding/ridden: cleared in clearFrom but not restored — live recordMountKeyframes
             * rewrites from entity state so a non-sitting re-take does not keep old sitting keys. */
        }

        this.restoreDouble(this.yaw, tick, yaw);
        this.restoreDouble(this.pitch, tick, pitch);
        this.restoreDouble(this.headYaw, tick, headYaw);
        this.restoreDouble(this.bodyYaw, tick, bodyYaw);

        this.restoreDouble(this.stickLeftX, tick, stickLeftX);
        this.restoreDouble(this.stickLeftY, tick, stickLeftY);
        this.restoreDouble(this.stickRightX, tick, stickRightX);
        this.restoreDouble(this.stickRightY, tick, stickRightY);
        this.restoreDouble(this.triggerLeft, tick, triggerLeft);
        this.restoreDouble(this.triggerRight, tick, triggerRight);
        this.restoreDouble(this.extra1X, tick, extra1X);
        this.restoreDouble(this.extra1Y, tick, extra1Y);
        this.restoreDouble(this.extra2X, tick, extra2X);
        this.restoreDouble(this.extra2Y, tick, extra2Y);

        if (mainHand != null)
        {
            this.mainHand.insert(tick, mainHand);
        }

        if (offHand != null)
        {
            this.offHand.insert(tick, offHand);
        }

        if (armorHead != null)
        {
            this.armorHead.insert(tick, armorHead);
        }

        if (armorChest != null)
        {
            this.armorChest.insert(tick, armorChest);
        }

        if (armorLegs != null)
        {
            this.armorLegs.insert(tick, armorLegs);
        }

        if (armorFeet != null)
        {
            this.armorFeet.insert(tick, armorFeet);
        }

        if (selectedSlot != null)
        {
            this.selectedSlot.insert(tick, selectedSlot);
        }
    }

    /**
     * After a viewport re-record, insert position hold keys one tick before the first
     * new-take keyframe when that keyframe differs from the pre-record timeline. Runs
     * after {@link KeyframeChannel#simplify()} so the hold is not stripped as redundant.
     */
    public void sealPositionRecordingCut(float fromTick, BaseType beforeRecording, List<String> groups)
    {
        boolean empty = groups == null || groups.isEmpty();

        if (!empty && !groups.contains(GROUP_POSITION))
        {
            return;
        }

        if (beforeRecording == null || fromTick < 1F)
        {
            return;
        }

        ReplayKeyframes before = new ReplayKeyframes("recording_cut_before");

        before.fromData(beforeRecording);

        this.sealPositionChannelCut(this.x, before.x, fromTick);
        this.sealPositionChannelCut(this.y, before.y, fromTick);
        this.sealPositionChannelCut(this.z, before.z, fromTick);
        this.sealPositionChannelCut(this.vX, before.vX, fromTick);
        this.sealPositionChannelCut(this.vY, before.vY, fromTick);
        this.sealPositionChannelCut(this.vZ, before.vZ, fromTick);
        this.sealPositionChannelCut(this.fall, before.fall, fromTick);
    }

    private void sealPositionChannelCut(KeyframeChannel<Double> channel, KeyframeChannel<Double> before, float fromTick)
    {
        if (channel.isEmpty() || before.isEmpty())
        {
            return;
        }

        Keyframe<Double> firstNew = null;

        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            if (keyframe.getTick() >= fromTick)
            {
                firstNew = keyframe;

                break;
            }
        }

        if (firstNew == null)
        {
            return;
        }

        float holdTick = firstNew.getTick() - 1F;

        if (holdTick < 0F)
        {
            return;
        }

        Double oldValue = before.interpolate(holdTick);

        if (oldValue == null)
        {
            return;
        }

        if (channel.getFactory().compare(oldValue, firstNew.getValue()))
        {
            return;
        }

        channel.insert(holdTick, channel.getFactory().copy(oldValue));
    }

    private Double snapshotDouble(KeyframeChannel<Double> channel, float tick)
    {
        if (channel.isEmpty())
        {
            return null;
        }

        Double value = channel.interpolate(tick);

        return value == null ? null : channel.getFactory().copy(value);
    }

    private ItemStack snapshotItem(KeyframeChannel<ItemStack> channel, float tick)
    {
        if (channel.isEmpty())
        {
            return null;
        }

        ItemStack value = channel.interpolate(tick);

        return value == null ? null : channel.getFactory().copy(value);
    }

    private Integer snapshotInteger(KeyframeChannel<Integer> channel, float tick)
    {
        if (channel.isEmpty())
        {
            return null;
        }

        Integer value = channel.interpolate(tick);

        return value == null ? null : channel.getFactory().copy(value);
    }

    private void restoreDouble(KeyframeChannel<Double> channel, float tick, Double value)
    {
        if (value != null)
        {
            channel.insert(tick, value);
        }
    }

    /**
     * Record a 0/1 pose flag. Skips seeding {@code 0} into an empty channel so
     * intentionally cleared tracks stay empty until the entity actually enters the state.
     */
    private void insertVanillaFlag(KeyframeChannel<Double> channel, float tick, boolean active)
    {
        this.insertVanillaDouble(channel, tick, active ? 1D : 0D);
    }

    private void insertVanillaDouble(KeyframeChannel<Double> channel, float tick, double value)
    {
        if (channel.isEmpty() && value == 0D)
        {
            return;
        }

        channel.insertIfChanged(tick, value);
    }

    public void record(float tick, IEntity entity, List<String> groups)
    {
        boolean empty = groups == null || groups.isEmpty();
        boolean position = empty || groups.contains(GROUP_POSITION);
        boolean rotation = empty || groups.contains(GROUP_ROTATION);
        boolean leftStick = empty || groups.contains(GROUP_LEFT_STICK);
        boolean rightStick = empty || groups.contains(GROUP_RIGHT_STICK);
        boolean triggers = empty || groups.contains(GROUP_TRIGGERS);
        boolean extra1 = empty || groups.contains(GROUP_EXTRA1);
        boolean extra2 = empty || groups.contains(GROUP_EXTRA2);

        /* Position and rotation — insert only when values change (with hold
         * keyframes for linear interp), matching post-simplify density. */
        if (position)
        {
            this.x.insertIfChanged(tick, entity.getX());
            this.y.insertIfChanged(tick, entity.getY());
            this.z.insertIfChanged(tick, entity.getZ());

            this.vX.insertIfChanged(tick, entity.getVelocity().x);
            this.vY.insertIfChanged(tick, entity.getVelocity().y);
            this.vZ.insertIfChanged(tick, entity.getVelocity().z);

            this.fall.insertIfChanged(tick, (double) entity.getFallDistance());
        }

        if (wantsVanillaPoseActions(groups))
        {
            /* Empty channels: do not plant 0D — user-cleared pose/action tracks stay empty
             * until the entity actually enters that state (or a non-zero action value). */
            this.insertVanillaFlag(this.sneaking, tick, entity.isSneaking());
            this.insertVanillaFlag(this.sprinting, tick, entity.isSprinting());
            this.insertVanillaFlag(this.swimming, tick, entity.isSwimming());
            this.insertVanillaFlag(this.flying, tick, entity.isFlying());
            this.insertVanillaFlag(this.fallFlying, tick, entity.isFallFlying());
            this.insertVanillaFlag(this.crawling, tick, entity.isCrawling());
            this.insertVanillaFlag(this.climbing, tick, entity.isClimbing());
            this.insertVanillaFlag(this.blocking, tick, entity.isBlocking());
            this.insertVanillaFlag(this.sleeping, tick, entity.isSleeping());
            this.insertVanillaFlag(this.riptide, tick, entity.isUsingRiptide());
            this.insertVanillaFlag(this.grounded, tick, entity.isOnGround());
            this.insertVanillaDouble(this.damage, tick, (double) entity.getHurtTimer());
            this.insertVanillaDouble(this.deathTime, tick, (double) entity.getDeathTime());
            this.insertVanillaFlag(this.usingItem, tick, entity.isUsingItem());
            this.insertVanillaDouble(this.itemUseTime, tick, (double) this.getItemUseElapsed(entity));
            this.insertVanillaFlag(this.fire, tick, entity.getFireTicks() > 0);
            this.insertVanillaFlag(this.particles, tick, entity.isParticlesEnabled());
            this.insertVanillaFlag(this.activeHand, tick, entity.getActiveHand() == Hand.OFF_HAND);
        }

        if (rotation)
        {
            this.yaw.insertIfChanged(tick, (double) entity.getYaw());
            this.pitch.insertIfChanged(tick, (double) entity.getPitch());
            this.headYaw.insertIfChanged(tick, (double) entity.getHeadYaw());
            this.bodyYaw.insertIfChanged(tick, (double) entity.getBodyYaw());
        }

        float[] sticks = entity.getExtraVariables();

        if (leftStick)
        {
            this.stickLeftX.insertIfChanged(tick, (double) sticks[0]);
            this.stickLeftY.insertIfChanged(tick, (double) sticks[1]);
        }

        if (rightStick)
        {
            this.stickRightX.insertIfChanged(tick, (double) sticks[2]);
            this.stickRightY.insertIfChanged(tick, (double) sticks[3]);
        }

        if (triggers)
        {
            this.triggerLeft.insertIfChanged(tick, (double) sticks[4]);
            this.triggerRight.insertIfChanged(tick, (double) sticks[5]);
        }

        if (extra1)
        {
            this.extra1X.insertIfChanged(tick, (double) sticks[6]);
            this.extra1Y.insertIfChanged(tick, (double) sticks[7]);
        }

        if (extra2)
        {
            this.extra2X.insertIfChanged(tick, (double) sticks[8]);
            this.extra2Y.insertIfChanged(tick, (double) sticks[9]);
        }

        if (empty)
        {
            this.mainHand.insertIfChanged(tick, entity.getEquipmentStack(EquipmentSlot.MAINHAND).copy());
            this.offHand.insertIfChanged(tick, entity.getEquipmentStack(EquipmentSlot.OFFHAND).copy());
            this.armorHead.insertIfChanged(tick, entity.getEquipmentStack(EquipmentSlot.HEAD).copy());
            this.armorChest.insertIfChanged(tick, entity.getEquipmentStack(EquipmentSlot.CHEST).copy());
            this.armorLegs.insertIfChanged(tick, entity.getEquipmentStack(EquipmentSlot.LEGS).copy());
            this.armorFeet.insertIfChanged(tick, entity.getEquipmentStack(EquipmentSlot.FEET).copy());
            this.selectedSlot.insertIfChanged(tick, entity.getSelectedSlot());
        }
    }

    /**
     * Insert keyframes at {@code tick} using values interpolated from the
     * existing animation at that tick (for cursor placement).
     */
    public void insertInterpolated(float tick, List<String> groups)
    {
        boolean empty = groups == null || groups.isEmpty();
        boolean position = empty || groups.contains(GROUP_POSITION);
        boolean rotation = empty || groups.contains(GROUP_ROTATION);
        boolean leftStick = empty || groups.contains(GROUP_LEFT_STICK);
        boolean rightStick = empty || groups.contains(GROUP_RIGHT_STICK);
        boolean triggers = empty || groups.contains(GROUP_TRIGGERS);
        boolean extra1 = empty || groups.contains(GROUP_EXTRA1);
        boolean extra2 = empty || groups.contains(GROUP_EXTRA2);

        if (position)
        {
            this.x.insertInterpolated(tick);
            this.y.insertInterpolated(tick);
            this.z.insertInterpolated(tick);
            this.vX.insertInterpolated(tick);
            this.vY.insertInterpolated(tick);
            this.vZ.insertInterpolated(tick);
            this.fall.insertInterpolated(tick);
        }

        this.sneaking.insertInterpolated(tick);
        this.sprinting.insertInterpolated(tick);
        this.swimming.insertInterpolated(tick);
        this.flying.insertInterpolated(tick);
        this.fallFlying.insertInterpolated(tick);
        this.crawling.insertInterpolated(tick);
        this.climbing.insertInterpolated(tick);
        this.blocking.insertInterpolated(tick);
        this.sleeping.insertInterpolated(tick);
        this.riptide.insertInterpolated(tick);
        this.grounded.insertInterpolated(tick);
        this.damage.insertInterpolated(tick);

        if (rotation)
        {
            this.yaw.insertInterpolated(tick);
            this.pitch.insertInterpolated(tick);
            this.headYaw.insertInterpolated(tick);
            this.bodyYaw.insertInterpolated(tick);
        }

        if (leftStick)
        {
            this.stickLeftX.insertInterpolated(tick);
            this.stickLeftY.insertInterpolated(tick);
        }

        if (rightStick)
        {
            this.stickRightX.insertInterpolated(tick);
            this.stickRightY.insertInterpolated(tick);
        }

        if (triggers)
        {
            this.triggerLeft.insertInterpolated(tick);
            this.triggerRight.insertInterpolated(tick);
        }

        if (extra1)
        {
            this.extra1X.insertInterpolated(tick);
            this.extra1Y.insertInterpolated(tick);
        }

        if (extra2)
        {
            this.extra2X.insertInterpolated(tick);
            this.extra2Y.insertInterpolated(tick);
        }

        if (empty)
        {
            this.mainHand.insertInterpolated(tick);
            this.offHand.insertInterpolated(tick);
            this.armorHead.insertInterpolated(tick);
            this.armorChest.insertInterpolated(tick);
            this.armorLegs.insertInterpolated(tick);
            this.armorFeet.insertInterpolated(tick);
            this.selectedSlot.insertInterpolated(tick);
        }
    }

    public void apply(int tick, IEntity entity)
    {
        this.apply(tick, entity, null);
    }

    /**
     * Apply a frame at given tick on the given entity.
     */
    public void apply(int tick, IEntity entity, List<String> groups)
    {
        boolean empty = groups == null || groups.isEmpty();
        boolean position = empty || !groups.contains(GROUP_POSITION);
        boolean rotation = empty || !groups.contains(GROUP_ROTATION);
        boolean leftStick = empty || !groups.contains(GROUP_LEFT_STICK);
        boolean rightStick = empty || !groups.contains(GROUP_RIGHT_STICK);
        boolean triggers = empty || !groups.contains(GROUP_TRIGGERS);
        boolean extra1 = empty || !groups.contains(GROUP_EXTRA1);
        boolean extra2 = empty || !groups.contains(GROUP_EXTRA2);
        MountLink riding = this.getRidingAt(tick);
        boolean mounted = entity.getMountTarget() != null;
        boolean sitting = riding.active && !mounted;

        if (position && !mounted)
        {
            entity.setVelocity(this.vX.interpolate(tick).floatValue(), this.vY.interpolate(tick).floatValue(), this.vZ.interpolate(tick).floatValue());
            entity.setFallDistance(this.fall.interpolate(tick).floatValue());

            KeyframeSegment<Double> x = this.x.findSegment(tick);
            Vector2d xx = this.getPrev(x, this.x.interpolate(tick - 1), tick);
            KeyframeSegment<Double> y = this.y.findSegment(tick);
            Vector2d yy = this.getPrev(y, this.y.interpolate(tick - 1), tick);
            KeyframeSegment<Double> z = this.z.findSegment(tick);
            Vector2d zz = this.getPrev(z, this.z.interpolate(tick - 1), tick);

            entity.setPosition(xx.x, yy.x, zz.x);
            entity.setPrevX(xx.y);
            entity.setPrevY(yy.y);
            entity.setPrevZ(zz.y);
        }
        else if (mounted)
        {
            entity.setVelocity(0F, 0F, 0F);
            entity.setFallDistance(0F);
        }

        if (rotation && !mounted)
        {
            KeyframeSegment<Double> yaw = this.yaw.findSegment(tick);
            Vector2d yyaw = this.getPrev(yaw, this.yaw.interpolate(tick - 1), tick);
            KeyframeSegment<Double> pitch = this.pitch.findSegment(tick);
            Vector2d ppitch = this.getPrev(pitch, this.pitch.interpolate(tick - 1), tick);
            KeyframeSegment<Double> headYaw = this.headYaw.findSegment(tick);
            Vector2d hheadYaw = this.getPrev(headYaw, this.headYaw.interpolate(tick - 1), tick);
            KeyframeSegment<Double> bodyYaw = this.bodyYaw.findSegment(tick);
            Vector2d bbodyYaw = this.getPrev(bodyYaw, this.bodyYaw.interpolate(tick - 1), tick);

            /* Unwrap prev toward current so render lerp takes the short arc (±180). */
            double yawNow = yyaw.x;
            double headNow = hheadYaw.x;
            double bodyNow = bbodyYaw.x;

            entity.setYaw((float) yawNow);
            entity.setPitch((float) ppitch.x);
            entity.setHeadYaw((float) headNow);
            entity.setBodyYaw((float) bodyNow);

            entity.setPrevYaw((float) Lerps.normalizeYaw(yawNow, yyaw.y));
            entity.setPrevPitch((float) ppitch.y);
            entity.setPrevHeadYaw((float) Lerps.normalizeYaw(headNow, hheadYaw.y));
            entity.setPrevBodyYaw((float) Lerps.normalizeYaw(bodyNow, bbodyYaw.y));
        }

        /* Motion and fall distance */
        entity.setSneaking(mounted || sitting ? false : this.sneaking.interpolate(tick) != 0D);
        entity.setSprinting(mounted || sitting ? false : this.sprinting.interpolate(tick) != 0D);
        entity.setSwimming(mounted || sitting ? false : this.swimming.interpolate(tick) != 0D);
        entity.setFlying(mounted || sitting ? false : this.flying.interpolate(tick) != 0D);
        entity.setFallFlying(mounted || sitting ? false : this.fallFlying.interpolate(tick) != 0D);
        entity.setCrawling(mounted || sitting ? false : this.crawling.interpolate(tick) != 0D);
        entity.setClimbing(mounted || sitting ? false : this.climbing.interpolate(tick) != 0D);
        entity.setBlocking(mounted || sitting ? false : this.blocking.interpolate(tick) != 0D);
        entity.setSleeping(mounted || sitting ? false : this.sleeping.interpolate(tick) != 0D);
        entity.setRiptide(mounted || sitting ? false : this.riptide.interpolate(tick) != 0D);
        entity.setOnGround(this.grounded.interpolate(tick) != 0D);
        entity.setHurtTimer(this.damage.interpolate(tick).intValue());
        entity.setDeathTime(this.deathTime.interpolate(tick).intValue());
        int itemUseElapsed = this.getItemUseElapsedAt(tick);
        boolean usingItem = this.isUsingItemAt(tick);

        entity.setUsingItem(usingItem);
        entity.setItemUseTimeLeft(itemUseElapsed);
        entity.setFireTicks(this.getFireTicksAt(tick));
        entity.setParticlesEnabled(this.getParticlesAt(tick));
        entity.setActiveHand(this.activeHand.interpolate(tick) > 0D ? Hand.OFF_HAND : Hand.MAIN_HAND);

        float[] sticks = entity.getExtraVariables();

        if (leftStick)
        {
            sticks[0] = this.stickLeftX.interpolate(tick).floatValue();
            sticks[1] = this.stickLeftY.interpolate(tick).floatValue();
        }

        if (rightStick)
        {
            sticks[2] = this.stickRightX.interpolate(tick).floatValue();
            sticks[3] = this.stickRightY.interpolate(tick).floatValue();
        }

        if (triggers)
        {
            sticks[4] = this.triggerLeft.interpolate(tick).floatValue();
            sticks[5] = this.triggerRight.interpolate(tick).floatValue();
        }

        if (extra1)
        {
            sticks[6] = this.extra1X.interpolate(tick).floatValue();
            sticks[7] = this.extra1Y.interpolate(tick).floatValue();
        }

        if (extra2)
        {
            sticks[8] = this.extra2X.interpolate(tick).floatValue();
            sticks[9] = this.extra2Y.interpolate(tick).floatValue();
        }

        entity.setEquipmentStack(EquipmentSlot.MAINHAND, this.mainHand.interpolate(tick, ItemStack.EMPTY));
        entity.setEquipmentStack(EquipmentSlot.OFFHAND, this.offHand.interpolate(tick, ItemStack.EMPTY));
        entity.setEquipmentStack(EquipmentSlot.HEAD, this.armorHead.interpolate(tick, ItemStack.EMPTY));
        entity.setEquipmentStack(EquipmentSlot.CHEST, this.armorChest.interpolate(tick, ItemStack.EMPTY));
        entity.setEquipmentStack(EquipmentSlot.LEGS, this.armorLegs.interpolate(tick, ItemStack.EMPTY));
        entity.setEquipmentStack(EquipmentSlot.FEET, this.armorFeet.interpolate(tick, ItemStack.EMPTY));
    }

    /**
     * Force teleportation for the previous keyframe being constant
     */
    private Vector2d getPrev(KeyframeSegment<Double> frame, double prev, int tick)
    {
        if (frame == null)
        {
            return new Vector2d(prev, prev);
        }

        IInterp interp = frame.a.getInterpolation().getInterp();
        Double interpolated = frame.createInterpolated();

        /*  */
        if (interp == Interpolations.CONST || interp == Interpolations.STEP)
        {
            if (interpolated != null)
            {
                prev = interpolated;
            }

            return new Vector2d(prev, prev);
        }

        if (frame.preA != frame.a && frame.a.getTick() == tick && (frame.preA.getInterpolation().getInterp() == Interpolations.CONST || frame.preA.getInterpolation().getInterp() == Interpolations.STEP))
        {
            if (interpolated != null)
            {
                prev = interpolated;
            }

            return new Vector2d(prev, prev);
        }

        return new Vector2d(interpolated == null ? prev : interpolated, prev);
    }

    public int getFireTicksAt(float tick)
    {
        if (this.fire.isEmpty() || this.fire.interpolate(tick) <= 0D)
        {
            return 0;
        }

        return Math.max(1, ((int) tick % 20) + 1);
    }

    public boolean getParticlesAt(float tick)
    {
        if (this.particles.isEmpty())
        {
            return true;
        }

        return this.particles.interpolate(tick) > 0D;
    }

    /**
     * {@code using_item} wins when that track exists so leftover {@code item_use_time}
     * after a finished eat cannot keep the use pose running for the rest of the film.
     */
    public boolean isUsingItemAt(float tick)
    {
        if (!this.usingItem.isEmpty())
        {
            return this.usingItem.interpolate(tick) > 0D;
        }

        return !this.itemUseTime.isEmpty() && this.itemUseTime.interpolate(tick) > 0D;
    }

    public int getItemUseElapsedAt(float tick)
    {
        if (!this.isUsingItemAt(tick) || this.itemUseTime.isEmpty())
        {
            return 0;
        }

        return Math.max(0, this.itemUseTime.interpolate(tick).intValue());
    }

    /**
     * Actor-only nested toggle under Damage: when true, the physical actor
     * ignores live damage while still applying keyframed hurt flash.
     * <p>
     * Empty track falls back to {@link Form#filmInvulnerable}.
     */
    public boolean isInvulnerableAt(float tick)
    {
        return this.isInvulnerableAt(tick, null);
    }

    public boolean isInvulnerableAt(float tick, Form form)
    {
        if (this.invulnerable.isEmpty())
        {
            return form != null && form.filmInvulnerable.get();
        }

        Boolean value = this.invulnerable.interpolate(tick);

        return value != null && value;
    }

    public MountLink getRidingAt(float tick)
    {
        boolean active = this.riding.interpolate(tick) != 0D;

        return new MountLink(active, MountLink.NO_REPLAY);
    }

    public static MountLink resolveRiding(Replay riderReplay, List<Replay> replays, int riderIndex, float tick)
    {
        if (riderReplay == null)
        {
            return new MountLink();
        }

        boolean active = riderReplay.keyframes.riding.interpolate(tick) != 0D;

        if (!active)
        {
            return new MountLink();
        }

        int mountIndex = ReplayKeyframes.resolveMountReplay(replays, riderIndex, tick);

        return new MountLink(true, mountIndex >= 0 ? mountIndex : MountLink.NO_REPLAY);
    }

    public static int resolveMountReplay(List<Replay> replays, int riderIndex, float tick)
    {
        if (replays == null)
        {
            return -1;
        }

        for (int i = 0; i < replays.size(); i++)
        {
            Replay mountReplay = replays.get(i);

            if (mountReplay == null)
            {
                continue;
            }

            MountLink ridden = mountReplay.keyframes.getRiddenAt(tick);

            if (ridden.active && ridden.replay == riderIndex)
            {
                return i;
            }
        }

        return -1;
    }

    public MountLink getRiddenAt(float tick)
    {
        return ReplayKeyframes.getMountLinkAt(this.ridden, tick);
    }

    /**
     * On all-groups re-record of rider {@code riderIndex}, drop only {@code ridden}
     * keys from {@code tick} that link to that rider — leave other mounts' links alone.
     */
    public void removeRiddenLinksFrom(float tick, int riderIndex)
    {
        if (riderIndex < 0)
        {
            return;
        }

        this.ridden.removeFrom(tick, (link) -> link.active && link.replay == riderIndex);
    }

    /**
     * Timeline stores elapsed item use ticks (0 = just started, higher = further along, e.g. bow draw).
     */
    private int getItemUseElapsed(IEntity entity)
    {
        if (!entity.isUsingItem())
        {
            return 0;
        }

        if (entity instanceof StubEntity)
        {
            return entity.getItemUseTimeLeft();
        }

        Hand hand = entity.getActiveHand();
        EquipmentSlot slot = hand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        ItemStack stack = entity.getEquipmentStack(slot);

        if (stack.isEmpty())
        {
            return 0;
        }

        int left = entity.getItemUseTimeLeft();
        int max = 20;

        if (entity instanceof MCEntity mcEntity && mcEntity.getMcEntity() instanceof LivingEntity living)
        {
            max = stack.getMaxUseTime(living);
        }

        if (max <= 0)
        {
            return left;
        }

        return Math.max(0, max - left);
    }

    /**
     * Mount links are inactive before the first keyframe. A single-keyframe channel
     * would otherwise apply that keyframe at every tick (including tick 0).
     */
    private static MountLink getMountLinkAt(KeyframeChannel<MountLink> channel, float tick)
    {
        if (channel.isEmpty())
        {
            return new MountLink();
        }

        Keyframe<MountLink> first = channel.get(0);

        if (first == null || tick < first.getTick())
        {
            return new MountLink();
        }

        if (channel.getKeyframes().size() == 1)
        {
            return first.getValue().copy();
        }

        return channel.interpolate(tick);
    }
}