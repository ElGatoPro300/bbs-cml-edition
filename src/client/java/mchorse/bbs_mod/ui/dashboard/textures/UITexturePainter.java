package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.undo.UndoManager;

import org.joml.Vector2i;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UITexturePainter extends UIElement
{
    public static class ReferenceImage
    {
        public Link link;
        public boolean visible = true;
        public UITextureEditor editor;

        public ReferenceImage(Link link, UITextureEditor editor)
        {
            this.link = link;
            this.editor = editor;
        }
    }

    private static final int MODEL_PREVIEW_LEFT_WIDTH = 220;
    private static final int MODEL_PREVIEW_GAP = 6;

    private int sidePanelWidth = 190;
    private int sidePanelSplitY = 276;

    public List<ReferenceImage> referenceImages = new ArrayList<>();

    public UITrackpad brightness;
    public UITrackpad brush;
    public UIElement headerToolbar;
    public UIElement sidePanel;
    public UIElement sidePanelResizer;
    public UIElement sidePanelInnerResizer;
    public UIElement colorTabContent;
    public UIElement paletteTabContent;
    public UIElement mediaTabContent;
    public UIButton tabColor;
    public UIButton tabPalette;
    public UIButton tabImages;
    public UIButton tabLayers;
    public UIButton primarySlot;
    public UIButton secondarySlot;
    public UIIcon swapColorsButton;
    public UIElement layerRow;
    public UIElement imageRow;
    public UITrackpad layerOpacity;
    public UIButton selectTextureButton;
    public UIIcon addLayerButton;
    public UIIcon duplicateLayerButton;
    public UIIcon removeLayerButton;
    public UIIcon moveLayerUpButton;
    public UIIcon moveLayerDownButton;
    public UIIcon layerOptionsButton;
    public UIScrollView imageRows;
    public UIScrollView layerRows;

    public UIColor primary;
    public UIColor secondary;
    public UITextureInlineColorPicker fixedColorPicker;

    public UITextureEditor main;
    public UITextureEditor reference;
    public UIElement modelPreviewArea;
    public UIFormRenderer modelPreview;
    public UIIcon toolBrush;
    public UIIcon toolEraser;
    public UIIcon toolShading;
    public UIIcon toolNoise;
    public UIIcon toolSelect;
    public UIIcon toolPick;
    public UIIcon toolFill;
    public UIIcon toolShape;
    public UIIcon toolGradient;
    public UIIcon toolSquare;
    public UIIcon toolCircle;
    public UIIcon toolLockAlpha;
    public UIIcon toolMirrorX;
    public UIIcon toolMirrorY;
    public UIIcon toolPixelPerfect;
    public UIIcon toolImageOps;
    public UIButton extractPaletteButton;
    public UIButton palettePresetsButton;
    public UIButton paletteImportButton;
    public UIButton paletteExportButton;
    public UIElement paletteSwatchesContainer;

    private int[] paletteColors = new int[] {
        0x000000, 0xffffff, 0x8f3f20, 0xd87f33, 0xff0000, 0xff55ff,
        0x00aa00, 0x55ffff, 0x3c44aa, 0x8932b8, 0xa0a0a0, 0x5a5a5a,
        0x191919, 0x33ebcb, 0xea323c, 0x00bfff, 0xffd700, 0x7cfc00
    };

    private Supplier<Form> formPreviewSupplier;
    private final Set<Link> touchedPreviewTextures = new HashSet<>();
    private UIPixelsEditor.Tool activeTool = UIPixelsEditor.Tool.BRUSH;
    private UIPixelsEditor.BrushShape activeBrushShape = UIPixelsEditor.BrushShape.SQUARE;
    private UIPixelsEditor.ShapeType activeShapeType = UIPixelsEditor.ShapeType.RECTANGLE;
    private boolean shapeFilled = false;
    private boolean lockAlpha = false;
    private boolean mirrorX = false;
    private boolean mirrorY = false;
    private boolean pixelPerfect = false;
    private boolean editingPrimary = true;
    private boolean topTabColor = true;
    private boolean bottomTabLayers = true;
    private final List<Link> imageTextures = new ArrayList<>();
    private final List<TextureLayer> layers = new ArrayList<>();
    private final List<Texture> layerPreviewTextures = new ArrayList<>();
    private final Map<Link, List<TextureLayer>> layersByTexture = new HashMap<>();
    private final Map<Link, Integer> selectedLayerByTexture = new HashMap<>();
    private int selectedImageIndex = -1;
    private int selectedLayerIndex = -1;
    private Texture layersCompositeTexture;
    private Pixels layersCompositePixels;
    private UIElement texturePickerPopup;

    public enum BlendMode
    {
        NORMAL,
        MULTIPLY,
        SCREEN,
        OVERLAY,
        ADD,
        DARKEN,
        LIGHTEN
    }

    private static class TextureLayer
    {
        public String name;
        public float opacity;
        public boolean visible;
        public Pixels pixels;
        public UndoManager<Pixels> undoManager;
        public BlendMode blendMode = BlendMode.NORMAL;

        public TextureLayer(String name, float opacity, boolean visible, Pixels pixels, UndoManager<Pixels> undoManager)
        {
            this(name, opacity, visible, pixels, undoManager, BlendMode.NORMAL);
        }

        public TextureLayer(String name, float opacity, boolean visible, Pixels pixels, UndoManager<Pixels> undoManager, BlendMode blendMode)
        {
            this.name = name;
            this.opacity = opacity;
            this.visible = visible;
            this.pixels = pixels;
            this.undoManager = undoManager;
            this.blendMode = blendMode == null ? BlendMode.NORMAL : blendMode;
        }
    }

    private List<TextureLayer> copyLayers(List<TextureLayer> source)
    {
        List<TextureLayer> copy = new ArrayList<>();

        for (TextureLayer layer : source)
        {
            Pixels pixels = this.copyPixels(layer.pixels);

            if (pixels == null && layer.pixels != null && layer.pixels.width > 0 && layer.pixels.height > 0)
            {
                pixels = Pixels.fromSize(layer.pixels.width, layer.pixels.height);
            }

            copy.add(new TextureLayer(layer.name, layer.opacity, layer.visible, pixels, layer.undoManager, layer.blendMode));
        }

        return copy;
    }

    private Pixels copyPixels(Pixels pixels)
    {
        if (pixels == null || pixels.getBuffer() == null || pixels.width <= 0 || pixels.height <= 0)
        {
            return null;
        }

        Pixels copy = Pixels.fromSize(pixels.width, pixels.height);

        copy.draw(pixels, 0, 0, copy.width, copy.height);

        return copy;
    }

    private void clearLayerPreviewTextures()
    {
        for (Texture texture : this.layerPreviewTextures)
        {
            if (texture != null && texture.isValid())
            {
                texture.delete();
            }
        }

        this.layerPreviewTextures.clear();
    }

    private Texture createLayerPreviewTexture(TextureLayer layer)
    {
        if (layer == null || layer.pixels == null || layer.pixels.getBuffer() == null)
        {
            return null;
        }

        Texture previewTexture = new Texture();
        previewTexture.setFilter(GL11.GL_NEAREST);

        layer.pixels.rewindBuffer();
        previewTexture.bind();
        previewTexture.updateTexture(layer.pixels);
        this.layerPreviewTextures.add(previewTexture);

        return previewTexture;
    }

    private void saveCurrentTextureLayers()
    {
        Link texture = this.main.getTexture();

        if (texture == null)
        {
            return;
        }

        this.storeActiveLayerPixels();
        this.layersByTexture.put(texture, this.copyLayers(this.layers));

        if (this.selectedLayerIndex >= 0)
        {
            this.selectedLayerByTexture.put(texture, this.selectedLayerIndex);
        }
        else
        {
            this.selectedLayerByTexture.remove(texture);
        }
    }

    private void loadTextureLayers(Link texture)
    {
        this.layers.clear();
        this.selectedLayerIndex = -1;

        if (texture == null)
        {
            this.ensureDefaultLayer();

            return;
        }

        List<TextureLayer> storedLayers = this.layersByTexture.get(texture);

        if (storedLayers == null || storedLayers.isEmpty())
        {
            this.ensureDefaultLayer();
            this.layersByTexture.put(texture, this.copyLayers(this.layers));

            return;
        }

        this.layers.addAll(this.copyLayers(storedLayers));

        int selected = this.selectedLayerByTexture.getOrDefault(texture, this.layers.size() - 1);
        this.selectedLayerIndex = Math.max(0, Math.min(selected, this.layers.size() - 1));
        this.layerOpacity.setValue(Math.round(this.layers.get(this.selectedLayerIndex).opacity * 100F));
    }

    public UITexturePainter(Consumer<Link> saveCallback)
    {
        this.brightness = new UITrackpad();
        this.brightness.limit(0, 1).setValue(0.7);
        this.brightness.tooltip(UIKeys.TEXTURES_VIEWER_BRIGHTNESS, Direction.BOTTOM);
        this.brightness.w(52).maxW(52);

        this.brush = new UITrackpad((v) ->
        {
            int brushSize = Math.max(1, v.intValue());

            this.main.setBrushSize(brushSize);
            this.forEachReferenceEditor((editor) -> editor.setBrushSize(brushSize));

            if (this.reference != null)
            {
                this.reference.setBrushSize(brushSize);
            }
        });
        this.brush.integer().limit(1, 32, true).setValue(1);
        this.brush.tooltip(UIKeys.TEXTURES_BRUSH_SIZE, Direction.BOTTOM);
        this.brush.w(40).maxW(40);

        this.primary = new UIColor((c) -> {}).noLabel();
        this.primary.direction(Direction.BOTTOM).h(20);
        this.secondary = new UIColor((c) -> {}).noLabel();
        this.secondary.direction(Direction.BOTTOM).wh(20, 20);

        this.primary.setColor(Colors.WHITE);
        this.secondary.setColor(0);

        this.toolBrush = new UIIcon(Icons.BRUSH, (b) -> this.setActiveTool(UIPixelsEditor.Tool.BRUSH))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolEraser = new UIIcon(Icons.ERASER, (b) -> this.setActiveTool(UIPixelsEditor.Tool.ERASER))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolShading = new UIIcon(Icons.SUN, (b) -> this.setActiveTool(UIPixelsEditor.Tool.SHADING))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolNoise = new UIIcon(Icons.SPRAY, (b) -> this.setActiveTool(UIPixelsEditor.Tool.NOISE))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolSelect = new UIIcon(Icons.OUTLINE, (b) -> this.setActiveTool(UIPixelsEditor.Tool.SELECT))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolSelect.context((menu) ->
        {
            menu.action(Icons.FULLSCREEN, UIKeys.TEXTURE_PAINTER_SELECT_ALL, this::selectAll);
            menu.action(Icons.COPY, UIKeys.TEXTURE_PAINTER_COPY, this::copySelection);
            menu.action(Icons.CUT, UIKeys.TEXTURE_PAINTER_CUT, this::cutSelection);
            menu.action(Icons.PASTE, UIKeys.TEXTURE_PAINTER_PASTE, this::pasteSelection);
            menu.action(Icons.CLOSE, UIKeys.TEXTURE_PAINTER_DESELECT, this::clearSelection);
        });
        this.toolPick = new UIIcon(Icons.DROPPER, (b) -> this.setActiveTool(UIPixelsEditor.Tool.PICK))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolFill = new UIIcon(Icons.DROP, (b) -> this.setActiveTool(UIPixelsEditor.Tool.FILL))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolSquare = new UIIcon(Icons.SQUARE, (b) -> this.setBrushShape(UIPixelsEditor.BrushShape.SQUARE))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolCircle = new UIIcon(Icons.CIRCLE, (b) -> this.setBrushShape(UIPixelsEditor.BrushShape.CIRCLE))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolLockAlpha = new UIIcon(Icons.LOCKED, (b) -> this.toggleLockAlpha())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolShape = new UIIcon(Icons.SHAPES, (b) -> this.setActiveTool(UIPixelsEditor.Tool.SHAPE))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolShape.context((menu) ->
        {
            menu.action(Icons.SQUARE, UIKeys.TEXTURE_PAINTER_SHAPE_RECTANGLE, () -> this.setShapeType(UIPixelsEditor.ShapeType.RECTANGLE));
            menu.action(Icons.CIRCLE, UIKeys.TEXTURE_PAINTER_SHAPE_CIRCLE, () -> this.setShapeType(UIPixelsEditor.ShapeType.CIRCLE));
            menu.action(Icons.BLOCK, UIKeys.TEXTURE_PAINTER_SHAPE_FILLED, () -> this.toggleShapeFilled());
        });
        this.toolGradient = new UIIcon(Icons.GRAPH, (b) -> this.setActiveTool(UIPixelsEditor.Tool.GRADIENT))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolMirrorX = new UIIcon(Icons.ALL_DIRECTIONS, (b) -> this.toggleMirrorX())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolMirrorY = new UIIcon(Icons.EXCHANGE, (b) -> this.toggleMirrorY())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };
        this.toolPixelPerfect = new UIIcon(Icons.MAZE, (b) -> this.togglePixelPerfect())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (this.isActive())
                {
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                }
            }
        };

        this.toolBrush.tooltip(UIKeys.GENERAL_EDIT, Direction.BOTTOM);
        this.toolEraser.tooltip(UIKeys.TEXTURE_EDITOR_ERASE, Direction.BOTTOM);
        this.toolShading.tooltip(UIKeys.TEXTURE_PAINTER_TOOL_SHADING, Direction.BOTTOM);
        this.toolNoise.tooltip(UIKeys.TEXTURE_PAINTER_TOOL_NOISE, Direction.BOTTOM);
        this.toolSelect.tooltip(UIKeys.TEXTURE_PAINTER_TOOL_SELECT, Direction.BOTTOM);
        this.toolShape.tooltip(UIKeys.TEXTURE_PAINTER_TOOL_SHAPE, Direction.BOTTOM);
        this.toolGradient.tooltip(UIKeys.TEXTURE_PAINTER_TOOL_GRADIENT, Direction.BOTTOM);
        this.toolPick.tooltip(UIKeys.TEXTURES_KEYS_PICK, Direction.BOTTOM);
        this.toolFill.tooltip(UIKeys.TEXTURES_KEYS_FILL, Direction.BOTTOM);
        this.toolSquare.tooltip(UIKeys.KEYFRAMES_SHAPES_SQUARE, Direction.BOTTOM);
        this.toolCircle.tooltip(UIKeys.KEYFRAMES_SHAPES_CIRCLE, Direction.BOTTOM);
        this.toolLockAlpha.tooltip(UIKeys.TEXTURE_PAINTER_LOCK_ALPHA_TOOLTIP, Direction.BOTTOM);
        this.toolMirrorX.tooltip(UIKeys.TEXTURE_PAINTER_TOOL_MIRROR_X, Direction.BOTTOM);
        this.toolMirrorY.tooltip(UIKeys.TEXTURE_PAINTER_TOOL_MIRROR_Y, Direction.BOTTOM);
        this.toolPixelPerfect.tooltip(UIKeys.TEXTURE_PAINTER_TOOL_PIXEL_PERFECT, Direction.BOTTOM);

        this.toolImageOps = new UIIcon(Icons.CONVERT, (b) -> {});
        this.toolImageOps.context((menu) ->
        {
            menu.action(Icons.ALL_DIRECTIONS, UIKeys.TEXTURE_PAINTER_FLIP_H, this::flipHorizontal);
            menu.action(Icons.EXCHANGE, UIKeys.TEXTURE_PAINTER_FLIP_V, this::flipVertical);
            menu.action(Icons.REFRESH, UIKeys.TEXTURE_PAINTER_ROTATE_CW, () -> this.rotate90(true));
            menu.action(Icons.REDO, UIKeys.TEXTURE_PAINTER_ROTATE_CCW, () -> this.rotate90(false));
            menu.action(Icons.FULLSCREEN, UIKeys.TEXTURE_PAINTER_ROTATE_180, this::rotate180);
            menu.action(Icons.GRAPH, UIKeys.TEXTURE_PAINTER_INVERT, this::invertColors);
            menu.action(Icons.SPHERE, UIKeys.TEXTURE_PAINTER_GRAYSCALE, this::grayscale);
            menu.action(Icons.SUN, UIKeys.TEXTURE_PAINTER_ADJUST_COLORS, this::openAdjustColorsOverlay);
        });
        this.toolImageOps.tooltip(UIKeys.TEXTURE_PAINTER_OPS_IMAGE, Direction.BOTTOM);

        this.main = new UITextureEditor().saveCallback(saveCallback);
        this.main.renderTextureSupplier(this::getComposedEditorTexture);
        this.main.savePixelsSupplier(this::getComposedSavePixels);
        this.configureEditor(this.main);
        this.main.full(this);
        this.main.undo.removeFromParent();
        this.main.redo.removeFromParent();
        this.main.resize.removeFromParent();
        this.main.extract.removeFromParent();
        this.main.save.removeFromParent();
        this.main.resize.callback = (b) -> this.openResizeOverlay();
        this.main.resize.tooltip(UIKeys.TEXTURES_RESIZE, Direction.BOTTOM);
        this.main.extract.tooltip(UIKeys.TEXTURES_EXTRACT_FRAMES_TITLE, Direction.BOTTOM);
        this.main.save.tooltip(UIKeys.TEXTURES_SAVE, Direction.BOTTOM);
        this.toolBrush.wh(20, 20).minW(20).maxW(20);
        this.toolEraser.wh(20, 20).minW(20).maxW(20);
        this.toolShading.wh(20, 20).minW(20).maxW(20);
        this.toolNoise.wh(20, 20).minW(20).maxW(20);
        this.toolSelect.wh(20, 20).minW(20).maxW(20);
        this.toolShape.wh(20, 20).minW(20).maxW(20);
        this.toolGradient.wh(20, 20).minW(20).maxW(20);
        this.toolPick.wh(20, 20).minW(20).maxW(20);
        this.toolFill.wh(20, 20).minW(20).maxW(20);
        this.toolSquare.wh(20, 20).minW(20).maxW(20);
        this.toolCircle.wh(20, 20).minW(20).maxW(20);
        this.toolLockAlpha.wh(20, 20).minW(20).maxW(20);
        this.toolMirrorX.wh(20, 20).minW(20).maxW(20);
        this.toolMirrorY.wh(20, 20).minW(20).maxW(20);
        this.toolPixelPerfect.wh(20, 20).minW(20).maxW(20);
        this.toolImageOps.wh(20, 20).minW(20).maxW(20);
        this.main.undo.wh(20, 20).minW(20).maxW(20);
        this.main.redo.wh(20, 20).minW(20).maxW(20);
        this.main.resize.wh(20, 20).minW(20).maxW(20);
        this.main.extract.wh(20, 20).minW(20).maxW(20);
        this.main.save.wh(20, 20).minW(20).maxW(20);
        this.headerToolbar = new UIElement();
        UIElement toolsGroup = UI.row(
            0,
            this.toolBrush,
            this.toolEraser,
            this.toolShading,
            this.toolNoise,
            this.toolSelect,
            this.toolShape,
            this.toolGradient,
            this.toolPick,
            this.toolFill.marginRight(8),
            this.toolSquare,
            this.toolCircle,
            this.toolLockAlpha,
            this.toolMirrorX,
            this.toolMirrorY,
            this.toolPixelPerfect.marginRight(8),
            this.toolImageOps.marginRight(8),
            this.main.undo,
            this.main.redo,
            this.main.resize,
            this.main.extract,
            this.main.save
        );
        toolsGroup.row(0).width(20);
        toolsGroup.relative(this.headerToolbar).xy(0, 0).h(20).w(1F, -108);

        UIElement controlsGroup = UI.row(0, this.brush.marginRight(4), this.brightness);
        controlsGroup.relative(this.headerToolbar).x(1F, -96).y(0).wh(96, 20);

        this.headerToolbar.add(toolsGroup, controlsGroup);
        this.updateToolButtons();

        this.sidePanelResizer = new UIElement()
        {
            private boolean dragging;

            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    this.dragging = true;

                    return true;
                }

                return super.subMouseClicked(context);
            }

            @Override
            protected boolean subMouseReleased(UIContext context)
            {
                this.dragging = false;

                return super.subMouseReleased(context);
            }

            @Override
            public void render(UIContext context)
            {
                super.render(context);

                if (this.dragging && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
                {
                    this.dragging = false;
                }

                if (this.dragging)
                {
                    int newW = UITexturePainter.this.area.ex() - context.mouseX;
                    int clampedW = Math.max(140, Math.min(UITexturePainter.this.area.w - 150, newW));

                    if (clampedW != UITexturePainter.this.sidePanelWidth)
                    {
                        UITexturePainter.this.sidePanelWidth = clampedW;
                        UITexturePainter.this.updateEditorsLayout();
                        UITexturePainter.this.resize();
                    }
                }

                if (this.area.isInside(context) || this.dragging)
                {
                    int color = this.dragging ? (0xff000000 | BBSSettings.primaryColor.get()) : 0x8840a0ff;
                    context.batcher.box(this.area.x + 2, this.area.y, this.area.x + 4, this.area.ey(), color);
                }
            }
        };

        this.add(this.main);
        this.setupSidePanel();
        this.add(this.sidePanelResizer);

        this.modelPreviewArea = new UIElement();
        this.modelPreview = new UIFormRenderer();
        this.modelPreview.grid = false;
        this.modelPreview.setDistance(14);
        this.modelPreview.setPosition(0F, 1F, 0F);
        this.modelPreview.setRotation(34F, 8F);
        this.modelPreview.relative(this.modelPreviewArea).full(this.modelPreviewArea);
        this.modelPreviewArea.add(this.modelPreview);
        this.modelPreviewArea.setVisible(false);
        this.add(this.modelPreviewArea);

        IKey category = UIKeys.TEXTURES_KEYS_CATEGORY;

        this.keys().register(Keys.PIXEL_SWAP, this::swapColors).inside().category(category);
        this.keys().register(Keys.PIXEL_PICK, this::pickColor).inside().category(category);
        this.keys().register(Keys.PIXEL_FILL, this::fillColor).inside().category(category);
    }

    private void setupSidePanel()
    {
        this.sidePanel = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                this.area.render(context.batcher, 0xFF141417);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50);
                super.render(context);
            }
        };

        this.sidePanelInnerResizer = new UIElement()
        {
            private boolean dragging;

            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    this.dragging = true;

                    return true;
                }

                return super.subMouseClicked(context);
            }

            @Override
            protected boolean subMouseReleased(UIContext context)
            {
                this.dragging = false;

                return super.subMouseReleased(context);
            }

            @Override
            public void render(UIContext context)
            {
                super.render(context);

                if (this.dragging && !Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
                {
                    this.dragging = false;
                }

                if (this.dragging)
                {
                    int newSplit = context.mouseY - UITexturePainter.this.sidePanel.area.y;
                    int clampedSplit = Math.max(150, Math.min(UITexturePainter.this.sidePanel.area.h - 80, newSplit));

                    if (clampedSplit != UITexturePainter.this.sidePanelSplitY)
                    {
                        UITexturePainter.this.sidePanelSplitY = clampedSplit;
                        UITexturePainter.this.updateSidePanelLayout();
                        UITexturePainter.this.sidePanel.resize();
                    }
                }

                int midY = this.area.y + this.area.h / 2;
                context.batcher.box(this.area.x + 6, midY, this.area.ex() - 6, midY + 1, Colors.A50);

                if (this.area.isInside(context) || this.dragging)
                {
                    int color = this.dragging ? (0xff000000 | BBSSettings.primaryColor.get()) : 0x8840a0ff;
                    int midX = this.area.x + this.area.w / 2;

                    context.batcher.box(this.area.x + 6, midY - 1, this.area.ex() - 6, midY + 2, color);
                    context.batcher.box(midX - 12, midY - 2, midX + 12, midY + 3, color);
                }
            }
        };

        this.fixedColorPicker = new UITextureInlineColorPicker((color) ->
        {
            if (this.editingPrimary)
            {
                this.primary.setColor(color);
            }
            else
            {
                this.secondary.setColor(color);
            }

            this.updateColorSlots();
        });
        this.fixedColorPicker.setup(0, 0);

        this.tabColor = new UIButton(UIKeys.TEXTURE_PAINTER_TAB_COLOR, (b) -> this.setTopTab(true));
        this.tabPalette = new UIButton(UIKeys.TEXTURE_PAINTER_TAB_PALETTE, (b) -> this.setTopTab(false));
        this.tabColor.relative(this.sidePanel).xy(8, 8).w(52).h(20);
        this.tabPalette.relative(this.sidePanel).xy(62, 8).w(52).h(20);

        this.primarySlot = new UIButton(IKey.EMPTY, (b) -> this.setEditingPrimary(true))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (UITexturePainter.this.editingPrimary)
                {
                    int outline = 0xff000000 | BBSSettings.primaryColor.get();
                    context.batcher.outline(this.area.x - 1, this.area.y - 1, this.area.ex() + 1, this.area.ey() + 1, outline);
                }
            }
        };
        this.primarySlot.wh(18, 20).tooltip(UIKeys.TEXTURE_PAINTER_PRIMARY_COLOR, Direction.BOTTOM);
        this.primarySlot.relative(this.sidePanel).xy(118, 8);

        this.secondarySlot = new UIButton(IKey.EMPTY, (b) -> this.setEditingPrimary(false))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);

                if (!UITexturePainter.this.editingPrimary)
                {
                    int outline = 0xff000000 | BBSSettings.primaryColor.get();
                    context.batcher.outline(this.area.x - 1, this.area.y - 1, this.area.ex() + 1, this.area.ey() + 1, outline);
                }
            }
        };
        this.secondarySlot.wh(18, 20).tooltip(UIKeys.TEXTURE_PAINTER_SECONDARY_COLOR, Direction.BOTTOM);
        this.secondarySlot.relative(this.sidePanel).xy(138, 8);

        this.swapColorsButton = new UIIcon(Icons.REFRESH, (b) -> this.swapColors());
        this.swapColorsButton.wh(18, 20).tooltip(UIKeys.TEXTURES_KEYS_SWAP, Direction.BOTTOM);
        this.swapColorsButton.relative(this.sidePanel).xy(158, 8);

        this.colorTabContent = new UIElement();
        this.fixedColorPicker.relative(this.colorTabContent).xy(0, 0).w(1F).h(1F);
        this.colorTabContent.add(this.fixedColorPicker);

        this.paletteTabContent = UI.scrollView(4, 0);

        this.paletteSwatchesContainer = new UIElement();
        this.paletteSwatchesContainer.h(86);

        this.extractPaletteButton = new UIButton(UIKeys.TEXTURE_PAINTER_EXTRACT_PALETTE, (b) -> this.extractPaletteFromTexture());
        this.extractPaletteButton.h(20).tooltip(UIKeys.TEXTURE_PAINTER_EXTRACT_PALETTE, Direction.BOTTOM);

        this.palettePresetsButton = new UIButton(UIKeys.TEXTURE_PAINTER_PALETTE_PRESETS, (b) -> {});
        this.palettePresetsButton.context((menu) ->
        {
            menu.action(Icons.BLOCK, UIKeys.TEXTURE_PAINTER_PALETTE_VANILLA, () -> this.applyPalettePreset(new int[] {
                0x1f1f1f, 0xffffff, 0x8f3f20, 0xd87f33, 0xb02e26, 0xf9801d,
                0x5e7c16, 0x835432, 0x3c44aa, 0x8932b8, 0x169c9c, 0x474f52,
                0x9c9d97, 0x33ebcb, 0xea323c, 0x00bfff, 0xffd700, 0x7cfc00
            }));
            menu.action(Icons.SUN, UIKeys.TEXTURE_PAINTER_PALETTE_NETHER, () -> this.applyPalettePreset(new int[] {
                0x1a0808, 0x380e0e, 0x5e1818, 0x8a2020, 0xbe2b2b, 0xf04824,
                0xff7b00, 0xffb700, 0x301934, 0x4d134d, 0x800080, 0x9932cc,
                0x0d3b66, 0x006494, 0x00a6fb, 0x0582ca, 0x006466, 0x065a60
            }));
            menu.action(Icons.SPHERE, UIKeys.TEXTURE_PAINTER_PALETTE_END, () -> this.applyPalettePreset(new int[] {
                0x0b0813, 0x18122b, 0x271e3d, 0x392467, 0x5d3587, 0xa367b1,
                0xdf826c, 0xebd9b4, 0xdbcfb0, 0xb8aa85, 0x8f825e, 0x61563b,
                0x103738, 0x1b5c5e, 0x298487, 0x45b3b6, 0x72e1e4, 0xaef7f9
            }));
            menu.action(Icons.JOYSTICK, UIKeys.TEXTURE_PAINTER_PALETTE_PICO8, () -> this.applyPalettePreset(new int[] {
                0x000000, 0x1d2b53, 0x7e2553, 0x008751, 0xab5236, 0x5f574f,
                0xc2c3c7, 0xfff1e8, 0xff004d, 0xffa300, 0xffec27, 0x00e436,
                0x29adff, 0x83769c, 0xff77a8, 0xffccaa, 0x222034, 0xffffff
            }));
            menu.action(Icons.TREE, UIKeys.TEXTURE_PAINTER_PALETTE_NATURE, () -> this.applyPalettePreset(new int[] {
                0x1e3f20, 0x2d5a27, 0x3e7a33, 0x529a42, 0x6bbb55, 0x8ee06f,
                0x3d2817, 0x5c3c21, 0x7f532f, 0xa06b3e, 0xc48954, 0xe4ab76,
                0x2b3a42, 0x3f5866, 0x56778a, 0x709bb0, 0x93c0d6, 0xc4e5f2
            }));
        });
        this.palettePresetsButton.h(20).tooltip(UIKeys.TEXTURE_PAINTER_PALETTE_PRESETS, Direction.BOTTOM);

        this.paletteImportButton = new UIButton(UIKeys.TEXTURE_PAINTER_PALETTE_IMPORT, (b) -> this.importPaletteFromClipboard());
        this.paletteImportButton.h(20).tooltip(UIKeys.TEXTURE_PAINTER_PALETTE_IMPORT, Direction.BOTTOM);

        this.paletteExportButton = new UIButton(UIKeys.TEXTURE_PAINTER_PALETTE_EXPORT, (b) -> this.exportPaletteToClipboard());
        this.paletteExportButton.h(20).tooltip(UIKeys.TEXTURE_PAINTER_PALETTE_EXPORT, Direction.BOTTOM);

        UIElement paletteImportExportRow = UI.row(4, this.paletteImportButton, this.paletteExportButton);
        paletteImportExportRow.h(20);

        this.paletteTabContent.add(
            this.paletteSwatchesContainer,
            this.extractPaletteButton,
            this.palettePresetsButton,
            paletteImportExportRow
        );
        this.refreshPaletteSwatches();

        this.tabImages = new UIButton(UIKeys.TEXTURE_PAINTER_TAB_IMAGES, (b) -> this.setBottomTab(false));
        this.tabLayers = new UIButton(UIKeys.TEXTURE_PAINTER_TAB_LAYERS, (b) -> this.setBottomTab(true));

        this.mediaTabContent = new UIElement();

        this.imageRow = new UIElement();
        this.imageRow.relative(this.mediaTabContent).full(this.mediaTabContent);

        this.selectTextureButton = new UIButton(UIKeys.TEXTURE_PAINTER_ADD_REFERENCE, (b) -> this.openTextureSelector());
        this.selectTextureButton.relative(this.imageRow).xy(0, 0).w(1F).h(20).tooltip(UIKeys.TEXTURE_PAINTER_OPEN_TEXTURE_PICKER, Direction.BOTTOM);

        this.imageRows = UI.scrollView(2, 0);
        this.imageRows.relative(this.imageRow).xy(0, 24).w(1F).h(1F, -24);

        this.imageRow.add(this.selectTextureButton, this.imageRows);

        this.layerRow = new UIElement();
        this.layerRow.relative(this.mediaTabContent).full(this.mediaTabContent);

        this.addLayerButton = new UIIcon(Icons.ADD, (b) -> this.addLayer());
        this.addLayerButton.wh(18, 20).tooltip(UIKeys.TEXTURE_PAINTER_ADD_LAYER, Direction.BOTTOM);

        this.duplicateLayerButton = new UIIcon(Icons.DUPE, (b) -> this.duplicateLayer(this.selectedLayerIndex));
        this.duplicateLayerButton.wh(18, 20).tooltip(UIKeys.TEXTURE_PAINTER_DUPLICATE_LAYER, Direction.BOTTOM);

        this.removeLayerButton = new UIIcon(Icons.REMOVE, (b) -> this.removeLayer(this.selectedLayerIndex));
        this.removeLayerButton.wh(18, 20).tooltip(UIKeys.TEXTURE_PAINTER_REMOVE_LAYER, Direction.BOTTOM);

        this.moveLayerUpButton = new UIIcon(Icons.MOVE_UP, (b) -> this.moveLayerUp(this.selectedLayerIndex));
        this.moveLayerUpButton.wh(18, 20).tooltip(UIKeys.TEXTURE_PAINTER_MOVE_LAYER_UP, Direction.BOTTOM);

        this.moveLayerDownButton = new UIIcon(Icons.MOVE_DOWN, (b) -> this.moveLayerDown(this.selectedLayerIndex));
        this.moveLayerDownButton.wh(18, 20).tooltip(UIKeys.TEXTURE_PAINTER_MOVE_LAYER_DOWN, Direction.BOTTOM);

        this.layerOptionsButton = new UIIcon(Icons.MORE, (b) -> {});
        this.layerOptionsButton.context((menu) ->
        {
            menu.action(Icons.EDIT, UIKeys.TEXTURE_PAINTER_RENAME_LAYER, () -> this.renameLayer(this.selectedLayerIndex));
            menu.action(Icons.DUPE, UIKeys.TEXTURE_PAINTER_DUPLICATE_LAYER, () -> this.duplicateLayer(this.selectedLayerIndex));
            if (this.selectedLayerIndex > 0)
            {
                menu.action(Icons.MORE, UIKeys.TEXTURE_PAINTER_MERGE_DOWN, () -> this.mergeDownLayer(this.selectedLayerIndex));
            }
            if (this.layers.size() > 1)
            {
                menu.action(Icons.FULLSCREEN, UIKeys.TEXTURE_PAINTER_FLATTEN, this::flattenLayers);
            }
            menu.action(Icons.BLOCK, UIKeys.TEXTURE_PAINTER_BLEND_NORMAL, () -> this.setLayerBlendMode(this.selectedLayerIndex, BlendMode.NORMAL));
            menu.action(Icons.CLOSE, UIKeys.TEXTURE_PAINTER_BLEND_MULTIPLY, () -> this.setLayerBlendMode(this.selectedLayerIndex, BlendMode.MULTIPLY));
            menu.action(Icons.SUN, UIKeys.TEXTURE_PAINTER_BLEND_SCREEN, () -> this.setLayerBlendMode(this.selectedLayerIndex, BlendMode.SCREEN));
            menu.action(Icons.GRAPH, UIKeys.TEXTURE_PAINTER_BLEND_OVERLAY, () -> this.setLayerBlendMode(this.selectedLayerIndex, BlendMode.OVERLAY));
            menu.action(Icons.ADD, UIKeys.TEXTURE_PAINTER_BLEND_ADD, () -> this.setLayerBlendMode(this.selectedLayerIndex, BlendMode.ADD));
            menu.action(Icons.MOVE_DOWN, UIKeys.TEXTURE_PAINTER_BLEND_DARKEN, () -> this.setLayerBlendMode(this.selectedLayerIndex, BlendMode.DARKEN));
            menu.action(Icons.MOVE_UP, UIKeys.TEXTURE_PAINTER_BLEND_LIGHTEN, () -> this.setLayerBlendMode(this.selectedLayerIndex, BlendMode.LIGHTEN));
            menu.action(Icons.REMOVE, UIKeys.TEXTURE_PAINTER_REMOVE_LAYER, () -> this.removeLayer(this.selectedLayerIndex));
        });
        this.layerOptionsButton.wh(18, 20).tooltip(UIKeys.TEXTURE_PAINTER_LAYER_OPTIONS, Direction.BOTTOM);

        this.layerOpacity = new UITrackpad((v) ->
        {
            if (this.selectedLayerIndex >= 0 && this.selectedLayerIndex < this.layers.size())
            {
                float opacity = Math.max(0F, Math.min(1F, v.floatValue() / 100F));
                this.layers.get(this.selectedLayerIndex).opacity = opacity;
                this.refreshLayerRows();
            }
        });
        this.layerOpacity.integer().limit(0, 100, true);
        this.layerOpacity.setValue(100);
        this.layerOpacity.tooltip(UIKeys.TEXTURE_PAINTER_LAYER_OPACITY, Direction.BOTTOM);
        this.layerOpacity.relative(this.layerRow).x(1F, -46).y(0).w(46).h(20);

        UIElement layerButtonsGroup = UI.row(
            1,
            this.addLayerButton,
            this.duplicateLayerButton,
            this.removeLayerButton,
            this.moveLayerUpButton,
            this.moveLayerDownButton,
            this.layerOptionsButton
        );
        layerButtonsGroup.relative(this.layerRow).xy(0, 0).w(1F, -50).h(20);

        this.layerRows = UI.scrollView(2, 0);
        this.layerRows.relative(this.layerRow).xy(0, 24).w(1F).h(1F, -24);

        this.layerRow.add(layerButtonsGroup, this.layerOpacity, this.layerRows);
        this.mediaTabContent.add(this.imageRow, this.layerRow);

        this.sidePanel.add(
            this.tabColor,
            this.tabPalette,
            this.primarySlot,
            this.secondarySlot,
            this.swapColorsButton,
            this.colorTabContent,
            this.paletteTabContent,
            this.sidePanelInnerResizer,
            this.tabImages,
            this.tabLayers,
            this.mediaTabContent
        );

        this.updateSidePanelLayout();

        this.fixedColorPicker.setColor(this.primary.picker.color.getRGBColor());
        this.setTopTab(true);
        this.setBottomTab(true);
        this.ensureDefaultLayer();
        this.refreshLayerRows();
        this.refreshImageRows();
        this.updateColorSlots();
        this.add(this.sidePanel);
    }

    private void updateSidePanelLayout()
    {
        int topHeight = Math.max(60, this.sidePanelSplitY - 38);

        this.colorTabContent.relative(this.sidePanel).xy(8, 32).w(1F, -16).h(topHeight);
        this.paletteTabContent.relative(this.sidePanel).xy(8, 32).w(1F, -16).h(topHeight);

        this.sidePanelInnerResizer.relative(this.sidePanel).xy(0, this.sidePanelSplitY - 4).w(1F).h(8);

        this.tabImages.relative(this.sidePanel).x(8).y(this.sidePanelSplitY + 4).w(0.5F, -10).h(20);
        this.tabLayers.relative(this.sidePanel).x(0.5F, 2).y(this.sidePanelSplitY + 4).w(0.5F, -10).h(20);

        this.mediaTabContent.relative(this.sidePanel).x(8).y(this.sidePanelSplitY + 28).w(1F, -16).h(1F, -(this.sidePanelSplitY + 36));
    }

    public UITexturePainter withFormPreview(Supplier<Form> supplier)
    {
        this.formPreviewSupplier = supplier;
        this.modelPreviewArea.setVisible(supplier != null);
        this.refreshModelPreview();
        this.updateEditorsLayout();
        this.resize();

        return this;
    }

    private void swapColors()
    {
        int swap = this.primary.picker.color.getRGBColor();

        this.primary.setColor(this.secondary.picker.color.getRGBColor());
        this.secondary.setColor(swap);
        this.fixedColorPicker.setColor(this.getActiveColor());
        this.updateColorSlots();
    }

    private int getActiveColor()
    {
        return this.editingPrimary ? this.primary.picker.color.getRGBColor() : this.secondary.picker.color.getRGBColor();
    }

    private Color getActiveBrushColor()
    {
        return this.editingPrimary ? this.primary.picker.color : this.secondary.picker.color;
    }

    private void setEditingPrimary(boolean editingPrimary)
    {
        this.editingPrimary = editingPrimary;
        this.fixedColorPicker.setColor(this.getActiveColor());
        this.updateColorSlots();
    }

    private void updateColorSlots()
    {
        this.primarySlot.color(this.primary.picker.color.getRGBColor()).background(true);
        this.secondarySlot.color(this.secondary.picker.color.getRGBColor()).background(true);
    }

    private void setTopTab(boolean color)
    {
        this.topTabColor = color;
        this.colorTabContent.setVisible(color);
        this.paletteTabContent.setVisible(!color);

        this.tabColor.background(color).textColor(color ? Colors.WHITE : 0xb0b0b0, false);
        this.tabPalette.background(!color).textColor(color ? 0xb0b0b0 : Colors.WHITE, false);
    }

    private void setBottomTab(boolean layers)
    {
        this.bottomTabLayers = layers;
        this.layerRow.setVisible(layers);
        this.imageRow.setVisible(!layers);

        this.tabLayers.background(layers).textColor(layers ? Colors.WHITE : 0xb0b0b0, false);
        this.tabImages.background(!layers).textColor(layers ? 0xb0b0b0 : Colors.WHITE, false);
    }

    private void openTextureSelector()
    {
        if (this.texturePickerPopup != null && this.texturePickerPopup.hasParent())
        {
            return;
        }

        UIContext context = this.getContext();

        if (context == null || context.menu == null || context.menu.overlay == null)
        {
            return;
        }

        UIElement overlay = context.menu.overlay;
        UIElement popup = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                this.area.render(context.batcher, Colors.A50);
                super.render(context);
            }
        };
        popup.full(overlay);
        popup.markContainer().eventPropagataion(EventPropagation.BLOCK);

        UIElement content = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                this.area.render(context.batcher, Colors.A25);
                context.batcher.outline(this.area.x - 1, this.area.y - 1, this.area.ex() + 1, this.area.ey() + 1, Colors.A100);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                super.render(context);
            }
        };
        content.relative(popup).set(20, 20, 0, 0).w(1F, -40).h(1F, -40);

        UITexturePicker picker = new UITexturePicker((link) ->
        {
            this.closeTextureSelectorPopup();

            if (link == null)
            {
                return;
            }

            this.addReferenceImage(link);
        });
        picker.disablePixelEditor();
        picker.disableMultiSkin();
        content.add(picker);
        popup.add(content);
        overlay.add(popup);
        popup.resize();
        content.resize();
        picker.full(content);
        picker.resize();
        picker.fill(this.main.getTexture());
        this.texturePickerPopup = popup;
    }

    private void closeTextureSelectorPopup()
    {
        if (this.texturePickerPopup != null)
        {
            this.texturePickerPopup.removeFromParent();
            this.texturePickerPopup = null;
        }
    }

    public void addReferenceImage(Link link)
    {
        if (link == null)
        {
            return;
        }

        for (ReferenceImage ref : this.referenceImages)
        {
            if (link.equals(ref.link))
            {
                ref.visible = true;

                if (ref.editor != null)
                {
                    ref.editor.setVisible(true);
                }

                this.updateEditorsLayout();
                this.refreshImageRows();
                this.resize();

                return;
            }
        }

        UITextureEditor refEditor = new UITextureEditor();
        refEditor.fillTexture(link);
        refEditor.setEditing(false);
        this.configureEditor(refEditor);
        refEditor.undo.removeFromParent();
        refEditor.redo.removeFromParent();
        refEditor.resize.removeFromParent();
        refEditor.extract.removeFromParent();
        refEditor.save.removeFromParent();

        this.addBefore(this.sidePanel, refEditor);

        ReferenceImage refItem = new ReferenceImage(link, refEditor);
        this.referenceImages.add(refItem);

        this.updateEditorsLayout();
        this.refreshImageRows();
        this.resize();
    }

    public void removeReferenceImage(int index)
    {
        if (index >= 0 && index < this.referenceImages.size())
        {
            ReferenceImage ref = this.referenceImages.remove(index);

            if (ref.editor != null)
            {
                ref.editor.removeFromParent();
            }

            this.updateEditorsLayout();
            this.refreshImageRows();
            this.resize();
        }
    }

    public void toggleReferenceVisibility(int index)
    {
        if (index >= 0 && index < this.referenceImages.size())
        {
            ReferenceImage ref = this.referenceImages.get(index);
            ref.visible = !ref.visible;

            if (ref.editor != null)
            {
                ref.editor.setVisible(ref.visible);
            }

            this.updateEditorsLayout();
            this.refreshImageRows();
            this.resize();
        }
    }

    private void refreshImageRows()
    {
        if (this.imageRows == null)
        {
            return;
        }

        this.imageRows.removeAll();

        if (this.referenceImages.isEmpty())
        {
            UIElement emptyLabel = UI.label(UIKeys.TEXTURE_PAINTER_NO_REFERENCES, 16);
            emptyLabel.h(40);
            this.imageRows.add(emptyLabel);
            this.imageRows.resize();

            return;
        }

        for (int i = 0; i < this.referenceImages.size(); i++)
        {
            final int index = i;
            final ReferenceImage ref = this.referenceImages.get(i);
            final Link texture = ref.link;
            String name = texture == null ? "reference.png" : StringUtils.fileName(texture.path);

            if (name == null || name.isEmpty())
            {
                name = texture == null ? "reference" : texture.toString();
            }

            UIElement row = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    int color = this.area.isInside(context) ? (Colors.A50 | BBSSettings.primaryColor.get()) : Colors.A25;
                    this.area.render(context.batcher, color);
                    super.render(context);
                }
            };
            row.h(22);

            UIElement preview = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    super.render(context);

                    context.batcher.iconArea(Icons.CHECKBOARD, Colors.A50, this.area.x, this.area.y, this.area.w, this.area.h);
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A100);

                    if (texture != null)
                    {
                        Texture thumbnail = BBSModClient.getTextures().getTexture(texture);

                        if (thumbnail != null && thumbnail.isValid())
                        {
                            context.batcher.fullTexturedBox(thumbnail, this.area.x, this.area.y, this.area.w, this.area.h);
                        }
                    }
                }
            };
            preview.relative(row).xy(2, 2).wh(18, 18);

            UIButton nameButton = new UIButton(IKey.constant(name), (b) -> {});
            nameButton.relative(row).x(24).y(1).w(1F, -66).h(20);
            nameButton.background(false).textColor(ref.visible ? Colors.WHITE : 0x888888, false);
            if (texture != null)
            {
                nameButton.tooltip(IKey.constant(texture.toString()), Direction.BOTTOM);
            }
            nameButton.context((menu) ->
            {
                menu.action(Icons.COPY, UIKeys.TEXTURE_PAINTER_COPY, () ->
                {
                    if (ref.editor != null)
                    {
                        ref.editor.copySelection();
                    }
                });
                menu.action(Icons.VISIBLE, ref.visible ? UIKeys.TEXTURE_PAINTER_HIDE_LAYER : UIKeys.TEXTURE_PAINTER_SHOW_LAYER, () -> this.toggleReferenceVisibility(index));
                menu.action(Icons.REMOVE, UIKeys.TEXTURE_PAINTER_REMOVE_LAYER, () -> this.removeReferenceImage(index));
            });

            UIIcon visibility = new UIIcon(
                ref.visible ? Icons.VISIBLE : Icons.INVISIBLE,
                (b) -> this.toggleReferenceVisibility(index)
            );
            visibility.wh(18, 18).tooltip(ref.visible ? UIKeys.TEXTURE_PAINTER_HIDE_LAYER : UIKeys.TEXTURE_PAINTER_SHOW_LAYER, Direction.BOTTOM);

            UIIcon remove = new UIIcon(
                Icons.REMOVE,
                (b) -> this.removeReferenceImage(index)
            );
            remove.wh(18, 18).tooltip(UIKeys.TEXTURE_PAINTER_REMOVE_LAYER, Direction.BOTTOM);

            UIElement buttons = UI.row(2, visibility, remove);
            buttons.relative(row).x(1F, -40).y(2).wh(38, 18);

            row.add(preview, nameButton, buttons);
            this.imageRows.add(row);
        }

        this.imageRows.resize();
    }

    private void ensureDefaultLayer()
    {
        if (this.layers.isEmpty())
        {
            this.layers.add(new TextureLayer("layer", 1F, true, this.main.getPixels(), this.main.exportUndoManager()));
            this.selectedLayerIndex = 0;
            this.layerOpacity.setValue(100);
        }
    }

    public void addLayer()
    {
        this.storeActiveLayerPixels();

        String name = this.layers.isEmpty() ? "layer" : "layer_" + this.layers.size();
        Pixels pixels = this.createTransparentLayerPixels();
        this.layers.add(new TextureLayer(name, 1F, true, pixels, null));
        this.selectedLayerIndex = this.layers.size() - 1;
        this.layerOpacity.setValue(100);
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
    }

    public void duplicateLayer(int index)
    {
        if (index < 0 || index >= this.layers.size())
        {
            return;
        }

        this.storeActiveLayerPixels();

        TextureLayer source = this.layers.get(index);
        Pixels copy = this.copyPixels(source.pixels);
        String name = source.name + "_copy";
        TextureLayer duplicate = new TextureLayer(name, source.opacity, source.visible, copy, null);

        this.layers.add(index + 1, duplicate);
        this.selectedLayerIndex = index + 1;
        this.layerOpacity.setValue(Math.round(duplicate.opacity * 100F));
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
    }

    public void removeLayer(int index)
    {
        if (index < 0 || index >= this.layers.size())
        {
            return;
        }

        this.storeActiveLayerPixels();

        if (this.layers.size() <= 1)
        {
            TextureLayer layer = this.layers.get(0);

            if (layer.pixels != null)
            {
                layer.pixels.drawRect(0, 0, layer.pixels.width, layer.pixels.height, 0);
            }

            layer.opacity = 1F;
            layer.name = "layer";
            this.selectedLayerIndex = 0;
            this.layerOpacity.setValue(100);
            this.loadSelectedLayerPixels();
            this.refreshLayerRows();

            return;
        }

        this.layers.remove(index);
        this.selectedLayerIndex = Math.max(0, Math.min(index, this.layers.size() - 1));
        this.layerOpacity.setValue(Math.round(this.layers.get(this.selectedLayerIndex).opacity * 100F));
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
    }

    public void moveLayerUp(int index)
    {
        if (index < 0 || index >= this.layers.size() - 1)
        {
            return;
        }

        this.storeActiveLayerPixels();
        Collections.swap(this.layers, index, index + 1);
        this.selectedLayerIndex = index + 1;
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
    }

    public void moveLayerDown(int index)
    {
        if (index <= 0 || index >= this.layers.size())
        {
            return;
        }

        this.storeActiveLayerPixels();
        Collections.swap(this.layers, index, index - 1);
        this.selectedLayerIndex = index - 1;
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
    }

    public void mergeDownLayer(int index)
    {
        if (index <= 0 || index >= this.layers.size())
        {
            return;
        }

        this.storeActiveLayerPixels();

        TextureLayer top = this.layers.get(index);
        TextureLayer bottom = this.layers.get(index - 1);

        if (top.pixels != null && bottom.pixels != null && top.pixels.getBuffer() != null && bottom.pixels.getBuffer() != null)
        {
            Color output = new Color();
            BlendMode mode = top.blendMode == null ? BlendMode.NORMAL : top.blendMode;

            for (int x = 0; x < bottom.pixels.width; x++)
            {
                for (int y = 0; y < bottom.pixels.height; y++)
                {
                    Color src = top.pixels.getColor(x, y);
                    Color dst = bottom.pixels.getColor(x, y);

                    if (src == null || src.a <= 0F || top.opacity <= 0F)
                    {
                        continue;
                    }

                    float alpha = src.a * top.opacity;

                    if (dst == null || dst.a <= 0F)
                    {
                        output.r = src.r;
                        output.g = src.g;
                        output.b = src.b;
                        output.a = alpha;
                        bottom.pixels.setColor(x, y, output);
                        continue;
                    }

                    float outA = alpha + dst.a * (1F - alpha);

                    if (outA <= 0F)
                    {
                        continue;
                    }

                    float br = this.blendChannel(src.r, dst.r, mode);
                    float bg = this.blendChannel(src.g, dst.g, mode);
                    float bb = this.blendChannel(src.b, dst.b, mode);

                    output.a = outA;
                    output.r = (br * alpha + dst.r * dst.a * (1F - alpha)) / outA;
                    output.g = (bg * alpha + dst.g * dst.a * (1F - alpha)) / outA;
                    output.b = (bb * alpha + dst.b * dst.a * (1F - alpha)) / outA;
                    bottom.pixels.setColor(x, y, output);
                }
            }
        }

        this.layers.remove(index);
        this.selectedLayerIndex = index - 1;
        this.layerOpacity.setValue(Math.round(bottom.opacity * 100F));
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
    }

    public void flattenLayers()
    {
        this.storeActiveLayerPixels();
        Pixels composed = this.composeVisibleLayers();

        if (composed == null)
        {
            return;
        }

        Pixels base = this.copyPixels(composed);

        this.layers.clear();
        this.layers.add(new TextureLayer("Background", 1F, true, base, null));
        this.selectedLayerIndex = 0;
        this.layerOpacity.setValue(100);
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
    }

    public void renameLayer(int index)
    {
        if (index < 0 || index >= this.layers.size())
        {
            return;
        }

        TextureLayer layer = this.layers.get(index);
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.TEXTURE_PAINTER_RENAME_LAYER,
            IKey.EMPTY,
            (name) ->
            {
                if (name != null && !name.trim().isEmpty())
                {
                    layer.name = name.trim();
                    this.refreshLayerRows();
                }
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel);
        panel.text.setText(layer.name);
        panel.text.textbox.moveCursorTo(layer.name.length());
        panel.text.textbox.setSelection(0);
    }

    public void toggleLockAlpha()
    {
        this.lockAlpha = !this.lockAlpha;
        this.main.setLockAlpha(this.lockAlpha);

        if (this.reference != null)
        {
            this.reference.setLockAlpha(this.lockAlpha);
        }

        this.updateToolButtons();
    }

    private Pixels createTransparentLayerPixels()
    {
        Pixels current = this.main.getPixels();

        if (current == null)
        {
            return null;
        }

        return Pixels.fromSize(current.width, current.height);
    }

    private void storeActiveLayerPixels()
    {
        if (this.selectedLayerIndex < 0 || this.selectedLayerIndex >= this.layers.size())
        {
            return;
        }

        TextureLayer activeLayer = this.layers.get(this.selectedLayerIndex);

        activeLayer.pixels = this.main.getPixels();
        activeLayer.undoManager = this.main.exportUndoManager();
    }

    private void loadSelectedLayerPixels()
    {
        if (this.selectedLayerIndex < 0 || this.selectedLayerIndex >= this.layers.size())
        {
            return;
        }

        TextureLayer layer = this.layers.get(this.selectedLayerIndex);

        if (layer.pixels == null)
        {
            layer.pixels = this.createTransparentLayerPixels();
        }

        if (layer.pixels != null)
        {
            this.main.fillPixels(layer.pixels, true);
            this.main.setEditing(true);
            this.main.importUndoManager(layer.undoManager);
            layer.undoManager = this.main.exportUndoManager();
        }
    }

    private void selectLayer(int index)
    {
        if (index < 0 || index >= this.layers.size())
        {
            return;
        }

        this.storeActiveLayerPixels();
        this.selectedLayerIndex = index;
        this.layerOpacity.setValue(Math.round(this.layers.get(index).opacity * 100F));
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
    }

    private void toggleLayerVisibility(int index)
    {
        if (index < 0 || index >= this.layers.size())
        {
            return;
        }

        TextureLayer layer = this.layers.get(index);
        layer.visible = !layer.visible;

        if (!layer.visible && this.selectedLayerIndex == index)
        {
            int next = this.findVisibleLayerIndex();

            if (next >= 0)
            {
                this.selectLayer(next);

                return;
            }
        }

        this.refreshLayerRows();
    }

    private int findVisibleLayerIndex()
    {
        for (int i = this.layers.size() - 1; i >= 0; i--)
        {
            if (this.layers.get(i).visible)
            {
                return i;
            }
        }

        return -1;
    }


    private void refreshLayerRows()
    {
        if (this.layerRows == null)
        {
            return;
        }

        this.clearLayerPreviewTextures();
        this.layerRows.removeAll();

        int count = this.layers.size();

        for (int rowIndex = 0; rowIndex < count; rowIndex++)
        {
            final int index = count - 1 - rowIndex;
            TextureLayer layer = this.layers.get(index);
            int opacity = Math.round(layer.opacity * 100F);
            String text = (rowIndex + 1) + ". " + layer.name + " (" + opacity + "%" + (layer.blendMode != BlendMode.NORMAL ? ", " + layer.blendMode.name() : "") + ")";

            UIElement row = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    boolean selected = index == UITexturePainter.this.selectedLayerIndex;
                    int color = selected ? (Colors.A50 | BBSSettings.primaryColor.get()) : Colors.A25;

                    this.area.render(context.batcher, color);
                    super.render(context);
                }
            };
            row.relative(this.layerRows).x(0).y(rowIndex * 26).w(1F, -8).h(24);
            Texture previewTexture = this.createLayerPreviewTexture(layer);

            UIElement preview = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    super.render(context);

                    context.batcher.iconArea(Icons.CHECKBOARD, Colors.A50, this.area.x, this.area.y, this.area.w, this.area.h);
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A100);

                    if (previewTexture != null && previewTexture.isValid())
                    {
                        int alpha = Math.max(0, Math.min(255, Math.round(layer.opacity * 255F)));

                        context.batcher.fullTexturedBox(previewTexture, (alpha << 24) | 0x00ffffff, this.area.x, this.area.y, this.area.w, this.area.h);
                    }
                }
            };
            preview.relative(row).xy(2, 2).wh(20, 20);

            UIButton select = new UIButton(IKey.constant(text), (b) -> this.selectLayer(index));
            select.relative(row).x(24).y(0).w(1F, -44).h(24);
            select.background(false).textColor(index == this.selectedLayerIndex ? Colors.WHITE : 0xd0d0d0, false);
            select.context((menu) ->
            {
                menu.action(Icons.EDIT, UIKeys.TEXTURE_PAINTER_RENAME_LAYER, () -> this.renameLayer(index));
                menu.action(Icons.DUPE, UIKeys.TEXTURE_PAINTER_DUPLICATE_LAYER, () -> this.duplicateLayer(index));
                if (index > 0)
                {
                    menu.action(Icons.MORE, UIKeys.TEXTURE_PAINTER_MERGE_DOWN, () -> this.mergeDownLayer(index));
                }
                if (this.layers.size() > 1)
                {
                    menu.action(Icons.FULLSCREEN, UIKeys.TEXTURE_PAINTER_FLATTEN, this::flattenLayers);
                }
                menu.action(Icons.BLOCK, UIKeys.TEXTURE_PAINTER_BLEND_NORMAL, () -> this.setLayerBlendMode(index, BlendMode.NORMAL));
                menu.action(Icons.CLOSE, UIKeys.TEXTURE_PAINTER_BLEND_MULTIPLY, () -> this.setLayerBlendMode(index, BlendMode.MULTIPLY));
                menu.action(Icons.SUN, UIKeys.TEXTURE_PAINTER_BLEND_SCREEN, () -> this.setLayerBlendMode(index, BlendMode.SCREEN));
                menu.action(Icons.GRAPH, UIKeys.TEXTURE_PAINTER_BLEND_OVERLAY, () -> this.setLayerBlendMode(index, BlendMode.OVERLAY));
                menu.action(Icons.ADD, UIKeys.TEXTURE_PAINTER_BLEND_ADD, () -> this.setLayerBlendMode(index, BlendMode.ADD));
                menu.action(Icons.MOVE_DOWN, UIKeys.TEXTURE_PAINTER_BLEND_DARKEN, () -> this.setLayerBlendMode(index, BlendMode.DARKEN));
                menu.action(Icons.MOVE_UP, UIKeys.TEXTURE_PAINTER_BLEND_LIGHTEN, () -> this.setLayerBlendMode(index, BlendMode.LIGHTEN));
                menu.action(Icons.REMOVE, UIKeys.TEXTURE_PAINTER_REMOVE_LAYER, () -> this.removeLayer(index));
            });

            UIIcon visibility = new UIIcon(() -> layer.visible ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> this.toggleLayerVisibility(index));
            visibility.relative(row).x(1F, -20).y(0).wh(20, 20);
            visibility.tooltip(layer.visible ? UIKeys.TEXTURE_PAINTER_HIDE_LAYER : UIKeys.TEXTURE_PAINTER_SHOW_LAYER, Direction.LEFT);

            row.add(preview, select, visibility);
            this.layerRows.add(row);
        }

        this.layerRows.resize();
    }

    private float blendChannel(float s, float d, BlendMode mode)
    {
        if (mode == BlendMode.MULTIPLY)
        {
            return s * d;
        }
        else if (mode == BlendMode.SCREEN)
        {
            return s + d - s * d;
        }
        else if (mode == BlendMode.OVERLAY)
        {
            return d < 0.5F ? (2F * s * d) : (1F - 2F * (1F - s) * (1F - d));
        }
        else if (mode == BlendMode.ADD)
        {
            return Math.min(1F, s + d);
        }
        else if (mode == BlendMode.DARKEN)
        {
            return Math.min(s, d);
        }
        else if (mode == BlendMode.LIGHTEN)
        {
            return Math.max(s, d);
        }

        return s;
    }

    public void setLayerBlendMode(int index, BlendMode mode)
    {
        if (index < 0 || index >= this.layers.size())
        {
            return;
        }

        this.layers.get(index).blendMode = mode == null ? BlendMode.NORMAL : mode;
        this.refreshLayerRows();
        this.saveCurrentTextureLayers();
        this.refreshModelPreview();
    }

    private Pixels composeVisibleLayers()
    {
        Pixels base = this.main.getPixels();

        if (base == null)
        {
            return null;
        }

        if (this.layersCompositePixels == null || this.layersCompositePixels.width != base.width || this.layersCompositePixels.height != base.height)
        {
            if (this.layersCompositePixels != null)
            {
                this.layersCompositePixels.delete();
            }

            this.layersCompositePixels = Pixels.fromSize(base.width, base.height);
        }

        Pixels composed = this.layersCompositePixels;
        composed.drawRect(0, 0, composed.width, composed.height, 0);
        Color output = new Color();

        for (int i = 0; i < this.layers.size(); i++)
        {
            TextureLayer layer = this.layers.get(i);

            if (!layer.visible || layer.opacity <= 0F || layer.pixels == null)
            {
                continue;
            }

            Pixels source = layer.pixels;

            if (source.getBuffer() == null)
            {
                continue;
            }

            BlendMode mode = layer.blendMode == null ? BlendMode.NORMAL : layer.blendMode;

            for (int x = 0; x < composed.width; x++)
            {
                for (int y = 0; y < composed.height; y++)
                {
                    Color src = source.getColor(x, y);

                    if (src == null)
                    {
                        continue;
                    }

                    float alpha = src.a * layer.opacity;

                    if (alpha <= 0F)
                    {
                        continue;
                    }

                    Color dst = composed.getColor(x, y);

                    if (dst == null || dst.a <= 0F)
                    {
                        output.r = src.r;
                        output.g = src.g;
                        output.b = src.b;
                        output.a = alpha;
                        composed.setColor(x, y, output);
                        continue;
                    }

                    float outA = alpha + dst.a * (1F - alpha);

                    if (outA <= 0F)
                    {
                        continue;
                    }

                    float br = this.blendChannel(src.r, dst.r, mode);
                    float bg = this.blendChannel(src.g, dst.g, mode);
                    float bb = this.blendChannel(src.b, dst.b, mode);

                    output.a = outA;
                    output.r = (br * alpha + dst.r * dst.a * (1F - alpha)) / outA;
                    output.g = (bg * alpha + dst.g * dst.a * (1F - alpha)) / outA;
                    output.b = (bb * alpha + dst.b * dst.a * (1F - alpha)) / outA;
                    composed.setColor(x, y, output);
                }
            }
        }

        return composed;
    }

    private Texture getComposedEditorTexture()
    {
        this.storeActiveLayerPixels();
        Pixels composed = this.composeVisibleLayers();

        if (composed == null)
        {
            return null;
        }

        if (this.layersCompositeTexture == null || !this.layersCompositeTexture.isValid())
        {
            this.layersCompositeTexture = new Texture();
            this.layersCompositeTexture.setFilter(GL11.GL_NEAREST);
        }

        composed.rewindBuffer();
        this.layersCompositeTexture.bind();
        this.layersCompositeTexture.updateTexture(composed);

        return this.layersCompositeTexture;
    }

    private Pixels getComposedSavePixels()
    {
        this.storeActiveLayerPixels();

        return this.composeVisibleLayers();
    }

    private UITextureEditor getHoverEditor(UIContext context)
    {
        if (this.main.area.isInside(context))
        {
            return this.main;
        }

        for (ReferenceImage ref : this.referenceImages)
        {
            if (ref.visible && ref.editor != null && ref.editor.area.isInside(context))
            {
                return ref.editor;
            }
        }

        return this.reference != null && this.reference.area.isInside(context) ? this.reference : null;
    }

    private void pickColor()
    {
        UIContext context = this.getContext();
        UITextureEditor editor = this.getHoverEditor(context);

        if (editor != null)
        {
            Vector2i pixel = editor.getHoverPixel(context.mouseX, context.mouseY);
            Color color = editor.getPixels().getColor(pixel.x, pixel.y);

            if (color != null)
            {
                if (this.editingPrimary)
                {
                    this.primary.setColor(color.getRGBColor());
                }
                else
                {
                    this.secondary.setColor(color.getRGBColor());
                }

                this.fixedColorPicker.setColor(this.getActiveColor());
                this.updateColorSlots();
            }
        }
    }

    private void fillColor()
    {
        UIContext context = this.getContext();
        UITextureEditor editor = this.getHoverEditor(context);

        if (editor != null)
        {
            Vector2i pixel = editor.getHoverPixel(context.mouseX, context.mouseY);

            editor.fillColor(pixel, this.getActiveBrushColor(), Window.isShiftPressed());
        }
    }

    private void configureEditor(UITextureEditor editor)
    {
        editor
            .colorSupplier(this::getActiveBrushColor)
            .secondaryColorSupplier(() -> this.secondary.picker.color)
            .backgroundSupplier(() -> (float) this.brightness.getValue())
            .onPickColor((color) ->
            {
                if (this.editingPrimary)
                {
                    this.primary.setColor(color.getRGBColor());
                }
                else
                {
                    this.secondary.setColor(color.getRGBColor());
                }

                this.fixedColorPicker.setColor(this.getActiveColor());
                this.updateColorSlots();
            })
            .onFillColor((pixel, replace) -> editor.fillColor(pixel, this.getActiveBrushColor(), replace))
            .setTool(this.activeTool)
            .setBrushShape(this.activeBrushShape)
            .setLockAlpha(this.lockAlpha)
            .setMirrorX(this.mirrorX)
            .setMirrorY(this.mirrorY)
            .setPixelPerfect(this.pixelPerfect)
            .setShapeType(this.activeShapeType)
            .setShapeFilled(this.shapeFilled)
            .useExternalToolbar();
        editor.setBrushSize((int) this.brush.getValue());
    }

    private void forEachReferenceEditor(Consumer<UITextureEditor> consumer)
    {
        for (ReferenceImage ref : this.referenceImages)
        {
            if (ref.editor != null)
            {
                consumer.accept(ref.editor);
            }
        }
    }

    public void setShapeType(UIPixelsEditor.ShapeType shapeType)
    {
        this.activeShapeType = shapeType == null ? UIPixelsEditor.ShapeType.RECTANGLE : shapeType;
        this.main.setShapeType(this.activeShapeType);
        this.forEachReferenceEditor((editor) -> editor.setShapeType(this.activeShapeType));

        if (this.reference != null)
        {
            this.reference.setShapeType(this.activeShapeType);
        }

        this.setActiveTool(UIPixelsEditor.Tool.SHAPE);
    }

    public void toggleShapeFilled()
    {
        this.shapeFilled = !this.shapeFilled;
        this.main.setShapeFilled(this.shapeFilled);
        this.forEachReferenceEditor((editor) -> editor.setShapeFilled(this.shapeFilled));

        if (this.reference != null)
        {
            this.reference.setShapeFilled(this.shapeFilled);
        }
    }

    public void toggleMirrorX()
    {
        this.mirrorX = !this.mirrorX;
        this.main.setMirrorX(this.mirrorX);
        this.forEachReferenceEditor((editor) -> editor.setMirrorX(this.mirrorX));

        if (this.reference != null)
        {
            this.reference.setMirrorX(this.mirrorX);
        }

        this.updateToolButtons();
    }

    public void toggleMirrorY()
    {
        this.mirrorY = !this.mirrorY;
        this.main.setMirrorY(this.mirrorY);
        this.forEachReferenceEditor((editor) -> editor.setMirrorY(this.mirrorY));

        if (this.reference != null)
        {
            this.reference.setMirrorY(this.mirrorY);
        }

        this.updateToolButtons();
    }

    public void togglePixelPerfect()
    {
        this.pixelPerfect = !this.pixelPerfect;
        this.main.setPixelPerfect(this.pixelPerfect);
        this.forEachReferenceEditor((editor) -> editor.setPixelPerfect(this.pixelPerfect));

        if (this.reference != null)
        {
            this.reference.setPixelPerfect(this.pixelPerfect);
        }

        this.updateToolButtons();
    }

    private void setActiveTool(UIPixelsEditor.Tool tool)
    {
        this.activeTool = tool == null ? UIPixelsEditor.Tool.BRUSH : tool;

        this.main.setTool(this.activeTool);
        this.forEachReferenceEditor((editor) -> editor.setTool(this.activeTool));

        if (this.reference != null)
        {
            this.reference.setTool(this.activeTool);
        }

        this.updateToolButtons();
    }

    private void setBrushShape(UIPixelsEditor.BrushShape brushShape)
    {
        this.activeBrushShape = brushShape == null ? UIPixelsEditor.BrushShape.SQUARE : brushShape;

        this.main.setBrushShape(this.activeBrushShape);
        this.forEachReferenceEditor((editor) -> editor.setBrushShape(this.activeBrushShape));

        if (this.reference != null)
        {
            this.reference.setBrushShape(this.activeBrushShape);
        }

        this.updateToolButtons();
    }

    private void updateToolButtons()
    {
        this.toolBrush.active(this.activeTool == UIPixelsEditor.Tool.BRUSH);
        this.toolEraser.active(this.activeTool == UIPixelsEditor.Tool.ERASER);
        this.toolShading.active(this.activeTool == UIPixelsEditor.Tool.SHADING);
        this.toolNoise.active(this.activeTool == UIPixelsEditor.Tool.NOISE);
        this.toolSelect.active(this.activeTool == UIPixelsEditor.Tool.SELECT);
        this.toolShape.active(this.activeTool == UIPixelsEditor.Tool.SHAPE);
        this.toolGradient.active(this.activeTool == UIPixelsEditor.Tool.GRADIENT);
        this.toolPick.active(this.activeTool == UIPixelsEditor.Tool.PICK);
        this.toolFill.active(this.activeTool == UIPixelsEditor.Tool.FILL);
        this.toolSquare.active(this.activeBrushShape == UIPixelsEditor.BrushShape.SQUARE);
        this.toolCircle.active(this.activeBrushShape == UIPixelsEditor.BrushShape.CIRCLE);
        this.toolLockAlpha.active(this.lockAlpha);
        this.toolMirrorX.active(this.mirrorX);
        this.toolMirrorY.active(this.mirrorY);
        this.toolPixelPerfect.active(this.pixelPerfect);
    }

    private void openResizeOverlay()
    {
        Pixels pixels = this.main.getPixels();
        int w = pixels == null ? 64 : pixels.width;
        int h = pixels == null ? 64 : pixels.height;

        UIResizeTextureOverlayPanel panel = new UIResizeTextureOverlayPanel(w, h, this::resizeTexture);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    public void resizeTexture(int width, int height, boolean rescale, boolean center)
    {
        if (width <= 0 || height <= 0)
        {
            return;
        }

        this.storeActiveLayerPixels();

        for (TextureLayer layer : this.layers)
        {
            if (layer.pixels == null)
            {
                layer.pixels = Pixels.fromSize(width, height);
                continue;
            }

            Pixels old = layer.pixels;
            Pixels resized = Pixels.fromSize(width, height);

            if (rescale)
            {
                /* Rescale using Nearest Neighbor for crisp pixel art */
                for (int x = 0; x < width; x++)
                {
                    int srcX = Math.min(old.width - 1, (int) Math.floor((float) x / width * old.width));

                    for (int y = 0; y < height; y++)
                    {
                        int srcY = Math.min(old.height - 1, (int) Math.floor((float) y / height * old.height));
                        Color color = old.getColor(srcX, srcY);

                        if (color != null)
                        {
                            resized.setColor(x, y, color);
                        }
                    }
                }
            }
            else
            {
                /* Canvas resize with anchor */
                int ox = center ? (width - old.width) / 2 : 0;
                int oy = center ? (height - old.height) / 2 : 0;

                for (int x = 0; x < old.width; x++)
                {
                    int dx = ox + x;

                    if (dx < 0 || dx >= width)
                    {
                        continue;
                    }

                    for (int y = 0; y < old.height; y++)
                    {
                        int dy = oy + y;

                        if (dy < 0 || dy >= height)
                        {
                            continue;
                        }

                        Color color = old.getColor(x, y);

                        if (color != null)
                        {
                            resized.setColor(dx, dy, color);
                        }
                    }
                }
            }

            old.delete();
            layer.pixels = resized;
            layer.undoManager = null;
        }

        if (this.layersCompositePixels != null)
        {
            this.layersCompositePixels.delete();
            this.layersCompositePixels = null;
        }

        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
        this.saveCurrentTextureLayers();
        this.refreshModelPreview();
    }

    public void selectAll()
    {
        this.main.selectAll();

        if (this.reference != null)
        {
            this.reference.selectAll();
        }
    }

    public void copySelection()
    {
        this.main.copySelection();
    }

    public void cutSelection()
    {
        this.main.cutSelection();
    }

    public void pasteSelection()
    {
        this.main.pasteSelection();
    }

    public void clearSelection()
    {
        this.main.clearSelection();

        if (this.reference != null)
        {
            this.reference.clearSelection();
        }
    }

    public void flipHorizontal()
    {
        this.main.flipHorizontal();

        if (this.reference != null)
        {
            this.reference.flipHorizontal();
        }
    }

    public void flipVertical()
    {
        this.main.flipVertical();

        if (this.reference != null)
        {
            this.reference.flipVertical();
        }
    }

    public void rotate90(boolean clockwise)
    {
        this.main.rotate90(clockwise);

        if (this.reference != null)
        {
            this.reference.rotate90(clockwise);
        }
    }

    public void rotate180()
    {
        this.main.rotate180();

        if (this.reference != null)
        {
            this.reference.rotate180();
        }
    }

    public void invertColors()
    {
        this.main.invertColors();

        if (this.reference != null)
        {
            this.reference.invertColors();
        }
    }

    public void grayscale()
    {
        this.main.grayscale();

        if (this.reference != null)
        {
            this.reference.grayscale();
        }
    }

    public void openAdjustColorsOverlay()
    {
        UIAdjustColorsOverlayPanel panel = new UIAdjustColorsOverlayPanel((brightness, contrast) ->
        {
            this.main.adjustBrightnessContrast(brightness, contrast);

            if (this.reference != null)
            {
                this.reference.adjustBrightnessContrast(brightness, contrast);
            }
        });

        UIOverlay.addOverlay(this.getContext(), panel, 260, 90);
    }

    private void refreshPaletteSwatches()
    {
        if (this.paletteSwatchesContainer == null)
        {
            return;
        }

        this.paletteSwatchesContainer.removeAll();

        UIElement paletteRowOne = new UIElement();
        UIElement paletteRowTwo = new UIElement();
        UIElement paletteRowThree = new UIElement();
        paletteRowOne.relative(this.paletteSwatchesContainer).xy(0, 2).w(1F).h(26).row(4);
        paletteRowTwo.relative(this.paletteSwatchesContainer).xy(0, 30).w(1F).h(26).row(4);
        paletteRowThree.relative(this.paletteSwatchesContainer).xy(0, 58).w(1F).h(26).row(4);

        for (int i = 0; i < this.paletteColors.length; i++)
        {
            final int color = this.paletteColors[i];
            UIButton swatch = new UIButton(IKey.EMPTY, (b) ->
            {
                if (this.editingPrimary)
                {
                    this.primary.setColor(color);
                }
                else
                {
                    this.secondary.setColor(color);
                }

                this.fixedColorPicker.setColor(this.getActiveColor());
                this.updateColorSlots();
            });

            swatch.color(color).background(true).h(24).tooltip(IKey.constant(String.format("#%06X", color)), Direction.TOP);

            if (i < 6)
            {
                paletteRowOne.add(swatch);
            }
            else if (i < 12)
            {
                paletteRowTwo.add(swatch);
            }
            else if (i < 18)
            {
                paletteRowThree.add(swatch);
            }
        }

        this.paletteSwatchesContainer.add(paletteRowOne, paletteRowTwo, paletteRowThree);
        this.paletteSwatchesContainer.resize();

        if (this.paletteTabContent != null)
        {
            this.paletteTabContent.resize();
        }
    }

    private void extractPaletteFromTexture()
    {
        Pixels pixels = this.getComposedSavePixels();

        if (pixels == null || pixels.width <= 0 || pixels.height <= 0)
        {
            return;
        }

        Map<Integer, Integer> counts = new HashMap<>();

        for (int x = 0; x < pixels.width; x++)
        {
            for (int y = 0; y < pixels.height; y++)
            {
                Color c = pixels.getColor(x, y);

                if (c != null && c.a > 0.1F)
                {
                    int rgb = c.getRGBColor() & 0xFFFFFF;
                    counts.put(rgb, counts.getOrDefault(rgb, 0) + 1);
                }
            }
        }

        if (counts.isEmpty())
        {
            return;
        }

        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int max = Math.min(18, sorted.size());

        for (int i = 0; i < max; i++)
        {
            this.paletteColors[i] = sorted.get(i).getKey();
        }

        this.refreshPaletteSwatches();
    }

    public void applyPalettePreset(int[] colors)
    {
        if (colors == null || colors.length == 0)
        {
            return;
        }

        for (int i = 0; i < Math.min(colors.length, this.paletteColors.length); i++)
        {
            this.paletteColors[i] = colors[i];
        }

        this.refreshPaletteSwatches();
    }

    public void exportPaletteToClipboard()
    {
        StringBuilder sb = new StringBuilder();

        for (int color : this.paletteColors)
        {
            sb.append(String.format("#%06X", (color & 0xffffff))).append("\n");
        }

        Window.setClipboard(sb.toString().trim());
    }

    public void importPaletteFromClipboard()
    {
        String text = Window.getClipboard();

        if (text == null || text.trim().isEmpty())
        {
            return;
        }

        List<Integer> parsed = new ArrayList<>();

        for (String line : text.split("[\r\n,;]+"))
        {
            line = line.trim();

            if (line.startsWith("#"))
            {
                line = line.substring(1);
            }

            if (line.length() == 6)
            {
                try
                {
                    parsed.add(Integer.parseInt(line, 16));
                }
                catch (Exception ignored)
                {}
            }
        }

        if (!parsed.isEmpty())
        {
            for (int i = 0; i < Math.min(parsed.size(), this.paletteColors.length); i++)
            {
                this.paletteColors[i] = parsed.get(i);
            }

            this.refreshPaletteSwatches();
        }
    }

    public void fillTexture(Link current)
    {
        this.saveCurrentTextureLayers();
        this.main.fillTexture(current);
        this.main.setEditing(true);
        this.loadTextureLayers(current);
        this.loadSelectedLayerPixels();
        this.refreshLayerRows();
        this.saveCurrentTextureLayers();
        this.refreshModelPreview();
    }

    private void refreshModelPreview()
    {
        if (this.formPreviewSupplier == null)
        {
            this.modelPreview.form = null;

            return;
        }

        Form source = this.formPreviewSupplier.get();
        this.modelPreview.form = source == null ? null : FormUtils.copy(source);
    }

    private void updateEditorsLayout()
    {
        boolean sidePanelVisible = true;
        this.sidePanel.setVisible(sidePanelVisible);
        this.sidePanelResizer.setVisible(sidePanelVisible);

        List<UITextureEditor> allCanvases = new ArrayList<>();
        allCanvases.add(this.main);

        for (ReferenceImage ref : this.referenceImages)
        {
            if (ref.visible && ref.editor != null)
            {
                allCanvases.add(ref.editor);
                ref.editor.setVisible(true);
            }
            else if (ref.editor != null)
            {
                ref.editor.setVisible(false);
            }
        }

        if (this.reference != null)
        {
            this.reference.setVisible(false);
        }

        int N = allCanvases.size();
        int rows = (N <= 2) ? 1 : ((N <= 6) ? 2 : (int) Math.ceil(Math.sqrt(N)));
        int cols = (int) Math.ceil((double) N / rows);
        float itemHFrac = 1F / rows;
        float defaultItemWFrac = 1F / cols;

        if (this.modelPreviewArea.isVisible())
        {
            this.modelPreviewArea.relative(this).x(0).y(6).w(MODEL_PREVIEW_LEFT_WIDTH).h(1F, -12);
            this.sidePanel.relative(this).x(1F, -this.sidePanelWidth).y(0).w(this.sidePanelWidth).h(1F);
            this.sidePanelResizer.relative(this).x(1F, -this.sidePanelWidth - 3).y(0).w(6).h(1F);

            int startX = MODEL_PREVIEW_LEFT_WIDTH + MODEL_PREVIEW_GAP;
            int totalOffset = -(this.sidePanelWidth + MODEL_PREVIEW_LEFT_WIDTH + MODEL_PREVIEW_GAP + 4);

            int currentIndex = 0;

            for (int r = 0; r < rows && currentIndex < N; r++)
            {
                int remaining = N - currentIndex;
                int countInThisRow = (r == rows - 1) ? remaining : Math.min(cols, remaining);
                float startXFrac = (1F - countInThisRow * defaultItemWFrac) / 2F;

                for (int c = 0; c < countInThisRow; c++)
                {
                    UITextureEditor canvas = allCanvases.get(currentIndex++);
                    float xFrac = startXFrac + c * defaultItemWFrac;
                    float yFrac = r * itemHFrac;

                    canvas.relative(this)
                        .x(xFrac, startX + (int) (xFrac * totalOffset))
                        .y(yFrac, 0)
                        .w(defaultItemWFrac, (int) (defaultItemWFrac * totalOffset))
                        .h(itemHFrac, 0);
                }
            }

            return;
        }

        int totalOffset = -(this.sidePanelWidth + 4);

        this.sidePanel.relative(this).x(1F, -this.sidePanelWidth).y(0).w(this.sidePanelWidth).h(1F);
        this.sidePanelResizer.relative(this).x(1F, -this.sidePanelWidth - 3).y(0).w(6).h(1F);

        int currentIndex = 0;

        for (int r = 0; r < rows && currentIndex < N; r++)
        {
            int remaining = N - currentIndex;
            int countInThisRow = (r == rows - 1) ? remaining : Math.min(cols, remaining);
            float startXFrac = (1F - countInThisRow * defaultItemWFrac) / 2F;

            for (int c = 0; c < countInThisRow; c++)
            {
                UITextureEditor canvas = allCanvases.get(currentIndex++);
                float xFrac = startXFrac + c * defaultItemWFrac;
                float yFrac = r * itemHFrac;

                canvas.relative(this)
                    .x(xFrac, (int) (xFrac * totalOffset))
                    .y(yFrac, 0)
                    .w(defaultItemWFrac, (int) (defaultItemWFrac * totalOffset))
                    .h(itemHFrac, 0);
            }
        }
    }

    public UIElement getHeaderToolbar()
    {
        return this.headerToolbar;
    }

    private boolean updatePreviewTexture(TextureManager manager, Link textureLink, Pixels pixels)
    {
        if (manager == null)
        {
            return false;
        }

        Texture texture = manager.getTexture(textureLink, GL11.GL_NEAREST, true);

        if (texture == null || texture == manager.getError())
        {
            return false;
        }

        pixels.rewindBuffer();
        texture.bind();
        texture.updateTexture(pixels);
        this.touchedPreviewTextures.add(textureLink);

        return true;
    }

    private void updateLiveModelPreviewTexture(UIContext context)
    {
        if (!this.modelPreviewArea.isVisible() || this.formPreviewSupplier == null)
        {
            return;
        }

        Link textureLink = this.main.getTexture();
        this.storeActiveLayerPixels();
        Pixels pixels = this.composeVisibleLayers();

        if (textureLink == null || pixels == null)
        {
            return;
        }

        if (this.modelPreview.form instanceof ModelForm modelForm)
        {
            modelForm.texture.set(textureLink);
        }

        boolean updated = this.updatePreviewTexture(context == null ? null : context.render.getTextures(), textureLink, pixels);

        if (context == null || context.render.getTextures() != BBSModClient.getTextures())
        {
            updated = this.updatePreviewTexture(BBSModClient.getTextures(), textureLink, pixels) || updated;
        }

        if (!updated)
        {
            this.updatePreviewTexture(BBSModClient.getTextures(), textureLink, pixels);
        }
    }

    public void discardPreviewTextureChanges()
    {
        for (Link link : this.touchedPreviewTextures)
        {
            BBSModClient.getTextures().delete(link);
        }

        this.touchedPreviewTextures.clear();
    }

    @Override
    public void render(UIContext context)
    {
        this.updateEditorsLayout();
        this.updateLiveModelPreviewTexture(context);

        if (this.modelPreviewArea.isVisible())
        {
            this.modelPreviewArea.area.render(context.batcher, Colors.A25);
            context.batcher.outline(this.modelPreviewArea.area.x, this.modelPreviewArea.area.y, this.modelPreviewArea.area.ex(), this.modelPreviewArea.area.ey(), Colors.A50);
        }

        super.render(context);

        UITextureEditor editor = this.getHoverEditor(context);

        if (editor != null && editor.getPixels() != null)
        {
            Vector2i pixel = editor.getHoverPixel(context.mouseX, context.mouseY);
            Color color = editor.getPixels().getColor(pixel.x, pixel.y);

            int r = 0;
            int g = 0;
            int b = 0;
            int a = 0;

            if (color != null)
            {
                r = (int) Math.floor(color.r * 255);
                g = (int) Math.floor(color.g * 255);
                b = (int) Math.floor(color.b * 255);
                a = (int) Math.floor(color.a * 255);
            }

            String[] information = {
                editor.getPixels().width + "x" + editor.getPixels().height + " (" + pixel.x + ", " + pixel.y + ")",
                "\u00A7cR\u00A7aG\u00A79B\u00A7rA (" + r + ", " + g + ", " + b + ", " + a + ")",
                "Brush " + editor.getBrushSize() + "x" + editor.getBrushSize() + " " + (editor.getBrushShape() == UIPixelsEditor.BrushShape.CIRCLE ? "Circle" : "Square"),
            };

            int x = this.area.x + 10;
            int y = this.area.ey() - context.batcher.getFont().getHeight() - 10 - (information.length - 1)* 14;

            for (String line : information)
            {
                context.batcher.textCard(line, x, y);

                y += 14;
            }
        }
    }
}
