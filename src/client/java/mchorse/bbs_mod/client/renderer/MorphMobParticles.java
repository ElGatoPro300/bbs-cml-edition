package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;

import net.minecraft.entity.AnimationState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Emits and optionally suppresses vanilla mob particles for morph stub entities
 * during film replay playback.
 */
public final class MorphMobParticles
{
    private static final ThreadLocal<Boolean> SUPPRESS = new ThreadLocal<>();
    private static final Map<UUID, ParticleState> STATES = new WeakHashMap<>();

    private MorphMobParticles()
    {}

    public static boolean shouldSuppress()
    {
        return Boolean.TRUE.equals(SUPPRESS.get());
    }

    public static void beginTick(boolean enabled)
    {
        if (enabled)
        {
            SUPPRESS.remove();
        }
        else
        {
            SUPPRESS.set(true);
        }
    }

    public static void endTick()
    {
        SUPPRESS.remove();
    }

    public static void afterTick(Entity morph, IEntity source, boolean enabled)
    {
        if (!enabled || morph == null)
        {
            return;
        }

        World world = morph.getWorld();

        if (world == null || !world.isClient)
        {
            return;
        }

        ParticleState state = STATES.computeIfAbsent(morph.getUuid(), (key) -> new ParticleState());

        if (morph instanceof LivingEntity living)
        {
            emitDeathParticles(world, living, source, state);
        }

        emitStatusParticles(morph, source, state);
    }

    /**
     * Vanilla living entities only spawn the poof burst once when the corpse finishes
     * its death animation ({@code deathTime == 20}), not every tick while dying.
     */
    private static void emitDeathParticles(World world, LivingEntity living, IEntity source, ParticleState state)
    {
        int deathTime = source.getDeathTime();

        if (deathTime < 19)
        {
            state.deathBurstEmitted = false;
            return;
        }

        if (state.deathBurstEmitted)
        {
            return;
        }

        state.deathBurstEmitted = true;

        double x = living.getX();
        double y = living.getY() + living.getEyeHeight(living.getPose()) * 0.5D;
        double z = living.getZ();
        float width = Math.max(living.getWidth(), 0.6F);

        for (int i = 0; i < 20; ++i)
        {
            double offsetX = (living.getRandom().nextDouble() - 0.5D) * width;
            double offsetY = living.getRandom().nextDouble() * living.getHeight();
            double offsetZ = (living.getRandom().nextDouble() - 0.5D) * width;
            double dx = living.getRandom().nextGaussian() * 0.02D;
            double dy = living.getRandom().nextGaussian() * 0.02D;
            double dz = living.getRandom().nextGaussian() * 0.02D;

            world.addParticle(ParticleTypes.POOF, x + offsetX, y + offsetY, z + offsetZ, dx, dy, dz);
        }
    }

    private static void emitStatusParticles(Entity morph, IEntity source, ParticleState state)
    {
        if (morph instanceof WardenEntity warden)
        {
            emitWardenStatus(warden, source, state);
        }
    }

    private static void emitWardenStatus(WardenEntity warden, IEntity source, ParticleState state)
    {
        WardenEntity sourceWarden = getSourceWarden(source, warden);

        tickWardenAnimations(warden);

        if (sourceWarden != null)
        {
            tickWardenAnimations(sourceWarden);
        }

        boolean charging = sourceWarden != null
            ? isAnimationRunning(sourceWarden.chargingSonicBoomAnimationState)
            : isAnimationRunning(warden.chargingSonicBoomAnimationState);

        if (charging && !state.chargingSonicBoom)
        {
            triggerSonicBoom(warden);
        }

        state.chargingSonicBoom = charging;

        boolean roaring = sourceWarden != null
            ? isAnimationRunning(sourceWarden.roaringAnimationState)
            : isAnimationRunning(warden.roaringAnimationState);

        if (roaring && !state.roaring)
        {
            warden.handleStatus((byte) 4);
        }

        state.roaring = roaring;

        boolean sniffing = sourceWarden != null
            ? isAnimationRunning(sourceWarden.sniffingAnimationState)
            : isAnimationRunning(warden.sniffingAnimationState);

        if (sniffing && !state.sniffing)
        {
            warden.handleStatus((byte) 61);
        }

        state.sniffing = sniffing;
    }

    private static WardenEntity getSourceWarden(IEntity source, WardenEntity morph)
    {
        if (source instanceof MCEntity mcEntity)
        {
            Entity entity = mcEntity.getMcEntity();

            if (entity instanceof WardenEntity warden)
            {
                return warden;
            }
        }

        World world = morph.getWorld();

        if (world == null)
        {
            return null;
        }

        Box box = morph.getBoundingBox().expand(2.0D);

        for (WardenEntity warden : world.getEntitiesByClass(WardenEntity.class, box, (candidate) -> candidate != morph))
        {
            if (isAnimationRunning(warden.chargingSonicBoomAnimationState)
                || isAnimationRunning(warden.roaringAnimationState)
                || isAnimationRunning(warden.sniffingAnimationState))
            {
                return warden;
            }
        }

        return null;
    }

    private static void tickWardenAnimations(WardenEntity warden)
    {
        int age = warden.age;

        warden.chargingSonicBoomAnimationState.skip(age, 1.0F);
        warden.roaringAnimationState.skip(age, 1.0F);
        warden.sniffingAnimationState.skip(age, 1.0F);
        warden.attackingAnimationState.skip(age, 1.0F);
        warden.emergingAnimationState.skip(age, 1.0F);
        warden.diggingAnimationState.skip(age, 1.0F);
    }

    private static void triggerSonicBoom(WardenEntity warden)
    {
        warden.handleStatus(EntityStatuses.SONIC_BOOM);
        spawnSonicBoomParticle(warden);
    }

    private static void spawnSonicBoomParticle(Entity entity)
    {
        World world = entity.getWorld();

        if (world == null || !world.isClient)
        {
            return;
        }

        double x = entity.getX();
        double y = entity.getY() + entity.getHeight() * 0.5D;
        double z = entity.getZ();

        world.addImportantParticle(ParticleTypes.SONIC_BOOM, x, y, z, 0D, 0D, 0D);
    }

    private static boolean isAnimationRunning(AnimationState animationState)
    {
        return animationState != null && animationState.isRunning();
    }

    public static void clear(Entity morph)
    {
        if (morph != null)
        {
            STATES.remove(morph.getUuid());
        }
    }

    private static class ParticleState
    {
        public boolean chargingSonicBoom;
        public boolean roaring;
        public boolean sniffing;
        public boolean deathBurstEmitted;
    }
}
