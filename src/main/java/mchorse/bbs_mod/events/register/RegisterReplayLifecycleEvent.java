package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Event allowing addons to observe and handle replay lifecycle changes
 * such as creation, removal, duplication, and reordering.
 */
public class RegisterReplayLifecycleEvent
{
    @FunctionalInterface
    public interface ReplayReorderListener
    {
        public void onReordered(Film film, Replay replay, int fromIndex, int toIndex);
    }

    private static final List<BiConsumer<Film, Replay>> addListeners = new ArrayList<>();
    private static final List<BiConsumer<Film, Replay>> removeListeners = new ArrayList<>();
    private static final List<ReplayReorderListener> reorderListeners = new ArrayList<>();
    private static final List<BiConsumer<Replay, Replay>> duplicateListeners = new ArrayList<>();

    public void registerAdd(BiConsumer<Film, Replay> listener)
    {
        if (listener != null)
        {
            addListeners.add(listener);
        }
    }

    public void registerRemove(BiConsumer<Film, Replay> listener)
    {
        if (listener != null)
        {
            removeListeners.add(listener);
        }
    }

    public void registerReorder(ReplayReorderListener listener)
    {
        if (listener != null)
        {
            reorderListeners.add(listener);
        }
    }

    public void registerDuplicate(BiConsumer<Replay, Replay> listener)
    {
        if (listener != null)
        {
            duplicateListeners.add(listener);
        }
    }

    public static void postAdd(Film film, Replay replay)
    {
        for (BiConsumer<Film, Replay> listener : addListeners)
        {
            try
            {
                listener.accept(film, replay);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postRemove(Film film, Replay replay)
    {
        for (BiConsumer<Film, Replay> listener : removeListeners)
        {
            try
            {
                listener.accept(film, replay);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postReorder(Film film, Replay replay, int fromIndex, int toIndex)
    {
        for (ReplayReorderListener listener : reorderListeners)
        {
            try
            {
                listener.onReordered(film, replay, fromIndex, toIndex);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postDuplicate(Replay original, Replay copy)
    {
        for (BiConsumer<Replay, Replay> listener : duplicateListeners)
        {
            try
            {
                listener.accept(original, copy);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
