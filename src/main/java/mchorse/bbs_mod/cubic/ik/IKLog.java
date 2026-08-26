package mchorse.bbs_mod.cubic.ik;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Diagnostic dump for the IK pipeline, written to {@code ik-log.txt} in the game
 * folder — the thing to ask for when a limb refuses to bend and nothing throws.
 *
 * <p>It answers the questions guessing cannot: did the limb COMPILE at all (and
 * if not, why), which solver PATH did it take, what goal was it handed, and how
 * far did the effector end up from it.
 *
 * <p>Deliberately deduplicated rather than per-frame: a steady 60 fps through
 * several render passes would bury the one line that matters under thousands of
 * identical ones. Each distinct message is written ONCE per session, so the file
 * stays short enough to read whole. Flip {@link #ENABLED} off to make every call
 * here a no-op.
 */
public final class IKLog
{
    /** Master switch. Off = every call below returns immediately and no file is touched. */
    public static final boolean ENABLED = true;

    /** The game runs in {@code run/}, so the name must be bare or it lands in a folder that does not exist. */
    private static final String FILE = "ik-log.txt";

    /** Messages already written, so a per-frame call logs once and then goes quiet. */
    private static final Set<String> SEEN = new LinkedHashSet<>();

    /** Safety valve: a rig that somehow produces endless distinct lines still cannot fill the disk. */
    private static final int MAX_LINES = 400;

    private static boolean started;

    private IKLog()
    {
    }

    /** A limb that did not compile, and the reason — the first thing to check when IK "does nothing". */
    public static void rejected(String tip, String why)
    {
        write("REJECTED  " + tip + "  —  " + why);
    }

    /** A limb that compiled, with the chain it spans. */
    public static void compiled(String tip, String controller, String pole, boolean classic, java.util.List<String> chain)
    {
        write("compiled  " + tip + "  controller=" + controller
            + "  pole=" + (pole == null || pole.isEmpty() ? "(none)" : pole)
            + "  classic=" + classic
            + "  chain=" + chain);
    }

    /** Which solver a group of limbs went to, and why. */
    public static void path(String what, String why)
    {
        write("path      " + what + "  —  " + why);
    }

    /** A finished solve: where the effector wanted to be and where it ended up. */
    public static void solved(String tip, String goal, String reached, float error)
    {
        write("solved    " + tip + "  goal=" + goal + "  reached=" + reached + "  error=" + error);
    }

    /** Anything else worth one line. */
    public static void note(String message)
    {
        write("note      " + message);
    }

    private static synchronized void write(String line)
    {
        if (!ENABLED || SEEN.size() >= MAX_LINES || !SEEN.add(line))
        {
            return;
        }

        try (FileWriter writer = new FileWriter(FILE, started))
        {
            if (!started)
            {
                writer.write("--- BBS IK log; each distinct line appears once per session ---\n");
                started = true;
            }

            writer.write(line);
            writer.write('\n');
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
