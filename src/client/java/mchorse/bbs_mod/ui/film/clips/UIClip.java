package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.DamageActionClip;
import mchorse.bbs_mod.actions.types.MobDeathActionClip;
import mchorse.bbs_mod.actions.types.ProjectileAttackActionClip;
import mchorse.bbs_mod.actions.types.SwipeActionClip;
import mchorse.bbs_mod.actions.types.blocks.BreakBlockActionClip;
import mchorse.bbs_mod.actions.types.blocks.InteractBlockActionClip;
import mchorse.bbs_mod.actions.types.blocks.PlaceBlockActionClip;
import mchorse.bbs_mod.actions.types.chat.ChatActionClip;
import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import mchorse.bbs_mod.actions.types.item.ItemDropActionClip;
import mchorse.bbs_mod.actions.types.item.UseBlockItemActionClip;
import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.BossBarClip;
import mchorse.bbs_mod.camera.clips.misc.CurveClientClip;
import mchorse.bbs_mod.camera.clips.misc.HotbarClip;
import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.clips.misc.VideoClip;
import mchorse.bbs_mod.camera.clips.modifiers.AngleClip;
import mchorse.bbs_mod.camera.clips.modifiers.DollyZoomClip;
import mchorse.bbs_mod.camera.clips.modifiers.DragClip;
import mchorse.bbs_mod.camera.clips.modifiers.LookClip;
import mchorse.bbs_mod.camera.clips.modifiers.MathClip;
import mchorse.bbs_mod.camera.clips.modifiers.OrbitClip;
import mchorse.bbs_mod.camera.clips.modifiers.RemapperClip;
import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.camera.clips.modifiers.TranslateClip;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.clips.overwrite.PathClip;
import mchorse.bbs_mod.camera.clips.screen.CinematicClip;
import mchorse.bbs_mod.camera.clips.screen.ColorClip;
import mchorse.bbs_mod.camera.clips.screen.EyeClip;
import mchorse.bbs_mod.camera.clips.screen.GrainClip;
import mchorse.bbs_mod.camera.clips.screen.LetterboxClip;
import mchorse.bbs_mod.camera.clips.screen.ScreenNodeClip;
import mchorse.bbs_mod.camera.clips.screen.VignetteClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIAttackActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIBreakBlockActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIChatActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UICommandActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIDamageActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIInteractBlockActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIItemDropActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIMobDeathActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIPlaceBlockActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIProjectileAttackActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UISwipeActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIUseBlockItemActionClip;
import mchorse.bbs_mod.ui.film.clips.actions.UIUseItemActionClip;
import mchorse.bbs_mod.ui.film.clips.widgets.UIEnvelope;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.TimeUtilsClient;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.undo.IUndo;

import java.util.HashMap;
import java.util.Map;

public abstract class UIClip <T extends Clip> extends UIElement
{
    private static final Map<Class, IUIClipFactory> FACTORIES = new HashMap<>();
    private static final Map<Class, Integer> SCROLLS = new HashMap<>();

    public T clip;
    public IUIClipsDelegate editor;

    public UIToggle enabled;
    public UITextbox title;
    public UITrackpad layer;
    public UITrackpad tick;
    public UITrackpad duration;

    public UIEnvelope envelope;

    public UIScrollView panels;

    static
    {
        register(IdleClip.class, UIIdleClip::new);
        register(DollyClip.class, UIDollyClip::new);
        register(PathClip.class, UIPathClip::new);
        register(KeyframeClip.class, UIKeyframeClip::new);
        register(TranslateClip.class, UITranslateClip::new);
        register(AngleClip.class, UIAngleClip::new);
        register(DragClip.class, UIDragClip::new);
        register(ShakeClip.class, UIShakeClip::new);
        register(MathClip.class, UIMathClip::new);
        register(LookClip.class, UILookClip::new);
        register(TrackerClientClip.class, UITrackerClip::new);
        register(OrbitClip.class, UIOrbitClip::new);
        register(RemapperClip.class, UIRemapperClip::new);
        register(AudioClientClip.class, UIAudioClip::new);
        register(VideoClip.class, UIVideoClip::new);
        register(SubtitleClip.class, UISubtitleClip::new);
        register(ImageClip.class, UIImageClip::new);
        register(HotbarClip.class, UIHotbarClip::new);
        register(CurveClientClip.class, UICurveClip::new);
        register(DollyZoomClip.class, UIDollyZoomClip::new);
        register(ColorClip.class, UIColorClip::new);
        register(CinematicClip.class, UICinematicClip::new);
        register(VignetteClip.class, UIVignetteClip::new);
        register(LetterboxClip.class, UILetterboxClip::new);
        register(GrainClip.class, UIGrainClip::new);
        register(ScreenNodeClip.class, UIScreenNodeClip::new);
        register(BossBarClip.class, UIBossBarClip::new);
        register(EyeClip.class, UIEyeClip::new);

        register(ChatActionClip.class, UIChatActionClip::new);
        register(CommandActionClip.class, UICommandActionClip::new);
        register(PlaceBlockActionClip.class, UIPlaceBlockActionClip::new);
        register(InteractBlockActionClip.class, UIInteractBlockActionClip::new);
        register(BreakBlockActionClip.class, UIBreakBlockActionClip::new);
        register(UseItemActionClip.class, UIUseItemActionClip::new);
        register(UseBlockItemActionClip.class, UIUseBlockItemActionClip::new);
        register(AttackActionClip.class, UIAttackActionClip::new);
        register(ProjectileAttackActionClip.class, UIProjectileAttackActionClip::new);
        register(DamageActionClip.class, UIDamageActionClip::new);
        register(MobDeathActionClip.class, UIMobDeathActionClip::new);
        register(ItemDropActionClip.class, UIItemDropActionClip::new);
        register(SwipeActionClip.class, UISwipeActionClip::new);
    }

    public static <T extends Clip> void register(Class<T> clazz, IUIClipFactory<T> factory)
    {
        FACTORIES.put(clazz, factory);
    }

    public static void saveScroll(UIClip editor)
    {
        if (editor != null)
        {
            SCROLLS.put(editor.clip.getClass(), (int) editor.panels.scroll.getScroll());
        }
    }

    public static UIClip createPanel(Clip clip, IUIClipsDelegate delegate)
    {
        IUIClipFactory factory = FACTORIES.get(clip.getClass());
        UIClip clipEditor = factory == null ? null : factory.create(clip, delegate);

        if (clipEditor != null)
        {
            clipEditor.panels.scroll.setScroll(SCROLLS.getOrDefault(clip.getClass(), 0));
        }

        return clipEditor;
    }

    public static UILabel label(IKey key)
    {
        /* Narrow clip inspector truncates single-line headers; wrap so full titles remain readable. */
        return UI.label(key).background(() -> BBSSettings.primaryColor(Colors.A50)).wrapping();
    }

    public UIClip(T clip, IUIClipsDelegate editor)
    {
        this.clip = clip;
        this.editor = editor;

        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> this.editor.editMultiple(this.clip.enabled, (value) ->
        {
            value.set(b.getValue());
        }));
        this.title = new UITextbox(1000, (t) -> this.clip.title.set(t));
        this.title.tooltip(UIKeys.CAMERA_PANELS_TITLE_TOOLTIP);
        this.layer = new UITrackpad((v) -> this.editor.editMultiple(this.clip.layer, v.intValue()));
        this.layer.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.CAMERA_PANELS_LAYER);
        this.tick = new UITrackpad((v) -> this.editor.editMultiple(this.clip.tick, (int) TimeUtils.fromTime(v)));
        this.tick.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.CAMERA_PANELS_TICK);
        this.duration = new UITrackpad((v) ->
        {
            this.editor.editMultiple(this.clip.duration, (int) TimeUtils.fromTime(v));
            this.updateDuration((int) TimeUtils.fromTime(v));
        });
        this.duration.limit(1, Clip.MAX_DURATION_TICKS, true).tooltip(UIKeys.CAMERA_PANELS_DURATION);
        this.envelope = new UIEnvelope(this);
        this.envelope.channel.setUndoId("envelope_keyframes");

        boolean horizontal = BBSSettings.editorHorizontalClipEditor.get();

        this.panels = new UIScrollView(horizontal ? ScrollDirection.HORIZONTAL : ScrollDirection.VERTICAL);
        this.panels.scroll.cancelScrolling();

        if (horizontal)
        {
            this.panels.full(this).column(5).scroll().width(140).padding(10);
        }
        else
        {
            this.panels.full(this).column(5).scroll().vertical().stretch().padding(10);
        }

        this.registerUI();
        this.registerPanels();
        /* Clip-specific options first; envelopes last so they do not bury property fields. */
        this.addEnvelopes();

        this.setupSections(this.panels);

        this.add(this.panels);
    }

    private void setupSections(UIElement element)
    {
        if (element instanceof UISection)
        {
            UISection group = (UISection) element;
            String id = group.title.label.get();
            String clipType = this.clip.getClass().getSimpleName();

            boolean global = BBSSettings.editorGlobalClipPanels != null && BBSSettings.editorGlobalClipPanels.get();

            if (global)
            {
                MapType data = PresetManager.CLIP_LAYOUTS.load("_clip_ui_sections");

                if (data == null)
                {
                    data = PresetManager.CLIPS.load("_clip_ui_sections");
                }

                if (data != null && data.has(clipType, BaseType.TYPE_MAP))
                {
                    MapType clipMap = data.getMap(clipType);

                    if (clipMap.has(id, BaseType.TYPE_BYTE))
                    {
                        group.setExpanded(clipMap.getBool(id));
                    }
                }
            }
            else if (this.editor != null && this.editor.getFilm() != null)
            {
                BaseType data = this.editor.getFilm().projectData.get();

                if (data instanceof MapType)
                {
                    MapType map = (MapType) data;

                    if (map.has(clipType, BaseType.TYPE_MAP))
                    {
                        MapType clipMap = map.getMap(clipType);

                        if (clipMap.has(id, BaseType.TYPE_BYTE))
                        {
                            group.setExpanded(clipMap.getBool(id));
                        }
                    }
                }
            }

            group.onToggle((open) ->
            {
                if (BBSSettings.editorGlobalClipPanels != null && BBSSettings.editorGlobalClipPanels.get())
                {
                    MapType map = PresetManager.CLIP_LAYOUTS.load("_clip_ui_sections");

                    if (map == null)
                    {
                        map = PresetManager.CLIPS.load("_clip_ui_sections");
                    }

                    if (map == null)
                    {
                        map = new MapType();
                    }

                    if (!map.has(clipType, BaseType.TYPE_MAP))
                    {
                        map.put(clipType, new MapType());
                    }

                    MapType clipMap = map.getMap(clipType);
                    clipMap.putBool(id, open);

                    PresetManager.CLIP_LAYOUTS.save("_clip_ui_sections", map);
                }
                else if (this.editor != null && this.editor.getFilm() != null)
                {
                    BaseType baseData = this.editor.getFilm().projectData.get();

                    if (!(baseData instanceof MapType))
                    {
                        baseData = new MapType();
                    }

                    MapType map = (MapType) baseData;

                    if (!map.has(clipType, BaseType.TYPE_MAP))
                    {
                        map.put(clipType, new MapType());
                    }

                    MapType clipMap = map.getMap(clipType);
                    clipMap.putBool(id, open);

                    this.editor.getFilm().projectData.set(baseData);
                }
            });
        }

        for (UIElement child : element.getChildren(UIElement.class))
        {
            this.setupSections(child);
        }
    }

    protected void registerUI()
    {}

    protected void registerPanels()
    {
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TITLE, this.title, this.enabled));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_METRICS, UI.row(this.layer, this.tick), this.duration));
    }

    protected void addEnvelopes()
    {
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_ENVELOPES_TITLE, this.envelope));
    }

    protected UISection section(IKey title, UIElement... fields)
    {
        UISection group = new UISection(title);

        group.fields.add(fields);

        return group;
    }

    public void handleUndo(IUndo<ValueGroup> undo, boolean redo)
    {
        this.fillData();
    }

    protected void updateDuration(int duration)
    {}

    public void editClip(Position position)
    {
        this.fillData();
    }

    public void fillData()
    {
        TimeUtilsClient.configure(this.tick, 0);
        TimeUtilsClient.configure(this.duration, 1);

        this.enabled.setValue(this.clip.enabled.get());

        /* setText() moves the caret to the start — skip while the user is typing. */
        if (!this.title.isFocused())
        {
            this.title.setText(this.clip.title.get());
        }

        this.layer.setValue(this.clip.layer.get());
        this.tick.setValue(TimeUtils.toTime(this.clip.tick.get()));
        this.duration.setValue(TimeUtils.toTime(this.clip.duration.get()));
        this.envelope.fillData();
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.gradientHBox(this.area.x - 40, this.area.y, this.area.ex() - 40, this.area.ey(), 0, Colors.A25);
        context.batcher.box(this.area.ex() - 40, this.area.y, this.area.ex(), this.area.ey(), Colors.A25);

        super.render(context);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("envelope"))
        {
            this.editor.embedView(this.envelope.channel);
            this.envelope.channel.view.editSheet(this.envelope.channel.view.getGraph().getSheets().get(0));
            this.envelope.channel.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.envelope.channel.hasParent())
        {
            data.putString("embed", "envelope");
        }
    }

    public UIElement resolveEmbeddableView(String embeddedId)
    {
        return this.resolveClipEmbeddableView(embeddedId);
    }

    protected UIElement resolveClipEmbeddableView(String embeddedId)
    {
        return null;
    }

    public static interface IUIClipFactory <T extends Clip>
    {
        public UIClip create(T clip, IUIClipsDelegate delegate);
    }
}
