package mchorse.bbs_mod.ui.model_blocks;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.ModelBlock;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.forms.UIToggleEditorEvent;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.model_blocks.camera.ImmersiveModelBlockCameraController;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoController;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoSurface;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIModelBlockPanel extends UIDashboardPanel implements IFlightSupported, GizmoSurface {
    public static boolean toggleRendering;

    /*
     * Region constants: docked cards live in one of two regions. LEFT region
     * columns pack from the screen's left edge rightward; RIGHT region columns
     * pack from the right edge leftward. The gap between them shows the world.
     * Each region can hold any number of columns, and each column any number of
     * vertically stacked cards — so windows can be docked side-by-side freely.
     */
    private static final int SIDE_LEFT = 0;
    private static final int SIDE_RIGHT = 1;

    /*
     * Default layout: Blocks alone in the LEFT region; Transforms + Properties
     * stacked in a single RIGHT-region column (Transforms on top).
     */
    private int leftCardX = 10;
    private int leftCardY = 10;
    private int leftCardWidth = 220;
    private int leftCardHeight = 400;
    private boolean leftCollapsed = false;
    private boolean leftDocked = true;
    private int leftDockSide = SIDE_LEFT;
    private int leftDockColumn = 0;
    private int leftDockRow = 0;
    private boolean leftVisible = true;

    private int middleCardX = 470;
    private int middleCardY = 410;
    private int middleCardWidth = 240;
    private int middleCardHeight = 400;
    private boolean middleCollapsed = false;
    private boolean middleDocked = true;
    private int middleDockSide = SIDE_RIGHT;
    private int middleDockColumn = 0;
    private int middleDockRow = 1;
    private boolean middleVisible = true;

    private int rightCardX = 470;
    private int rightCardY = 10;
    private int rightCardWidth = 240;
    private int rightCardHeight = 160;
    private boolean rightCollapsed = false;
    private boolean rightDocked = true;
    private int rightDockSide = SIDE_RIGHT;
    private int rightDockColumn = 0;
    private int rightDockRow = 0;
    private boolean rightVisible = true;

    private float leftWeight = 1.0F;
    private float middleWeight = 1.0F;
    private float rightWeight = 1.0F;

    /*
     * Snapshot of all card positions at the moment a drag starts. Used so the
     * dock cubes can stay anchored to where each window WAS before the layout
     * restacked itself — otherwise the remaining cards stretch and the cubes
     * end up at the column center instead of the user-visible card centers.
     */
    private final boolean[] prevDragging = new boolean[3];
    private final int[] snapX = new int[3];
    private final int[] snapY = new int[3];
    private final int[] snapW = new int[3];
    private final int[] snapH = new int[3];
    private boolean dragSnapshotValid;

    // Split scroll views (middle/right cards; the left card hosts the list
    // directly)
    public UIScrollView middleScrollView;
    public UIScrollView rightScrollView;

    // Split editors
    public UIElement middleEditor;
    public UIElement rightEditor;

    // Draggable headers
    public UIPanelDragHandle leftDragHandle;
    public UIPanelDragHandle middleDragHandle;
    public UIPanelDragHandle rightDragHandle;

    // Card resizers
    public UIDraggable leftCardResizer;
    public UIDraggable middleCardResizer;
    public UIDraggable rightCardResizer;

    public UIModelBlockEntityList modelBlocks;
    public UINestedEdit pickEdit;
    public UIToggle enabled;
    public UIToggle shadow;
    public UIToggle hitbox;
    public UIToggle global;
    public UIToggle lookAt;
    public UIToggle chromaSky;
    public UIToggle localLighting;
    public UITrackpad lightLevel;
    public UITrackpad hardness;
    public UIPropTransform transform;
    public UIItemStack mainHand;
    public UIItemStack offHand;
    public UIItemStack armorHead;
    public UIItemStack armorChest;
    public UIItemStack armorLegs;
    public UIItemStack armorFeet;
    public UIElement properties;

    private ModelBlockEntity modelBlock;
    private ModelBlockEntity hovered;
    private Vector3f mouseDirection = new Vector3f();

    /*
     * Real (pickable/draggable) gizmo shown at the edited model block. It renders in world
     * space during renderInWorld, picks through its own stencil framebuffer, and drags the
     * same UIPropTransform that backs the Transforms card, so the numbers update live.
     */
    private StencilFormFramebuffer gizmoStencil = new StencilFormFramebuffer();
    private StencilMap gizmoStencilMap = new StencilMap();
    private GizmoController gizmoController = new GizmoController(this);
    private final Matrix4f gizmoWorldMatrix = new Matrix4f();
    private final Matrix4f gizmoProjection = new Matrix4f();
    private final Matrix4f gizmoInterfaceMatrix = new Matrix4f();
    private final Vector3d gizmoCameraPosition = new Vector3d();
    private boolean hasGizmo;
    private boolean hasGizmoInterfaceMatrix;

    private Set<ModelBlockEntity> toSave = new HashSet<>();

    private ImmersiveModelBlockCameraController cameraController;
    private UIElement keyDude;

    private UndoManager<UIModelBlockPanel> undoManager = new UndoManager<>(100);
    private MapType pendingUndoBefore;

    public UIModelBlockPanel(UIDashboard dashboard) {
        super(dashboard);

        this.keyDude = new UIElement().noCulling();
        this.keyDude.keys().register(Keys.MODEL_BLOCKS_MOVE_TO, () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            Camera camera = mc.gameRenderer.getCamera();
            BlockHitResult blockHitResult = RayTracing.rayTrace(mc.world, camera.getPos(),
                    RayTracing.fromVector3f(this.mouseDirection), 512F);

            if (blockHitResult.getType() != HitResult.Type.MISS) {
                Vec3d hit = blockHitResult.getPos();
                BlockPos pos = this.modelBlock.getPos();

                this.modelBlock.getProperties().getTransform().translate.set(hit.x - pos.getX() - 0.5F,
                        hit.y - pos.getY(), hit.z - pos.getZ() - 0.5F);
                this.fillData();
            }
        }).active(() -> this.modelBlock != null);

        this.modelBlocks = new UIModelBlockEntityList((l) -> this.fill(l.get(0), false)) {
            @Override
            public void render(UIContext context) {
                /* Inset bordered panel so the list reads as a distinct surface. */
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF141418);
                super.render(context);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
            }
        };
        this.modelBlocks.context((menu) -> {
            if (this.modelBlock != null) {
                menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, this::renameModelBlock);
                menu.action(UIKeys.MODEL_BLOCKS_KEYS_TELEPORT, this::teleport);
            }
        });
        this.modelBlocks.background(0);

        this.pickEdit = new UINestedEdit((editing) -> {
            UIFormPalette palette = UIFormPalette.open(this, editing, this.modelBlock.getProperties().getForm(),
                    (f) -> {
                        this.beginUndoCapture();
                        this.pickEdit.setForm(f);
                        this.modelBlock.getProperties().setForm(f);
                        /* While the nested form editor is open, keep the shared live form and
                         * defer network sync. Saving now round-trips fromData() and replaces
                         * the form instance the editor is mutating, so F7 shows stale transforms. */
                        if (this.isEditing(this.modelBlock))
                        {
                            this.toSave.add(this.modelBlock);
                        }

                        /* Always finish the undo entry so a later gizmo drag cannot keep the
                         * pre-form snapshot in pendingUndoBefore and merge form+transform. */
                        this.endUndoCapture();
                    });

            palette.immersive();
            /* Match Transformaciones/morphing: keep the world visible behind the form list
             * instead of the default near-opaque palette scrim. */
            palette.noBackground();
            palette.editor.keys().register(Keys.MODEL_BLOCKS_TOGGLE_RENDERING,
                    () -> toggleRendering = !toggleRendering);
            palette.editor.renderer.full(dashboard.getRoot());
            palette.editor.renderer.setTarget(this.modelBlock.getEntity());
            /* Interaction (gizmos / bone pick) can stay on with F7 when the setting allows it;
             * the mesh itself is still suppressed so the world copy is not doubled. */
            palette.editor.renderer.setRenderForm(() ->
                !toggleRendering || BBSSettings.gizmosWorldRendering.get());
            palette.editor.renderer.setRenderFormMesh(() -> !toggleRendering);
            palette.getEvents().register(UIToggleEditorEvent.class, (e) ->
            {
                if (e.editing)
                {
                    this.addCameraController(palette);
                }
                else
                {
                    this.removeCameraController();
                }
            });
            palette.getEvents().register(UIRemovedEvent.class, (e) ->
            {
                /* resize() recomputes every card's visibility from scratch. */
                this.resize();
            });

            palette.resize();

            /* Stop stuck WASD/flight velocity from the world view behind the palette. */
            this.dashboard.orbit.reset();
            this.dashboard.orbit.release();

            if (editing) {
                this.addCameraController(palette);
            }

            /*
             * Hide every card while the form palette is open. pickEdit lives
             * inside middleScrollView now so it follows that visibility.
             */
            this.modelBlocks.setVisible(false);
            this.leftDragHandle.setVisible(false);
            this.leftCardResizer.setVisible(false);
            this.middleScrollView.setVisible(false);
            this.middleDragHandle.setVisible(false);
            this.middleCardResizer.setVisible(false);
            this.rightScrollView.setVisible(false);
            this.rightDragHandle.setVisible(false);
            this.rightCardResizer.setVisible(false);
        });
        this.pickEdit.keybinds();

        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setEnabled(b.getValue());
            this.endUndoCapture();
        });
        this.shadow = new UIToggle(UIKeys.MODEL_BLOCKS_SHADOW, (b) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setShadow(b.getValue());
            this.endUndoCapture();
        });
        this.hitbox = new UIToggle(UIKeys.MODEL_BLOCKS_HITBOX, (b) -> {
            if (this.modelBlock == null)
                return;

            this.beginUndoCapture();
            this.modelBlock.getProperties().setHitbox(b.getValue());
            this.endUndoCapture();
        });
        this.global = new UIToggle(UIKeys.MODEL_BLOCKS_GLOBAL, (b) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setGlobal(b.getValue());
            MinecraftClient.getInstance().worldRenderer.reload();
            this.endUndoCapture();
        });
        this.lookAt = new UIToggle(UIKeys.CAMERA_PANELS_LOOK_AT, (b) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setLookAt(b.getValue());
            this.endUndoCapture();
        });
        this.chromaSky = new UIToggle(UIKeys.MODEL_BLOCKS_CHROMA_SKY, (b) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setChromaSky(b.getValue());
            this.endUndoCapture();
        });
        this.chromaSky.tooltip(UIKeys.MODEL_BLOCKS_CHROMA_SKY_TOOLTIP);
        this.localLighting = new UIToggle(UIKeys.MODEL_BLOCKS_LOCAL_LIGHTING, (b) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setLocalLighting(b.getValue());
            this.endUndoCapture();
        });
        this.localLighting.tooltip(UIKeys.MODEL_BLOCKS_LOCAL_LIGHTING_TOOLTIP);

        this.lightLevel = new UITrackpad((v) -> {
            if (this.modelBlock == null)
                return;

            this.beginUndoCapture();
            int lvl = v.intValue();

            this.modelBlock.getProperties().setLightLevel(lvl);

            try {
                MinecraftClient mc = MinecraftClient.getInstance();

                if (mc.world != null) {
                    BlockPos p = this.modelBlock.getPos();
                    BlockState state = mc.world.getBlockState(p);

                    mc.world.setBlockState(p, state.with(ModelBlock.LIGHT_LEVEL, lvl), Block.NOTIFY_LISTENERS);
                }
            } catch (Exception e) {

            }

            this.endUndoCapture();
        }).integer().limit(0, 15);

        this.lightLevel.textbox.setColor(Colors.YELLOW);
        this.lightLevel.w(1F);

        this.hardness = new UITrackpad((v) -> {
            if (this.modelBlock == null)
                return;

            this.beginUndoCapture();
            this.modelBlock.getProperties().setHardness(v.floatValue());
            this.endUndoCapture();
        }).limit(0, 50);
        this.hardness.w(1F);
        this.hardness.textbox.setColor(Colors.PINK);

        this.transform = new UIPropTransform();
        this.transform.callbacks(this::beginUndoCapture, this::endUndoCapture);
        this.transform.enableHotkeys().marginBottom(4);

        this.mainHand = new UIItemStack((stack) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setItemMainHand(stack);
            this.endUndoCapture();
        }).optionsOnLeft(true).optionsIcon(Icons.MAIN_HAND);
        this.offHand = new UIItemStack((stack) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setItemOffHand(stack);
            this.endUndoCapture();
        }).optionsOnLeft(true).optionsIcon(Icons.SECONDARY_HAND);
        this.armorHead = new UIItemStack((stack) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setArmorHead(stack);
            this.endUndoCapture();
        }).optionsOnLeft(true).optionsIcon(Icons.HELMET);
        this.armorChest = new UIItemStack((stack) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setArmorChest(stack);
            this.endUndoCapture();
        }).optionsOnLeft(true).optionsIcon(Icons.CHESTPLATE);
        this.armorLegs = new UIItemStack((stack) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setArmorLegs(stack);
            this.endUndoCapture();
        }).optionsOnLeft(true).optionsIcon(Icons.LEGINGS);
        this.armorFeet = new UIItemStack((stack) -> {
            if (this.modelBlock == null)
                return;
            this.beginUndoCapture();
            this.modelBlock.getProperties().setArmorFeet(stack);
            this.endUndoCapture();
        }).optionsOnLeft(true).optionsIcon(Icons.BOOTS);

        UIElement mainHandColumn = UI.column(2, UI.label(UIKeys.MODELS_ITEMS_MAIN), this.mainHand);
        UIElement offHandColumn = UI.column(2, UI.label(UIKeys.MODELS_ITEMS_OFF), this.offHand);
        UIElement armorHeadColumn = UI.column(2, UI.label(UIKeys.MODEL_BLOCKS_ARMOR_HEAD), this.armorHead);
        UIElement armorChestColumn = UI.column(2, UI.label(UIKeys.MODEL_BLOCKS_ARMOR_CHEST),
                this.armorChest);
        UIElement armorLegsColumn = UI.column(2, UI.label(UIKeys.MODEL_BLOCKS_ARMOR_LEGS), this.armorLegs);
        UIElement armorFeetColumn = UI.column(2, UI.label(UIKeys.MODEL_BLOCKS_ARMOR_FEET), this.armorFeet);

        /* Equipment is laid out as a 3-column grid (two rows). */
        float equipColumn = 1F / 3F;
        mainHandColumn.w(equipColumn, -3);
        offHandColumn.w(equipColumn, -3);
        armorHeadColumn.w(equipColumn, -3);
        armorChestColumn.w(equipColumn, -3);
        armorLegsColumn.w(equipColumn, -3);
        armorFeetColumn.w(equipColumn, -3);

        UIElement lightIcon = new UIElement() {
            @Override
            public void render(UIContext context) {
                super.render(context);

                context.batcher.icon(Icons.LIGHT, Colors.WHITE, this.area.mx(), this.area.my(), 0.5F, 0.5F);
            }
        }.w(20).h(20);

        UIElement hardnessIcon = new UIElement() {
            @Override
            public void render(UIContext context) {
                super.render(context);

                context.batcher.icon(Icons.PICKAXE, Colors.WHITE, this.area.mx(), this.area.my(), 0.5F, 0.5F);
            }
        }.w(20).h(20);

        /* The two block sliders sit side by side, each half-width. */
        UIElement lightGroup = UI.row(5, 0, 20, lightIcon, this.lightLevel);
        UIElement hardnessGroup = UI.row(5, 0, 20, hardnessIcon, this.hardness);

        lightGroup.w(0.5F, -2);
        hardnessGroup.w(0.5F, -2);

        /*
         * Grouped layout: Form (Pick/Edit) first, then toggles 2-per-row,
         * the two block sliders side by side, then equipment as a 3-column grid.
         */
        this.properties = UI.column(5,
                this.sectionHeader(UIKeys.MODEL_BLOCKS_FORM),
                this.pickEdit,
                this.sectionHeader(UIKeys.MODEL_BLOCKS_DISPLAY),
                UI.row(4, this.enabled, this.shadow),
                UI.row(4, this.global, this.lookAt),
                UI.row(4, this.hitbox, this.chromaSky),
                UI.row(4, this.localLighting),
                this.sectionHeader(UIKeys.MODEL_BLOCKS_BLOCK),
                UI.row(4, lightGroup, hardnessGroup),
                this.sectionHeader(UIKeys.MODEL_BLOCKS_EQUIPMENT),
                UI.row(4, armorHeadColumn, armorChestColumn, mainHandColumn),
                UI.row(4, armorLegsColumn, armorFeetColumn, offHandColumn));

        this.lightLevel.tooltip(UIKeys.MODEL_BLOCKS_LIGHT_LEVEL, Direction.BOTTOM);
        this.hardness.tooltip(UIKeys.MODEL_BLOCKS_HARDNESS, Direction.BOTTOM);

        /*
         * Left card content: the self-scrolling list fills the card body and the
         * Pick/Edit buttons are pinned to the bottom (positioned in resize()).
         */

        this.leftDragHandle = new UIPanelDragHandle(
                UIKeys.MODEL_BLOCKS_TITLE,
                Icons.BLOCK,
                () -> this.leftCollapsed,
                () -> {
                    this.leftCollapsed = !this.leftCollapsed;
                    this.resize();
                    this.saveLayout();
                },
                (context) -> {
                    this.leftDocked = false;
                    this.leftCardX = context.mouseX - this.area.x;
                    this.leftCardY = context.mouseY - this.area.y;
                    this.clampPositions();
                    this.resize();
                });
        this.leftDragHandle.reference(() -> new Vector2i(this.area.x + this.leftCardX, this.area.y + this.leftCardY));
        this.leftDragHandle.threshold(4);
        this.leftDragHandle.dragEnd(() -> {
            handleDragEnd(0, this.getContext());
            this.saveLayout();
        });

        this.leftCardResizer = new UIDraggable((context) -> {
            if (this.leftDocked) {
                int below = getCardBelow(0);
                if (below != -1 && !this.leftCollapsed && !isCardCollapsed(below)) {
                    resizeCardHeight(0, below, context.mouseY);
                } else {
                    resizeCardWidth(0, context.mouseX);
                }
            } else {
                int minWidth = 180;
                int maxWidth = Math.max(minWidth, this.area.w / 2);
                this.leftCardWidth = Math.max(minWidth,
                        Math.min(maxWidth, context.mouseX - (this.area.x + this.leftCardX)));
                int minHeight = 100;
                int maxHeight = this.area.h - this.leftCardY;
                this.leftCardHeight = Math.max(minHeight,
                        Math.min(maxHeight, context.mouseY - (this.area.y + this.leftCardY)));
            }

            this.resize();
        });
        this.leftCardResizer.rendering((c) -> renderResizer(0, this.leftCardResizer, c));
        this.leftCardResizer.dragEnd(this::saveLayout);

        // Assembly Middle Card content (Properties Window)
        this.middleEditor = this.properties;
        this.middleScrollView = UI.scrollView(5, 12, this.middleEditor);
        this.middleScrollView.scroll.opposite().cancelScrolling();

        this.middleDragHandle = new UIPanelDragHandle(
                UIKeys.MODEL_BLOCKS_PROPERTIES,
                Icons.PROPERTIES,
                () -> this.middleCollapsed,
                () -> {
                    this.middleCollapsed = !this.middleCollapsed;
                    this.resize();
                    this.saveLayout();
                },
                (context) -> {
                    this.middleDocked = false;
                    this.middleCardX = context.mouseX - this.area.x;
                    this.middleCardY = context.mouseY - this.area.y;
                    this.clampPositions();
                    this.resize();
                });
        this.middleDragHandle
                .reference(() -> new Vector2i(this.area.x + this.middleCardX, this.area.y + this.middleCardY));
        this.middleDragHandle.threshold(4);
        this.middleDragHandle.dragEnd(() -> {
            handleDragEnd(1, this.getContext());
            this.saveLayout();
        });

        this.middleCardResizer = new UIDraggable((context) -> {
            if (this.middleDocked) {
                int below = getCardBelow(1);
                if (below != -1 && !this.middleCollapsed && !isCardCollapsed(below)) {
                    resizeCardHeight(1, below, context.mouseY);
                } else {
                    resizeCardWidth(1, context.mouseX);
                }
            } else {
                int minWidth = 180;
                int maxWidth = Math.max(minWidth, this.area.w / 2);
                this.middleCardWidth = Math.max(minWidth,
                        Math.min(maxWidth, context.mouseX - (this.area.x + this.middleCardX)));
                int minHeight = 100;
                int maxHeight = this.area.h - this.middleCardY;
                this.middleCardHeight = Math.max(minHeight,
                        Math.min(maxHeight, context.mouseY - (this.area.y + this.middleCardY)));
            }

            this.resize();
        });
        this.middleCardResizer.rendering((c) -> renderResizer(1, this.middleCardResizer, c));
        this.middleCardResizer.dragEnd(this::saveLayout);

        // Assembly Right Card content
        this.rightEditor = UI.column(4,
                this.transform);
        this.rightScrollView = UI.scrollView(5, 12, this.rightEditor);
        this.rightScrollView.scroll.opposite().cancelScrolling();

        this.rightDragHandle = new UIPanelDragHandle(
                UIKeys.MODEL_BLOCKS_TRANSFORMS,
                Icons.GEAR,
                () -> this.rightCollapsed,
                () -> {
                    this.rightCollapsed = !this.rightCollapsed;
                    this.resize();
                    this.saveLayout();
                },
                (context) -> {
                    this.rightDocked = false;
                    this.rightCardX = context.mouseX - this.area.x;
                    this.rightCardY = context.mouseY - this.area.y;
                    this.clampPositions();
                    this.resize();
                });
        this.rightDragHandle
                .reference(() -> new Vector2i(this.area.x + this.rightCardX, this.area.y + this.rightCardY));
        this.rightDragHandle.threshold(4);
        this.rightDragHandle.dragEnd(() -> {
            handleDragEnd(2, this.getContext());
            this.saveLayout();
        });

        this.rightCardResizer = new UIDraggable((context) -> {
            if (this.rightDocked) {
                int below = getCardBelow(2);
                if (below != -1 && !this.rightCollapsed && !isCardCollapsed(below)) {
                    resizeCardHeight(2, below, context.mouseY);
                } else {
                    resizeCardWidth(2, context.mouseX);
                }
            } else {
                int minWidth = 180;
                int maxWidth = Math.max(minWidth, this.area.w / 2);
                this.rightCardWidth = Math.max(minWidth,
                        Math.min(maxWidth, context.mouseX - (this.area.x + this.rightCardX)));
                int minHeight = 100;
                int maxHeight = this.area.h - this.rightCardY;
                this.rightCardHeight = Math.max(minHeight,
                        Math.min(maxHeight, context.mouseY - (this.area.y + this.rightCardY)));
            }

            this.resize();
        });
        this.rightCardResizer.rendering((c) -> renderResizer(2, this.rightCardResizer, c));
        this.rightCardResizer.dragEnd(this::saveLayout);

        this.loadLayout();

        this.fill(null, false);

        this.keys().register(Keys.MODEL_BLOCKS_TELEPORT, this::teleport);
        this.keys().register(Keys.UNDO, this::undoModelBlock).active(() -> this.modelBlock != null);
        this.keys().register(Keys.REDO, this::redoModelBlock).active(() -> this.modelBlock != null);

        this.leftDragHandle.relative(this);
        this.modelBlocks.relative(this);
        this.leftCardResizer.relative(this);
        this.middleDragHandle.relative(this);
        this.middleScrollView.relative(this);
        this.middleCardResizer.relative(this);
        this.rightDragHandle.relative(this);
        this.rightScrollView.relative(this);
        this.rightCardResizer.relative(this);

        /*
         * Add resizers LAST so they win mouse-hit priority (children are
         * iterated in reverse for clicks). The boundary resizer straddles two
         * cards; without this order the lower card's drag handle would eat the
         * click.
         */
        this.add(this.leftDragHandle, this.modelBlocks,
                this.middleDragHandle, this.middleScrollView,
                this.rightDragHandle, this.rightScrollView,
                this.leftCardResizer, this.middleCardResizer, this.rightCardResizer);
    }

    /*
     * Section header for the Properties window — matches the category titles in
     * the Settings panel: primary-color text on a dark background, 20px tall,
     * bottom-left aligned, with breathing room above.
     */
    private UIElement sectionHeader(IKey label) {
        UILabel header = UI.label(label)
                .labelAnchor(0, 1)
                .color(0xFF000000 | BBSSettings.accentRgb())
                .background(() -> 0xFF1A1A22);

        header.h(20).marginTop(8);

        return header;
    }

    private int getCardX(int i) {
        if (i == 0)
            return this.leftCardX;
        if (i == 1)
            return this.middleCardX;
        return this.rightCardX;
    }

    private void setCardX(int i, int x) {
        if (i == 0)
            this.leftCardX = x;
        else if (i == 1)
            this.middleCardX = x;
        else
            this.rightCardX = x;
    }

    private int getCardY(int i) {
        if (i == 0)
            return this.leftCardY;
        if (i == 1)
            return this.middleCardY;
        return this.rightCardY;
    }

    private void setCardY(int i, int y) {
        if (i == 0)
            this.leftCardY = y;
        else if (i == 1)
            this.middleCardY = y;
        else
            this.rightCardY = y;
    }

    private int getCardWidth(int i) {
        if (i == 0)
            return this.leftCardWidth;
        if (i == 1)
            return this.middleCardWidth;
        return this.rightCardWidth;
    }

    private void setCardWidth(int i, int w) {
        if (i == 0)
            this.leftCardWidth = w;
        else if (i == 1)
            this.middleCardWidth = w;
        else
            this.rightCardWidth = w;
    }

    /*
     * Width is shared across all docked cards in a column (clampPositions sizes
     * the column to the max width of its members). When the user drags a width
     * resizer we must update every card in that column, otherwise a stale wider
     * sibling stops the column from shrinking.
     */
    private void setColumnWidth(int side, int column, int w) {
        for (int i = 0; i < 3; i++) {
            if (isCardVisible(i) && isCardDocked(i)
                    && getCardDockSide(i) == side && getCardDockColumn(i) == column) {
                setCardWidth(i, w);
            }
        }
    }

    /* Resize a docked card's column width by dragging its outer-edge bar. */
    private void resizeCardWidth(int i, int mouseX) {
        int minWidth = 180;
        int maxWidth = Math.max(minWidth, this.area.w / 2);
        int cardX = getCardX(i);
        int cardW = getCardWidth(i);
        int localMouseX = mouseX - this.area.x;

        int newW;
        if (getCardDockSide(i) == SIDE_LEFT) {
            /* Resizer on the right edge: width grows as the mouse moves right. */
            newW = localMouseX - cardX;
        } else {
            /* Resizer on the left edge: width grows as the mouse moves left. */
            newW = (cardX + cardW) - localMouseX;
        }

        newW = Math.max(minWidth, Math.min(maxWidth, newW));
        setColumnWidth(getCardDockSide(i), getCardDockColumn(i), newW);
    }

    /* Resize the boundary between a docked card and the one below it (weights). */
    private void resizeCardHeight(int i, int below, int mouseY) {
        int hA = getCardHeight(i);
        int hB = getCardHeight(below);
        int hTotal = hA + hB;
        int new_hA = mouseY - (this.area.y + getCardY(i));
        new_hA = Math.max(40, Math.min(hTotal - 40, new_hA));
        int new_hB = hTotal - new_hA;
        float weightTotal = getCardWeight(i) + getCardWeight(below);
        setCardWeight(i, weightTotal * ((float) new_hA / hTotal));
        setCardWeight(below, weightTotal * ((float) new_hB / hTotal));
    }

    /* Sorted, distinct column indices for docked+visible cards in a region. */
    private List<Integer> columnsOnSide(int side) {
        TreeSet<Integer> cols = new TreeSet<>();
        for (int i = 0; i < 3; i++) {
            if (isCardVisible(i) && isCardDocked(i) && getCardDockSide(i) == side) {
                cols.add(getCardDockColumn(i));
            }
        }
        return new ArrayList<>(cols);
    }

    /* Card indices in a given region+column, sorted top-to-bottom by row. */
    private List<Integer> cardsInColumn(int side, int column) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (isCardVisible(i) && isCardDocked(i)
                    && getCardDockSide(i) == side && getCardDockColumn(i) == column) {
                list.add(i);
            }
        }
        list.sort((a, b) -> Integer.compare(getCardDockRow(a), getCardDockRow(b)));
        return list;
    }

    /*
     * Densify column and row indices (0,1,2…) so insertions/removals don't leave
     * gaps. Safe to call repeatedly — it's a no-op on already-dense layouts.
     */
    private void normalizeDockOrder() {
        for (int side = 0; side <= SIDE_RIGHT; side++) {
            List<Integer> cols = columnsOnSide(side);
            for (int newCol = 0; newCol < cols.size(); newCol++) {
                int oldCol = cols.get(newCol);
                List<Integer> cards = cardsInColumn(side, oldCol);
                for (int row = 0; row < cards.size(); row++) {
                    setCardDockRow(cards.get(row), row);
                }
                /* Reassign column last so cardsInColumn above still matches. */
                for (int card : cards) {
                    setCardDockColumn(card, 100 + newCol);
                }
            }
        }
        /* Strip the +100 offset used to avoid clashes during remap. */
        for (int i = 0; i < 3; i++) {
            if (isCardVisible(i) && isCardDocked(i) && getCardDockColumn(i) >= 100) {
                setCardDockColumn(i, getCardDockColumn(i) - 100);
            }
        }
    }

    private int getCardHeight(int i) {
        if (i == 0)
            return this.leftCardHeight;
        if (i == 1)
            return this.middleCardHeight;
        return this.rightCardHeight;
    }

    private void setCardHeight(int i, int h) {
        if (i == 0)
            this.leftCardHeight = h;
        else if (i == 1)
            this.middleCardHeight = h;
        else
            this.rightCardHeight = h;
    }

    private boolean isCardCollapsed(int i) {
        if (i == 0)
            return this.leftCollapsed;
        if (i == 1)
            return this.middleCollapsed;
        return this.rightCollapsed;
    }

    private boolean isCardVisible(int i) {
        if (i == 0)
            return this.leftVisible;
        if (i == 1)
            return this.middleVisible;
        return this.rightVisible;
    }

    private boolean isCardDocked(int i) {
        if (i == 0)
            return this.leftDocked;
        if (i == 1)
            return this.middleDocked;
        return this.rightDocked;
    }

    private void setCardDocked(int i, boolean d) {
        if (i == 0)
            this.leftDocked = d;
        else if (i == 1)
            this.middleDocked = d;
        else
            this.rightDocked = d;
    }

    private int getCardDockSide(int i) {
        if (i == 0)
            return this.leftDockSide;
        if (i == 1)
            return this.middleDockSide;
        return this.rightDockSide;
    }

    private void setCardDockSide(int i, int side) {
        if (i == 0)
            this.leftDockSide = side;
        else if (i == 1)
            this.middleDockSide = side;
        else
            this.rightDockSide = side;
    }

    private int getCardDockColumn(int i) {
        if (i == 0)
            return this.leftDockColumn;
        if (i == 1)
            return this.middleDockColumn;
        return this.rightDockColumn;
    }

    private void setCardDockColumn(int i, int col) {
        if (i == 0)
            this.leftDockColumn = col;
        else if (i == 1)
            this.middleDockColumn = col;
        else
            this.rightDockColumn = col;
    }

    private int getCardDockRow(int i) {
        if (i == 0)
            return this.leftDockRow;
        if (i == 1)
            return this.middleDockRow;
        return this.rightDockRow;
    }

    private void setCardDockRow(int i, int row) {
        if (i == 0)
            this.leftDockRow = row;
        else if (i == 1)
            this.middleDockRow = row;
        else
            this.rightDockRow = row;
    }

    /*
     * A dock target: a clickable cube and the drop it represents. A zone either
     * targets an existing column (newColumn = false: stack into it, row decided
     * by drop Y) or inserts a brand-new column at slot `column` on `side`
     * (newColumn = true: side-by-side dock).
     */
    private static class DockZone {
        final int side;
        final int column;
        final boolean newColumn;
        final int cx;
        final int cy;

        DockZone(int side, int column, boolean newColumn, int cx, int cy) {
            this.side = side;
            this.column = column;
            this.newColumn = newColumn;
            this.cx = cx;
            this.cy = cy;
        }
    }

    private List<DockZone> computeDockZones(int draggingCard) {
        List<DockZone> zones = new ArrayList<>();
        boolean[] sideHasCard = new boolean[2];

        for (int j = 0; j < 3; j++) {
            if (j != draggingCard && isCardVisible(j) && isCardDocked(j)) {
                /*
                 * Use the pre-drag snapshot so cubes stay over the window the
                 * user is looking at, not over the restacked column.
                 */
                int cardX = this.dragSnapshotValid ? this.snapX[j] : getCardX(j);
                int cardY = this.dragSnapshotValid ? this.snapY[j] : getCardY(j);
                int cardW = this.dragSnapshotValid ? this.snapW[j] : getCardWidth(j);
                int cardH = this.dragSnapshotValid ? this.snapH[j] : getCardHeight(j);

                int side = getCardDockSide(j);
                int col = getCardDockColumn(j);
                sideHasCard[side] = true;

                int cx = this.area.x + cardX + cardW / 2;
                int cy = this.area.y + cardY + cardH / 2;

                /* Center cube: stack into this card's column. */
                zones.add(new DockZone(side, col, false, cx, cy));

                /*
                 * Edge cubes insert a new column. In the LEFT region a lower
                 * column index is further left; in the RIGHT region column 0 is
                 * the rightmost, so the slots are mirrored. leftSlot always
                 * lands visually to the left of the card, rightSlot to the
                 * right.
                 */
                int leftSlot = (side == SIDE_LEFT) ? col : col + 1;
                int rightSlot = (side == SIDE_LEFT) ? col + 1 : col;

                zones.add(new DockZone(side, leftSlot, true, this.area.x + cardX + 18, cy));
                zones.add(new DockZone(side, rightSlot, true, this.area.x + cardX + cardW - 18, cy));
            }
        }

        /* Each empty region gets a single cube at its screen edge. */
        int cy = this.area.y + this.area.h / 2;
        if (!sideHasCard[SIDE_LEFT]) {
            zones.add(new DockZone(SIDE_LEFT, 0, true, this.area.x + 110, cy));
        }
        if (!sideHasCard[SIDE_RIGHT]) {
            zones.add(new DockZone(SIDE_RIGHT, 0, true, this.area.ex() - 110, cy));
        }

        return zones;
    }

    /*
     * Apply a drop: place card i according to the zone. Other cards in the same
     * region shift to make room for new columns. dropY decides the row when
     * stacking into an existing column.
     */
    private void applyDock(int i, DockZone zone, int dropY) {
        setCardDocked(i, true);
        setCardDockSide(i, zone.side);

        if (zone.newColumn) {
            for (int j = 0; j < 3; j++) {
                if (j != i && isCardVisible(j) && isCardDocked(j)
                        && getCardDockSide(j) == zone.side && getCardDockColumn(j) >= zone.column) {
                    setCardDockColumn(j, getCardDockColumn(j) + 1);
                }
            }
            setCardDockColumn(i, zone.column);
            setCardDockRow(i, 0);
        } else {
            setCardDockColumn(i, zone.column);

            List<Integer> existing = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                if (j != i && isCardVisible(j) && isCardDocked(j)
                        && getCardDockSide(j) == zone.side && getCardDockColumn(j) == zone.column) {
                    existing.add(j);
                }
            }
            existing.sort((a, b) -> Integer.compare(getCardDockRow(a), getCardDockRow(b)));

            int insertPos = existing.size();
            for (int k = 0; k < existing.size(); k++) {
                int other = existing.get(k);
                int otherMidY = this.dragSnapshotValid
                        ? this.snapY[other] + this.snapH[other] / 2
                        : getCardY(other) + getCardHeight(other) / 2;
                if (dropY < otherMidY) {
                    insertPos = k;
                    break;
                }
            }
            existing.add(insertPos, i);
            for (int r = 0; r < existing.size(); r++) {
                setCardDockRow(existing.get(r), r);
            }
        }

        normalizeDockOrder();
    }

    /*
     * Compute where card i would land if dropped onto a zone — without mutating
     * persistent state. Returns {x, y, w, h} (panel-local).
     */
    private int[] simulateDrop(int draggingCard, DockZone zone, int dropY) {
        boolean[] sDocked = new boolean[3];
        int[] sSide = new int[3];
        int[] sCol = new int[3];
        int[] sRow = new int[3];
        int[] sX = new int[3];
        int[] sY = new int[3];
        int[] sW = new int[3];
        int[] sH = new int[3];
        for (int j = 0; j < 3; j++) {
            sDocked[j] = isCardDocked(j);
            sSide[j] = getCardDockSide(j);
            sCol[j] = getCardDockColumn(j);
            sRow[j] = getCardDockRow(j);
            sX[j] = getCardX(j);
            sY[j] = getCardY(j);
            sW[j] = getCardWidth(j);
            sH[j] = getCardHeight(j);
        }

        applyDock(draggingCard, zone, dropY);
        clampPositions();
        int[] rect = { getCardX(draggingCard), getCardY(draggingCard),
                getCardWidth(draggingCard), getCardHeight(draggingCard) };

        for (int j = 0; j < 3; j++) {
            setCardDocked(j, sDocked[j]);
            setCardDockSide(j, sSide[j]);
            setCardDockColumn(j, sCol[j]);
            setCardDockRow(j, sRow[j]);
            setCardX(j, sX[j]);
            setCardY(j, sY[j]);
            setCardWidth(j, sW[j]);
            setCardHeight(j, sH[j]);
        }

        return rect;
    }

    private void handleDragEnd(int i, UIContext context) {
        if (context == null || this.area == null)
            return;
        int dropY = context.mouseY - this.area.y;

        DockZone target = null;
        for (DockZone zone : computeDockZones(i)) {
            if (context.mouseX >= zone.cx - 18 && context.mouseX <= zone.cx + 18
                    && context.mouseY >= zone.cy - 18 && context.mouseY <= zone.cy + 18) {
                target = zone;
                break;
            }
        }

        if (target != null) {
            applyDock(i, target, dropY);
        } else {
            setCardDocked(i, false);
            normalizeDockOrder();
        }

        this.resize();
    }

    private void positionResizer(int i, UIDraggable resizer) {
        int cx = getCardX(i);
        int cy = getCardY(i);
        int cw = getCardWidth(i);
        int ch = getCardHeight(i);

        if (isCardDocked(i)) {
            int below = getCardBelow(i);
            if (below != -1 && !isCardCollapsed(i) && !isCardCollapsed(below)) {
                /*
                 * Horizontal splitter straddling the card boundary (5px above,
                 * 5px below) — generous click target.
                 */
                resizer.x(cx).y(cy + ch - 5).w(cw).h(10);
            } else {
                /*
                 * Width resizer on the column's OUTER edge (toward the world):
                 * LEFT region → right edge; RIGHT region → left edge. Spans the
                 * full panel height so a stacked column shows one bar.
                 */
                if (getCardDockSide(i) == SIDE_LEFT) {
                    resizer.x(cx + cw - 5).y(0).w(10).h(this.area.h);
                } else {
                    resizer.x(cx - 5).y(0).w(10).h(this.area.h);
                }
            }
        } else {
            resizer.x(cx + cw - 10).y(cy + ch - 10).w(10).h(10);
        }
    }

    private void renderResizer(int i, UIDraggable resizer, UIContext c) {
        boolean active = resizer.area.isInside(c) || resizer.isDragging();
        if (isCardDocked(i)) {
            int activeColor = 0xFF000000 | BBSSettings.accentRgb();
            int idleColor = 0xAA666666;
            int below = getCardBelow(i);
            if (below != -1 && !isCardCollapsed(i) && !isCardCollapsed(below)) {
                /*
                 * Horizontal splitter — 2px bar centered on the card boundary
                 * (middle of the 10px straddling hit area). Always visible.
                 */
                int midY = resizer.area.y + resizer.area.h / 2;
                int color = active ? activeColor : idleColor;
                c.batcher.box(resizer.area.x, midY - 1, resizer.area.ex(), midY + 1, color);
            } else {
                /* Vertical splitter — full column height, 2px bar centered. */
                int midX = resizer.area.x + resizer.area.w / 2;
                int color = active ? activeColor : idleColor;
                c.batcher.box(midX - 1, resizer.area.y, midX + 1, resizer.area.ey(), color);
            }
        } else {
            int color = active ? (0xFF000000 | BBSSettings.accentRgb()) : 0xFF888888;
            int rx = resizer.area.ex() - 8;
            int ry = resizer.area.ey() - 8;
            c.batcher.box(rx + 2, ry + 5, rx + 6, ry + 6, color);
            c.batcher.box(rx + 4, ry + 3, rx + 6, ry + 4, color);
            c.batcher.box(rx + 5, ry + 1, rx + 6, ry + 2, color);
        }
    }

    private void clampPositions() {
        if (this.area == null || this.area.w <= 0 || this.area.h <= 0) {
            return;
        }

        // Clamp card widths
        int minWidth = 180;
        int maxWidth = Math.max(minWidth, this.area.w / 2);
        this.leftCardWidth = Math.max(minWidth, Math.min(maxWidth, this.leftCardWidth));
        this.middleCardWidth = Math.max(minWidth, Math.min(maxWidth, this.middleCardWidth));
        this.rightCardWidth = Math.max(minWidth, Math.min(maxWidth, this.rightCardWidth));

        /*
         * Solve layout for docked cards — two regions, each with packed columns.
         * LEFT region columns pack from x=0 rightward; RIGHT region columns pack
         * from x=area.w leftward (column 0 nearest the screen edge).
         */
        for (int side = SIDE_LEFT; side <= SIDE_RIGHT; side++) {
            List<Integer> columns = columnsOnSide(side);
            if (columns.isEmpty()) {
                continue;
            }

            /* Width of each column = max card width within it. */
            int[] colWidths = new int[columns.size()];
            for (int c = 0; c < columns.size(); c++) {
                int max = minWidth;
                for (int card : cardsInColumn(side, columns.get(c))) {
                    max = Math.max(max, getCardWidth(card));
                }
                colWidths[c] = max;
            }

            /* Column X positions. */
            int[] colX = new int[columns.size()];
            if (side == SIDE_LEFT) {
                int x = 0;
                for (int c = 0; c < columns.size(); c++) {
                    colX[c] = x;
                    x += colWidths[c];
                }
            } else {
                int x = this.area.w;
                for (int c = 0; c < columns.size(); c++) {
                    x -= colWidths[c];
                    colX[c] = x;
                }
            }

            /* Vertical layout within each column (weighted heights). */
            for (int c = 0; c < columns.size(); c++) {
                List<Integer> cards = cardsInColumn(side, columns.get(c));
                int count = cards.size();
                if (count == 0) {
                    continue;
                }

                int collapsedCount = 0;
                float totalWeight = 0;
                for (int idx : cards) {
                    if (isCardCollapsed(idx)) {
                        collapsedCount++;
                    } else {
                        totalWeight += getCardWeight(idx);
                    }
                }
                int remainingHeight = this.area.h - (collapsedCount * 22);

                int currentY = 0;
                for (int r = 0; r < count; r++) {
                    int idx = cards.get(r);
                    int h;
                    if (isCardCollapsed(idx)) {
                        h = 22;
                    } else if (totalWeight > 0) {
                        h = Math.round(remainingHeight * (getCardWeight(idx) / totalWeight));
                    } else {
                        int expandedCount = count - collapsedCount;
                        h = expandedCount > 0 ? remainingHeight / expandedCount : 0;
                    }

                    if (r == count - 1) {
                        h = this.area.h - currentY;
                    }

                    setCardX(idx, colX[c]);
                    setCardY(idx, currentY);
                    setCardWidth(idx, colWidths[c]);
                    setCardHeight(idx, h);

                    currentY += h;
                }
            }
        }

        // Solve layout for floating cards
        for (int i = 0; i < 3; i++) {
            if (isCardVisible(i) && !isCardDocked(i)) {
                int w = getCardWidth(i);
                int h = getCardHeight(i);
                if (isCardCollapsed(i)) {
                    h = 22;
                } else {
                    h = Math.max(100, Math.min(this.area.h - getCardY(i), h));
                }

                int maxCardX = this.area.w - w;
                int maxCardY = this.area.h - h;
                int cx = Math.max(0, Math.min(maxCardX, getCardX(i)));
                int cy = Math.max(0, Math.min(maxCardY, getCardY(i)));

                setCardX(i, cx);
                setCardY(i, cy);
                setCardHeight(i, h);
            }
        }
    }

    @Override
    public void resize() {
        this.clampPositions();

        boolean paletteOpen = !this.getChildren(UIFormPalette.class).isEmpty();

        // Update layout boundaries dynamically before resizing children elements
        this.leftDragHandle.x(this.leftCardX).y(this.leftCardY).w(this.leftCardWidth).h(22);

        /*
         * Left card body: the list fills the entire body (Pick/Edit now lives
         * in the Properties card).
         */
        int pad = 6;
        int bodyX = this.leftCardX + pad;
        int bodyY = this.leftCardY + 22 + pad;
        int bodyW = this.leftCardWidth - pad * 2;
        int bodyH = this.leftCardHeight - 22 - pad * 2;
        boolean leftBody = this.leftVisible && !this.leftCollapsed && !paletteOpen;

        this.modelBlocks.x(bodyX).y(bodyY).w(bodyW).h(Math.max(0, bodyH));

        this.leftDragHandle.setVisible(this.leftVisible && !paletteOpen);
        this.modelBlocks.setVisible(leftBody);
        this.leftCardResizer.setVisible(this.leftVisible && !this.leftCollapsed && !paletteOpen);

        this.middleDragHandle.x(this.middleCardX).y(this.middleCardY).w(this.middleCardWidth).h(22);
        this.middleScrollView.x(this.middleCardX).y(this.middleCardY + 22).w(this.middleCardWidth)
                .h(this.middleCardHeight - 22);

        this.middleDragHandle.setVisible(this.middleVisible && this.modelBlock != null && !paletteOpen);
        this.middleScrollView
                .setVisible(this.middleVisible && this.modelBlock != null && !paletteOpen && !this.middleCollapsed);
        this.middleCardResizer
                .setVisible(this.middleVisible && this.modelBlock != null && !paletteOpen && !this.middleCollapsed);

        this.rightDragHandle.x(this.rightCardX).y(this.rightCardY).w(this.rightCardWidth).h(22);
        this.rightScrollView.x(this.rightCardX).y(this.rightCardY + 22).w(this.rightCardWidth)
                .h(this.rightCardHeight - 22);

        this.rightDragHandle.setVisible(this.rightVisible && this.modelBlock != null && !paletteOpen);
        this.rightScrollView
                .setVisible(this.rightVisible && this.modelBlock != null && !paletteOpen && !this.rightCollapsed);
        this.rightCardResizer
                .setVisible(this.rightVisible && this.modelBlock != null && !paletteOpen && !this.rightCollapsed);

        positionResizer(0, this.leftCardResizer);
        positionResizer(1, this.middleCardResizer);
        positionResizer(2, this.rightCardResizer);

        super.resize();
    }

    private void beginUndoCapture() {
        if (this.modelBlock == null) {
            return;
        }

        if (this.pendingUndoBefore == null) {
            this.pendingUndoBefore = this.modelBlock.getProperties().toData();
        }
    }

    private void endUndoCapture() {
        if (this.modelBlock == null || this.pendingUndoBefore == null) {
            return;
        }

        MapType before = this.pendingUndoBefore;
        this.pendingUndoBefore = null;

        MapType after = this.modelBlock.getProperties().toData();

        if (before.toString().equals(after.toString())) {
            return;
        }

        this.undoManager.pushUndo(new ModelBlockPropertiesUndo(this.modelBlock.getPos(), before, after));
        this.toSave.add(this.modelBlock);

        /* Nested form editor mutates the live form instance; syncing now round-trips
         * fromData() and replaces it (stale F7 transforms). Defer until the editor closes. */
        if (!this.isEditing(this.modelBlock))
        {
            this.save(this.modelBlock);
        }
    }

    private void applyPropertiesSnapshot(BlockPos pos, MapType data) {
        if (this.modelBlock == null || !this.modelBlock.getPos().equals(pos)) {
            for (ModelBlockEntity candidate : this.modelBlocks.getList()) {
                if (candidate != null && candidate.getPos().equals(pos)) {
                    this.modelBlock = candidate;
                    break;
                }
            }
        }

        if (this.modelBlock == null || !this.modelBlock.getPos().equals(pos)) {
            return;
        }

        this.modelBlock.getProperties().fromData(data);
        this.toSave.add(this.modelBlock);
        this.fillData();
    }

    private void undoModelBlock() {
        UIContext context = this.getContext();
        if (context != null && context.isFocused()) {
            return;
        }

        boolean ok = this.undoManager.undo(this);
        if (ok)
            UIUtils.playClick();
    }

    private void redoModelBlock() {
        UIContext context = this.getContext();
        if (context != null && context.isFocused()) {
            return;
        }

        boolean ok = this.undoManager.redo(this);
        if (ok)
            UIUtils.playClick();
    }

    private static class ModelBlockPropertiesUndo implements IUndo<UIModelBlockPanel> {
        private static final Set<String> TRANSFORM_KEYS = Set.of(
            "transform",
            "transformThirdPerson",
            "transformInventory",
            "transformFirstPerson"
        );

        private final BlockPos pos;
        private final MapType before;
        private MapType after;
        private boolean mergable = true;

        private ModelBlockPropertiesUndo(BlockPos pos, MapType before, MapType after) {
            this.pos = pos;
            this.before = before;
            this.after = after;
        }

        @Override
        public IUndo<UIModelBlockPanel> noMerging() {
            this.mergable = false;
            return this;
        }

        @Override
        public boolean isMergeable(IUndo<UIModelBlockPanel> undo) {
            if (!this.mergable || !(undo instanceof ModelBlockPropertiesUndo other) || !this.pos.equals(other.pos)) {
                return false;
            }

            /* Only coalesce consecutive transform/gizmo edits. Merging a form pick with a
             * later gizmo move made Ctrl+Z restore the previous form as well. */
            return isTransformOnlyChange(this.before, this.after)
                && isTransformOnlyChange(other.before, other.after);
        }

        @Override
        public void merge(IUndo<UIModelBlockPanel> undo) {
            ModelBlockPropertiesUndo other = (ModelBlockPropertiesUndo) undo;
            this.after = other.after;
        }

        @Override
        public void undo(UIModelBlockPanel context) {
            context.applyPropertiesSnapshot(this.pos, this.before);
        }

        @Override
        public void redo(UIModelBlockPanel context) {
            context.applyPropertiesSnapshot(this.pos, this.after);
        }

        private static boolean isTransformOnlyChange(MapType before, MapType after) {
            if (before == null || after == null) {
                return false;
            }

            Set<String> keys = new HashSet<>();
            keys.addAll(before.keys());
            keys.addAll(after.keys());

            boolean anyChange = false;

            for (String key : keys) {
                String a = before.has(key) ? String.valueOf(before.get(key)) : "";
                String b = after.has(key) ? String.valueOf(after.get(key)) : "";

                if (a.equals(b)) {
                    continue;
                }

                anyChange = true;

                if (!TRANSFORM_KEYS.contains(key)) {
                    return false;
                }
            }

            return anyChange;
        }
    }

    private void teleport() {
        if (this.modelBlock != null) {
            BlockPos pos = this.modelBlock.getPos();

            PlayerUtils.teleport(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            UIUtils.playClick();
        }
    }

    private void renameModelBlock() {
        if (this.modelBlock == null || this.getContext() == null) {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_RENAME,
                UIKeys.PANELS_MODALS_RENAME,
                this::applyModelBlockName);

        panel.text.setText(this.modelBlock.getProperties().getName());

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void applyModelBlockName(String name) {
        if (this.modelBlock == null) {
            return;
        }

        this.modelBlock.getProperties().setName(name);
        this.toSave.add(this.modelBlock);
        this.modelBlocks.update();
        this.save(this.modelBlock);
    }

    @Override
    public boolean supportsRollFOVControl() {
        return false;
    }

    @Override
    public Area getFlightViewportArea()
    {
        List<UIFormPalette> palettes = this.getChildren(UIFormPalette.class);

        if (!palettes.isEmpty())
        {
            UIFormEditor editor = palettes.get(0).editor;

            if (editor != null && editor.modelSettingsEditor != null && editor.modelSettingsEditor.isVisible())
            {
                return editor.modelSettingsEditor.renderer.area;
            }

            if (editor != null)
            {
                return editor.renderer.area;
            }
        }

        return null;
    }

    @Override
    public void appear() {
        super.appear();

        this.getContext().menu.main.add(this.keyDude);
        this.dashboard.orbitKeysUI.setEnabled(() ->
        {
            if (!this.getChildren(UIFormPalette.class).isEmpty())
            {
                return false;
            }

            UIContext context = this.getContext();

            return context == null || !context.hasOverlayPanel();
        });
        /* Drop any in-flight WASD velocity immediately when entering this panel's UI. */
        this.dashboard.orbit.reset();

        if (this.cameraController != null) {
            BBSModClient.getCameraController().add(this.cameraController);
        }
    }

    @Override
    public void disappear() {
        this.dismissFormPaletteSilently();

        super.disappear();

        this.keyDude.removeFromParent();
        this.dashboard.orbitKeysUI.setEnabled(null);

        if (this.cameraController != null) {
            BBSModClient.getCameraController().remove(this.cameraController);
        }

        this.flushPendingSaves();
    }

    /**
     * Fully closes the nested form palette (edit mode, model editor, camera controller)
     * and restores the normal model block cards. Used when leaving this panel so the
     * edit session does not linger in memory and reopen unexpectedly later.
     */
    public void dismissFormPaletteSilently()
    {
        List<UIFormPalette> palettes = this.getChildren(UIFormPalette.class);

        if (palettes.isEmpty())
        {
            return;
        }

        UIFormPalette palette = palettes.get(0);

        if (palette.editor.isEditing())
        {
            palette.toggleEditor();
        }

        this.removeCameraController();
        palette.removeFromParent();
        this.resize();
    }

    public ModelBlockEntity getModelBlock() {
        return this.modelBlock;
    }

    private void addCameraController(UIFormPalette palette) {
        if (this.cameraController == null) {
            this.cameraController = new ImmersiveModelBlockCameraController(palette.editor, this.modelBlock);

            BBSModClient.getCameraController().add(this.cameraController);

            Transform transform = this.modelBlock.getProperties().getTransform().copy();

            transform.translate.set(0F, 0F, 0F);
            palette.editor.renderer.setTransform(new Matrix4f(transform.createMatrix()));
        }
    }

    private void removeCameraController() {
        if (this.cameraController != null) {
            BBSModClient.getCameraController().remove(this.cameraController);

            this.cameraController = null;
        }
    }

    @Override
    public boolean needsBackground() {
        return false;
    }

    @Override
    public boolean canPause() {
        return false;
    }

    @Override
    public void open() {
        super.open();

        BBSRendering.ensureMainFramebuffer();
        this.updateList();
        this.dropRemovedSelection();
    }

    @Override
    public void update() {
        super.update();

        this.dropRemovedSelection();
    }

    @Override
    public void close() {
        super.close();

        this.dismissFormPaletteSilently();
        this.removeCameraController();
        this.saveLayout();
        this.flushPendingSaves();
    }

    private void updateList() {
        this.modelBlocks.clear();

        for (ModelBlockEntity modelBlock : BBSRendering.capturedModelBlocks) {
            this.modelBlocks.add(modelBlock);
        }

        this.fill(this.modelBlock, true);
    }

    /**
     * Drop selection and pending saves for block entities that no longer exist in the
     * world. Otherwise leaving/reopening this panel (including right-click → setPanel)
     * would flush stale properties to the same BlockPos and overwrite a newly placed block.
     */
    private void dropRemovedSelection() {
        this.toSave.removeIf((entity) -> entity == null || entity.isRemoved());

        if (this.modelBlock != null && this.modelBlock.isRemoved()) {
            this.pendingUndoBefore = null;
            this.fill(null, true);
        }
    }

    private void flushPendingSaves() {
        this.toSave.removeIf((entity) -> entity == null || entity.isRemoved());

        for (ModelBlockEntity entity : this.toSave) {
            this.save(entity);
        }

        this.toSave.clear();
    }

    public void fill(ModelBlockEntity modelBlock, boolean select) {
        /* Never keep pending writes for a broken block, or for a previous entity that
         * shared this position — both would overwrite the live block on flush. */
        this.toSave.removeIf((entity) ->
            entity == null
                || entity.isRemoved()
                || (modelBlock != null
                    && entity != modelBlock
                    && entity.getPos().equals(modelBlock.getPos())));

        if (modelBlock != null && !modelBlock.isRemoved()) {
            this.toSave.add(modelBlock);
        }

        if (this.modelBlock != modelBlock) {
            this.undoManager = new UndoManager<>(100);
            this.pendingUndoBefore = null;
        }

        this.modelBlock = modelBlock;

        if (modelBlock != null) {
            this.fillData();
        }

        boolean hasBlock = modelBlock != null;

        boolean paletteOpen = !this.getChildren(UIFormPalette.class).isEmpty();

        this.middleScrollView.setVisible(this.middleVisible && hasBlock && !paletteOpen && !this.middleCollapsed);
        this.middleDragHandle.setVisible(this.middleVisible && hasBlock && !paletteOpen);
        this.middleCardResizer.setVisible(this.middleVisible && hasBlock && !paletteOpen && !this.middleCollapsed);

        this.rightScrollView.setVisible(this.rightVisible && hasBlock && !paletteOpen && !this.rightCollapsed);
        this.rightDragHandle.setVisible(this.rightVisible && hasBlock && !paletteOpen);
        this.rightCardResizer.setVisible(this.rightVisible && hasBlock && !paletteOpen && !this.rightCollapsed);

        if (select) {
            this.modelBlocks.setCurrentScroll(modelBlock);
        }

        this.resize();
    }

    public boolean isLeftVisible() {
        return this.leftVisible;
    }

    public void setLeftVisible(boolean visible) {
        this.leftVisible = visible;
        this.resize();
        this.saveLayout();
    }

    public boolean isMiddleVisible() {
        return this.middleVisible;
    }

    public void setMiddleVisible(boolean visible) {
        this.middleVisible = visible;
        boolean paletteOpen = !this.getChildren(UIFormPalette.class).isEmpty();
        this.middleScrollView.setVisible(visible && this.modelBlock != null && !paletteOpen && !this.middleCollapsed);
        this.middleDragHandle.setVisible(visible && this.modelBlock != null && !paletteOpen);
        this.middleCardResizer.setVisible(visible && this.modelBlock != null && !paletteOpen && !this.middleCollapsed);
        this.resize();
        this.saveLayout();
    }

    public boolean isRightVisible() {
        return this.rightVisible;
    }

    public void setRightVisible(boolean visible) {
        this.rightVisible = visible;
        boolean paletteOpen = !this.getChildren(UIFormPalette.class).isEmpty();
        this.rightScrollView.setVisible(visible && this.modelBlock != null && !paletteOpen && !this.rightCollapsed);
        this.rightDragHandle.setVisible(visible && this.modelBlock != null && !paletteOpen);
        this.rightCardResizer.setVisible(visible && this.modelBlock != null && !paletteOpen && !this.rightCollapsed);
        this.resize();
        this.saveLayout();
    }

    /*
     * Persist the current window layout to BBSSettings. Saves are debounced by
     * the settings thread, so it's fine to call frequently.
     */
    private void saveLayout() {
        if (BBSSettings.modelBlockPanelLayout == null)
            return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0)
                sb.append(';');
            sb.append(getCardX(i)).append(',')
                    .append(getCardY(i)).append(',')
                    .append(getCardWidth(i)).append(',')
                    .append(getCardHeight(i)).append(',')
                    .append(isCardCollapsed(i) ? 1 : 0).append(',')
                    .append(isCardDocked(i) ? 1 : 0).append(',')
                    .append(getCardDockColumn(i)).append(',')
                    .append(getCardDockRow(i)).append(',')
                    .append(isCardVisible(i) ? 1 : 0).append(',')
                    .append(getCardWeight(i)).append(',')
                    .append(getCardDockSide(i));
        }
        BBSSettings.modelBlockPanelLayout.set(sb.toString());
    }

    private void loadLayout() {
        if (BBSSettings.modelBlockPanelLayout == null)
            return;
        String raw = BBSSettings.modelBlockPanelLayout.get();
        if (raw == null || raw.isEmpty())
            return;

        try {
            String[] parts = raw.split(";");
            if (parts.length < 3)
                return;

            for (int i = 0; i < 3; i++) {
                String[] f = parts[i].split(",");
                if (f.length != 11)
                    return;

                setCardX(i, Integer.parseInt(f[0]));
                setCardY(i, Integer.parseInt(f[1]));
                setCardWidth(i, Integer.parseInt(f[2]));
                setCardHeight(i, Integer.parseInt(f[3]));
                setCardCollapsed(i, f[4].equals("1"));
                setCardDocked(i, f[5].equals("1"));
                setCardDockColumn(i, Integer.parseInt(f[6]));
                setCardDockRow(i, Integer.parseInt(f[7]));
                setCardVisible(i, f[8].equals("1"));
                setCardWeight(i, Float.parseFloat(f[9]));
                setCardDockSide(i, Integer.parseInt(f[10]));
            }
            normalizeDockOrder();
        } catch (Exception e) {
            /* Malformed layout — fall back to defaults silently. */
        }
    }

    private void setCardCollapsed(int i, boolean c) {
        if (i == 0)
            this.leftCollapsed = c;
        else if (i == 1)
            this.middleCollapsed = c;
        else
            this.rightCollapsed = c;
    }

    private void setCardVisible(int i, boolean v) {
        if (i == 0)
            this.leftVisible = v;
        else if (i == 1)
            this.middleVisible = v;
        else
            this.rightVisible = v;
    }

    public void resetLayout() {
        this.leftCardX = 10;
        this.leftCardY = 10;
        this.leftCardWidth = 220;
        this.leftCardHeight = 400;
        this.leftCollapsed = false;
        this.leftDocked = true;
        this.leftDockSide = SIDE_LEFT;
        this.leftDockColumn = 0;
        this.leftDockRow = 0;
        this.leftVisible = true;

        this.middleCardX = 470;
        this.middleCardY = 410;
        this.middleCardWidth = 240;
        this.middleCardHeight = 400;
        this.middleCollapsed = false;
        this.middleDocked = true;
        this.middleDockSide = SIDE_RIGHT;
        this.middleDockColumn = 0;
        this.middleDockRow = 1;
        this.middleVisible = true;

        this.rightCardX = 470;
        this.rightCardY = 10;
        this.rightCardWidth = 240;
        this.rightCardHeight = 160;
        this.rightCollapsed = false;
        this.rightDocked = true;
        this.rightDockSide = SIDE_RIGHT;
        this.rightDockColumn = 0;
        this.rightDockRow = 0;
        this.rightVisible = true;

        this.leftWeight = 1.0F;
        this.middleWeight = 1.0F;
        this.rightWeight = 1.0F;

        this.resize();
        this.saveLayout();
    }

    private void fillData() {
        ModelProperties properties = this.modelBlock.getProperties();

        this.pickEdit.setForm(properties.getForm());
        this.transform.setTransform(properties.getTransform());
        this.enabled.setValue(properties.isEnabled());
        this.shadow.setValue(properties.isShadow());
        this.hitbox.setValue(properties.isHitbox());
        this.global.setValue(properties.isGlobal());
        this.lookAt.setValue(properties.isLookAt());
        this.chromaSky.setValue(properties.isChromaSky());
        this.localLighting.setValue(properties.isLocalLighting());
        this.lightLevel.setValue(properties.getLightLevel());
        this.hardness.setValue(properties.getHardness());

        this.mainHand.setStack(properties.getItemMainHand());
        this.offHand.setStack(properties.getItemOffHand());
        this.armorHead.setStack(properties.getArmorHead());
        this.armorChest.setStack(properties.getArmorChest());
        this.armorLegs.setStack(properties.getArmorLegs());
        this.armorFeet.setStack(properties.getArmorFeet());
    }

    private void save(ModelBlockEntity modelBlock) {
        if (modelBlock != null && !modelBlock.isRemoved()) {
            ClientNetwork.sendModelBlockForm(modelBlock.getPos(), modelBlock);
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context) {
        if (super.subMouseClicked(context)) {
            return true;
        }

        if (context.mouseButton == 0 && this.gizmoController.tryStartHandleDrag(context, this.transform)) {
            return true;
        }

        if (this.hovered != null && context.mouseButton == 0 && BBSSettings.clickModelBlocks.get()) {
            this.fill(this.hovered, true);
        }

        /* Nothing in this panel claimed the click (no gizmo handle, no block under the
         * cursor): start the shared orbit camera drag directly instead of letting the click
         * fall through, since this panel sits on top of the dashboard's orbitUI in the click
         * dispatch order and would otherwise silently eat the click without it ever reaching
         * the camera (same fix as the film viewport's flight-mode click handling). */
        int button = this.dashboard.orbitUI.orbit.canStart(context);

        if (button >= 0) {
            this.dashboard.orbitUI.orbit.start(button, context.mouseX, context.mouseY);

            return true;
        }

        return false;
    }

    @Override
    public boolean subMouseReleased(UIContext context) {
        this.gizmoController.consumePendingTrackballClick();
        this.gizmoController.stop();

        return super.subMouseReleased(context);
    }

    @Override
    protected IUIElement childrenKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE) && !this.getChildren(UIFormPalette.class).isEmpty())
        {
            /* Body-part form picker (UIFormEditorList) and the nested palette editor must
             * handle Esc first — e.g. close the picker and stay in form edit mode. */
            IUIElement handled = super.childrenKeyPressed(context);

            if (handled != null)
            {
                return handled;
            }

            this.dismissFormPaletteSilently();

            return this;
        }

        return super.childrenKeyPressed(context);
    }

    @Override
    public void render(UIContext context) {
        if (BBSRendering.isIrisShadersEnabled())
        {
            this.renderGizmoStencilInterface(context);

            if (this.hasGizmo && this.hasGizmoInterfaceMatrix)
            {
                this.applyGizmoCaptureToSingleton();
                this.gizmoController.renderGizmo(context, this.gizmoProjection, this.getGizmoArea());
            }
        }
        else
        {
            this.renderGizmoStencilInterface(context);

            if (this.hasGizmo && this.hasGizmoInterfaceMatrix)
            {
                this.applyGizmoCaptureToSingleton();
                this.gizmoController.renderGizmo(context, this.gizmoProjection, this.getGizmoArea());
            }
        }

        /*
         * Capture pre-drag layout once per drag, before updateDrag fires the
         * callback that undocks the card and rearranges its column.
         */
        for (int i = 0; i < 3; i++) {
            UIDraggable handle = (i == 0) ? this.leftDragHandle
                    : ((i == 1) ? this.middleDragHandle : this.rightDragHandle);
            boolean dragging = handle.isDragging();
            if (dragging && !this.prevDragging[i]) {
                for (int j = 0; j < 3; j++) {
                    this.snapX[j] = getCardX(j);
                    this.snapY[j] = getCardY(j);
                    this.snapW[j] = getCardWidth(j);
                    this.snapH[j] = getCardHeight(j);
                }
                this.dragSnapshotValid = true;
            } else if (!dragging && this.prevDragging[i]) {
                this.dragSnapshotValid = false;
            }
            this.prevDragging[i] = dragging;
        }

        // Update drag positions early to prevent frame lag / splitting!
        if (this.leftDragHandle.isDragging())
            this.leftDragHandle.updateDrag(context);
        if (this.middleDragHandle.isDragging())
            this.middleDragHandle.updateDrag(context);
        if (this.rightDragHandle.isDragging())
            this.rightDragHandle.updateDrag(context);
        if (this.leftCardResizer.isDragging())
            this.leftCardResizer.updateDrag(context);
        if (this.middleCardResizer.isDragging())
            this.middleCardResizer.updateDrag(context);
        if (this.rightCardResizer.isDragging())
            this.rightCardResizer.updateDrag(context);

        // 1. Render Card Glassmorphic Backgrounds
        boolean paletteOpen = !this.getChildren(UIFormPalette.class).isEmpty();
        for (int i = 0; i < 3; i++) {
            if (this.isCardVisible(i)) {
                if (paletteOpen || (i > 0 && this.modelBlock == null)) {
                    continue;
                }
                int cx = this.area.x + this.getCardX(i);
                int cy = this.area.y + this.getCardY(i);
                int cw = this.getCardWidth(i);
                int ch = this.getCardHeight(i);

                // Draw full contiguous outline border (matching film editor windows)
                context.batcher.outline(cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, 0xFF5A5A5A);

                if (!this.isCardCollapsed(i)) {
                    // Glassmorphic translucent body
                    context.batcher.box(cx, cy + 22, cx + cw, cy + ch, 0xCE111115);
                }
            }
        }

        int draggingCard = -1;
        if (this.leftDragHandle.isActivelyDragging())
            draggingCard = 0;
        else if (this.middleDragHandle.isActivelyDragging())
            draggingCard = 1;
        else if (this.rightDragHandle.isActivelyDragging())
            draggingCard = 2;

        String label = UIKeys.FILM_CONTROLLER_SPEED.format(this.dashboard.orbit.speed.getValue()).get();
        FontRenderer font = context.batcher.getFont();
        int labelW = font.getWidth(label);
        int labelX = this.area.w - labelW - 5;
        int labelY = this.area.ey() - font.getHeight() - 5;

        context.batcher.textCard(label, labelX, labelY, Colors.WHITE, Colors.A50);
        super.render(context);

        /*
         * Dock cubes + drop preview render LAST so they sit on top of every
         * card's drag handle and scroll view (super.render draws children).
         */
        int baseColor = BBSSettings.editorDockGuideColor == null ? 0x57CCFF : BBSSettings.editorDockGuideColor.get();
        float opacity = BBSSettings.editorDockGuideOpacity == null ? 0.5F
                : Math.max(0F, Math.min(1F, BBSSettings.editorDockGuideOpacity.get()));
        int fill = Colors.setA(Colors.mulRGB(baseColor, 1.2F), opacity * 0.34F);
        int border = Colors.setA(Colors.mulRGB(baseColor, 1.35F), opacity * 0.6F);

        if (draggingCard != -1) {
            int dropY = context.mouseY - this.area.y;

            /*
             * One center + two edge cubes per existing card, plus one per empty
             * region. Hovering a cube highlights it and previews the drop.
             */
            List<DockZone> zones = computeDockZones(draggingCard);
            DockZone hoveredZone = null;
            for (DockZone zone : zones) {
                boolean hovered = context.mouseX >= zone.cx - 18 && context.mouseX <= zone.cx + 18
                        && context.mouseY >= zone.cy - 18 && context.mouseY <= zone.cy + 18;
                if (checkAndRenderDockCube(context, zone.cx, zone.cy, hovered) && hoveredZone == null) {
                    hoveredZone = zone;
                }
            }

            if (hoveredZone != null) {
                /*
                 * Simulate the drop to get the exact landing rectangle — this
                 * reuses the real layout solver so the preview always matches.
                 */
                int[] rect = simulateDrop(draggingCard, hoveredZone, dropY);
                int previewX = this.area.x + rect[0];
                int previewY = this.area.y + rect[1];
                int previewW = rect[2];
                int previewH = rect[3];

                context.batcher.box(previewX, previewY, previewX + previewW, previewY + previewH, fill);

                int t = 2;
                context.batcher.box(previewX, previewY, previewX + previewW, previewY + t, border);
                context.batcher.box(previewX, previewY + previewH - t, previewX + previewW, previewY + previewH,
                        border);
                context.batcher.box(previewX, previewY, previewX + t, previewY + previewH, border);
                context.batcher.box(previewX + previewW - t, previewY, previewX + previewW, previewY + previewH,
                        border);
            }
        }
    }

    @Override
    public void renderInWorld(WorldRenderContext context) {
        super.renderInWorld(context);

        MatrixStack matrices = context.matrixStack();
        boolean shaderPath = BBSRendering.isIrisShadersEnabled();

        if (shaderPath)
        {
            if (matrices == null)
            {
                return;
            }
        }
        else
        {
            /* AFTER_ENTITIES has no reliable stack; draw block overlays in absolute world space. */
            matrices = new MatrixStack();
        }

        Camera camera = context.camera();
        Vec3d pos = camera.getPos();

        MinecraftClient mc = MinecraftClient.getInstance();
        double x = mc.mouse.getX();
        double y = mc.mouse.getY();

        /* The view matrix is rebuilt from the camera's own rotation instead of using the world
         * render matrix stack top: that stack isn't guaranteed to hold the camera rotation
         * (and doesn't in 1.21.1), which used to skew this ray - the gizmo dragged with an
         * inverted/stuttering rotation and a way-too-fast Z axis while stencil-based hover
         * (which doesn't use this ray) kept working fine. */
        Matrix4f view = new Matrix4f().rotation(camera.getRotation().conjugate(new Quaternionf()));

        this.mouseDirection.set(CameraUtils.getMouseDirection(
                RenderSystem.getProjectionMatrix(),
                view,
                (int) x, (int) y, 0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        this.hovered = this.getClosestObject(new Vector3d(pos.x, pos.y, pos.z), this.mouseDirection);

        RenderSystem.enableDepthTest();

        for (ModelBlockEntity entity : this.modelBlocks.getList()) {
            BlockPos blockPos = entity.getPos();

            if (!this.isEditing(entity)) {
                matrices.push();

                if (shaderPath)
                {
                    matrices.translate(blockPos.getX() - pos.x, blockPos.getY() - pos.y,
                            blockPos.getZ() - pos.z);

                    if (this.hovered == entity || entity == this.modelBlock) {
                        Draw.renderBox(matrices, 0D, 0D, 0D, 1D, 1D, 1D, 0, 0.5F, 1F);
                    } else {
                        Draw.renderBox(matrices, 0D, 0D, 0D, 1D, 1D, 1D);
                    }
                }
                else
                {
                    matrices.translate(-pos.x, -pos.y, -pos.z);

                    if (this.hovered == entity || entity == this.modelBlock) {
                        Draw.renderBox(matrices, blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1D, 1D, 1D, 0, 0.5F, 1F);
                    } else {
                        Draw.renderBox(matrices, blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1D, 1D, 1D);
                    }
                }

                matrices.pop();
            }
        }

        this.renderSelectedHitbox(matrices, pos, shaderPath);
        this.renderGizmo(context, pos, matrices);

        RenderSystem.enableDepthTest();
    }

    /** Draws the selected block's form hitbox wireframe in world space. */
    private void renderSelectedHitbox(MatrixStack matrices, Vec3d cameraPos, boolean shaderPath)
    {
        if (this.modelBlock == null || this.isEditing(this.modelBlock))
        {
            return;
        }

        ModelProperties properties = this.modelBlock.getProperties();

        if (!properties.isHitbox())
        {
            return;
        }

        Form form = properties.getForm();

        if (form == null || !form.hitbox.get())
        {
            return;
        }

        float hitboxW = form.hitboxWidth.get();
        float hitboxH = form.hitboxHeight.get();

        if (hitboxW <= 0F || hitboxH <= 0F)
        {
            return;
        }

        Transform blockTransform = properties.getTransform();
        BlockPos blockPos = this.modelBlock.getPos();

        matrices.push();

        if (shaderPath)
        {
            matrices.translate(blockPos.getX() - cameraPos.x + 0.5D, blockPos.getY() - cameraPos.y,
                    blockPos.getZ() - cameraPos.z + 0.5D);
            MatrixStackUtils.applyTransform(matrices, blockTransform);
        }
        else
        {
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            matrices.translate(blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D);
            MatrixStackUtils.applyTransform(matrices, blockTransform);
        }

        Draw.renderBox(matrices, -hitboxW / 2D, 0D, -hitboxW / 2D, hitboxW, hitboxH, hitboxW, 0F, 0.5F, 1F);
        matrices.pop();
    }

    /** Renders the interactive gizmo at the edited model block. */
    private void renderGizmo(WorldRenderContext context, Vec3d cameraPos, MatrixStack stack) {
        this.hasGizmo = false;
        this.hasGizmoInterfaceMatrix = false;

        /* Don't draw/pick this panel's own gizmo while the block's nested form/model editor is
         * open on top - that editor has its own gizmo (rendered in its own viewport), and
         * showing both at once would double up and fight over the same drag. */
        if (this.modelBlock == null || this.isEditing(this.modelBlock) || !UIBaseMenu.renderAxes)
        {
            this.gizmoStencil.clearPicking();
            this.gizmoController.updateHover();

            return;
        }

        Transform blockTransform = this.modelBlock.getProperties().getTransform();
        BlockPos blockPos = this.modelBlock.getPos();
        double px = blockPos.getX() + 0.5D + blockTransform.translate.x;
        double py = blockPos.getY() + blockTransform.translate.y;
        double pz = blockPos.getZ() + 0.5D + blockTransform.translate.z;

        this.gizmoWorldMatrix.identity().translate((float) px, (float) py, (float) pz);

        /* In local mode the ring/axis directions should follow the block's own rotated
         * orientation (same as every other gizmo surface), not stay world-axis-aligned. */
        if (this.transform != null && this.transform.isLocal())
        {
            this.gizmoWorldMatrix.mul(new Matrix4f(blockTransform.createRotationMatrix()));
        }

        this.gizmoCameraPosition.set(cameraPos.x, cameraPos.y, cameraPos.z);
        this.hasGizmo = true;
        this.gizmoProjection.set(RenderSystem.getProjectionMatrix());

        MatrixStack gizmoStack;

        if (BBSRendering.isIrisShadersEnabled())
        {
            /* Films#render clears Gizmo#hasGizmoMatrix after this pass, so keep a local
             * copy for the deferred UI draw + stencil pick. */
            gizmoStack = stack;
        }
        else
        {
            /* Without shaders the world stack is unreliable; capture only the camera-relative
             * block transform and premultiply BBSRendering.camera in applyGizmoCaptureToSingleton. */
            gizmoStack = new MatrixStack();
        }

        gizmoStack.push();
        gizmoStack.translate(px - cameraPos.x, py - cameraPos.y, pz - cameraPos.z);

        if (this.transform != null && this.transform.isLocal())
        {
            MatrixStackUtils.multiply(gizmoStack, new Matrix4f(blockTransform.createRotationMatrix()));
        }

        this.gizmoInterfaceMatrix.set(gizmoStack.peek().getPositionMatrix());
        this.hasGizmoInterfaceMatrix = true;
        Gizmo.INSTANCE.captureVisual(gizmoStack);

        RenderSystem.enableDepthTest();
        gizmoStack.pop();
    }

    /**
     * The screen region the gizmo renders into: the full menu viewport. The model
     * block gizmo is drawn onto the full-screen world view, not this panel's card
     * area (which is shorter than the screen because of the taskbar).
     */
    private Area getGizmoArea()
    {
        UIContext context = this.getContext();

        return context != null ? context.menu.viewport : this.area;
    }

    private void applyGizmoCaptureToSingleton()
    {
        /* Whether the captured matrix already bakes BBSRendering.camera depends on the
         * render path (Iris pack vs. vanilla). composeVisualMatrix detects double-camera
         * by view-space origin distance and keeps the gizmo on the block. */
        Gizmo.composeVisualMatrix(this.gizmoInterfaceMatrix, BBSRendering.camera, this.gizmoProjection, Gizmo.INSTANCE.lastGizmoMatrix);
        Gizmo.INSTANCE.hasGizmoMatrix = true;
    }

    private void renderGizmoStencilInterface(UIContext context)
    {
        if (!this.hasGizmo || !this.hasGizmoInterfaceMatrix)
        {
            this.gizmoStencil.clearPicking();
            this.gizmoController.updateHover();

            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        this.gizmoStencil.setup(Link.bbs("stencil_model_block"));

        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        Texture texture = this.gizmoStencil.getFramebuffer().getMainTexture();

        if (texture.width != w || texture.height != h)
        {
            this.gizmoStencil.resize(w, h);
        }

        this.gizmoStencilMap.setup();
        context.batcher.flush();
        this.gizmoStencil.apply();

        this.applyGizmoCaptureToSingleton();
        Gizmo.INSTANCE.renderStencilInterface(context, this.gizmoProjection, this.getGizmoArea(), this.gizmoStencilMap);

        this.gizmoStencil.pick((int) mc.mouse.getX(), (int) (h - mc.mouse.getY()));
        this.gizmoStencil.unbind(this.gizmoStencilMap);
        this.gizmoController.updateHover();

        mc.getFramebuffer().beginWrite(true);
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil() {
        return this.gizmoStencil;
    }

    @Override
    public void prepareGizmoDrag(UIPropTransform transform) {
        if (transform == null) {
            return;
        }

        /* Y/Z ring and white view-ring values follow the mouse; only those process bars
         * wind the wrong way (especially in Local). */
        transform.setInvertRotationArcY(true);
        transform.setInvertRotationArcZ(true);
        transform.setInvertRotationArcViewRing(true);

        transform.setGizmoRayProvider(new UIPropTransform.IGizmoRayProvider() {
            @Override
            public boolean getMouseRay(UIContext context, int mouseX, int mouseY, Vector3d rayOrigin, Vector3f rayDirection) {
                if (!UIModelBlockPanel.this.hasGizmo || UIModelBlockPanel.this.mouseDirection.lengthSquared() <= 1.0E-12F) {
                    return false;
                }

                rayDirection.set(UIModelBlockPanel.this.mouseDirection).normalize();
                rayOrigin.set(UIModelBlockPanel.this.gizmoCameraPosition);

                return true;
            }

            @Override
            public boolean getGizmoMatrix(Matrix4f matrix) {
                if (!UIModelBlockPanel.this.hasGizmo) {
                    return false;
                }

                matrix.set(UIModelBlockPanel.this.gizmoWorldMatrix);

                return true;
            }
        });
    }

    private ModelBlockEntity getClosestObject(Vector3d finalPosition, Vector3f mouseDirection) {
        ModelBlockEntity closest = null;

        for (ModelBlockEntity object : this.modelBlocks.getList()) {
            AABB aabb = this.getHitbox(object);

            if (aabb.intersectsRay(finalPosition, mouseDirection)) {
                if (closest == null) {
                    closest = object;
                } else {
                    AABB aabb2 = this.getHitbox(closest);

                    if (finalPosition.distanceSquared(aabb.x, aabb.y, aabb.z) < finalPosition.distanceSquared(aabb2.x,
                            aabb2.y, aabb2.z)) {
                        closest = object;
                    }
                }
            }
        }
        return closest;
    }

    private AABB getHitbox(ModelBlockEntity closest) {
        BlockPos pos = closest.getPos();

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double w = 1D;
        double h = 1D;
        double d = 1D;

        if (closest.getProperties().isHitbox()) {
            Form form = closest.getProperties().getForm();

            if (form != null && form.hitbox.get()) {
                float width = form.hitboxWidth.get();
                float height = form.hitboxHeight.get();

                if (width > 0F && height > 0F) {
                    float halfWidth = width / 2F;

                    double minX = x + 0.5D - halfWidth;
                    double maxX = x + 0.5D + halfWidth;
                    double minZ = z + 0.5D - halfWidth;
                    double maxZ = z + 0.5D + halfWidth;
                    double minY = y;
                    double maxY = y + height;

                    double clampedMinX = Math.max(x, minX);
                    double clampedMinZ = Math.max(z, minZ);
                    double clampedMaxX = Math.min(x + 1D, maxX);
                    double clampedMaxZ = Math.min(z + 1D, maxZ);
                    double clampedMaxY = Math.min(y + 1D, maxY);

                    if (clampedMinX < clampedMaxX && clampedMinZ < clampedMaxZ && clampedMaxY > minY) {
                        x = clampedMinX;
                        y = minY;
                        z = clampedMinZ;
                        w = clampedMaxX - clampedMinX;
                        h = clampedMaxY - minY;
                        d = clampedMaxZ - clampedMinZ;
                    }
                }
            }
        }

        return new AABB(x, y, z, w, h, d);
    }

    public boolean isEditing(ModelBlockEntity entity) {
        if (this.modelBlock == entity) {
            List<UIFormPalette> children = this.getChildren(UIFormPalette.class);

            if (!children.isEmpty()) {
                return children.get(0).editor.isEditing();
            }
        }
        return false;
    }

    /**
     * Live form currently being mutated in the nested form editor for this block, if any.
     * Used by world/F7 rendering so the preview matches in-editor transforms even if a
     * block-entity sync replaced {@link ModelProperties#getForm()}.
     */
    public static Form getLiveEditedForm(ModelBlockEntity entity)
    {
        if (entity == null || !(UIScreen.getCurrentMenu() instanceof UIDashboard dashboard))
        {
            return null;
        }

        if (!(dashboard.getPanels().panel instanceof UIModelBlockPanel panel) || !panel.isEditing(entity))
        {
            return null;
        }

        List<UIFormPalette> children = panel.getChildren(UIFormPalette.class);

        if (children.isEmpty())
        {
            return null;
        }

        return children.get(0).editor.form;
    }

    /** Unlike {@link #isEditing(ModelBlockEntity)} (which also requires the nested form
     *  palette to be open), this only asks "is this the block currently selected in this
     *  panel" - i.e. the one the real interactive gizmo is drawn for - regardless of whether
     *  its nested form editor happens to be open. Used to suppress the old decorative/
     *  non-interactive axes for the selected block so they don't overlap the real gizmo. */
    public boolean isSelectedForGizmo(ModelBlockEntity entity) {
        return this.modelBlock == entity;
    }

    private float getCardWeight(int i) {
        if (i == 0)
            return this.leftWeight;
        if (i == 1)
            return this.middleWeight;
        return this.rightWeight;
    }

    private void setCardWeight(int i, float w) {
        if (i == 0)
            this.leftWeight = w;
        else if (i == 1)
            this.middleWeight = w;
        else
            this.rightWeight = w;
    }

    private int getCardBelow(int i) {
        if (!isCardVisible(i) || !isCardDocked(i))
            return -1;
        int side = getCardDockSide(i);
        int col = getCardDockColumn(i);
        int row = getCardDockRow(i);
        int belowIdx = -1;
        int minRowBelow = Integer.MAX_VALUE;
        for (int j = 0; j < 3; j++) {
            if (j != i && isCardVisible(j) && isCardDocked(j)
                    && getCardDockSide(j) == side && getCardDockColumn(j) == col) {
                int r = getCardDockRow(j);
                if (r > row && r < minRowBelow) {
                    minRowBelow = r;
                    belowIdx = j;
                }
            }
        }
        return belowIdx;
    }

    private boolean checkAndRenderDockCube(UIContext context, int cx, int cy, boolean hovered) {
        int x1 = cx - 14;
        int y1 = cy - 14;
        int x2 = cx + 14;
        int y2 = cy + 14;

        int baseColor = BBSSettings.accentRgb();

        /*
         * Idle cubes need to stand out against any background (dark world or
         * translucent panel body), so use a near-opaque dark fill with a bright
         * border and white core.
         */
        int bg = hovered ? Colors.setA(baseColor, 0.85F) : 0xEE1A1A20;
        int border = hovered ? 0xFFFFFFFF : (0xFF000000 | baseColor);

        context.batcher.box(x1, y1, x2, y2, bg);
        context.batcher.outline(x1, y1, x2, y2, border);

        int coreSize = hovered ? 5 : 4;
        int coreColor = 0xFFFFFFFF;
        context.batcher.box(cx - coreSize, cy - coreSize, cx + coreSize, cy + coreSize, coreColor);

        if (hovered) {
            context.batcher.outline(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x88FFFFFF);
        }

        return context.mouseX >= x1 - 4 && context.mouseX <= x2 + 4 &&
                context.mouseY >= y1 - 4 && context.mouseY <= y2 + 4;
    }

    // Draggable header element class
    public static class UIPanelDragHandle extends UIDraggable {
        private final IKey title;
        private final Icon icon;
        private final Supplier<Boolean> collapsedSupplier;
        private final Runnable toggleCollapse;

        public UIPanelDragHandle(IKey title, Icon icon, Supplier<Boolean> collapsedSupplier, Runnable toggleCollapse,
                Consumer<UIContext> callback) {
            super(callback);
            this.title = title;
            this.icon = icon;
            this.collapsedSupplier = collapsedSupplier;
            this.toggleCollapse = toggleCollapse;
            this.h(20);
            this.rendering((context) -> {
            });
        }

        @Override
        protected boolean subMouseClicked(UIContext context) {
            if (this.area.isInside(context) && context.mouseButton == 0) {
                // Check if clicked the chevron icon on the right edge (last 20px)
                if (context.mouseX >= this.area.ex() - 20) {
                    if (this.toggleCollapse != null) {
                        this.toggleCollapse.run();
                        UIUtils.playClick();
                    }
                    return true;
                }
            }
            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context) {
            // Render solid premium gray background directly using this.area
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF1D1D1D);
            context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), 0xFF3C3C3C);

            boolean hovered = this.area.isInside(context) || this.isDragging();
            int textColor = hovered ? (0xFF000000 | BBSSettings.accentRgb()) : 0xFFFFFFFF;

            FontRenderer font = context.batcher.getFont();
            int titleY = this.area.my(font.getHeight());

            if (this.icon != null && this.icon != Icons.NONE) {
                context.batcher.icon(this.icon, textColor, this.area.x + 12, this.area.my(), 0.5F, 0.5F);
                context.batcher.text(this.title.get(), this.area.x + 24, titleY, textColor);
            } else {
                context.batcher.text(this.title.get(), this.area.x + 12, titleY, textColor);
            }

            // Draw collapse/expand chevron on the right edge
            boolean collapsed = this.collapsedSupplier != null && this.collapsedSupplier.get();
            Icon chevronIcon = collapsed ? Icons.COLLAPSED : Icons.UNCOLLAPSED;
            context.batcher.icon(chevronIcon, textColor, this.area.ex() - 12, this.area.my(), 0.5F, 0.5F);

            super.render(context);
        }
    }
}
