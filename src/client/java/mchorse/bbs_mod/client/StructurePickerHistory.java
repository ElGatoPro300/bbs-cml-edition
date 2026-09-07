package mchorse.bbs_mod.client;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo/redo stack for Structure Picker panel actions (break, remove, import).
 */
public class StructurePickerHistory
{
    private static final int MAX_ENTRIES = 32;

    private static final Deque<Entry> undoStack = new ArrayDeque<>();
    private static final Deque<Entry> redoStack = new ArrayDeque<>();

    public interface Entry
    {
        void undo();

        void redo();
    }

    public static void push(Entry entry)
    {
        if (entry == null)
        {
            return;
        }

        StructurePickerHistory.undoStack.addLast(entry);
        StructurePickerHistory.redoStack.clear();

        while (StructurePickerHistory.undoStack.size() > MAX_ENTRIES)
        {
            StructurePickerHistory.undoStack.removeFirst();
        }
    }

    public static boolean canUndo()
    {
        return !StructurePickerHistory.undoStack.isEmpty();
    }

    public static boolean canRedo()
    {
        return !StructurePickerHistory.redoStack.isEmpty();
    }

    public static boolean undo()
    {
        if (StructurePickerHistory.undoStack.isEmpty())
        {
            return false;
        }

        Entry entry = StructurePickerHistory.undoStack.removeLast();

        entry.undo();
        StructurePickerHistory.redoStack.addLast(entry);

        return true;
    }

    public static boolean redo()
    {
        if (StructurePickerHistory.redoStack.isEmpty())
        {
            return false;
        }

        Entry entry = StructurePickerHistory.redoStack.removeLast();

        entry.redo();
        StructurePickerHistory.undoStack.addLast(entry);

        return true;
    }

    public static void clear()
    {
        StructurePickerHistory.undoStack.clear();
        StructurePickerHistory.redoStack.clear();
    }
}
