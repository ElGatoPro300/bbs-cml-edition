package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Event allowing addons to observe texture manager flushes and asset hot-reloads
 * in order to clear generated runtime/tween texture caches.
 */
public class RegisterTextureInvalidationEvent
{
    @FunctionalInterface
    public interface TexturePathInvalidationListener
    {
        public void onInvalidatePath(TextureManager manager, Path path, WatchDogEvent event);
    }

    private static final List<Consumer<TextureManager>> invalidateAllListeners = new ArrayList<>();
    private static final List<TexturePathInvalidationListener> invalidatePathListeners = new ArrayList<>();

    public void registerInvalidateAll(Consumer<TextureManager> listener)
    {
        if (listener != null)
        {
            invalidateAllListeners.add(listener);
        }
    }

    public void registerInvalidatePath(TexturePathInvalidationListener listener)
    {
        if (listener != null)
        {
            invalidatePathListeners.add(listener);
        }
    }

    public static void postInvalidateAll(TextureManager manager)
    {
        for (Consumer<TextureManager> listener : invalidateAllListeners)
        {
            try
            {
                listener.accept(manager);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postInvalidatePath(TextureManager manager, Path path, WatchDogEvent event)
    {
        for (TexturePathInvalidationListener listener : invalidatePathListeners)
        {
            try
            {
                listener.onInvalidatePath(manager, path, event);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
