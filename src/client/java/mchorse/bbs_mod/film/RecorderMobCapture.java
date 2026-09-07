package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.types.MobDeathActionClip;
import mchorse.bbs_mod.actions.types.item.ItemDropActionClip;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.MobCemItemCapture;
import mchorse.bbs_mod.film.MobCemPoseCapture;
import mchorse.bbs_mod.film.replays.MountLink;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Captures world mobs into new film replays while {@link Recorder} is active.
 */
public final class RecorderMobCapture
{
    private static final int DEATH_ANIMATION_TICKS = 20;
    private static final double DROP_SCAN_RADIUS = 2D;
    private static final double CONVERSION_SCAN_RADIUS = 1.5D;
    private static final int CONVERSION_PENDING_TICKS = 40;
    private static final int CONVERSION_SUCCESSOR_MAX_AGE = 5;

    public static final class Session
    {
        public int entityId;
        public final int replayIndex;
        public final boolean livingEntity;
        public final UUID entityUuid;
        public final boolean playerEntity;
        public final boolean playerNametag;
        public final boolean playerModelForm;

        public int deathTickIndex = 0;
        public boolean deathHandled = false;
        public boolean recordingDeath = false;
        public boolean waitingForPlayerRespawn = false;

        public double lastX;
        public double lastY;
        public double lastZ;
        public float lastYaw;
        public float lastPitch;
        public float lastHeadYaw;
        public float lastBodyYaw;

        public double deathX;
        public double deathY;
        public double deathZ;
        public float deathYaw;
        public float deathPitch;
        public float deathHeadYaw;
        public float deathBodyYaw;

        public boolean tracksSnowTrail = false;
        public final Map<Long, BlockState> snowTrailSnapshots = new HashMap<>();

        public Boolean lastFire;
        public Boolean lastParticles;

        public Session(int entityId, int replayIndex, boolean livingEntity)
        {
            this(entityId, replayIndex, livingEntity, null, false, false, false);
        }

        public Session(int entityId, int replayIndex, boolean livingEntity, UUID entityUuid, boolean playerEntity, boolean playerNametag, boolean playerModelForm)
        {
            this.entityId = entityId;
            this.replayIndex = replayIndex;
            this.livingEntity = livingEntity;
            this.entityUuid = entityUuid;
            this.playerEntity = playerEntity;
            this.playerNametag = playerNametag;
            this.playerModelForm = playerModelForm;
        }
    }

    private static final class PendingConversion
    {
        public final String groupPath;
        public final boolean vanillaPlayback;
        public int ticksRemaining;

        public PendingConversion(String groupPath, boolean vanillaPlayback)
        {
            this.groupPath = groupPath == null ? "" : groupPath;
            this.vanillaPlayback = vanillaPlayback;
            this.ticksRemaining = CONVERSION_PENDING_TICKS;
        }
    }

    private static final class PendingConversionScan
    {
        public final double x;
        public final double y;
        public final double z;
        public final String groupPath;
        public final boolean vanillaPlayback;
        public int ticksRemaining;

        public PendingConversionScan(double x, double y, double z, String groupPath, boolean vanillaPlayback)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.groupPath = groupPath == null ? "" : groupPath;
            this.vanillaPlayback = vanillaPlayback;
            this.ticksRemaining = CONVERSION_PENDING_TICKS;
        }
    }

    private final List<Session> sessions = new ArrayList<>();
    private final Set<Integer> capturedEntityIds = new HashSet<>();
    private final Map<Integer, Integer> entityReplayIndices = new HashMap<>();
    private final Set<Integer> vanillaPlaybackEntityIds = new HashSet<>();
    private boolean capturePlayers = true;
    private boolean playerNametags = false;
    private boolean playerModelForms = false;
    private final Map<Integer, PendingConversion> pendingConversions = new HashMap<>();
    private final List<PendingConversionScan> pendingConversionScans = new ArrayList<>();
    private final Map<Integer, PendingConversion> recentSilentRemovals = new HashMap<>();
    private Film lastFilm;
    private int lastTick;

    public void applyRecordingSetup(MobCaptureRecordingSetup setup)
    {
        this.vanillaPlaybackEntityIds.clear();
        this.capturePlayers = true;
        this.playerNametags = false;
        this.playerModelForms = false;

        if (setup != null)
        {
            this.vanillaPlaybackEntityIds.addAll(setup.vanillaPlaybackEntityIds);
            this.capturePlayers = setup.capturePlayers;
            this.playerNametags = setup.playerNametags;
            this.playerModelForms = setup.playerModelForms;
        }
    }

    private void applyVanillaMobPlayback(Replay replay, boolean enabled)
    {
        if (replay.form.get() instanceof MobForm)
        {
            replay.vanillaMobPlayback.set(enabled);
            replay.vanillaMobPlaybackSerialized = true;
        }
    }

    public List<Session> getSessions()
    {
        return this.sessions;
    }

    public boolean isEmpty()
    {
        return this.sessions.isEmpty();
    }

    public void clear()
    {
        this.sessions.clear();
        this.capturedEntityIds.clear();
        this.entityReplayIndices.clear();
        this.vanillaPlaybackEntityIds.clear();
        this.capturePlayers = true;
        this.playerNametags = false;
        this.playerModelForms = false;
        this.pendingConversions.clear();
        this.pendingConversionScans.clear();
        this.recentSilentRemovals.clear();
        this.lastFilm = null;
        this.lastTick = 0;
    }

    public Set<Integer> getCapturedEntityIds()
    {
        return this.capturedEntityIds;
    }

    public int getReplayIndexForEntity(int entityId)
    {
        Integer index = this.entityReplayIndices.get(entityId);

        return index == null ? -1 : index;
    }

    public static boolean canCapture()
    {
        if (!BBSSettings.recordingAutoCaptureMobs.get())
        {
            return false;
        }

        Recorder recorder = BBSModClient.getFilms().getRecorder();

        return recorder != null && !recorder.hasNotStarted();
    }

    public static void onEntityInteraction(Entity target)
    {
        if (!canCapture())
        {
            return;
        }

        Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder != null)
        {
            recorder.getMobCapture().tryCapture(recorder, target);
        }
    }

    public static void recordMountKeyframes(List<Replay> replays, int riderIndex, ReplayKeyframes riderKeyframes, IEntity entity, int tick)
    {
        int mountIndex = -1;
        Entity vehicle = MorphMountSync.resolveVehicleEntity(entity);

        if (vehicle != null)
        {
            mountIndex = RecorderMobCapture.resolveReplayIndexForEntity(vehicle.getId());
        }

        double ridingValue = mountIndex >= 0 ? 1D : 0D;

        /* Do not plant riding=0 on an empty track (user-cleared / never recorded). */
        if (!riderKeyframes.riding.isEmpty() || ridingValue != 0D)
        {
            riderKeyframes.riding.insertIfChanged(tick, ridingValue);
        }

        if (mountIndex >= 0 && replays != null && mountIndex < replays.size())
        {
            Replay mountReplay = replays.get(mountIndex);
            MountLink ridden = new MountLink(true, riderIndex);

            mountReplay.keyframes.ridden.insertIfChanged(tick, ridden);
        }
    }

    public void ensurePlayerVehicleCaptured(Recorder recorder)
    {
        this.capturePlayerVehicle(recorder);
    }

    public static int resolveReplayIndexForEntity(int entityId)
    {
        Films films = BBSModClient.getFilms();
        Recorder recorder = films.getRecorder();

        if (recorder != null)
        {
            int index = recorder.getMobCapture().getReplayIndexForEntity(entityId);

            if (index >= 0)
            {
                return index;
            }
        }

        return films.getEditorMobCapture().getReplayIndexForEntity(entityId);
    }

    /**
     * Server {@code MobEntity.convertTo} → hide the old autocaptured replay (no death
     * animation) and start capturing the successor entity.
     */
    public static void handleServerConversion(int oldEntityId, int newEntityId)
    {
        Films films = BBSModClient.getFilms();
        Recorder recorder = films.getRecorder();

        if (recorder != null && !recorder.hasNotStarted())
        {
            recorder.getMobCapture().onEntityConverted(recorder.film, recorder.getTick(), oldEntityId, newEntityId, recorder);
        }

        RecorderMobCapture editor = films.getEditorMobCapture();

        if (!editor.isEmpty() && editor.lastFilm != null)
        {
            editor.onEntityConverted(editor.lastFilm, editor.lastTick, oldEntityId, newEntityId, null);
        }
    }

    public boolean tryCapture(Recorder recorder, Entity target)
    {
        return this.tryCapture(recorder, target, "");
    }

    public boolean tryCapture(Recorder recorder, Entity target, String groupPath)
    {
        if (!this.canCaptureTarget(target))
        {
            return false;
        }

        if (this.capturedEntityIds.contains(target.getId()))
        {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null)
        {
            return false;
        }

        Form captured = this.captureForm(player, target);

        if (captured == null)
        {
            return false;
        }

        Form form = FormUtils.copy(captured);
        int tick = recorder.getTick();
        int[] replayIndex = new int[] {-1};

        BaseValue.edit(recorder.film.replays, (replays) ->
        {
            Replay replay = replays.addReplay();

            this.configureReplay(replay, target, form, this.vanillaPlaybackEntityIds.contains(target.getId()));

            if (groupPath != null && !groupPath.isEmpty())
            {
                replay.group.set(groupPath);
            }

            this.recordEntity(replay, target, tick);

            replayIndex[0] = replays.getList().indexOf(replay);
        });

        if (replayIndex[0] < 0)
        {
            return false;
        }

        return this.registerSession(recorder, target, replayIndex[0]);
    }

    private boolean canCaptureTarget(Entity target)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (target == null || target instanceof ActorEntity || target == mc.player)
        {
            return false;
        }

        return !(target instanceof PlayerEntity) || this.capturePlayers;
    }

    private Form captureForm(ClientPlayerEntity player, Entity target)
    {
        if (target instanceof PlayerEntity targetPlayer)
        {
            return PlayerCaptureForms.create(targetPlayer, this.playerModelForms);
        }

        return Morph.captureFormFromEntity(player, target);
    }

    private void configureReplay(Replay replay, Entity target, Form form, boolean vanillaPlayback)
    {
        replay.form.set(form);
        replay.label.set(this.getEntityLabel(target, form));

        if (target instanceof PlayerEntity player && this.playerNametags)
        {
            replay.nameTag.set(player.getGameProfile().name());
        }

        if (!(target instanceof PlayerEntity) || !this.playerModelForms)
        {
            this.applyVanillaMobPlayback(replay, vanillaPlayback);
        }
    }

    private Session createSession(Entity target, int replayIndex)
    {
        boolean player = target instanceof PlayerEntity;

        return new Session(
            target.getId(),
            replayIndex,
            target instanceof LivingEntity,
            target.getUuid(),
            player,
            player && this.playerNametags,
            player && this.playerModelForms
        );
    }

    public void bulkCapture(Film film, int tick, MobCaptureRecordingSetup setup, UIFilmPanel panel)
    {
        if (setup == null)
        {
            return;
        }

        this.applyRecordingSetup(setup);

        if (!setup.shouldCapture())
        {
            return;
        }

        Map<String, MobCaptureAreaScanner.TypeBucket> buckets = MobCaptureAreaScanner.scan(setup);
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null || buckets.isEmpty())
        {
            return;
        }

        BaseValue.edit(film.replays, (replays) ->
        {
            List<Replay> list = replays.getList();

            for (Map.Entry<String, MobCaptureAreaScanner.TypeBucket> entry : buckets.entrySet())
            {
                MobCaptureAreaScanner.TypeBucket bucket = entry.getValue();

                if (bucket.entities.isEmpty())
                {
                    continue;
                }

                boolean hasSelectedEntity = false;

                for (Entity entity : bucket.entities)
                {
                    if (setup.selectedEntityIds.contains(entity.getId()))
                    {
                        hasSelectedEntity = true;

                        break;
                    }
                }

                if (!hasSelectedEntity)
                {
                    continue;
                }

                Replay group = new Replay("replay");

                group.uuid.set(UUID.randomUUID().toString());
                group.isGroup.set(true);
                group.label.set(bucket.label);

                int insertAt = list.size();
                String groupPath = group.uuid.get();

                replays.add(insertAt, group);

                for (Entity entity : bucket.entities)
                {
                    if (!setup.selectedEntityIds.contains(entity.getId()))
                    {
                        continue;
                    }

                    if (this.capturedEntityIds.contains(entity.getId()) || !this.canCaptureTarget(entity))
                    {
                        continue;
                    }

                    Form captured = this.captureForm(player, entity);

                    if (captured == null)
                    {
                        continue;
                    }

                    Form form = FormUtils.copy(captured);
                    Replay replay = new Replay("replay");

                    this.configureReplay(replay, entity, form, setup.vanillaPlaybackEntityIds.contains(entity.getId()));
                    replay.group.set(groupPath);
                    this.recordEntity(replay, entity, tick);

                    replays.add(replay);

                    Session session = this.createSession(entity, list.indexOf(replay));

                    if (entity instanceof LivingEntity living)
                    {
                        session.tracksSnowTrail = this.isSnowGolem(form, living);
                        this.updateSessionState(session, living);
                        this.recordFireAndParticlesIfChanged(replay, session, living, tick);
                    }
                    else
                    {
                        this.updateSessionState(session, entity);
                    }

                    this.sessions.add(session);
                    this.capturedEntityIds.add(entity.getId());
                    this.entityReplayIndices.put(entity.getId(), session.replayIndex);
                }
            }

            replays.sync();
        });

        if (panel != null)
        {
            panel.replayEditor.replays.replays.buildVisualList();
            panel.replayEditor.updateChannelsList();
            panel.getController().refreshEntities();
        }
    }

    public void recordTickForFilm(Film film, int tick)
    {
        if (this.sessions.isEmpty() && this.pendingConversions.isEmpty() && this.pendingConversionScans.isEmpty())
        {
            return;
        }

        this.lastFilm = film;
        this.lastTick = tick;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;

        if (world == null)
        {
            return;
        }

        this.processPendingConversions(film, tick, world, null);

        Iterator<Session> iterator = this.sessions.iterator();

        while (iterator.hasNext())
        {
            Session session = iterator.next();

            if (session.replayIndex < 0 || session.replayIndex >= film.replays.getList().size())
            {
                iterator.remove();
                continue;
            }

            Replay replay = film.replays.getList().get(session.replayIndex);
            Entity entity = world.getEntityById(session.entityId);

            if (session.waitingForPlayerRespawn)
            {
                this.tryResumePlayerRespawn(film, replay, session, tick, world);

                continue;
            }

            if (session.recordingDeath)
            {
                this.advanceDeathRecording(replay, session, entity, tick);

                if (session.deathTickIndex >= DEATH_ANIMATION_TICKS)
                {
                    this.finishDeathRecording(null, replay, session, tick, iterator);
                }

                continue;
            }

            if (entity == null)
            {
                this.onCapturedEntityMissing(film, replay, session, tick, world, iterator, null);
                continue;
            }

            if (!session.livingEntity)
            {
                this.updateSessionState(session, entity);
                this.recordEntity(replay, entity, tick);
                this.syncMobFormNbt(replay, entity);
                continue;
            }

            if (entity instanceof LivingEntity living)
            {
                boolean dying = !living.isAlive() || living.deathTime > 0;

                if (!dying)
                {
                    this.updateSessionState(session, living);
                    this.recordEntity(replay, entity, tick);
                    this.recordFireAndParticlesIfChanged(replay, session, living, tick);

                    if (session.tracksSnowTrail)
                    {
                        RecorderWorldEffectCapture.captureSnowTrail(replay, session.snowTrailSnapshots, living, tick, world);
                    }
                }
                else
                {
                    if (!session.deathHandled)
                    {
                        this.captureDeathState(session, living);
                        this.handleDeathForFilm(film, replay, session, living, tick, world);
                        session.deathHandled = true;
                        session.recordingDeath = true;
                        session.deathTickIndex = living.deathTime > 0 ? living.deathTime : 1;
                    }

                    this.recordDeathEntity(replay, session, living, tick, Math.min(session.deathTickIndex, DEATH_ANIMATION_TICKS));

                    if (session.deathTickIndex >= DEATH_ANIMATION_TICKS)
                    {
                        this.finishDeathRecording(null, replay, session, tick, iterator);
                    }
                    else
                    {
                        session.recordingDeath = true;
                    }
                }
            }
            else if (session.deathHandled)
            {
                session.recordingDeath = true;
                session.deathTickIndex = 1;
                this.recordDeathEntity(replay, session, null, tick, 1);
            }
            else if (session.livingEntity)
            {
                this.onCapturedEntityMissing(film, replay, session, tick, world, iterator, null);
            }
        }
    }

    private boolean registerSession(Recorder recorder, Entity target, int replayIndex)
    {
        Session session = this.createSession(target, replayIndex);
        Form form = recorder.film.replays.getList().get(replayIndex).form.get();

        if (target instanceof LivingEntity living)
        {
            session.tracksSnowTrail = this.isSnowGolem(form, living);
            this.updateSessionState(session, living);
            this.recordFireAndParticlesIfChanged(recorder.film.replays.getList().get(replayIndex), session, living, recorder.getTick());
        }
        else
        {
            this.updateSessionState(session, target);
        }

        this.sessions.add(session);
        this.capturedEntityIds.add(target.getId());
        this.entityReplayIndices.put(target.getId(), replayIndex);
        this.refreshFilmUi(recorder);

        return true;
    }

    private void handleDeathForFilm(Film film, Replay replay, Session session, LivingEntity living, int tick, ClientWorld world)
    {
        this.applyDeathEffectKeyframes(replay, session, tick);

        BaseValue.edit(replay.actions, (actions) ->
        {
            if (!this.captureNearbyDrops(replay, tick, session.deathX, session.deathY, session.deathZ, world))
            {
                this.captureEquipmentDrops(replay, living, tick, session.deathX, session.deathY, session.deathZ);
            }
        });
    }

    private void capturePlayerVehicle(Recorder recorder)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null)
        {
            return;
        }

        Entity vehicle = player.getVehicle();

        if (vehicle != null)
        {
            this.tryCapture(recorder, vehicle);
        }
    }

    public void recordTick(Recorder recorder)
    {
        this.capturePlayerVehicle(recorder);

        if (this.sessions.isEmpty() && this.pendingConversions.isEmpty() && this.pendingConversionScans.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;

        if (world == null)
        {
            return;
        }

        int tick = recorder.getTick();
        Film film = recorder.film;

        this.lastFilm = film;
        this.lastTick = tick;
        this.processPendingConversions(film, tick, world, recorder);

        Iterator<Session> iterator = this.sessions.iterator();

        while (iterator.hasNext())
        {
            Session session = iterator.next();

            if (session.replayIndex < 0 || session.replayIndex >= film.replays.getList().size())
            {
                iterator.remove();
                continue;
            }

            Replay replay = film.replays.getList().get(session.replayIndex);
            Entity entity = world.getEntityById(session.entityId);

            if (session.waitingForPlayerRespawn)
            {
                this.tryResumePlayerRespawn(film, replay, session, tick, world);

                continue;
            }

            if (session.recordingDeath)
            {
                this.advanceDeathRecording(replay, session, entity, tick);

                if (session.deathTickIndex >= DEATH_ANIMATION_TICKS)
                {
                    this.finishDeathRecording(recorder, replay, session, tick, iterator);
                }

                continue;
            }

            if (entity == null)
            {
                this.onCapturedEntityMissing(film, replay, session, tick, world, iterator, recorder);
                continue;
            }

            if (!session.livingEntity)
            {
                this.updateSessionState(session, entity);
                this.recordEntity(replay, entity, tick);
                this.syncMobFormNbt(replay, entity);
                continue;
            }

            if (entity instanceof LivingEntity living)
            {
                boolean dying = !living.isAlive() || living.deathTime > 0;

                if (!dying)
                {
                    this.updateSessionState(session, living);
                    this.recordEntity(replay, entity, tick);
                    this.recordFireAndParticlesIfChanged(replay, session, living, tick);

                    if (session.tracksSnowTrail)
                    {
                        RecorderWorldEffectCapture.captureSnowTrail(replay, session.snowTrailSnapshots, living, tick, world);
                    }
                }
                else
                {
                    if (!session.deathHandled)
                    {
                        this.captureDeathState(session, living);
                        this.handleDeath(recorder, replay, session, living, tick, world);
                        session.deathHandled = true;
                        session.recordingDeath = true;
                        session.deathTickIndex = living.deathTime > 0 ? living.deathTime : 1;
                    }

                    this.recordDeathEntity(replay, session, living, tick, Math.min(session.deathTickIndex, DEATH_ANIMATION_TICKS));

                    if (session.deathTickIndex >= DEATH_ANIMATION_TICKS)
                    {
                        this.finishDeathRecording(recorder, replay, session, tick, iterator);
                    }
                    else
                    {
                        session.recordingDeath = true;
                    }
                }
            }
            else if (session.deathHandled)
            {
                session.recordingDeath = true;
                session.deathTickIndex = 1;
                this.recordDeathEntity(replay, session, null, tick, 1);
            }
            else if (session.livingEntity)
            {
                this.onCapturedEntityMissing(film, replay, session, tick, world, iterator, recorder);
            }
        }
    }

    public void simplify(Film film)
    {
        this.finishOpenDeathSessions(film);

        for (Session session : this.sessions)
        {
            if (session.replayIndex >= 0 && session.replayIndex < film.replays.getList().size())
            {
                Replay replay = film.replays.getList().get(session.replayIndex);

                for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
                {
                    channel.simplify();
                }

                BaseValue poseValue = replay.properties.get("pose");

                if (poseValue instanceof KeyframeChannel<?> poseChannel)
                {
                    poseChannel.simplify();
                }
            }
        }
    }

    /**
     * If recording stops mid-death, still hide the corpse and spawn death particles.
     */
    private void finishOpenDeathSessions(Film film)
    {
        int tick = -1;
        Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder != null)
        {
            tick = recorder.getTick();
        }

        for (Session session : this.sessions)
        {
            if ((!session.recordingDeath && !session.deathHandled)
                || session.replayIndex < 0
                || session.replayIndex >= film.replays.getList().size())
            {
                continue;
            }

            Replay replay = film.replays.getList().get(session.replayIndex);
            int disappearTick = tick >= 0 ? tick : session.deathTickIndex;

            if (disappearTick < 0)
            {
                disappearTick = 0;
            }

            while (session.deathTickIndex < DEATH_ANIMATION_TICKS)
            {
                session.deathTickIndex += 1;
                disappearTick += 1;
                this.recordDeathEntity(replay, session, null, disappearTick, Math.min(session.deathTickIndex, DEATH_ANIMATION_TICKS));
            }

            this.applyDeathVisibilityKeyframes(replay, disappearTick);

            if (!session.playerEntity)
            {
                this.addMobDeathClip(replay, disappearTick);
            }
        }
    }

    private void finishDeathRecording(Recorder recorder, Replay replay, Session session, int disappearTick, Iterator<Session> iterator)
    {
        this.applyDeathVisibilityKeyframes(replay, disappearTick);

        if (session.playerEntity)
        {
            session.recordingDeath = false;
            session.waitingForPlayerRespawn = true;
            this.forgetEntity(session.entityId);
            this.refreshFilmUi(recorder);

            return;
        }

        this.addMobDeathClip(replay, disappearTick);
        iterator.remove();
        this.refreshFilmUi(recorder);
    }

    private boolean tryResumePlayerRespawn(Film film, Replay replay, Session session, int tick, ClientWorld world)
    {
        Entity entity = this.findPlayerByUuid(world, session.entityUuid);

        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.deathTime > 0)
        {
            return false;
        }

        session.entityId = entity.getId();
        session.waitingForPlayerRespawn = false;
        session.recordingDeath = false;
        session.deathHandled = false;
        session.deathTickIndex = 0;
        this.capturedEntityIds.add(entity.getId());
        this.entityReplayIndices.put(entity.getId(), session.replayIndex);
        this.applyAppearVisibilityKeyframes(replay, tick);
        this.updateSessionState(session, living);
        this.recordEntity(replay, entity, tick);
        this.recordFireAndParticlesIfChanged(replay, session, living, tick);
        this.refreshFilmUi(film);

        return true;
    }

    private Entity findPlayerByUuid(ClientWorld world, UUID uuid)
    {
        if (world == null || uuid == null)
        {
            return null;
        }

        for (PlayerEntity player : world.getPlayers())
        {
            if (uuid.equals(player.getUuid()))
            {
                return player;
            }
        }

        return null;
    }

    private void addMobDeathClip(Replay replay, int tick)
    {
        if (tick < 0)
        {
            return;
        }

        BaseValue.edit(replay.actions, (actions) ->
        {
            MobDeathActionClip deathClip = new MobDeathActionClip();

            deathClip.tick.set(tick);
            deathClip.duration.set(1);
            actions.addClip(deathClip);
        });
    }

    private void applyDeathVisibilityKeyframes(Replay replay, int disappearTick)
    {
        Form form = replay.form.get();

        if (form == null || disappearTick < 0)
        {
            return;
        }

        int visibleTick = disappearTick - 1;

        BaseValue.edit(replay.properties, (properties) ->
        {
            if (visibleTick >= 0)
            {
                properties.insertVisibleRenderEnabled(form, visibleTick, true);
            }

            properties.insertVisibleRenderEnabled(form, disappearTick, false);
        });
    }

    /**
     * Conversion successors must stay invisible until the tick they appear, otherwise
     * the first recorded pose holds and the new MobForm shows from film start.
     */
    private void applyAppearVisibilityKeyframes(Replay replay, int appearTick)
    {
        Form form = replay.form.get();

        if (form == null || appearTick < 0)
        {
            return;
        }

        BaseValue.edit(replay.properties, (properties) ->
        {
            if (appearTick > 0)
            {
                properties.insertVisibleRenderEnabled(form, 0, false);
            }

            properties.insertVisibleRenderEnabled(form, appearTick, true);
        });
    }

    private void applyDeathEffectKeyframes(Replay replay, Session session, int deathTick)
    {
        if (deathTick < 0)
        {
            return;
        }

        replay.keyframes.fire.insert(deathTick, 0D);
        replay.keyframes.particles.insert(deathTick, 0D);
        session.lastFire = Boolean.FALSE;
        session.lastParticles = Boolean.FALSE;
    }

    private void recordFireAndParticlesIfChanged(Replay replay, Session session, LivingEntity living, int tick)
    {
        boolean fire = living.getFireTicks() > 0;
        boolean particles = living.isAlive();

        if (session.lastFire == null || session.lastFire.booleanValue() != fire)
        {
            replay.keyframes.fire.insertIfChanged(tick, fire ? 1D : 0D);
            session.lastFire = fire;
        }

        if (session.lastParticles == null || session.lastParticles.booleanValue() != particles)
        {
            replay.keyframes.particles.insertIfChanged(tick, particles ? 1D : 0D);
            session.lastParticles = particles;
        }
    }

    private boolean isSnowGolem(Form form, LivingEntity living)
    {
        if (form instanceof MobForm mobForm && mobForm.mobID.get().equals("minecraft:snow_golem"))
        {
            return true;
        }

        return living.getType() == EntityType.SNOW_GOLEM;
    }

    private static final List<String> MOB_NBT_STRIP_KEYS = Arrays.asList(
        "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround",
        "Invulnerable", "PortalCooldown", "UUID",
        "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount",
        "FallFlying", "Brain", "Attributes", "ActiveEffects", "Passengers",
        "SleepingX", "SleepingY", "SleepingZ"
    );

    private void recordEntity(Replay replay, Entity entity, int tick)
    {
        MCEntity wrapper = new MCEntity(entity);

        wrapper.update();
        replay.keyframes.record(tick, wrapper, null);
        this.syncMobFormNbt(replay, entity);

        Form form = replay.form.get();

        if (MobCemPoseCapture.isActive(replay))
        {
            MobCemPoseCapture.recordPoseKeyframe(replay, form, wrapper, tick, 0F);
        }

        if (form instanceof MobForm mobForm)
        {
            MobCemItemCapture.recordItemStats(replay, mobForm, wrapper, tick, 0F);
        }
    }

    private void syncMobFormNbt(Replay replay, Entity entity)
    {
        Form form = replay.form.get();

        if (!(form instanceof MobForm mobForm))
        {
            return;
        }

        NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY, entity.getEntityWorld().getRegistryManager());
        entity.writeData(view);
        NbtCompound compound = view.getNbt();

        for (String key : MOB_NBT_STRIP_KEYS)
        {
            compound.remove(key);
        }

        String nbt = compound.toString();

        /* Skip writes when NBT is unchanged so recording does not spam form updates. */
        if (nbt.equals(mobForm.mobNBT.get()))
        {
            return;
        }

        mobForm.mobNBT.set(nbt);
    }

    /**
     * While the corpse still exists, keep sampling live pose (knockback hop, etc.).
     * After despawn, hold the last sampled pose and only advance {@code death_time}.
     */
    private void advanceDeathRecording(Replay replay, Session session, Entity entity, int tick)
    {
        if (entity instanceof LivingEntity living && living.deathTime > 0)
        {
            session.deathTickIndex = living.deathTime;
        }
        else
        {
            session.deathTickIndex += 1;
        }

        this.recordDeathEntity(replay, session, entity, tick, Math.min(session.deathTickIndex, DEATH_ANIMATION_TICKS));
    }

    private void recordDeathEntity(Replay replay, Session session, Entity entity, int tick, int deathTime)
    {
        if (entity != null)
        {
            this.updateSessionState(session, entity);
            session.deathX = session.lastX;
            session.deathY = session.lastY;
            session.deathZ = session.lastZ;
            session.deathYaw = session.lastYaw;
            session.deathPitch = session.lastPitch;
            session.deathHeadYaw = session.lastHeadYaw;
            session.deathBodyYaw = session.lastBodyYaw;

            MCEntity wrapper = new MCEntity(entity);

            wrapper.update();
            /* Keep ambient morph particles off while dying; MobDeathActionClip
             * spawns the single vanilla-style poof burst at despawn. */
            wrapper.setParticlesEnabled(false);
            replay.keyframes.record(tick, wrapper, null);
            replay.keyframes.deathTime.insertIfChanged(tick, (double) deathTime);
            /* Keep damage flash on for the whole death tip — live hurtTime decays early,
             * and playback must stay driven only by the damage track. */
            replay.keyframes.damage.insertIfChanged(tick, 10D);
            replay.keyframes.particles.insertIfChanged(tick, 0D);

            return;
        }

        StubEntity wrapper = new StubEntity(MinecraftClient.getInstance().world);

        wrapper.setPosition(session.deathX, session.deathY, session.deathZ);
        wrapper.setPrevX(session.deathX);
        wrapper.setPrevY(session.deathY);
        wrapper.setPrevZ(session.deathZ);
        wrapper.setYaw(session.deathYaw);
        wrapper.setPitch(session.deathPitch);
        wrapper.setHeadYaw(session.deathHeadYaw);
        wrapper.setBodyYaw(session.deathBodyYaw);
        wrapper.setPrevYaw(session.deathYaw);
        wrapper.setPrevPitch(session.deathPitch);
        wrapper.setPrevHeadYaw(session.deathHeadYaw);
        wrapper.setPrevBodyYaw(session.deathBodyYaw);
        wrapper.setDeathTime(deathTime);
        wrapper.setHurtTimer(10);
        wrapper.setSneaking(false);
        wrapper.setSprinting(false);
        wrapper.setOnGround(true);
        wrapper.setVelocity(0F, 0F, 0F);
        wrapper.setParticlesEnabled(false);

        replay.keyframes.record(tick, wrapper, null);
        replay.keyframes.damage.insertIfChanged(tick, 10D);
        replay.keyframes.particles.insertIfChanged(tick, 0D);
    }

    private void updateSessionState(Session session, Entity entity)
    {
        session.lastX = entity.getX();
        session.lastY = entity.getY();
        session.lastZ = entity.getZ();
        session.lastYaw = entity.getYaw();
        session.lastPitch = entity.getPitch();

        if (entity instanceof LivingEntity living)
        {
            session.lastHeadYaw = living.getHeadYaw();
            session.lastBodyYaw = living.bodyYaw;
        }
        else
        {
            session.lastHeadYaw = entity.getYaw();
            session.lastBodyYaw = entity.getYaw();
        }
    }

    private void captureDeathState(Session session, LivingEntity living)
    {
        if (living.isAlive() || living.deathTime <= 1)
        {
            session.deathX = living.getX();
            session.deathY = living.getY();
            session.deathZ = living.getZ();
            session.deathYaw = living.getYaw();
            session.deathPitch = living.getPitch();
            session.deathHeadYaw = living.getHeadYaw();
            session.deathBodyYaw = living.bodyYaw;
        }
        else
        {
            session.deathX = session.lastX;
            session.deathY = session.lastY;
            session.deathZ = session.lastZ;
            session.deathYaw = session.lastYaw;
            session.deathPitch = session.lastPitch;
            session.deathHeadYaw = session.lastHeadYaw;
            session.deathBodyYaw = session.lastBodyYaw;
        }
    }

    private void handleDeath(Recorder recorder, Replay replay, Session session, LivingEntity living, int tick, ClientWorld world)
    {
        this.applyDeathEffectKeyframes(replay, session, tick);

        BaseValue.edit(replay.actions, (actions) ->
        {
            if (!this.captureNearbyDrops(replay, tick, session.deathX, session.deathY, session.deathZ, world))
            {
                this.captureEquipmentDrops(replay, living, tick, session.deathX, session.deathY, session.deathZ);
            }
        });

        this.refreshFilmUi(recorder);
    }

    private boolean captureNearbyDrops(Replay replay, int tick, double x, double y, double z, ClientWorld world)
    {
        Box box = new Box(
            x - DROP_SCAN_RADIUS, y - DROP_SCAN_RADIUS, z - DROP_SCAN_RADIUS,
            x + DROP_SCAN_RADIUS, y + DROP_SCAN_RADIUS, z + DROP_SCAN_RADIUS
        );
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box, (item) -> item.age <= 2);
        boolean found = false;

        for (ItemEntity item : items)
        {
            if (item.getStack().isEmpty())
            {
                continue;
            }

            this.addItemDropClip(replay, tick, item.getEntityPos(), item.getVelocity(), item.getStack());
            found = true;
        }

        return found;
    }

    private void captureEquipmentDrops(Replay replay, LivingEntity living, int tick, double x, double y, double z)
    {
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            ItemStack stack = living.getEquippedStack(slot);

            if (stack.isEmpty())
            {
                continue;
            }

            Vec3d velocity = new Vec3d(
                (living.getRandom().nextDouble() - 0.5D) * 0.2D,
                living.getRandom().nextDouble() * 0.2D + 0.1D,
                (living.getRandom().nextDouble() - 0.5D) * 0.2D
            );

            this.addItemDropClip(replay, tick, new Vec3d(x, y + 0.5D, z), velocity, stack);
        }
    }

    private void addItemDropClip(Replay replay, int tick, Vec3d pos, Vec3d velocity, ItemStack stack)
    {
        ItemDropActionClip clip = new ItemDropActionClip();

        clip.tick.set(tick);
        clip.duration.set(1);
        clip.posX.set(pos.x);
        clip.posY.set(pos.y);
        clip.posZ.set(pos.z);
        clip.velocityX.set((float) velocity.x);
        clip.velocityY.set((float) velocity.y);
        clip.velocityZ.set((float) velocity.z);
        clip.itemStack.set(stack.copy());
        replay.actions.addClip(clip);
    }

    private String getEntityLabel(Entity entity, Form form)
    {
        if (form instanceof MobForm mobForm && !mobForm.mobID.get().isEmpty())
        {
            String id = mobForm.mobID.get();
            int colon = id.indexOf(':');

            if (colon >= 0 && colon < id.length() - 1)
            {
                return id.substring(colon + 1);
            }

            return id;
        }

        return entity.getName().getString();
    }

    private void onCapturedEntityMissing(Film film, Replay replay, Session session, int tick, ClientWorld world, Iterator<Session> iterator, Recorder recorder)
    {
        if (!session.livingEntity)
        {
            this.applyDeathVisibilityKeyframes(replay, tick);
            this.forgetEntity(session.entityId);
            iterator.remove();
            return;
        }

        if (session.deathHandled)
        {
            session.recordingDeath = true;
            session.deathTickIndex = 1;
            this.recordDeathEntity(replay, session, null, tick, 1);
            return;
        }

        if (session.playerEntity)
        {
            this.applyDeathVisibilityKeyframes(replay, tick);
            this.forgetEntity(session.entityId);
            session.waitingForPlayerRespawn = true;
            return;
        }

        /* Conversion / silent despawn: hide without death roll or MobDeath particles. */
        this.finishSilentRemoval(film, replay, session, tick, world, iterator, recorder);
    }

    private void finishSilentRemoval(Film film, Replay replay, Session session, int tick, ClientWorld world, Iterator<Session> iterator, Recorder recorder)
    {
        this.applyDeathVisibilityKeyframes(replay, tick);

        String groupPath = replay.group.get();
        boolean vanilla = replay.vanillaMobPlayback.get();
        int oldEntityId = session.entityId;

        this.recentSilentRemovals.put(oldEntityId, new PendingConversion(groupPath, vanilla));
        this.forgetEntity(oldEntityId);
        iterator.remove();

        Entity successor = this.findConversionSuccessor(world, session);

        if (successor != null)
        {
            this.recentSilentRemovals.remove(oldEntityId);
            this.captureSuccessor(film, tick, successor, groupPath, vanilla, recorder);
            return;
        }

        this.pendingConversionScans.add(new PendingConversionScan(session.lastX, session.lastY, session.lastZ, groupPath, vanilla));
    }

    private void onEntityConverted(Film film, int tick, int oldEntityId, int newEntityId, Recorder recorder)
    {
        if (film == null || newEntityId < 0)
        {
            return;
        }

        String groupPath = "";
        boolean vanilla = false;
        boolean known = false;
        Session session = this.findSession(oldEntityId);

        if (session != null && session.replayIndex >= 0 && session.replayIndex < film.replays.getList().size())
        {
            Replay oldReplay = film.replays.getList().get(session.replayIndex);

            groupPath = oldReplay.group.get();
            vanilla = oldReplay.vanillaMobPlayback.get();
            known = true;

            if (!session.deathHandled && !session.recordingDeath)
            {
                this.applyDeathVisibilityKeyframes(oldReplay, tick);
            }

            this.sessions.remove(session);
            this.forgetEntity(oldEntityId);
            this.recentSilentRemovals.remove(oldEntityId);
        }
        else
        {
            PendingConversion recent = this.recentSilentRemovals.remove(oldEntityId);

            if (recent != null)
            {
                groupPath = recent.groupPath;
                vanilla = recent.vanillaPlayback;
                known = true;
            }
        }

        if (!known)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        Entity successor = world == null ? null : world.getEntityById(newEntityId);

        if (successor != null)
        {
            this.captureSuccessor(film, tick, successor, groupPath, vanilla, recorder);
            return;
        }

        this.pendingConversions.put(newEntityId, new PendingConversion(groupPath, vanilla));
    }

    private Session findSession(int entityId)
    {
        for (Session session : this.sessions)
        {
            if (session.entityId == entityId)
            {
                return session;
            }
        }

        return null;
    }

    private void forgetEntity(int entityId)
    {
        this.capturedEntityIds.remove(entityId);
        this.entityReplayIndices.remove(entityId);
    }

    private Entity findConversionSuccessor(ClientWorld world, Session session)
    {
        Box box = new Box(
            session.lastX - CONVERSION_SCAN_RADIUS,
            session.lastY - CONVERSION_SCAN_RADIUS,
            session.lastZ - CONVERSION_SCAN_RADIUS,
            session.lastX + CONVERSION_SCAN_RADIUS,
            session.lastY + CONVERSION_SCAN_RADIUS + 1D,
            session.lastZ + CONVERSION_SCAN_RADIUS
        );
        Entity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : world.getOtherEntities(null, box, this::canCaptureConversionSuccessor))
        {
            if (entity.age > CONVERSION_SUCCESSOR_MAX_AGE)
            {
                continue;
            }

            double dist = entity.squaredDistanceTo(session.lastX, session.lastY, session.lastZ);

            if (dist < bestDist)
            {
                bestDist = dist;
                best = entity;
            }
        }

        return best;
    }

    private boolean canCaptureConversionSuccessor(Entity entity)
    {
        if (entity == null || entity instanceof PlayerEntity || entity instanceof ActorEntity)
        {
            return false;
        }

        if (!(entity instanceof LivingEntity))
        {
            return false;
        }

        return !this.capturedEntityIds.contains(entity.getId());
    }

    private void processPendingConversions(Film film, int tick, ClientWorld world, Recorder recorder)
    {
        if (!this.pendingConversions.isEmpty())
        {
            Iterator<Map.Entry<Integer, PendingConversion>> iterator = this.pendingConversions.entrySet().iterator();

            while (iterator.hasNext())
            {
                Map.Entry<Integer, PendingConversion> entry = iterator.next();
                PendingConversion pending = entry.getValue();
                Entity successor = world.getEntityById(entry.getKey());

                if (successor != null)
                {
                    this.captureSuccessor(film, tick, successor, pending.groupPath, pending.vanillaPlayback, recorder);
                    iterator.remove();
                    continue;
                }

                pending.ticksRemaining -= 1;

                if (pending.ticksRemaining <= 0)
                {
                    iterator.remove();
                }
            }
        }

        if (!this.pendingConversionScans.isEmpty())
        {
            Iterator<PendingConversionScan> scanIterator = this.pendingConversionScans.iterator();

            while (scanIterator.hasNext())
            {
                PendingConversionScan scan = scanIterator.next();
                Session probe = new Session(-1, -1, true);

                probe.lastX = scan.x;
                probe.lastY = scan.y;
                probe.lastZ = scan.z;

                Entity successor = this.findConversionSuccessor(world, probe);

                if (successor != null)
                {
                    this.captureSuccessor(film, tick, successor, scan.groupPath, scan.vanillaPlayback, recorder);
                    scanIterator.remove();
                    continue;
                }

                scan.ticksRemaining -= 1;

                if (scan.ticksRemaining <= 0)
                {
                    scanIterator.remove();
                }
            }
        }

        if (!this.recentSilentRemovals.isEmpty())
        {
            Iterator<Map.Entry<Integer, PendingConversion>> recentIterator = this.recentSilentRemovals.entrySet().iterator();

            while (recentIterator.hasNext())
            {
                PendingConversion pending = recentIterator.next().getValue();

                pending.ticksRemaining -= 1;

                if (pending.ticksRemaining <= 0)
                {
                    recentIterator.remove();
                }
            }
        }
    }

    private void captureSuccessor(Film film, int tick, Entity successor, String groupPath, boolean vanilla, Recorder recorder)
    {
        if (successor == null || this.capturedEntityIds.contains(successor.getId()))
        {
            return;
        }

        if (vanilla)
        {
            this.vanillaPlaybackEntityIds.add(successor.getId());
        }

        boolean captured;

        if (recorder != null)
        {
            captured = this.tryCapture(recorder, successor, groupPath);
        }
        else
        {
            captured = this.tryCaptureOnFilm(film, tick, successor, groupPath);
        }

        if (!captured)
        {
            return;
        }

        int replayIndex = this.getReplayIndexForEntity(successor.getId());

        if (replayIndex < 0 || replayIndex >= film.replays.getList().size())
        {
            return;
        }

        this.applyAppearVisibilityKeyframes(film.replays.getList().get(replayIndex), tick);
    }

    private boolean tryCaptureOnFilm(Film film, int tick, Entity target, String groupPath)
    {
        if (!this.canCaptureTarget(target))
        {
            return false;
        }

        if (this.capturedEntityIds.contains(target.getId()))
        {
            return false;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null || film == null)
        {
            return false;
        }

        Form captured = this.captureForm(player, target);

        if (captured == null)
        {
            return false;
        }

        Form form = FormUtils.copy(captured);
        int[] replayIndex = new int[] {-1};

        BaseValue.edit(film.replays, (replays) ->
        {
            Replay replay = replays.addReplay();

            this.configureReplay(replay, target, form, this.vanillaPlaybackEntityIds.contains(target.getId()));

            if (groupPath != null && !groupPath.isEmpty())
            {
                replay.group.set(groupPath);
            }

            this.recordEntity(replay, target, tick);
            replayIndex[0] = replays.getList().indexOf(replay);
        });

        if (replayIndex[0] < 0)
        {
            return false;
        }

        Session session = this.createSession(target, replayIndex[0]);

        if (target instanceof LivingEntity living)
        {
            session.tracksSnowTrail = this.isSnowGolem(form, living);
            this.updateSessionState(session, living);
            this.recordFireAndParticlesIfChanged(film.replays.getList().get(replayIndex[0]), session, living, tick);
        }
        else
        {
            this.updateSessionState(session, target);
        }

        this.sessions.add(session);
        this.capturedEntityIds.add(target.getId());
        this.entityReplayIndices.put(target.getId(), replayIndex[0]);
        this.refreshFilmUi(film);

        return true;
    }

    private void refreshFilmUi(Film film)
    {
        MinecraftClient.getInstance().execute(() ->
        {
            UIDashboard dashboard = BBSModClient.getDashboard();

            if (dashboard == null)
            {
                return;
            }

            UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

            if (panel == null || panel.getData() != film || !(UIScreen.getCurrentMenu() instanceof UIDashboard))
            {
                return;
            }

            panel.replayEditor.replays.replays.buildVisualList();
            panel.replayEditor.updateChannelsList();
            panel.getController().refreshEntities();
        });
    }

    private void refreshFilmUi(Recorder recorder)
    {
        if (recorder == null)
        {
            return;
        }

        this.refreshFilmUi(recorder.film);
    }
}
