package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.UIScreenNodeEditor;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbar;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarDock;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarDockLayout;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarRegistry;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarWiring;
import mchorse.bbs_mod.ui.film.utils.UIFilmUndoHandler;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.factory.IFactory;
import mchorse.bbs_mod.utils.undo.IUndoElement;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIClipsPanel extends UIElement implements IUIClipsDelegate
{
    public UIClips clips;
    public UIFilmPanel filmPanel;
    public TimelineToolbar toolbar;

    /**
     * Whether this clip panel drives the camera timeline (as opposed to the
     * action timeline). Used by the toolbar to pick which hierarchy to display.
     */
    private final boolean isCameraTimeline;

    public boolean isCameraTimeline()
    {
        return this.isCameraTimeline;
    }

    private UIClip panel;

    public UIClip getClipPanel()
    {
        return this.panel;
    }

    private UIElement target;
    /** Last embed reported to {@link #onEmbedViewChanged}; used to detect real leave. */
    private UIElement lastEmbeddedView;

    public UIClipsPanel(UIFilmPanel panel, IFactory<Clip, ClipFactoryData> factory, boolean isCameraTimeline)
    {
        this.filmPanel = panel;
        this.isCameraTimeline = isCameraTimeline;
        this.clips = new UIClips(this, factory);

        this.add(this.clips);

        this.toolbar = new TimelineToolbar();
        this.toolbar.setInteractionCancelListener(this::cancelToolbarInteraction);
        this.applyDefaultToolbarSections();
        this.add(this.toolbar);

        this.applyToolbarDockLayout();

        this.clips.setEmbedViewListener(this::onEmbedViewChanged);
    }

    private String getToolbarPanelId()
    {
        return this.isCameraTimeline
            ? TimelineToolbarDockLayout.PANEL_CAMERA
            : TimelineToolbarDockLayout.PANEL_ACTION;
    }

    public String getToolbarStoragePanelId()
    {
        UIElement embed = this.clips.getEmbeddedView();

        if (embed instanceof UIKeyframeEditor)
        {
            return TimelineToolbarDockLayout.PANEL_REPLAY;
        }

        if (embed instanceof UIScreenNodeEditor)
        {
            return TimelineToolbarDockLayout.PANEL_SCREEN_NODE_VIEW;
        }

        return this.getToolbarPanelId();
    }

    public void applyToolbarDockLayout()
    {
        String panelId = this.getToolbarStoragePanelId();
        TimelineToolbarDock dock = BBSSettings.timelineToolbarDocks.getDock(panelId);

        this.toolbar.configureDockHost(this, panelId, this::applyToolbarDockLayout);
        TimelineToolbarDockLayout.apply(this, this.toolbar, dock, this.getClipPropertySidebarWidth(), this.clips);
    }

    private int getClipPropertySidebarWidth()
    {
        return this.target == null && this.panel != null ? 160 : 0;
    }

    /**
     * Convenience constructor used by legacy call sites; defaults to the
     * camera hierarchy when {@code isCameraTimeline} is unspecified.
     */
    public UIClipsPanel(UIFilmPanel panel, IFactory<Clip, ClipFactoryData> factory)
    {
        this(panel, factory, true);
    }

    private void applyDefaultToolbarSections()
    {
        this.toolbar.setSections(this.isCameraTimeline
            ? TimelineToolbarRegistry.forClipsCamera()
            : TimelineToolbarRegistry.forClipsAction());
        TimelineToolbarWiring.wireClipsToolbar(this);
    }

    private void onEmbedViewChanged(UIElement embed)
    {
        UIElement previous = this.lastEmbeddedView;

        this.lastEmbeddedView = embed;
        this.cancelToolbarInteraction();

        if (embed instanceof UIKeyframeEditor editor)
        {
            /* When a keyframe editor takes over the clip area, swap the toolbar
             * to the keyframe hierarchy (without the "Actor" section, which is
             * only relevant to the standalone replay editor). */
            this.toolbar.setSections(TimelineToolbarRegistry.forReplays(false));
            TimelineToolbarWiring.wireKeyframesToolbar(this.filmPanel, editor.view, this.toolbar);
        }
        else if (embed instanceof UIScreenNodeEditor editor)
        {
            this.toolbar.setSections(TimelineToolbarRegistry.forScreenNodeGraph());
            TimelineToolbarWiring.wireScreenNodeToolbar(this.filmPanel, editor, this.toolbar);
        }
        else
        {
            this.applyDefaultToolbarSections();

            /* Only when actually leaving an embedded keyframe editor (not every
             * embedView(null) from clip selection / setClips). Properties (editArea)
             * may still be selected from picking a keyframe and would be empty;
             * restore Camera/Action Properties for the selected clip. Skip when
             * keyframe props use the floating side panel. */
            if (embed == null
                && previous instanceof UIKeyframeEditor
                && !BBSSettings.isEmbeddedKeyframeSidePanelEnabled())
            {
                this.filmPanel.focusClipPropertiesTab(this.isCameraTimeline);
            }
        }

        this.applyToolbarDockLayout();
    }

    private void cancelToolbarInteraction()
    {
        this.clips.cancelToolbarInteraction();

        UIElement embed = this.clips.getEmbeddedView();

        if (embed instanceof UIKeyframeEditor editor)
        {
            editor.view.cancelTrackInteraction();
        }
    }

    public UIClipsPanel target(UIElement target)
    {
        this.target = target;

        return this;
    }

    public UIElement getTarget()
    {
        return this.target;
    }

    /**
     * Re-parent the open clip form onto {@link #target} after the film panel
     * retargets hosts (e.g. Action Properties hidden → Properties fallback).
     */
    public void remountClipPanel()
    {
        if (this.panel == null)
        {
            return;
        }

        Clip clip = this.panel.clip;

        this.panel.removeFromParent();
        this.panel = null;

        if (clip != null)
        {
            this.pickClip(clip);
        }
    }

    @Override
    public void removeFromParent()
    {
        super.removeFromParent();

        if (this.panel != null)
        {
            this.panel.removeFromParent();
        }
    }

    @Override
    public void setVisible(boolean visible)
    {
        if (!visible)
        {
            this.toolbar.cancelDockDrag();
        }

        super.setVisible(visible);

        if (this.panel != null)
        {
            this.panel.setVisible(visible);
        }
    }

    public void setClips(Clips clips)
    {
        this.clips.setClips(clips);
        this.clips.setVisible(clips != null);
    }

    public void editClip(Position position)
    {
        if (this.panel != null)
        {
            Map<Clip, Position> snapshots = this.filmPanel.getRunner().getContext().getSnapshots();
            Position newPosition = new Position();
            Position snapshot = snapshots.get(this.panel.clip);

            newPosition.copy(position);

            if (snapshot != null)
            {
                Clip top = this.panel.clip;

                for (Clip clip : snapshots.keySet())
                {
                    if (clip.layer.get() > top.layer.get())
                    {
                        top = clip;
                    }
                }

                Position topPosition = snapshots.get(top);

                if (topPosition != null)
                {
                    newPosition.point.x -= topPosition.point.x - snapshot.point.x;
                    newPosition.point.y -= topPosition.point.y - snapshot.point.y;
                    newPosition.point.z -= topPosition.point.z - snapshot.point.z;

                    newPosition.angle.yaw -= topPosition.angle.yaw - snapshot.angle.yaw;
                    newPosition.angle.pitch-= topPosition.angle.pitch - snapshot.angle.pitch;
                    newPosition.angle.roll -= topPosition.angle.roll - snapshot.angle.roll;
                    newPosition.angle.fov -= topPosition.angle.fov - snapshot.angle.fov;
                }
            }

            this.panel.editClip(newPosition);
        }
    }

    @Override
    public Film getFilm()
    {
        return this.filmPanel.getData();
    }

    @Override
    public Camera getCamera()
    {
        return this.filmPanel.getCamera();
    }

    @Override
    public Clip getClip()
    {
        return this.panel == null ? null : this.panel.clip;
    }

    @Override
    public void pickClip(Clip clip)
    {
        UIClip.saveScroll(this.panel);

        if (this.panel != null)
        {
            if (this.panel.clip == clip)
            {
                this.panel.fillData();

                return;
            }
            else
            {
                this.panel.removeFromParent();
            }
        }

        if (clip == null)
        {
            this.panel = null;

            this.clips.clearSelection();
            this.applyToolbarDockLayout();

            return;
        }

        try
        {
            this.clips.embedView(null);

            this.panel = UIClip.createPanel(clip, this);
            this.panel.setUndoId("clip_panel");
            this.panel.setVisible(this.isVisible());

            if (this.target == null)
            {
                this.panel.relative(this).x(1F, -160).w(160).h(1F);
                this.add(this.panel);
            }
            else
            {
                this.panel.full(this.target);
                this.target.add(this.panel);
                this.target.resize();
            }

            this.resize();
            this.panel.fillData();

            if (this.filmPanel.isFlying())
            {
                this.setCursor(Math.round(clip.tick.get()));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        this.applyToolbarDockLayout();

        this.filmPanel.pickClip(clip, this);
    }

    @Override
    public void setFlight(boolean flight)
    {
        this.filmPanel.setFlight(flight);
    }

    @Override
    public boolean isFlying()
    {
        return this.filmPanel.isFlying();
    }

    @Override
    public int getCursor()
    {
        return this.filmPanel.getCursor();
    }

    @Override
    public void setCursor(int tick)
    {
        this.filmPanel.setCursor(tick);
    }

    @Override
    public boolean isRunning()
    {
        return this.filmPanel.isRunning();
    }

    @Override
    public void togglePlayback()
    {
        this.filmPanel.togglePlayback();
    }

    @Override
    public boolean canUseKeybinds()
    {
        return this.filmPanel.canUseKeybinds();
    }

    @Override
    public void fillData()
    {
        if (this.panel != null)
        {
            this.panel.fillData();
        }
    }

    @Override
    public void embedView(UIElement element)
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            context.closeContextMenu();
        }

        UIElement current = this.clips.getEmbeddedView();
        UIFilmUndoHandler undoHandler = this.filmPanel.getUndoHandler();
        MapType uiBefore = null;
        boolean closing = current != null && element == null;
        boolean switching = current != null && element != null && current != element;
        boolean recordUndo = undoHandler != null
            && !undoHandler.isUndoing()
            && (closing || switching);

        if (recordUndo)
        {
            uiBefore = this.collectUIViewSnapshot();
        }

        this.clips.embedView(element);

        if (undoHandler != null && !undoHandler.isUndoing())
        {
            undoHandler.clearUIDataSnapshot();
        }

        if (recordUndo)
        {
            undoHandler.pushUIViewUndo(uiBefore, this.collectUIViewSnapshot());
        }
    }

    private MapType collectUIViewSnapshot()
    {
        return this.filmPanel.collectFilmUndoSnapshot();
    }

    @Override
    public void markLastUndoNoMerging()
    {
        this.filmPanel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
    }

    @Override
    public <T extends BaseValue> void editMultiple(T property, Consumer<T> consumer)
    {
        if (property == null || consumer == null)
        {
            return;
        }

        BaseValue ancestor = this.getClip();

        if (ancestor == null)
        {
            return;
        }

        DataPath path = property.getRelativePath(ancestor);

        if (path == null)
        {
            return;
        }

        for (Clip clip : this.clips.getClipsFromSelection())
        {
            BaseValue value = clip.getRecursively(path);

            if (value != null && value.getClass() == property.getClass())
            {
                consumer.accept((T) value);
            }
        }
    }

    @Override
    public void editMultiple(ValueInt property, int value)
    {
        int difference = value - property.get();
        List<Clip> clips = this.clips.getClipsFromSelection();

        for (Clip clip : clips)
        {
            ValueInt clipValue = (ValueInt) clip.get(property.getId());
            int newValue = clipValue.get() + difference;

            if (newValue < clipValue.getMin() || newValue > clipValue.getMax())
            {
                return;
            }
        }

        for (Clip clip : clips)
        {
            ValueInt clipValue = (ValueInt) clip.get(property.getId());

            clipValue.set(clipValue.get() + difference);
        }
    }

    @Override
    public void editMultiple(ValueFloat property, float value)
    {
        float difference = value - property.get();
        List<Clip> clips = this.clips.getClipsFromSelection();

        for (Clip clip : clips)
        {
            ValueFloat clipValue = (ValueFloat) clip.get(property.getId());
            float newValue = clipValue.get() + difference;

            if (newValue < clipValue.getMin() || newValue > clipValue.getMax())
            {
                return;
            }
        }

        for (Clip clip : clips)
        {
            ValueFloat clipValue = (ValueFloat) clip.get(property.getId());

            clipValue.set(clipValue.get() + difference);
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        List<Integer> selection = DataStorageUtils.intListFromData(data.getList("selection"));

        this.clips.scale.view(data.getDouble("x_min"), data.getDouble("x_max"));
        this.clips.vertical.setScroll(data.getDouble("scroll"));
        this.clips.vertical.updateTarget();

        this.clips.setSelection(selection);
        this.pickClip(selection.isEmpty() ? null : this.clips.getClips().get(selection.get(selection.size() - 1)));

        this.applyEmbeddedUndo(data);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.put("selection", DataStorageUtils.intListToData(this.clips.getSelection()));
        data.putDouble("x_min", this.clips.scale.getMinValue());
        data.putDouble("x_max", this.clips.scale.getMaxValue());
        data.putDouble("scroll", this.clips.vertical.getScroll());
        data.putBool("embedded_stacked", this.clips.isEmbeddedStackedLayout());

        UIElement embedded = this.clips.getEmbeddedView();

        if (embedded instanceof IUndoElement)
        {
            IUndoElement undoElement = (IUndoElement) embedded;

            if (!undoElement.getUndoId().isEmpty())
            {
                MapType embeddedState = new MapType();

                undoElement.collectUndoData(embeddedState);
                data.putString("embedded_id", undoElement.getUndoId());
                data.put("embedded_state", embeddedState);
            }
        }
    }

    /**
     * Symmetric embedded view restore (approach B): undo closes the overlay when the snapshot
     * had no embedded view; redo reopens it from {@code embedded_id}.
     */
    private void applyEmbeddedUndo(MapType data)
    {
        String embeddedId = data.getString("embedded_id");

        if (embeddedId.isEmpty())
        {
            this.embedView(null);

            return;
        }

        if (this.panel == null)
        {
            this.embedView(null);

            return;
        }

        UIElement view = this.panel.resolveEmbeddableView(embeddedId);

        if (view == null)
        {
            this.embedView(null);

            return;
        }

        if (view instanceof UIKeyframeEditor)
        {
            this.clips.setEmbeddedStackedLayout(data.getBool("embedded_stacked"));
        }

        this.embedView(view);

        if (view instanceof IUndoElement)
        {
            IUndoElement undoElement = (IUndoElement) view;
            MapType embeddedState = data.getMap("embedded_state");

            if (embeddedState != null)
            {
                undoElement.applyUndoData(embeddedState);
            }
        }
    }
}