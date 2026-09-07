package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UIBlockRepeatKeyframeUtils
{
    private static final int GROUP_COLOR = 0xffffa040;
    private static final String GROUP_KEY = "repeat";

    private static final String[] REPEAT_TRACKS = {
        "repeat_x",
        "repeat_y",
        "repeat_z"
    };

    private static final String[] REPEAT_CENTER_TRACKS = {
        "repeat_center_x",
        "repeat_center_y",
        "repeat_center_z"
    };

    private UIBlockRepeatKeyframeUtils()
    {
    }

    public static boolean isRepeatCenterTimelineHidden(String key)
    {
        if (key == null || key.isEmpty())
        {
            return false;
        }

        String name = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;

        for (String track : REPEAT_CENTER_TRACKS)
        {
            if (track.equals(name))
            {
                return true;
            }
        }

        return false;
    }

    public static void groupRepeatSheets(List<UIKeyframeSheet> sheets, Map<String, Boolean> collapsed, Runnable onToggle)
    {
        List<UIKeyframeSheet> repeatSheets = new ArrayList<>();
        int insertAt = -1;

        for (String track : REPEAT_TRACKS)
        {
            UIKeyframeSheet sheet = findSheet(sheets, track);

            if (sheet != null)
            {
                int index = sheets.indexOf(sheet);

                insertAt = insertAt < 0 ? index : Math.min(insertAt, index);
                repeatSheets.add(sheet);
            }
        }

        if (repeatSheets.isEmpty())
        {
            return;
        }

        /* Center tracks are edited through the Centered toggle inside the repeat
         * keyframe editor, so they never show up as timeline rows. */
        Iterator<UIKeyframeSheet> iterator = sheets.iterator();

        while (iterator.hasNext())
        {
            UIKeyframeSheet sheet = iterator.next();
            String name = sheetName(sheet.id);

            if (isRepeatTrack(name) || isRepeatCenterTrack(name))
            {
                iterator.remove();
            }
        }

        if (insertAt < 0)
        {
            insertAt = sheets.size();
        }

        boolean expanded = !collapsed.getOrDefault(GROUP_KEY, false);
        int parentLevel = repeatSheets.get(0).level;

        UIKeyframeSheet header = UIKeyframeSheet.groupHeader(
            "__repeat__" + GROUP_KEY,
            UIKeys.FORMS_EDITORS_BLOCK_REPEAT,
            GROUP_COLOR,
            GROUP_KEY,
            expanded,
            () ->
            {
                collapsed.put(GROUP_KEY, !collapsed.getOrDefault(GROUP_KEY, false));

                if (onToggle != null)
                {
                    onToggle.run();
                }
            }
        );

        header.level = parentLevel;
        sheets.add(insertAt, header);

        if (!expanded)
        {
            return;
        }

        int childLevel = parentLevel + 1;
        int childIndex = insertAt + 1;

        for (UIKeyframeSheet sheet : repeatSheets)
        {
            String name = sheetName(sheet.id);

            sheet.level = childLevel;
            sheet.groupKey = GROUP_KEY;
            sheet.title = repeatTrackTitle(name);
            sheet.icon(repeatTrackIcon(name));
            sheets.add(childIndex++, sheet);
        }
    }

    private static UIKeyframeSheet findSheet(List<UIKeyframeSheet> sheets, String track)
    {
        for (UIKeyframeSheet sheet : sheets)
        {
            if (track.equals(sheetName(sheet.id)))
            {
                return sheet;
            }
        }

        return null;
    }

    private static boolean isRepeatTrack(String name)
    {
        for (String track : REPEAT_TRACKS)
        {
            if (track.equals(name))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isRepeatCenterTrack(String name)
    {
        for (String track : REPEAT_CENTER_TRACKS)
        {
            if (track.equals(name))
            {
                return true;
            }
        }

        return false;
    }

    public static IKey repeatTrackTitle(String track)
    {
        if ("repeat_x".equals(track))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_X;
        }

        if ("repeat_y".equals(track))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_Y;
        }

        if ("repeat_z".equals(track))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_Z;
        }

        if ("repeat_center_x".equals(track))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_X;
        }

        if ("repeat_center_y".equals(track))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_Y;
        }

        if ("repeat_center_z".equals(track))
        {
            return UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_Z;
        }

        return IKey.constant(track);
    }

    public static int repeatAxisColor(String axis)
    {
        if ("repeat_x".equals(axis) || "repeat_center_x".equals(axis))
        {
            return Colors.RED;
        }

        if ("repeat_y".equals(axis) || "repeat_center_y".equals(axis))
        {
            return Colors.GREEN;
        }

        if ("repeat_z".equals(axis) || "repeat_center_z".equals(axis))
        {
            return Colors.BLUE;
        }

        return UIReplaysEditor.getColor(axis);
    }

    private static Icon repeatTrackIcon(String track)
    {
        if ("repeat_x".equals(track) || "repeat_center_x".equals(track))
        {
            return Icons.X;
        }

        if ("repeat_y".equals(track) || "repeat_center_y".equals(track))
        {
            return Icons.Y;
        }

        if ("repeat_z".equals(track) || "repeat_center_z".equals(track))
        {
            return Icons.Z;
        }

        return Icons.BLOCK;
    }

    private static String sheetName(String id)
    {
        if (id == null || id.isEmpty())
        {
            return "";
        }

        int slash = id.lastIndexOf('/');

        return slash == -1 ? id : id.substring(slash + 1);
    }
}
