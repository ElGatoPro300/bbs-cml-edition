package mchorse.bbs_mod.client;

import mchorse.bbs_mod.utils.ExportWorldFxFreeze;

/**
 * During HQ chunk-settle, world/client ticks run fast so terrain can catch up.
 * Particles must not age during that burst or dig/emit effects die before capture.
 */
public final class ExportParticleFreeze
{
    private ExportParticleFreeze()
    {}

    public static boolean isFrozen()
    {
        return ExportWorldFxFreeze.areParticlesFrozen();
    }
}
