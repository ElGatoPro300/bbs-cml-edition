package mchorse.bbs_mod.ui.items;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.OrbitCamera;
import mchorse.bbs_mod.camera.controller.OrbitCameraController;
import mchorse.bbs_mod.client.StructurePickerClient;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.items.StructurePickerBrushShape;
import mchorse.bbs_mod.items.StructurePickerExporter;
import mchorse.bbs_mod.items.StructurePickerMode;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCamera;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.editors.utils.UIStructureOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Immersive Structure Picker: tools on left, mode list on right; slide in from each side.
 */
public class UIStructurePickerPanel extends UIOverlayPanel
{
    private static final int MODES_W = 120;
    private static final int TOOLS_W = 180;
    private static final int TOOLS_H = 380;
    private static final int MODE_EXTRA_BAR_H = 92;
    private static final int COLOR_IMPORT_MODEL = 0xFF3B82F6;
    private static final int COLOR_IMPORT_FILM = 0xFF3B82F6;
    private static final int COLOR_PLACE = 0xFF22C55E;
    private static final int COLOR_SAVE = 0xFF14B8A6;
    private static final int COLOR_REMOVE = 0xFFF59E0B;
    private static final int COLOR_BREAK = Colors.RED;
    public static final int COLOR_SIDEBAR = 0x66101018;

    private static UIStructurePickerPanel opened;

    private final UIElement modesPanel;
    private final UIElement toolsPanel;
    private final UITextbox nameBox;
    private final UIButton modelBlockButton;
    private final UIButton importFilmButton;
    private final UIButton placeStructureButton;
    private final UIButton saveStructureButton;
    private final UIButton placeAndSelectButton;
    private final UIButton placeCancelButton;
    private final UIButton removeSelectionButton;
    private final UIButton breakSelectionButton;
    private final UIElement placementCoords;
    private final UITrackpad placementX;
    private final UITrackpad placementY;
    private final UITrackpad placementZ;
    private final UIElement selectionSizeCoords;
    private final UITrackpad selectionSizeX;
    private final UITrackpad selectionSizeY;
    private final UITrackpad selectionSizeZ;
    private final UIStructurePickerModePicker modePicker;
    private final UITrackpad sameBlockLimit;
    private final UIElement brushControls;
    private final UICirculate brushShape;
    private final UITrackpad brushRadius;
    private final UITrackpad brushDepth;
    private final UIToggle clickOnAirToggle;
    private final UIToggle subtractToggle;
    private final UIOrbitCamera uiOrbitCamera;
    private final IUIElement worldPickLayer;

    private OrbitCamera orbitCamera;
    private boolean syncingPlacementCoords;
    private boolean syncingSelectionSize;
    private boolean suppressFreecamUntilLmbUp;
    private Runnable exitMenu;

    public static void open()
    {
        if (UIStructurePickerPanel.opened != null)
        {
            return;
        }

        UIStructurePickerPanel panel = new UIStructurePickerPanel();

        UIStructurePickerPanel.opened = panel;

        MinecraftClient client = MinecraftClient.getInstance();
        Screen returnScreen = client.currentScreen;
        ClientPlayerEntity player = client.player;
        OrbitCamera orbit = new OrbitCamera();

        orbit.setFovRoll(false);

        if (player != null)
        {
            /* Match UIDashboard freecam setup so the view keeps the same angle. */
            orbit.position.set(player.getX(), player.getEyeY(), player.getZ());
            orbit.rotation.set(
                MathUtils.toRad(player.getPitch()),
                MathUtils.toRad(player.getHeadYaw() - 180),
                0F
            );
            orbit.fov = MathUtils.toRad(client.options.getFov().getValue().floatValue());
        }

        panel.orbitCamera = orbit;
        panel.uiOrbitCamera.orbit = orbit;
        StructurePickerClient.setFreecamOrbit(orbit);

        OrbitCameraController orbitController = new OrbitCameraController(orbit);
        boolean previousHudHidden = client.options.hudHidden;
        boolean[] closed = new boolean[] {false};

        Runnable finishExit = () ->
        {
            if (closed[0])
            {
                return;
            }

            closed[0] = true;
            /* Clear before setScreen so any leftover UIScreen.render won't re-hide the HUD. */
            UIStructurePickerPanel.opened = null;
            client.options.hudHidden = previousHudHidden;

            if (StructurePickerClient.isPlacementActive())
            {
                StructurePickerClient.clearBoundStructurePath();
            }

            StructurePickerClient.cancelPlacement();
            StructurePickerClient.setPlacementUiListener(null);
            StructurePickerClient.setFreecamOrbit(null);

            if (panel.orbitCamera != null)
            {
                panel.orbitCamera.reset();
            }

            BBSModClient.getCameraController().remove(orbitController);
        };

        Runnable exitToReturnScreen = () ->
        {
            finishExit.run();

            if (client.currentScreen instanceof UIScreen)
            {
                client.setScreen(returnScreen);
            }
        };

        panel.onClose((event) -> exitToReturnScreen.run());
        panel.exitMenu = exitToReturnScreen;

        StructurePickerClient.setPlacementUiListener(panel::syncPlacementButtons);
        StructurePickerClient.ensureCubeScaleGizmo();
        panel.syncPlacementButtons();

        UIScreen.open(new UIBaseMenu()
        {
            @Override
            public boolean needsWorldRender()
            {
                return true;
            }

            @Override
            public boolean canHideHUD()
            {
                /* Stop forcing F1 as soon as exit begins / panel is cleared. */
                return UIStructurePickerPanel.opened != null && !closed[0];
            }

            @Override
            public boolean canPause()
            {
                return false;
            }

            @Override
            protected void closeMenu()
            {
                finishExit.run();
                client.setScreen(returnScreen);
            }

            @Override
            public void onOpen(UIBaseMenu oldMenu)
            {
                super.onOpen(oldMenu);

                BBSModClient.getCameraController().add(orbitController);

                UIOverlay overlay = new UIOverlay()
                {
                    @Override
                    protected boolean subMouseClicked(UIContext context)
                    {
                        /* Immersive fullscreen picker: empty-world LMB is freecam, not dismiss. */
                        return false;
                    }
                };

                overlay.noBackground();
                panel.relative(overlay).xy(0.5F, 0.5F).wh(1F, 1F).anchor(0.5F).bounds(overlay, 0);
                UIOverlay.setupPanel(this.context, overlay, panel);
                /* Ignore saved 300x240 size from the old compact confirm dialog. */
                panel.getFlex().x.offset = 0;
                panel.getFlex().y.offset = 0;
                panel.getFlex().w.offset = 0;
                panel.getFlex().h.offset = 0;
                overlay.resize();
            }

            @Override
            public void onClose(UIBaseMenu nextMenu)
            {
                finishExit.run();
                client.options.hudHidden = previousHudHidden;
                super.onClose(nextMenu);
            }
        });
    }

    public static boolean isOpened()
    {
        if (UIStructurePickerPanel.opened != null)
        {
            return true;
        }

        Screen currentScreen = MinecraftClient.getInstance().currentScreen;

        if (!(currentScreen instanceof UIScreen uiScreen))
        {
            return false;
        }

        List<UIStructurePickerPanel> panels = uiScreen.getMenu().getRoot().getChildren(UIStructurePickerPanel.class);

        return !panels.isEmpty();
    }

    @Override
    public String getKey()
    {
        /* Separate from the old compact 300x240 confirm dialog saved size. */
        return "UIStructurePickerPanel_immersive";
    }

    public UIStructurePickerPanel()
    {
        super(UIKeys.STRUCTURE_PICKER_CONFIRM_TITLE);

        this.title.setVisible(false);
        this.title.setEnabled(false);
        this.close.setVisible(false);
        this.close.setEnabled(false);
        this.icons.setVisible(false);
        this.icons.setEnabled(false);
        this.resizable(false);
        this.content.relative(this).xy(0, 0).w(1F).h(1F);

        this.uiOrbitCamera = new UIOrbitCamera()
        {
            @Override
            public IUIElement mouseClicked(UIContext context)
            {
                /* LMB look is driven every frame in updateFreecamLook. */
                return null;
            }

            @Override
            public void render(UIContext context)
            {
                /* Look-drag is driven by UIStructurePickerPanel.updateFreecamLook. */
            }

            @Override
            public IUIElement mouseScrolled(UIContext context)
            {
                if (UIStructurePickerPanel.this.isOverSidePanels(context))
                {
                    return null;
                }

                return this.orbit.scroll((int) context.mouseWheel) ? this : null;
            }
        };
        this.uiOrbitCamera.setControl(true);
        this.orbitCamera = this.uiOrbitCamera.orbit;
        this.orbitCamera.setFovRoll(false);

        /* Checked before freecam orbit: consume LMB only when over gizmos/corners. */
        this.worldPickLayer = new IUIElement()
        {
            @Override
            public IUIElement mouseClicked(UIContext context)
            {
                if (context.mouseButton != 0)
                {
                    return null;
                }

                if (UIStructurePickerPanel.this.isOverSidePanels(context))
                {
                    return null;
                }

                if (StructurePickerClient.isPlacementActive() && StructurePickerClient.isOverPlacementGizmo())
                {
                    return this;
                }

                if (!StructurePickerClient.isPlacementActive())
                {
                    if (StructurePickerClient.isOverSelectionMoveGizmo())
                    {
                        return this;
                    }

                    if (StructurePickerClient.isOverSelectionCorner())
                    {
                        StructurePickerClient.tryActivateSelectionMoveFromUi();

                        return this;
                    }
                }

                return null;
            }

            @Override
            public IUIElement mouseScrolled(UIContext context)
            {
                if (UIStructurePickerPanel.this.isOverSidePanels(context) || UIStructurePickerPanel.this.orbitCamera == null)
                {
                    return null;
                }

                return UIStructurePickerPanel.this.orbitCamera.scroll((int) context.mouseWheel) ? this : null;
            }

            @Override
            public IUIElement mouseReleased(UIContext context)
            {
                return null;
            }

            @Override
            public IUIElement keyPressed(UIContext context)
            {
                return null;
            }

            @Override
            public IUIElement textInput(UIContext context)
            {
                return null;
            }

            @Override
            public boolean isEnabled()
            {
                return true;
            }

            @Override
            public boolean isVisible()
            {
                return true;
            }

            @Override
            public void resize()
            {}

            @Override
            public boolean canBeRendered(Area area)
            {
                return false;
            }

            @Override
            public void render(UIContext context)
            {}
        };

        this.modesPanel = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                float t = UIStructurePickerPanel.this.getOpenAmount();
                float slide = (1.0F - t) * -(MODES_W + 24);

                context.render.batcher.getContext().getMatrices().push();
                context.render.batcher.getContext().getMatrices().translate(slide, 0.0F, 0.0F);
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), COLOR_SIDEBAR);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x55FFFFFF, 1);
                super.render(context);
                context.render.batcher.getContext().getMatrices().pop();
            }
        };
        this.modesPanel.mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);
        this.toolsPanel = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                float t = UIStructurePickerPanel.this.getOpenAmount();
                float slide = (1.0F - t) * (TOOLS_W + 24);

                context.render.batcher.getContext().getMatrices().push();
                context.render.batcher.getContext().getMatrices().translate(slide, 0.0F, 0.0F);
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), COLOR_SIDEBAR);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x55FFFFFF, 1);
                super.render(context);
                context.render.batcher.getContext().getMatrices().pop();
            }
        };
        this.toolsPanel.mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);

        this.nameBox = new UITextbox(64, null);
        this.nameBox.filename();
        this.nameBox.placeholder(UIKeys.STRUCTURE_PICKER_NAME_PLACEHOLDER);

        this.modelBlockButton = new UIButton(UIKeys.STRUCTURE_PICKER_MAKE_MODEL_BLOCK, (b) -> this.importSelection(true));
        this.modelBlockButton.color(COLOR_IMPORT_MODEL);
        this.importFilmButton = new UIButton(UIKeys.STRUCTURE_PICKER_IMPORT_FILM, (b) -> this.importSelection(false));
        this.importFilmButton.color(COLOR_IMPORT_FILM);
        this.placeStructureButton = new UIButton(UIKeys.STRUCTURE_PICKER_PLACE_STRUCTURE, (b) -> this.browseStructures());
        this.placeStructureButton.color(COLOR_PLACE);
        this.saveStructureButton = new UIButton(UIKeys.STRUCTURE_PICKER_SAVE_STRUCTURE, (b) -> this.saveBoundStructure());
        this.saveStructureButton.color(COLOR_SAVE);
        this.placeAndSelectButton = new UIButton(UIKeys.STRUCTURE_PICKER_PLACE_AND_SELECT, (b) ->
        {
            StructurePickerClient.confirmPlaceAndSelect();
            this.syncBoundStructureName();
            this.syncPlacementButtons();
        });
        this.placeAndSelectButton.color(COLOR_PLACE);
        this.placeCancelButton = new UIButton(UIKeys.STRUCTURE_PICKER_PLACE_CANCEL, (b) ->
        {
            StructurePickerClient.cancelPlacement();
            StructurePickerClient.clearBoundStructurePath();
            this.syncPlacementButtons();
        });
        this.placeCancelButton.color(COLOR_REMOVE);

        this.placementX = new UITrackpad((v) -> this.applyPlacementCoord()).integer();
        this.placementY = new UITrackpad((v) -> this.applyPlacementCoord()).integer();
        this.placementZ = new UITrackpad((v) -> this.applyPlacementCoord()).integer();
        this.placementCoords = UI.column(
            4,
            UI.row(4, new UILabel(UIKeys.GENERAL_X).background().w(18), this.placementX),
            UI.row(4, new UILabel(UIKeys.GENERAL_Y).background().w(18), this.placementY),
            UI.row(4, new UILabel(UIKeys.GENERAL_Z).background().w(18), this.placementZ)
        );

        this.selectionSizeX = new UITrackpad((v) -> this.applySelectionSize()).integer().limit(1, 512);
        this.selectionSizeY = new UITrackpad((v) -> this.applySelectionSize()).integer().limit(1, 512);
        this.selectionSizeZ = new UITrackpad((v) -> this.applySelectionSize()).integer().limit(1, 512);
        this.selectionSizeCoords = UI.column(
            4,
            UI.row(4, new UILabel(UIKeys.GENERAL_X).background().w(18), this.selectionSizeX),
            UI.row(4, new UILabel(UIKeys.GENERAL_Y).background().w(18), this.selectionSizeY),
            UI.row(4, new UILabel(UIKeys.GENERAL_Z).background().w(18), this.selectionSizeZ)
        );

        this.removeSelectionButton = new UIButton(UIKeys.STRUCTURE_PICKER_REMOVE_SELECTION, (b) -> this.removeSelection());
        this.removeSelectionButton.color(COLOR_REMOVE);
        this.breakSelectionButton = new UIButton(UIKeys.STRUCTURE_PICKER_BREAK_SELECTION, (b) -> this.breakSelection());
        this.breakSelectionButton.color(COLOR_BREAK);

        this.modePicker = new UIStructurePickerModePicker();

        this.sameBlockLimit = new UITrackpad((v) ->
        {
            StructurePickerClient.setSameBlockLimit(v.intValue());
        }).integer().limit(1, 500).values(1D);
        this.sameBlockLimit.tooltip(UIKeys.STRUCTURE_PICKER_SAME_LIMIT);
        this.sameBlockLimit.setValue(StructurePickerClient.getSameBlockLimit());

        this.brushShape = new UICirculate((b) ->
        {
            StructurePickerClient.setBrushShape(StructurePickerBrushShape.fromIndex(b.getValue()));
        });
        this.brushShape.addLabel(UIKeys.STRUCTURE_PICKER_BRUSH_SPHERE);
        this.brushShape.addLabel(UIKeys.STRUCTURE_PICKER_BRUSH_CUBE);
        this.brushShape.setValue(StructurePickerClient.getBrushShape().index);

        this.brushRadius = new UITrackpad((v) ->
        {
            StructurePickerClient.setBrushRadius(v.intValue());
        }).integer().limit(0, 16).values(1D);
        this.brushRadius.tooltip(UIKeys.STRUCTURE_PICKER_BRUSH_RADIUS);
        this.brushRadius.setValue(StructurePickerClient.getBrushRadius());

        this.brushDepth = new UITrackpad((v) ->
        {
            StructurePickerClient.setBrushDepth(v.intValue());
        }).integer().limit(1, 16).values(1D);
        this.brushDepth.tooltip(UIKeys.STRUCTURE_PICKER_BRUSH_DEPTH);
        this.brushDepth.setValue(StructurePickerClient.getBrushDepth());

        this.brushControls = new UIElement();
        this.brushShape.relative(this.brushControls).x(8).y(0).w(1F, -16).h(20);
        this.brushRadius.relative(this.brushControls).x(8).y(24).w(1F, -16).h(20);
        this.brushDepth.relative(this.brushControls).x(8).y(48).w(1F, -16).h(20);
        this.brushControls.add(this.brushShape, this.brushRadius, this.brushDepth);

        this.clickOnAirToggle = new UIToggle(UIKeys.STRUCTURE_PICKER_CLICK_ON_AIR, StructurePickerClient.isClickOnAir(), (b) ->
        {
            StructurePickerClient.setClickOnAir(b.getValue());
        });
        this.clickOnAirToggle.color(Colors.WHITE, true);

        this.subtractToggle = new UIToggle(UIKeys.STRUCTURE_PICKER_SUBTRACT_SELECTION, StructurePickerClient.isSubtractMode(), (b) ->
        {
            StructurePickerClient.setSubtractMode(b.getValue());
        });
        this.subtractToggle.color(Colors.WHITE, true);

        int panelH = Math.max(this.modePicker.preferredHeight() + MODE_EXTRA_BAR_H, TOOLS_H);

        /* Mode list flush left, tools flush right — matched height. */
        this.modesPanel.relative(this.content).x(0).y(0.5F).w(MODES_W).h(panelH).anchorY(0.5F);
        this.toolsPanel.relative(this.content).x(1F).y(0.5F).w(TOOLS_W).h(panelH).anchor(1F, 0.5F);

        this.modePicker.relative(this.modesPanel).xy(0, 0).w(1F).h(1F, -MODE_EXTRA_BAR_H);
        this.sameBlockLimit.relative(this.modesPanel).x(8).y(1F, -28).w(1F, -16).h(20);
        this.brushControls.relative(this.modesPanel).x(0).y(1F, -MODE_EXTRA_BAR_H + 8).w(1F).h(MODE_EXTRA_BAR_H - 8);

        this.nameBox.relative(this.toolsPanel).x(10).y(10).w(1F, -20).h(20);
        this.modelBlockButton.relative(this.toolsPanel).x(10).y(36).w(1F, -20).h(22);
        this.importFilmButton.relative(this.toolsPanel).x(10).y(62).w(1F, -20).h(22);
        this.removeSelectionButton.relative(this.toolsPanel).x(10).y(88).w(1F, -20).h(22);
        this.breakSelectionButton.relative(this.toolsPanel).x(10).y(114).w(1F, -20).h(22);
        this.placeStructureButton.relative(this.toolsPanel).x(10).y(140).w(1F, -20).h(22);
        this.saveStructureButton.relative(this.toolsPanel).x(10).y(166).w(1F, -20).h(22);
        this.clickOnAirToggle.relative(this.toolsPanel).x(10).y(198).w(1F, -20).h(16);
        this.subtractToggle.relative(this.toolsPanel).x(10).y(220).w(1F, -20).h(16);

        /* Placement / selection size sit at the bottom of the tools panel. */
        this.placementCoords.relative(this.toolsPanel).x(10).y(1F, -78).w(1F, -20).h(68);
        this.selectionSizeCoords.relative(this.toolsPanel).x(10).y(1F, -78).w(1F, -20).h(68);
        this.placeCancelButton.relative(this.toolsPanel).x(10).y(1F, -104).w(1F, -20).h(22);
        this.placeAndSelectButton.relative(this.toolsPanel).x(10).y(1F, -130).w(1F, -20).h(22);

        this.modesPanel.add(this.modePicker, this.sameBlockLimit, this.brushControls);
        this.toolsPanel.add(
            this.nameBox,
            this.modelBlockButton,
            this.importFilmButton,
            this.removeSelectionButton,
            this.breakSelectionButton,
            this.placeStructureButton,
            this.saveStructureButton,
            this.placeAndSelectButton,
            this.placeCancelButton,
            this.placementCoords,
            this.selectionSizeCoords,
            this.clickOnAirToggle,
            this.subtractToggle
        );
        /* Orbit first (checked last): LMB look on empty world. World-pick before orbit. */
        this.content.prepend(this.uiOrbitCamera);
        this.content.add(this.worldPickLayer, this.modesPanel, this.toolsPanel);

        this.keys().register(Keys.UNDO, StructurePickerClient::undo).active(StructurePickerClient::canUndo);
        this.keys().register(Keys.REDO, StructurePickerClient::redo).active(StructurePickerClient::canRedo);

        this.updateImportButtons();
        this.syncPlacementButtons();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        return super.subMouseClicked(context);
    }

    private boolean isOverWorldGizmo()
    {
        if (StructurePickerClient.isPlacementActive())
        {
            return StructurePickerClient.isOverPlacementGizmo();
        }

        return StructurePickerClient.isOverSelectionMoveGizmo()
            || StructurePickerClient.isOverSelectionCorner();
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        return super.subMouseReleased(context);
    }

    /**
     * Drive freecam look / WASD every frame. LMB is look-only (no world selection).
     */
    private void updateFreecamLook(UIContext context)
    {
        if (this.orbitCamera == null)
        {
            return;
        }

        long window = MinecraftClient.getInstance().getWindow().getHandle();
        boolean lmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean overUi = this.isOverSidePanels(context);
        boolean toolDrag = StructurePickerClient.isScaleDragging() || StructurePickerClient.isPlacementDragging();
        boolean trackpadDrag = this.isCoordTrackpadDragging();
        boolean overHandle = StructurePickerClient.isOverPreciseHandle();
        boolean looking = this.orbitCamera.isDragging();

        if (!lmb)
        {
            this.suppressFreecamUntilLmbUp = false;
        }
        else if (!looking && (overUi || trackpadDrag))
        {
            /* LMB started on UI / trackpad: never steal look until release, even if the
             * cursor leaves the panel while dragging a size value. */
            this.suppressFreecamUntilLmbUp = true;
        }

        /* Once look has started, keep it until LMB release — do not steal to gizmos mid-drag. */
        boolean canContinueLook = lmb && !rmb && !overUi && !toolDrag && !trackpadDrag && !this.suppressFreecamUntilLmbUp;
        boolean canStartLook = canContinueLook && !overHandle;

        if (looking && canContinueLook)
        {
            this.orbitCamera.drag(context.mouseX, context.mouseY);
        }
        else if (canStartLook)
        {
            this.orbitCamera.start(0, context.mouseX, context.mouseY);
            context.unfocus();
        }
        else if (looking)
        {
            this.orbitCamera.release();
        }

        /* Held-key sync (not press/release edges) so WASD/Space never stick after release.
         * Over tools/modes: Shift must not lower the camera (Shift+click buttons). */
        if (context.isFocused() && !this.orbitCamera.isDragging())
        {
            this.orbitCamera.reset();
        }
        else
        {
            this.orbitCamera.syncFlightFromHeldKeys(overUi);
        }

        this.orbitCamera.update(context);
    }

    private boolean isCoordTrackpadDragging()
    {
        return this.selectionSizeX.isDragging()
            || this.selectionSizeY.isDragging()
            || this.selectionSizeZ.isDragging()
            || this.placementX.isDragging()
            || this.placementY.isDragging()
            || this.placementZ.isDragging()
            || this.sameBlockLimit.isDragging()
            || this.brushRadius.isDragging()
            || this.brushDepth.isDragging();
    }

    private float getOpenAmount()
    {
        UIElement parent = this.getParent();

        if (parent instanceof UIOverlay)
        {
            return ((UIOverlay) parent).getOpenTransition();
        }

        return 1.0F;
    }

    @Override
    protected void beginOpenTransition(UIContext context, float transition)
    {
        /* Per-panel slides are applied in tools/modes render — no center scale. */
    }

    @Override
    protected void endOpenTransition(UIContext context, float transition)
    {
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        /* Transparent shell — side panels paint their own fill. */
    }

    @Override
    public void render(UIContext context)
    {
        this.syncSameLimitBar();
        this.updateFreecamLook(context);
        super.render(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(Keys.CLOSE))
        {
            if (StructurePickerClient.isPlacementActive())
            {
                StructurePickerClient.cancelPlacement();
                StructurePickerClient.clearBoundStructurePath();
                this.syncPlacementButtons();

                return true;
            }

            /* Close the whole menu immediately — overlay-only close leaves UIScreen
               forcing hudHidden=true every frame (looks stuck in F1). */
            if (this.exitMenu != null)
            {
                this.exitMenu.run();

                return true;
            }

            UIBaseMenu menu = UIScreen.getCurrentMenu();

            if (menu != null)
            {
                menu.closeThisMenu();

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    private boolean isOverSidePanels(UIContext context)
    {
        return this.modesPanel.area.isInside(context) || this.toolsPanel.area.isInside(context);
    }

    private void syncSameLimitBar()
    {
        StructurePickerMode mode = StructurePickerClient.getMode();
        boolean placing = StructurePickerClient.isPlacementActive();
        boolean showSame = mode == StructurePickerMode.SAME && !placing;
        boolean showBrush = mode == StructurePickerMode.BRUSH && !placing;

        this.sameBlockLimit.setVisible(showSame);
        this.brushControls.setVisible(showBrush);

        if (showSame && !this.sameBlockLimit.isFocused() && !this.sameBlockLimit.isDragging())
        {
            int limit = StructurePickerClient.getSameBlockLimit();

            if ((int) this.sameBlockLimit.getValue() != limit)
            {
                this.sameBlockLimit.setValue(limit);
            }
        }

        if (showBrush)
        {
            int shape = StructurePickerClient.getBrushShape().index;

            if (this.brushShape.getValue() != shape)
            {
                this.brushShape.setValue(shape);
            }

            if (!this.brushRadius.isFocused() && !this.brushRadius.isDragging())
            {
                int radius = StructurePickerClient.getBrushRadius();

                if ((int) this.brushRadius.getValue() != radius)
                {
                    this.brushRadius.setValue(radius);
                }
            }

            if (!this.brushDepth.isFocused() && !this.brushDepth.isDragging())
            {
                int depth = StructurePickerClient.getBrushDepth();

                if ((int) this.brushDepth.getValue() != depth)
                {
                    this.brushDepth.setValue(depth);
                }
            }
        }
    }

    private void syncPlacementButtons()
    {
        boolean placing = StructurePickerClient.isPlacementActive();
        boolean showSize = !placing && StructurePickerClient.hasActiveCubeSelection();
        boolean canSave = StructurePickerClient.canSaveBoundStructure();

        this.placeStructureButton.setVisible(!placing);
        this.saveStructureButton.setVisible(!placing);
        this.saveStructureButton.setEnabled(canSave);

        if (canSave)
        {
            this.saveStructureButton.removeTooltip();
        }
        else
        {
            this.saveStructureButton.tooltip(UIKeys.STRUCTURE_PICKER_SAVE_STRUCTURE_HINT, Direction.LEFT);
        }
        this.placeAndSelectButton.setVisible(placing);
        this.placeCancelButton.setVisible(placing);
        this.placementCoords.setVisible(placing);
        this.selectionSizeCoords.setVisible(showSize);
        this.removeSelectionButton.setVisible(!placing);
        this.breakSelectionButton.setVisible(!placing);
        this.clickOnAirToggle.setVisible(!placing);
        this.subtractToggle.setVisible(!placing);

        this.modelBlockButton.setEnabled(!placing);
        this.modePicker.setEnabled(!placing);
        this.sameBlockLimit.setEnabled(!placing);
        this.brushControls.setEnabled(!placing);
        this.nameBox.setEnabled(!placing);
        this.updateImportButtons();
        this.syncSameLimitBar();

        if (placing)
        {
            this.importFilmButton.setEnabled(false);
            this.syncPlacementCoords();
        }
        else if (showSize)
        {
            StructurePickerClient.ensureCubeScaleGizmo();
            this.syncSelectionSize();
        }

        this.syncBoundStructureName();
    }

    private void syncBoundStructureName()
    {
        String path = StructurePickerClient.getBoundStructurePath();

        if (path == null || path.isEmpty())
        {
            return;
        }

        String display = StructurePickerExporter.displayNameOf(null, path);

        if (!display.isEmpty())
        {
            this.nameBox.setText(display);
        }
    }

    private void saveBoundStructure()
    {
        if (!StructurePickerClient.canSaveBoundStructure())
        {
            return;
        }

        StructurePickerClient.saveBoundStructure(this.getStructureName());
    }

    private void syncPlacementCoords()
    {
        BlockPos origin = StructurePickerClient.getPlacementOrigin();

        if (origin == null)
        {
            return;
        }

        this.syncingPlacementCoords = true;
        this.placementX.setValue(origin.getX());
        this.placementY.setValue(origin.getY());
        this.placementZ.setValue(origin.getZ());
        this.syncingPlacementCoords = false;
    }

    private void syncSelectionSize()
    {
        if (!StructurePickerClient.hasActiveCubeSelection())
        {
            return;
        }

        this.syncingSelectionSize = true;
        this.selectionSizeX.setValue(StructurePickerClient.getActiveSelectionSizeX());
        this.selectionSizeY.setValue(StructurePickerClient.getActiveSelectionSizeY());
        this.selectionSizeZ.setValue(StructurePickerClient.getActiveSelectionSizeZ());
        this.syncingSelectionSize = false;
    }

    private void applyPlacementCoord()
    {
        if (this.syncingPlacementCoords || !StructurePickerClient.isPlacementActive())
        {
            return;
        }

        StructurePickerClient.setPlacementOriginCoords(
            (int) this.placementX.getValue(),
            (int) this.placementY.getValue(),
            (int) this.placementZ.getValue()
        );
    }

    private void applySelectionSize()
    {
        if (this.syncingSelectionSize || StructurePickerClient.isPlacementActive())
        {
            return;
        }

        StructurePickerClient.setActiveSelectionSize(
            (int) this.selectionSizeX.getValue(),
            (int) this.selectionSizeY.getValue(),
            (int) this.selectionSizeZ.getValue()
        );
        this.syncSelectionSize();
    }

    private void browseStructures()
    {
        UIStructureOverlayPanel[] holder = new UIStructureOverlayPanel[1];

        holder[0] = new UIStructureOverlayPanel((Link link) ->
        {
            if (link == null)
            {
                return;
            }

            StructurePickerClient.startPlacement(link.toString());
            this.syncPlacementButtons();

            if (holder[0] != null)
            {
                holder[0].close();
            }
        }, this.getContext());

        UIOverlay.addOverlay(this.getContext(), holder[0], 280, 0.5F);
    }

    private void updateImportButtons()
    {
        UIFilmPanel filmPanel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
        boolean canImport = filmPanel != null && filmPanel.getData() != null;

        this.importFilmButton.setEnabled(canImport);

        if (canImport)
        {
            this.importFilmButton.removeTooltip();
        }
        else
        {
            this.importFilmButton.tooltip(UIKeys.STRUCTURE_PICKER_IMPORT_FILM_DISABLED, Direction.LEFT);
        }
    }

    private String getStructureName()
    {
        return this.nameBox.getText() == null ? "" : this.nameBox.getText().trim();
    }

    private void importSelection(boolean toModelBlock)
    {
        if (!toModelBlock)
        {
            UIFilmPanel filmPanel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

            if (filmPanel == null || filmPanel.getData() == null)
            {
                return;
            }
        }

        StructurePickerClient.importSelection(toModelBlock, this.getStructureName());
    }

    private void removeSelection()
    {
        if (Window.isShiftPressed())
        {
            StructurePickerClient.removeSelection();
            this.syncPlacementButtons();

            return;
        }

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
            UIKeys.STRUCTURE_PICKER_REMOVE_SELECTION,
            UIKeys.STRUCTURE_PICKER_REMOVE_CONFIRM,
            (confirmed) ->
            {
                if (confirmed)
                {
                    StructurePickerClient.removeSelection();
                    this.syncPlacementButtons();
                }
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void breakSelection()
    {
        if (Window.isShiftPressed())
        {
            StructurePickerClient.breakSelection();
            this.close();

            return;
        }

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
            UIKeys.STRUCTURE_PICKER_BREAK_SELECTION,
            UIKeys.STRUCTURE_PICKER_BREAK_CONFIRM,
            (confirmed) ->
            {
                if (confirmed)
                {
                    StructurePickerClient.breakSelection();
                    this.close();
                }
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel);
    }
}
