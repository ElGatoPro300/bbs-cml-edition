package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.ChromaSkyCurveSettings;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UILabelListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UICurveClip extends UIClip<CurveClip>
{
    private static final String CHROMA_SKY_ADD_ID = "__chroma_sky__";
    private static final IKey CHROMA_SKY_TITLE = UIKeys.CAMERA_PANELS_CURVES_CHROMA_SKY;

    public UIKeyframeEditor keyframes;
    public UIButton edit;

    public UICurveClip(CurveClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    public static void offerCurveKeys(UIContext context, List<String> existing, Consumer<String> callback)
    {
        List<Label<String>> list = new ArrayList<>();
        String language = BBSModClient.getLanguageKey();
        Map<String, String> languageMap = BBSRendering.getShadersLanguageMap(language);

        for (ShaderCurves.ShaderVariable value : ShaderCurves.variableMap.values())
        {
            if (existing.contains(value.name)
                || existing.contains(CurveClip.SHADER_CURVES_PREFIX + value.name)
                || ShaderCurves.SHADER_SHADOW_OPACITY.equals(value.name))
            {
                continue;
            }

            String key = value.name;
            String newKey = languageMap.get("option." + key);

            if (newKey != null)
            {
                key = newKey + " (" + key + ")";
            }

            list.add(new Label<>(IKey.constant(key), CurveClip.SHADER_CURVES_PREFIX + value.name));
        }

        if (!existing.contains(ShaderCurves.BRIGHTNESS)) list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_BRIGHTNESS, ShaderCurves.BRIGHTNESS));
        if (!existing.contains(ShaderCurves.SUN_ROTATION)) list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_TIME_OF_DAY, ShaderCurves.SUN_ROTATION));
        if (!existing.contains(ShaderCurves.SUN_PATH_ROTATION)) list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_SUN_PATH_ROTATION, ShaderCurves.SUN_PATH_ROTATION));
        if (!existing.contains(ShaderCurves.WEATHER)) list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_WEATHER, ShaderCurves.WEATHER));
        if (!existing.contains(ShaderCurves.SHADER_SHADOW_OPACITY)
            && !existing.contains(CurveClip.SHADER_CURVES_PREFIX + ShaderCurves.SHADER_SHADOW_OPACITY))
        {
            list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_SHADER_SHADOW_OPACITY, ShaderCurves.SHADER_SHADOW_OPACITY));
        }
        if (!existing.contains(CHROMA_SKY_ADD_ID)) list.add(new Label<>(CHROMA_SKY_TITLE, CHROMA_SKY_ADD_ID));

        UILabelListOverlayPanel panel = new UILabelListOverlayPanel(UIKeys.CAMERA_PANELS_PICK_KEY, list, callback);

        panel.strings.list.sort();

        UIOverlay.addOverlay(context, panel, 0.9F, 0.5F);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, this.keyframes.view, (Clips) this.clip.getParent(), Math.round(this.clip.tick.get()), this.clip);
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("curve_keyframes");

        this.keyframes.view.context((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.CAMERA_PANELS_CURVE_ADD, () ->
            {
                List<String> existing = new ArrayList<>();

                for (KeyframeChannel<Double> channel : this.clip.channels.getChannels())
                {
                    existing.add(channel.getId());
                }

                offerCurveKeys(this.getContext(), existing, (s) ->
                {
                    if (CHROMA_SKY_ADD_ID.equals(s))
                    {
                        if (this.clip.chromaSky.isEmpty())
                        {
                            this.clip.chromaSky.insert(0F, new ChromaSkyCurveSettings());
                        }
                    }
                    else
                    {
                        this.clip.channels.addChannel(s);
                    }

                    this.fillData();
                });
            }).order(-3);

            UIKeyframeSheet sheet = this.keyframes.view.getDopeSheet().getSheet(this.getContext().mouseY);

            if (sheet != null)
            {
                menu.action(Icons.REMOVE, UIKeys.CAMERA_PANELS_CURVE_REMOVE, Colors.RED, () ->
                {
                    if (sheet.channel == this.clip.chromaSky)
                    {
                        this.clip.chromaSky.removeAll();
                    }
                    else
                    {
                        this.clip.channels.removeChannel(sheet.channel);
                    }

                    this.fillData();
                });
            }
        });

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    private void addChannel(KeyframeChannel<?> channel, IKey title, int color)
    {
        this.keyframes.view.addSheet(new UIKeyframeSheet(channel.getId(), title, color, false, channel, null));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:curve"), this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.keyframes.view.removeAllSheets();

        if (!this.clip.chromaSky.isEmpty())
        {
            this.addChannel(this.clip.chromaSky, CHROMA_SKY_TITLE, Colors.CYAN);
        }

        List<KeyframeChannel<Double>> channels = new ArrayList<>(this.clip.channels.getChannels());

        channels.sort((a, b) ->
        {
            int orderA = getChannelOrder(a.getId());
            int orderB = getChannelOrder(b.getId());

            if (orderA != orderB)
            {
                return Integer.compare(orderA, orderB);
            }

            return a.getId().compareToIgnoreCase(b.getId());
        });

        for (KeyframeChannel<Double> channel : channels)
        {
            this.addChannel(channel, getChannelTitle(channel.getId()), getChannelColor(channel.getId()));
        }
    }

    private static int getChannelOrder(String id)
    {
        if (ShaderCurves.SUN_ROTATION.equals(id))
        {
            return 1;
        }

        if (ShaderCurves.SUN_PATH_ROTATION.equals(id))
        {
            return 2;
        }

        if (ShaderCurves.BRIGHTNESS.equals(id))
        {
            return 3;
        }

        if (ShaderCurves.WEATHER.equals(id))
        {
            return 4;
        }

        if (ShaderCurves.SHADER_SHADOW_OPACITY.equals(id)
            || id.equals(CurveClip.SHADER_CURVES_PREFIX + ShaderCurves.SHADER_SHADOW_OPACITY))
        {
            return 5;
        }

        return 100;
    }

    private static int getChannelColor(String id)
    {
        if (ShaderCurves.BRIGHTNESS.equals(id))
        {
            return 0xd4b23a;
        }

        if (ShaderCurves.SUN_ROTATION.equals(id))
        {
            return 0x3aa0ff;
        }

        if (ShaderCurves.SUN_PATH_ROTATION.equals(id))
        {
            return 0xff7b3a;
        }

        if (ShaderCurves.WEATHER.equals(id))
        {
            return 0x2f8f72;
        }

        if (ShaderCurves.SHADER_SHADOW_OPACITY.equals(id)
            || id.equals(CurveClip.SHADER_CURVES_PREFIX + ShaderCurves.SHADER_SHADOW_OPACITY))
        {
            return 0x6e7888;
        }

        return id.hashCode() & Colors.RGB;
    }

    private static IKey getChannelTitle(String id)
    {
        if (ShaderCurves.BRIGHTNESS.equals(id))
        {
            return UIKeys.CAMERA_PANELS_CURVES_BRIGHTNESS;
        }

        if (ShaderCurves.SUN_ROTATION.equals(id))
        {
            return UIKeys.CAMERA_PANELS_CURVES_TIME_OF_DAY;
        }

        if (ShaderCurves.SUN_PATH_ROTATION.equals(id))
        {
            return UIKeys.CAMERA_PANELS_CURVES_SUN_PATH_ROTATION;
        }

        if (ShaderCurves.WEATHER.equals(id))
        {
            return UIKeys.CAMERA_PANELS_CURVES_WEATHER;
        }

        if (ShaderCurves.SHADER_SHADOW_OPACITY.equals(id)
            || id.equals(CurveClip.SHADER_CURVES_PREFIX + ShaderCurves.SHADER_SHADOW_OPACITY))
        {
            return UIKeys.CAMERA_PANELS_CURVES_SHADER_SHADOW_OPACITY;
        }

        return IKey.constant(id);
    }

    @Override
    protected UIKeyframeEditor resolveClipEmbeddableView(String undoId)
    {
        return undoId.equals(this.keyframes.getUndoId()) ? this.keyframes : null;
    }
}
