package mchorse.bbs_mod.film;

import mchorse.bbs_mod.film.replays.MountLink;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.List;

import io.netty.util.collection.IntObjectMap;

/**
 * Links rider and mount replay entities so mob morph renderers can call
 * {@link Entity#startRiding(Entity, boolean)} and trigger ridden animations on modded mobs.
 */
public final class MorphMountSync
{
    private MorphMountSync()
    {}

    public static void assignMountTargets(IntObjectMap<IEntity> entities, List<Replay> replays, int ticks)
    {
        for (IEntity entity : entities.values())
        {
            if (entity != null)
            {
                entity.setMountTarget(null);
                entity.setRiderTarget(null);
                entity.setSitting(false);
            }
        }

        for (int i = 0; i < replays.size(); i++)
        {
            IEntity rider = entities.get(i);
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (rider == null || replay == null)
            {
                continue;
            }

            int replayTick = replay.getTick(ticks);
            MountLink riding = ReplayKeyframes.resolveRiding(replay, replays, i, replayTick);

            if (!riding.active)
            {
                continue;
            }

            if (riding.replay < 0)
            {
                rider.setSitting(true);

                continue;
            }

            IEntity vehicle = entities.get(riding.replay);

            if (vehicle != null)
            {
                rider.setMountTarget(vehicle);
                vehicle.setRiderTarget(rider);
            }
        }

        for (int i = 0; i < replays.size(); i++)
        {
            IEntity mount = entities.get(i);
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (mount == null || replay == null)
            {
                continue;
            }

            int replayTick = replay.getTick(ticks);
            MountLink ridden = replay.keyframes.getRiddenAt(replayTick);

            if (!ridden.active || ridden.replay < 0)
            {
                continue;
            }

            IEntity rider = entities.get(ridden.replay);

            if (rider == null)
            {
                continue;
            }

            Replay riderReplay = CollectionUtils.getSafe(replays, ridden.replay);

            if (riderReplay == null)
            {
                continue;
            }

            int riderTick = riderReplay.getTick(ticks);
            MountLink riderRiding = ReplayKeyframes.resolveRiding(riderReplay, replays, ridden.replay, riderTick);

            /* Ridden on the mount is ignored when the rider's Sit toggle is off */
            if (!riderRiding.active)
            {
                continue;
            }

            if (riderRiding.replay >= 0 && riderRiding.replay != i)
            {
                continue;
            }

            if (rider.getMountTarget() == null)
            {
                rider.setMountTarget(mount);
            }

            mount.setRiderTarget(rider);
        }

        for (IEntity entity : entities.values())
        {
            if (entity == null)
            {
                continue;
            }

            Form form = entity.getForm();

            if (form instanceof MobForm mobForm && mobForm.getRenderer() instanceof MobFormRenderer renderer)
            {
                renderer.ensureRenderEntity();
            }
        }
    }

    /**
     * Anchor-like follow: rider world position tracks the mount replay plus the recorded offset.
     */
    public static void syncMountedState(IntObjectMap<IEntity> entities, List<Replay> replays, int ticks)
    {
        for (int i = 0; i < replays.size(); i++)
        {
            IEntity rider = entities.get(i);
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (rider == null || replay == null)
            {
                continue;
            }

            IEntity vehicle = rider.getMountTarget();

            if (vehicle == null)
            {
                continue;
            }

            int replayTick = replay.getTick(ticks);
            MountLink riding = ReplayKeyframes.resolveRiding(replay, replays, i, replayTick);

            if (!riding.active || riding.replay < 0)
            {
                continue;
            }

            Replay vehicleReplay = CollectionUtils.getSafe(replays, riding.replay);

            if (vehicleReplay == null)
            {
                continue;
            }

            Entity mountRender = MorphMountSync.getRenderedEntity(vehicle);
            Entity riderRender = MorphMountSync.getRenderedEntity(rider);

            if (mountRender != null && riderRender != null)
            {
                if (riderRender.getVehicle() != mountRender)
                {
                    riderRender.stopRiding();
                    riderRender.startRiding(mountRender);
                }
            }

            MorphMountSync.syncMountedPosition(replay.keyframes, vehicleReplay.keyframes, replayTick, rider, vehicle);
            MorphMountSync.syncMountedRotation(replay.keyframes, replayTick, rider, vehicle);

            rider.setVelocity(0F, 0F, 0F);
            rider.setSprinting(false);
            MorphMountSync.zeroLimbAnimator(rider);
        }
    }

    private static void syncMountedPosition(ReplayKeyframes riderKeyframes, ReplayKeyframes vehicleKeyframes, int replayTick, IEntity rider, IEntity vehicle)
    {
        float anchorTick = MorphMountSync.resolveRidingAnchorTick(riderKeyframes.riding, replayTick);

        if (anchorTick < 0F)
        {
            return;
        }

        double worldOffsetX = riderKeyframes.x.interpolate(anchorTick) - vehicleKeyframes.x.interpolate(anchorTick);
        double worldOffsetY = riderKeyframes.y.interpolate(anchorTick) - vehicleKeyframes.y.interpolate(anchorTick);
        double worldOffsetZ = riderKeyframes.z.interpolate(anchorTick) - vehicleKeyframes.z.interpolate(anchorTick);

        float anchorBodyYaw = vehicleKeyframes.bodyYaw.interpolate(anchorTick).floatValue();
        double[] local = MorphMountSync.worldOffsetToLocal(worldOffsetX, worldOffsetZ, anchorBodyYaw);
        double[] currentOffset = MorphMountSync.localOffsetToWorld(local[0], local[1], vehicle.getBodyYaw());
        double[] prevOffset = MorphMountSync.localOffsetToWorld(local[0], local[1], vehicle.getPrevBodyYaw());

        rider.setPosition(vehicle.getX() + currentOffset[0], vehicle.getY() + worldOffsetY, vehicle.getZ() + currentOffset[1]);
        rider.setPrevX(vehicle.getPrevX() + prevOffset[0]);
        rider.setPrevY(vehicle.getPrevY() + worldOffsetY);
        rider.setPrevZ(vehicle.getPrevZ() + prevOffset[1]);
    }

    /**
     * Rider body yaw follows the mount; rider head yaw and pitch stay on recorded keyframes.
     * Mount head yaw locks to body yaw; mount pitch follows the rider.
     */
    private static void syncMountedRotation(ReplayKeyframes riderKeyframes, int replayTick, IEntity rider, IEntity vehicle)
    {
        float riderHeadYaw = riderKeyframes.headYaw.interpolate(replayTick).floatValue();
        float riderPrevHeadYaw = riderKeyframes.headYaw.interpolate(replayTick - 1).floatValue();
        float riderPitch = riderKeyframes.pitch.interpolate(replayTick).floatValue();
        float riderPrevPitch = riderKeyframes.pitch.interpolate(replayTick - 1).floatValue();

        float bodyYaw = vehicle.getBodyYaw();
        float prevBodyYaw = vehicle.getPrevBodyYaw();

        rider.setBodyYaw(bodyYaw);
        rider.setPrevBodyYaw(prevBodyYaw);
        rider.setYaw(bodyYaw);
        rider.setPrevYaw(prevBodyYaw);
        rider.setHeadYaw(riderHeadYaw);
        rider.setPrevHeadYaw(riderPrevHeadYaw);
        rider.setPitch(riderPitch);
        rider.setPrevPitch(riderPrevPitch);

        vehicle.setHeadYaw(bodyYaw);
        vehicle.setPrevHeadYaw(prevBodyYaw);
        vehicle.setPitch(riderPitch);
        vehicle.setPrevPitch(riderPrevPitch);
    }

    /**
     * Anchor offsets at the latest active riding keyframe at or before the current tick.
     * Using segment start would shift the anchor on every riding keyframe boundary.
     */
    private static float resolveRidingAnchorTick(KeyframeChannel<Double> channel, int replayTick)
    {
        float anchorTick = -1F;

        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            if (keyframe.getTick() > replayTick)
            {
                break;
            }

            if (keyframe.getValue() != 0D)
            {
                anchorTick = keyframe.getTick();
            }
        }

        return anchorTick;
    }

    /**
     * Entities face (-sin(yaw), 0, cos(yaw)); convert a recorded world offset into mount-local
     * right/forward so it can be re-applied after the mount rotates.
     */
    private static double[] worldOffsetToLocal(double worldX, double worldZ, float bodyYaw)
    {
        float rad = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double localRight = worldX * cos + worldZ * sin;
        double localForward = -worldX * sin + worldZ * cos;

        return new double[] {localRight, localForward};
    }

    private static double[] localOffsetToWorld(double localRight, double localForward, float bodyYaw)
    {
        float rad = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double worldX = localRight * cos - localForward * sin;
        double worldZ = localRight * sin + localForward * cos;

        return new double[] {worldX, worldZ};
    }

    public static Entity getRenderedEntity(IEntity entity)
    {
        if (entity == null)
        {
            return null;
        }

        Form form = entity.getForm();

        if (form instanceof MobForm mobForm && mobForm.getRenderer() instanceof MobFormRenderer renderer)
        {
            return renderer.getRenderEntity();
        }

        return null;
    }

    public static void applyRiding(Entity rider, IEntity source)
    {
        if (rider == null || source == null)
        {
            return;
        }

        IEntity mountTarget = source.getMountTarget();

        if (mountTarget == null)
        {
            if (rider.hasVehicle())
            {
                rider.stopRiding();
            }

            return;
        }

        Entity vehicle = MorphMountSync.getRenderedEntity(mountTarget);

        if (vehicle == null)
        {
            return;
        }

        if (rider.getVehicle() != vehicle)
        {
            rider.stopRiding();
            rider.startRiding(vehicle);
        }

        rider.setVelocity(0D, 0D, 0D);
        rider.setSprinting(false);
        rider.fallDistance = 0F;

        if (rider instanceof LivingEntity living)
        {
            MorphMountSync.zeroLimbAnimator(living);
        }
    }

    public static void zeroLimbAnimator(IEntity entity)
    {
        if (entity == null)
        {
            return;
        }

        LimbAnimator limbAnimator = entity.getLimbAnimator();

        if (limbAnimator instanceof LimbAnimatorAccessor accessor)
        {
            accessor.setPrevSpeed(0F);
            accessor.setSpeed(0F);
        }
    }

    private static void zeroLimbAnimator(LivingEntity living)
    {
        if (living == null)
        {
            return;
        }

        LimbAnimator limbAnimator = living.limbAnimator;

        if (limbAnimator instanceof LimbAnimatorAccessor accessor)
        {
            accessor.setPrevSpeed(0F);
            accessor.setSpeed(0F);
        }
    }

    public static Entity resolveVehicleEntity(IEntity entity)
    {
        if (entity instanceof MCEntity mcEntity)
        {
            Entity vehicle = mcEntity.getMcEntity().getVehicle();

            if (vehicle != null)
            {
                return vehicle;
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null || !player.hasVehicle())
        {
            return null;
        }

        if (entity instanceof MCEntity mcEntity && mcEntity.getMcEntity() instanceof PlayerEntity)
        {
            return player.getVehicle();
        }

        if (entity instanceof StubEntity)
        {
            double dx = entity.getX() - player.getX();
            double dy = entity.getY() - player.getY();
            double dz = entity.getZ() - player.getZ();

            if (dx * dx + dy * dy + dz * dz < 4D)
            {
                return player.getVehicle();
            }
        }

        return null;
    }
}
