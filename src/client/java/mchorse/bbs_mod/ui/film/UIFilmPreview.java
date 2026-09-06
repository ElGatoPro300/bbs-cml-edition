package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.clips.misc.VideoClip;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.video.VideoRenderer;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.values.ui.ValueGizmoToolbar;
import mchorse.bbs_mod.settings.values.ui.ValueViewportToolbar;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.controller.UIGizmoSizeContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIGizmoThicknessContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIGizmoTranslateSpeedContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIOnionSkinContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIViewportHideContextMenu;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarSettings;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIViewportStack;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4fStack;
import org.joml.Vector2i;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIFilmPreview extends UIElement
{
    public static final List<Consumer<UIFilmPreview>> extensions = new ArrayList<>();

    private static final int GIZMO_ICON_IDLE = Colors.setA(Colors.WHITE, 0.5F);
    private static final int GIZMO_ICON_HOVER = Colors.WHITE;

    private List<AudioClip> clips = new ArrayList<>();
    private File pendingThumbnail;
    private Runnable pendingThumbnailCallback;
    private UIFilmPanel panel;
    private boolean viewportButtonsHidden;
    private final List<UIIcon> viewportButtons = new ArrayList<>();
    private final Map<String, UIIcon> viewportButtonMap = new HashMap<>();
    private final Map<String, UIIcon> gizmoButtonMap = new HashMap<>();

    public UIElement icons;
    public UIElement gizmos;

    public UIIcon gizmoMove;
    public UIIcon gizmoScale;
    public UIIcon gizmoRotate;
    public UIIcon gizmoCombined;
    public UIIcon gizmoTop;
    public UIIcon gizmoSize;
    public UIIcon gizmoThickness;
    public UIIcon gizmoTranslateSpeed;
    public UIIcon onionSkin;
    public UIIcon hideOverlays;
    public UIIcon toggleShaders;
    public UIIcon plause;
    public UIIcon teleport;
    public UIIcon flight;
    public UIIcon control;
    public UIIcon perspective;
    public UIIcon recordReplay;
    public UIIcon recordVideo;
    public UIIcon renderQueue;
    public UIIcon restoreBlocks;
    public UIButton joinWorld;

    public UIFilmPreview(UIFilmPanel filmPanel)
    {
        this.panel = filmPanel;

        this.icons = UI.row(0, 0);
        this.icons.row().resize();
        this.icons.relative(this).x(0.5F).y(1F).anchor(0.5F, 1F);

        /* Gizmo transform-mode buttons (move / scale / rotate), horizontal along the
           top letterbox so they sit in the empty bar above the camera render. */
        this.gizmoMove = this.createGizmoButton(Icons.ALL_DIRECTIONS, Gizmo.Mode.TRANSLATE, UIKeys.FILM_GIZMO_MOVE);
        this.gizmoScale = this.createGizmoButton(Icons.SCALE, Gizmo.Mode.SCALE, UIKeys.FILM_GIZMO_SCALE);
        this.gizmoRotate = this.createGizmoButton(Icons.ARC, Gizmo.Mode.ROTATE, UIKeys.FILM_GIZMO_ROTATE);
        this.gizmoCombined = this.createGizmoButton(Icons.SHAPES, Gizmo.Mode.COMBINED, UIKeys.FILM_GIZMO_COMBINED);
        this.gizmoTop = this.createGizmoButton(Icons.SPHERE, Gizmo.Mode.TOP, UIKeys.FILM_GIZMO_TOP);

        /* Gizmo size popup: opens a small trackpad menu bound to BBSSettings.axesScale. */
        this.gizmoSize = new UIIcon(Icons.MAXIMIZE, (b) ->
            this.getContext().replaceContextMenu(new UIGizmoSizeContextMenu())
        );
        this.gizmoSize.tooltip(UIKeys.FILM_GIZMO_SIZE);
        this.styleGizmoToolbarIcon(this.gizmoSize);

        this.gizmoThickness = new UIIcon(Icons.LINE, (b) ->
            this.getContext().replaceContextMenu(new UIGizmoThicknessContextMenu())
        );
        this.gizmoThickness.tooltip(UIKeys.FILM_GIZMO_THICKNESS);
        this.styleGizmoToolbarIcon(this.gizmoThickness);

        this.gizmoTranslateSpeed = new UIIcon(Icons.FORWARD, (b) ->
            this.getContext().replaceContextMenu(new UIGizmoTranslateSpeedContextMenu())
        );
        this.gizmoTranslateSpeed.tooltip(UIKeys.FILM_GIZMO_TRANSLATE_SPEED);
        this.styleGizmoToolbarIcon(this.gizmoTranslateSpeed);

        this.gizmoButtonMap.put(ValueGizmoToolbar.MOVE, this.gizmoMove);
        this.gizmoButtonMap.put(ValueGizmoToolbar.SCALE, this.gizmoScale);
        this.gizmoButtonMap.put(ValueGizmoToolbar.ROTATE, this.gizmoRotate);
        this.gizmoButtonMap.put(ValueGizmoToolbar.COMBINED, this.gizmoCombined);
        this.gizmoButtonMap.put(ValueGizmoToolbar.TOP, this.gizmoTop);
        this.gizmoButtonMap.put(ValueGizmoToolbar.SIZE, this.gizmoSize);
        this.gizmoButtonMap.put(ValueGizmoToolbar.THICKNESS, this.gizmoThickness);
        this.gizmoButtonMap.put(ValueGizmoToolbar.TRANSLATE_SPEED, this.gizmoTranslateSpeed);

        this.gizmos = new UIElement();
        this.gizmos.relative(this).x(4).y(4);
        this.rebuildGizmoToolbar();
        BBSSettings.editorGizmoToolbar.postCallback((v, f) -> this.rebuildGizmoToolbar());
        BBSSettings.editorGizmoToolbarHorizontal.postCallback((v, f) -> this.rebuildGizmoToolbar());
        this.add(this.gizmos);


        /* Preview buttons */
        this.onionSkin = new UIIcon(Icons.ONION_SKIN, (b) -> this.openOnionSkin());
        this.onionSkin.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE);
        this.hideOverlays = new UIIcon(() -> BBSSettings.editorFilmOverlayVisible.get() && !UIFilmPreview.this.viewportButtonsHidden ? Icons.VISIBLE : Icons.INVISIBLE, (b) ->
        {
            BBSSettings.editorFilmOverlayVisible.set(!BBSSettings.editorFilmOverlayVisible.get());
        })
        {
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 1 && this.area.isInside(context))
                {
                    UIUtils.playClick();
                    UIFilmPreview.this.getContext().replaceContextMenu(new UIViewportHideContextMenu(UIFilmPreview.this));

                    return true;
                }

                return super.subMouseClicked(context);
            }
        };
        this.hideOverlays.tooltip(UIKeys.FILM_PREVIEW_TOGGLE_OVERLAYS);
        this.toggleShaders = new UIIcon(Icons.GLOBE, (b) -> BBSRendering.toggleShaders());
        this.toggleShaders.tooltip(UIKeys.FILM_PREVIEW_TOGGLE_SHADERS);
        this.toggleShaders.setVisible(BBSRendering.isIrisLoaded());
        this.toggleShaders.context((menu) ->
        {
            menu.action(Icons.CURVES, UIKeys.FILM_PREVIEW_SHADER_SETTINGS, () ->
            {
                UIOverlay.addOverlay(this.getContext(), this.panel.dashboard.settingsPanel, 580, 340);
                this.panel.dashboard.settingsPanel.selectCategory("shader_curves");
            });
            menu.action(Icons.MATERIAL, UIKeys.FILM_PREVIEW_CHANGE_SHADER, () -> BBSRendering.openShaderPackScreen());
        });
        this.plause = new UIIcon(() -> this.panel.isRunning() ? Icons.PAUSE : Icons.PLAY, (b) -> this.panel.togglePlayback());
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE);
        this.plause.context((menu) ->
        {
            menu.action(Icons.PLAY, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAY_FILM, () ->
            {
                if (!this.panel.checkShowNoCamera())
                {
                    this.panel.dashboard.closeThisMenu();

                    if (this.panel.getData() != null)
                    {
                        Films.playFilm(this.panel.getData().getId(), true);
                    }
                }
            });

            menu.action(Icons.PAUSE, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_FREEZE_PAUSED, !this.panel.getController().isPaused(), () ->
            {
                this.panel.getController().setPaused(!this.panel.getController().isPaused());
            });
        });
        this.teleport = new UIIcon(Icons.MOVE_TO, (b) -> this.panel.teleportToCamera());
        this.teleport.tooltip(UIKeys.FILM_TELEPORT_TITLE);
        this.teleport.context((menu) ->
        {
            menu.action(Icons.MOVE_TO, UIKeys.FILM_TELEPORT_CONTEXT_PLAYER, BBSSettings.editorCameraPreviewPlayerSync.get(), () -> BBSSettings.editorCameraPreviewPlayerSync.set(!BBSSettings.editorCameraPreviewPlayerSync.get()));
            menu.action(Icons.COPY, UIKeys.CAMERA_PANELS_CONTEXT_COPY_POSITION, () ->
            {
                Position current = new Position(this.panel.getCamera());

                Map<String, Double> map = new LinkedHashMap<>();

                UICameraUtils.copyPoint(map, current.point);
                UICameraUtils.copyAngle(map, current.angle);

                Window.setClipboard(UICameraUtils.mapToString(map));
            });

            Map<String, Double> map = UICameraUtils.stringToMap(Window.getClipboard());

            if (!map.isEmpty())
            {
                menu.action(Icons.PASTE, UIKeys.CAMERA_PANELS_CONTEXT_PASTE_POSITION, () ->
                {
                    Position position = new Position();
                    Point point = UICameraUtils.createPoint(map);
                    Angle angle = UICameraUtils.createAngle(map);

                    if (point != null && angle != null)
                    {
                        position.point.set(point);
                        position.angle.set(angle);
                    }

                    this.panel.cameraEditor.editClip(position);
                });
            }
        });
        this.flight = new UIIcon(Icons.PLANE, (b) -> this.panel.toggleFlight());
        this.flight.tooltip(UIKeys.CAMERA_EDITOR_KEYS_MODES_FLIGHT);
        this.control = new UIIcon(Icons.POSE, (b) -> this.panel.getController().toggleControl());
        this.control.tooltip(UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_CONTROL);
        this.perspective = new UIIcon(this.panel.getController()::getOrbitModeIcon, (b) -> this.panel.getController().toggleOrbitMode());
        this.perspective.tooltip(UIKeys.FILM_CONTROLLER_KEYS_CHANGE_CAMERA_MODE);
        this.recordReplay = new UIIcon(Icons.SPHERE, (b) -> this.panel.getController().pickRecording());
        this.recordReplay.tooltip(UIKeys.FILM_REPLAY_RECORD);
        this.recordReplay.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_INSTANT_KEYFRAMES, this.panel.getController().isInstantKeyframes(), () ->
            {
                this.panel.getController().toggleInstantKeyframes();
            });

            menu.action(Icons.ALL_DIRECTIONS, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_COUNTDOWN_CONTROL, this.panel.getController().isCountdownControlEnabled(), () ->
            {
                this.panel.getController().toggleCountdownControl();
            });
        });
        this.recordVideo = new UIIcon(Icons.VIDEO_CAMERA, (b) ->
        {
            if (this.panel.checkShowNoCamera())
            {
                return;
            }

            if (!FFMpegUtils.checkFFMPEG())
            {
                UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
                UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

                guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE, Direction.LEFT);
                panel.icons.add(guide);

                UIOverlay.addOverlay(this.getContext(), panel);

                return;
            }

            if (this.panel.getData() != null)
            {
                this.panel.recorder.startRecording(this.panel.getData().camera.calculateDuration(), BBSRendering.getTexture());
            }
        });
        this.recordVideo.tooltip(UIKeys.CAMERA_TOOLTIPS_RECORD);
        this.recordVideo.context((menu) ->
        {
            menu.action(Icons.CAMERA, UIKeys.FILM_SCREENSHOT, () ->
            {
                ScreenshotRecorder recorder = BBSModClient.getScreenshotRecorder();
                Texture texture = BBSRendering.getTexture();

                recorder.takeScreenshot(Window.isAltPressed() ? null : recorder.getScreenshotFile(), texture.id, texture.width, texture.height);

                UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                    UIKeys.FILM_SCREENSHOT_TITLE,
                    UIKeys.FILM_SCREENSHOT_DESCRIPTION,
                    recorder.getScreenshots()
                );

                UIOverlay.addOverlay(this.getContext(), overlayPanel);
            });
            menu.action(Icons.IMAGE, UIKeys.FILM_SET_THUMBNAIL, this.panel::setFilmThumbnailFromViewport);
            menu.action(Icons.FOLDER, UIKeys.CAMERA_TOOLTIPS_OPEN_SCREENSHOTS, () -> UIUtils.openFolder(BBSModClient.getScreenshotRecorder().getScreenshots()));

            menu.action(Icons.FILM, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, () -> this.panel.recorder.openMovies());
            menu.action(Icons.GEAR, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEO_SETTINGS, () ->
            {
                UIOverlay.addOverlay(this.getContext(), this.panel.dashboard.settingsPanel, 580, 340);
                this.panel.dashboard.settingsPanel.selectCategory("video");
            });

            menu.action(Icons.SOUND, UIKeys.FILM_RENDER_AUDIO, this::renderAudio);
            menu.action(Icons.REFRESH, UIKeys.FILM_RESET_REPLAYS, this.panel.recorder.resetReplays, () ->
            {
                this.panel.recorder.resetReplays = !this.panel.recorder.resetReplays;
            });
        });

        this.renderQueue = new UIIcon(Icons.FILM, (b) -> this.panel.openRenderQueueOverlay());
        this.renderQueue.tooltip(UIKeys.FILM_OPEN_RENDER_QUEUE);

        this.restoreBlocks = new UIIcon(Icons.BLOCK, (b) ->
        {
            this.panel.notifyServer(ActionState.RESTORE);
            UIUtils.playClick();
        });
        this.restoreBlocks.tooltip(UIKeys.FILM_PREVIEW_RESTORE_BLOCKS);

        this.viewportButtonMap.put(ValueViewportToolbar.HIDE_OVERLAYS, this.hideOverlays);
        this.viewportButtonMap.put(ValueViewportToolbar.ONION_SKIN, this.onionSkin);
        this.viewportButtonMap.put(ValueViewportToolbar.TOGGLE_SHADERS, this.toggleShaders);
        this.viewportButtonMap.put(ValueViewportToolbar.PLAYBACK, this.plause);
        this.viewportButtonMap.put(ValueViewportToolbar.TELEPORT, this.teleport);
        this.viewportButtonMap.put(ValueViewportToolbar.FLIGHT, this.flight);
        this.viewportButtonMap.put(ValueViewportToolbar.CONTROL, this.control);
        this.viewportButtonMap.put(ValueViewportToolbar.PERSPECTIVE, this.perspective);
        this.viewportButtonMap.put(ValueViewportToolbar.RECORD_REPLAY, this.recordReplay);
        this.viewportButtonMap.put(ValueViewportToolbar.RECORD_VIDEO, this.recordVideo);
        this.viewportButtonMap.put(ValueViewportToolbar.RENDER_QUEUE, this.renderQueue);
        this.viewportButtonMap.put(ValueViewportToolbar.RESTORE_BLOCKS, this.restoreBlocks);

        this.rebuildViewportToolbar();
        BBSSettings.editorViewportToolbar.postCallback((v, f) -> this.rebuildViewportToolbar());
        this.add(this.icons);

        this.joinWorld = new UIButton(UIKeys.FILM_JOIN_WORLD, (b) -> this.panel.joinPendingWorld());
        this.joinWorld.relative(this).x(1F, -12).y(1F, -28).anchor(1F, 1F).w(120).h(20);
        this.joinWorld.setVisible(false);
        this.add(this.joinWorld);

        for (Consumer<UIFilmPreview> consumer : extensions)
        {
            consumer.accept(this);
        }
    }

    /* Build a single gizmo transform-mode button that selects its mode and highlights while that
       mode is active. Idle icons stay half-transparent so the letterbox stays readable. */
    private UIIcon createGizmoButton(Icon icon, Gizmo.Mode mode, IKey tooltip)
    {
        UIIcon button = new UIIcon(icon, (b) ->
        {
            Gizmo.INSTANCE.setMode(mode);
            UIUtils.playClick();
        })
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                int previous = this.iconColor;

                /* Selected mode stays fully opaque (activeBackground would otherwise keep idle alpha). */
                if (this.isActive())
                {
                    this.iconColor = GIZMO_ICON_HOVER;
                }

                super.renderSkin(context);
                this.iconColor = previous;
            }
        };

        button.tooltip(tooltip);
        this.styleGizmoToolbarIcon(button);
        button.activeBackground(Colors.A50 | Colors.BLUE);

        return button;
    }

    private void styleGizmoToolbarIcon(UIIcon button)
    {
        button.iconColor(GIZMO_ICON_IDLE);
        button.hoverColor(GIZMO_ICON_HOVER);
    }

    public void openReplays()
    {
        this.panel.focusAnchoredReplaysPanel();
    }

    public void openOnionSkin()
    {
        this.getContext().replaceContextMenu(new UIOnionSkinContextMenu(this.panel, this.panel.getController().getOnionSkin()));
    }

    public void rebuildGizmoToolbar()
    {
        this.gizmos.removeAll();

        for (String id : BBSSettings.editorGizmoToolbar.getVisibleOrder())
        {
            UIIcon button = this.gizmoButtonMap.get(id);

            if (button == null)
            {
                continue;
            }

            button.setVisible(true);
            this.gizmos.add(button);
        }

        int count = this.gizmos.getChildren().size();
        boolean horizontal = BBSSettings.editorGizmoToolbarHorizontal == null
            || BBSSettings.editorGizmoToolbarHorizontal.get();

        if (horizontal)
        {
            this.gizmos.row(0);
            this.gizmos.w(Math.max(20, count * 20));
            this.gizmos.h(20);
        }
        else
        {
            this.gizmos.column(0).vertical().stretch();
            this.gizmos.w(20);
            this.gizmos.h(Math.max(20, count * 20));
        }

        this.gizmos.resize();

        if (this.viewportButtonsHidden)
        {
            this.gizmos.setVisible(false);
        }
    }

    public void rebuildViewportToolbar()
    {
        this.icons.removeAll();
        this.viewportButtons.clear();

        for (String id : BBSSettings.editorViewportToolbar.getVisibleOrder())
        {
            UIIcon button = this.viewportButtonMap.get(id);

            if (button == null)
            {
                continue;
            }

            if (ValueViewportToolbar.TOGGLE_SHADERS.equals(id) && !BBSRendering.isIrisLoaded())
            {
                button.setVisible(false);

                continue;
            }

            button.setVisible(true);
            this.icons.add(button);

            if (!ValueViewportToolbar.HIDE_OVERLAYS.equals(id))
            {
                this.viewportButtons.add(button);
            }
        }

        int count = this.icons.getChildren().size();

        this.icons.row(0);
        this.icons.w(count * 20);
        this.icons.h(20);
        this.icons.setVisible(count > 0);
        this.icons.resize();

        if (this.viewportButtonsHidden)
        {
            for (UIIcon button : this.viewportButtons)
            {
                button.setVisible(false);
            }
        }
    }

    public boolean isViewportButtonsHidden()
    {
        return this.viewportButtonsHidden;
    }

    public void setViewportButtonsHidden(boolean hidden)
    {
        if (this.viewportButtonsHidden == hidden)
        {
            return;
        }

        this.viewportButtonsHidden = hidden;

        for (UIIcon button : this.viewportButtons)
        {
            button.setVisible(!this.viewportButtonsHidden);
        }

        this.gizmos.setVisible(!this.viewportButtonsHidden);
    }

    private void toggleViewportButtonsHidden()
    {
        this.setViewportButtonsHidden(!this.viewportButtonsHidden);
    }

    private void renderAudio()
    {
        if (this.panel.getData() == null)
        {
            return;
        }

        Clips camera = this.panel.getData().camera;
        List<AudioClip> audioClips = camera.getClips(AudioClip.class);

        String name = StringUtils.createTimestampFilename() + ".wav";
        File videos = BBSRendering.getVideoFolder();
        UIContext context = this.getContext();
        Vector2i range = BBSSettings.editorLoop.get() ? this.panel.getLoopingRange() : new Vector2i();

        if (AudioRenderer.renderAudio(new File(videos, name), audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y)))
        {
            UIOverlay.addOverlay(context, new UIMessageFolderOverlayPanel(UIKeys.GENERAL_SUCCESS, UIKeys.FILM_RENDER_AUDIO_SUCCESS, videos));
        }
        else
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.FILM_RENDER_AUDIO_ERROR));
        }
    }

    public Area getViewport()
    {
        int exportW = BBSSettings.videoSettings.width.get();
        int exportH = BBSSettings.videoSettings.height.get();
        int w = this.area.w;
        int h = this.area.h;

        Vector2i size = Vectors.resize(exportW / (float) exportH, w, h);
        Area area = new Area();

        area.setSize(size.x, size.y);
        area.setPos(this.area.mx() - area.w / 2, this.area.my() - area.h / 2);

        return area;
    }

    /**
     * Letterboxed viewport bounds in screen space. Used for mouse rays and hit tests
     * when the click context may not share the preview element's local coordinates
     * (e.g. floating viewport passthrough from {@link UIFilmPanel}).
     */
    public Area getAbsoluteViewport()
    {
        Area viewport = this.getViewport();
        UIViewportStack stack = UIViewportStack.fromElement(this);
        Area absolute = new Area();

        absolute.setSize(viewport.w, viewport.h);
        absolute.x = stack.globalX(viewport.x);
        absolute.y = stack.globalY(viewport.y);

        return absolute;
    }

    /**
     * Extra bottom offset so viewport hints sit above the preview icon row when it
     * overlaps the letterboxed viewport.
     */
    private int getViewportHintBottomReserve(Area area)
    {
        if (this.viewportButtonsHidden || !this.icons.isVisible() || this.icons.getChildren().isEmpty())
        {
            return 0;
        }

        return this.icons.area.h + TimelineToolbarSettings.INTERACTION_HINT_MARGIN;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context))
        {
            if (this.panel.replayEditor.handleViewportInteractionMouse(context, area))
            {
                return true;
            }

            /* In flight mode, viewport clicks drive the camera directly (left = look around,
             * right = roll, middle = FOV). This has to be started here rather than left to the
             * dashboard's orbit element, because this panel uses BLOCK_INSIDE mouse propagation
             * (to stop clicks falling through into stacked panels), which would otherwise
             * consume the click before the orbit camera ever saw it. */
            if (this.panel.isFlying())
            {
                if (!BBSSettings.editorFlightFreeLook.get())
                {
                    if (this.panel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
                    {
                        if (!this.panel.getController().orbit.isAnimating() && this.panel.getController().orbit.canStart(context) >= 0)
                        {
                            this.panel.getController().orbit.start(context);

                            return true;
                        }
                    }
                    else
                    {
                        int button = this.panel.dashboard.orbitUI.orbit.canStart(context);

                        if (button >= 0)
                        {
                            this.panel.dashboard.orbitUI.orbit.start(button, context.mouseX, context.mouseY);

                            return true;
                        }
                    }
                }

                return false;
            }

            if (context.mouseButton == 1 && this.panel.replayEditor.clickViewport(context, area))
            {
                return true;
            }

            if (this.panel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT
                && !this.panel.getController().orbit.isAnimating()
                && this.panel.getController().orbit.canStart(context) >= 0)
            {
                this.panel.getController().orbit.start(context);

                return true;
            }

            return this.panel.replayEditor.clickViewport(context, area);
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.panel.replayEditor.handleViewportInteractionKey(context))
        {
            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.panel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT && this.panel.getController().orbit.enabled)
        {
            this.panel.getController().orbit.stop();
        }
        else if (!this.panel.isFlying())
        {
            this.panel.replayEditor.stopGizmoDrag();
        }

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.clip(this.area, context);

        if (this.joinWorld != null)
        {
            this.joinWorld.setVisible(this.panel.canShowJoinWorld());
        }

        /* Keep the gizmo buttons highlighted to match the active transform mode. */
        Gizmo.Mode mode = Gizmo.INSTANCE.getMode();

        this.gizmoMove.active(mode == Gizmo.Mode.TRANSLATE);
        this.gizmoScale.active(mode == Gizmo.Mode.SCALE);
        this.gizmoRotate.active(mode == Gizmo.Mode.ROTATE);
        this.gizmoCombined.active(mode == Gizmo.Mode.COMBINED);
        this.gizmoTop.active(mode == Gizmo.Mode.TOP);

        Texture texture = BBSRendering.getTexture();
        Area area = this.getViewport();
        Camera camera = this.panel.getCamera();

        camera.copy(this.panel.getWorldCamera());
        camera.view.set(this.panel.lastView);
        camera.projection.set(this.panel.lastProjection);
        context.batcher.flush();

        if (texture != null && area.w > 0 && area.h > 0)
        {
            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        if (this.panel.replayEditor.isViewportInteractionActive())
        {
            this.panel.replayEditor.renderViewportInteraction(context, area);
        }

        if (this.pendingThumbnail != null)
        {
            boolean oldNames = BBSSettings.editorReplayHudDisplayName.get();
            BBSSettings.editorReplayHudDisplayName.set(false);

            context.batcher.flush();
            this.captureThumbnailInternal(this.pendingThumbnail, this.pendingThumbnailCallback);
            this.pendingThumbnail = null;
            this.pendingThumbnailCallback = null;

            BBSSettings.editorReplayHudDisplayName.set(oldNames);
        }

        this.renderCursor(context);

        /* Render rule of thirds */
        if (BBSSettings.editorRuleOfThirds.get())
        {
            int guidesColor = BBSSettings.editorGuidesColor.get();

            context.batcher.box(area.x + area.w / 3 - 1, area.y, area.x + area.w / 3, area.y + area.h, guidesColor);
            context.batcher.box(area.x + area.w - area.w / 3, area.y, area.x + area.w - area.w / 3 + 1, area.y + area.h, guidesColor);

            context.batcher.box(area.x, area.y + area.h / 3 - 1, area.x + area.w, area.y + area.h / 3, guidesColor);
            context.batcher.box(area.x, area.y + area.h - area.h / 3 - 1, area.x + area.w, area.y + area.h - area.h / 3, guidesColor);
        }

        /* Render safe margins (action safe 90%, title safe 80%) */
        if (BBSSettings.editorSafeMargins.get())
        {
            int guidesColor = BBSSettings.editorSafeMarginsColor.get();

            int actionMarginX = Math.round(area.w * 0.05F);
            int actionMarginY = Math.round(area.h * 0.05F);
            int actionLeft = area.x + actionMarginX;
            int actionRight = area.x + area.w - actionMarginX;
            int actionTop = area.y + actionMarginY;
            int actionBottom = area.y + area.h - actionMarginY;

            context.batcher.box(actionLeft, actionTop, actionLeft + 1, actionBottom, guidesColor);
            context.batcher.box(actionRight - 1, actionTop, actionRight, actionBottom, guidesColor);
            context.batcher.box(actionLeft, actionTop, actionRight, actionTop + 1, guidesColor);
            context.batcher.box(actionLeft, actionBottom - 1, actionRight, actionBottom, guidesColor);

            int titleMarginX = Math.round(area.w * 0.10F);
            int titleMarginY = Math.round(area.h * 0.10F);
            int titleLeft = area.x + titleMarginX;
            int titleRight = area.x + area.w - titleMarginX;
            int titleTop = area.y + titleMarginY;
            int titleBottom = area.y + area.h - titleMarginY;

            context.batcher.box(titleLeft, titleTop, titleLeft + 1, titleBottom, guidesColor);
            context.batcher.box(titleRight - 1, titleTop, titleRight, titleBottom, guidesColor);
            context.batcher.box(titleLeft, titleTop, titleRight, titleTop + 1, guidesColor);
            context.batcher.box(titleLeft, titleBottom - 1, titleRight, titleBottom, guidesColor);
        }

        VideoRenderer.update();

        if (BBSSettings.editorCenterLines.get())
        {
            int guidesColor = BBSSettings.editorGuidesColor.get();
            int x = area.mx();
            int y = area.my();

            context.batcher.box(area.x, y, area.ex(), y + 1, guidesColor);
            context.batcher.box(x, area.y, x + 1, area.ey(), guidesColor);
        }

        if (BBSSettings.editorCrosshair.get())
        {
            int x = area.mx() + 1;
            int y = area.my() + 1;

            context.batcher.box(x - 4, y - 1, x + 3, y, Colors.setA(Colors.WHITE, 0.5F));
            context.batcher.box(x - 1, y - 4, x, y + 3, Colors.setA(Colors.WHITE, 0.5F));
        }

        this.panel.getController().renderHUD(context, area);

        if (BBSSettings.editorFilmOverlayVisible.get() && this.panel.replayEditor.isVisible() && this.panel.getData() != null)
        {
            RunnerCameraController runner = this.panel.getRunner();
            int w = (int) (area.w * BBSSettings.audioWaveformWidth.get());
            int x = area.x(0.5F, w);
            float tick = this.panel.getCursor() + (runner.isRunning() ? context.getTransition() : 0);

            this.clips.clear();

            for (Clip clip : this.panel.getData().camera.get())
            {
                if (clip instanceof AudioClip)
                {
                    this.clips.add((AudioClip) clip);
                }
            }

            AudioRenderer.renderAll(context.batcher, this.clips, tick, x, area.y + 10, w, BBSSettings.audioWaveformHeight.get(), context.menu.width, context.menu.height);
        }

        Area a = this.icons.area;

        /* Render icon bar backgrounds only for visible controls */
        if (this.viewportButtonsHidden)
        {
            if (this.hideOverlays.isVisible())
            {
                Area hideArea = this.hideOverlays.area;

                context.batcher.gradientVBox(hideArea.x, hideArea.y, hideArea.ex(), hideArea.ey(), 0, Colors.A50);
            }
        }
        else
        {
            context.batcher.gradientVBox(a.x, a.y, a.ex(), a.ey(), 0, Colors.A50);
        }

        if (!this.viewportButtonsHidden)
        {
            if (this.panel.isFlying()) UIDashboardPanels.renderHighlight(context.batcher, this.flight.area, Direction.BOTTOM);
            if (this.panel.getController().isControlling()) UIDashboardPanels.renderHighlight(context.batcher, this.control.area, Direction.BOTTOM);
            if (this.panel.getController().isRecording()) UIDashboardPanels.renderHighlight(context.batcher, this.recordReplay.area, Direction.BOTTOM);
            if (this.panel.recorder.isRecording()) UIDashboardPanels.renderHighlight(context.batcher, this.recordVideo.area, Direction.BOTTOM);
            if (this.panel.getController().getOnionSkin().enabled.get()) UIDashboardPanels.renderHighlight(context.batcher, this.onionSkin.area, Direction.BOTTOM);
        }

        if (!BBSSettings.editorFilmOverlayVisible.get()) UIDashboardPanels.renderHighlight(context.batcher, this.hideOverlays.area, Direction.BOTTOM);
        if (this.viewportButtonsHidden) UIDashboardPanels.renderHighlight(context.batcher, this.hideOverlays.area, Direction.BOTTOM);
        if (BBSRendering.isIrisShadersEnabled() && this.toggleShaders.isVisible()) UIDashboardPanels.renderHighlight(context.batcher, this.toggleShaders.area, Direction.BOTTOM);

        if (!this.viewportButtonsHidden && this.panel.getController().isControlling())
        {
            String s = UIKeys.FILM_CONTROLLER_CONTROL_MODE_TOOLTIP.format(KeyCodes.getName(Keys.FILM_CONTROLLER_TOGGLE_CONTROL.getMainKey())).get();
            int w = context.batcher.getFont().getWidth(s);
            int height = context.batcher.getFont().getHeight();

            context.batcher.textCard(s, a.mx(w), a.y - height - 5);
        }

        super.render(context);

        if (!this.viewportButtonsHidden && this.panel.replayEditor.isViewportInteractionActive())
        {
            this.panel.replayEditor.renderViewportInteractionHint(context, area,
                this.getViewportHintBottomReserve(area));
        }

        context.batcher.unclip(context);
    }

    private void renderCursor(UIContext context)
    {
        net.minecraft.client.render.Camera mcCamera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Matrix4fStack stack = RenderSystem.getModelViewStack();

        stack.pushMatrix();

        stack.mul(context.batcher.getContext().getMatrices().peek().getPositionMatrix());
        stack.translate(area.x + 16, area.ey() - 12, 0F);
        stack.rotate(RotationAxis.NEGATIVE_X.rotationDegrees(mcCamera.getPitch()));
        stack.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(mcCamera.getYaw()));
        stack.scale(-1F, -1F, -1F);
        MatrixStackUtils.applyModelViewMatrix();
        RenderSystem.renderCrosshair(10);

        stack.popMatrix();
        MatrixStackUtils.applyModelViewMatrix();
    }

    public void cancelCapture()
    {
        this.pendingThumbnail = null;
        this.pendingThumbnailCallback = null;
    }

    public void captureThumbnail(File output)
    {
        this.captureThumbnail(output, null);
    }

    public void captureThumbnail(File output, Runnable onComplete)
    {
        this.pendingThumbnail = output;
        this.pendingThumbnailCallback = onComplete;
    }

    public void captureThumbnailNow(File output, Runnable onComplete)
    {
        this.pendingThumbnail = null;
        this.pendingThumbnailCallback = null;
        this.captureThumbnailInternal(output, onComplete);
    }

    private void captureThumbnailInternal(File output, Runnable onComplete)
    {
        Texture viewportTexture = BBSRendering.getTexture();

        if (viewportTexture != null && viewportTexture.isValid() && viewportTexture.width > 0 && viewportTexture.height > 0)
        {
            try
            {
                int width = viewportTexture.width;
                int height = viewportTexture.height;
                FloatBuffer pixelData = BufferUtils.createFloatBuffer(width * height * 4);

                viewportTexture.bind();
                GL11.glGetTexImage(viewportTexture.target, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixelData);
                viewportTexture.unbind();
                pixelData.rewind();

                int[] pixels = new int[width * height];

                for (int y = 0; y < height; ++y)
                {
                    for (int x = 0; x < width; ++x)
                    {
                        float r = pixelData.get() * 255F;
                        float g = pixelData.get() * 255F;
                        float b = pixelData.get() * 255F;
                        float a = pixelData.get() * 255F;
                        int i = ((height - 1) - y) * width + x;

                        pixels[i] = ((int) a << 24) + ((int) r << 16) + ((int) g << 8) + (int) b;
                    }
                }

                if (!this.isThumbnailPixelDataValid(pixels))
                {
                    if (onComplete != null)
                    {
                        onComplete.run();
                    }

                    return;
                }

                new Thread(() ->
                {
                    ScreenshotRecorder.ScreenshotRunner runner = new ScreenshotRecorder.ScreenshotRunner(width, height, pixels, output);

                    runner.playSound = false;
                    runner.run();

                    if (onComplete != null)
                    {
                        MinecraftClient.getInstance().execute(onComplete);
                    }
                }).start();
            }
            catch (Exception e)
            {
                e.printStackTrace();

                if (onComplete != null)
                {
                    onComplete.run();
                }
            }

            return;
        }

        Area area = this.getViewport();
        UIContext context = this.getContext();

        if (area == null || context == null || area.w <= 0 || area.h <= 0)
        {
            if (onComplete != null)
            {
                onComplete.run();
            }

            return;
        }

        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();

        int width = (int) (area.w * scale);
        int height = (int) (area.h * scale);
        int x = (int) (context.globalX(area.x) * scale);
        int y = (int) (MinecraftClient.getInstance().getWindow().getFramebufferHeight() - context.globalY(area.y) * scale - height);

        if (width <= 0 || height <= 0)
        {
            if (onComplete != null)
            {
                onComplete.run();
            }

            return;
        }

        FloatBuffer pixelData = BufferUtils.createFloatBuffer(width * height * 4);

        GL11.glReadPixels(x, y, width, height, GL11.GL_RGBA, GL11.GL_FLOAT, pixelData);
        pixelData.rewind();

        int[] pixels = new int[width * height];

        for (int i = 0; i < height; ++i)
        {
            for (int j = 0; j < width; ++j)
            {
                float r = pixelData.get() * 255;
                float g = pixelData.get() * 255;
                float b = pixelData.get() * 255;
                float a = pixelData.get() * 255;
                int k = ((height - 1) - i) * width + j;

                pixels[k] = ((int) a << 24) + ((int) r << 16) + ((int) g << 8) + (int) b;
            }
        }

        if (!this.isThumbnailPixelDataValid(pixels))
        {
            if (onComplete != null)
            {
                onComplete.run();
            }

            return;
        }

        new Thread(() ->
        {
            new ScreenshotRecorder.ScreenshotRunner(width, height, pixels, output).run();

            if (onComplete != null)
            {
                MinecraftClient.getInstance().execute(onComplete);
            }
        }).start();
    }

    private boolean isThumbnailPixelDataValid(int[] pixels)
    {
        if (pixels == null || pixels.length == 0)
        {
            return false;
        }

        int bright = 0;
        int step = Math.max(1, pixels.length / 64);

        for (int i = 0; i < pixels.length; i += step)
        {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            if (r + g + b > 12)
            {
                bright++;
            }
        }

        return bright >= 3;
    }
}
