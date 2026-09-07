package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.BossBarClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

public class UIBossBarClip extends UIClip<BossBarClip>
{
    private static final int COLOR_GROUP = Colors.LIGHTEST_GRAY & 0xffffff;
    private static final Color DEFAULT_COLOR = Color.rgba(BossBarClip.PRESET_ENDER_DRAGON);
    private static final Color DEFAULT_TEXT_COLOR = Color.rgba(0xFFFFFFFF);

    private final Map<String, Boolean> collapsed = new HashMap<>();

    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad width;
    public UITrackpad height;
    public UITrackpad zoom;
    public UITrackpad progress;
    public UIColor color;
    public UIColor textColor;
    public UITrackpad textSize;
    public UIElement colorPresets;
    public UIButton presetEnderDragon;
    public UIButton presetWither;
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    public UIBossBarClip(BossBarClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.title.callback = (t) ->
        {
            int tick = this.getClipTick();

            this.clip.text.insert(tick, t);
            this.clip.title.set(t);
            this.fillData();
        };

        this.x = this.createChannelTrackpad(this.clip.x, UIKeys.CAMERA_PANELS_BOSS_BAR_X, true, null, null);
        this.y = this.createChannelTrackpad(this.clip.y, UIKeys.CAMERA_PANELS_BOSS_BAR_Y, true, null, null);
        this.width = this.createChannelTrackpad(this.clip.width, UIKeys.CAMERA_PANELS_BOSS_BAR_WIDTH, false, 0.05F, null);
        this.height = this.createChannelTrackpad(this.clip.height, UIKeys.CAMERA_PANELS_BOSS_BAR_HEIGHT, false, 1F, null);
        this.zoom = this.createChannelTrackpad(this.clip.bossZoom, UIKeys.CAMERA_PANELS_BOSS_BAR_ZOOM, false, 0.05F, null);
        this.progress = this.createChannelTrackpad(this.clip.progress, UIKeys.CAMERA_PANELS_BOSS_BAR_PROGRESS, false, 0F, 1F);
        this.progress.values(0.1D, 0.01D, 1D);

        this.textColor = this.createColorChannel(this.clip.textColor);
        this.textSize = this.createChannelTrackpad(this.clip.textSize, UIKeys.CAMERA_PANELS_BOSS_BAR_TEXT_SIZE, false, 0.05F, null);

        this.color = this.createColorChannel(this.clip.color);
        this.color.withAlpha();

        this.presetEnderDragon = this.createPresetButton(BossBarClip.PRESET_ENDER_DRAGON, UIKeys.CAMERA_PANELS_BOSS_BAR_PRESET_ENDER_DRAGON);
        this.presetWither = this.createPresetButton(BossBarClip.PRESET_WITHER, UIKeys.CAMERA_PANELS_BOSS_BAR_PRESET_WITHER);
        this.presetEnderDragon.w(1F);
        this.presetWither.w(1F);

        this.colorPresets = UI.row(this.presetEnderDragon, this.presetWither);

        this.keyframes = this.createKeyframeEditor("boss_bar_keyframes");

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    private UIButton createPresetButton(int color, IKey label)
    {
        UIButton button = new UIButton(label, (b) ->
        {
            int tick = this.getClipTick();

            this.clip.color.insert(tick, Color.rgba(color));
            this.fillData();
        });

        button.color(color).background(true).h(20);

        return button;
    }

    private UITrackpad createChannelTrackpad(KeyframeChannel<Double> channel, IKey tooltip, boolean integer, Float min, Float max)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            int tick = this.getClipTick();

            channel.insert(tick, v.doubleValue());
            this.fillData();
        });

        if (integer)
        {
            trackpad.integer();
        }

        if (min != null)
        {
            if (max != null)
            {
                trackpad.limit(min, max);
            }
            else
            {
                trackpad.limit(min);
            }
        }

        if (tooltip != null)
        {
            trackpad.tooltip(tooltip);
        }

        return trackpad;
    }

    private UIColor createColorChannel(KeyframeChannel<Color> channel)
    {
        return new UIColor((c) ->
        {
            int tick = this.getClipTick();

            channel.insert(tick, Color.rgba(c));
            this.fillData();
        });
    }

    private UIKeyframeEditor createKeyframeEditor(String undoId)
    {
        UIKeyframeEditor editor = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));

        editor.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, editor.view, (Clips) this.clip.getParent(), this.clip.tick.get(), this.clip);
        });
        editor.view.duration(() -> this.clip.duration.get());
        editor.setUndoId(undoId);

        return editor;
    }

    private int getClipTick()
    {
        return MathHelper.clamp(this.editor.getCursor() - this.clip.tick.get(), 0, this.clip.duration.get());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_BOSS_BAR_TITLE, this.textColor, this.textSize));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_BOSS_BAR_POSITION, UI.row(this.x, this.y)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_BOSS_BAR_SIZE, UI.row(this.width, this.height), this.zoom, this.progress));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_BOSS_BAR_COLOR, this.color, this.colorPresets));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_KEYFRAMES, this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        /* setText() moves the caret to the start — skip while the user is typing. */
        if (!this.title.isFocused())
        {
            this.title.setText(this.getStringValue(this.clip.text, this.clip.title.get()));
        }

        this.x.setValue(this.getDoubleValue(this.clip.x, 0D));
        this.y.setValue(this.getDoubleValue(this.clip.y, 100D));
        this.width.setValue(this.getDoubleValue(this.clip.width, 1D));
        this.height.setValue(this.getDoubleValue(this.clip.height, BossBarClip.DEFAULT_HEIGHT));
        this.zoom.setValue(this.getDoubleValue(this.clip.bossZoom, BossBarClip.DEFAULT_ZOOM));
        this.progress.setValue(this.getDoubleValue(this.clip.progress, 1D));
        this.color.setColor(this.getColorValue(this.clip.color, DEFAULT_COLOR).getARGBColor());
        this.textColor.setColor(this.getColorValue(this.clip.textColor, DEFAULT_TEXT_COLOR).getARGBColor());
        this.textSize.setValue(this.getDoubleValue(this.clip.textSize, BossBarClip.DEFAULT_TEXT_SIZE));

        /* Avoid rebuilding keyframe sheets on every cursor scrub — only when empty. */
        if (this.keyframes.view.getGraph().getSheets().isEmpty())
        {
            this.rebuildChannels();
        }
        else
        {
            this.updateTrackTitles();
        }
    }

    private void rebuildChannels()
    {
        UIKeyframes view = this.keyframes.view;

        view.removeAllSheets();

        this.addGroup(view, "title", UIKeys.CAMERA_PANELS_BOSS_BAR_TITLE, BossBarClip.PRESET_ENDER_DRAGON,
            new KeyframeChannel[] {this.clip.text, this.clip.textColor, this.clip.textSize},
            new int[] {Colors.WHITE, Colors.WHITE, Colors.CYAN},
            1);

        this.addGroup(view, "position", UIKeys.CAMERA_PANELS_BOSS_BAR_POSITION, Colors.YELLOW,
            new KeyframeChannel[] {this.clip.x, this.clip.y},
            new int[] {Colors.YELLOW, Colors.YELLOW},
            1);

        this.addGroup(view, "bar", UIKeys.CAMERA_PANELS_BOSS_BAR_BAR, BossBarClip.PRESET_ENDER_DRAGON,
            new KeyframeChannel[] {this.clip.width, this.clip.height, this.clip.bossZoom, this.clip.progress, this.clip.color},
            new int[] {Colors.CYAN, Colors.MAGENTA, Colors.GREEN, Colors.RED, BossBarClip.PRESET_ENDER_DRAGON},
            1);

        this.keyframes.view.getGraph().clearSelection();
        this.updateTrackTitles();
    }

    private void addGroup(UIKeyframes view, String key, IKey title, int color, KeyframeChannel[] channels, int[] colors, int level)
    {
        boolean expanded = !this.collapsed.getOrDefault(key, false);

        UIKeyframeSheet header = UIKeyframeSheet.groupHeader(
            "__boss_bar__" + key,
            title,
            COLOR_GROUP,
            key,
            expanded,
            () ->
            {
                this.collapsed.put(key, !this.collapsed.getOrDefault(key, false));
                this.rebuildChannels();
            }
        );

        header.level = level;
        view.addSheet(header);

        if (channels != null && expanded)
        {
            for (int i = 0; i < channels.length; i++)
            {
                UIKeyframeSheet sheet = new UIKeyframeSheet(colors[i % colors.length], false, channels[i], null);

                sheet.level = level + 1;
                sheet.groupKey = key;
                view.addSheet(sheet);
            }
        }
    }

    private String getStringValue(KeyframeChannel<String> channel, String fallback)
    {
        int tick = this.getClipTick();

        if (channel.isEmpty())
        {
            return fallback;
        }

        return channel.interpolate(tick, fallback);
    }

    private double getDoubleValue(KeyframeChannel<Double> channel, double fallback)
    {
        int tick = this.getClipTick();

        if (channel.isEmpty())
        {
            return fallback;
        }

        return channel.interpolate(tick);
    }

    private Color getColorValue(KeyframeChannel<Color> channel, Color fallback)
    {
        int tick = this.getClipTick();

        if (channel.isEmpty())
        {
            return fallback;
        }

        return channel.interpolate(tick, fallback);
    }

    private void updateTrackTitles()
    {
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            if (sheet.groupHeader)
            {
                continue;
            }

            sheet.title = this.getTrackTitle(sheet.id);
        }
    }

    private IKey getTrackTitle(String id)
    {
        if (id != null && id.startsWith("__boss_bar__"))
        {
            return switch (id.substring("__boss_bar__".length()))
            {
                case "title" -> UIKeys.CAMERA_PANELS_BOSS_BAR_TITLE;
                case "position" -> UIKeys.CAMERA_PANELS_BOSS_BAR_POSITION;
                case "bar" -> UIKeys.CAMERA_PANELS_BOSS_BAR_BAR;
                default -> IKey.constant(id);
            };
        }

        return switch (id)
        {
            case "text" -> UIKeys.CAMERA_PANELS_TITLE;
            case "text_color" -> UIKeys.CAMERA_PANELS_BOSS_BAR_TEXT_COLOR;
            case "text_size" -> UIKeys.CAMERA_PANELS_BOSS_BAR_TEXT_SIZE;
            case "x" -> UIKeys.CAMERA_PANELS_BOSS_BAR_X;
            case "y" -> UIKeys.CAMERA_PANELS_BOSS_BAR_Y;
            case "width" -> UIKeys.CAMERA_PANELS_BOSS_BAR_WIDTH;
            case "height" -> UIKeys.CAMERA_PANELS_BOSS_BAR_HEIGHT;
            case "boss_zoom" -> UIKeys.CAMERA_PANELS_BOSS_BAR_ZOOM;
            case "progress" -> UIKeys.CAMERA_PANELS_BOSS_BAR_PROGRESS;
            case "color" -> UIKeys.CAMERA_PANELS_BOSS_BAR_COLOR;
            default -> IKey.constant(id);
        };
    }

    @Override
    protected UIKeyframeEditor resolveClipEmbeddableView(String undoId)
    {
        return undoId.equals(this.keyframes.getUndoId()) ? this.keyframes : null;
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if ("boss_bar_keyframes".equals(data.getString("embed")))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", "boss_bar_keyframes");
        }
    }
}
