package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Event allowing addons to observe UndoManager lifecycle:
 * when an undo action is pushed, executed, or redone.
 */
public class RegisterUndoEvent
{
    private static final List<BiConsumer<UndoManager<?>, IUndo<?>>> pushListeners = new ArrayList<>();
    private static final List<BiConsumer<UndoManager<?>, IUndo<?>>> undoListeners = new ArrayList<>();
    private static final List<BiConsumer<UndoManager<?>, IUndo<?>>> redoListeners = new ArrayList<>();

    public void registerPush(BiConsumer<UndoManager<?>, IUndo<?>> listener)
    {
        if (listener != null)
        {
            pushListeners.add(listener);
        }
    }

    public void registerUndo(BiConsumer<UndoManager<?>, IUndo<?>> listener)
    {
        if (listener != null)
        {
            undoListeners.add(listener);
        }
    }

    public void registerRedo(BiConsumer<UndoManager<?>, IUndo<?>> listener)
    {
        if (listener != null)
        {
            redoListeners.add(listener);
        }
    }

    public static void postPush(UndoManager<?> manager, IUndo<?> undo)
    {
        for (BiConsumer<UndoManager<?>, IUndo<?>> listener : pushListeners)
        {
            try
            {
                listener.accept(manager, undo);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postUndo(UndoManager<?> manager, IUndo<?> undo)
    {
        for (BiConsumer<UndoManager<?>, IUndo<?>> listener : undoListeners)
        {
            try
            {
                listener.accept(manager, undo);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postRedo(UndoManager<?> manager, IUndo<?> undo)
    {
        for (BiConsumer<UndoManager<?>, IUndo<?>> listener : redoListeners)
        {
            try
            {
                listener.accept(manager, undo);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
