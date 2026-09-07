package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.clips.converters.IClipConverter;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.clips.misc.VideoClip;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.audio.UIAudioRecorder;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.renderer.IUIClipRenderer;
import mchorse.bbs_mod.ui.film.clips.renderer.UIClipRenderers;
import mchorse.bbs_mod.ui.film.toolbar.ClipPlacementInteractionState;
import mchorse.bbs_mod.ui.film.toolbar.LoopMarkerInteractionState;
import mchorse.bbs_mod.ui.film.toolbar.TimelineClipTypeGroups;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarPointerBlock;
import mchorse.bbs_mod.ui.film.toolbar.UIClipPlacementInteraction;
import mchorse.bbs_mod.ui.film.toolbar.UILoopMarkerInteraction;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.TimelineRuler;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ColorfulContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextCategoryAction;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.ui.utils.presets.UIPresetContextMenu;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.factory.IFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.presets.PresetManager;

import org.joml.Vector3i;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class UIClips extends UIElement
{
    /* Constants */
    public static final IKey KEYS_CATEGORY = UIKeys.CAMERA_EDITOR_KEYS_CLIPS_TITLE;

    private static final int MARGIN = 10;
    private static final int LAYER_HEIGHT = 20;
    private static final int RULER_HEIGHT = 16;

    private static final Area CLIP_AREA = new Area();

    /* Main objects */
    private IUIClipsDelegate delegate;
    private Clips clips;
    private IFactory<Clip, ClipFactoryData> factory;

    /* Navigation */
    public Scale scale = new Scale(this.area, ScrollDirection.HORIZONTAL);
    public Scroll vertical = new Scroll(new Area());

    private boolean canGrab;
    private boolean grabbing;
    private boolean scrubbing;
    private boolean scrolling;
    private int lastX;
    private int lastY;
    private int initialX;
    private int initialY;
    private int grabMode;

    /* Looping */
    public int loopMin = 0;
    public int loopMax = 0;
    private int selectingLoop = -1;

    /* Selection */
    private boolean selecting;
    private List<Integer> selection = new ArrayList<>();

    /* Embedded view */
    private UIIcon embeddedClose;
    private UIIcon embeddedLayout;
    private UIElement embedded;
    private boolean embeddedStackedLayout;

    /**
     * Optional listener notified whenever {@link #embedView(UIElement)} runs.
     * Receives the new embedded element (may be {@code null}). Added for the
     * timeline toolbar system so it can swap its section hierarchy when a
     * keyframe editor is opened inside a clip timeline. Purely additive: when
     * unset, this class behaves exactly as before.
     */
    private Consumer<UIElement> embedViewListener;

    private Vector3i addPreview;
    private Vector3i placementPreview;
    private final UIClipPlacementInteraction clipPlacement = new UIClipPlacementInteraction();
    private final UILoopMarkerInteraction loopMarkerInteraction = new UILoopMarkerInteraction();
    private int layers;

    private UIClipRenderers renderers = new UIClipRenderers();

    private List<Clip> grabbedClips = Collections.emptyList();
    private List<Clip> otherClips = Collections.emptyList();
    private Set<Integer> snappingPoints = new HashSet<>();
    private List<Vector3i> grabbedData = new ArrayList<>();
    private List<Integer> grabbedOffsets = new ArrayList<>();
    /** Grab-start tick/layer/duration for {@link #otherClips} (ripple resize). */
    private List<Vector3i> otherClipData = new ArrayList<>();
    private List<Integer> otherOffsets = new ArrayList<>();

    private UICopyPasteController copyPasteController;

    /**
     * Render cursor that displays the full duration of the camera work,
     * and also current tick within the camera work.
     */
    public static void renderCursor(UIContext context, String label, Area area, int x)
    {
        /* Draw the marker */
        FontRenderer font = context.batcher.getFont();
        int width = font.getWidth(label) + 3;

        context.batcher.box(x, area.y, x + 2, area.ey(), Colors.CURSOR);

        /* Move the tick line left, so it won't overflow the timeline */
        if (x + 2 + width > area.ex())
        {
            x -= width + 1;
        }

        /* Draw the tick label */
        context.batcher.textCard(label, x + 4, area.ey() - 2 - font.getHeight(), Colors.WHITE, Colors.setA(Colors.CURSOR, 0.75F), 2);
    }

    public UIClips(IUIClipsDelegate delegate, IFactory<Clip, ClipFactoryData> factory)
    {
        super();

        this.copyPasteController = new UICopyPasteController(PresetManager.CLIPS, "_CopyClips")
            .supplier(this::copyClips)
            .consumer(this::pasteClips)
            .canCopy(() -> this.delegate.getClip() != null);

        this.delegate = delegate;
        this.factory = factory;

        this.embeddedClose = new UIIcon(Icons.CLOSE, (b) -> this.delegate.embedView(null))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (UIClips.this.embedded != null)
                {
                    this.area.render(context.batcher, Colors.setA(Colors.RED, 0.5F));
                }

                super.renderSkin(context);
            }
        };
        this.embeddedClose.relative(this).xy(4, 4);

        this.embeddedLayout = new UIIcon(Icons.EXCHANGE, (b) ->
        {
            if (this.embedded instanceof UIKeyframeEditor keyframeEditor)
            {
                this.embeddedStackedLayout = !this.embeddedStackedLayout;
                keyframeEditor.setStackedLayout(this.embeddedStackedLayout);
                b.active(this.embeddedStackedLayout);
            }
        })
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                int primary = BBSSettings.primaryColor.get();
                /* Match Open Camera Editor highlight colors, but with vertical top->bottom gradient. */
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + 2, Colors.A100 | primary);
                context.batcher.gradientVBox(this.area.x, this.area.y + 2, this.area.ex(), this.area.ey(), Colors.A75 | primary, primary);

                super.renderSkin(context);
            }
        };
        this.embeddedLayout.relative(this).xy(26, 4);

        this.context((menu) ->
        {
            if (this.clipPlacement.isActive() || this.loopMarkerInteraction.isActive())
            {
                return;
            }

            UIContext context = this.getContext();
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;
            boolean hasSelected = this.delegate.getClip() != null;

            menu.custom(new UIPresetContextMenu(this.copyPasteController, mouseX, mouseY)
                .labels(UIKeys.CAMERA_TIMELINE_CONTEXT_COPY, UIKeys.CAMERA_TIMELINE_CONTEXT_PASTE));

            if (this.fromLayerY(mouseY) < 0)
            {
                return;
            }

            menu.action(Icons.ADD, UIKeys.CAMERA_TIMELINE_CONTEXT_ADD, () -> this.showAdds(mouseX, mouseY));

            if (hasSelected)
            {
                this.addConverters(menu, context);
                menu.action(Icons.CUT, UIKeys.CAMERA_TIMELINE_CONTEXT_CUT, this::cut);
                menu.action(Icons.MOVE_TO, UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT, this::shiftToCursor);
                menu.action(Icons.SHIFT_TO, UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT_DURATION, this::shiftDurationToCursor);
            }

            menu.action(Icons.MAXIMIZE, UIKeys.CAMERA_TIMELINE_CONTEXT_MAXIMIZE, this::resetView);
            menu.action(Icons.EXCHANGE, UIKeys.CAMERA_TIMELINE_CONTEXT_REORGANIZE, () -> this.clips.sortLayers());

            if (hasSelected)
            {
                menu.action(Icons.REMOVE, UIKeys.CAMERA_TIMELINE_CONTEXT_REMOVE_CLIPS, Colors.NEGATIVE, this::removeSelected);
            }
        });

        Supplier<Boolean> canUseKeybinds = () -> this.canUseToolbarKeybinds();
        Supplier<Boolean> canUseKeybindsSelected = () -> this.delegate.getClip() != null && canUseKeybinds.get();

        this.keys().register(Keys.KEYFRAMES_MAXIMIZE, this::resetView).inside().category(KEYS_CATEGORY);
        this.keys().register(Keys.DESELECT, () -> this.pickClip(null)).inside().category(KEYS_CATEGORY).active(canUseKeybindsSelected);
        this.keys().register(Keys.ADD_ON_TOP, this::showAddsOnTop).inside().category(KEYS_CATEGORY).active(canUseKeybindsSelected);
        this.keys().register(Keys.ADD_AT_CURSOR, this::showAddsAtCursor).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.ADD_AT_TICK, this::showAddsAtTick).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.COPY, () ->
        {
            if (this.copyPasteController.copy())
            {
                UIUtils.playClick();
            }
        }).inside().category(KEYS_CATEGORY).active(canUseKeybindsSelected);
        this.keys().register(Keys.CUT, () ->
        {
            if (this.delegate.getClip() == null)
            {
                this.getContext().notifyError(UIKeys.GENERAL_CUT_EMPTY);
                return;
            }

            if (this.copyPasteController.copy())
            {
                this.removeSelected();
                UIUtils.playClick();
                this.getContext().notifyInfo(UIKeys.GENERAL_CUT);
            }
        }).inside().category(KEYS_CATEGORY).active(canUseKeybindsSelected);
        this.keys().register(Keys.PASTE, () ->
        {
            UIContext context = this.getContext();

            if (this.copyPasteController.paste(context.mouseX, context.mouseY))
            {
                UIUtils.playClick();
            }
        }).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.PRESETS, () ->
        {
            UIContext context = this.getContext();

            if (this.copyPasteController.canPreviewPresets())
            {
                this.copyPasteController.openPresets(context, context.mouseX, context.mouseY);
                UIUtils.playClick();
            }
        }).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.CLIP_CUT, this::cut).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.CLIP_SHIFT, this::shiftToCursor).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.CLIP_DURATION, this::shiftDurationToCursor).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.DELETE, this::removeSelected).inside().label(UIKeys.CAMERA_TIMELINE_CONTEXT_REMOVE_CLIPS).category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.CLIP_ENABLE, this::toggleEnabled).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.CLIP_SELECT_AFTER, this::selectAfter).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.CLIP_SELECT_BEFORE, this::selectBefore).inside().category(KEYS_CATEGORY).active(canUseKeybinds);
        this.keys().register(Keys.FADE_IN, () ->
        {
            Clip clip = this.delegate.getClip();
            int tick = Math.max(0, this.delegate.getCursor() - clip.tick.get());

            clip.envelope.fadeIn.set((float) tick);
            this.delegate.fillData();
        }).inside().category(KEYS_CATEGORY).active(canUseKeybindsSelected);
        this.keys().register(Keys.FADE_OUT, () ->
        {
            Clip clip = this.delegate.getClip();
            int tick = Math.max(0, clip.tick.get() + clip.duration.get() - this.delegate.getCursor());

            clip.envelope.fadeOut.set((float) tick);
            this.delegate.fillData();
        }).inside().category(KEYS_CATEGORY).active(canUseKeybindsSelected);
    }

    public UIClipRenderers getRenderers()
    {
        return this.renderers;
    }

    public Film getFilm()
    {
        return this.delegate.getFilm();
    }

    public IFactory<Clip, ClipFactoryData> getFactory()
    {
        return this.factory;
    }

    /* Tools */

    private void showAdds(int mouseX, int mouseY)
    {
        UIContext context = this.getContext();

        context.replaceContextMenu((add) ->
        {
            add.action(Icons.CURSOR, UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_AT_CURSOR, () -> this.showAddsAtCursor(context, mouseX, mouseY));
            add.action(Icons.SHIFT_TO, UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_AT_TICK, () -> this.showAddsAtTick(context, mouseX, mouseY));

            if (this.delegate.getClip() != null)
            {
                add.action(Icons.UPLOAD, UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_ON_TOP, this::showAddsOnTop);
            }

            if (this.factory.getKeys().contains(Link.bbs("keyframe")))
            {
                add.action(Icons.EDITOR, UIKeys.CAMERA_TIMELINE_CONTEXT_FROM_PLAYER_RECORDING, () -> this.fromReplay(mouseX, mouseY));
            }
        });
    }

    private void showAddsAtCursor()
    {
        this.enterAddClipPlacement(-1, UIKeys.TIMELINE_INTERACTION_PICK_ADD_AT_CURSOR);
    }

    private void showAddsAtCursor(UIContext context, int mouseX, int mouseY)
    {
        this.showAddClips(context, this.checkSize(this.fromGraphX(mouseX), this.fromLayerY(mouseY), BBSSettings.getDefaultDuration()));
    }

    private void showAddsAtTick()
    {
        this.enterAddClipPlacement(this.delegate.getCursor(), UIKeys.TIMELINE_INTERACTION_PICK_ADD_AT_TICK);
    }

    private void showAddsAtTick(UIContext context, int mouseX, int mouseY)
    {
        this.showAddClips(context, this.checkSize(this.delegate.getCursor(), this.fromLayerY(mouseY), BBSSettings.getDefaultDuration()));
    }

    private void enterAddClipPlacement(int lockedTick, IKey hint)
    {
        this.enterClipPlacement(hint, BBSSettings.getDefaultDuration(), lockedTick, -1,
            (tick, layer, duration) -> this.showAddClips(this.getContext(), new Vector3i(tick, layer, duration)));
    }

    private void showAddsOnTop()
    {
        Clip clip = this.delegate.getClip();
        UIContext context = this.getContext();

        if (clip == null)
        {
            return;
        }

        this.showAddClips(context, this.checkSize(clip.tick.get(), clip.layer.get() + 1, clip.duration.get()));
    }

    private Vector3i checkSize(int tick, int layer, int duration)
    {
        int start = tick;
        int remaining = duration;

        while (remaining > 0)
        {
            boolean advanced = false;

            for (Clip clip : this.clips.get())
            {
                if (clip.layer.get() != layer)
                {
                    continue;
                }

                int clipStart = clip.tick.get();
                int clipEnd = clipStart + clip.duration.get();
                int end = start + remaining;

                if (end <= clipStart || start >= clipEnd)
                {
                    continue;
                }

                if (start >= clipStart && start < clipEnd)
                {
                    int overflow = end - clipEnd;

                    if (overflow <= 0)
                    {
                        return null;
                    }

                    /* Place only the portion that extends past the obstacle's end. */
                    start = clipEnd;
                    remaining = overflow;
                    advanced = true;
                    break;
                }

                if (start < clipStart && end > clipStart)
                {
                    remaining = clipStart - start;

                    if (remaining <= 0)
                    {
                        return null;
                    }

                    return new Vector3i(start, layer, remaining);
                }
            }

            if (advanced)
            {
                continue;
            }

            return new Vector3i(start, layer, remaining);
        }

        return null;
    }

    private void showAddClips(UIContext context, Vector3i preview)
    {
        if (preview == null)
        {
            this.addPreview = null;

            this.getContext().notifyError(UIKeys.CAMERA_TIMELINE_CANT_FIT_NOTIFICATION);

            return;
        }

        context.replaceContextMenu((add) ->
        {
            add.custom(new UIClipsAddContextMenu(this, preview));
            add.onClose((m) -> this.addPreview = null);
        });

        this.addPreview = preview;
    }

    private void addClip(Link type, int tick, int layer, int duration)
    {
        Clip clip = this.factory.create(type);

        if (clip instanceof CameraClip)
        {
            ((CameraClip) clip).fromCamera(this.delegate.getCamera());
        }

        this.addClip(clip, tick, layer, duration);
    }

    /**
     * Add a new clip of given type at mouse coordinates.
     */
    private void addClip(Clip clip, int tick, int layer, int duration)
    {
        clip.layer.set(layer);
        clip.tick.set(tick);
        clip.duration.set(duration);

        this.clips.addClip(clip);
        this.pickClip(clip);
    }

    private MapType copyClips()
    {
        MapType data = new MapType();
        ListType clips = new ListType();

        data.put("clips", clips);

        for (Clip clip : this.getClipsFromSelection())
        {
            clips.add(this.factory.toData(clip));
        }

        return data;
    }

    private void pasteClips(MapType data, int mouseX, int mouseY)
    {
        this.pasteClips(data, this.fromGraphX(mouseX));
    }

    /**
     * Paste given clip data to timeline.
     */
    private void pasteClips(MapType data, int tick)
    {
        this.clearSelection();

        ListType clipsList = data.getList("clips");
        List<Clip> newClips = new ArrayList<>();
        int min = Integer.MAX_VALUE;

        try
        {
            for (BaseType type : clipsList)
            {
                MapType typeMap = type.asMap();
                Clip clip = this.factory.fromData(typeMap);

                min = Math.min(min, clip.tick.get());

                newClips.add(clip);
            }

            for (Clip clip : newClips)
            {
                clip.tick.set(tick + (clip.tick.get() - min));
                clip.layer.set(this.clips.findFreeLayer(clip));
                this.clips.addClip(clip);
                this.addSelected(clip);
            }

            this.pickLastSelectedClip();
        }
        catch (Exception e)
        {
            e.printStackTrace();

            this.getContext().notifyError(UIKeys.CAMERA_TIMELINE_INCOMPATIBLE_PASTE);
        }
    }

    /**
     * Breakdown currently selected clip into two.
     */
    private void cut()
    {
        List<Clip> selectedClips = this.isSelecting() ? this.getClipsFromSelection() : new ArrayList<>(this.clips.get());
        Clip original = this.delegate.getClip();
        int offset = this.delegate.getCursor();

        this.clips.preNotify();

        for (Clip clip : selectedClips)
        {
            if (!clip.isInside(offset))
            {
                continue;
            }

            Clip copy = clip.breakDown(offset - clip.tick.get());

            if (copy != null)
            {
                clip.duration.set(clip.duration.get() - copy.duration.get());
                copy.tick.set(copy.tick.get() + clip.duration.get());
                this.clips.addClip(copy);
                this.addSelected(copy);
            }
        }

        this.clips.postNotify();

        this.addSelected(original);
    }

    /**
     * Add available converters to context menu.
     */
    private void addConverters(ContextMenuManager menu, UIContext context)
    {
        ClipFactoryData data = this.factory.getData(this.delegate.getClip());
        Collection<Link> converters = data.converters.keySet();

        if (converters.isEmpty())
        {
            return;
        }

        menu.action(Icons.REFRESH, UIKeys.CAMERA_TIMELINE_CONTEXT_CONVERT, () ->
        {
            context.replaceContextMenu((add) ->
            {
                for (Link type : converters)
                {
                    IKey label = UIKeys.CAMERA_TIMELINE_CONTEXT_CONVERT_TO.format(UIKeys.C_CLIP.get(type));

                    add.action(Icons.REFRESH, label, this.factory.getData(type).color, () -> this.convertTo(type));
                }
            });
        });
    }

    /**
     * Convert currently editing camera clip into given type.
     */
    private void convertTo(Link type)
    {
        List<Clip> clipsFromSelection = this.getClipsFromSelection();

        if (clipsFromSelection.isEmpty())
        {
            return;
        }

        for (Clip clip : clipsFromSelection)
        {
            if (clip.getClass() != clipsFromSelection.get(0).getClass())
            {
                return;
            }
        }

        ClipFactoryData data = this.factory.getData(clipsFromSelection.get(clipsFromSelection.size() - 1));
        IClipConverter converter = data.converters.get(type);
        List<Clip> newClips = new ArrayList<>();

        for (Clip clip : clipsFromSelection)
        {
            Clip converted = converter.convert(clip);

            if (converted == null)
            {
                continue;
            }

            this.clips.remove(clip);
            this.clips.addClip(converted);
            newClips.add(converted);
        }

        if (newClips.isEmpty())
        {
            return;
        }

        this.clearSelection();

        for (Clip newClip : newClips)
        {
            this.addSelected(newClip);
        }

        this.pickLastSelectedClip();
    }

    private void fromReplay(int mouseX, int mouseY)
    {
        Film film = this.delegate.getFilm();

        this.getContext().replaceContextMenu((menu) ->
        {
            for (Replay replay : film.replays.getList())
            {
                Form form = replay.form.get();

                menu.action(Icons.EDITOR, IKey.constant(form == null ? "-" : form.getFormIdOrName()), () ->
                {
                    KeyframeClip clip = this.createKeyframeClipFromReplay(replay);
                    int size = this.computeReplayClipDuration(replay);

                    this.addClip(clip, this.fromGraphX(mouseX), this.fromLayerY(mouseY), size);
                });
            }
        });
    }

    /**
     * Move clips to cursor.
     */
    private void shiftToCursor()
    {
        List<Clip> clips = this.getClipsFromSelection();

        if (clips.isEmpty())
        {
            return;
        }

        int min = Integer.MAX_VALUE;

        for (Clip clip : clips)
        {
            min = Math.min(min, clip.tick.get());
        }

        int diff = this.delegate.getCursor() - min;

        for (Clip clip : clips)
        {
            clip.tick.set(clip.tick.get() + diff);
        }

        this.delegate.fillData();
    }

    /**
     * Move duration of currently selected clip(s) to cursor.
     */
    private void shiftDurationToCursor()
    {
        List<Clip> clips = this.getClipsFromSelection();

        if (clips.isEmpty())
        {
            return;
        }

        for (Clip clip : clips)
        {
            int offset = clip.tick.get();

            if (this.delegate.getCursor() > offset)
            {
                clip.duration.set(this.delegate.getCursor() - offset);
            }
            else if (this.delegate.getCursor() < offset + clip.duration.get())
            {
                clip.tick.set(this.delegate.getCursor());
                clip.duration.set(clip.duration.get() + offset - this.delegate.getCursor());
            }
        }

        this.delegate.fillData();
    }

    /**
     * Remove currently selected camera clip(s) from the camera work.
     */
    private void removeSelected()
    {
        List<Clip> selectedClips = this.getClipsFromSelection();

        if (selectedClips.isEmpty())
        {
            return;
        }

        for (Clip clip : selectedClips)
        {
            this.clips.remove(clip);
        }

        this.pickClip(null);
    }

    /**
     * Toggle enabled option of all selected clips
     */
    private void toggleEnabled()
    {
        List<Clip> clips = this.getClipsFromSelection();

        if (clips.isEmpty())
        {
            return;
        }

        for (Clip clip : clips)
        {
            clip.enabled.set(!clip.enabled.get());
        }

        this.delegate.fillData();
    }

    private void selectBefore()
    {
        int i = 0;

        this.clearSelection();

        for (Clip clip : this.clips.get())
        {
            if (clip.tick.get() < this.delegate.getCursor())
            {
                this.selection.add(i);
            }

            i += 1;
        }

        this.delegate.pickClip(this.selection.isEmpty() ? null : this.clips.get(this.selection.get(0)));
    }

    private void selectAfter()
    {
        int i = 0;

        this.clearSelection();

        for (Clip clip : this.clips.get())
        {
            if (clip.tick.get() + clip.duration.get() > this.delegate.getCursor())
            {
                this.selection.add(i);
            }

            i += 1;
        }

        this.delegate.pickClip(this.selection.isEmpty() ? null : this.clips.get(this.selection.get(0)));
    }

    /* Selection */

    private boolean isSelecting()
    {
        return !this.selection.isEmpty();
    }

    public List<Integer> getSelection()
    {
        return Collections.unmodifiableList(this.selection);
    }

    public List<Clip> getClipsFromSelection()
    {
        List<Clip> clips = new ArrayList<>();

        for (int index : this.selection)
        {
            Clip clip = this.clips.get(index);

            if (clip != null)
            {
                clips.add(clip);
            }
        }

        return clips;
    }

    public Clip getLastSelectedClip()
    {
        if (!this.isSelecting())
        {
            return null;
        }

        return this.clips.get(this.selection.get(this.selection.size() - 1));
    }

    public void setSelection(List<Integer> selection)
    {
        this.clearSelection();
        this.selection.addAll(selection);
    }

    public void clearSelection()
    {
        this.selection.clear();
    }

    public void pickClip(Clip clip)
    {
        this.setSelected(clip);
        this.delegate.pickClip(clip);
    }

    private void pickLastSelectedClip()
    {
        this.delegate.pickClip(this.getLastSelectedClip());
    }

    public void setSelected(Clip clip)
    {
        this.clearSelection();
        this.addSelected(clip);
    }

    public void addSelected(Clip clip)
    {
        if (clip == null || this.clips == null)
        {
            return;
        }

        int index = this.clips.getIndex(clip);

        if (index >= 0)
        {
            this.selection.remove((Integer) index);
            this.selection.add(index);
        }
    }

    public boolean hasSelected(int clip)
    {
        return this.selection.contains(clip);
    }

    /* Getters and setters */

    public Clips getClips()
    {
        return this.clips;
    }

    public void setClips(Clips clips)
    {
        this.clips = clips;
        this.addPreview = null;

        this.vertical.scrollToEnd();
        this.vertical.updateTarget();
        this.clearSelection();
        this.embedView(null);

        this.resetView();
    }

    private void resetView()
    {
        this.scale.anchor(0F);

        if (clips != null)
        {
            int duration = clips.calculateDuration();

            /* Guard against absurd spans from corrupted tick/duration data. */
            if (duration > Clip.MAX_DURATION_TICKS * 10)
            {
                duration = Clip.MAX_DURATION_TICKS * 10;
            }

            if (duration > 0)
            {
                this.scale.view(0, duration);
            }
            else
            {
                this.scale.set(0, 1);
            }
        }
    }

    public int fromLayerY(int mouseY)
    {
        int bottom = this.area.ey() - MARGIN;

        if (mouseY < this.area.y + RULER_HEIGHT || mouseY > bottom)
        {
            return -1;
        }

        mouseY -= this.getScroll();

        return (bottom - mouseY) / LAYER_HEIGHT;
    }

    public int toLayerY(int layer)
    {
        int h = LAYER_HEIGHT;

        return this.area.ey() - MARGIN - (layer + 1) * h + this.getScroll();
    }

    private int getScroll()
    {
        if (this.vertical.scrollSize < this.vertical.area.h)
        {
            return 0;
        }

        return this.vertical.scrollSize - this.vertical.area.h - (int) this.vertical.getScroll();
    }

    public void updateLayers()
    {
        this.layers = 20;

        for (Clip clip : this.clips.get())
        {
            this.layers = Math.max(this.layers, clip.layer.get() + 1);
        }
    }

    public int fromGraphX(int mouseX)
    {
        return (int) Math.round(this.scale.from(mouseX));
    }

    public int toGraphX(int value)
    {
        return (int) (this.scale.to(value));
    }

    public void setLoopMin()
    {
        this.setLoopMinAt(this.delegate.getCursor());
    }

    public void setLoopMax()
    {
        this.setLoopMaxAt(this.delegate.getCursor());
    }

    public void setLoopMinAt(int tick)
    {
        this.loopMin = tick;
        this.verifyLoopMinMax();
    }

    public void setLoopMaxAt(int tick)
    {
        this.loopMax = tick;
        this.verifyLoopMinMax();
    }

    public void toolbarEnterLoopMin()
    {
        if (!this.canUseToolbarKeybinds())
        {
            return;
        }

        this.cancelToolbarInteraction();
        this.loopMarkerInteraction.enter(new LoopMarkerInteractionState(true,
            UIKeys.TIMELINE_INTERACTION_LOOP_SET_MIN));
    }

    public void toolbarEnterLoopMax()
    {
        if (!this.canUseToolbarKeybinds())
        {
            return;
        }

        this.cancelToolbarInteraction();
        this.loopMarkerInteraction.enter(new LoopMarkerInteractionState(false,
            UIKeys.TIMELINE_INTERACTION_LOOP_SET_MAX));
    }

    private void verifyLoopMinMax()
    {
        int min = this.loopMin;
        int max = this.loopMax;

        this.loopMin = Math.min(min, max);
        this.loopMax = Math.max(min, max);
    }

    /* Embedded view */

    public boolean hasEmbeddedView()
    {
        return this.embedded != null;
    }

    /**
     * Whether clip-timeline keybinds (and toolbar clip actions) are allowed.
     * Disabled while a keyframe editor is embedded on top of the timeline.
     */
    public boolean canUseToolbarKeybinds()
    {
        return this.delegate.canUseKeybinds() && !this.hasEmbeddedView();
    }

    public Clip getDelegateClip()
    {
        return this.delegate.getClip();
    }

    /**
     * Toolbar action: clear the current clip selection.
     */
    public void toolbarDeselectAll()
    {
        this.pickClip(null);
    }

    /**
     * Toolbar action: reset horizontal view scale to fit all clips in the timeline.
     */
    public void toolbarResetView()
    {
        this.resetView();
    }

    /**
     * Toolbar action: toggle the enabled flag on all selected clips.
     */
    public void toolbarToggleEnabled()
    {
        this.toggleEnabled();
    }

    public void toolbarCopy()
    {
        if (this.copyPasteController.copy())
        {
            UIUtils.playClick();
        }
    }

    public void toolbarCut()
    {
        if (this.delegate.getClip() == null)
        {
            this.getContext().notifyError(UIKeys.GENERAL_CUT_EMPTY);

            return;
        }

        if (this.copyPasteController.copy())
        {
            this.removeSelected();
            UIUtils.playClick();
            this.getContext().notifyInfo(UIKeys.GENERAL_CUT);
        }
    }

    public void toolbarRemoveSelected()
    {
        this.removeSelected();
    }

    public void toolbarCutClipAtCursor()
    {
        this.cut();
    }

    public void toolbarShiftToCursor()
    {
        this.shiftToCursor();
    }

    public void toolbarShiftDurationToCursor()
    {
        this.shiftDurationToCursor();
    }

    public void toolbarFadeIn()
    {
        Clip clip = this.delegate.getClip();
        int tick = Math.max(0, this.delegate.getCursor() - clip.tick.get());

        clip.envelope.fadeIn.set((float) tick);
        this.delegate.fillData();
    }

    public void toolbarFadeOut()
    {
        Clip clip = this.delegate.getClip();
        int tick = Math.max(0, clip.tick.get() + clip.duration.get() - this.delegate.getCursor());

        clip.envelope.fadeOut.set((float) tick);
        this.delegate.fillData();
    }

    public void toolbarSelectBefore()
    {
        this.selectBefore();
    }

    public void toolbarSelectAfter()
    {
        this.selectAfter();
    }

    public void toolbarPaste()
    {
        UIContext context = this.getContext();
        int pasteX = this.toGraphX(this.delegate.getCursor());
        int pasteY = this.area.my() + this.area.h / 2;

        if (this.copyPasteController.paste(pasteX, pasteY))
        {
            UIUtils.playClick();
        }
    }

    public void toolbarOpenPresets()
    {
        UIContext context = this.getContext();
        int anchorX = this.toGraphX(this.delegate.getCursor());
        int anchorY = this.area.my() + this.area.h / 2;

        if (this.copyPasteController.canPreviewPresets())
        {
            this.copyPasteController.openPresets(context, anchorX, anchorY);
            UIUtils.playClick();
        }
    }

    public void toolbarReorganize()
    {
        this.clips.sortLayers();
    }

    public void toolbarConvertTo(Link type)
    {
        this.convertTo(type);
    }

    public void toolbarShowAddsAtCursor()
    {
        this.showAddsAtCursor();
    }

    public void toolbarShowAddsAtTick()
    {
        this.showAddsAtTick();
    }

    public void toolbarShowAddsOnTop()
    {
        this.showAddsOnTop();
    }

    public void toolbarAddClipType(Link type)
    {
        this.enterClipPlacement(UIKeys.TIMELINE_INTERACTION_PLACE_CLIP, BBSSettings.getDefaultDuration(), -1, -1,
            (tick, layer, duration) -> this.addClip(type, tick, layer, duration));
    }

    public void toolbarImportReplay(Replay replay)
    {
        int duration = this.computeReplayClipDuration(replay);

        this.enterClipPlacement(UIKeys.TIMELINE_INTERACTION_PLACE_REPLAY, duration, -1, -1,
            (tick, layer, ignored) ->
            {
                KeyframeClip clip = this.createKeyframeClipFromReplay(replay);

                this.addClip(clip, tick, layer, duration);
            });
    }

    public void toolbarRecordMicrophone()
    {
        UIFilmPanel filmPanel = this.getFilmPanel();

        if (filmPanel == null)
        {
            return;
        }

        UIAudioRecorder.promptThenPlace(filmPanel, this);
    }

    public void enterClipPlacement(IKey hint, int duration, int lockedTick, int lockedLayer,
        ClipPlacementInteractionState.IClipPlacementConfirm onConfirm)
    {
        this.loopMarkerInteraction.cancel();
        this.clipPlacement.enter(new ClipPlacementInteractionState(hint, duration, lockedTick, lockedLayer, onConfirm));
    }

    public void cancelClipPlacement()
    {
        this.clipPlacement.cancel(this);
    }

    public void cancelToolbarInteraction()
    {
        this.clipPlacement.cancel(this);
        this.loopMarkerInteraction.cancel();
    }

    public boolean isClipPlacementActive()
    {
        return this.clipPlacement.isActive();
    }

    public boolean isLoopMarkerInteractionActive()
    {
        return this.loopMarkerInteraction.isActive();
    }

    @Override
    public UIContextMenu createContextMenu(UIContext context)
    {
        if (this.clipPlacement.isActive() || this.loopMarkerInteraction.isActive())
        {
            return null;
        }

        return super.createContextMenu(context);
    }

    public Area getVerticalArea()
    {
        return this.vertical.area;
    }

    public Vector3i computePlacementSize(int tick, int layer, int duration)
    {
        return this.checkSize(tick, layer, duration);
    }

    public void setPlacementPreview(Vector3i preview)
    {
        this.placementPreview = preview;
    }

    public Vector3i getPlacementPreview()
    {
        return this.placementPreview;
    }

    public void clearPlacementPreview()
    {
        this.placementPreview = null;
    }

    private UIFilmPanel getFilmPanel()
    {
        if (this.delegate instanceof UIClipsPanel panel)
        {
            return panel.filmPanel;
        }

        return null;
    }

    private KeyframeClip createKeyframeClipFromReplay(Replay replay)
    {
        KeyframeClip clip = new KeyframeClip();

        clip.fov.insert(0, 50D);

        clip.x.copyKeyframes(replay.keyframes.x);
        clip.y.copyKeyframes(replay.keyframes.y);
        clip.z.copyKeyframes(replay.keyframes.z);

        clip.yaw.copyKeyframes(replay.keyframes.yaw);
        clip.pitch.copyKeyframes(replay.keyframes.pitch);

        for (Keyframe<Double> keyframe : clip.yaw.getKeyframes())
        {
            keyframe.setValue(180D + keyframe.getValue());
        }

        return clip;
    }

    private int computeReplayClipDuration(Replay replay)
    {
        KeyframeClip clip = this.createKeyframeClipFromReplay(replay);

        return (int) Math.max(
            clip.x.getLength(),
            Math.max(
                clip.y.getLength(),
                Math.max(
                    clip.z.getLength(),
                    Math.max(clip.yaw.getLength(), clip.pitch.getLength())
                )
            )
        );
    }

    public UIElement getEmbeddedView()
    {
        return this.embedded;
    }

    public boolean isEmbeddedStackedLayout()
    {
        return this.embeddedStackedLayout;
    }

    public void setEmbeddedStackedLayout(boolean stacked)
    {
        this.embeddedStackedLayout = stacked;

        if (this.embedded instanceof UIKeyframeEditor keyframeEditor)
        {
            keyframeEditor.setStackedLayout(this.embeddedStackedLayout);
            this.embeddedLayout.active(this.embeddedStackedLayout);
        }
    }

    public void setEmbedViewListener(Consumer<UIElement> listener)
    {
        this.embedViewListener = listener;
    }

    public void embedView(UIElement element)
    {
        /* Selecting clips / setClips always call embedView(null). Re-notifying when
         * already closed made UIClipsPanel re-focus Camera Properties and rebuild the
         * whole film layout — that letterboxed the workspace (black strip on the right). */
        if (this.embedded == element)
        {
            return;
        }

        this.embeddedClose.removeFromParent();
        this.embeddedLayout.removeFromParent();

        if (this.embedded != null)
        {
            if (this.embedded instanceof UIKeyframeEditor keyframeEditor)
            {
                keyframeEditor.pickListener(null);
                keyframeEditor.target(null);
            }

            this.embedded.removeFromParent();
        }

        this.embedded = element;

        if (this.embedded != null)
        {
            this.embedded.resetFlex().full(this);

            this.prepend(this.embedded);
            this.add(this.embeddedClose);

            if (this.embedded instanceof UIKeyframeEditor keyframeEditor)
            {
                this.configureEmbeddedKeyframeEditor(keyframeEditor);
            }

            this.embedded.resize();
            this.embeddedClose.resize();
        }
        else
        {
            this.syncEmbeddedClipPanelVisibility(true);
        }

        if (this.embedViewListener != null)
        {
            this.embedViewListener.accept(this.embedded);
        }
    }

    /**
     * Re-apply side-panel vs properties-tab mode for an open embedded keyframe editor
     * (settings toggle or workspace layout change).
     */
    public void applyEmbeddedKeyframePropertiesMode()
    {
        if (this.embedded instanceof UIKeyframeEditor keyframeEditor)
        {
            this.embeddedLayout.removeFromParent();
            /* Retarget only — do not steal the active tab (avoids nested
             * setupEditorFlex while switching Properties tabs). */
            this.configureEmbeddedKeyframeEditor(keyframeEditor);
            this.embedded.resize();
            this.embeddedClose.resize();
        }
    }

    private void configureEmbeddedKeyframeEditor(UIKeyframeEditor keyframeEditor)
    {
        UIFilmPanel filmPanel = this.getFilmPanel();
        boolean sidePanel = BBSSettings.isEmbeddedKeyframeSidePanelEnabled() || filmPanel == null;

        if (sidePanel)
        {
            keyframeEditor.pickListener(null);
            keyframeEditor.target(null);
            keyframeEditor.overlayPanel(true);
            keyframeEditor.setStackedLayout(this.embeddedStackedLayout);
            this.embeddedLayout.active(this.embeddedStackedLayout);
            this.add(this.embeddedLayout);
            this.embeddedLayout.resize();
            this.syncEmbeddedClipPanelVisibility(true);
        }
        else
        {
            /* Same host as replay keyframes: the general Properties tab (editArea),
             * not Camera/Action properties — those keep the clip form. */
            UIElement propertiesTarget = filmPanel.shouldRedirectProperties()
                ? filmPanel.unifiedEditArea
                : filmPanel.editArea;

            keyframeEditor.overlayPanel(false);
            keyframeEditor.target(propertiesTarget);
            /* Pick a keyframe → show Properties tab. Opening the embed alone does not
             * focus that tab (keeps Camera/Action Properties if already selected). */
            keyframeEditor.pickListener(filmPanel::focusEmbeddedKeyframePropertiesTab);
            this.syncEmbeddedClipPanelVisibility(true);
        }
    }

    private void syncEmbeddedClipPanelVisibility(boolean visible)
    {
        if (!(this.delegate instanceof UIClipsPanel panel))
        {
            return;
        }

        UIClip clipPanel = panel.getClipPanel();

        if (clipPanel != null)
        {
            clipPanel.setVisible(visible && panel.isVisible());
        }
    }

    /* Handling user input */

    @Override
    protected void afterResizeApplied()
    {
        super.afterResizeApplied();

        this.vertical.area.copy(this.area);
        this.vertical.area.y += RULER_HEIGHT;
        this.vertical.area.h -= RULER_HEIGHT + MARGIN;
    }

    public void updateScrollSize()
    {
        this.updateLayers();

        this.vertical.scrollSize = this.clips == null ? 0 : this.layers * LAYER_HEIGHT;
        this.vertical.clamp();
    }

    private void setMouse(int x, int y)
    {
        this.lastX = this.initialX = x;
        this.lastY = this.initialY = y;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.loopMarkerInteraction.isActive())
        {
            if (this.vertical.mouseClicked(context))
            {
                return true;
            }

            if (this.loopMarkerInteraction.handleMouseClicked(this, context))
            {
                return true;
            }
        }
        else if (this.loopMarkerInteraction.handleMouseClicked(this, context))
        {
            return true;
        }

        if (this.clipPlacement.isActive())
        {
            if (this.vertical.mouseClicked(context))
            {
                return true;
            }

            if (this.clipPlacement.handleMouseClicked(this, context))
            {
                return true;
            }
        }
        else if (this.clipPlacement.handleMouseClicked(this, context))
        {
            return true;
        }

        if (!this.clipPlacement.isActive() && !this.loopMarkerInteraction.isActive() && this.vertical.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && !this.hasEmbeddedView() && !TimelineToolbarPointerBlock.blocksPointer(context))
        {
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;
            boolean ctrl = Window.isCtrlPressed();
            boolean shift = Window.isShiftPressed();
            boolean alt = Window.isAltPressed();

            if (context.mouseButton == 0 && this.handleLeftClick(context, mouseX, mouseY, ctrl, shift, alt))
            {
                return true;
            }
            else if (context.mouseButton == 1 && this.handleRightClick(mouseX, mouseY, ctrl, shift, alt))
            {
                return true;
            }
            else if (context.mouseButton == 2 && this.handleMiddleClick(mouseX, mouseY, ctrl, shift, alt))
            {
                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    private boolean handleLeftClick(UIContext context, int mouseX, int mouseY, boolean ctrl, boolean shift, boolean alt)
    {
        if (!this.hasEmbeddedView())
        {
            int tick = (int) Math.floor(this.scale.from(mouseX));
            int layerIndex = this.fromLayerY(mouseY);
            Clip original = this.delegate.getClip();
            Clip clip = this.clips.getClipAt(tick, layerIndex);

            if (clip != null)
            {
                if (clip != original)
                {
                    if (shift || this.selection.contains(this.clips.getIndex(clip)))
                    {
                        this.addSelected(clip);

                        Clip last = this.getLastSelectedClip();

                        if (last != original)
                        {
                            this.delegate.pickClip(last);
                        }
                    }
                    else
                    {
                        this.delegate.pickClip(clip);
                        this.setSelected(clip);
                    }
                }

                this.grabMode = this.getClipHandle(clip, context, LAYER_HEIGHT);
                this.canGrab = false;
                this.grabbing = true;
                this.grabbedClips = this.getClipsFromSelection();
                this.otherClips = new ArrayList<>(this.clips.get());
                this.otherClips.removeIf(this.grabbedClips::contains);
                this.otherClipData.clear();
                this.otherOffsets.clear();
                this.grabbedData.clear();
                this.grabbedOffsets.clear();
                this.snappingPoints.clear();
                this.snappingPoints.add(this.delegate.getCursor());

                if (BBSSettings.editorSnapToMarkers.get())
                {
                    int mult = TimelineRuler.steps(this.scale).minor;
                    int start = (int) this.scale.getMinValue();
                    int end = (int) this.scale.getMaxValue();
                    int max = Integer.MAX_VALUE;

                    start -= start % mult;
                    end -= end % mult;

                    start = MathUtils.clamp(start, 0, max);
                    end = MathUtils.clamp(end, mult, max);
                    end = this.capRulerEnd(start, end, mult);

                    for (int j = start; j <= end; j += mult)
                    {
                        this.snappingPoints.add(j);
                    }
                }
                else
                {
                    this.snappingPoints.add(0);
                }

                for (Clip otherClip : this.otherClips)
                {
                    this.otherClipData.add(new Vector3i(otherClip.tick.get(), otherClip.layer.get(), otherClip.duration.get()));
                    this.otherOffsets.add(this.getClipOffset(otherClip));
                    this.snappingPoints.add(otherClip.tick.get());
                    this.snappingPoints.add(otherClip.tick.get() + otherClip.duration.get());
                }

                this.setMouse(mouseX, mouseY);

                for (Clip selectedClip : this.getClipsFromSelection())
                {
                    this.grabbedData.add(new Vector3i(selectedClip.tick.get(), selectedClip.layer.get(), selectedClip.duration.get()));
                    this.grabbedOffsets.add(this.getClipOffset(selectedClip));
                }

                return true;
            }
        }

        if (shift && !this.hasEmbeddedView())
        {
            this.selecting = true;

            this.setMouse(mouseX, mouseY);

            return true;
        }
        else if (alt)
        {
            this.selectingLoop = 0;
            this.loopMin = this.fromGraphX(mouseX);
            this.verifyLoopMinMax();
        }
        else
        {
            this.scrubbing = true;
            this.delegate.setCursor(this.fromGraphX(mouseX));

            return true;
        }

        return false;
    }

    private boolean handleRightClick(int mouseX, int mouseY, boolean ctrl, boolean shift, boolean alt)
    {
        if (alt)
        {
            boolean same = this.loopMin == this.loopMax;

            this.selectingLoop = 1;
            this.loopMax = this.fromGraphX(mouseX);

            if (same)
            {
                this.loopMin = this.loopMax;
            }
            else
            {
                this.verifyLoopMinMax();
            }

            return true;
        }

        return false;
    }

    private boolean handleMiddleClick(int mouseX, int mouseY, boolean ctrl, boolean shift, boolean alt)
    {
        if (alt)
        {
            this.loopMin = this.loopMax = 0;
        }
        else
        {
            this.scrolling = true;
            this.setMouse(mouseX, mouseY);

            return true;
        }

        return false;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && !this.scrolling && !this.hasEmbeddedView()
            && !TimelineToolbarPointerBlock.blocksPointer(context))
        {
            if (context.mouseWheelHorizontal != 0D)
            {
                this.scale.setShift(this.scale.getShift() - (25F * BBSSettings.scrollingSensitivityHorizontal.get() * context.mouseWheelHorizontal) / this.scale.getZoom());
            }
            else if (Window.isShiftPressed())
            {
                this.vertical.mouseScroll(context);
            }
            else if (context.mouseWheel != 0D)
            {
                this.scale.zoomAnchor(Scale.getAnchorX(context, this.area), Math.copySign(this.scale.getZoomFactor(), context.mouseWheel));
            }

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.hasEmbeddedView())
        {
            return super.subMouseReleased(context);
        }

        this.vertical.mouseReleased(context);
        this.resetStates();

        return super.subMouseReleased(context);
    }

    private void resetStates()
    {
        this.submitGrabbedUndo();

        if (this.selecting)
        {
            this.pickLastSelectedClip();
        }

        this.grabMode = 0;
        this.canGrab = false;
        this.grabbing = false;
        this.selecting = false;
        this.scrubbing = false;
        this.scrolling = false;
        this.selectingLoop = -1;

        this.grabbedClips = Collections.emptyList();
        this.otherClips = Collections.emptyList();
        this.snappingPoints.clear();
        this.grabbedData.clear();
        this.grabbedOffsets.clear();
        this.otherClipData.clear();
        this.otherOffsets.clear();

        this.vertical.dragging = false;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.loopMarkerInteraction.handleKeyPressed(context))
        {
            return true;
        }

        if (this.clipPlacement.handleKeyPressed(this, context))
        {
            return true;
        }

        if (this.embedded != null && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.delegate.embedView(null);
            UIUtils.playClick();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.updateScrollSize();

        if (this.clips != null && !this.hasEmbeddedView())
        {
            if (!TimelineToolbarPointerBlock.blocksPointer(context))
            {
                this.vertical.drag(context);
                this.handleInput(context.mouseX, context.mouseY);
                this.handleScrolling(context.mouseX, context.mouseY);
            }

            this.renderCameraWork(context);
        }

        this.loopMarkerInteraction.updatePreview(this, context);
        this.clipPlacement.updatePreview(this, context);
        this.loopMarkerInteraction.renderTickPulse(this, context);
        this.loopMarkerInteraction.renderHint(context, this.area, this);
        this.clipPlacement.renderHint(context, this.area, this);

        super.render(context);
    }

    private void handleInput(int mouseX, int mouseY)
    {
        if ((this.scrubbing || this.selecting || this.grabbing || this.selectingLoop == 0) && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1))
        {
            this.resetStates();

            return;
        }

        if (this.selectingLoop == 1 && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_2))
        {
            this.resetStates();

            return;
        }

        if (this.scrubbing)
        {
            this.delegate.setCursor(this.fromGraphX(mouseX));
        }
        else if (this.selectingLoop == 0)
        {
            this.loopMin = MathUtils.clamp(this.fromGraphX(mouseX), 0, this.loopMax);
        }
        else if (this.selectingLoop == 1)
        {
            this.loopMax = MathUtils.clamp(this.fromGraphX(mouseX), this.loopMin, Integer.MAX_VALUE);
        }
        else if (this.selecting)
        {
            Area selection = new Area();

            selection.setPoints(this.lastX, this.lastY, mouseX, mouseY);
            this.captureSelection(selection);
        }
        else if (this.grabbing)
        {
            if (this.canGrab)
            {
                this.dragClips(mouseX, mouseY);

                this.lastX = mouseX;
                this.lastY = mouseY;
            }
            else if (Math.abs(mouseX - this.initialX) > 1 || Math.abs(mouseY - this.initialY) > 1 || Window.isAltPressed())
            {
                this.canGrab = true;
            }
        }
    }

    private void dragClips(int mouseX, int mouseY)
    {
        if (this.grabbedClips.isEmpty() || this.grabbedData.isEmpty())
        {
            return;
        }

        List<Clip> others = Window.isAltPressed() ? Collections.emptyList() : this.otherClips;
        int dx = this.fromGraphX(mouseX) - this.fromGraphX(this.initialX);
        int dy = this.fromLayerY(mouseY) - this.fromLayerY(this.initialY);

        if (this.grabMode == 0) this.moveClips(others, dx, dy);
        else if (this.grabMode == 1) this.dragLeftEdge(others, dx, dy);
        else if (this.grabMode == 2) this.dragRightEdge(others, dx, dy);

        this.delegate.fillData();
    }

    private void moveClips(List<Clip> others, int dx, int dy)
    {
        Anchor anchor = this.findClosestAnchor(this.grabbedData);

        if (anchor != null)
        {
            Vector3i ref = this.grabbedData.get(anchor.clipIndex());
            int edgeTick = ref.x() + (anchor.isLeft() ? 0 : ref.z());
            int snapped = this.snap(edgeTick + dx);

            dx += snapped - (edgeTick + dx);
        }

        int[] adjusted = this.resolveCollisions(others, this.grabbedData, dx, dy);

        for (int i = 0; i < this.grabbedClips.size(); i++)
        {
            Vector3i v = this.grabbedData.get(i);

            this.setClipData(this.grabbedClips.get(i), v.x() + adjusted[0], v.y() + adjusted[1], v.z());
        }
    }

    private void dragLeftEdge(List<Clip> others, int dx, int dy)
    {
        Vector3i data = grabbedData.get(grabbedData.size() - 1);
        Clip clip = grabbedClips.get(grabbedClips.size() - 1);
        int tick = data.x();
        int duration = data.z();
        int newTick = tick + dx;
        int newDuration = duration - dx;
        int snapped = this.snap(newTick);
        int minLeft = others.stream()
            .filter((o) -> this.sameLayer(o, clip) && o.tick.get() + o.duration.get() <= tick)
            .mapToInt((o) -> o.tick.get() + o.duration.get())
            .max()
            .orElse(0);

        newDuration += newTick - snapped;
        newTick = Math.max(minLeft, snapped);

        if (newDuration < 1)
        {
            newDuration = 1;
            newTick = tick + duration - 1;
        }

        this.setClipData(clip, newTick, data.y(), newDuration);
    }

    private void dragRightEdge(List<Clip> others, int dx, int dy)
    {
        Vector3i data = this.grabbedData.get(this.grabbedData.size() - 1);
        Clip clip = this.grabbedClips.get(this.grabbedClips.size() - 1);
        int tick = data.x();
        int duration = data.z();
        int originalEnd = tick + duration;
        int snapped = this.snap(tick + duration + dx);
        int newDuration = Math.max(1, snapped - tick);
        int desiredEnd = tick + newDuration;

        /* Push same-layer clips ahead instead of clamping/overlapping.
         * Alt keeps others empty so free overlap still works. */
        if (!others.isEmpty() && this.otherClipData.size() == this.otherClips.size())
        {
            int nextStart = Integer.MAX_VALUE;

            for (int i = 0; i < this.otherClips.size(); i++)
            {
                Clip other = this.otherClips.get(i);
                Vector3i od = this.otherClipData.get(i);

                if (this.sameLayer(other, clip) && od.x() >= originalEnd)
                {
                    nextStart = Math.min(nextStart, od.x());
                }
            }

            int overflow = desiredEnd > nextStart ? desiredEnd - nextStart : 0;

            for (int i = 0; i < this.otherClips.size(); i++)
            {
                Clip other = this.otherClips.get(i);
                Vector3i od = this.otherClipData.get(i);

                if (this.sameLayer(other, clip) && od.x() >= originalEnd)
                {
                    this.setClipData(other, od.x() + overflow, od.y(), od.z());
                }
            }
        }

        this.setClipData(clip, tick, data.y(), newDuration);
    }

    private Anchor findClosestAnchor(List<Vector3i> data)
    {
        return IntStream.range(0, data.size())
            .boxed()
            .flatMap((i) ->
            {
                Vector3i v = data.get(i);
                int left = this.toGraphX(v.x());
                int right = this.toGraphX(v.x() + v.z());

                return Stream.of(new Anchor(i, true, left), new Anchor(i, false, right));
            })
            .min(Comparator.comparingInt((a) -> Math.abs(a.graphX() - this.initialX)))
            .orElse(null);
    }

    private int[] resolveCollisions(List<Clip> others, List<Vector3i> data, int dx, int dy)
    {
        int dir = 0;

        while (this.collisionExists(others, data, dx, dy))
        {
            if (dir % 2 == 0 && dx != 0) dx -= Integer.signum(dx);
            if (dir % 2 == 1 && dy != 0) dy -= Integer.signum(dy);

            dir += 1;
        }

        return new int[]{dx, dy};
    }

    private boolean collisionExists(List<Clip> others, List<Vector3i> data, int dx, int dy)
    {
        for (int i = 0; i < data.size(); i++)
        {
            Vector3i v = data.get(i);

            int newTick = v.x() + dx;
            int newLayer = v.y() + dy;
            int newDuration = newTick + v.z();

            if (newTick < 0 || newLayer < 0)
            {
                return true;
            }

            for (Clip other : others)
            {
                if (other.layer.get() == newLayer && MathUtils.isInside(newTick, newDuration, other.tick.get(), other.tick.get() + other.duration.get()))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean sameLayer(Clip a, Clip b)
    {
        return a.layer.get().equals(b.layer.get());
    }

    private int getClipOffset(Clip clip)
    {
        if (clip instanceof AudioClip audio)
        {
            return audio.offset.get();
        }
        else if (clip instanceof VideoClip video)
        {
            return video.offset.get();
        }

        return 0;
    }

    private void setClipOffset(Clip clip, int offset, boolean direct)
    {
        if (clip instanceof AudioClip audio)
        {
            if (direct)
            {
                audio.offset.setDirect(offset);
            }
            else
            {
                audio.offset.set(offset);
            }
        }
        else if (clip instanceof VideoClip video)
        {
            if (direct)
            {
                video.offset.setDirect(offset);
            }
            else
            {
                video.offset.set(offset);
            }
        }
    }

    private ValueInt getClipOffsetValue(Clip clip)
    {
        if (clip instanceof AudioClip audio)
        {
            return audio.offset;
        }
        else if (clip instanceof VideoClip video)
        {
            return video.offset;
        }

        return null;
    }

    private void setClipData(Clip clip, int newTick, int newLayer, int newDuration)
    {
        if (clip.tick.get() != newTick && clip.duration.get() != newDuration)
        {
            clip.shiftLeft(newTick, this.canGrab);
        }

        if (this.canGrab)
        {
            clip.tick.setDirect(newTick);
            clip.duration.setDirect(newDuration);
            clip.layer.setDirect(newLayer);
        }
        else
        {
            clip.tick.set(newTick);
            clip.duration.set(newDuration);
            clip.layer.set(newLayer);
        }
    }

    private void submitGrabbedUndo()
    {
        if (!this.canGrab || this.grabbedClips.isEmpty())
        {
            return;
        }

        boolean hasChanges = false;
        List<Clip> changedClips = new ArrayList<>();
        List<Vector3i> initialDataList = new ArrayList<>();
        List<Integer> initialOffsetsList = new ArrayList<>();
        List<Vector3i> finalDataList = new ArrayList<>();
        List<Integer> finalOffsetsList = new ArrayList<>();

        /* Check grabbed clips */
        for (int i = 0; i < this.grabbedClips.size() && i < this.grabbedData.size(); i++)
        {
            Clip clip = this.grabbedClips.get(i);
            Vector3i initial = this.grabbedData.get(i);
            int initialOffset = i < this.grabbedOffsets.size() ? this.grabbedOffsets.get(i) : this.getClipOffset(clip);
            int currentOffset = this.getClipOffset(clip);

            if (initial.x() != clip.tick.get() || initial.y() != clip.layer.get()
                || initial.z() != clip.duration.get() || initialOffset != currentOffset)
            {
                hasChanges = true;
                changedClips.add(clip);
                initialDataList.add(initial);
                initialOffsetsList.add(initialOffset);
                finalDataList.add(new Vector3i(clip.tick.get(), clip.layer.get(), clip.duration.get()));
                finalOffsetsList.add(currentOffset);
            }
        }

        /* Check other clips (e.g. ripple resize) */
        for (int i = 0; i < this.otherClips.size() && i < this.otherClipData.size(); i++)
        {
            Clip clip = this.otherClips.get(i);
            Vector3i initial = this.otherClipData.get(i);
            int initialOffset = i < this.otherOffsets.size() ? this.otherOffsets.get(i) : this.getClipOffset(clip);
            int currentOffset = this.getClipOffset(clip);

            if (initial.x() != clip.tick.get() || initial.y() != clip.layer.get()
                || initial.z() != clip.duration.get() || initialOffset != currentOffset)
            {
                hasChanges = true;
                changedClips.add(clip);
                initialDataList.add(initial);
                initialOffsetsList.add(initialOffset);
                finalDataList.add(new Vector3i(clip.tick.get(), clip.layer.get(), clip.duration.get()));
                finalOffsetsList.add(currentOffset);
            }
        }

        if (!hasChanges)
        {
            return;
        }

        /* 1. Temporarily revert all changed clips to initial values directly */
        for (int i = 0; i < changedClips.size(); i++)
        {
            Clip clip = changedClips.get(i);
            Vector3i initial = initialDataList.get(i);
            int initialOffset = initialOffsetsList.get(i);

            clip.tick.setDirect(initial.x());
            clip.layer.setDirect(initial.y());
            clip.duration.setDirect(initial.z());
            this.setClipOffset(clip, initialOffset, true);
        }

        /* 2. Fire preNotify(FLAG_UNMERGEABLE) on all properties that actually changed */
        for (int i = 0; i < changedClips.size(); i++)
        {
            Clip clip = changedClips.get(i);
            Vector3i initial = initialDataList.get(i);
            Vector3i finalData = finalDataList.get(i);
            int initialOffset = initialOffsetsList.get(i);
            int finalOffset = finalOffsetsList.get(i);

            if (initial.x() != finalData.x())
            {
                clip.tick.preNotify(IValueListener.FLAG_UNMERGEABLE);
            }
            if (initial.y() != finalData.y())
            {
                clip.layer.preNotify(IValueListener.FLAG_UNMERGEABLE);
            }
            if (initial.z() != finalData.z())
            {
                clip.duration.preNotify(IValueListener.FLAG_UNMERGEABLE);
            }
            if (initialOffset != finalOffset)
            {
                ValueInt offsetValue = this.getClipOffsetValue(clip);

                if (offsetValue != null)
                {
                    offsetValue.preNotify(IValueListener.FLAG_UNMERGEABLE);
                }
            }
        }

        /* 3. Set all changed clips to their final values directly */
        for (int i = 0; i < changedClips.size(); i++)
        {
            Clip clip = changedClips.get(i);
            Vector3i finalData = finalDataList.get(i);
            int finalOffset = finalOffsetsList.get(i);

            clip.tick.setDirect(finalData.x());
            clip.layer.setDirect(finalData.y());
            clip.duration.setDirect(finalData.z());
            this.setClipOffset(clip, finalOffset, true);
        }

        /* 4. Fire postNotify(FLAG_UNMERGEABLE) on all properties that actually changed */
        for (int i = 0; i < changedClips.size(); i++)
        {
            Clip clip = changedClips.get(i);
            Vector3i initial = initialDataList.get(i);
            Vector3i finalData = finalDataList.get(i);
            int initialOffset = initialOffsetsList.get(i);
            int finalOffset = finalOffsetsList.get(i);

            if (initial.x() != finalData.x())
            {
                clip.tick.postNotify(IValueListener.FLAG_UNMERGEABLE);
            }
            if (initial.y() != finalData.y())
            {
                clip.layer.postNotify(IValueListener.FLAG_UNMERGEABLE);
            }
            if (initial.z() != finalData.z())
            {
                clip.duration.postNotify(IValueListener.FLAG_UNMERGEABLE);
            }
            if (initialOffset != finalOffset)
            {
                ValueInt offsetValue = this.getClipOffsetValue(clip);

                if (offsetValue != null)
                {
                    offsetValue.postNotify(IValueListener.FLAG_UNMERGEABLE);
                }
            }
        }
    }

    private int snap(int tick)
    {
        if (Window.isAltPressed())
        {
            return tick;
        }

        int diff = 11;
        int closest = tick;

        for (int point : this.snappingPoints)
        {
            int pointX = this.toGraphX(point);
            int abs = Math.abs(this.toGraphX(tick) - pointX);

            if (abs <= 10 && abs < diff)
            {
                closest = point;
                diff = abs;
            }
        }

        return closest;
    }

    private void captureSelection(Area area)
    {
        this.clearSelection();

        for (Clip clip : this.clips.get())
        {
            Area clipArea = new Area();

            int x = this.toGraphX(clip.tick.get());
            int y = this.toLayerY(clip.layer.get());

            clipArea.set(x, y, this.toGraphX(clip.tick.get() + clip.duration.get()) - x, LAYER_HEIGHT);

            if (area.intersects(clipArea))
            {
                this.addSelected(clip);
            }
        }
    }

    private void handleScrolling(int mouseX, int mouseY)
    {
        if (this.scrolling)
        {
            if (!Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_3))
            {
                this.resetStates();

                return;
            }

            this.scale.setShift(this.scale.getShift() - (mouseX - this.lastX) / this.scale.getZoom());
            this.vertical.scrollBy(this.lastY - mouseY);
            this.vertical.clamp();

            this.lastX = mouseX;
            this.lastY = mouseY;

            this.scale.setShift(this.scale.getShift());
            this.scale.calculateMultiplier();
        }
    }

    /**
     * Render camera work (layers, clips, envelope previews, looping region, cursor, etc.)
     */
    private void renderCameraWork(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        Area area = this.area;
        int h = LAYER_HEIGHT;
        int leftEdge = this.toGraphX(0);

        this.renderTimelineBackground(context, leftEdge, h);

        if (leftEdge > this.area.x)
        {
            batcher.box(this.area.x, this.area.y, Math.min(leftEdge, this.area.ex()), this.area.ey(), 0x9905070b);
        }

        batcher.clip(this.area, context);

        this.renderTickMarkers(context, area.y, area.h);

        batcher.unclip(context);
        batcher.clip(this.vertical.area, context);

        for (int i = 0; i < this.layers; i++)
        {
            int ly = this.toLayerY(i);

            batcher.box(this.area.x, ly + h - 1, this.area.ex(), ly + h, 0x16000000);
        }

        batcher.unclip(context);
        batcher.clip(this.vertical.area, context);

        List<Clip> clips = this.clips.get();

        for (int i = 0, c = clips.size(); i < c; i++)
        {
            Clip clip = clips.get(i);
            IUIClipRenderer renderer = this.renderers.get(clip);

            Area clipArea = this.getClipArea(clip, CLIP_AREA, h);
            boolean selected = this.hasSelected(i);

            if (!this.hasEmbeddedView())
            {
                clipArea.y += 1;
                clipArea.h -= 2;
            }

            renderer.renderClip(context, this, clip, clipArea, selected, this.delegate.getClip() == clip);

            int clipHandle = TimelineToolbarPointerBlock.blocksPointer(context)
                ? -1
                : this.getClipHandle(clip, context, h);
            int color = this.grabMode != 0 ? Colors.WHITE : Colors.A50;

            if (clipHandle == 1 || (selected && this.grabMode == 1))
            {
                context.batcher.icon(Icons.CLIP_HANLDE_LEFT, color, clipArea.x, clipArea.y + 10, 0F, 0.5F);
            }
            else if (clipHandle == 2 || (selected && this.grabMode == 2))
            {
                context.batcher.icon(Icons.CLIP_HANLDE_RIGHT, color, clipArea.ex(), clipArea.y + 10, 1F, 0.5F);
            }
        }

        this.renderAddPreview(context, h);
        this.renderLoopingRegion(context, area.y);

        batcher.unclip(context);
        batcher.clip(this.area, context);

        String label = TimeUtils.formatTime(this.delegate.getCursor()) + "/" + TimeUtils.formatTime(this.clips.calculateDuration());

        renderCursor(context, label, area, this.toGraphX(this.delegate.getCursor()));
        this.renderSelection(context);

        batcher.unclip(context);
        batcher.clip(this.vertical.area, context);

        this.vertical.renderScrollbar(batcher);

        batcher.unclip(context);
    }

    private void renderTimelineBackground(UIContext context, int leftEdge, int h)
    {
        Batcher2D batcher = context.batcher;

        batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xee0b0d12);
        batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + RULER_HEIGHT, 0xff111115);
        batcher.box(this.area.x, this.area.y + RULER_HEIGHT - 1, this.area.ex(), this.area.y + RULER_HEIGHT, 0x22ffffff);

        batcher.clip(this.vertical.area, context);

        for (int i = 0; i < this.layers; i++)
        {
            int ly = this.toLayerY(i);

            if (i % 2 != 0)
            {
                batcher.box(Math.max(leftEdge, this.area.x), ly, this.area.ex(), ly + h, 0x26000000);
            }
        }

        batcher.unclip(context);
    }

    private Area getClipArea(Clip clip, Area area, int h)
    {
        int tick = clip.tick.get();
        int x = this.toGraphX(tick);
        int y = this.toLayerY(clip.layer.get());
        int w = this.toGraphX(tick + clip.duration.get()) - x;

        area.set(x, y, w, h);

        return area;
    }

    private int getClipHandle(Clip clip, UIContext context, int h)
    {
        Area clipArea = this.getClipArea(clip, CLIP_AREA, h);
        int separation = Math.min(clipArea.w / 2, 5);

        if (clipArea.isInside(context))
        {
            if (Window.isCtrlPressed())
            {
                return 0;
            }

            if (context.mouseX - clipArea.x < separation)
            {
                return 1;
            }
            else if (context.mouseX - clipArea.ex() >= -separation)
            {
                return 2;
            }

            return 0;
        }

        return -1;
    }

    private void renderAddPreview(UIContext context, int h)
    {
        if (this.placementPreview != null)
        {
            this.renderPreviewBox(context, h, this.placementPreview, true);
        }

        if (this.addPreview != null)
        {
            this.renderPreviewBox(context, h, this.addPreview, true);
        }
    }

    private void renderPreviewBox(UIContext context, int h, Vector3i preview, boolean pulsing)
    {
        int x = this.toGraphX(preview.x);
        int y = this.toLayerY(preview.y);
        int d = this.toGraphX(preview.x + preview.z);

        if (pulsing)
        {
            UIClipPlacementInteraction.renderPulsingOutline(context, x, y, d, y + h);
        }
        else
        {
            context.batcher.outline(x, y, d, y + h, Colors.WHITE);
        }
    }

    /**
     * Keep ruler / snap loops bounded to roughly screen-width iterations.
     * Huge clip durations used to set the view span to billions of ticks and hang here.
     */
    private int capRulerEnd(int start, int end, int mult)
    {
        if (mult <= 0)
        {
            return start;
        }

        int maxMarkers = Math.max(this.area.w * 2, 512) + 2;
        long span = (long) end - (long) start;

        if (span / mult > maxMarkers)
        {
            long capped = (long) start + (long) mult * maxMarkers;

            return capped > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capped;
        }

        return end;
    }

    /**
     * Render tick markers that help orient within camera work.
     */
    private void renderTickMarkers(UIContext context, int y, int h)
    {
        TimelineRuler.Step step = TimelineRuler.steps(this.scale);
        int mult = step.minor;
        int start = (int) this.scale.getMinValue();
        int end = (int) this.scale.getMaxValue();
        int max = Integer.MAX_VALUE;
        int major = step.major;

        start -= start % mult;
        end -= end % mult;

        start = MathUtils.clamp(start, 0, max);
        end = MathUtils.clamp(end, mult, max);
        end = this.capRulerEnd(start, end, mult);

        for (int j = start; j <= end; j += mult)
        {
            int xx = this.toGraphX(j);
            boolean majorTick = j % major == 0;
            int lineColor = majorTick ? 0x1cffffff : 0x0affffff;
            int tickBottom = this.area.y + RULER_HEIGHT;
            int tickHeight = majorTick ? 8 : 4;

            context.batcher.box(xx, y, xx + 1, y + h, lineColor);
            context.batcher.box(xx, tickBottom - tickHeight, xx + 1, tickBottom, majorTick ? 0xddffffff : 0x77ffffff);

            if (majorTick)
            {
                String value = TimeUtils.formatTime(j);

                context.batcher.textShadow(value, xx + 4, this.area.y + 2, Colors.WHITE);
            }
        }
    }

    /**
     * Render selection box.
     */
    private void renderSelection(UIContext context)
    {
        if (this.selecting)
        {
            context.batcher.normalizedBox(this.lastX, this.lastY, context.mouseX, context.mouseY, Colors.setA(Colors.ACTIVE, 0.25F));
        }
    }

    /**
     * Render looping region
     */
    private void renderLoopingRegion(UIContext context, int y)
    {
        if (this.loopMin == this.loopMax)
        {
            return;
        }

        int min = Math.min(this.loopMin, this.loopMax);
        int max = Math.max(this.loopMin, this.loopMax);

        int minX = this.toGraphX(min);
        int maxX = this.toGraphX(max);

        if (maxX >= this.area.x + 1 && minX < this.area.ex() - 1)
        {
            minX = MathUtils.clamp(minX, this.area.x + 1, this.area.ex() - 1);
            maxX = MathUtils.clamp(maxX, this.area.x + 1, this.area.ex() - 1);

            float alpha = BBSSettings.editorLoop.get() ? 1 : 0.4F;
            int color = Colors.mulRGB(0xff88ffff, alpha);

            context.batcher.gradientVBox(minX, y, maxX, this.area.ey(), Colors.mulRGB(0x0000ffff, alpha), Colors.mulRGB(0xaa0088ff, alpha));
            context.batcher.box(minX, y, minX + 1, this.area.ey(), color);
            context.batcher.box(maxX - 1, y, maxX, this.area.ey(), color);
        }
    }

    private record Anchor(int clipIndex, boolean isLeft, int graphX)
    {}

    private interface ClipTransformStrategy
    {
        public void apply(List<Clip> others, List<Clip> grabbedClips, List<Vector3i> grabbedData, int dx, int dy);
    }

    private static class UITabButton extends UIButton
    {
        private final Icon icon;
        private final IKey tooltip;
        private boolean active;
        private boolean noSeparator;

        public UITabButton(IKey label, IKey tooltip, Icon icon, Consumer<UIButton> callback)
        {
            super(label, callback);
            this.tooltip = tooltip;
            this.icon = icon;
            this.tooltip(this.tooltip, Direction.TOP);
        }

        public void noSeparator()
        {
            this.noSeparator = true;
        }

        public void setActive(boolean active)
        {
            this.active = active;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            boolean enabled = this.isEnabled();
            int primary = BBSSettings.primaryColor.get();
            int color = this.active ? primary : 0;
            int iconColor = this.active ? Colors.WHITE : 0xddffffff;

            if (!enabled)
            {
                iconColor = 0x80404040;
            }
            else if (this.hover)
            {
                color = this.active ? Colors.mulRGB(primary, 0.9F) : Colors.A25;
                iconColor = Colors.WHITE;
            }

            if (color != 0)
            {
                this.area.render(context.batcher, this.active ? (color | Colors.A100) : color);
            }

            if (!this.noSeparator)
            {
                context.batcher.box(this.area.ex() - 1, this.area.y + 2, this.area.ex(), this.area.ey() - 2, 0x22ffffff);
            }

            context.batcher.icon(this.icon, iconColor, this.area.mx(), this.area.my(), 0.5F, 0.5F);
        }
    }

    private enum ClipTab
    {
        CAMERA,
        RESOURCE,
        SCREEN,
        ANCHOR,
        EXTRAS
    }

    private static class UIClipsAddContextMenu extends UISimpleContextMenu
    {
        private final UIElement tabs;
        private final UIElement separator;
        private final UIButton camera;
        private final UIButton resource;
        private final UIButton screen;
        private final UIButton anchor;
        private final UIButton extras;

        private final List<ContextAction> cameraActions = new ArrayList<>();
        private final List<ContextAction> resourceActions = new ArrayList<>();
        private final List<ContextAction> screenActions = new ArrayList<>();
        private final List<ContextAction> anchorActions = new ArrayList<>();
        private final List<ContextAction> extrasActions = new ArrayList<>();

        private ClipTab tab = ClipTab.CAMERA;

        public UIClipsAddContextMenu(UIClips uiClips, Vector3i preview)
        {
            super();

            for (TimelineClipTypeGroups.ClipGroup group : TimelineClipTypeGroups.forCamera(uiClips.factory))
            {
                List<ContextAction> target = this.actionsForGroup(group.label);

                if (target == null)
                {
                    continue;
                }

                for (Link type : group.types)
                {
                    IKey typeKey = UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_CLIP_TYPE.format(UIKeys.C_CLIP.get(type));
                    ClipFactoryData data = uiClips.factory.getData(type);
                    Runnable runnable = () -> uiClips.addClip(type, preview.x, preview.y, preview.z);

                    target.add(new ColorfulContextAction(data.icon, typeKey, runnable, data.color));
                }
            }

            this.camera = new UITabButton(IKey.EMPTY, UIKeys.CAMERA_TIMELINE_CLIPS_TABS_CAMERA, Icons.CAMERA, (b) -> this.setTab(ClipTab.CAMERA));
            this.resource = new UITabButton(IKey.EMPTY, UIKeys.CAMERA_TIMELINE_CLIPS_TABS_RESOURCE, Icons.FOLDER, (b) -> this.setTab(ClipTab.RESOURCE));
            this.screen = new UITabButton(IKey.EMPTY, UIKeys.CAMERA_TIMELINE_CLIPS_TABS_SCREEN, Icons.CONSOLE, (b) -> this.setTab(ClipTab.SCREEN));
            this.anchor = new UITabButton(IKey.EMPTY, UIKeys.CAMERA_TIMELINE_CLIPS_TABS_ANCHOR, Icons.ORBIT, (b) -> this.setTab(ClipTab.ANCHOR));
            this.extras = new UITabButton(IKey.EMPTY, UIKeys.CAMERA_TIMELINE_CLIPS_TABS_EXTRAS, Icons.MORE, (b) -> this.setTab(ClipTab.EXTRAS));

            ((UITabButton) this.extras).noSeparator();

            this.tabs = UI.row(0, this.camera, this.resource, this.screen, this.anchor, this.extras);
            this.separator = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x44ffffff);
                }
            };

            this.tabs.relative(this).w(1F).h(20).row(0).resize();
            this.separator.relative(this).xy(0, 20).w(1F).h(1);
            this.actions.relative(this).xy(0, 21).w(1F).h(1F, -21);
            this.add(this.tabs, this.separator);

            this.camera.setEnabled(!this.cameraActions.isEmpty());
            this.resource.setEnabled(!this.resourceActions.isEmpty());
            this.screen.setEnabled(!this.screenActions.isEmpty());
            this.anchor.setEnabled(!this.anchorActions.isEmpty());
            this.extras.setEnabled(!this.extrasActions.isEmpty());

            if (!this.cameraActions.isEmpty()) this.setTab(ClipTab.CAMERA);
            else if (!this.resourceActions.isEmpty()) this.setTab(ClipTab.RESOURCE);
            else if (!this.screenActions.isEmpty()) this.setTab(ClipTab.SCREEN);
            else if (!this.anchorActions.isEmpty()) this.setTab(ClipTab.ANCHOR);
            else this.setTab(ClipTab.EXTRAS);
        }

        private List<ContextAction> actionsForGroup(IKey label)
        {
            if (label == UIKeys.CAMERA_TIMELINE_CLIPS_TABS_CAMERA)
            {
                return this.cameraActions;
            }

            if (label == UIKeys.CAMERA_TIMELINE_CLIPS_TABS_RESOURCE)
            {
                return this.resourceActions;
            }

            if (label == UIKeys.CAMERA_TIMELINE_CLIPS_TABS_SCREEN)
            {
                return this.screenActions;
            }

            if (label == UIKeys.CAMERA_TIMELINE_CLIPS_TABS_ANCHOR)
            {
                return this.anchorActions;
            }

            if (label == UIKeys.CAMERA_TIMELINE_CLIPS_TABS_EXTRAS)
            {
                return this.extrasActions;
            }

            return this.extrasActions;
        }

        private void setTab(ClipTab tab)
        {
            this.tab = tab;
            ((UITabButton) this.camera).setActive(tab == ClipTab.CAMERA);
            ((UITabButton) this.resource).setActive(tab == ClipTab.RESOURCE);
            ((UITabButton) this.screen).setActive(tab == ClipTab.SCREEN);
            ((UITabButton) this.anchor).setActive(tab == ClipTab.ANCHOR);
            ((UITabButton) this.extras).setActive(tab == ClipTab.EXTRAS);

            List<ContextAction> activeList;
            switch (tab)
            {
                case CAMERA: activeList = this.cameraActions; break;
                case RESOURCE: activeList = this.resourceActions; break;
                case SCREEN: activeList = this.screenActions; break;
                case ANCHOR: activeList = this.anchorActions; break;
                default: activeList = this.extrasActions; break;
            }

            this.actions.setList(new ArrayList<>(activeList));

            UIContext context = this.getContext();
            if (context != null)
            {
                this.w(this.calculateWidth(context));
                this.h(this.calculateHeight());
                this.bounds(context.menu.overlay, 5);
                this.resize();
            }
        }

        @Override
        public void setMouse(UIContext context)
        {
            int w = this.calculateWidth(context);
            int h = this.calculateHeight();

            this.xy(context.mouseX(), context.mouseY()).w(w).h(h).bounds(context.menu.overlay, 5);
            this.resize();
        }

        private int calculateWidth(UIContext context)
        {
            int w = 120;

            for (ContextAction action : this.cameraActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));
            for (ContextAction action : this.resourceActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));
            for (ContextAction action : this.screenActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));
            for (ContextAction action : this.anchorActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));
            for (ContextAction action : this.extrasActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));

            return w % 4 == 0 ? w : w + (4 - w % 4);
        }

        private int calculateHeight()
        {
            int actionsSize;
            switch (this.tab)
            {
                case CAMERA: actionsSize = this.cameraActions.size(); break;
                case RESOURCE: actionsSize = this.resourceActions.size(); break;
                case SCREEN: actionsSize = this.screenActions.size(); break;
                case ANCHOR: actionsSize = this.anchorActions.size(); break;
                default: actionsSize = this.extrasActions.size(); break;
            }

            actionsSize = Math.max(actionsSize, 1);
            return 21 + actionsSize * this.actions.scroll.scrollItemSize;
        }
    }
}
