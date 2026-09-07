package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class MobDeathActionClip extends ActionClip
{
    @Override
    public boolean isClient()
    {
        return true;
    }

    @Override
    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {
        World world = entity.getWorld();

        if (world == null || !world.isClient())
        {
            return;
        }

        Random random = world.getRandom();
        double x = entity.getX();
        double y = entity.getY() + entity.getEyeHeight() * 0.5D;
        double z = entity.getZ();
        float width = 0.6F;

        for (int i = 0; i < 20; i++)
        {
            double offsetX = (random.nextDouble() - 0.5D) * width;
            double offsetY = random.nextDouble() * 0.5D;
            double offsetZ = (random.nextDouble() - 0.5D) * width;
            double velocityX = random.nextGaussian() * 0.02D;
            double velocityY = random.nextGaussian() * 0.02D;
            double velocityZ = random.nextGaussian() * 0.02D;

            world.addParticleClient(ParticleTypes.POOF, x + offsetX, y + offsetY, z + offsetZ, velocityX, velocityY, velocityZ);
        }
    }

    @Override
    protected Clip create()
    {
        return new MobDeathActionClip();
    }
}
