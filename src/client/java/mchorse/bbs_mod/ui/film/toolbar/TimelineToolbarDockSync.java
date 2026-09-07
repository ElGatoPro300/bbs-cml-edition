package mchorse.bbs_mod.ui.film.toolbar;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Keeps timeline toolbar dock positions in sync across linked hosts (for example
 * the replay timeline and embedded keyframe editors inside clip panels).
 */
public final class TimelineToolbarDockSync
{
    public static void setDock(String panelId, TimelineToolbarDock dock)
    {
        if (panelId == null || panelId.isEmpty() || dock == null)
        {
            return;
        }

        BBSSettings.timelineToolbarDocks.setDock(panelId, dock);
    }

    public static void refreshLinkedToolbars(UIElement host, String changedPanelId)
    {
        UIFilmPanel film = findFilmPanel(host);

        if (film == null || changedPanelId == null)
        {
            return;
        }

        if (TimelineToolbarDockLayout.PANEL_REPLAY.equals(changedPanelId))
        {
            refreshReplayEditor(film.replayEditor);
            refreshClipsPanel(film.cameraEditor);
            refreshClipsPanel(film.actionEditor);
        }
        else if (TimelineToolbarDockLayout.PANEL_SCREEN_NODE_VIEW.equals(changedPanelId))
        {
            refreshClipsPanel(film.cameraEditor);
            refreshClipsPanel(film.actionEditor);
        }
        else if (TimelineToolbarDockLayout.PANEL_CAMERA.equals(changedPanelId))
        {
            refreshClipsPanel(film.cameraEditor);
        }
        else if (TimelineToolbarDockLayout.PANEL_ACTION.equals(changedPanelId))
        {
            refreshClipsPanel(film.actionEditor);
        }
    }

    public static void refreshFilmPanel(UIFilmPanel film)
    {
        if (film == null)
        {
            return;
        }

        refreshReplayEditor(film.replayEditor);
        refreshClipsPanel(film.cameraEditor);
        refreshClipsPanel(film.actionEditor);
    }

    public static void applySettingsChange()
    {
        UIFilmPanel film = findOpenFilmPanel();

        if (film != null)
        {
            refreshFilmPanel(film);
        }
    }

    public static UIFilmPanel findOpenFilmPanel()
    {
        UIDashboard dashboard = BBSModClient.peekDashboard();

        if (dashboard == null || dashboard.getPanels() == null)
        {
            return null;
        }

        if (dashboard.getPanels().panel instanceof UIFilmPanel film)
        {
            return film;
        }

        return null;
    }

    private static void refreshReplayEditor(UIReplaysEditor editor)
    {
        if (editor != null)
        {
            editor.applyToolbarDockLayout();
        }
    }

    private static void refreshClipsPanel(UIClipsPanel panel)
    {
        if (panel != null)
        {
            panel.applyToolbarDockLayout();
        }
    }

    private static UIFilmPanel findFilmPanel(UIElement host)
    {
        UIElement current = host;

        while (current != null)
        {
            if (current instanceof UIFilmPanel film)
            {
                return film;
            }

            current = current.getParent();
        }

        return null;
    }

    private TimelineToolbarDockSync()
    {}
}
