package mchorse.bbs_mod.entity;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.ActorReplayStateSync;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.ShadowSettings;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.utils.StringUtils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ActorEntity extends LivingEntity implements IEntityFormProvider
{
    public static DefaultAttributeContainer.Builder createActorAttributes()
    {
        return LivingEntity.createLivingAttributes()
            .add(EntityAttributes.ATTACK_DAMAGE, 1D)
            .add(EntityAttributes.MOVEMENT_SPEED, 0.1D)
            .add(EntityAttributes.ATTACK_SPEED)
            .add(EntityAttributes.LUCK);
    }

    private boolean despawn;
    private MCEntity entity = new MCEntity(this);
    private Form form;

    public MCEntity getBbsEntity()
    {
        return this.entity;
    }

    private Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();

    private boolean lastHitboxEnabled;
    private float lastHitboxWidth = Float.NaN;
    private float lastHitboxHeight = Float.NaN;
    private float lastHitboxSneakMultiplier = Float.NaN;
    private float lastHitboxEyeHeight = Float.NaN;
    private boolean lastSneaking;

    /**
     * After unpause, ramp walk velocity back in over a few ticks so limb swing
     * accelerates naturally instead of snapping for one tick from settled → full walk.
     */
    private int playbackVelocityBlendTicks;

    /**
     * When true, {@link #spawnSprintingParticles()} is a no-op. Used while the film
     * editor has actor-control on this replay: the live player may be sprinting, and
     * {@code ActorReplayStateSync} copies that flag onto this entity, but the body can
     * still sit on the film pose — without this, vanilla dust sprays at the wrong place.
     */
    private boolean suppressSprintParticles;

    /**
     * When true, skip real-time {@link LivingEntity} limb/age ticks and form animator
     * updates so film timeline pause/scrub can drive them instead (see
     * {@code BBSSettings.editorActorPauseAnimations}).
     */
    private boolean pauseNaturalAnimations;

    /**
     * Live player velocity while actor-control snaps this body each tick with physics
     * velocity cleared (avoids ice drift). Applied only around {@link Form#update} so
     * emoticons/procedural jump+fall still see {@code |vy| > 0.2}. Cleared after the update.
     */
    private Vec3d animationVelocityHint;

    /**
     * Cached deterministic limb phase for timeline-paused scrubbing.
     */
    private int timelineLimbTick = -1;
    private float timelineLimbPos;
    private int timelineFormTick = Integer.MIN_VALUE;

    /** Keyframed damage track is driving red flash this sync tick. */
    private boolean keyframeHurtActive;

    /** One-shot flag so emoticon/BOBJ {@code hurt} starts on the hit tick. */
    private boolean pendingHurtAnimation;

    /* Film and replay data for item drops */
    private Film film;
    private Replay replay;
    private int currentTick;
    private boolean replayItemsDropped;
    
    /* Runtime inventory for replay actors (initial inventory + picked up items) */
    private final List<ItemStack> runtimeInventory = new ArrayList<>();
    private boolean runtimeInventoryInitialized;
    private final Set<UUID> pickedUpEntityIds = new HashSet<>();

    /**
     * Ground-blob shadow from the owning replay (toggle / size / opacity / offset).
     * Applied by {@code ActorEntityRenderer}; defaults match a normal entity blob.
     */
    private boolean filmShadowEnabled = true;
    private float filmShadowOpacity = 1F;
    private float filmShadowRadiusX = 0.5F;
    private float filmShadowRadiusZ = 0.5F;
    private float filmShadowOffsetX;
    private float filmShadowOffsetY;
    private float filmShadowOffsetZ;

    public ActorEntity(EntityType<? extends LivingEntity> entityType, World world)
    {
        super(entityType, world);
    }

    /**
     * Set the film and replay associated with this actor for item dropping on death
     */
    public void setReplayData(Film film, Replay replay, int tick)
    {
        this.film = film;
        this.replay = replay;
        this.currentTick = tick;
        this.initializeRuntimeInventory();
        this.syncNameTag(replay);
        this.syncShadow(replay, tick);
    }

    public Film getFilm()
    {
        return this.film;
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public int getCurrentTick()
    {
        return this.currentTick;
    }

    /**
     * Actor-mode bodies are drawn by {@code ActorEntityRenderer}, not the film
     * stub path that draws {@link Replay#nameTag}. Mirror that string onto the
     * living entity so vanilla label rendering shows it.
     */
    public void syncNameTag(Replay replay)
    {
        String nameTag = replay == null ? "" : replay.nameTag.get();

        if (nameTag == null || nameTag.isEmpty())
        {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
        }
        else
        {
            this.setCustomName(Text.literal(StringUtils.processColoredText(nameTag)));
            this.setCustomNameVisible(true);
        }
    }

    /**
     * Mirror {@link Replay#shadow} (+ size / opacity / offset) onto this entity so
     * {@code ActorEntityRenderer} can draw the same ground blob as stub replays.
     * Does not affect movement or pose.
     */
    public void syncShadow(Replay replay, float tick)
    {
        if (replay == null)
        {
            this.filmShadowEnabled = true;
            this.filmShadowOpacity = 1F;
            this.filmShadowRadiusX = 0.5F;
            this.filmShadowRadiusZ = 0.5F;
            this.filmShadowOffsetX = 0F;
            this.filmShadowOffsetY = 0F;
            this.filmShadowOffsetZ = 0F;

            return;
        }

        this.filmShadowEnabled = replay.shadow.get();
        this.filmShadowOpacity = Math.max(0F, Math.min(1F, replay.shadowOpacity.get()));
        this.filmShadowRadiusX = Math.max(0F, replay.shadowSize.get());
        this.filmShadowRadiusZ = Math.max(0F, replay.shadowSizeZ.get());
        this.filmShadowOffsetX = replay.shadowOffsetX.get();
        this.filmShadowOffsetY = replay.shadowOffsetY.get();
        this.filmShadowOffsetZ = replay.shadowOffsetZ.get();

        if (!replay.keyframes.shadowSize.isEmpty())
        {
            ShadowSettings size = replay.keyframes.shadowSize.interpolate(tick);

            if (size != null)
            {
                this.filmShadowRadiusX = Math.max(0F, size.widthX);
                this.filmShadowRadiusZ = Math.max(0F, size.widthZ);
                this.filmShadowOffsetX = size.offsetX;
                this.filmShadowOffsetY = size.offsetY;
                this.filmShadowOffsetZ = size.offsetZ;
            }
        }

        if (!replay.keyframes.shadowOpacity.isEmpty())
        {
            Double opacity = replay.keyframes.shadowOpacity.interpolate(tick);

            if (opacity != null)
            {
                this.filmShadowOpacity = Math.max(0F, Math.min(1F, opacity.floatValue()));
            }
        }
    }

    /**
     * Prefer client-resolved settings (includes the same path as stub film shadows).
     */
    public void syncShadow(boolean enabled, ShadowSettings settings)
    {
        this.filmShadowEnabled = enabled;

        if (settings == null)
        {
            this.filmShadowOpacity = enabled ? 1F : 0F;
            this.filmShadowRadiusX = enabled ? 0.5F : 0F;
            this.filmShadowRadiusZ = enabled ? 0.5F : 0F;
            this.filmShadowOffsetX = 0F;
            this.filmShadowOffsetY = 0F;
            this.filmShadowOffsetZ = 0F;

            return;
        }

        this.filmShadowOpacity = Math.max(0F, Math.min(1F, settings.opacity));
        this.filmShadowRadiusX = Math.max(0F, settings.widthX);
        this.filmShadowRadiusZ = Math.max(0F, settings.widthZ);
        this.filmShadowOffsetX = settings.offsetX;
        this.filmShadowOffsetY = settings.offsetY;
        this.filmShadowOffsetZ = settings.offsetZ;
    }

    public boolean isFilmShadowEnabled()
    {
        return this.filmShadowEnabled;
    }

    public float getFilmShadowOpacity()
    {
        return this.filmShadowOpacity;
    }

    public float getFilmShadowRadiusX()
    {
        return this.filmShadowRadiusX;
    }

    public float getFilmShadowRadiusZ()
    {
        return this.filmShadowRadiusZ;
    }

    public float getFilmShadowOffsetX()
    {
        return this.filmShadowOffsetX;
    }

    public float getFilmShadowOffsetY()
    {
        return this.filmShadowOffsetY;
    }

    public float getFilmShadowOffsetZ()
    {
        return this.filmShadowOffsetZ;
    }

    public boolean shouldRenderFilmGroundShadow()
    {
        return this.filmShadowEnabled
            && this.filmShadowOpacity > 0.001F
            && (this.filmShadowRadiusX > 0F || this.filmShadowRadiusZ > 0F);
    }
    
    /**
     * Update the current tick for accurate item retrieval
     */
    public void updateTick(int tick)
    {
        this.currentTick = tick;
    }

    /**
     * Film playback resumed after a hold-still pause. Softens the first walk
     * velocities so {@link LimbAnimator} does not jump.
     */
    public void markPlaybackResumed()
    {
        this.playbackVelocityBlendTicks = 4;
    }

    /**
     * Multiplier for keyframe horizontal velocity this tick (1 = normal playback).
     */
    public double consumePlaybackVelocityScale()
    {
        if (this.playbackVelocityBlendTicks <= 0)
        {
            return 1D;
        }

        int remaining = this.playbackVelocityBlendTicks;

        this.playbackVelocityBlendTicks -= 1;

        return 1D - (remaining / 4D);
    }

    public void setSuppressSprintParticles(boolean suppress)
    {
        this.suppressSprintParticles = suppress;
    }

    public void setPauseNaturalAnimations(boolean pause)
    {
        if (!pause)
        {
            this.timelineLimbTick = -1;
            this.timelineFormTick = Integer.MIN_VALUE;
        }

        this.pauseNaturalAnimations = pause;
    }

    public boolean areNaturalAnimationsPaused()
    {
        return this.pauseNaturalAnimations;
    }

    /**
     * Hint for form animators while physics velocity is forced to zero (actor-control).
     * Consumed on the next {@link #tick()} around {@link Form#update}.
     */
    public void setAnimationVelocityHint(Vec3d velocity)
    {
        this.animationVelocityHint = velocity;
    }

    /**
     * Lock limb swing to the walk phase implied by keyframe motion up to {@code tick}.
     * Same pose every time you scrub to that tick (no accumulating jitter).
     */
    public void applyTimelineLimbPhase(ReplayKeyframes keyframes, int tick, boolean mounted)
    {
        if (!(this.limbAnimator instanceof LimbAnimatorAccessor limb))
        {
            return;
        }

        if (mounted || keyframes == null)
        {
            limb.setPrevSpeed(0F);
            limb.setSpeed(0F);
            this.timelineLimbTick = tick;

            return;
        }

        int t = Math.max(0, tick);

        if (this.timelineLimbTick < 0 || t < this.timelineLimbTick || t - this.timelineLimbTick > 64)
        {
            this.timelineLimbPos = ActorReplayStateSync.limbPosUntil(keyframes, t);
        }
        else if (t > this.timelineLimbTick)
        {
            for (int i = this.timelineLimbTick + 1; i <= t; i++)
            {
                this.timelineLimbPos += ActorReplayStateSync.limbSpeedAt(keyframes, i);
            }
        }

        this.timelineLimbTick = t;

        float speed = ActorReplayStateSync.limbSpeedAt(keyframes, t);

        limb.setPrevSpeed(speed);
        limb.setSpeed(speed);
        limb.setPos(this.timelineLimbPos);
    }

    /**
     * Advance emoticon/BOBJ clocks only when the timeline tick changes (avoids
     * scrub-cursor flicker pumping ActionPlayback every frame).
     * <p>
     * Does not assign {@code age = filmTick}: actors accumulate a real entity age
     * while playing, and dropping age to the playhead triggers
     * {@code ModelFormRenderer} animator reset (walk/run/emoticon restart).
     */
    public void syncTimelineFormTick(int tick)
    {
        int t = Math.max(0, tick);

        if (this.timelineFormTick == Integer.MIN_VALUE)
        {
            /* Anchor playhead tracking only — keep current age / ActionPlayback. */
            this.timelineFormTick = t;

            return;
        }

        if (t == this.timelineFormTick)
        {
            return;
        }

        int delta = t - this.timelineFormTick;

        if (delta < 0)
        {
            /* Seeking backward: one age drop so ModelFormRenderer can reset. */
            if (this.form != null)
            {
                this.age = Math.max(0, this.age + delta);
                this.form.update(this.entity);
            }
        }
        else
        {
            this.advanceFormAnimationTicks(Math.min(delta, 16));
        }

        this.timelineFormTick = t;
    }

    /**
     * Remember the film tick at pause without changing limbs or form clocks.
     * Scrub steps then advance relative to this anchor.
     */
    public void anchorTimelinePauseState(int tick)
    {
        int t = Math.max(0, tick);

        if (this.timelineFormTick == Integer.MIN_VALUE)
        {
            this.timelineFormTick = t;
        }

        if (this.timelineLimbTick < 0 && this.limbAnimator instanceof LimbAnimatorAccessor limb)
        {
            this.timelineLimbPos = limb.getPos();
            this.timelineLimbTick = t;
        }
    }

    /**
     * Advance emoticon/BOBJ form animators by {@code steps} ticks while natural
     * animations are timeline-driven.
     */
    public void advanceFormAnimationTicks(int steps)
    {
        if (steps <= 0 || this.form == null)
        {
            return;
        }

        for (int i = 0; i < steps; i++)
        {
            this.age += 1;
            this.form.update(this.entity);
        }
    }

    /**
     * One natural walk/form step for timeline scrub: limb swing from horizontal
     * delta (same formula as {@link StubEntity#update})
     * plus one form animator tick.
     */
    public void advanceNaturalMotionStep(double fromX, double fromZ, double toX, double toZ)
    {
        ActorReplayStateSync.advanceLimbStep(this, fromX, fromZ, toX, toZ);
        this.age += 1;

        if (this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    @Override
    protected void spawnSprintingParticles()
    {
        if (this.suppressSprintParticles)
        {
            return;
        }

        super.spawnSprintingParticles();
    }

    private void initializeRuntimeInventory()
    {
        this.runtimeInventory.clear();
        this.pickedUpEntityIds.clear();

        if (this.replay != null && this.replay.inventory != null)
        {
            for (ItemStack stack : this.replay.inventory.getStacks())
            {
                this.runtimeInventory.add(stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }

        this.runtimeInventoryInitialized = true;
    }

    public MCEntity getWrappingEntity()
    {
        return this.entity;
    }

    @Override
    public int getEntityId()
    {
        return this.getId();
    }

    @Override
    public Form getForm()
    {
        return this.form;
    }

    @Override
    public void setForm(Form form)
    {
        Form lastForm = this.form;

        this.form = form;

        if (!this.getEntityWorld().isClient())
        {
            if (lastForm != null) lastForm.onDemorph(this);
            if (form != null) form.onMorph(this);
        }
        
        this.updateHitboxDimensions();
    }

    public boolean isCollidable()
    {
        return this.form != null && this.form.hitbox.get();
    }

    public boolean isPushable()
    {
        return this.form == null || !this.form.hitbox.get();
    }

    public void pushAwayFrom(Entity entity)
    {
        if (this.form == null || !this.form.hitbox.get())
        {
            super.pushAwayFrom(entity);
        }
    }

    @Override
    public boolean handleAttack(Entity attacker)
    {
        if (this.form == null || !this.form.hitbox.get())
        {
            return super.handleAttack(attacker);
        }
        return false;
    }

    public boolean shouldRender(double distance)
    {
        double d = this.getBoundingBox().getAverageSideLength();

        if (Double.isNaN(d))
        {
            d = 1D;
        }

        return distance < (d * 256D) * (d * 256D);
    }

    public Iterable<ItemStack> getHandItems()
    {
        return List.of(this.getEquippedStack(EquipmentSlot.MAINHAND), this.getEquippedStack(EquipmentSlot.OFFHAND));
    }

    public Iterable<ItemStack> getArmorItems()
    {
        return List.of(this.getEquippedStack(EquipmentSlot.FEET), this.getEquippedStack(EquipmentSlot.LEGS), this.getEquippedStack(EquipmentSlot.CHEST), this.getEquippedStack(EquipmentSlot.HEAD));
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot)
    {
        return this.equipment.getOrDefault(slot, ItemStack.EMPTY);
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack)
    {
        this.equipment.put(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    @Override
    public Arm getMainArm()
    {
        return Arm.RIGHT;
    }

    @Override
    public void tick()
    {
        this.clearStaleCombatDeathIfAlive();

        /* Timeline freeze must not stall vanilla death: otherwise corpses never
         * finish deathTime removal and leave permanent shadow/nametag ghosts. */
        boolean dying = this.isDead() || this.getHealth() <= 0F || this.deathTime > 0;

        if (this.pauseNaturalAnimations && !dying)
        {
            /* Hold limbs / emoticon clocks; still allow swipe hand-swing progress. */
            this.tickHandSwing();
            this.updateHitboxDimensions();

            if (!this.getEntityWorld().isClient())
            {
                this.tickItemPickup();
            }

            return;
        }

        /* Poof burst on the last living death tick — same timing as MobDeathActionClip. */
        if (this.getEntityWorld().isClient() && dying && this.deathTime == 19)
        {
            this.spawnDeathBurstParticles();
        }

        super.tick();

        this.tickHandSwing();
        this.updateHitboxDimensions();

        Vec3d animationVelocity = this.animationVelocityHint;

        this.animationVelocityHint = null;

        if (animationVelocity != null)
        {
            this.setVelocity(animationVelocity);
        }

        try
        {
            if (this.form != null && !dying)
            {
                this.form.update(this.entity);
            }
        }
        finally
        {
            if (animationVelocity != null)
            {
                /* Keep physics velocity cleared; next client sync snaps position again. */
                this.setVelocity(0D, 0D, 0D);
            }
        }

        if (!this.getEntityWorld().isClient())
        {
            this.tickItemPickup();
        }
    }

    private void spawnDeathBurstParticles()
    {
        Random random = this.getEntityWorld().getRandom();
        double x = this.getX();
        double y = this.getY() + this.getEyeHeight(this.getPose()) * 0.5D;
        double z = this.getZ();
        float width = 0.6F;

        for (int i = 0; i < 20; i++)
        {
            double offsetX = (random.nextDouble() - 0.5D) * width;
            double offsetY = random.nextDouble() * 0.5D;
            double offsetZ = (random.nextDouble() - 0.5D) * width;
            double velocityX = random.nextGaussian() * 0.02D;
            double velocityY = random.nextGaussian() * 0.02D;
            double velocityZ = random.nextGaussian() * 0.02D;

            this.getEntityWorld().addParticleClient(ParticleTypes.POOF, x + offsetX, y + offsetY, z + offsetZ, velocityX, velocityY, velocityZ);
        }
    }

    private void tickItemPickup()
    {
        /* Don't pickup items when dead */
        if (this.isDead())
        {
            return;
        }

        /* Pickup items */
        Box box = this.getBoundingBox().expand(1D, 0.5D, 1D);
        List<Entity> list = this.getEntityWorld().getOtherEntities(this, box);

        for (Entity entity : list)
        {
            if (entity instanceof ItemEntity itemEntity)
            {
                UUID entityId = itemEntity.getUuid();
                ItemStack itemStack = itemEntity.getStack();
                int i = itemStack.getCount();

                if (!entity.isRemoved() && !itemEntity.cannotPickup() && !this.pickedUpEntityIds.contains(entityId))
                {
                    this.pickedUpEntityIds.add(entityId);
                    this.addToRuntimeInventory(itemStack.copy());
                    
                    ((ServerWorld) this.getEntityWorld()).getChunkManager().sendToOtherNearbyPlayers(entity, new ItemPickupAnimationS2CPacket(entity.getId(), this.getId(), i));
                    entity.discard();
                }
            }
        }
    }

    private void addToRuntimeInventory(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        if (!this.runtimeInventoryInitialized)
        {
            this.initializeRuntimeInventory();
        }

        int remaining = stack.getCount();

        for (int i = 0; i < this.runtimeInventory.size(); i++)
        {
            ItemStack existing = this.runtimeInventory.get(i);

            if (existing.isEmpty())
            {
                int move = Math.min(remaining, stack.getMaxCount());
                ItemStack copy = stack.copy();
                copy.setCount(move);
                this.runtimeInventory.set(i, copy);
                remaining -= move;

                if (remaining <= 0)
                {
                    return;
                }
            }
            else if (ItemStack.areItemsAndComponentsEqual(existing, stack) && existing.getCount() < existing.getMaxCount())
            {
                int space = existing.getMaxCount() - existing.getCount();
                int move = Math.min(space, remaining);
                existing.increment(move);
                remaining -= move;

                if (remaining <= 0)
                {
                    return;
                }
            }
        }

        if (remaining > 0)
        {
            ItemStack copy = stack.copy();
            copy.setCount(remaining);
            this.runtimeInventory.add(copy);
        }
    }

    @Override
    public void setSneaking(boolean sneaking)
    {
        super.setSneaking(sneaking);

        if (this.form != null && this.form.hitbox.get())
        {
            this.updateHitboxDimensions();
        }
    }

    private void updateHitboxDimensions()
    {
        if (this.form == null)
        {
            return;
        }

        boolean enabled = this.form.hitbox.get();
        boolean sneaking = this.isSneaking();
        float width = this.form.hitboxWidth.get();
        float height = this.form.hitboxHeight.get();
        float sneakMultiplier = this.form.hitboxSneakMultiplier.get();
        float eyeHeight = this.form.hitboxEyeHeight.get();

        if (enabled != this.lastHitboxEnabled
            || sneaking != this.lastSneaking
            || width != this.lastHitboxWidth
            || height != this.lastHitboxHeight
            || sneakMultiplier != this.lastHitboxSneakMultiplier
            || eyeHeight != this.lastHitboxEyeHeight)
        {
            this.lastHitboxEnabled = enabled;
            this.lastSneaking = sneaking;
            this.lastHitboxWidth = width;
            this.lastHitboxHeight = height;
            this.lastHitboxSneakMultiplier = sneakMultiplier;
            this.lastHitboxEyeHeight = eyeHeight;

            this.calculateDimensions();
        }
    }

    @Override
    public EntityDimensions getBaseDimensions(EntityPose pose)
    {
        EntityDimensions dimensions = super.getBaseDimensions(pose);
        Form currentForm = this.form;

        if (currentForm != null && currentForm.hitbox.get())
        {
            float height = currentForm.hitboxHeight.get() * (this.isSneaking() ? currentForm.hitboxSneakMultiplier.get() : 1F);
            float eyeHeight = currentForm.hitboxEyeHeight.get() * height;
            EntityDimensions shaped = dimensions.fixed()
                ? EntityDimensions.fixed(currentForm.hitboxWidth.get(), height)
                : EntityDimensions.changing(currentForm.hitboxWidth.get(), height);

            return shaped.withEyeHeight(eyeHeight);
        }

        return dimensions;
    }



    @Override
    public void takeKnockback(double strength, double x, double z)
    {
        /* Film actors are pose-driven by keyframes; vanilla hit knockback causes
         * a visible hop on lethal hits. */
        if (this.replay != null)
        {
            return;
        }

        super.takeKnockback(strength, x, z);
    }

    @Override
    public void onDeath(DamageSource damageSource)
    {
        super.onDeath(damageSource);
        
        if (!this.getEntityWorld().isClient() && !this.replayItemsDropped && this.replay != null && this.film != null && this.replay.dropItemsOnDeath.get())
        {
            this.dropReplayItems();
            this.replayItemsDropped = true;
        }
    }

    /**
     * Live hits keep HP/knockback. Flash overlay and procedural/emoticon damage
     * pose are gated by {@link BBSSettings#actorDamageFlash} /
     * {@link BBSSettings#actorDamageAnimation}. Keyframed damage still applies
     * via {@link ActorReplayStateSync}.
     * <p>
     * Only {@link #onDamaged} / {@link #animateDamage} are overridden — {@code damage()}
     * already calls {@link #onDamaged}, so a second hook there would double-run.
     */
    @Override
    public void onDamaged(DamageSource damageSource)
    {
        super.onDamaged(damageSource);
        /* super already set limbAnimator speed to 1.5F */
        this.gateLiveDamageReaction(true);
    }

    @Override
    public void animateDamage(float yaw)
    {
        if (!BBSSettings.shouldKeepActorLiveHurtTime())
        {
            return;
        }

        super.animateDamage(yaw);
        /* animateDamage only sets hurtTime */
        this.gateLiveDamageReaction(false);
    }

    private void gateLiveDamageReaction(boolean limbSpikeFromSuper)
    {
        boolean playAnim = BBSSettings.shouldPlayActorDamageAnimation();

        if (!BBSSettings.shouldKeepActorLiveHurtTime())
        {
            this.hurtTime = 0;
            this.maxHurtTime = 0;
            this.setLimbSwingSpeed(0F);
            this.pendingHurtAnimation = false;

            return;
        }

        if (playAnim)
        {
            if (!limbSpikeFromSuper)
            {
                this.setLimbSwingSpeed(1.5F);
            }

            if (this.getEntityWorld().isClient())
            {
                this.pendingHurtAnimation = true;
            }
        }
        else
        {
            this.setLimbSwingSpeed(0F);
            this.pendingHurtAnimation = false;
        }
    }

    public boolean shouldPreserveLiveHurtLimbSwing()
    {
        return BBSSettings.shouldPlayActorDamageAnimation() && this.hurtTime > 0;
    }

    public boolean shouldShowDamageFlashOverlay()
    {
        if (this.deathTime > 0 || this.keyframeHurtActive)
        {
            return true;
        }

        return this.hurtTime > 0 && BBSSettings.shouldFlashActorLiveDamage();
    }

    public boolean consumePendingHurtAnimation()
    {
        if (!this.pendingHurtAnimation)
        {
            return false;
        }

        this.pendingHurtAnimation = false;

        return true;
    }

    public void setKeyframeHurtActive(boolean active)
    {
        this.keyframeHurtActive = active;
    }

    /**
     * {@code deathTime} is not a synced field. After {@code ActionPlayer.goTo} restores HP
     * on scrub, the client can keep a leftover death tip / red corpse flash. Only clear when
     * {@code deathTime} is still &gt; 0 while already alive — do not touch live {@code hurtTime}
     * damage flash.
     */
    public void clearStaleCombatDeathIfAlive()
    {
        if (this.deathTime <= 0 || this.getHealth() <= 0F || this.isDead())
        {
            return;
        }

        this.deathTime = 0;
        this.hurtTime = 0;
        this.maxHurtTime = 0;
        this.keyframeHurtActive = false;
        this.pendingHurtAnimation = false;
    }

    private void setLimbSwingSpeed(float speed)
    {
        if (this.limbAnimator instanceof LimbAnimatorAccessor limb)
        {
            limb.setPrevSpeed(speed <= 0F ? 0F : limb.getSpeed());
            limb.setSpeed(speed);
        }
    }

    /**
     * Actor-mode invulnerability from the nested {@code invulnerable} keyframe track.
     * Blocks live hits / action-clip damage while keyframed {@code damage} hurt flash
     * still applies via {@link ActorReplayStateSync}.
     */
    @Override
    public boolean isInvulnerableTo(ServerWorld world, DamageSource damageSource)
    {
        if (this.isKeyframeInvulnerable())
        {
            return true;
        }

        return super.isInvulnerableTo(world, damageSource);
    }

    private boolean isKeyframeInvulnerable()
    {
        if (this.replay == null || this.replay.keyframes == null)
        {
            return false;
        }

        Form form = this.form;

        if (form == null && this.replay.form != null)
        {
            form = this.replay.form.get();
        }

        return this.replay.keyframes.isInvulnerableAt((float) this.currentTick, form);
    }
    
    /**
     * Drop items from the replay's inventory and equipment when it dies
     * Mimics vanilla Minecraft item drop behavior
     */
    private void dropReplayItems()
    {
        List<ItemStack> inventoryStacks = this.runtimeInventoryInitialized
            ? this.runtimeInventory
            : (this.replay.inventory == null ? Collections.emptyList() : this.replay.inventory.getStacks());
        boolean hasInventoryData = !inventoryStacks.isEmpty();
        boolean inventoryHasItems = false;

        if (hasInventoryData)
        {
            for (ItemStack stack : inventoryStacks)
            {
                if (stack != null && !stack.isEmpty())
                {
                    inventoryHasItems = true;
                    break;
                }
            }
        }

        boolean inventoryLikelyIncludesEquipment = inventoryStacks.size() >= 40;
        boolean dropEquipment = !hasInventoryData || !inventoryHasItems || !inventoryLikelyIncludesEquipment;

        // Drop equipped items from keyframes at current tick
        if (dropEquipment && this.replay.keyframes != null)
        {
            float tick = (float) this.currentTick;
            
            // Drop main hand item
            ItemStack mainHand = this.replay.keyframes.mainHand.interpolate(tick, ItemStack.EMPTY);
            if (!mainHand.isEmpty())
            {
                this.dropItemStack(mainHand.copy());
            }
            
            // Drop off hand item
            ItemStack offHand = this.replay.keyframes.offHand.interpolate(tick, ItemStack.EMPTY);
            if (!offHand.isEmpty())
            {
                this.dropItemStack(offHand.copy());
            }
            
            // Drop armor pieces
            ItemStack armorHead = this.replay.keyframes.armorHead.interpolate(tick, ItemStack.EMPTY);
            if (!armorHead.isEmpty())
            {
                this.dropItemStack(armorHead.copy());
            }
            
            ItemStack armorChest = this.replay.keyframes.armorChest.interpolate(tick, ItemStack.EMPTY);
            if (!armorChest.isEmpty())
            {
                this.dropItemStack(armorChest.copy());
            }
            
            ItemStack armorLegs = this.replay.keyframes.armorLegs.interpolate(tick, ItemStack.EMPTY);
            if (!armorLegs.isEmpty())
            {
                this.dropItemStack(armorLegs.copy());
            }
            
            ItemStack armorFeet = this.replay.keyframes.armorFeet.interpolate(tick, ItemStack.EMPTY);
            if (!armorFeet.isEmpty())
            {
                this.dropItemStack(armorFeet.copy());
            }
        }
        
        // Drop items from replay inventory if available
        if (hasInventoryData && inventoryHasItems)
        {
            for (ItemStack stack : inventoryStacks)
            {
                if (stack != null && !stack.isEmpty())
                {
                    this.dropItemStack(stack.copy());
                }
            }
        }
    }
    
    /**
     * Drop a single item stack with configurable physics from replay settings
     */
    private void dropItemStack(ItemStack stack)
    {
        if (stack.isEmpty() || this.replay == null)
        {
            return;
        }
        
        // Create item entity at actor's position
        ItemEntity itemEntity = new ItemEntity(
            this.getEntityWorld(),
            this.getX(),
            this.getY() + 0.5,
            this.getZ(),
            stack
        );
        
        // Apply random velocity using replay's configured values
        float minX = this.replay.dropVelocityMinX.get();
        float maxX = this.replay.dropVelocityMaxX.get();
        float minY = this.replay.dropVelocityMinY.get();
        float maxY = this.replay.dropVelocityMaxY.get();
        float minZ = this.replay.dropVelocityMinZ.get();
        float maxZ = this.replay.dropVelocityMaxZ.get();
        
        // Debug: Print velocity values to console
        System.out.println("[BBS Debug] Drop velocities - X: [" + minX + ", " + maxX + "], Y: [" + minY + ", " + maxY + "], Z: [" + minZ + ", " + maxZ + "]");
        
        double velocityX = minX + this.random.nextDouble() * (maxX - minX);
        double velocityY = minY + this.random.nextDouble() * (maxY - minY);
        double velocityZ = minZ + this.random.nextDouble() * (maxZ - minZ);
        
        itemEntity.setVelocity(velocityX, velocityY, velocityZ);
        itemEntity.setToDefaultPickupDelay();
        
        this.getEntityWorld().spawnEntity(itemEntity);
    }


    @Override
    public void checkDespawn()
    {
        super.checkDespawn();

        if (this.despawn)
        {
            this.discard();
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player)
    {
        super.onStartedTrackingBy(player);

        ServerNetwork.sendEntityForm(player, this);
    }

    @Override
    public void readCustomData(ReadView view)
    {
        super.readCustomData(view);

        this.despawn = view.getBoolean("despawn", false);

        if (view.contains("Equipment"))
        {
            NbtCompound equipmentNbt = view.read("Equipment", NbtCompound.CODEC).orElse(null);
            if (equipmentNbt == null) return;
            RegistryWrapper.WrapperLookup registries = this.getEntityWorld() != null ? this.getEntityWorld().getRegistryManager() : BBSMod.getRegistryManager();

            for (EquipmentSlot slot : EquipmentSlot.values())
            {
                if (equipmentNbt.contains(slot.getName()))
                {
                    NbtCompound itemNbt = equipmentNbt.getCompound(slot.getName()).orElse(null);
                    if (itemNbt == null) continue;
                    ItemStack stack = registries != null
                        ? ItemStack.CODEC.parse(RegistryOps.of(NbtOps.INSTANCE, registries), itemNbt).result().orElse(ItemStack.EMPTY)
                        : ItemStack.EMPTY;

                    this.equipment.put(slot, stack);
                }
            }
        }
    }

    @Override
    public void writeCustomData(WriteView view)
    {
        super.writeCustomData(view);

        view.putBoolean("despawn", true);

        NbtCompound equipmentNbt = new NbtCompound();
        RegistryWrapper.WrapperLookup registries = this.getEntityWorld() != null ? this.getEntityWorld().getRegistryManager() : BBSMod.getRegistryManager();

        for (Map.Entry<EquipmentSlot, ItemStack> entry : this.equipment.entrySet())
        {
            if (!entry.getValue().isEmpty())
            {
                ItemStack stack = entry.getValue();
                NbtElement itemNbt = registries != null
                    ? ItemStack.CODEC.encodeStart(RegistryOps.of(NbtOps.INSTANCE, registries), stack).result().orElse(null)
                    : ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result().orElse(null);

                if (itemNbt instanceof NbtCompound compound)
                {
                    equipmentNbt.put(entry.getKey().getName(), compound);
                }
            }
        }

        view.put("Equipment", NbtCompound.CODEC, equipmentNbt);
    }

    protected int getPermissionLevel()
    {
        return 4;
    }
}
