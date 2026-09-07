package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.DamageActionClip;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.ActorReplayStateSync;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ActionPlayer
{
    private static final int DEATH_ANIMATION_TICKS = 20;
    public Film film;
    public int tick;
    public boolean playing = true;
    public int countdown;
    public int exception;
    /**
     * Replay index currently driven by film-editor actor-control ({@code -1} = none).
     * While set, {@link #tick()} must not snap that actor back to keyframe pose.
     */
    public int controlledReplay = -1;
    public PlayerType type;

    public boolean syncing;
    public boolean stopDamage = true;

    /**
     * When true, skip applying action clips (used while HQ export settles the world).
     */
    private boolean freezeActions;
    /**
     * HQ export: skip applying actions in {@link #tick()}; film sync drives them instead.
     */
    private boolean exportSyncOnly;
    private float lastActionTime;

    private ServerPlayerEntity serverPlayer;
    private ServerWorld world;
    private int duration;
    private boolean wasPlaying = true;

    private Map<String, LivingEntity> actors = new HashMap<>();
    /**
     * Actor replays that finished a combat death this session. Kept out of
     * {@link #ensureMissingActors()} so Play/Pause at the current tick cannot
     * revive them mid-timeline (Alt+R / seek rebuild clears this).
     */
    private Set<String> combatFinishedIds = new HashSet<>();

    private List<ItemStack> cachedInventory = new ArrayList<>();
    private Form cachedForm;

    private float cacheHp;
    private float cacheHunger;
    private int cacheXpLevel;
    private float cacheXpProgress;

    public ActionPlayer(ServerPlayerEntity serverPlayer, ServerWorld world, Film film, int tick, int countdown, int exception, PlayerType type)
    {
        this.world = world;
        this.film = film;
        this.tick = tick;
        this.countdown = countdown;
        this.exception = exception;
        this.type = type;

        this.serverPlayer = serverPlayer;
        this.duration = film.camera.calculateDuration();
        this.lastActionTime = tick - 1F;

        this.updateReplayEntities();

        Replay fpReplay = film.getFirstPersonReplay();

        if (this.type == PlayerType.NORMAL && this.serverPlayer != null && fpReplay != null)
        {
            for (int i = 0; i < this.serverPlayer.getInventory().size(); i++)
            {
                this.cachedInventory.add(serverPlayer.getInventory().getStack(i).copy());
                this.serverPlayer.getInventory().setStack(i, CollectionUtils.getSafe(fpReplay.inventory.getStacks(), i, ItemStack.EMPTY));
            }

            Morph morph = Morph.getMorph(this.serverPlayer);

            if (morph != null)
            {
                this.cachedForm = FormUtils.copy(morph.getForm());
            }

            ServerNetwork.sendMorphToTracked(this.serverPlayer, fpReplay.form.get());

            this.cacheHp = this.serverPlayer.getHealth();
            this.cacheHunger = this.serverPlayer.getHungerManager().getSaturationLevel();
            this.cacheXpLevel = this.serverPlayer.experienceLevel;
            this.cacheXpProgress = this.serverPlayer.experienceProgress;

            this.serverPlayer.setHealth(this.film.hp.get());
            this.serverPlayer.getHungerManager().setSaturationLevel(this.film.hunger.get());
            this.serverPlayer.experienceProgress = this.film.xpProgress.get();
            this.serverPlayer.setExperienceLevel(this.film.xpLevel.get());
        }
    }

    public void updateReplayEntities()
    {
        this.combatFinishedIds.clear();

        for (LivingEntity entity : this.actors.values())
        {
            if (!entity.isPlayer())
            {
                entity.discard();
            }
        }

        this.actors.clear();

        List<Replay> list = this.film.replays.getList();

        for (int i = 0; i < list.size(); i++)
        {
            Replay replay = list.get(i);

            if (i == this.exception || !this.shouldTrackActor(replay) || !replay.enabled.get())
            {
                continue;
            }

            if (this.shouldUseServerPlayer(replay))
            {
                this.actors.put(replay.getId(), this.serverPlayer);
            }
            else
            {
                ActorEntity actor = this.spawnActor(replay);

                this.actors.put(replay.getId(), actor);
            }
        }

        this.broadcastActors();
    }

    private ActorEntity spawnActor(Replay replay)
    {
        ActorEntity actor = new ActorEntity(BBSMod.ACTOR_ENTITY, this.world);

        actor.setForm(FormUtils.copy(replay.form.get()));
        actor.setReplayData(this.film, replay, this.tick);

        this.apply(actor, replay, this.tick, false);

        if (!this.playing)
        {
            actor.setVelocity(0D, 0D, 0D);
        }

        this.world.spawnEntity(actor);

        return actor;
    }

    private boolean shouldUseServerPlayer(Replay replay)
    {
        return replay.fp.get() && this.serverPlayer != null && this.type == PlayerType.NORMAL;
    }

    private boolean shouldTrackActor(Replay replay)
    {
        return replay.actor.get() || this.shouldUseServerPlayer(replay);
    }

    private boolean shouldSpawnActorEntity(Replay replay)
    {
        return replay.actor.get() && !this.shouldUseServerPlayer(replay);
    }

    private void broadcastActors()
    {
        for (ServerPlayerEntity player : this.world.getPlayers())
        {
            ServerNetwork.sendActors(player, this.film.getId(), this.actors);
        }
    }

    /**
     * Respawn actor-mode bodies missing from the map (e.g. after a combat death)
     * so seeking / undo can revive them before actions re-apply.
     */
    private void ensureMissingActors()
    {
        List<Replay> list = this.film.replays.getList();
        boolean changed = false;

        for (int i = 0; i < list.size(); i++)
        {
            Replay replay = list.get(i);

            if (i == this.exception || !this.shouldTrackActor(replay) || !replay.enabled.get())
            {
                continue;
            }

            LivingEntity existing = this.actors.get(replay.getId());

            if (this.combatFinishedIds.contains(replay.getId()))
            {
                continue;
            }

            if (existing == null || existing.isRemoved())
            {
                LivingEntity actor = this.shouldUseServerPlayer(replay) ? this.serverPlayer : this.spawnActor(replay);

                this.actors.put(replay.getId(), actor);
                changed = true;
            }
        }

        if (changed)
        {
            this.broadcastActors();
        }
    }

    public ServerWorld getWorld()
    {
        return this.world;
    }

    public LivingEntity getActor(String replayId)
    {
        return replayId == null || replayId.isEmpty() ? null : this.actors.get(replayId);
    }

    public Map<String, LivingEntity> getActors()
    {
        return this.actors;
    }

    public void apply(LivingEntity actor, Replay replay, float tick, boolean ticking)
    {
        /* Once combat (Attack clip) has killed this actor, keep snapping to
         * keyframed pose (no knockback hop) while death anim plays. */
        if (actor instanceof ActorEntity && (actor.isDead() || actor.getHealth() <= 0F))
        {
            if (actor.deathTime >= DEATH_ANIMATION_TICKS)
            {
                if (!actor.isRemoved())
                {
                    actor.discard();
                }

                this.combatFinishedIds.add(replay.getId());

                return;
            }

            double x = replay.keyframes.x.interpolate(tick);
            double y = replay.keyframes.y.interpolate(tick);
            double z = replay.keyframes.z.interpolate(tick);
            float yawHead = replay.keyframes.headYaw.interpolate(tick).floatValue();
            float yawBody = replay.keyframes.bodyYaw.interpolate(tick).floatValue();
            float pitch = replay.keyframes.pitch.interpolate(tick).floatValue();

            actor.setVelocity(0D, 0D, 0D);
            actor.setPosition(x, y, z);
            actor.setYaw(yawHead);
            actor.setHeadYaw(yawHead);
            actor.setPitch(pitch);
            actor.setBodyYaw(yawBody);

            /* Seed deathTime so MobForm tip + discard can progress if entity.tick
             * has not advanced it yet this phase. Do not read death_time keyframes
             * into ActorEntity (scrubs stuck the red overlay). */
            if (ticking && actor.deathTime <= 0)
            {
                actor.deathTime = 1;
            }

            if (actor instanceof ActorEntity actorEntity)
            {
                actorEntity.syncNameTag(replay);
                actorEntity.updateTick((int) tick);
            }

            return;
        }

        double x = replay.keyframes.x.interpolate(tick);
        double y = replay.keyframes.y.interpolate(tick);
        double z = replay.keyframes.z.interpolate(tick);
        float yawHead = replay.keyframes.headYaw.interpolate(tick).floatValue();
        float yawBody = replay.keyframes.bodyYaw.interpolate(tick).floatValue();
        float pitch = replay.keyframes.pitch.interpolate(tick).floatValue();
        boolean grounded = replay.keyframes.grounded.interpolate(tick) > 0;

        Vec3d pos = actor.getPos();

        if (ticking)
        {
            actor.setOnGround(grounded);
            actor.move(MovementType.SELF, new Vec3d(x - pos.x, y - pos.y, z - pos.z));
        }

        actor.setPosition(x, y, z);
        actor.setYaw(yawHead);
        actor.setHeadYaw(yawHead);
        actor.setPitch(pitch);
        actor.setBodyYaw(yawBody);
        actor.setSneaking(replay.keyframes.sneaking.interpolate(tick) > 0);
        actor.setOnGround(grounded);
        /* Apply full vanilla pose/action keyframes (sprinting, swimming, limbs, …) so
         * actor-mode procedural/Gecko animators match stub playback. */
        ActorReplayStateSync.applyFromKeyframes(replay.keyframes, tick, actor, actor.hasVehicle(), ticking);
        ActionPlayer.equipIfChanged(actor, EquipmentSlot.OFFHAND, replay.keyframes.offHand.interpolate(tick, ItemStack.EMPTY));
        ActionPlayer.equipIfChanged(actor, EquipmentSlot.HEAD, replay.keyframes.armorHead.interpolate(tick, ItemStack.EMPTY));
        ActionPlayer.equipIfChanged(actor, EquipmentSlot.CHEST, replay.keyframes.armorChest.interpolate(tick, ItemStack.EMPTY));
        ActionPlayer.equipIfChanged(actor, EquipmentSlot.LEGS, replay.keyframes.armorLegs.interpolate(tick, ItemStack.EMPTY));
        ActionPlayer.equipIfChanged(actor, EquipmentSlot.FEET, replay.keyframes.armorFeet.interpolate(tick, ItemStack.EMPTY));

        if (actor instanceof ServerPlayerEntity player)
        {
            int selectedSlot = player.getInventory().selectedSlot;
            Integer heldSlot = replay.keyframes.selectedSlot.interpolateHeld(this.tick);
            int slot = MathUtils.clamp(heldSlot == null ? 0 : heldSlot, 0, 8);

            if (selectedSlot != slot)
            {
                ServerNetwork.sendSelectedSlot(player, slot);
            }
        }

        ActionPlayer.equipIfChanged(actor, EquipmentSlot.MAINHAND, replay.keyframes.mainHand.interpolate(tick, ItemStack.EMPTY));

        if (actor instanceof ActorEntity actorEntity)
        {
            actorEntity.syncNameTag(replay);

            if (!ticking)
            {
                actor.setVelocity(0D, 0D, 0D);
            }
            else
            {
                /* Aim LivingEntity's post-ActionPlayer integration at the *next*
                 * keyframe. Backward Δ (t - (t-1)) matches constant speed, but on
                 * deceleration overshoots tick+1 and reads as extra coast vs stubs.
                 * Forward Δ keeps non-zero velocity for smooth interp without that
                 * stop overshoot. Non-actor / ServerPlayer paths stay unchanged. */
                double vx = replay.keyframes.x.interpolate(tick + 1) - x;
                double vy = replay.keyframes.y.interpolate(tick + 1) - y;
                double vz = replay.keyframes.z.interpolate(tick + 1) - z;

                if (vy == 0D)
                {
                    vy = -0.0784;
                }

                double scale = actorEntity.consumePlaybackVelocityScale();

                actor.setVelocity(vx * scale, vy, vz * scale);
            }
        }
        else
        {
            double vx = x - replay.keyframes.x.interpolate(tick - 1);
            double vy = y - replay.keyframes.y.interpolate(tick - 1);
            double vz = z - replay.keyframes.z.interpolate(tick - 1);

            if (vy == 0D)
            {
                vy = -0.0784;
            }

            actor.setVelocity(vx, vy, vz);
        }

        actor.fallDistance = replay.keyframes.fall.interpolate(tick).floatValue();
    }

    private static void equipIfChanged(LivingEntity actor, EquipmentSlot slot, ItemStack stack)
    {
        ItemStack next = stack == null ? ItemStack.EMPTY : stack;
        ItemStack current = actor.getEquippedStack(slot);

        if (!ItemStack.areEqual(current, next))
        {
            actor.equipStack(slot, next);
        }
    }

    public boolean tick()
    {
        if (this.countdown > 0)
        {
            this.countdown -= 1;

            return false;
        }

        boolean justResumed = this.playing && !this.wasPlaying;

        this.wasPlaying = this.playing;

        boolean actorsChanged = false;
        List<String> removeIds = new ArrayList<>();

        for (Map.Entry<String, LivingEntity> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());

            if (replay != null)
            {
                /* Editor actor-control owns this body — follow the editor player and
                 * hold velocity at zero so the server entity does not keep sliding with
                 * leftover keyframe/walk velocity while the client puppets.
                 * Match by replay id, not Replay.equals() (deep form/actions compare). */
                if (this.isControlledReplay(entry.getKey()))
                {
                    LivingEntity actor = entry.getValue();

                    if (actor != null && this.serverPlayer != null)
                    {
                        actor.setPosition(this.serverPlayer.getX(), this.serverPlayer.getY(), this.serverPlayer.getZ());
                        actor.setYaw(this.serverPlayer.getYaw());
                        actor.setHeadYaw(this.serverPlayer.getHeadYaw());
                        actor.setBodyYaw(this.serverPlayer.getBodyYaw());
                        actor.setPitch(this.serverPlayer.getPitch());
                        actor.setVelocity(0D, 0D, 0D);
                        actor.velocityModified = true;
                        /* Drive procedural walk from the editor player's natural limbs,
                         * not from teleport deltas on the snapped actor body. */
                        ActorReplayStateSync.syncFromSource(actor, new MCEntity(this.serverPlayer), true);

                        if (actor instanceof ActorEntity actorEntity)
                        {
                            actorEntity.updateTick(this.tick);
                            actorEntity.setSuppressSprintParticles(true);
                            actorEntity.setPauseNaturalAnimations(false);
                        }
                    }

                    continue;
                }

                LivingEntity actor = entry.getValue();

                /* After a combat death the body is discarded. Do not respawn from
                 * later movement keyframes mid-playback — that teleports the actor
                 * to where they "would have been". Seek/updateReplayEntities revives. */
                if (actor == null || actor.isRemoved())
                {
                    removeIds.add(entry.getKey());
                    this.combatFinishedIds.add(entry.getKey());
                    actorsChanged = true;

                    continue;
                }

                if (actor instanceof ActorEntity actorEntity)
                {
                    actorEntity.updateTick(this.tick);
                    actorEntity.setSuppressSprintParticles(false);

                    boolean pauseAnims = BBSSettings.editorActorPauseAnimations != null
                        && BBSSettings.editorActorPauseAnimations.get()
                        && !this.playing;

                    actorEntity.setPauseNaturalAnimations(pauseAnims);

                    if (justResumed)
                    {
                        actorEntity.markPlaybackResumed();
                        actorEntity.setPauseNaturalAnimations(false);
                    }
                }

                /* While paused: hold position without move()/walk velocity so
                 * LivingEntity limb swing decays naturally (player-stop style). */
                this.apply(actor, replay, this.tick, this.playing);

                if (actor.isRemoved())
                {
                    removeIds.add(entry.getKey());
                    this.combatFinishedIds.add(entry.getKey());
                    actorsChanged = true;
                }
                else if (!this.playing)
                {
                    actor.setVelocity(0D, 0D, 0D);
                }
            }
        }

        for (String id : removeIds)
        {
            this.actors.remove(id);
        }

        if (actorsChanged)
        {
            this.broadcastActors();
        }

        if (!this.playing)
        {
            return false;
        }

        /* HQ settle: keep actors posed on the current film tick; do not advance or fire. */
        if (this.freezeActions)
        {
            return false;
        }

        if (this.tick >= 0 && !this.exportSyncOnly)
        {
            this.applyAction();
            this.lastActionTime = this.tick;
        }

        this.tick += 1;

        return !this.syncing && this.tick >= this.duration;
    }

    private boolean isControlledReplay(String replayId)
    {
        if (this.controlledReplay < 0 || replayId == null)
        {
            return false;
        }

        List<Replay> list = this.film.replays.getList();

        return this.controlledReplay < list.size()
            && replayId.equals(list.get(this.controlledReplay).getId());
    }

    public void setFreezeActions(boolean freeze)
    {
        this.freezeActions = freeze;
    }

    public boolean isFreezeActions()
    {
        return this.freezeActions;
    }

    public void setExportSyncOnly(boolean syncOnly)
    {
        this.exportSyncOnly = syncOnly;
    }

    public boolean isExportSyncOnly()
    {
        return this.exportSyncOnly;
    }

    /**
     * Fire action clips whose times cross {@code (lastActionTime, filmTime]}.
     * Used during video export so commands hit the same fractional film clock as the camera.
     *
     * @return {@code true} if at least one action application ran
     */
    public boolean syncActionsTo(float filmTime)
    {
        if (this.countdown > 0 || !this.playing || this.freezeActions)
        {
            return false;
        }

        return this.applyActionsUpTo(filmTime) > 0;
    }

    private int applyActionsUpTo(float filmTime)
    {
        if (filmTime <= this.lastActionTime)
        {
            return 0;
        }

        SuperFakePlayer fakePlayer = SuperFakePlayer.get(this.world);
        List<Replay> list = this.film.replays.getList();
        float prev = this.lastActionTime;
        float curr = filmTime;
        int fired = 0;

        for (int i = 0; i < list.size(); i++)
        {
            if (i == this.exception || i == this.controlledReplay)
            {
                continue;
            }

            Replay replay = list.get(i);

            if (!replay.enabled.get() || replay.actions.isEmpty())
            {
                continue;
            }

            if (this.combatFinishedIds.contains(replay.getId()))
            {
                continue;
            }

            LivingEntity actor = this.actors.get(replay.getId());

            if (actor != null && (actor.isDead() || actor.getHealth() <= 0F || actor.deathTime > 0 || actor.isRemoved()))
            {
                continue;
            }

            if ((replay.actor.get() || replay.fp.get()) && (actor == null || actor.isRemoved()))
            {
                continue;
            }

            fired += replay.applyActionsCrossing(actor, fakePlayer, this.film, prev, curr);
        }

        this.lastActionTime = filmTime;

        return fired;
    }

    private void applyAction()
    {
        this.applyActionsFiltered(false);
    }

    /**
     * Seek scrub: fire world clips (drops, swipe, …) like before, but skip
     * Attack/Damage — cumulative HP is applied separately via silent calc so
     * scrubbing cannot spam hits or forget earlier damage.
     */
    private void applyNonCombatActions()
    {
        this.applyActionsFiltered(true);
    }

    private void applyActionsFiltered(boolean skipCombatClips)
    {
        SuperFakePlayer fakePlayer = SuperFakePlayer.get(this.world);
        List<Replay> list = this.film.replays.getList();

        for (int i = 0; i < list.size(); i++)
        {
            /* Outside RECORDING uses exception; editor puppet / viewport record uses
             * controlledReplay — both must skip pre-recorded Attack/Swipe/etc. so the
             * live player is the only source of those clips for that replay. */
            if (i == this.exception || i == this.controlledReplay)
            {
                continue;
            }

            Replay replay = list.get(i);

            if (!replay.enabled.get())
            {
                continue;
            }

            /* Combat-dead actors are removed from the map; without this guard their
             * Attack clips keep firing through SuperFakePlayer + bound target. */
            if (this.combatFinishedIds.contains(replay.getId()))
            {
                continue;
            }

            LivingEntity actor = this.actors.get(replay.getId());

            if (actor != null && (actor.isDead() || actor.getHealth() <= 0F || actor.deathTime > 0 || actor.isRemoved()))
            {
                continue;
            }

            /* Actor-mode replay with no living body (discarded mid-play): never
             * fall back to FakePlayer combat / world clips as if still alive. */
            if ((replay.actor.get() || replay.fp.get()) && (actor == null || actor.isRemoved()))
            {
                continue;
            }

            if (!skipCombatClips)
            {
                replay.applyActions(actor, fakePlayer, this.film, this.tick);

                continue;
            }

            for (Clip clip : replay.actions.getClips(this.tick))
            {
                if (clip instanceof AttackActionClip || clip instanceof DamageActionClip)
                {
                    continue;
                }

                if (clip instanceof ActionClip actionClip)
                {
                    actionClip.apply(actor, fakePlayer, this.film, replay, this.tick);
                }
            }
        }
    }

    public void syncData(DataPath key, BaseType data)
    {
        BaseValue baseValue = this.resolveValue(key);

        if (baseValue != null)
        {
            baseValue.fromData(data);

            /* Full-film undos replace keyframes + actor flags together — respawn so
             * ActorEntity is not left on a pre-undo pose after Ctrl+Z. */
            if (baseValue instanceof Film
                || baseValue.getId().equals("actor")
                || baseValue.getId().equals("enabled")
                || baseValue.getId().equals("replays"))
            {
                int keepTick = this.tick;

                this.updateReplayEntities();
                /* updateReplayEntities clears combatFinishedIds and respawns at full HP.
                 * Re-run silent combat at the current film tick so dead actors stay dead
                 * and the next hit still kills when it should. */
                this.goTo(keepTick);
            }
            else if (baseValue instanceof ValueForm || baseValue.getId().equals("form"))
            {
                /* Form swaps must update ActorEntity / FP morph copies. reapplyActors alone
                 * keeps the old mesh (and gizmo bone matrices) until Alt+R. */
                this.syncActorFormsFromReplays();
                this.reapplyActors();
            }
            else
            {
                /* Keyframes / properties: keep live actors on the
                 * updated timeline without discarding entities. */
                this.reapplyActors();
            }
        }
    }

    /**
     * Push {@link Replay#form} onto live actor bodies and notify clients.
     * Stubs are rebuilt client-side via {@code createEntities}; actors are not.
     */
    private void syncActorFormsFromReplays()
    {
        Replay fpReplay = this.film.getFirstPersonReplay();

        for (Map.Entry<String, LivingEntity> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());
            LivingEntity actor = entry.getValue();

            if (replay == null || actor == null || actor.isRemoved())
            {
                continue;
            }

            Form formCopy = FormUtils.copy(replay.form.get());

            if (actor instanceof ActorEntity actorEntity)
            {
                actorEntity.setForm(formCopy);

                for (ServerPlayerEntity player : this.world.getPlayers())
                {
                    ServerNetwork.sendEntityForm(player, actorEntity);
                }
            }
            else if (this.serverPlayer != null && actor == this.serverPlayer
                && fpReplay != null && replay.getId().equals(fpReplay.getId()))
            {
                Morph morph = Morph.getMorph(this.serverPlayer);

                if (morph != null)
                {
                    morph.setForm(formCopy);
                }

                ServerNetwork.sendMorphToTracked(this.serverPlayer, formCopy);
            }
        }
    }

    private void reapplyActors()
    {
        boolean actorsChanged = false;
        List<String> removeIds = new ArrayList<>();

        for (Map.Entry<String, LivingEntity> entry : this.actors.entrySet())
        {
            Replay replay = (Replay) this.film.replays.get(entry.getKey());

            if (replay != null)
            {
                LivingEntity actor = entry.getValue();

                if (actor == null || actor.isRemoved())
                {
                    if (!this.shouldSpawnActorEntity(replay) || this.combatFinishedIds.contains(replay.getId()))
                    {
                        if (actor != null && actor.isRemoved())
                        {
                            removeIds.add(entry.getKey());
                            actorsChanged = true;
                        }

                        continue;
                    }

                    actor = this.spawnActor(replay);
                    entry.setValue(actor);
                    actorsChanged = true;
                }

                this.apply(actor, replay, this.tick, false);

                if (actor.isRemoved())
                {
                    removeIds.add(entry.getKey());
                    this.combatFinishedIds.add(entry.getKey());
                    actorsChanged = true;
                }
                else if (!this.playing)
                {
                    actor.setVelocity(0D, 0D, 0D);
                }
            }
        }

        for (String id : removeIds)
        {
            this.actors.remove(id);
        }

        if (actorsChanged)
        {
            this.broadcastActors();
        }
    }

    private BaseValue resolveValue(DataPath path)
    {
        BaseValue current = this.film;

        int start = 0;

        if (this.film != null && path.size() > 0)
        {
            String filmId = this.film.getId();

            if (filmId != null && !filmId.isEmpty() && filmId.equals(path.strings.get(0)))
            {
                start = 1;
            }
        }

        for (int i = start; i < path.size(); i++)
        {
            String part = path.strings.get(i);

            if (current instanceof BaseValueGroup group)
            {
                BaseValue next = group.get(part);

                if (next == null)
                {
                    if (group instanceof FormProperties formProps)
                    {
                        if (formProps.getParent() instanceof Replay replay)
                        {
                            next = formProps.getOrCreate(replay.form.get(), part);
                        }
                    }
                }

                if (next == null)
                {
                    return null;
                }

                current = next;
            }
            else
            {
                return null;
            }
        }

        return current;
    }

    public void goTo(int tick)
    {
        this.goTo(this.tick, tick);
    }

    /**
     * Play/Pause only: move the server tick without rebuilding combat or
     * re-firing clips (avoids the cursor vs cursor+1 revive on pause).
     */
    public void syncPlaybackTick(int tick)
    {
        this.tick = Math.max(0, tick);
        this.reapplyActors();
    }

    public void goTo(int from, int tick)
    {
        tick = Math.max(0, tick);
        from = Math.max(0, from);

        /* 1) Silent HP from all Attack/Damage clips in [0..tick] — preserves
         * damage taken before the scrub window (fixes “final hit doesn’t kill”). */
        this.syncCombatState(tick);

        /* 2) Same delta walk as before for world clips (item drops, etc.).
         * Combat clips are skipped here to avoid hit spam; HP already matches tick. */
        if (from != tick)
        {
            this.tick = from;

            while (this.tick != tick)
            {
                this.tick += this.tick > tick ? -1 : 1;
                this.applyNonCombatActions();
            }
        }
        else
        {
            this.tick = tick;
        }

        this.reapplyActors();
    }

    public void syncCombatState(int tick)
    {
        tick = Math.max(0, tick);

        Map<String, Float> health = this.computeSilentHealth(tick);

        /* Default: rebuild finished-death set from timeline HP so scrubbing before
         * a kill revives actors. Off = legacy: once dead this session, stay gone
         * until Alt+R / updateReplayEntities clears the set. */
        if (this.isReplayDeathTimelineSyncEnabled())
        {
            this.combatFinishedIds.clear();
        }

        for (Map.Entry<String, Float> entry : health.entrySet())
        {
            if (entry.getValue() <= 0F)
            {
                this.combatFinishedIds.add(entry.getKey());
            }
        }

        this.discardFinishedActors();
        this.ensureMissingActors();
        this.applySilentHealthToActors(health);
    }

    private boolean isReplayDeathTimelineSyncEnabled()
    {
        return BBSSettings.replayDeathTimelineSync == null || BBSSettings.replayDeathTimelineSync.get();
    }

    /**
     * Dry-run Attack/Damage into an HP map. Does not touch the world.
     */
    private Map<String, Float> computeSilentHealth(int tick)
    {
        Map<String, Float> health = new HashMap<>();
        List<Replay> list = this.film.replays.getList();

        for (int i = 0; i < list.size(); i++)
        {
            Replay replay = list.get(i);

            if (i == this.exception || i == this.controlledReplay || !this.isCombatTrackedReplay(replay))
            {
                continue;
            }

            health.put(replay.getId(), 20F);
        }

        if (health.isEmpty() || tick < 0)
        {
            return health;
        }

        for (int t = 0; t <= tick; t++)
        {
            for (int i = 0; i < list.size(); i++)
            {
                if (i == this.exception || i == this.controlledReplay)
                {
                    continue;
                }

                Replay replay = list.get(i);

                if (!replay.enabled.get())
                {
                    continue;
                }

                for (Clip clip : replay.actions.getClips(t))
                {
                    if (!this.shouldApplyActionClip(clip, t))
                    {
                        continue;
                    }

                    if (clip instanceof DamageActionClip damageClip)
                    {
                        this.applySilentDamage(health, replay.getId(), damageClip.damage.get());
                    }
                    else if (clip instanceof AttackActionClip attackClip)
                    {
                        String targetId = attackClip.target.get();

                        if (targetId != null && !targetId.isEmpty())
                        {
                            this.applySilentDamage(health, targetId, attackClip.damage.get());
                        }
                    }
                }
            }
        }

        return health;
    }

    private boolean isCombatTrackedReplay(Replay replay)
    {
        return replay != null
            && replay.enabled.get()
            && replay.actor.get()
            && !replay.fp.get();
    }

    private boolean shouldApplyActionClip(Clip clip, int tick)
    {
        if (!(clip instanceof ActionClip actionClip) || !actionClip.enabled.get())
        {
            return false;
        }

        int relative = tick - actionClip.tick.get();
        int frequency = actionClip.frequency.get();

        if (frequency == 0)
        {
            return relative == 0;
        }

        return relative >= 0 && relative % frequency == 0;
    }

    private void applySilentDamage(Map<String, Float> health, String replayId, float amount)
    {
        if (replayId == null || !health.containsKey(replayId))
        {
            return;
        }

        float current = health.get(replayId);

        if (current <= 0F)
        {
            return;
        }

        if (amount >= AttackDamage.MOB_KILLER_DAMAGE)
        {
            health.put(replayId, 0F);

            return;
        }

        if (amount <= 0F)
        {
            return;
        }

        health.put(replayId, Math.max(0F, current - amount));
    }

    private void applySilentHealthToActors(Map<String, Float> health)
    {
        for (Map.Entry<String, Float> entry : health.entrySet())
        {
            if (this.combatFinishedIds.contains(entry.getKey()))
            {
                continue;
            }

            LivingEntity actor = this.actors.get(entry.getKey());

            if (actor == null || actor.isRemoved() || actor.isPlayer())
            {
                continue;
            }

            float hp = Math.max(0.01F, entry.getValue());

            actor.deathTime = 0;
            actor.setHealth(Math.min(hp, actor.getMaxHealth()));
            actor.hurtTime = 0;
            actor.timeUntilRegen = 0;

            if (actor instanceof ActorEntity actorEntity)
            {
                actorEntity.setKeyframeHurtActive(false);
            }
        }
    }

    private void discardFinishedActors()
    {
        boolean changed = false;
        List<String> removeIds = new ArrayList<>();

        for (Map.Entry<String, LivingEntity> entry : this.actors.entrySet())
        {
            if (!this.combatFinishedIds.contains(entry.getKey()))
            {
                continue;
            }

            LivingEntity actor = entry.getValue();

            if (actor != null && !actor.isPlayer() && !actor.isRemoved())
            {
                actor.discard();
            }

            removeIds.add(entry.getKey());
            changed = true;
        }

        for (String id : removeIds)
        {
            this.actors.remove(id);
        }

        if (changed)
        {
            this.broadcastActors();
        }
    }

    public void stop()
    {
        SuperFakePlayer fakePlayer = SuperFakePlayer.get(this.world);

        for (Replay replay : this.film.replays.getList())
        {
            fakePlayer.closeReplayChest(replay.getId());
        }

        fakePlayer.closeHandledScreen();

        for (LivingEntity value : this.actors.values())
        {
            if (!value.isPlayer())
            {
                value.discard();
            }
        }

        if (this.type == PlayerType.NORMAL && this.serverPlayer != null && this.film.getFirstPersonReplay() != null)
        {
            for (int i = 0; i < this.serverPlayer.getInventory().size(); i++)
            {
                this.serverPlayer.getInventory().setStack(i, this.cachedInventory.get(i));
            }

            ServerNetwork.sendMorphToTracked(this.serverPlayer, this.cachedForm);

            this.serverPlayer.setHealth(this.cacheHp);
            this.serverPlayer.getHungerManager().setSaturationLevel(this.cacheHunger);
            this.serverPlayer.experienceProgress = this.cacheXpProgress;
            this.serverPlayer.setExperienceLevel(this.cacheXpLevel);
        }
    }

    public void toggle()
    {
        this.playing = !this.playing;
    }
}
