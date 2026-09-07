package mchorse.bbs_mod.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;

/**
 * Detects when client terrain meshes have finished rebuilding after block edits
 * (e.g. {@code /fill}) so HQ export can wait before capturing a frame.
 */
public final class ExportChunkSettle
{
    private ExportChunkSettle()
    {}

    /**
     * @return {@code true} when chunk rebuild/upload queues look idle enough to capture
     */
    public static boolean isTerrainSettled()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.worldRenderer == null)
        {
            return true;
        }

        WorldRenderer worldRenderer = client.worldRenderer;

        try
        {
            if (!worldRenderer.isTerrainRenderComplete())
            {
                return false;
            }

            ChunkBuilder builder = worldRenderer.getChunkBuilder();

            if (builder == null)
            {
                return true;
            }

            if (!builder.isEmpty())
            {
                return false;
            }

            if (builder.getToBatchCount() > 0 || builder.getChunksToUpload() > 0)
            {
                return false;
            }

            return true;
        }
        catch (Throwable t)
        {
            /* Mapping/version drift: fall back to timed settle only. */
            return true;
        }
    }

    /**
     * Force pending GPU uploads so settle can observe a clean queue next frames.
     */
    public static void pumpUploads()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.worldRenderer == null)
        {
            return;
        }

        try
        {
            ChunkBuilder builder = client.worldRenderer.getChunkBuilder();

            if (builder != null)
            {
                builder.upload();
            }
        }
        catch (Throwable t)
        {}
    }
}
