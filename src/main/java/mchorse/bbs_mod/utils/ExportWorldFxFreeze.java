package mchorse.bbs_mod.utils;

/**
 * Shared HQ-export freeze flag (integrated server + client).
 * While set, item drops keep their spawn pose and client dig particles do not age
 * during chunk catch-up ticks.
 */
public final class ExportWorldFxFreeze
{
    private static volatile boolean particlesFrozen;
    private static volatile boolean itemPhysicsFrozen;

    private ExportWorldFxFreeze()
    {}

    public static void setParticlesFrozen(boolean frozen)
    {
        particlesFrozen = frozen;
    }

    public static void setItemPhysicsFrozen(boolean frozen)
    {
        itemPhysicsFrozen = frozen;
    }

    public static boolean areParticlesFrozen()
    {
        return particlesFrozen;
    }

    public static boolean areItemPhysicsFrozen()
    {
        return itemPhysicsFrozen;
    }

    public static void clear()
    {
        particlesFrozen = false;
        itemPhysicsFrozen = false;
    }
}
