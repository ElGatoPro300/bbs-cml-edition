package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.events.register.RegisterKeyframeFactoryUIEvent;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIBossBarColorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.tooltips.InterpolationTooltip;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class UIKeyframeFactory <T> extends UIElement
{
    public static final Map<IKeyframeFactory, IUIKeyframeFactoryFactory> FACTORIES = new HashMap<>();
    private static final Map<IKeyframeFactory, Integer> SCROLLS = new HashMap<>();

    public UIScrollView scroll;
    public UITrackpad tick;
    public UITrackpad duration;
    public UIIcon interp;

    public UIIcon shape;
    public UIColor color;

    protected Keyframe<T> keyframe;
    protected UIKeyframes editor;

    static
    {
        register(KeyframeFactories.ANCHOR, UIAnchorKeyframeFactory::new);
        register(KeyframeFactories.MOUNT_LINK, UIMountLinkKeyframeFactory::new);
        register(KeyframeFactories.LOOK_AT, UILookAtKeyframeFactory::new);
        register(KeyframeFactories.INVERSE_KINEMATICS, UIInverseKinematicsKeyframeFactory::new);
        register(KeyframeFactories.ILLUSION, UIIllusionKeyframeFactory::new);
        register(KeyframeFactories.BOOLEAN, UIBooleanKeyframeFactory::new);
        register(KeyframeFactories.COLOR, UIColorKeyframeFactory::new);
        register(KeyframeFactories.FLOAT, UIFloatKeyframeFactory::new);
        register(KeyframeFactories.LIGHTING_BRIGHTNESS, UIFloatKeyframeFactory::new);
        register(KeyframeFactories.LIGHTING_SETTINGS, UILightingKeyframeFactory::new);
        register(KeyframeFactories.DOUBLE, UIDoubleKeyframeFactory::new);
        register(KeyframeFactories.INTEGER, UIIntegerKeyframeFactory::new);
        register(KeyframeFactories.LINK, UILinkKeyframeFactory::new);
        register(KeyframeFactories.POSE, UIPoseKeyframeFactory::new);
        register(KeyframeFactories.STRING, UIStringKeyframeFactory::new);
        register(KeyframeFactories.TRANSFORM, UITransformKeyframeFactory::new);
        register(KeyframeFactories.VECTOR4F, UIVector4fKeyframeFactory::new);
        register(KeyframeFactories.BLOCK_STATE, UIBlockStateKeyframeFactory::new);
        register(KeyframeFactories.ITEM_STACK, UIItemStackKeyframeFactory::new);
        register(KeyframeFactories.ACTIONS_CONFIG, UIActionsConfigKeyframeFactory::new);
        register(KeyframeFactories.SHAPE_KEYS, UIShapeKeysKeyframeFactory::new);
        register(KeyframeFactories.PARTICLE_SETTINGS, UIParticleSettingsKeyframeFactory::new);
        register(KeyframeFactories.STRUCTURE_LIGHT_SETTINGS, UIStructureLightSettingsKeyframeFactory::new);
        register(KeyframeFactories.GLOW_SETTINGS, UIGlowSettingsKeyframeFactory::new);
        register(KeyframeFactories.PAINT_SETTINGS, UIPaintSettingsKeyframeFactory::new);
        register(KeyframeFactories.SHADOW_SETTINGS, UIShadowSettingsKeyframeFactory::new);
        register(KeyframeFactories.LENS_RADIUS_SETTINGS, UILensRadiusSettingsKeyframeFactory::new);
        register(KeyframeFactories.CHROMA_SKY_SETTINGS, UIChromaSkyCurveSettingsKeyframeFactory::new);
        register(KeyframeFactories.SHAKE_SETTINGS, UIShakeSettingsKeyframeFactory::new);
    }

    public static <T> void register(IKeyframeFactory<T> clazz, IUIKeyframeFactoryFactory<T> factory)
    {
        FACTORIES.put(clazz, factory);
    }

    public static void saveScroll(UIKeyframeFactory editor)
    {
        if (editor != null)
        {
            SCROLLS.put(editor.keyframe.getFactory(), (int) editor.scroll.scroll.getScroll());
        }
    }

    public static <T> UIKeyframeFactory createPanel(Keyframe<T> keyframe, UIKeyframes editor)
    {
        UIKeyframeFactory uiEditor = null;

        if (keyframe.getFactory() == KeyframeFactories.BOOLEAN && editor != null)
        {
            UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

            if (sheet != null && FormUtils.isVisiblePropertyPath(sheet.id))
            {
                @SuppressWarnings("unchecked")
                Keyframe<Boolean> booleanKeyframe = (Keyframe<Boolean>) keyframe;

                uiEditor = new UIVisibleKeyframeFactory(booleanKeyframe, editor);
            }
        }

        if (uiEditor == null && keyframe.getFactory() == KeyframeFactories.DOUBLE && editor != null)
        {
            UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

            /* Eye clip blink amount only — require color_opacity so Image/BossBar/Letterbox
             * height tracks (which also sit next to a color track) keep unbounded editing. */
            if (sheet != null && "height".equals(sheet.id) && editor.getGraph().getSheet("color_opacity") != null)
            {
                @SuppressWarnings("unchecked")
                Keyframe<Double> doubleKeyframe = (Keyframe<Double>) keyframe;

                uiEditor = new UIEyeBlinkKeyframeFactory(doubleKeyframe, editor);
            }
            else if (sheet != null && "particles".equals(sheet.id))
            {
                @SuppressWarnings("unchecked")
                Keyframe<Double> doubleKeyframe = (Keyframe<Double>) keyframe;

                uiEditor = new UIParticlesKeyframeFactory(doubleKeyframe, editor);
            }
            else if (sheet != null && ("using_item".equals(sheet.id) || sheet.id.endsWith("/using_item")))
            {
                @SuppressWarnings("unchecked")
                Keyframe<Double> doubleKeyframe = (Keyframe<Double>) keyframe;

                uiEditor = new UIUsingItemKeyframeFactory(doubleKeyframe, editor);
            }
            else if (sheet != null && "target".equals(sheet.id))
            {
                @SuppressWarnings("unchecked")
                Keyframe<Double> doubleKeyframe = (Keyframe<Double>) keyframe;

                uiEditor = new UITrackerTargetKeyframeFactory(doubleKeyframe, editor);
            }
        }

        if (uiEditor == null && keyframe.getFactory() == KeyframeFactories.STRING && editor != null)
        {
            UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

            if (sheet != null && "attachment".equals(sheet.id))
            {
                @SuppressWarnings("unchecked")
                Keyframe<String> stringKeyframe = (Keyframe<String>) keyframe;

                uiEditor = new UITrackerAttachmentKeyframeFactory(stringKeyframe, editor);
            }
        }

        if (uiEditor == null && keyframe.getFactory() == KeyframeFactories.COLOR && editor != null)
        {
            UIKeyframeSheet sheet = resolveColorSheet(editor, keyframe);

            if (sheet != null && "color".equals(sheet.id) && "bar".equals(sheet.groupKey))
            {
                @SuppressWarnings("unchecked")
                Keyframe<Color> colorKeyframe = (Keyframe<Color>) keyframe;

                uiEditor = new UIBossBarColorKeyframeFactory(colorKeyframe, editor);
            }
            else if (sheet != null)
            {
                String name = StringUtils.fileName(sheet.id);

                if (name.equals("color_grade"))
                {
                    @SuppressWarnings("unchecked")
                    Keyframe<Color> colorKeyframe = (Keyframe<Color>) keyframe;

                    uiEditor = new UIFormColorGradeKeyframeFactory(colorKeyframe, editor);
                }
                else if (isFormColorTrack(name))
                {
                    @SuppressWarnings("unchecked")
                    Keyframe<Color> colorKeyframe = (Keyframe<Color>) keyframe;

                    uiEditor = new UIFormColorKeyframeFactory(colorKeyframe, editor);
                }
            }
        }

        if (uiEditor == null)
        {
            IUIKeyframeFactoryFactory<T> factory = FACTORIES.get(keyframe.getFactory());
            uiEditor = factory == null ? null : factory.create(keyframe, editor);
        }

        if (uiEditor != null)
        {
            RegisterKeyframeFactoryUIEvent.post(uiEditor, editor, keyframe);
            uiEditor.scroll.scroll.setScroll(SCROLLS.getOrDefault(keyframe.getFactory(), 0));
        }

        return uiEditor;
    }

    /** True for the Color track and its overlay family ({@code color_overlay}, {@code color_overlay0}, …). */
    private static boolean isFormColorTrack(String name)
    {
        return "color".equals(name) || "color_overlay".equals(name)
            || (name != null && name.startsWith("color_overlay") && name.length() > "color_overlay".length());
    }

    /**
     * Prefer the sheet that currently holds the selection for Color / Color grade rows.
     */
    private static UIKeyframeSheet resolveColorSheet(UIKeyframes editor, Keyframe<?> keyframe)
    {
        if (editor == null || keyframe == null)
        {
            return null;
        }

        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

        if (sheet != null)
        {
            return sheet;
        }

        Object channel = keyframe.getParent();

        for (UIKeyframeSheet candidate : editor.getGraph().getSheets())
        {
            if (candidate.channel == channel)
            {
                return candidate;
            }
        }

        return null;
    }

    public UIKeyframeFactory(Keyframe<T> keyframe, UIKeyframes editor)
    {
        this.keyframe = keyframe;
        this.editor = editor;

        this.scroll = UI.scrollView(5, 10);
        this.scroll.scroll.cancelScrolling();
        this.scroll.full(this);

        this.tick = new UITrackpad(this::setTick)
        {
            @Override
            public void focus(UIContext context)
            {
                UIKeyframeFactory.this.syncTickFromKeyframe();
                super.focus(context);
            }
        };
        this.tick.tooltip(UIKeys.KEYFRAMES_TICK);
        this.tick.getEvents().register(UITrackpadDragStartEvent.class, (e) ->
        {
            /* Must run before lastValue is captured (see UITrackpad). */
            this.syncTickFromKeyframe();
            this.editor.cacheKeyframes();
        });
        this.tick.getEvents().register(UITrackpadDragEndEvent.class, (e) ->
        {
            this.editor.submitKeyframes();
            /* submitKeyframes() replaces channel keyframe instances — rebind. */
            this.rebindKeyframe();
            this.syncTickFromKeyframe();
        });
        this.duration = new UITrackpad((v) -> this.setDuration(v.floatValue()))
        {
            @Override
            public void focus(UIContext context)
            {
                this.setValue(TimeUtils.toTime(UIKeyframeFactory.this.getKeyframe().getDuration()));
                super.focus(context);
            }
        };
        this.duration.limit(0, Float.MAX_VALUE).tooltip(UIKeys.KEYFRAMES_FORCED_DURATION);
        this.duration.getEvents().register(UITrackpadDragStartEvent.class, (e) ->
        {
            this.rebindKeyframe();
            this.duration.setValue(TimeUtils.toTime(this.keyframe.getDuration()));
            this.editor.cacheKeyframes();
        });
        this.duration.getEvents().register(UITrackpadDragEndEvent.class, (e) ->
        {
            this.editor.submitKeyframes();
            this.rebindKeyframe();
            this.duration.setValue(TimeUtils.toTime(this.keyframe.getDuration()));
        });
        this.interp = new UIIcon(Icons.GRAPH, (b) ->
        {
            Interpolation interp = this.keyframe.getInterpolation();
            UIInterpolationContextMenu menu = new UIInterpolationContextMenu(interp);

            this.getContext().replaceContextMenu(menu.callback(() -> this.editor.getGraph().setInterpolation(interp)));
        });
        this.interp.tooltip(new InterpolationTooltip(0F, 0.5F, () -> this.keyframe.getInterpolation()));
        this.interp.keys().register(Keys.KEYFRAMES_INTERP, this.interp::clickItself).category(UIKeys.KEYFRAMES_KEYS_CATEGORY);

        this.color = new UIColor((c) ->
        {
            for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
            {
                /* RGB picker has no alpha channel; force opaque so the keyframe stops provisional blinking. */
                for (Keyframe kf : sheet.selection.getSelected()) kf.setColor(new Color().set(c, false));
            }
        });
        this.color.setColor(keyframe.getColor() == null ? 0 : keyframe.getColor().getRGBColor());
        this.color.tooltip(UIKeys.KEYFRAMES_CHANGE_COLOR);
        this.color.context((menu) ->
        {
            menu.action(Icons.COLOR, UIKeys.KEYFRAMES_RESET_COLOR, () ->
            {
                for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
                {
                    for (Keyframe kf : sheet.selection.getSelected()) kf.setColor(null);
                }

                this.color.setColor(0);
            });
        });

        this.shape = new UIIcon(Icons.SHAPES, (b) ->
        {
            KeyframeShape currentShape = keyframe.getShape() == null ? KeyframeShape.SQUARE : keyframe.getShape();

            this.getContext().replaceContextMenu((menu) ->
            {
                for (KeyframeShape shape : KeyframeShape.values())
                {
                    IKeyframeShapeRenderer shapeRenderer = KeyframeShapeRenderers.SHAPES.get(shape);

                    menu.action(shapeRenderer.getIcon(), shapeRenderer.getLabel(), shape == currentShape, () ->
                    {
                        for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
                        {
                            for (Keyframe kf : sheet.selection.getSelected())
                            {
                                kf.setShape(shape);
                            }
                        }
                    });
                }
            });
        });
        this.shape.tooltip(UIKeys.KEYFRAMES_CHANGE_SHAPE);

        this.scroll.add(UI.row(this.interp, this.tick, this.duration));
        this.scroll.add(UI.row(this.shape, this.color));

        this.add(this.scroll);

        /* Fill data */
        this.tick.setValue(TimeUtils.toTime(keyframe.getTick()));
        this.duration.setValue(TimeUtils.toTime(keyframe.getDuration()));
    }

    public Keyframe<T> getKeyframe()
    {
        this.rebindKeyframe();

        return this.keyframe;
    }

    public void setTick(double tick)
    {
        this.rebindKeyframe();

        if (this.keyframe == null)
        {
            return;
        }

        float time = (float) TimeUtils.fromTime(tick);
        float diff = time - this.keyframe.getTick();

        if (Math.abs(diff) > 1.0E-6F)
        {
            boolean movedSelection = false;

            /* Diff is relative to the panel's (live) keyframe so the trackpad
             * absolute value stays the source of truth. */
            for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
            {
                if (sheet.selection.hasAny())
                {
                    sheet.setTickBy(diff, false);
                    movedSelection = true;
                }
            }

            if (!movedSelection || Math.abs(this.keyframe.getTick() - time) > 1.0E-5F)
            {
                /* While cache/submit is open, stay silent — submitKeyframes owns the undo. */
                boolean dirty = !movedSelection && !this.editor.hasKeyframeCache();

                this.keyframe.setTick(time, dirty);
            }
        }

        if (!this.tick.isActivelyEditing() && !this.tick.isDragging())
        {
            this.syncTickFromKeyframe();
            this.editor.triggerChange();
        }
    }

    public void setDuration(float value)
    {
        this.rebindKeyframe();
        this.editor.getGraph().setDuration(value, !this.editor.hasKeyframeCache());

        if (!this.duration.isActivelyEditing() && !this.duration.isDragging())
        {
            this.editor.triggerChange();
        }
    }

    public void setValue(Object value)
    {
        this.rebindKeyframe();
        /* Trackpads register cache on DragStart and submit on DragEnd. Notifying
         * here as well duplicated the same edit (visible Ctrl+Z that does nothing). */
        this.editor.getGraph().setValue(value, !this.editor.hasKeyframeCache());
        this.editor.triggerChange();
    }

    public void update()
    {
        this.rebindKeyframe();

        if (!this.tick.isActivelyEditing() && !this.tick.isDragging())
        {
            this.syncTickFromKeyframe();
        }

        if (!this.duration.isActivelyEditing() && !this.duration.isDragging())
        {
            this.duration.setValue(TimeUtils.toTime(this.keyframe.getDuration()));
        }
    }

    private void syncTickFromKeyframe()
    {
        this.rebindKeyframe();

        if (this.keyframe != null)
        {
            this.tick.setValue(TimeUtils.toTime(this.keyframe.getTick()));
        }
    }

    /**
     * {@link UIKeyframes#submitKeyframes()} replaces channel keyframe instances
     * via {@code copyKeyframes}. The factory must point at the live selected
     * keyframe or {@link #update()} will keep resetting inputs from an orphan.
     */
    @SuppressWarnings("unchecked")
    protected void rebindKeyframe()
    {
        if (this.keyframe == null || this.editor == null)
        {
            return;
        }

        if (this.keyframe.getParent() instanceof KeyframeChannel<?> channel
            && channel.getKeyframes().contains(this.keyframe))
        {
            return;
        }

        Keyframe<?> selected = this.editor.getGraph().getSelected();

        if (selected != null && selected.getFactory() == this.keyframe.getFactory())
        {
            this.keyframe = (Keyframe<T>) selected;

            return;
        }

        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);

        if (sheet != null && sheet.selection.hasAny())
        {
            List<Keyframe> selectedList = sheet.selection.getSelected();

            if (!selectedList.isEmpty())
            {
                this.keyframe = (Keyframe<T>) selectedList.get(0);
            }
        }
    }

    /**
     * Wire undo cache/submit and live rebind for a keyframe value trackpad.
     */
    protected void registerValueTrackpad(UITrackpad trackpad)
    {
        trackpad.getEvents().register(UITrackpadDragStartEvent.class, (e) ->
        {
            this.rebindKeyframe();
            this.editor.cacheKeyframes();
        });
        trackpad.getEvents().register(UITrackpadDragEndEvent.class, (e) ->
        {
            this.editor.submitKeyframes();
            this.rebindKeyframe();
        });
    }

    public static interface IUIKeyframeFactoryFactory <T>
    {
        public UIKeyframeFactory<T> create(Keyframe<T> keyframe, UIKeyframes editor);
    }
}
