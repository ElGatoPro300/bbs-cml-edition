package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.RecordingPauseHelper;
import mchorse.bbs_mod.ui.dashboard.EditorSpectatorHelper;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.VideoRecorder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServerLoader;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.level.storage.LevelStorage;

import java.nio.file.Path;

public class WorldLaunchHelper
{
    private static final int DISCONNECT_RETRY_TICKS = 40;
    private static final int DISCONNECT_FAILSAFE_TICKS = 600;

    private static String pendingWorldFolder;
    private static int pendingWaitTicks;

    public static boolean isCurrentWorld(MinecraftClient client, String worldFolder)
    {
        if (worldFolder == null || worldFolder.isEmpty())
        {
            return false;
        }

        if (!client.isIntegratedServerRunning() || client.getServer() == null)
        {
            return false;
        }

        Path currentSave = client.getServer().getSavePath(WorldSavePath.ROOT);

        for (LevelStorage.LevelSave save : client.getLevelStorage().getLevelList().levels())
        {
            if (!currentSave.equals(save.path()))
            {
                continue;
            }

            if (WorldLaunchHelper.matchesWorldFolder(worldFolder, save.getRootPath()))
            {
                return true;
            }
        }

        String current = currentSave.getFileName().toString();

        return WorldLaunchHelper.matchesWorldFolder(worldFolder, current);
    }

    private static boolean matchesWorldFolder(String expected, String actual)
    {
        if (expected == null || actual == null)
        {
            return false;
        }

        if (expected.equals(actual))
        {
            return true;
        }

        return expected.equalsIgnoreCase(actual);
    }

    /**
     * True when the client is already inside a loaded world session.
     */
    public static boolean isInLoadedWorld(MinecraftClient client)
    {
        return client != null && client.world != null;
    }

    public static void loadWorld(String worldFolder)
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (WorldLaunchHelper.isCurrentWorld(client, worldFolder))
        {
            return;
        }

        WorldLaunchHelper.prepareClientForWorldSwitch(client);

        if (WorldLaunchHelper.needsDisconnect(client))
        {
            WorldLaunchHelper.pendingWorldFolder = worldFolder;
            WorldLaunchHelper.pendingWaitTicks = 0;
            WorldLaunchHelper.requestDisconnect(client);

            return;
        }

        WorldLaunchHelper.startWorldLoad(client, worldFolder);
    }

    public static void tick(MinecraftClient client)
    {
        if (WorldLaunchHelper.pendingWorldFolder == null)
        {
            return;
        }

        WorldLaunchHelper.ensureRenderTarget(client);

        if (WorldLaunchHelper.needsDisconnect(client))
        {
            WorldLaunchHelper.pendingWaitTicks += 1;

            if (WorldLaunchHelper.pendingWaitTicks >= WorldLaunchHelper.DISCONNECT_FAILSAFE_TICKS)
            {
                WorldLaunchHelper.abortPendingLaunch(client);

                return;
            }

            if (WorldLaunchHelper.pendingWaitTicks % WorldLaunchHelper.DISCONNECT_RETRY_TICKS == 0)
            {
                WorldLaunchHelper.requestDisconnect(client);
            }

            return;
        }

        String folder = WorldLaunchHelper.pendingWorldFolder;

        WorldLaunchHelper.pendingWorldFolder = null;
        WorldLaunchHelper.pendingWaitTicks = 0;
        WorldLaunchHelper.startWorldLoad(client, folder);
    }

    public static void onClientDisconnected(MinecraftClient client)
    {
        WorldLaunchHelper.ensureRenderTarget(client);
    }

    public static void clearPending()
    {
        WorldLaunchHelper.pendingWorldFolder = null;
        WorldLaunchHelper.pendingWaitTicks = 0;
    }

    private static boolean needsDisconnect(MinecraftClient client)
    {
        return client.world != null || client.isIntegratedServerRunning();
    }

    private static void requestDisconnect(MinecraftClient client)
    {
        WorldLaunchHelper.prepareClientForWorldSwitch(client);

        ClientWorld world = client.world;

        if (world != null)
        {
            world.disconnect();
        }

        client.disconnect(new TitleScreen());
    }

    private static void abortPendingLaunch(MinecraftClient client)
    {
        WorldLaunchHelper.clearPending();
        WorldLaunchHelper.ensureRenderTarget(client);

        if (client.currentScreen == null)
        {
            client.setScreen(new TitleScreen());
        }
    }

    private static void prepareClientForWorldSwitch(MinecraftClient client)
    {
        if (client.currentScreen instanceof UIScreen)
        {
            client.setScreen(null);
        }

        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (videoRecorder.isRecording())
        {
            videoRecorder.stopRecording();
        }

        BBSModClient.getCameraController().reset();
        BBSRendering.setCustomSize(false);
        WorldLaunchHelper.ensureRenderTarget(client);
        RecordingPauseHelper.reset();
        EditorSpectatorHelper.restore();
    }

    private static void ensureRenderTarget(MinecraftClient client)
    {
        BBSRendering.ensureMainFramebuffer();
        client.getFramebuffer().beginWrite(false);
    }

    private static void startWorldLoad(MinecraftClient client, String worldFolder)
    {
        client.execute(() ->
        {
            WorldLaunchHelper.ensureRenderTarget(client);

            IntegratedServerLoader loader = client.createIntegratedServerLoader();

            loader.start(worldFolder, WorldLaunchHelper::clearPending);
        });
    }
}
