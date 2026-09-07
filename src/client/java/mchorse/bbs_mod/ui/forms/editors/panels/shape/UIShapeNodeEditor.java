package mchorse.bbs_mod.ui.forms.editors.panels.shape;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.shape.INodeGraph;
import mchorse.bbs_mod.forms.forms.shape.ShapeConnection;
import mchorse.bbs_mod.forms.forms.shape.ShapeFormGraph;
import mchorse.bbs_mod.forms.forms.shape.ValueShapeGraph;
import mchorse.bbs_mod.forms.forms.shape.nodes.BumpNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.ClampNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.ColorNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.CombineColorNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.CommentNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.CoordinateNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.FlowNoiseNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.InvertNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.IrisAttributeNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.IrisShaderNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.MathNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.MixColorNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.NoiseNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.OutputNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.RemapNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.ShapeNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.SmoothstepNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.SplitColorNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.TextureNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.TimeNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.TriggerNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.ValueNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.VectorMathNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.VoronoiNode;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIColorOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UITextareaOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.presets.PresetManager;

import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import org.joml.Matrix4f;
import org.joml.Vector2f;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UIShapeNodeEditor extends UIElement
{
    /* ---- Socket type palette ---- */
    protected static final int SOCKET_FLOAT = Colors.WHITE;
    protected static final int SOCKET_COLOR = 0xFFFFAA33;
    protected static final int SOCKET_VECTOR = 0xFF5599FF;

    /* ---- Node header palette ---- */
    protected static final int HEADER_DEFAULT  = Colors.A100 | 0x444444;
    protected static final int HEADER_OUTPUT   = Colors.A100 | 0xFF5555;
    protected static final int HEADER_MATH     = Colors.A100 | 0x5555FF;
    protected static final int HEADER_INPUT    = Colors.A100 | 0x55BB55;
    protected static final int HEADER_TEXTURE  = Colors.A100 | 0xBB8833;
    protected static final int HEADER_TRIGGER  = Colors.A100 | 0xFF55FF;
    protected static final int HEADER_NOISE    = Colors.A100 | 0xFFAA00;
    protected static final int HEADER_BUMP     = Colors.A100 | 0xFF55AA;
    protected static final int HEADER_COLOR    = Colors.A100 | 0xFF7733;

    private INodeGraph graph;
    private ValueShapeGraph value;

    private float scale = 1F;
    private float translateX = 0F;
    private float translateY = 0F;

    private int lastMouseX;
    private int lastMouseY;
    private boolean dragging;

    private ShapeNode draggingNode;
    private float draggingNodeX;
    private float draggingNodeY;
    private int draggingMouseX;
    private int draggingMouseY;

    private int draggingConnectionNode = -1;
    private int draggingConnectionIndex = -1;
    private boolean draggingConnectionInput = false;

    private final Set<ShapeNode> selection = new HashSet<>();
    private final Set<ShapeConnection> selectedConnections = new HashSet<>();
    private final Map<ShapeNode, Vector2f> initialPositions = new HashMap<>();
    private boolean selecting;
    private int selectingX;
    private int selectingY;
    private static MapType clipboard;

    private UIElement toolbar;
    private UIIcon presets;
    private UICopyPasteController copyPaste;

    public UIShapeNodeEditor()
    {
        super();

        this.copyPaste = new UICopyPasteController(PresetManager.SHAPE_GRAPHS, "ShapeGraph");
        this.copyPaste.supplier(this::createData);
        this.copyPaste.consumer(this::pasteData);

        this.toolbar = UI.row(0, 0);
        this.toolbar.relative(this).x(10).y(10).w(100).h(20);

        this.presets = new UIIcon(Icons.SAVED, (b) -> this.openPresets());
        this.toolbar.add(this.presets);
        this.toolbar.add(new UIIcon(Icons.REFRESH, (b) -> this.resetView()));

        this.add(this.toolbar);
    }

    private void openPresets()
    {}

    private void resetView()
    {
        this.scale = 1F;
        this.translateX = 0F;
        this.translateY = 0F;
    }

    /* ======================================================================
     * Graph / value wiring
     * ====================================================================== */

    public void setGraph(INodeGraph graph)
    {
        this.graph = graph;
    }

    /** Convenience overload so callers that already have a ShapeFormGraph compile. */
    public void setGraph(ShapeFormGraph graph)
    {
        this.graph = graph;
    }

    public void setValue(ValueShapeGraph value)
    {
        this.value = value;
    }

    private void editGraph(Runnable callback)
    {
        if (callback == null)
        {
            return;
        }

        if (this.value != null)
        {
            BaseValue.edit(this.value, (__) -> callback.run());
        }
        else
        {
            callback.run();
        }
    }

    /* ======================================================================
     * Portability — override these in subclasses for custom node types
     * ====================================================================== */

    /**
     * Human-readable label shown in the node header. Override to customise for
     * node types that are not part of the default shape graph.
     */
    protected String getNodeTitle(ShapeNode node)
    {
        if (node instanceof MathNode)
        {
            MathNode math = (MathNode) node;
            String[] ops = {"+", "-", "×", "÷", "%", "min", "max", "^"};
            int op = math.operation;

            if (op == 8) return "Math (" + math.expression + ")";
            if (op >= 0 && op < ops.length) return "Math (" + ops[op] + ")";

            return "Math";
        }

        if (node instanceof VectorMathNode)
        {
            String[] ops = {"Add", "Sub", "Mul", "Div", "Cross", "Project", "Reflect",
                "Dot", "Distance", "Length", "Scale", "Normalize", "Abs",
                "Min", "Max", "Floor", "Ceil", "Fract", "Mod", "Snap", "Sin", "Cos", "Tan"};
            int op = ((VectorMathNode) node).operation;
            String label = (op >= 0 && op < ops.length) ? ops[op] : "?";

            return "Vector (" + label + ")";
        }

        if (node instanceof ValueNode)    return "Value: " + ((ValueNode) node).value;
        if (node instanceof TimeNode)     return "Time";
        if (node instanceof CoordinateNode) return "Coordinate";
        if (node instanceof ColorNode)    return "Color";
        if (node instanceof TextureNode)
        {
            Link t = ((TextureNode) node).texture;
            return "Texture" + (t == null ? "" : ": " + t.path);
        }

        if (node instanceof InvertNode)
        {
            return ((InvertNode) node).mode == 1 ? "Invert (color)" : "Invert";
        }

        if (node instanceof RemapNode)     return "Remap";
        if (node instanceof ClampNode)     return "Clamp";
        if (node instanceof SmoothstepNode) return "Smoothstep";
        if (node instanceof MixColorNode)  return "Mix Color";
        if (node instanceof SplitColorNode) return "Split Color";
        if (node instanceof CombineColorNode) return "Combine Color";

        if (node instanceof NoiseNode)     return "Noise (seed " + ((NoiseNode) node).seed + ")";
        if (node instanceof VoronoiNode)   return "Voronoi (seed " + ((VoronoiNode) node).seed + ")";
        if (node instanceof FlowNoiseNode) return "Flow Noise (seed " + ((FlowNoiseNode) node).seed + ")";

        if (node instanceof TriggerNode)
        {
            String[] modes = {"Greater", "Less", "Equal", "Not Equal", "Pulse"};
            int m = ((TriggerNode) node).mode;
            return "Trigger (" + (m >= 0 && m < modes.length ? modes[m] : "?") + ")";
        }

        if (node instanceof BumpNode)       return "Bump";
        if (node instanceof CommentNode)    return ((CommentNode) node).title;
        if (node instanceof OutputNode)     return "Output";
        if (node instanceof IrisShaderNode)
        {
            String u = ((IrisShaderNode) node).uniform;
            return "Iris Shader" + (u.isEmpty() ? "" : ": " + u);
        }
        if (node instanceof IrisAttributeNode) return "Iris: " + ((IrisAttributeNode) node).attribute.name();

        return node.getType();
    }

    /**
     * Header background colour. Override to theme custom node types.
     */
    protected int getNodeHeaderColor(ShapeNode node)
    {
        if (node instanceof OutputNode)   return HEADER_OUTPUT;
        if (node instanceof MathNode || node instanceof VectorMathNode
            || node instanceof RemapNode || node instanceof ClampNode
            || node instanceof SmoothstepNode || node instanceof InvertNode) return HEADER_MATH;
        if (node instanceof ValueNode || node instanceof TimeNode
            || node instanceof CoordinateNode) return HEADER_INPUT;
        if (node instanceof ColorNode || node instanceof MixColorNode
            || node instanceof SplitColorNode || node instanceof CombineColorNode) return HEADER_COLOR;
        if (node instanceof TextureNode)  return HEADER_TEXTURE;
        if (node instanceof TriggerNode)  return HEADER_TRIGGER;
        if (node instanceof NoiseNode || node instanceof VoronoiNode
            || node instanceof FlowNoiseNode) return HEADER_NOISE;
        if (node instanceof BumpNode)     return HEADER_BUMP;

        return HEADER_DEFAULT;
    }

    /**
     * Node body width in unscaled graph pixels. Override for nodes that need
     * more space (e.g. those with many long slot labels).
     */
    protected int getNodeWidth(ShapeNode node)
    {
        if (node instanceof VectorMathNode) return 140;
        if (node instanceof RemapNode)      return 140;

        return 120;
    }

    /**
     * Height of the extra preview area drawn between the node header and the first
     * socket row, in unscaled graph pixels. Override to add more preview space for
     * custom node types.
     */
    protected int getNodePreviewHeight(ShapeNode node)
    {
        if (node instanceof TextureNode) return 52;
        if (node instanceof MixColorNode || node instanceof SplitColorNode
            || node instanceof CombineColorNode) return 14;

        return 0;
    }

    /**
     * Called after type-specific items are added but before Remove/Copy/Paste.
     * Override to inject extra menu items for custom node types.
     */
    protected void appendNodeContextMenu(UIContext context, ShapeNode node, ContextMenuManager menu)
    {}

    /**
     * Override to add custom node categories / types to the Add-Node submenu.
     * The default implementation adds all built-in shape nodes organised by category.
     */
    protected void populateAddMenu(UIContext context, ContextMenuManager menu)
    {
        /* Input — green */
        ContextMenuManager inputSub = new ContextMenuManager();
        inputSub.action(Icons.MAXIMIZE, UIKeys.RAW_VALUE,      Colors.POSITIVE, () -> this.addNode("value"));
        inputSub.action(Icons.MATERIAL, UIKeys.RAW_COLOR,      Colors.POSITIVE, () -> this.addNode("color"));
        inputSub.action(Icons.TIME,     UIKeys.RAW_TIME,       Colors.POSITIVE, () -> this.addNode("time"));
        inputSub.action(Icons.ALL_DIRECTIONS, UIKeys.RAW_COORDINATE, Colors.POSITIVE, () -> this.addNode("coordinate"));
        inputSub.action(Icons.IMAGE,    UIKeys.RAW_TEXTURE,    Colors.POSITIVE, () -> this.addNode("texture"));
        menu.action(Icons.DOWNLOAD, UIKeys.RAW_INPUT, Colors.POSITIVE, () -> context.replaceContextMenu(inputSub.create()));

        /* Math — blue */
        ContextMenuManager mathSub = new ContextMenuManager();
        mathSub.action(Icons.GEAR,   UIKeys.RAW_MATH,       Colors.ACTIVE, () -> this.addNode("math"));
        mathSub.action(Icons.ALL_DIRECTIONS, UIKeys.RAW_VECTOR_MATH, Colors.ACTIVE, () -> this.addNode("vector_math"));
        mathSub.action(Icons.GEAR,   UIKeys.RAW_REMAP,      Colors.ACTIVE, () -> this.addNode("remap"));
        mathSub.action(Icons.GEAR,   UIKeys.RAW_CLAMP,      Colors.ACTIVE, () -> this.addNode("clamp"));
        mathSub.action(Icons.GEAR,   UIKeys.RAW_SMOOTHSTEP, Colors.ACTIVE, () -> this.addNode("smoothstep"));
        mathSub.action(Icons.REFRESH, UIKeys.RAW_INVERT,    Colors.ACTIVE, () -> this.addNode("invert"));
        menu.action(Icons.GEAR, UIKeys.RAW_MATH, Colors.ACTIVE, () -> context.replaceContextMenu(mathSub.create()));

        /* Color — orange */
        ContextMenuManager colorSub = new ContextMenuManager();
        colorSub.action(Icons.REFRESH, UIKeys.RAW_MIX_COLOR,     Colors.ORANGE, () -> this.addNode("mix_color"));
        colorSub.action(Icons.FILTER,  UIKeys.RAW_SPLIT_COLOR,   Colors.ORANGE, () -> this.addNode("split_color"));
        colorSub.action(Icons.FILTER,  UIKeys.RAW_COMBINE_COLOR, Colors.ORANGE, () -> this.addNode("combine_color"));
        menu.action(Icons.MATERIAL, UIKeys.RAW_COLOR, Colors.ORANGE, () -> context.replaceContextMenu(colorSub.create()));

        /* Noise — yellow */
        ContextMenuManager noiseSub = new ContextMenuManager();
        noiseSub.action(Icons.SOUND, UIKeys.RAW_PERLIN_NOISE, Colors.INACTIVE, () -> this.addNode("noise"));
        noiseSub.action(Icons.SOUND, UIKeys.RAW_VORONOI,      Colors.INACTIVE, () -> this.addNode("voronoi"));
        noiseSub.action(Icons.SOUND, UIKeys.RAW_FLOW_NOISE,   Colors.INACTIVE, () -> this.addNode("flow_noise"));
        menu.action(Icons.SOUND, UIKeys.RAW_NOISE, Colors.INACTIVE, () -> context.replaceContextMenu(noiseSub.create()));

        /* Utility — no color */
        ContextMenuManager utilitySub = new ContextMenuManager();
        utilitySub.action(Icons.GEAR,   UIKeys.RAW_TRIGGER, () -> this.addNode("trigger"));
        utilitySub.action(Icons.UPLOAD, UIKeys.RAW_BUMP,    () -> this.addNode("bump"));
        utilitySub.action(Icons.EDIT,   UIKeys.RAW_COMMENT, () -> this.addNode("comment"));
        menu.action(Icons.MORE, UIKeys.RAW_UTILITY, () -> context.replaceContextMenu(utilitySub.create()));

        /* Output / integration — red */
        ContextMenuManager outputSub = new ContextMenuManager();
        outputSub.action(Icons.DOWNLOAD, UIKeys.RAW_OUTPUT,         Colors.NEGATIVE, () -> this.addNode("output"));
        outputSub.action(Icons.GLOBE,    UIKeys.RAW_IRIS_SHADER,    Colors.NEGATIVE, () -> this.addNode("iris_shader"));
        outputSub.action(Icons.VISIBLE,  UIKeys.RAW_IRIS_ATTRIBUTE, Colors.NEGATIVE, () -> this.addNode("iris_attribute"));
        menu.action(Icons.UPLOAD, UIKeys.RAW_OUTPUT, Colors.NEGATIVE, () -> context.replaceContextMenu(outputSub.create()));
    }

    /* ======================================================================
     * Copy / paste helpers
     * ====================================================================== */

    private MapType createData()
    {
        if (this.selection.isEmpty() && this.selectedConnections.isEmpty()) return null;

        MapType data = new MapType();
        ListType nodesList = new ListType();
        ListType connectionsList = new ListType();

        for (ShapeNode node : this.selection)
        {
            MapType nodeData = new MapType();
            node.toData(nodeData);
            nodesList.add(nodeData);
        }

        Set<ShapeConnection> toAdd = new HashSet<>(this.selectedConnections);

        if (this.graph != null)
        {
            for (ShapeConnection c : this.graph.getConnections())
            {
                boolean inputSelected = false;
                boolean outputSelected = false;

                for (ShapeNode node : this.selection)
                {
                    if (node.id == c.inputNodeId) inputSelected = true;
                    if (node.id == c.outputNodeId) outputSelected = true;
                }

                if (inputSelected && outputSelected) toAdd.add(c);
            }
        }

        for (ShapeConnection c : toAdd)
        {
            MapType cData = new MapType();
            c.toData(cData);
            connectionsList.add(cData);
        }

        data.put("nodes", nodesList);
        data.put("connections", connectionsList);

        return data;
    }

    private void pasteData(MapType data, int mouseX, int mouseY)
    {
        if (this.graph == null) return;

        ListType nodesList = data.getList("nodes");
        ListType connectionsList = data.getList("connections");

        Map<Integer, Integer> idMap = new HashMap<>();

        this.selection.clear();
        this.selectedConnections.clear();

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;

        for (BaseType element : nodesList)
        {
            if (element instanceof MapType)
            {
                float nx = ((MapType) element).getFloat("x");
                float ny = ((MapType) element).getFloat("y");

                if (nx < minX) minX = nx;
                if (ny < minY) minY = ny;
            }
        }

        float graphX = (-this.translateX + mouseX - this.area.x) / this.scale;
        float graphY = (-this.translateY + mouseY - this.area.y) / this.scale;

        for (BaseType element : nodesList)
        {
            if (element instanceof MapType)
            {
                MapType nodeData = (MapType) element;
                String type = nodeData.getString("type");
                ShapeNode node = this.graph.createNode(type);

                if (node != null)
                {
                    node.fromData(nodeData);

                    int oldId = node.id;
                    node.id = 0;
                    this.graph.addNode(node);
                    idMap.put(oldId, node.id);

                    node.x = graphX + (node.x - minX);
                    node.y = graphY + (node.y - minY);

                    this.selection.add(node);
                }
            }
        }

        for (BaseType element : connectionsList)
        {
            if (element instanceof MapType)
            {
                ShapeConnection c = new ShapeConnection();
                c.fromData((MapType) element);

                if (idMap.containsKey(c.inputNodeId) && idMap.containsKey(c.outputNodeId))
                {
                    this.graph.connect(idMap.get(c.outputNodeId), c.outputIndex,
                        idMap.get(c.inputNodeId), c.inputIndex);
                }
            }
        }
    }

    private void copyNodes()
    {
        MapType data = this.createData();

        if (data != null) clipboard = data;
    }

    private void pasteNodes(int mx, int my)
    {
        if (clipboard != null) this.pasteData(clipboard, mx, my);
    }

    public boolean hasToolbarNodeSelection()
    {
        return !this.selection.isEmpty();
    }

    public boolean hasToolbarClipboard()
    {
        return clipboard != null;
    }

    public void toolbarRemoveSelectedNodes()
    {
        if (!this.selection.isEmpty())
        {
            this.removeSelection();
        }
    }

    public void toolbarCopyNodes()
    {
        if (!this.selection.isEmpty())
        {
            this.copyNodes();
        }
    }

    public void toolbarPasteNodes()
    {
        if (clipboard != null)
        {
            this.pasteNodes(this.lastMouseX, this.lastMouseY);
        }
    }

    /* ======================================================================
     * Node management helpers
     * ====================================================================== */

    protected void addNode(String type)
    {
        if (this.graph == null) return;

        ShapeNode node = this.graph.createNode(type);

        if (node != null)
        {
            node.x = (this.lastMouseX - this.area.x - this.translateX) / this.scale;
            node.y = (this.lastMouseY - this.area.y - this.translateY) / this.scale;

            this.editGraph(() -> this.graph.addNode(node));
        }
    }

    private void removeSelection()
    {
        if (this.graph == null) return;

        this.editGraph(() ->
        {
            for (ShapeNode node : this.selection)
            {
                this.graph.removeNode(node);
            }

            this.graph.getConnections().removeAll(this.selectedConnections);
        });

        this.selection.clear();
        this.selectedConnections.clear();
    }

    private void removeNode(ShapeNode node)
    {
        this.editGraph(() -> this.graph.removeNode(node));
        this.selection.remove(node);
    }

    /* ======================================================================
     * Context menus
     * ====================================================================== */

    private void openContextMenu(UIContext context)
    {
        ContextMenuManager menu = new ContextMenuManager();

        if (!this.selection.isEmpty() || !this.selectedConnections.isEmpty())
        {
            menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, Colors.NEGATIVE, () -> this.removeSelection());
        }

        if (!this.selection.isEmpty())
        {
            menu.action(Icons.COPY, UIKeys.RAW_COPY, Colors.POSITIVE, () -> this.copyNodes());
        }

        if (clipboard != null)
        {
            menu.action(Icons.PASTE, UIKeys.RAW_PASTE, Colors.INACTIVE, () -> this.pasteNodes(this.lastMouseX, this.lastMouseY));
        }

        ContextMenuManager addSub = new ContextMenuManager();
        this.populateAddMenu(context, addSub);
        menu.action(Icons.ADD, UIKeys.RAW_ADD_NODE, Colors.ACTIVE, () -> context.replaceContextMenu(addSub.create()));

        context.replaceContextMenu(menu.create());
    }

    private void openNodeContextMenu(UIContext context, ShapeNode node)
    {
        ContextMenuManager menu = new ContextMenuManager();

        /* Type-specific items */
        if (node instanceof MathNode)
        {
            MathNode math = (MathNode) node;
            ContextMenuManager op = new ContextMenuManager();

            op.action(Icons.ADD,    UIKeys.RAW_ADD,    () -> math.operation = 0);
            op.action(Icons.REMOVE, UIKeys.RAW_SUB,    () -> math.operation = 1);
            op.action(Icons.CLOSE,  UIKeys.RAW_MUL,    () -> math.operation = 2);
            op.action(Icons.MAXIMIZE, UIKeys.RAW_DIV,  () -> math.operation = 3);
            op.action(Icons.REFRESH, UIKeys.RAW_MOD,   () -> math.operation = 4);
            op.action(Icons.DOWNLOAD, UIKeys.RAW_MIN,  () -> math.operation = 5);
            op.action(Icons.UPLOAD,  UIKeys.RAW_MAX,   () -> math.operation = 6);
            op.action(Icons.MORE,    UIKeys.RAW_POW,   () -> math.operation = 7);
            op.action(Icons.EDIT,    UIKeys.RAW_CUSTOM, () ->
            {
                math.operation = 8;
                UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                    UIKeys.RAW_EDIT_EXPRESSION, UIKeys.RAW_ENTER_MOLANG_EXPRESSION,
                    (s) -> math.setExpression(s));
                panel.text.setText(math.expression);
                UIOverlay.addOverlay(context, panel);
            });

            menu.action(Icons.GEAR, UIKeys.RAW_OPERATION, Colors.INACTIVE, () -> context.replaceContextMenu(op.create()));

            if (math.operation == 8)
            {
                menu.action(Icons.EDIT, UIKeys.RAW_EDIT_EXPRESSION, Colors.ACTIVE, () ->
                {
                    UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                        UIKeys.RAW_EDIT_EXPRESSION, UIKeys.RAW_ENTER_MOLANG_EXPRESSION,
                        (s) -> math.setExpression(s));
                    panel.text.setText(math.expression);
                    UIOverlay.addOverlay(context, panel);
                });
            }
        }
        else if (node instanceof VectorMathNode)
        {
            VectorMathNode math = (VectorMathNode) node;
            ContextMenuManager op = new ContextMenuManager();

            String[] names = {"Add", "Sub", "Mul", "Div", "Cross", "Project", "Reflect",
                "Dot", "Distance", "Length", "Scale", "Normalize", "Abs", "Min", "Max",
                "Floor", "Ceil", "Fract", "Modulo", "Snap", "Sin", "Cos", "Tan"};

            for (int i = 0; i < names.length; i++)
            {
                final int idx = i;
                op.action(Icons.GEAR, IKey.raw(names[i]), () -> math.operation = idx);
            }

            menu.action(Icons.GEAR, UIKeys.RAW_OPERATION, Colors.INACTIVE, () -> context.replaceContextMenu(op.create()));
        }
        else if (node instanceof ValueNode)
        {
            ValueNode vn = (ValueNode) node;
            menu.action(Icons.EDIT, UIKeys.RAW_EDIT_VALUE, Colors.ACTIVE, () ->
            {
                UINumberOverlayPanel panel = new UINumberOverlayPanel(
                    UIKeys.RAW_EDIT_VALUE, UIKeys.RAW_ENTER_A_NEW_VALUE,
                    (v) -> vn.value = v.floatValue());
                panel.value.setValue((double) vn.value);
                UIOverlay.addOverlay(context, panel);
            });
        }
        else if (node instanceof ColorNode)
        {
            ColorNode cn = (ColorNode) node;
            menu.action(Icons.MATERIAL, UIKeys.RAW_EDIT_COLOR, Colors.ACTIVE, () ->
            {
                UIColorOverlayPanel panel = new UIColorOverlayPanel(UIKeys.RAW_EDIT_COLOR, (c) -> cn.color.set(c));
                panel.picker.editAlpha = true;
                panel.picker.setColor(cn.color.getARGBColor());
                UIOverlay.addOverlay(context, panel, 250, 160);
            });
        }
        else if (node instanceof TextureNode)
        {
            TextureNode tn = (TextureNode) node;
            menu.action(Icons.IMAGE, UIKeys.RAW_PICK_TEXTURE, Colors.ACTIVE, () ->
                UITexturePicker.open(context, tn.texture, (l) -> tn.texture = l));
        }
        else if (node instanceof InvertNode)
        {
            InvertNode inv = (InvertNode) node;
            ContextMenuManager modeMenu = new ContextMenuManager();

            modeMenu.action(Icons.GEAR, UIKeys.RAW_SCALAR_1_VALUE, () -> inv.mode = 0);
            modeMenu.action(Icons.MATERIAL, UIKeys.RAW_COLOR_INVERT_RGB, Colors.INACTIVE, () -> inv.mode = 1);

            menu.action(Icons.GEAR, UIKeys.RAW_MODE, Colors.INACTIVE, () -> context.replaceContextMenu(modeMenu.create()));
        }
        else if (node instanceof NoiseNode)
        {
            NoiseNode nn = (NoiseNode) node;
            menu.action(Icons.EDIT, UIKeys.RAW_EDIT_SEED, Colors.ACTIVE, () ->
            {
                UINumberOverlayPanel panel = new UINumberOverlayPanel(
                    UIKeys.RAW_NOISE_SEED, UIKeys.RAW_ENTER_INTEGER_SEED,
                    (v) -> nn.seed = v.intValue());
                panel.value.setValue(nn.seed);
                UIOverlay.addOverlay(context, panel);
            });
        }
        else if (node instanceof VoronoiNode)
        {
            VoronoiNode vn = (VoronoiNode) node;
            menu.action(Icons.EDIT, UIKeys.RAW_EDIT_SEED, Colors.ACTIVE, () ->
            {
                UINumberOverlayPanel panel = new UINumberOverlayPanel(
                    UIKeys.RAW_VORONOI_SEED, UIKeys.RAW_ENTER_INTEGER_SEED,
                    (v) -> vn.seed = v.intValue());
                panel.value.setValue(vn.seed);
                UIOverlay.addOverlay(context, panel);
            });
        }
        else if (node instanceof FlowNoiseNode)
        {
            FlowNoiseNode fn = (FlowNoiseNode) node;
            menu.action(Icons.EDIT, UIKeys.RAW_EDIT_SEED, Colors.ACTIVE, () ->
            {
                UINumberOverlayPanel panel = new UINumberOverlayPanel(
                    UIKeys.RAW_FLOW_NOISE_SEED, UIKeys.RAW_ENTER_INTEGER_SEED,
                    (v) -> fn.seed = v.intValue());
                panel.value.setValue(fn.seed);
                UIOverlay.addOverlay(context, panel);
            });
        }
        else if (node instanceof TriggerNode)
        {
            TriggerNode tn = (TriggerNode) node;
            ContextMenuManager modeMenu = new ContextMenuManager();

            modeMenu.action(Icons.GEAR, UIKeys.RAW_GREATER,   () -> tn.mode = 0);
            modeMenu.action(Icons.GEAR, UIKeys.RAW_LESS,      () -> tn.mode = 1);
            modeMenu.action(Icons.GEAR, UIKeys.RAW_EQUAL,     () -> tn.mode = 2);
            modeMenu.action(Icons.GEAR, UIKeys.RAW_NOT_EQUAL, () -> tn.mode = 3);
            modeMenu.action(Icons.GEAR, UIKeys.RAW_PULSE,     () -> tn.mode = 4);

            menu.action(Icons.GEAR, UIKeys.RAW_MODE, Colors.INACTIVE, () -> context.replaceContextMenu(modeMenu.create()));
        }
        else if (node instanceof CommentNode)
        {
            CommentNode cn = (CommentNode) node;
            menu.action(Icons.EDIT, UIKeys.RAW_EDIT_TITLE, Colors.ACTIVE, () ->
            {
                UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                    UIKeys.RAW_EDIT_TITLE, UIKeys.RAW_ENTER_TITLE, (s) -> cn.title = s);
                panel.text.setText(cn.title);
                UIOverlay.addOverlay(context, panel);
            });
            menu.action(Icons.EDIT, UIKeys.RAW_EDIT_COMMENT, Colors.ACTIVE, () ->
            {
                UITextareaOverlayPanel panel = new UITextareaOverlayPanel(
                    UIKeys.RAW_EDIT_COMMENT, UIKeys.RAW_ENTER_COMMENT, (s) -> cn.comment = s);
                panel.text.setText(cn.comment);
                UIOverlay.addOverlay(context, panel);
            });
        }
        else if (node instanceof IrisShaderNode)
        {
            IrisShaderNode sn = (IrisShaderNode) node;
            menu.action(Icons.EDIT, UIKeys.RAW_EDIT_UNIFORM, Colors.ACTIVE, () ->
            {
                UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                    UIKeys.RAW_EDIT_UNIFORM, UIKeys.RAW_ENTER_UNIFORM_NAME, (s) -> sn.uniform = s);
                panel.text.setText(sn.uniform);
                UIOverlay.addOverlay(context, panel);
            });
        }
        else if (node instanceof IrisAttributeNode)
        {
            IrisAttributeNode an = (IrisAttributeNode) node;
            ContextMenuManager attrMenu = new ContextMenuManager();

            for (IrisAttributeNode.Attribute attr : IrisAttributeNode.Attribute.values())
            {
                attrMenu.action(Icons.GEAR, IKey.raw(attr.name()), () -> an.attribute = attr);
            }

            menu.action(Icons.GEAR, UIKeys.SHAPES_ATTRIBUTE.format(an.attribute.name()), Colors.INACTIVE,
                () -> context.replaceContextMenu(attrMenu.create()));
        }

        /* Hook for subclasses */
        this.appendNodeContextMenu(context, node, menu);

        /* Always-present actions */
        menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, Colors.NEGATIVE, () -> this.removeNode(node));

        if (!this.selection.isEmpty())
        {
            menu.action(Icons.COPY, UIKeys.RAW_COPY, Colors.POSITIVE, () -> this.copyNodes());
        }

        if (clipboard != null)
        {
            menu.action(Icons.PASTE, UIKeys.RAW_PASTE, Colors.INACTIVE, () -> this.pasteNodes(this.lastMouseX, this.lastMouseY));
        }

        context.replaceContextMenu(menu.create());
    }

    /* ======================================================================
     * Input handling
     * ====================================================================== */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context)) return super.subMouseClicked(context);

        int mx = context.mouseX;
        int my = context.mouseY;

        if (this.graph != null)
        {
            /* Check sockets first (reverse order, topmost node wins) */
            List<ShapeNode> nodes = this.graph.getNodes();

            for (int i = nodes.size() - 1; i >= 0; i--)
            {
                ShapeNode node = nodes.get(i);
                int nx = this.nodeScreenX(node);
                int ny = this.nodeScreenY(node);
                int w = this.nodeScreenW(node);
                float hitR = Math.max(10F * this.scale, 6F);

                List<String> outputs = node.getOutputs();

                for (int j = 0; j < outputs.size(); j++)
                {
                    int sx = nx + w;
                    int sy = this.socketScreenY(node, j);

                    if (Math.abs(mx - sx) < hitR && Math.abs(my - sy) < hitR)
                    {
                        this.draggingConnectionNode = node.id;
                        this.draggingConnectionIndex = j;
                        this.draggingConnectionInput = false;

                        return true;
                    }
                }

                List<String> inputs = node.getInputs();

                for (int j = 0; j < inputs.size(); j++)
                {
                    int sx = nx;
                    int sy = this.socketScreenY(node, j);

                    if (Math.abs(mx - sx) < hitR && Math.abs(my - sy) < hitR)
                    {
                        this.draggingConnectionNode = node.id;
                        this.draggingConnectionIndex = j;
                        this.draggingConnectionInput = true;

                        return true;
                    }
                }
            }

            /* Check node bodies */
            for (int i = nodes.size() - 1; i >= 0; i--)
            {
                ShapeNode node = nodes.get(i);
                int nx = this.nodeScreenX(node);
                int ny = this.nodeScreenY(node);
                int max = Math.max(node.getInputs().size(), node.getOutputs().size());
                int w = this.nodeScreenW(node);
                int h = (int) ((35 + this.getNodePreviewHeight(node) + max * 20) * this.scale);

                if (mx >= nx && mx <= nx + w && my >= ny && my <= ny + h)
                {
                    if (context.mouseButton == 0)
                    {
                        if (Window.isCtrlPressed())
                        {
                            if (this.selection.contains(node)) this.selection.remove(node);
                            else this.selection.add(node);
                        }
                        else if (Window.isShiftPressed())
                        {
                            this.selection.add(node);
                        }
                        else
                        {
                            if (!this.selection.contains(node))
                            {
                                this.selection.clear();
                                this.selectedConnections.clear();
                                this.selection.add(node);
                            }
                        }

                        /* Bring node to top of render stack */
                        this.graph.bringToFront(node);

                        this.draggingNode = node;
                        this.draggingMouseX = mx;
                        this.draggingMouseY = my;
                        this.lastMouseX = mx;
                        this.lastMouseY = my;

                        this.initialPositions.clear();

                        for (ShapeNode sel : this.selection)
                        {
                            this.initialPositions.put(sel, new Vector2f(sel.x, sel.y));
                        }

                        return true;
                    }
                    else if (context.mouseButton == 1)
                    {
                        this.lastMouseX = mx;
                        this.lastMouseY = my;

                        if (!this.selection.contains(node))
                        {
                            this.selection.clear();
                            this.selectedConnections.clear();
                            this.selection.add(node);
                        }

                        this.openNodeContextMenu(context, node);

                        return true;
                    }
                }
            }

            /* Check connections */
            for (ShapeConnection c : this.graph.getConnections())
            {
                if (this.isOverConnection(mx, my, c))
                {
                    if (context.mouseButton == 0)
                    {
                        if (Window.isCtrlPressed())
                        {
                            if (this.selectedConnections.contains(c)) this.selectedConnections.remove(c);
                            else this.selectedConnections.add(c);
                        }
                        else if (Window.isShiftPressed())
                        {
                            this.selectedConnections.add(c);
                        }
                        else
                        {
                            this.selection.clear();
                            this.selectedConnections.clear();
                            this.selectedConnections.add(c);
                        }

                        return true;
                    }
                }
            }
        }

        if (context.mouseButton == 2 || context.mouseButton == 0)
        {
            if (context.mouseButton == 0 && Window.isShiftPressed())
            {
                this.selecting = true;
                this.selectingX = mx;
                this.selectingY = my;
            }
            else
            {
                this.lastMouseX = mx;
                this.lastMouseY = my;
                this.dragging = true;
            }

            if (!Window.isShiftPressed() && !Window.isCtrlPressed())
            {
                this.selection.clear();
                this.selectedConnections.clear();
            }

            return true;
        }

        if (context.mouseButton == 1)
        {
            this.lastMouseX = mx;
            this.lastMouseY = my;
            this.openContextMenu(context);
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.draggingConnectionNode != -1)
        {
            if (this.graph != null)
            {
                int mx = context.mouseX;
                int my = context.mouseY;
                List<ShapeNode> nodes = this.graph.getNodes();

                for (int i = nodes.size() - 1; i >= 0; i--)
                {
                    ShapeNode node = nodes.get(i);
                    int nx = this.nodeScreenX(node);
                    int ny = this.nodeScreenY(node);
                    int w = this.nodeScreenW(node);
                    float hitR = Math.max(10F * this.scale, 6F);

                    if (!this.draggingConnectionInput)
                    {
                        /* Output → find input socket on target node */
                        List<String> inputs = node.getInputs();

                        for (int j = 0; j < inputs.size(); j++)
                        {
                            int sx = nx;
                            int sy = this.socketScreenY(node, j);

                            if (Math.abs(mx - sx) < hitR && Math.abs(my - sy) < hitR)
                            {
                                final int inputIdx = j;
                                this.editGraph(() -> this.graph.connect(
                                    this.draggingConnectionNode, this.draggingConnectionIndex,
                                    node.id, inputIdx));
                                break;
                            }
                        }
                    }
                    else
                    {
                        /* Input → find output socket on target node */
                        List<String> outputs = node.getOutputs();

                        for (int j = 0; j < outputs.size(); j++)
                        {
                            int sx = nx + w;
                            int sy = this.socketScreenY(node, j);

                            if (Math.abs(mx - sx) < hitR && Math.abs(my - sy) < hitR)
                            {
                                final int outputIdx = j;
                                this.editGraph(() -> this.graph.connect(
                                    node.id, outputIdx,
                                    this.draggingConnectionNode, this.draggingConnectionIndex));
                                break;
                            }
                        }
                    }
                }
            }

            this.draggingConnectionNode = -1;
            this.draggingConnectionIndex = -1;
            this.draggingConnectionInput = false;

            return true;
        }

        if (this.selecting)
        {
            this.selectNodes(this.selectingX, this.selectingY, context.mouseX, context.mouseY,
                Window.isCtrlPressed());
            this.selecting = false;

            return true;
        }

        this.draggingNode = null;
        this.dragging = false;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (!this.area.isInside(context)) return super.subKeyPressed(context);

        if (Window.isCtrlPressed())
        {
            if (context.getKeyCode() == GLFW.GLFW_KEY_C)
            {
                this.copyNodes();
                return true;
            }
            else if (context.getKeyCode() == GLFW.GLFW_KEY_X)
            {
                if (this.selection.isEmpty() && this.selectedConnections.isEmpty())
                {
                    context.notifyError(UIKeys.GENERAL_CUT_EMPTY);
                    return true;
                }

                this.copyNodes();

                if (clipboard == null)
                {
                    context.notifyError(UIKeys.GENERAL_CUT_NOT_ALLOWED);
                    return true;
                }

                this.removeSelection();
                UIUtils.playClick();
                context.notifyInfo(UIKeys.GENERAL_CUT);
                return true;
            }
            else if (context.getKeyCode() == GLFW.GLFW_KEY_V)
            {
                this.pasteNodes(this.lastMouseX, this.lastMouseY);
                return true;
            }
        }

        if (context.getKeyCode() == GLFW.GLFW_KEY_DELETE
            || context.getKeyCode() == GLFW.GLFW_KEY_BACKSPACE)
        {
            if (!this.selection.isEmpty() || !this.selectedConnections.isEmpty())
            {
                this.removeSelection();
                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context)) return super.subMouseScrolled(context);

        float oldScale = this.scale;
        /* Proportional zoom: 10% per scroll step */
        this.scale *= (float) Math.pow(1.1, Math.copySign(1, context.mouseWheel));
        this.scale = Math.max(0.1F, Math.min(this.scale, 4F));

        float factor = this.scale / oldScale;

        /* Zoom towards mouse cursor */
        float cx = context.mouseX - this.area.x;
        float cy = context.mouseY - this.area.y;
        this.translateX = cx - (cx - this.translateX) * factor;
        this.translateY = cy - (cy - this.translateY) * factor;

        return true;
    }

    private void selectNodes(int x1, int y1, int x2, int y2, boolean add)
    {
        if (this.graph == null) return;

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);

        if (!add)
        {
            this.selection.clear();
        }

        for (ShapeNode node : this.graph.getNodes())
        {
            int nx = this.nodeScreenX(node);
            int ny = this.nodeScreenY(node);
            int max = Math.max(node.getInputs().size(), node.getOutputs().size());
            int w = this.nodeScreenW(node);
            int h = (int) ((35 + this.getNodePreviewHeight(node) + max * 20) * this.scale);

            if (nx + w > minX && nx < maxX && ny + h > minY && ny < maxY)
            {
                this.selection.add(node);
            }
        }
    }

    /* ======================================================================
     * Rendering
     * ====================================================================== */

    @Override
    public void render(UIContext context)
    {
        /* Pan update */
        if (this.dragging)
        {
            this.translateX += context.mouseX - this.lastMouseX;
            this.translateY += context.mouseY - this.lastMouseY;
            this.lastMouseX = context.mouseX;
            this.lastMouseY = context.mouseY;
        }

        /* Node drag update */
        if (this.draggingNode != null)
        {
            float dx = (context.mouseX - this.draggingMouseX) / this.scale;
            float dy = (context.mouseY - this.draggingMouseY) / this.scale;

            for (ShapeNode node : this.selection)
            {
                Vector2f pos = this.initialPositions.get(node);

                if (pos != null)
                {
                    node.x = pos.x + dx;
                    node.y = pos.y + dy;
                }
            }
        }

        if (!Window.isMouseButtonPressed(0) && !Window.isMouseButtonPressed(2))
        {
            this.dragging = false;
        }

        this.renderBackground(context);

        if (this.graph != null)
        {
            for (ShapeNode node : this.graph.getNodes())
            {
                this.drawNode(context, node);
            }

            for (ShapeConnection c : this.graph.getConnections())
            {
                this.drawConnection(context, c);
            }

            if (this.draggingConnectionNode != -1)
            {
                this.drawDraggingConnection(context);
            }
        }

        if (this.selecting)
        {
            context.batcher.normalizedBox(this.selectingX, this.selectingY,
                context.mouseX, context.mouseY, Colors.setA(Colors.ACTIVE, 0.25F));
        }

        super.render(context);
    }

    private void renderBackground(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A75 | 0x181818);
        context.batcher.clip(this.area, context);

        int size = 20;
        int color = Colors.A25 | 0xFFFFFF;
        float sc = this.scale;
        float ox = this.translateX % (size * sc);
        float oy = this.translateY % (size * sc);

        if (ox < 0) ox += size * sc;
        if (oy < 0) oy += size * sc;

        for (float x = ox; x < this.area.w; x += size * sc)
        {
            for (float y = oy; y < this.area.h; y += size * sc)
            {
                context.batcher.box(this.area.x + x - 1F, this.area.y + y - 1F,
                    this.area.x + x + 1F, this.area.y + y + 1F, color);
            }
        }

        context.batcher.unclip(context);
    }

    private void drawNode(UIContext context, ShapeNode node)
    {
        int x = this.nodeScreenX(node);
        int y = this.nodeScreenY(node);

        if (node instanceof CommentNode)
        {
            CommentNode comment = (CommentNode) node;
            int w = (int) (comment.width * this.scale);
            int h = (int) (comment.height * this.scale);

            context.batcher.box(x, y, x + w, y + h, Colors.A50 | 0x000000);
            context.batcher.outline(x, y, x + w, y + h, Colors.A50 | 0xFFFFFF);
            context.batcher.text(comment.title, x + 5, y + 5);

            if (!comment.comment.isEmpty())
            {
                context.batcher.text(comment.comment, x + 5, y + 20);
            }

            return;
        }

        List<String> inputs = node.getInputs();
        List<String> outputs = node.getOutputs();
        int max = Math.max(inputs.size(), outputs.size());

        int w = this.nodeScreenW(node);
        int h = (int) ((35 + this.getNodePreviewHeight(node) + max * 20) * this.scale);
        int headerH = (int) (20 * this.scale);

        /* Shadow */
        context.batcher.dropShadow(x, y, x + w, y + h, (int) (10 * this.scale), Colors.A50 | 0x000000, 0);

        /* Selection outline */
        if (this.selection.contains(node))
        {
            context.batcher.outline(x - 1, y - 1, x + w + 1, y + h + 1, Colors.WHITE);
            context.batcher.outline(x - 2, y - 2, x + w + 2, y + h + 2, Colors.WHITE);
        }

        /* Header */
        int headerColor = this.getNodeHeaderColor(node);
        context.batcher.box(x, y, x + w, y + headerH, headerColor);
        context.batcher.outline(x, y, x + w, y + headerH, 0xFF222222);

        /* Body */
        context.batcher.box(x, y + headerH, x + w, y + h, Colors.A75 | 0x222222);
        context.batcher.outline(x, y + headerH, x + w, y + h, 0xFF000000);

        /* Title */
        context.batcher.text(this.getNodeTitle(node), x + 5, y + 6);

        /* Color preview swatch for ColorNode */
        if (node instanceof ColorNode)
        {
            int c = ((ColorNode) node).color.getARGBColor();
            context.batcher.box(x + 5, y + 25, x + w - 5, y + 45, c);
            context.batcher.outline(x + 5, y + 25, x + w - 5, y + 45, 0xFF000000);
        }

        /* Texture preview */
        if (node instanceof TextureNode)
        {
            TextureNode tn = (TextureNode) node;
            int pad = (int) (5 * this.scale);
            int previewX = x + pad;
            int previewY = y + headerH + pad;
            int previewW = w - pad * 2;
            int previewH = (int) (42 * this.scale);

            if (tn.texture != null)
            {
                Texture tex = BBSModClient.getTextures().getTexture(tn.texture);

                if (tex != null)
                {
                    context.batcher.fullTexturedBox(tex, Colors.WHITE, previewX, previewY, previewW, previewH);
                }
                else
                {
                    context.batcher.box(previewX, previewY, previewX + previewW, previewY + previewH, Colors.A75 | 0x111111);
                }
            }
            else
            {
                context.batcher.box(previewX, previewY, previewX + previewW, previewY + previewH, Colors.A75 | 0x111111);
            }

            context.batcher.outline(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF000000);
        }

        /* RGBA channel strip for color mix/split/combine nodes */
        if (node instanceof MixColorNode || node instanceof SplitColorNode || node instanceof CombineColorNode)
        {
            int barY = y + headerH + (int) (2 * this.scale);
            int barH = (int) (10 * this.scale);
            int bw = w / 4;

            context.batcher.box(x,          barY, x + bw,     barY + barH, Colors.A100 | 0xFF4444);
            context.batcher.box(x + bw,     barY, x + bw * 2, barY + barH, Colors.A100 | 0x44FF44);
            context.batcher.box(x + bw * 2, barY, x + bw * 3, barY + barH, Colors.A100 | 0x4444FF);
            context.batcher.box(x + bw * 3, barY, x + w,      barY + barH, Colors.A100 | 0x888888);
        }

        /* Input sockets */
        for (int i = 0; i < inputs.size(); i++)
        {
            int sy = this.socketScreenY(node, i);
            int sockColor = this.socketColor(inputs.get(i));
            boolean connected = this.isInputConnected(node.id, i);

            this.drawSocket(context, x, sy, sockColor, connected);
            context.batcher.text(inputs.get(i), x + (int) (8 * this.scale), sy - 4);
        }

        /* Output sockets */
        for (int i = 0; i < outputs.size(); i++)
        {
            int sy = this.socketScreenY(node, i);
            String label = outputs.get(i);
            int sockColor = this.socketColor(label);
            boolean connected = this.isOutputConnected(node.id, i);

            this.drawSocket(context, x + w, sy, sockColor, connected);
            context.batcher.text(label,
                x + w - (int) (8 * this.scale) - context.batcher.getFont().getWidth(label),
                sy - 4);
        }
    }

    private void drawConnection(UIContext context, ShapeConnection c)
    {
        ShapeNode out = this.findNode(c.outputNodeId);
        ShapeNode in = this.findNode(c.inputNodeId);

        if (out == null || in == null) return;

        int x1 = this.nodeScreenX(out) + this.nodeScreenW(out);
        int y1 = this.socketScreenY(out, c.outputIndex);
        int x2 = this.nodeScreenX(in);
        int y2 = this.socketScreenY(in, c.inputIndex);

        int color = this.selectedConnections.contains(c)
            ? (Colors.A100 | Colors.ACTIVE)
            : Colors.WHITE;

        this.drawBezier(context, x1, y1, x2, y2, color, 2F * this.scale);
    }

    private void drawDraggingConnection(UIContext context)
    {
        ShapeNode node = this.findNode(this.draggingConnectionNode);

        if (node == null) return;

        if (!this.draggingConnectionInput)
        {
            int x1 = this.nodeScreenX(node) + this.nodeScreenW(node);
            int y1 = this.socketScreenY(node, this.draggingConnectionIndex);

            this.drawBezier(context, x1, y1, context.mouseX, context.mouseY, Colors.WHITE, 2F * this.scale);
        }
        else
        {
            int x1 = this.nodeScreenX(node);
            int y1 = this.socketScreenY(node, this.draggingConnectionIndex);

            this.drawBezier(context, context.mouseX, context.mouseY, x1, y1, Colors.WHITE, 2F * this.scale);
        }
    }

    private void drawSocket(UIContext context, int x, int y, int color, boolean filled)
    {
        int r = (int) Math.max(5F * this.scale, 3F);

        /* Dark border */
        context.batcher.box(x - r - 1, y - r - 1, x + r + 1, y + r + 1, 0xFF000000);

        /* Fill — colored when connected, dark when not */
        int fillColor = filled ? color : (Colors.A100 | 0x333333);
        context.batcher.box(x - r, y - r, x + r, y + r, fillColor);

        /* Inner dot when connected */
        if (filled)
        {
            int ir = Math.max(1, (int) (r * 0.45F));
            context.batcher.box(x - ir, y - ir, x + ir, y + ir, 0xFF000000);
        }
    }

    private void drawBezier(UIContext context, int x1, int y1, int x2, int y2, int color, float thickness)
    {
        int segments = 32;
        float dist = Math.max(Math.abs(x2 - x1) / 2F, 50F * this.scale);
        float hw = Math.max(1.5F, thickness * 0.5F);

        float px = x1;
        float py = y1;

        for (int i = 1; i <= segments; i++)
        {
            float t = i / (float) segments;
            float t1 = 1 - t;

            float c0 = t1 * t1 * t1;
            float c1 = 3 * t1 * t1 * t;
            float c2 = 3 * t1 * t * t;
            float c3 = t * t * t;

            float nx = c0 * x1 + c1 * (x1 + dist) + c2 * (x2 - dist) + c3 * x2;
            float ny = c0 * y1 + c1 * y1 + c2 * y2 + c3 * y2;

            float minX = Math.min(px, nx);
            float maxX = Math.max(px, nx);
            float minY = Math.min(py, ny);
            float maxY = Math.max(py, ny);

            /* Ensure minimum thickness on nearly-horizontal or nearly-vertical segments */
            if (maxX - minX < hw * 2)
            {
                float cx = (minX + maxX) / 2F;
                minX = cx - hw;
                maxX = cx + hw;
            }

            if (maxY - minY < hw * 2)
            {
                float cy = (minY + maxY) / 2F;
                minY = cy - hw;
                maxY = cy + hw;
            }

            context.batcher.box(minX, minY, maxX, maxY, color);

            px = nx;
            py = ny;
        }
    }

    /* ======================================================================
     * Helpers
     * ====================================================================== */

    /** Returns a color for a socket based on its slot name. */
    private static int socketColor(String name)
    {
        String n = name.toLowerCase();

        if (n.equals("rgba") || n.contains("color")) return SOCKET_COLOR;

        if (n.equals("r") || n.equals("g") || n.equals("b") || n.equals("a")) return SOCKET_COLOR;

        if (n.equals("x") || n.equals("y") || n.equals("z")
            || n.startsWith("ax") || n.startsWith("ay") || n.startsWith("az")
            || n.startsWith("bx") || n.startsWith("by") || n.startsWith("bz")) return SOCKET_VECTOR;

        return SOCKET_FLOAT;
    }

    private int nodeScreenX(ShapeNode node)
    {
        return (int) (this.area.x + this.translateX + node.x * this.scale);
    }

    private int nodeScreenY(ShapeNode node)
    {
        return (int) (this.area.y + this.translateY + node.y * this.scale);
    }

    private int nodeScreenW(ShapeNode node)
    {
        return (int) (this.getNodeWidth(node) * this.scale);
    }

    private int socketScreenY(ShapeNode node, int index)
    {
        return this.nodeScreenY(node) + (int) ((30 + this.getNodePreviewHeight(node) + index * 20) * this.scale);
    }

    private boolean isInputConnected(int nodeId, int inputIndex)
    {
        if (this.graph == null) return false;

        for (ShapeConnection c : this.graph.getConnections())
        {
            if (c.inputNodeId == nodeId && c.inputIndex == inputIndex) return true;
        }

        return false;
    }

    private boolean isOutputConnected(int nodeId, int outputIndex)
    {
        if (this.graph == null) return false;

        for (ShapeConnection c : this.graph.getConnections())
        {
            if (c.outputNodeId == nodeId && c.outputIndex == outputIndex) return true;
        }

        return false;
    }

    private boolean isOverConnection(int mx, int my, ShapeConnection c)
    {
        ShapeNode out = this.findNode(c.outputNodeId);
        ShapeNode in = this.findNode(c.inputNodeId);

        if (out == null || in == null) return false;

        int x1 = this.nodeScreenX(out) + this.nodeScreenW(out);
        int y1 = this.socketScreenY(out, c.outputIndex);
        int x2 = this.nodeScreenX(in);
        int y2 = this.socketScreenY(in, c.inputIndex);

        return this.isOverBezier(mx, my, x1, y1, x2, y2);
    }

    private boolean isOverBezier(int mx, int my, int x1, int y1, int x2, int y2)
    {
        int segments = 24;
        float dist = Math.max(Math.abs(x2 - x1) / 2F, 50F * this.scale);
        float threshold = 5F * this.scale;

        float px = x1;
        float py = y1;

        for (int i = 1; i <= segments; i++)
        {
            float t = i / (float) segments;
            float t1 = 1 - t;

            float c0 = t1 * t1 * t1;
            float c1 = 3 * t1 * t1 * t;
            float c2 = 3 * t1 * t * t;
            float c3 = t * t * t;

            float x = c0 * x1 + c1 * (x1 + dist) + c2 * (x2 - dist) + c3 * x2;
            float y = c0 * y1 + c1 * y1 + c2 * y2 + c3 * y2;

            if (this.distanceToSegment(mx, my, px, py, x, y) < threshold) return true;

            px = x;
            py = y;
        }

        return false;
    }

    private float distanceToSegment(float x, float y, float x1, float y1, float x2, float y2)
    {
        float A = x - x1;
        float B = y - y1;
        float C = x2 - x1;
        float D = y2 - y1;

        float dot = A * C + B * D;
        float lenSq = C * C + D * D;
        float param = lenSq != 0 ? dot / lenSq : -1;

        float xx;
        float yy;

        if (param < 0)
        {
            xx = x1;
            yy = y1;
        }
        else if (param > 1)
        {
            xx = x2;
            yy = y2;
        }
        else
        {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }

        float dx = x - xx;
        float dy = y - yy;

        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private ShapeNode findNode(int id)
    {
        if (this.graph == null) return null;

        for (ShapeNode node : this.graph.getNodes())
        {
            if (node.id == id) return node;
        }

        return null;
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putFloat("scale", this.scale);
        data.putFloat("translate_x", this.translateX);
        data.putFloat("translate_y", this.translateY);

        ListType nodeIds = new ListType();

        for (ShapeNode node : this.selection)
        {
            nodeIds.add(new IntType(node.id));
        }

        data.put("node_selection", nodeIds);

        ListType connections = new ListType();

        for (ShapeConnection connection : this.selectedConnections)
        {
            MapType connectionData = new MapType();

            connection.toData(connectionData);
            connections.add(connectionData);
        }

        data.put("connection_selection", connections);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.scale = data.getFloat("scale", 1F);
        this.translateX = data.getFloat("translate_x");
        this.translateY = data.getFloat("translate_y");

        this.selection.clear();
        this.selectedConnections.clear();

        if (this.graph == null)
        {
            return;
        }

        for (BaseType type : data.getList("node_selection"))
        {
            ShapeNode node = this.findNode(type.asNumeric().intValue());

            if (node != null)
            {
                this.selection.add(node);
            }
        }

        for (BaseType type : data.getList("connection_selection"))
        {
            ShapeConnection stored = new ShapeConnection();

            stored.fromData(type.asMap());

            for (ShapeConnection connection : this.graph.getConnections())
            {
                if (this.connectionMatches(connection, stored))
                {
                    this.selectedConnections.add(connection);

                    break;
                }
            }
        }
    }

    private boolean connectionMatches(ShapeConnection a, ShapeConnection b)
    {
        return a.outputNodeId == b.outputNodeId
            && a.outputIndex == b.outputIndex
            && a.inputNodeId == b.inputNodeId
            && a.inputIndex == b.inputIndex;
    }
}
