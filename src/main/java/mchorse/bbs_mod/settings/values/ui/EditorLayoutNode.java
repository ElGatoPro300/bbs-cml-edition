package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public abstract class EditorLayoutNode
{
    public static final String TYPE_SPLITTER = "splitter";
    public static final String TYPE_PANEL = "panel";
    public static final String TYPE_TABBED = "tabbed";
    public static final String DIR_V = "v";
    public static final String DIR_H = "h";

    public static final int EDGE_LEFT = 0;
    public static final int EDGE_RIGHT = 1;
    public static final int EDGE_TOP = 2;
    public static final int EDGE_BOTTOM = 3;

    public abstract BaseType toData();

    public abstract void computeBounds(float x, float y, float w, float h, Map<String, float[]> out);

    public abstract EditorLayoutNode copyWithSwappedIds(String id1, String id2);

    public static EditorLayoutNode fromData(BaseType data)
    {
        EditorLayoutNode decoded = LayoutCodec.decode(data);

        return decoded == null ? defaultFilmLayout() : decoded;
    }

    public static EditorLayoutNode defaultFilmLayout()
    {
        return new SplitterNode(
            true,
            0.5F,
            new SplitterNode(
                false,
                0.33F,
                new PanelNode("editArea"),
                new SplitterNode(
                    false,
                    0.5F,
                    new PanelNode("replayTimeline"),
                    new PanelNode("preview")
                )
            ),
            new SplitterNode(
                false,
                0.33F,
                new PanelNode("cameraEditArea"),
                new SplitterNode(
                    false,
                    0.5F,
                    new PanelNode("cameraTimeline"),
                    new PanelNode("actionTimeline")
                )
            )
        );
    }

    public static EditorLayoutNode classicCinemaFilmLayout()
    {
        return new SplitterNode(
            false,
            0.6F,
            new SplitterNode(
                true,
                0.68F,
                new PanelNode("preview"),
                new SplitterNode(
                    false,
                    0.5F,
                    new PanelNode("cameraEditArea"),
                    new PanelNode("editArea")
                )
            ),
            new SplitterNode(
                false,
                0.5F,
                new PanelNode("cameraTimeline"),
                new SplitterNode(
                    true,
                    0.5F,
                    new PanelNode("actionTimeline"),
                    new PanelNode("replayTimeline")
                )
            )
        );
    }

    public static EditorLayoutNode viewportFocusFilmLayout()
    {
        return new SplitterNode(
            true,
            0.75F,
            new SplitterNode(
                false,
                0.72F,
                new PanelNode("preview"),
                new SplitterNode(
                    true,
                    0.5F,
                    new PanelNode("cameraTimeline"),
                    new PanelNode("actionTimeline")
                )
            ),
            new SplitterNode(
                false,
                0.35F,
                new PanelNode("cameraEditArea"),
                new SplitterNode(
                    false,
                    0.5F,
                    new PanelNode("editArea"),
                    new PanelNode("replayTimeline")
                )
            )
        );
    }

    /**
     * Default particle layout: the section-group tabs stacked across the top, with the bottom split
     * between the preview (left) and the MoLang editor (right).
     */
    public static EditorLayoutNode defaultParticleLayout()
    {
        List<EditorLayoutNode> tabs = new ArrayList<>();

        tabs.add(new PanelNode("general"));
        tabs.add(new PanelNode("emitter"));
        tabs.add(new PanelNode("particle"));
        tabs.add(new PanelNode("appearance"));

        return new SplitterNode(
            false,
            0.22446808F,
            new TabbedNode(tabs, 0),
            new SplitterNode(
                true,
                0.7408994F,
                new PanelNode("preview"),
                new PanelNode("molang")
            )
        );
    }

    public static EditorLayoutNode copyWithRemovedLeaf(EditorLayoutNode root, String panelId)
    {
        if (root == null || panelId == null || panelId.isEmpty())
        {
            return root;
        }

        LayoutModel model = LayoutModel.fromRoot(root);
        if (!model.removePanel(panelId))
        {
            return root;
        }

        return model.toRoot();
    }

    public static EditorLayoutNode copyWithReplacedLeaf(EditorLayoutNode root, String leafId, EditorLayoutNode newNode)
    {
        if (root == null || newNode == null || leafId == null || leafId.isEmpty())
        {
            return root;
        }

        LayoutModel model = LayoutModel.fromRoot(root);
        int replacementRoot = model.importTree(newNode);

        if (!model.replacePanel(leafId, replacementRoot))
        {
            return root;
        }

        EditorLayoutNode rebuilt = model.toRoot();
        return rebuilt == null ? root : rebuilt;
    }

    public static EditorLayoutNode copyWithInsertSplitAt(EditorLayoutNode root, String targetPanelId, String droppedPanelId, int edge)
    {
        if (root == null || targetPanelId == null || droppedPanelId == null || targetPanelId.equals(droppedPanelId))
        {
            return root;
        }

        LayoutModel model = LayoutModel.fromRoot(root);
        if (!model.insertPanelAt(targetPanelId, droppedPanelId, edge))
        {
            return root;
        }

        EditorLayoutNode rebuilt = model.toRoot();
        return rebuilt == null ? root : rebuilt;
    }

    
    public static EditorLayoutNode copyWithDockedLeaf(EditorLayoutNode root, String targetPanelId, String droppedPanelId)
    {
        if (root == null || targetPanelId == null || droppedPanelId == null || targetPanelId.equals(droppedPanelId))
        {
            return root;
        }

        LayoutModel model = LayoutModel.fromRoot(root);
        if (!model.dockPanelAt(targetPanelId, droppedPanelId))
        {
            return root;
        }

        EditorLayoutNode rebuilt = model.toRoot();
        return rebuilt == null ? root : rebuilt;
    }

    public static EditorLayoutNode copyWithStackActivePanel(EditorLayoutNode root, String panelId, String activePanelId)
    {
        if (root == null || panelId == null || activePanelId == null)
        {
            return root;
        }

        if (root instanceof TabbedNode)
        {
            TabbedNode tabbed = (TabbedNode) root;

            int panelIndex = -1;
            int activeIndex = -1;

            for (int i = 0; i < tabbed.tabs.size(); i++)
            {
                EditorLayoutNode tab = tabbed.tabs.get(i);
                if (tab instanceof PanelNode)
                {
                    String id = ((PanelNode) tab).getPanelId();
                    if (id.equals(panelId))
                    {
                        panelIndex = i;
                    }
                    if (id.equals(activePanelId))
                    {
                        activeIndex = i;
                    }
                }
            }

            if (panelIndex != -1 && activeIndex != -1)
            {
                if (tabbed.activeTab == activeIndex)
                {
                    return root;
                }

                return new TabbedNode(tabbed.tabs, activeIndex);
            }
        }

        if (root instanceof SplitterNode)
        {
            SplitterNode splitter = (SplitterNode) root;

            EditorLayoutNode first = copyWithStackActivePanel(splitter.first, panelId, activePanelId);
            if (first != splitter.first)
            {
                return new SplitterNode(splitter.horizontal, splitter.ratio, first, splitter.second);
            }

            EditorLayoutNode second = copyWithStackActivePanel(splitter.second, panelId, activePanelId);
            if (second != splitter.second)
            {
                return new SplitterNode(splitter.horizontal, splitter.ratio, splitter.first, second);
            }
        }

        return root;
    }

    public static EditorLayoutNode copyWithReorderedTabs(EditorLayoutNode root, String panelId, int fromIndex, int toIndex)
    {
        if (root == null || panelId == null)
        {
            return root;
        }

        if (root instanceof TabbedNode)
        {
            TabbedNode tabbed = (TabbedNode) root;
            int from = -1;
            String activeId = null;

            for (int i = 0; i < tabbed.tabs.size(); i++)
            {
                EditorLayoutNode tab = tabbed.tabs.get(i);

                if (!(tab instanceof PanelNode))
                {
                    continue;
                }

                String id = ((PanelNode) tab).getPanelId();

                if (id.equals(panelId))
                {
                    from = i;
                }

                if (i == tabbed.activeTab)
                {
                    activeId = id;
                }
            }

            if (from >= 0)
            {
                List<EditorLayoutNode> newTabs = new ArrayList<>(tabbed.tabs);
                EditorLayoutNode moved = newTabs.remove(from);
                /* toIndex is the insert slot among remaining tabs (0..count inclusive).
                 * Count may equal the original size when dropping after the last tab. */
                int insertAt = Math.max(0, Math.min(toIndex, newTabs.size()));

                if (insertAt == from)
                {
                    return root;
                }

                newTabs.add(insertAt, moved);

                int newActiveTab = Math.max(0, Math.min(tabbed.activeTab, newTabs.size() - 1));

                if (activeId != null)
                {
                    for (int i = 0; i < newTabs.size(); i++)
                    {
                        EditorLayoutNode tab = newTabs.get(i);

                        if (tab instanceof PanelNode && ((PanelNode) tab).getPanelId().equals(activeId))
                        {
                            newActiveTab = i;
                            break;
                        }
                    }
                }

                return new TabbedNode(newTabs, newActiveTab);
            }
        }

        if (root instanceof SplitterNode)
        {
            SplitterNode splitter = (SplitterNode) root;

            EditorLayoutNode first = copyWithReorderedTabs(splitter.first, panelId, fromIndex, toIndex);

            if (first != splitter.first)
            {
                return new SplitterNode(splitter.horizontal, splitter.ratio, first, splitter.second);
            }

            EditorLayoutNode second = copyWithReorderedTabs(splitter.second, panelId, fromIndex, toIndex);

            if (second != splitter.second)
            {
                return new SplitterNode(splitter.horizontal, splitter.ratio, splitter.first, second);
            }
        }

        return root;
    }

    public static void collectSplitters(EditorLayoutNode node, List<SplitterNode> out)
    {
        if (node == null)
        {
            return;
        }

        Deque<EditorLayoutNode> stack = new ArrayDeque<>();
        stack.push(node);

        while (!stack.isEmpty())
        {
            EditorLayoutNode current = stack.pop();

            if (current instanceof SplitterNode)
            {
                SplitterNode splitter = (SplitterNode) current;
                out.add(splitter);
                stack.push(splitter.second);
                stack.push(splitter.first);
            }
            else if (current instanceof TabbedNode)
            {
                TabbedNode tabbed = (TabbedNode) current;

                if (!tabbed.tabs.isEmpty())
                {
                    int active = Math.max(0, Math.min(tabbed.activeTab, tabbed.tabs.size() - 1));
                    stack.push(tabbed.tabs.get(active));
                }
            }
        }
    }

    public static void collectTabbedNodes(EditorLayoutNode node, List<TabbedNode> out)
    {
        if (node == null)
        {
            return;
        }

        Deque<EditorLayoutNode> stack = new ArrayDeque<>();
        stack.push(node);

        while (!stack.isEmpty())
        {
            EditorLayoutNode current = stack.pop();
            if (current instanceof TabbedNode)
            {
                TabbedNode tabbed = (TabbedNode) current;
                out.add(tabbed);
                for (int i = tabbed.tabs.size() - 1; i >= 0; i--)
                {
                    stack.push(tabbed.tabs.get(i));
                }
            }
            else if (current instanceof SplitterNode)
            {
                SplitterNode splitter = (SplitterNode) current;
                stack.push(splitter.second);
                stack.push(splitter.first);
            }
        }
    }

    public static class SplitterHandleInfo
    {
        public final float hx, hy, hw, hh;
        public final float px, py, pw, ph;
        public final boolean horizontal;

        public SplitterHandleInfo(float hx, float hy, float hw, float hh, float px, float py, float pw, float ph, boolean horizontal)
        {
            this.hx = hx;
            this.hy = hy;
            this.hw = hw;
            this.hh = hh;
            this.px = px;
            this.py = py;
            this.pw = pw;
            this.ph = ph;
            this.horizontal = horizontal;
        }
    }

    private static final float SPLITTER_HANDLE_MARGIN = 0.003F;
    private static final float SPLITTER_HANDLE_MIN_THICKNESS = 0.02F;

    public static void computeSplitterHandles(EditorLayoutNode root, float x, float y, float w, float h, List<SplitterHandleInfo> out)
    {
        if (root == null)
        {
            return;
        }

        LayoutModel.fromRoot(root).fillSplitterHandles(x, y, w, h, out);
    }

    private static class LayoutCodec
    {
        private static EditorLayoutNode decode(BaseType data)
        {
            if (data == null || !data.isMap())
            {
                return null;
            }

            MapType map = data.asMap();
            String type = map.getString("type", "");

            if (TYPE_PANEL.equals(type))
            {
                String panelId = map.getString("id", "");
                return panelId.isEmpty() ? null : new PanelNode(panelId);
            }

            if (TYPE_SPLITTER.equals(type))
            {
                EditorLayoutNode first = decode(map.get("first"));
                EditorLayoutNode second = decode(map.get("second"));
                if (first == null || second == null)
                {
                    return null;
                }

                boolean horizontal = DIR_H.equals(map.getString("dir", DIR_V));
                float ratio = MathUtils.clamp(map.getFloat("ratio", 0.5F), 0.05F, 0.95F);

                return new SplitterNode(horizontal, ratio, first, second);
            }

            
            if (TYPE_TABBED.equals(type))
            {
                List<EditorLayoutNode> tabs = new ArrayList<>();
                if (map.has("tabs"))
                {
                    for (BaseType item : map.getList("tabs"))
                    {
                        EditorLayoutNode tab = decode(item);
                        if (tab != null)
                        {
                            tabs.add(tab);
                        }
                    }
                }
                int activeTab = map.getInt("active_tab", 0);
                if (tabs.isEmpty()) return null;
                return new TabbedNode(tabs, activeTab);
            }

            return null;
        }
    }

    private static class LayoutModel
    {
        private final List<ModelNode> nodes = new ArrayList<>();
        private int root = -1;

        private static LayoutModel fromRoot(EditorLayoutNode root)
        {
            LayoutModel model = new LayoutModel();
            if (root != null)
            {
                model.root = model.importTree(root);
            }

            return model;
        }

        private int importTree(EditorLayoutNode treeRoot)
        {
            IdentityHashMap<EditorLayoutNode, Integer> ids = new IdentityHashMap<>();
            Deque<BuildCursor> stack = new ArrayDeque<>();
            stack.push(new BuildCursor(treeRoot, false));

            while (!stack.isEmpty())
            {
                BuildCursor cursor = stack.pop();
                if (cursor.expanded)
                {
                    if (cursor.node instanceof PanelNode)
                    {
                        PanelNode panel = (PanelNode) cursor.node;
                        ids.put(cursor.node, this.addPanel(panel.panelId));
                    }
                                        else if (cursor.node instanceof TabbedNode)
                    {
                        TabbedNode tabbed = (TabbedNode) cursor.node;
                        List<Integer> tabIds = new ArrayList<>();
                        for (EditorLayoutNode tab : tabbed.tabs)
                        {
                            tabIds.add(ids.get(tab));
                        }
                        ids.put(cursor.node, this.addTabbed(tabIds, tabbed.activeTab));
                    }
                    else
                    {
                        SplitterNode splitter = (SplitterNode) cursor.node;
                        int first = ids.get(splitter.first);
                        int second = ids.get(splitter.second);
                        ids.put(cursor.node, this.addSplitter(splitter.horizontal, splitter.ratio, first, second));
                    }

                    continue;
                }

                stack.push(new BuildCursor(cursor.node, true));
                                if (cursor.node instanceof TabbedNode)
                {
                    TabbedNode tabbed = (TabbedNode) cursor.node;
                    for (int i = tabbed.tabs.size() - 1; i >= 0; i--)
                    {
                        stack.push(new BuildCursor(tabbed.tabs.get(i), false));
                    }
                }
                if (cursor.node instanceof SplitterNode)
                {
                    SplitterNode splitter = (SplitterNode) cursor.node;
                    stack.push(new BuildCursor(splitter.second, false));
                    stack.push(new BuildCursor(splitter.first, false));
                }
            }

            return ids.get(treeRoot);
        }

        private EditorLayoutNode toRoot()
        {
            if (!this.isValid(this.root))
            {
                return null;
            }

            Map<Integer, EditorLayoutNode> built = new HashMap<>();
            Deque<IndexCursor> stack = new ArrayDeque<>();
            stack.push(new IndexCursor(this.root, false));

            while (!stack.isEmpty())
            {
                IndexCursor cursor = stack.pop();
                ModelNode modelNode = this.nodes.get(cursor.index);

                if (cursor.expanded)
                {
                    if (modelNode.type == ModelNodeType.PANEL)
                    {
                        built.put(cursor.index, new PanelNode(modelNode.panelId));
                    }
                                        else if (modelNode.type == ModelNodeType.TABBED)
                    {
                        List<EditorLayoutNode> tabs = new ArrayList<>();
                        for (int tabIndex : modelNode.tabs)
                        {
                            tabs.add(built.get(tabIndex));
                        }
                        built.put(cursor.index, new TabbedNode(tabs, modelNode.activeTab));
                    }
                    else
                    {
                        EditorLayoutNode first = built.get(modelNode.first);
                        EditorLayoutNode second = built.get(modelNode.second);
                        built.put(cursor.index, new SplitterNode(modelNode.horizontal, modelNode.ratio, first, second));
                    }

                    continue;
                }

                stack.push(new IndexCursor(cursor.index, true));
                                if (modelNode.type == ModelNodeType.TABBED)
                {
                    for (int i = modelNode.tabs.size() - 1; i >= 0; i--)
                    {
                        stack.push(new IndexCursor(modelNode.tabs.get(i), false));
                    }
                }
                if (modelNode.type == ModelNodeType.SPLITTER)
                {
                    stack.push(new IndexCursor(modelNode.second, false));
                    stack.push(new IndexCursor(modelNode.first, false));
                }
            }

            return built.get(this.root);
        }

        private boolean removePanel(String panelId)
        {
            int panel = this.findPanel(panelId);
            if (!this.isValid(panel))
            {
                return false;
            }

            if (panel == this.root)
            {
                this.root = -1;
                return true;
            }

            int[] parents = this.parentLinks();
            int parent = parents[panel];
            if (!this.isValid(parent))
            {
                return false;
            }

            ModelNode parentNode = this.nodes.get(parent);

            if (parentNode.type == ModelNodeType.TABBED)
            {
                /* Remove the tab from the list */
                int tabIdx = parentNode.tabs.indexOf(panel);
                if (tabIdx < 0) return false;
                parentNode.tabs.remove(tabIdx);

                /* Fix activeTab index */
                if (parentNode.activeTab >= parentNode.tabs.size())
                {
                    parentNode.activeTab = Math.max(0, parentNode.tabs.size() - 1);
                }

                /* Collapse TabbedNode if only 1 tab remains */
                if (parentNode.tabs.size() == 1)
                {
                    int remaining = parentNode.tabs.get(0);
                    parentNode.tabs.clear(); /* Prevent orphaned node from corrupting parentLinks */
                    int grandParent = parents[parent];
                    if (!this.isValid(grandParent))
                    {
                        this.root = remaining;
                    }
                    else
                    {
                        this.relink(grandParent, parent, remaining);
                    }
                }
                else if (parentNode.tabs.isEmpty())
                {
                    int grandParent = parents[parent];
                    if (!this.isValid(grandParent))
                    {
                        this.root = -1;
                    }
                }

                return true;
            }

            /* SplitterNode parent: replace parent with the sibling */
            int sibling = parentNode.first == panel ? parentNode.second : parentNode.first;
            /* Prevent orphaned splitter from corrupting parentLinks */
            parentNode.first = -1;
            parentNode.second = -1;
            int grandParent = parents[parent];

            if (!this.isValid(grandParent))
            {
                this.root = sibling;
                return true;
            }

            this.relink(grandParent, parent, sibling);
            return true;
        }

        private boolean replacePanel(String panelId, int replacementRoot)
        {
            int panel = this.findPanel(panelId);
            if (!this.isValid(panel) || !this.isValid(replacementRoot))
            {
                return false;
            }

            if (panel == this.root)
            {
                this.root = replacementRoot;
                return true;
            }

            int[] parents = this.parentLinks();
            int parent = parents[panel];
            if (!this.isValid(parent))
            {
                return false;
            }

            this.relink(parent, panel, replacementRoot);
            return true;
        }

        private boolean insertPanelAt(String targetPanelId, String droppedPanelId, int edge)
        {
            Placement placement = Placement.fromEdge(edge);
            if (placement == null)
            {
                return false;
            }

            if (targetPanelId == null || droppedPanelId == null || targetPanelId.equals(droppedPanelId))
            {
                return false;
            }

            int existingPanel = this.findPanel(droppedPanelId);
            if (this.isValid(existingPanel))
            {
                if (!this.removePanel(droppedPanelId))
                {
                    return false;
                }
            }

            int target = this.findPanel(targetPanelId);
            if (!this.isValid(target))
            {
                return false;
            }

            int[] parents = this.parentLinks();
            int parent = parents[target];

            if (this.isValid(parent) && this.nodes.get(parent).type == ModelNodeType.TABBED)
            {
                // We cannot insert a SplitterNode inside a TabbedNode.
                // We must promote the target to the TabbedNode itself, so we split the entire TabbedNode block.
                target = parent;
                parent = parents[target];
            }

            int dropped = this.addPanel(droppedPanelId);
            int first = placement.droppedFirst ? dropped : target;
            int second = placement.droppedFirst ? target : dropped;
            int split = this.addSplitter(placement.horizontal, 0.5F, first, second);

            if (!this.isValid(parent))
            {
                this.root = split;
            }
            else
            {
                this.relink(parent, target, split);
            }

            return true;
        }

        private boolean swapPanelIds(String id1, String id2)
        {
            if (id1 == null || id2 == null || id1.equals(id2))
            {
                return false;
            }

            int first = this.findPanel(id1);
            int second = this.findPanel(id2);
            if (!this.isValid(first) || !this.isValid(second))
            {
                return false;
            }

            ModelNode a = this.nodes.get(first);
            ModelNode b = this.nodes.get(second);
            String x = a.panelId;
            a.panelId = b.panelId;
            b.panelId = x;

            return true;
        }

        private void fillBounds(float x, float y, float w, float h, Map<String, float[]> out)
        {
            if (!this.isValid(this.root))
            {
                return;
            }

            Deque<BoundsCursor> stack = new ArrayDeque<>();
            stack.push(new BoundsCursor(this.root, x, y, w, h));

            while (!stack.isEmpty())
            {
                BoundsCursor cursor = stack.pop();
                ModelNode node = this.nodes.get(cursor.index);

                if (node.type == ModelNodeType.PANEL)
                {
                    out.put(node.panelId, new float[] {cursor.x, cursor.y, cursor.w, cursor.h});
                    continue;
                }
                if (node.type == ModelNodeType.TABBED)
                {
                    if (node.tabs.size() > 0 && node.activeTab >= 0 && node.activeTab < node.tabs.size())
                    {
                        stack.push(new BoundsCursor(node.tabs.get(node.activeTab), cursor.x, cursor.y, cursor.w, cursor.h));
                    }
                    continue;
                }

                if (node.horizontal)
                {
                    float h1 = cursor.h * node.ratio;
                    stack.push(new BoundsCursor(node.second, cursor.x, cursor.y + h1, cursor.w, cursor.h - h1));
                    stack.push(new BoundsCursor(node.first, cursor.x, cursor.y, cursor.w, h1));
                }
                else
                {
                    float w1 = cursor.w * node.ratio;
                    stack.push(new BoundsCursor(node.second, cursor.x + w1, cursor.y, cursor.w - w1, cursor.h));
                    stack.push(new BoundsCursor(node.first, cursor.x, cursor.y, w1, cursor.h));
                }
            }
        }

        private void fillSplitterHandles(float x, float y, float w, float h, List<SplitterHandleInfo> out)
        {
            if (!this.isValid(this.root))
            {
                return;
            }

            float thickness = Math.max(2F * SPLITTER_HANDLE_MARGIN, SPLITTER_HANDLE_MIN_THICKNESS);
            Deque<BoundsCursor> stack = new ArrayDeque<>();
            stack.push(new BoundsCursor(this.root, x, y, w, h));

            while (!stack.isEmpty())
            {
                BoundsCursor cursor = stack.pop();
                ModelNode node = this.nodes.get(cursor.index);

                if (node.type == ModelNodeType.TABBED)
                {
                    if (node.tabs.size() > 0 && node.activeTab >= 0 && node.activeTab < node.tabs.size())
                    {
                        stack.push(new BoundsCursor(node.tabs.get(node.activeTab), cursor.x, cursor.y, cursor.w, cursor.h));
                    }

                    continue;
                }

                if (node.type == ModelNodeType.PANEL)
                {
                    continue;
                }

                if (node.type != ModelNodeType.SPLITTER)
                {
                    continue;
                }

                if (node.horizontal)
                {
                    float h1 = cursor.h * node.ratio;
                    float hy = cursor.y + h1 - thickness * 0.5F;
                    out.add(new SplitterHandleInfo(cursor.x, hy, cursor.w, thickness, cursor.x, cursor.y, cursor.w, cursor.h, true));
                    stack.push(new BoundsCursor(node.second, cursor.x, cursor.y + h1, cursor.w, cursor.h - h1));
                    stack.push(new BoundsCursor(node.first, cursor.x, cursor.y, cursor.w, h1));
                }
                else
                {
                    float w1 = cursor.w * node.ratio;
                    float hx = cursor.x + w1 - thickness * 0.5F;
                    out.add(new SplitterHandleInfo(hx, cursor.y, thickness, cursor.h, cursor.x, cursor.y, cursor.w, cursor.h, false));
                    stack.push(new BoundsCursor(node.second, cursor.x + w1, cursor.y, cursor.w - w1, cursor.h));
                    stack.push(new BoundsCursor(node.first, cursor.x, cursor.y, w1, cursor.h));
                }
            }
        }

        private int addPanel(String panelId)
        {
            this.nodes.add(ModelNode.panel(panelId));
            return this.nodes.size() - 1;
        }

        
        private int addTabbed(List<Integer> tabs, int activeTab)
        {
            this.nodes.add(ModelNode.tabbed(tabs, activeTab));
            return this.nodes.size() - 1;
        }

        private boolean dockPanelAt(String targetPanelId, String droppedPanelId)
        {
            if (targetPanelId == null || droppedPanelId == null || targetPanelId.equals(droppedPanelId))
            {
                return false;
            }

            int existingPanel = this.findPanel(droppedPanelId);
            if (this.isValid(existingPanel))
            {
                if (!this.removePanel(droppedPanelId))
                {
                    return false;
                }
            }

            int target = this.findPanel(targetPanelId);
            if (!this.isValid(target))
            {
                return false;
            }

            int dropped = this.addPanel(droppedPanelId);

            int[] parents = this.parentLinks();
            int parent = parents[target];

            if (this.isValid(parent) && this.nodes.get(parent).type == ModelNodeType.TABBED)
            {
                // Target is already in a tabbed node, just append
                ModelNode parentNode = this.nodes.get(parent);
                parentNode.tabs.add(dropped);
                parentNode.activeTab = parentNode.tabs.size() - 1;
            }
            else
            {
                // Create a new tabbed node
                List<Integer> tabIds = new ArrayList<>();
                tabIds.add(target);
                tabIds.add(dropped);
                int tabbed = this.addTabbed(tabIds, 1);

                if (!this.isValid(parent))
                {
                    this.root = tabbed;
                }
                else
                {
                    this.relink(parent, target, tabbed);
                }
            }

            return true;
        }

        private int addSplitter(boolean horizontal, float ratio, int first, int second)
        {
            this.nodes.add(ModelNode.splitter(horizontal, ratio, first, second));
            return this.nodes.size() - 1;
        }

        private int findPanel(String panelId)
        {
            for (int i = 0; i < this.nodes.size(); i++)
            {
                ModelNode node = this.nodes.get(i);
                if (node.type == ModelNodeType.PANEL && panelId.equals(node.panelId))
                {
                    return i;
                }
            }

            return -1;
        }

        private int[] parentLinks()
        {
            int[] parents = new int[this.nodes.size()];
            for (int i = 0; i < parents.length; i++)
            {
                parents[i] = -1;
            }

            for (int i = 0; i < this.nodes.size(); i++)
            {
                ModelNode node = this.nodes.get(i);
                if (node.type == ModelNodeType.SPLITTER)
                {
                    if (this.isValid(node.first)) parents[node.first] = i;
                    if (this.isValid(node.second)) parents[node.second] = i;
                }
                else if (node.type == ModelNodeType.TABBED)
                {
                    for (int tab : node.tabs)
                    {
                        if (this.isValid(tab)) parents[tab] = i;
                    }
                }
            }

            return parents;
        }

        private void relink(int parent, int oldChild, int newChild)
        {
            ModelNode node = this.nodes.get(parent);
            if (node.type == ModelNodeType.SPLITTER)
            {
                if (node.first == oldChild) node.first = newChild;
                else if (node.second == oldChild) node.second = newChild;
            }
            else if (node.type == ModelNodeType.TABBED)
            {
                int idx = node.tabs.indexOf(oldChild);
                if (idx >= 0) node.tabs.set(idx, newChild);
            }
        }

        private boolean isValid(int index)
        {
            return index >= 0 && index < this.nodes.size();
        }

        private static class BuildCursor
        {
            private final EditorLayoutNode node;
            private final boolean expanded;

            private BuildCursor(EditorLayoutNode node, boolean expanded)
            {
                this.node = node;
                this.expanded = expanded;
            }
        }

        private static class IndexCursor
        {
            private final int index;
            private final boolean expanded;

            private IndexCursor(int index, boolean expanded)
            {
                this.index = index;
                this.expanded = expanded;
            }
        }

        private static class BoundsCursor
        {
            private final int index;
            private final float x;
            private final float y;
            private final float w;
            private final float h;

            private BoundsCursor(int index, float x, float y, float w, float h)
            {
                this.index = index;
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
            }
        }
    }

    private static class Placement
    {
        private final boolean horizontal;
        private final boolean droppedFirst;

        private Placement(boolean horizontal, boolean droppedFirst)
        {
            this.horizontal = horizontal;
            this.droppedFirst = droppedFirst;
        }

        private static Placement fromEdge(int edge)
        {
            switch (edge)
            {
                case EDGE_LEFT:
                    return new Placement(false, true);
                case EDGE_RIGHT:
                    return new Placement(false, false);
                case EDGE_TOP:
                    return new Placement(true, true);
                case EDGE_BOTTOM:
                    return new Placement(true, false);
                default:
                    return null;
            }
        }
    }

    private enum ModelNodeType
    {
        PANEL,
        SPLITTER,
        TABBED
    }

    private static class ModelNode
    {
        private final ModelNodeType type;
        private String panelId;
        private boolean horizontal;
        private float ratio;
        private int first;
        private int second;

                private List<Integer> tabs;
        private int activeTab;

        private ModelNode(ModelNodeType type)
        {
            this.type = type;
        }

        private static ModelNode panel(String panelId)
        {
            ModelNode node = new ModelNode(ModelNodeType.PANEL);
            node.panelId = panelId;
            return node;
        }

        private static ModelNode splitter(boolean horizontal, float ratio, int first, int second)
        {
            ModelNode node = new ModelNode(ModelNodeType.SPLITTER);
            node.horizontal = horizontal;
            node.ratio = MathUtils.clamp(ratio, 0.05F, 0.95F);
            node.first = first;
            node.second = second;
            return node;
        }

        private static ModelNode tabbed(List<Integer> tabs, int activeTab)
        {
            ModelNode node = new ModelNode(ModelNodeType.TABBED);
            node.tabs = new ArrayList<>(tabs);
            node.activeTab = activeTab;
            return node;
        }
    }

    public static class SplitterNode extends EditorLayoutNode
    {
        private final boolean horizontal;
        private float ratio;
        private final EditorLayoutNode first;
        private final EditorLayoutNode second;

        public SplitterNode(boolean horizontal, float ratio, EditorLayoutNode first, EditorLayoutNode second)
        {
            this.horizontal = horizontal;
            this.ratio = MathUtils.clamp(ratio, 0.05F, 0.95F);
            this.first = first;
            this.second = second;
        }

        public boolean isHorizontal()
        {
            return this.horizontal;
        }

        public float getRatio()
        {
            return this.ratio;
        }

        public void setRatio(float ratio)
        {
            this.ratio = MathUtils.clamp(ratio, 0.05F, 0.95F);
        }

        public EditorLayoutNode getFirst()
        {
            return this.first;
        }

        public EditorLayoutNode getSecond()
        {
            return this.second;
        }

        @Override
        public BaseType toData()
        {
            MapType map = new MapType();
            map.putString("type", TYPE_SPLITTER);
            map.putString("dir", this.horizontal ? DIR_H : DIR_V);
            map.putFloat("ratio", this.ratio);
            map.put("first", this.first.toData());
            map.put("second", this.second.toData());
            return map;
        }

        @Override
        public void computeBounds(float x, float y, float w, float h, Map<String, float[]> out)
        {
            LayoutModel.fromRoot(this).fillBounds(x, y, w, h, out);
        }

        @Override
        public EditorLayoutNode copyWithSwappedIds(String id1, String id2)
        {
            LayoutModel model = LayoutModel.fromRoot(this);
            if (!model.swapPanelIds(id1, id2))
            {
                return this;
            }

            EditorLayoutNode rebuilt = model.toRoot();
            return rebuilt == null ? this : rebuilt;
        }
    }

    public static class PanelNode extends EditorLayoutNode
    {
        private final String panelId;

        public PanelNode(String panelId)
        {
            this.panelId = panelId;
        }

        public String getPanelId()
        {
            return this.panelId;
        }

        @Override
        public BaseType toData()
        {
            MapType map = new MapType();
            map.putString("type", TYPE_PANEL);
            map.putString("id", this.panelId);
            return map;
        }

        @Override
        public void computeBounds(float x, float y, float w, float h, Map<String, float[]> out)
        {
            out.put(this.panelId, new float[] {x, y, w, h});
        }

        @Override
        public EditorLayoutNode copyWithSwappedIds(String id1, String id2)
        {
            if (this.panelId.equals(id1))
            {
                return new PanelNode(id2);
            }

            if (this.panelId.equals(id2))
            {
                return new PanelNode(id1);
            }

            return this;
        }
    }

    public static class TabbedNode extends EditorLayoutNode
    {
        public final List<EditorLayoutNode> tabs;
        public int activeTab;

        public TabbedNode(List<EditorLayoutNode> tabs, int activeTab)
        {
            this.tabs = new ArrayList<>(tabs);
            this.activeTab = Math.max(0, Math.min(activeTab, Math.max(0, this.tabs.size() - 1)));
        }

        @Override
        public BaseType toData()
        {
            MapType map = new MapType();
            map.putString("type", TYPE_TABBED);
            map.putInt("active_tab", this.activeTab);
            ListType list = new ListType();
            for (EditorLayoutNode tab : this.tabs)
            {
                list.add(tab.toData());
            }
            map.put("tabs", list);
            return map;
        }

        @Override
        public void computeBounds(float x, float y, float w, float h, Map<String, float[]> out)
        {
            LayoutModel.fromRoot(this).fillBounds(x, y, w, h, out);
        }

        @Override
        public EditorLayoutNode copyWithSwappedIds(String id1, String id2)
        {
            LayoutModel model = LayoutModel.fromRoot(this);
            if (!model.swapPanelIds(id1, id2))
            {
                return this;
            }

            EditorLayoutNode rebuilt = model.toRoot();
            return rebuilt == null ? this : rebuilt;
        }
    }
}
