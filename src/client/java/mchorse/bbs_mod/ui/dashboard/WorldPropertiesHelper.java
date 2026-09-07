package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.BBSSettings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;

import java.util.function.IntConsumer;

/**
 * Applies world-property changes through the integrated-server API when available (no chat spam, no
 * command parsing lag). Falls back to silent {@code sendCommand} only on multiplayer without direct access.
 */
public class WorldPropertiesHelper
{
    private static volatile long clientTimeOverride = -1L;

    /** Gamma override (1.0 = vanilla 100%, 15.0 = 1500% full-bright), read by
     *  {@code SimpleOptionMixin} so it can exceed the vanilla 0..1 slider range. Negative
     *  means "no override" (vanilla setting applies). */
    private static volatile double gammaOverride = -1D;

    private WorldPropertiesHelper()
    {}

    /**
     * Client-side time override for smooth sun rotation while dragging the World Properties time slider
     * (same mechanism as the Sun Rotation curve via {@code ClientWorldPropertiesMixin}).
     */
    public static void setClientTimeOverride(long time)
    {
        clientTimeOverride = time % 24000L;

        if (clientTimeOverride < 0L)
        {
            clientTimeOverride += 24000L;
        }
    }

    public static Long getClientTimeOverride()
    {
        return clientTimeOverride >= 0L ? clientTimeOverride : null;
    }

    public static void clearClientTimeOverride()
    {
        clientTimeOverride = -1L;
    }

    public static boolean isGammaOverrideEnabled()
    {
        return gammaOverride >= 0D;
    }

    public static void setGammaOverrideEnabled(boolean enabled)
    {
        if (BBSSettings.worldGammaOverride != null)
        {
            BBSSettings.worldGammaOverride.set(enabled);
        }

        if (enabled)
        {
            double percent = BBSSettings.worldGammaPercent != null ? BBSSettings.worldGammaPercent.get() : 100D;

            setGammaPercent(percent);
        }
        else
        {
            clearGammaOverride();
        }
    }

    public static void clearGammaOverride()
    {
        gammaOverride = -1D;

        if (BBSSettings.worldGammaOverride != null)
        {
            BBSSettings.worldGammaOverride.set(false);
        }
    }

    public static void setGammaPercent(double percent)
    {
        gammaOverride = Math.max(0D, percent) / 100D;

        if (BBSSettings.worldGammaPercent != null)
        {
            BBSSettings.worldGammaPercent.set(percent);
        }

        if (BBSSettings.worldGammaOverride != null)
        {
            BBSSettings.worldGammaOverride.set(true);
        }
    }

    public static Double getGammaOverride()
    {
        return gammaOverride >= 0D ? gammaOverride : null;
    }

    public static double getGammaPercent()
    {
        if (gammaOverride >= 0D)
        {
            return gammaOverride * 100D;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        return mc.options == null ? 100D : mc.options.getGamma().getValue() * 100D;
    }

    public static void setSunPathRotation(float degrees)
    {
        if (degrees > 180F)
        {
            degrees = 180F;
        }
        else if (degrees < -180F)
        {
            degrees = -180F;
        }

        if (BBSSettings.worldSunPathRotation != null)
        {
            BBSSettings.worldSunPathRotation.set(degrees);
        }
    }

    public static float getSunPathRotation()
    {
        if (BBSSettings.worldSunPathRotation != null)
        {
            return BBSSettings.worldSunPathRotation.get();
        }

        return 0F;
    }

    public static void setNightVision(boolean enabled)
    {
        executeCommand(enabled
            ? "effect give @a minecraft:night_vision infinite 1 true"
            : "effect clear @a minecraft:night_vision");
    }

    public static boolean hasNightVision()
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        return player != null && player.hasStatusEffect(StatusEffects.NIGHT_VISION);
    }

    public static void setTimeOfDay(long time)
    {
        setClientTimeOverride(time);

        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();

        if (server != null)
        {
            server.execute(() ->
            {
                ServerWorld world = server.getOverworld();

                if (world != null)
                {
                    world.setTimeOfDay(time);
                }
            });

            return;
        }

        sendSilentCommand("time set " + time);
    }

    public static void setGamerule(GameRules.Key<GameRules.BooleanRule> key, boolean value)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();

        if (server != null)
        {
            server.execute(() ->
            {
                ServerWorld world = server.getOverworld();

                if (world != null)
                {
                    world.getGameRules().get(key).set(value, server);
                }
            });

            return;
        }

        String name = key.getName();

        sendSilentCommand("gamerule " + name + " " + value);
    }

    public static void setWeatherClear()
    {
        executeWeatherCommand("weather clear");
    }

    public static void setWeatherRain()
    {
        executeWeatherCommand("weather rain");
    }

    public static void setWeatherThunder()
    {
        executeWeatherCommand("weather thunder");
    }

    public static void killAllMobs(IntConsumer callback)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();

        if (server != null)
        {
            server.execute(() ->
            {
                ServerWorld world = server.getOverworld();
                int count = 0;

                if (world != null)
                {
                    for (Entity entity : world.iterateEntities())
                    {
                        if (!(entity instanceof PlayerEntity))
                        {
                            count++;
                        }
                    }
                }

                sendSilentCommandOnServer(server, "kill @e[type=!minecraft:player]");

                if (callback != null)
                {
                    int finalCount = count;
                    mc.execute(() -> callback.accept(finalCount));
                }
            });

            return;
        }

        sendSilentCommand("kill @e[type=!minecraft:player]");

        if (callback != null)
        {
            callback.accept(-1);
        }
    }

    /** Runs any command silently, through the integrated server when available. */
    public static void executeCommand(String command)
    {
        executeWeatherCommand(command);
    }

    private static void executeWeatherCommand(String command)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();

        if (server != null)
        {
            server.execute(() -> sendSilentCommandOnServer(server, command));

            return;
        }

        sendSilentCommand(command);
    }

    public static boolean readGamerule(GameRules.Key<GameRules.BooleanRule> key, boolean fallback)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();

        if (server != null)
        {
            ServerWorld world = server.getOverworld();

            if (world != null)
            {
                try
                {
                    return world.getGameRules().getBoolean(key);
                }
                catch (Exception e)
                {
                    return fallback;
                }
            }
        }

        ClientWorld world = mc.world;

        if (world == null)
        {
            return fallback;
        }

        try
        {
            return world.getGameRules().getBoolean(key);
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private static void sendSilentCommandOnServer(MinecraftServer server, String command)
    {
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
    }

    private static void sendSilentCommand(String command)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player != null)
        {
            player.networkHandler.sendCommand(command);
        }
    }
}
