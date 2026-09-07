package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.value.ValueKeyCombo;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueFormEditorGizmoToolbar;
import mchorse.bbs_mod.settings.values.ui.ValueGizmoToolbar;
import mchorse.bbs_mod.settings.values.ui.ValueLanguage;
import mchorse.bbs_mod.settings.values.ui.ValueVideoSettings;
import mchorse.bbs_mod.settings.values.ui.ValueViewportToolbar;
import mchorse.bbs_mod.text.RtlFontManager;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.GizmoToolbarButtons;
import mchorse.bbs_mod.ui.film.ViewportToolbarButtons;
import mchorse.bbs_mod.ui.forms.editors.FormEditorGizmoToolbarButtons;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFontPickerOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UILabelOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.CustomFontManager;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.OS;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;

import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class UIValueMap
{
    public static Map<Class<? extends BaseValue>, IUIValueFactory<? extends BaseValue>> factories = new HashMap<>();

    static
    {
        register(ValueBoolean.class, (value, ui) ->
        {
            if (value == BBSSettings.modelEditorAltHoverMultipleColors)
            {
                UIToggle toggle = UIValueFactory.booleanUINoLabel(value, (t) -> BBSModClient.applyModelEditorHoverLive());

                toggle.w(18);

                return Arrays.asList(UIValueFactory.column(toggle, value));
            }

            if (value == BBSSettings.linkUiScaleToGame)
            {
                UIToggle toggle = UIValueFactory.booleanUINoLabel(value, (t) -> BBSModClient.applyUIScaleLive());

                toggle.w(18);

                return Arrays.asList(UIValueFactory.column(toggle, value));
            }

            UIToggle toggle = UIValueFactory.booleanUINoLabel(value, null);

            toggle.w(18);

            return Arrays.asList(UIValueFactory.column(toggle, value));
        });

        register(ValueDouble.class, (value, ui) ->
        {
            UITrackpad trackpad = UIValueFactory.doubleUI(value, null);

            trackpad.w(90);

            return Arrays.asList(UIValueFactory.column(trackpad, value));
        });

        register(ValueFloat.class, (value, ui) ->
        {
            if (value == BBSSettings.userIntefaceScale)
            {
                UITrackpad trackpad = UIValueFactory.floatUI(value, (v) -> BBSModClient.applyUIScaleLive());

                trackpad.limit(0.1F, 4F).w(90);

                return Arrays.asList(UIValueFactory.column(trackpad, value));
            }

            if (value == BBSSettings.modelEditorHoverOpacity)
            {
                UITrackpad trackpad = UIValueFactory.floatUI(value, (v) -> BBSModClient.applyModelEditorHoverLive());

                trackpad.w(90);

                return Arrays.asList(UIValueFactory.column(trackpad, value));
            }

            if (value == BBSSettings.modelEditorAltHoverOpacity)
            {
                UITrackpad trackpad = UIValueFactory.floatUI(value, (v) -> BBSModClient.applyModelEditorHoverLive());

                trackpad.w(90);

                return Arrays.asList(UIValueFactory.column(trackpad, value));
            }

            if (value == BBSSettings.keyframePreviewOpacity)
            {
                UITrackpad trackpad = UIValueFactory.floatUI(value, null);

                trackpad.w(90);

                return Arrays.asList(UIValueFactory.column(trackpad, value));
            }

            UITrackpad trackpad = UIValueFactory.floatUI(value, null);

            trackpad.w(90);

            if (value == BBSSettings.uiFontSize)
            {
                trackpad.w(90);

                value.postCallback((changed, flag) ->
                {
                    if (!UISettingsOverlayPanel.isDeferringLiveSettings())
                    {
                        CustomFontManager.invalidate();
                        RtlFontManager.invalidate();
                    }
                });

                return Arrays.asList(UIValueFactory.column(trackpad, value));
            }

            return Arrays.asList(UIValueFactory.column(trackpad, value));
        });

        register(ValueInt.class, (value, ui) ->
        {
            if (value == BBSSettings.defaultKeyframeShape)
            {
                KeyframeShape currentShape = KeyframeShape.SQUARE;
                int currentIndex = value.get();
                KeyframeShape[] shapes = KeyframeShape.values();

                if (currentIndex >= 0 && currentIndex < shapes.length)
                {
                    currentShape = shapes[currentIndex];
                }

                IKeyframeShapeRenderer currentRenderer = KeyframeShapeRenderers.SHAPES.get(currentShape);
                UIButton button = new UIButton(currentRenderer.getLabel(), (b) ->
                {
                    ui.getContext().replaceContextMenu((menu) ->
                    {
                        int selected = value.get();

                        for (KeyframeShape shape : KeyframeShape.values())
                        {
                            IKeyframeShapeRenderer shapeRenderer = KeyframeShapeRenderers.SHAPES.get(shape);

                            menu.action(shapeRenderer.getIcon(), shapeRenderer.getLabel(), shape.ordinal() == selected, () ->
                            {
                                value.set(shape.ordinal());
                                b.label = shapeRenderer.getLabel();
                            });
                        }
                    });
                });

                button.w(90);

                return Arrays.asList(UIValueFactory.column(button, value));
            }

            if (value == BBSSettings.gizmoDefaultMode)
            {
                UIIcon translate = new UIIcon(Icons.ALL_DIRECTIONS, (b) ->
                {
                    value.set(0);
                    UIUtils.playClick();
                });
                UIIcon scale = new UIIcon(Icons.SCALE, (b) ->
                {
                    value.set(1);
                    UIUtils.playClick();
                });
                UIIcon rotate = new UIIcon(Icons.ARC, (b) ->
                {
                    value.set(2);
                    UIUtils.playClick();
                });
                UIIcon combined = new UIIcon(Icons.SHAPES, (b) ->
                {
                    value.set(3);
                    UIUtils.playClick();
                });
                UIIcon top = new UIIcon(Icons.SPHERE, (b) ->
                {
                    value.set(4);
                    UIUtils.playClick();
                });

                translate.tooltip(UIKeys.FILM_GIZMO_MOVE);
                scale.tooltip(UIKeys.FILM_GIZMO_SCALE);
                rotate.tooltip(UIKeys.FILM_GIZMO_ROTATE);
                combined.tooltip(UIKeys.FILM_GIZMO_COMBINED);
                top.tooltip(UIKeys.FILM_GIZMO_TOP);

                int activeBg = Colors.A50 | Colors.BLUE;

                translate.activeBackground(activeBg);
                scale.activeBackground(activeBg);
                rotate.activeBackground(activeBg);
                combined.activeBackground(activeBg);
                top.activeBackground(activeBg);

                Runnable syncActive = () ->
                {
                    int mode = MathUtils.clamp(value.get(), 0, 4);

                    translate.active(mode == 0);
                    scale.active(mode == 1);
                    rotate.active(mode == 2);
                    combined.active(mode == 3);
                    top.active(mode == 4);
                };

                value.postCallback((changed, flag) -> syncActive.run());
                syncActive.run();

                UIElement row = UI.row(2, translate, scale, rotate, combined, top);

                row.w(90);

                return Arrays.asList(UIValueFactory.column(row, value));
            }

            if (value == BBSSettings.defaultInterpolation || value == BBSSettings.defaultModelInterpolation || value == BBSSettings.defaultPathInterpolation || value == BBSSettings.defaultCameraKeyframeInterpolation)
            {
                List<IKey> labels = value.getLabels();
                int currentIndex = value.get();
                IKey label = UIKeys.CAMERA_PANELS_INTERPOLATION;

                if (labels != null && !labels.isEmpty())
                {
                    if (currentIndex < 0 || currentIndex >= labels.size())
                    {
                        currentIndex = 0;
                    }

                    label = labels.get(currentIndex);
                }

                UIButton button = new UIButton(label, (b) ->
                {
                    Interpolation interpolation = new Interpolation("default", Interpolations.MAP);
                    int index = value.get();
                    int i = 0;

                    for (IInterp interp : Interpolations.MAP.values())
                    {
                        if (i == index)
                        {
                            interpolation.setInterp(interp);
                            break;
                        }

                        i++;
                    }

                    UIInterpolationContextMenu menu = new UIInterpolationContextMenu(interpolation).callback(() ->
                    {
                        IInterp selected = interpolation.getInterp();
                        int idx = 0;

                        for (IInterp interp : Interpolations.MAP.values())
                        {
                            if (interp == selected || interp.equals(selected))
                            {
                                value.set(idx);

                                if (labels != null && !labels.isEmpty())
                                {
                                    if (idx < 0 || idx >= labels.size())
                                    {
                                        idx = 0;
                                    }

                                    b.label = labels.get(idx);
                                }

                                break;
                            }

                            idx++;
                        }
                    });

                    ui.getContext().replaceContextMenu(menu);
                });

                button.w(90);

                return Arrays.asList(UIValueFactory.column(button, value));
            }

            if (value.getSubtype() == ValueInt.Subtype.COLOR || value.getSubtype() == ValueInt.Subtype.COLOR_ALPHA)
            {
                /* Hover highlight color should take effect the moment it's changed, without
                 * requiring Apply/reload (the renderer reads the "applied" snapshot). */
                UIColor color = value == BBSSettings.modelEditorHoverColor || value == BBSSettings.modelEditorAltHoverColor
                    ? UIValueFactory.colorUI(value, (c) -> BBSModClient.applyModelEditorHoverLive())
                    : UIValueFactory.colorUI(value, null);

                color.w(90);

                return Arrays.asList(UIValueFactory.column(color, value));
            }
            else if (value.getSubtype() == ValueInt.Subtype.MODES)
            {
                UICirculate button = new UICirculate(null);

                for (IKey key : value.getLabels())
                {
                    button.addLabel(key);
                }

                button.callback = (b) -> value.set(button.getValue());
                button.setValue(value.get());
                button.w(90);

                return Arrays.asList(UIValueFactory.column(button, value));
            }

            UITrackpad trackpad = UIValueFactory.intUI(value, null);

            trackpad.w(90);

            return Arrays.asList(UIValueFactory.column(trackpad, value));
        });

        register(ValueLanguage.class, (value, ui) ->
        {
            Supplier<IKey> getLangLabel = () -> {
                for (Label<String> label : BBSModClient.getL10n().getSupportedLanguageLabels())
                {
                    if (label.value.equals(value.get()))
                    {
                        return label.title;
                    }
                }
                return UIKeys.LANGUAGE_PICK;
            };

            UIButton button = new UIButton(getLangLabel.get(), (b) ->
            {
                List<Label<String>> labels = BBSModClient.getL10n().getSupportedLanguageLabels();
                UILabelOverlayPanel<String> panel = new UILabelOverlayPanel<>(UIKeys.LANGUAGE_PICK_TITLE, labels, (str) -> value.set(str.value));

                panel.set(value.get());
                UIOverlay.addOverlay(ui.getContext(), panel);
            });

            value.postCallback((changed, flag) -> button.label = getLangLabel.get());

            button.w(150);

            UIText credits = new UIText().text(UIKeys.LANGUAGE_CREDITS).updates();

            return Arrays.asList(UIValueFactory.column(button, value), credits.marginBottom(8));
        });

        register(ValueLink.class, (value, ui) ->
        {
            UIButton pick = new UIButton(UIKeys.TEXTURE_PICK_TEXTURE, (button) ->
            {
                UITexturePicker.open(ui.getContext(), value.get(), value::set);
            });

            pick.w(90);

            return Arrays.asList(UIValueFactory.column(pick, value));
        });

        register(ValueString.class, (value, ui) ->
        {
            UITextbox textbox = UIValueFactory.stringUI(value, null);

            textbox.w(90);

            if (value == BBSSettings.uiFont)
            {
                textbox.w(150);

                UIButton browse = new UIButton(UIKeys.SETTINGS_FONT_BROWSE, (b) -> pickFontFile(value, textbox));
                UIButton reset = new UIButton(UIKeys.SETTINGS_FONT_RESET, (b) ->
                {
                    value.set("");
                    textbox.setText("");
                    CustomFontManager.invalidate();
                    RtlFontManager.invalidate();
                });

                browse.tooltip(UIKeys.SETTINGS_FONT_BROWSE);

                value.postCallback((changed, flag) ->
                {
                    if (!UISettingsOverlayPanel.isDeferringLiveSettings())
                    {
                        CustomFontManager.invalidate();
                        RtlFontManager.invalidate();
                    }
                });

                return Arrays.asList(UIValueFactory.column(textbox, value), UI.row(4, browse, reset));
            }

            if (value == BBSSettings.videoEncoderPath && OS.CURRENT == OS.WINDOWS)
            {
                textbox.context((menu) ->
                {
                    menu.action(Icons.SEARCH, UIKeys.GENERAL_FFMPEG_FIND, () ->
                    {
                        textbox.getContext().replaceContextMenu((submenu) ->
                        {
                            File[] files = File.listRoots();
                            File file = files.length == 0 ? new File("C:\\") : files[0];
                            Optional<Path> ffmpeg = FFMpegUtils.findFFMpeg(file.toPath());

                            if (ffmpeg.isPresent())
                            {
                                Path path = ffmpeg.get();
                                String pathString = path.toAbsolutePath().toString();

                                submenu.action(Icons.VIDEO_CAMERA, IKey.constant(pathString), () ->
                                {
                                    textbox.setText(pathString);
                                    value.set(pathString);
                                });
                            }
                        });
                    });
                });
            }

            return Arrays.asList(UIValueFactory.column(textbox, value));
        });

        register(ValueKeyCombo.class, (value, ui) ->
        {
            UILabel label = UI.label(value.get().label, 0).labelAnchor(0, 0.5F);
            UIKeybind keybind = new UIKeybind(value::set).mouse().escape();

            keybind.setKeyCombo(value.get());
            keybind.w(100);

            return Arrays.asList(UI.row(label, keybind).tooltip(value.get().label));
        });

        register(ValueVideoSettings.class, (value, ui) ->
        {
            List<UIElement> list = new ArrayList<>();

            UITextbox arguments = UIValueFactory.stringUI(value.arguments, null);
            arguments.w(180);
            list.add(customColumn(arguments, UIKeys.VIDEO_SETTINGS_ARGS, IKey.raw("")));

            UITextbox argumentsAudio = UIValueFactory.stringUI(value.argumentsAudio, null);
            argumentsAudio.w(180);
            list.add(customColumn(argumentsAudio, UIKeys.VIDEO_SETTINGS_AUDIO_ARGS, IKey.raw("")));

            UIToggle audio = new UIToggle(UIKeys.VIDEO_SETTINGS_AUDIO, value.audio.get(), (b) ->
            {
                value.audio.set(b.getValue());
            });
            audio.tooltip(UIKeys.VIDEO_SETTINGS_AUDIO_TOOLTIP);
            audio.w(1F).h(20);
            list.add(audio);

            UIToggle audioEnvironment = new UIToggle(UIKeys.VIDEO_SETTINGS_AUDIO_ENVIRONMENT, value.audioEnvironment.get(), (b) ->
            {
                value.audioEnvironment.set(b.getValue());
            });
            audioEnvironment.tooltip(UIKeys.VIDEO_SETTINGS_AUDIO_ENVIRONMENT_TOOLTIP);
            audioEnvironment.w(1F).h(20);
            list.add(audioEnvironment);

            UIToggle audioSeparateFile = new UIToggle(UIKeys.VIDEO_SETTINGS_AUDIO_SEPARATE_FILE, value.audioSeparateFile.get(), (b) ->
            {
                value.audioSeparateFile.set(b.getValue());
            });
            audioSeparateFile.tooltip(UIKeys.VIDEO_SETTINGS_AUDIO_SEPARATE_FILE_TOOLTIP);
            audioSeparateFile.w(1F).h(20);
            list.add(audioSeparateFile);

            UITrackpad width = UIValueFactory.intUI(value.width, null);
            value.width.postCallback((changed, flag) -> width.setValue(value.width.get()));
            width.w(90);
            width.tooltip(UIKeys.VIDEO_SETTINGS_WIDTH);

            UITrackpad height = UIValueFactory.intUI(value.height, null);
            value.height.postCallback((changed, flag) -> height.setValue(value.height.get()));
            height.w(90);
            height.tooltip(UIKeys.VIDEO_SETTINGS_HEIGHT);

            UIIcon swapResolution = new UIIcon(Icons.EXCHANGE, (b) ->
            {
                int w = value.width.get();
                int h = value.height.get();

                value.width.set(h);
                value.height.set(w);
            });
            swapResolution.tooltip(UIKeys.VIDEO_SETTINGS_SWAP_RESOLUTION);
            swapResolution.w(20).h(20);

            /* Stack the label above the inputs so long translations still fit the default settings width. */
            UIElement resolutionInputs = UI.row(4, 0, 20, width, swapResolution, height);

            resolutionInputs.w(1F);
            list.add(customColumn(resolutionInputs, UIKeys.VIDEO_SETTINGS_RESOLUTION, IKey.raw(""), true));

            UITrackpad frameRate = UIValueFactory.intUI(value.frameRate, null);
            frameRate.w(90);
            list.add(customColumn(frameRate, UIKeys.VIDEO_SETTINGS_FRAME_RATE, IKey.raw("")));

            UITrackpad warmupDelay = UIValueFactory.floatUI(value.warmupDelay, null);
            warmupDelay.w(90);
            list.add(customColumn(warmupDelay, UIKeys.VIDEO_SETTINGS_WARMUP_DELAY, UIKeys.VIDEO_SETTINGS_WARMUP_DELAY_TOOLTIP));

            UITrackpad motionBlur = UIValueFactory.intUI(value.motionBlur, null);
            motionBlur.w(90);
            list.add(customColumn(motionBlur, UIKeys.VIDEO_SETTINGS_MOTION_BLUR, UIKeys.VIDEO_SETTINGS_MOTION_BLUR_TOOLTIP));

            UITrackpad heldFrames = UIValueFactory.intUI(value.heldFrames, null);
            heldFrames.w(90);
            list.add(customColumn(heldFrames, UIKeys.VIDEO_SETTINGS_HELD_FRAMES, UIKeys.VIDEO_SETTINGS_HELD_FRAMES_TOOLTIP));

            UIToggle highQuality = new UIToggle(UIKeys.VIDEO_SETTINGS_HIGH_QUALITY, value.highQualityRender.get(), (b) ->
            {
                value.highQualityRender.set(b.getValue());
            });
            highQuality.tooltip(UIKeys.VIDEO_SETTINGS_HIGH_QUALITY_TOOLTIP);
            highQuality.w(1F).h(20);
            list.add(highQuality);

            UITrackpad settleTicks = UIValueFactory.intUI(value.highQualitySettleTicks, null);
            settleTicks.w(90);
            list.add(customColumn(settleTicks, UIKeys.VIDEO_SETTINGS_HIGH_QUALITY_SETTLE, UIKeys.VIDEO_SETTINGS_HIGH_QUALITY_SETTLE_TOOLTIP));

            UITextbox path = UIValueFactory.stringUI(value.path, null);
            path.w(140);
            list.add(customColumn(path, UIKeys.VIDEO_SETTINGS_PATH, IKey.raw("")));

            return list;
        });

        register(ValueViewportToolbar.class, (value, ui) ->
        {
            UIIconToolbarOrderEditor editor = new UIIconToolbarOrderEditor(value, ViewportToolbarButtons::getIcon, ViewportToolbarButtons::getTooltip, null);

            editor.w(1F);

            return Collections.singletonList(UIValueFactory.column(editor, value));
        });

        register(ValueGizmoToolbar.class, (value, ui) ->
        {
            UIIconToolbarOrderEditor editor = new UIIconToolbarOrderEditor(value, GizmoToolbarButtons::getIcon, GizmoToolbarButtons::getTooltip, null);

            editor.w(1F);

            return Collections.singletonList(UIValueFactory.column(editor, value));
        });

        register(ValueFormEditorGizmoToolbar.class, (value, ui) ->
        {
            UIIconToolbarOrderEditor editor = new UIIconToolbarOrderEditor(value, FormEditorGizmoToolbarButtons::getIcon, FormEditorGizmoToolbarButtons::getTooltip, null);

            editor.w(1F);

            return Collections.singletonList(UIValueFactory.column(editor, value));
        });
    }

    private static void pickFontFile(ValueString value, UITextbox textbox)
    {
        UIOverlay.addOverlay(textbox.getContext(), new UIFontPickerOverlayPanel((file) ->
        {
            String full = file.getAbsolutePath();

            value.set(full);
            textbox.setText(full);
            CustomFontManager.invalidate();
            RtlFontManager.invalidate();
        }), 320, 240);
    }

    private static UIElement customColumn(UIElement control, IKey label, IKey tooltip)
    {
        return customColumn(control, label, tooltip, false);
    }

    private static UIElement customColumn(UIElement control, IKey label, IKey tooltip, boolean stack)
    {
        UIElement element = new UIElement();
        control.removeTooltip();

        String comment = tooltip.get();
        boolean hasComment = !comment.isEmpty()
            && !comment.startsWith("bbs.settings.")
            && !comment.startsWith("cml.settings.")
            && (BBSSettings.hideSettingDescriptions == null || !BBSSettings.hideSettingDescriptions.get());

        if (hasComment || stack)
        {
            UILabel titleLabel = UI.label(label, 0).labelAnchor(0, 0.5F);
            titleLabel.relative(element).w(1F).h(11);

            element.column(3).vertical().padding(0).height(0);

            if (hasComment)
            {
                UIText desc = new UIText(tooltip)
                    .color(0xFF777788, true);
                desc.relative(element).w(1F);
                element.add(titleLabel, desc.marginTop(1), control.marginTop(2));
            }
            else
            {
                element.add(titleLabel, control.marginTop(2).w(1F));
            }
        }
        else
        {
            element.row(0).preferred(0).height(20);
            element.add(UI.label(label, 0).labelAnchor(0, 0.5F), control);
            control.marginTop(0);
        }

        return element;
    }

    public static <T extends BaseValue> void register(Class<T> clazz, IUIValueFactory<T> factory)
    {
        factories.put(clazz, factory);
    }

    public static <T extends BaseValue> List<UIElement> create(T value, UIElement element)
    {
        IUIValueFactory<T> factory = (IUIValueFactory<T>) factories.get(value.getClass());

        return factory == null ? Collections.emptyList() : factory.create(value, element);
    }

    public static interface IUIValueFactory <T extends BaseValue>
    {
        public List<UIElement> create(T value, UIElement element);
    }
}
