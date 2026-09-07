package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ValueEditorLayout extends BaseValue
{
    /* private boolean horizontal;
    private boolean layoutLocked;
    private boolean mainOnTop;
    private boolean mainOnLeft; */
    public static final int LAYOUT_HORIZONTAL_BOTTOM = 0;
    public static final int LAYOUT_HORIZONTAL_TOP = 1;
    public static final int LAYOUT_VERTICAL_LEFT = 2;
    public static final int LAYOUT_VERTICAL_RIGHT = 3;
    public static final int LAYOUT_VERTICAL_MIDDLE = 4;

    private int layout = LAYOUT_HORIZONTAL_BOTTOM;
    private boolean layoutLocked;
    private float mainSizeH = 0.66F;
    private float mainSizeV = 0.66F;
    private float editorSizeH = 0.5F;
    private float editorSizeV = 0.5F;
    private EditorLayoutNode particleLayoutRoot = EditorLayoutNode.defaultParticleLayout();
    private final List<EditorLayoutNode.SplitterNode> particleSplitters = new ArrayList<>();
    private float stateEditorSizeH = 0.7F;
    private float stateEditorSizeV = 0.25F;
    private boolean middleLayoutInverted;
    private boolean verticalLayoutInverted;
    private boolean horizontalLayoutInverted;
    private EditorLayoutNode filmLayoutRoot = EditorLayoutNode.defaultFilmLayout();
    private final List<EditorLayoutNode.SplitterNode> filmSplitters = new ArrayList<>();
    /* private float newFilmSidebarSize = 0.25F;
    private float newFilmMainSizeH = 0.5F;
    private int filmLayoutMode; */

    public ValueEditorLayout(String id)
    {
        super(id);
        this.rebuildFilmSplitters();
        this.rebuildParticleSplitters();
    }

    public EditorLayoutNode getFilmLayoutRoot()
    {
        return this.filmLayoutRoot;
    }

    public void setFilmLayoutRoot(EditorLayoutNode root)
    {
        BaseValue.edit(this, (v) ->
        {
            this.filmLayoutRoot = root == null ? EditorLayoutNode.defaultFilmLayout() : root;
            this.rebuildFilmSplitters();
        });
    }

    public List<EditorLayoutNode.SplitterNode> getFilmSplitters()
    {
        return this.filmSplitters;
    }

    public void syncFilmSplittersFromRoot(EditorLayoutNode root)
    {
        this.filmSplitters.clear();

        if (root != null)
        {
            EditorLayoutNode.collectSplitters(root, this.filmSplitters);

            List<EditorLayoutNode.SplitterHandleInfo> handles = new ArrayList<>();
            EditorLayoutNode.computeSplitterHandles(root, 0F, 0F, 1F, 1F, handles);

            if (this.filmSplitters.size() > handles.size())
            {
                this.filmSplitters.subList(handles.size(), this.filmSplitters.size()).clear();
            }
        }
    }

    public void setFilmSplitterRatio(int index, float ratio)
    {
        if (index < 0 || index >= this.filmSplitters.size())
        {
            return;
        }

        BaseValue.edit(this, (v) -> this.filmSplitters.get(index).setRatio(ratio));
    }

    public void setHorizontal(boolean horizontal)
    {
        /* BaseValue.edit(this, (v) -> this.horizontal = horizontal); */
    }

    public void setLayout(int layout)
    {
        BaseValue.edit(this, (v) -> this.layout = clampLayout(layout));
    }

    public int getLayout()
    {
        return this.layout;
    }

    public void setLayoutLocked(boolean locked)
    {
        BaseValue.edit(this, (v) -> this.layoutLocked = locked);
    }

    public boolean isLayoutLocked()
    {
        return this.layoutLocked;
    }

    /* public void setLayoutLocked(boolean layoutLocked)
    {
        BaseValue.edit(this, (v) -> this.layoutLocked = layoutLocked);
    } */

    /* public void setMainOnTop(boolean mainOnTop)
    {
        BaseValue.edit(this, (v) -> this.mainOnTop = mainOnTop);
    } */

    /* public void setMainOnLeft(boolean mainOnLeft)
    {
        BaseValue.edit(this, (v) -> this.mainOnLeft = mainOnLeft);
    } */

    public void setMainSizeH(float mainSizeH)
    {
        BaseValue.edit(this, (v) -> this.mainSizeH = mainSizeH);
    }

    public void setMainSizeV(float mainSizeV)
    {
        BaseValue.edit(this, (v) -> this.mainSizeV = mainSizeV);
    }

    public void setEditorSizeH(float editorSizeH)
    {
        BaseValue.edit(this, (v) -> this.editorSizeH = editorSizeH);
    }

    public void setEditorSizeV(float editorSizeV)
    {
        BaseValue.edit(this, (v) -> this.editorSizeV = editorSizeV);
    }

    /* public void setNewFilmSidebarSize(float sidebarSize)
    {
        BaseValue.edit(this, (v) -> this.newFilmSidebarSize = sidebarSize);
    }

    public void setNewFilmMainSizeH(float mainSizeH)
    {
        BaseValue.edit(this, (v) -> this.newFilmMainSizeH = mainSizeH);
    }

    public void setFilmLayoutMode(int filmLayoutMode)
    {
        BaseValue.edit(this, (v) -> this.filmLayoutMode = filmLayoutMode);
    } */

    public void setStateEditorSizeH(float editorSizeH)
    {
        BaseValue.edit(this, (v) -> this.stateEditorSizeH = editorSizeH);
    }

    public void setStateEditorSizeV(float editorSizeV)
    {
        BaseValue.edit(this, (v) -> this.stateEditorSizeV = editorSizeV);
    }

    public boolean isHorizontal()
    {
        return this.layout == LAYOUT_HORIZONTAL_BOTTOM || this.layout == LAYOUT_HORIZONTAL_TOP;
        //return this.horizontal;
    }

    /* public boolean isLayoutLocked()
    {
        return this.layoutLocked;
    } */

    public boolean isMainOnTop()
    {
        return this.layout == LAYOUT_HORIZONTAL_TOP;
        //return this.mainOnTop;
    }

    public boolean isMainOnLeft()
    {
        return this.layout == LAYOUT_VERTICAL_LEFT || this.layout == LAYOUT_VERTICAL_MIDDLE;
        //return this.mainOnLeft;
    }

    public boolean isMiddleLayout()
    {
        return this.layout == LAYOUT_VERTICAL_MIDDLE;
        
    }

    public float getMainSizeH()
    {
        return this.mainSizeH;
    }

    public float getMainSizeV()
    {
        return this.mainSizeV;
    }

    public float getEditorSizeH()
    {
        return this.editorSizeH;
    }

    public float getEditorSizeV()
    {
        return this.editorSizeV;
    }

    /* public float getNewFilmSidebarSize()
    {
        return MathUtils.clamp(this.newFilmSidebarSize, 0.1F, 0.6F);
    }

    public float getNewFilmMainSizeH()
    {
        return MathUtils.clamp(this.newFilmMainSizeH, 0.15F, 0.7F);
    }

    public int getFilmLayoutMode()
    {
        return MathUtils.clamp(this.filmLayoutMode, 0, 2);
    } */

    public float getStateEditorSizeH()
    {
        return MathUtils.clamp(this.stateEditorSizeH, 0.1F, 0.9F);
    }

    public float getStateEditorSizeV()
    {
        return MathUtils.clamp(this.stateEditorSizeV, 0.1F, 0.9F);
    }

    public void setMiddleLayoutInverted(boolean middleLayoutInverted)
    {
        BaseValue.edit(this, (v) -> this.middleLayoutInverted = middleLayoutInverted);
    }

    public boolean isMiddleLayoutInverted()
    {
        return this.middleLayoutInverted;
    }

    public void setVerticalLayoutInverted(boolean verticalLayoutInverted)
    {
        BaseValue.edit(this, (v) -> this.verticalLayoutInverted = verticalLayoutInverted);
    }

    public boolean isVerticalLayoutInverted()
    {
        return this.verticalLayoutInverted;
    }

    public EditorLayoutNode buildFilmLayoutFromLegacyState()
    {
        float mainH = this.clampRatio(this.mainSizeH);
        float mainV = this.clampRatio(this.mainSizeV);
        float editorH = this.clampRatio(this.editorSizeH);
        float editorV = this.clampRatio(this.editorSizeV);
        EditorLayoutNode panelMain = this.panel("main");
        EditorLayoutNode panelPreview = this.panel("preview");
        EditorLayoutNode panelEdit = this.panel("editArea");

        switch (this.layout)
        {
            case LAYOUT_HORIZONTAL_TOP:
                return this.layoutHorizontalTop(mainH, editorH, panelMain, panelPreview, panelEdit);

            case LAYOUT_HORIZONTAL_BOTTOM:
                return this.layoutHorizontalBottom(mainH, editorH, panelMain, panelPreview, panelEdit);

            case LAYOUT_VERTICAL_LEFT:
                return this.layoutVerticalLeft(mainV, editorV, panelMain, panelPreview, panelEdit);

            case LAYOUT_VERTICAL_RIGHT:
                return this.layoutVerticalRight(mainV, editorV, panelMain, panelPreview, panelEdit);

            case LAYOUT_VERTICAL_MIDDLE:
                return this.layoutVerticalMiddle(mainV, editorH, panelMain, panelPreview, panelEdit);

            default:
                return this.layoutHorizontalBottom(mainH, editorH, panelMain, panelPreview, panelEdit);
        }
    }

    private float clampRatio(float value)
    {
        return MathUtils.clamp(value, 0.05F, 0.95F);
    }

    private EditorLayoutNode panel(String id)
    {
        return new EditorLayoutNode.PanelNode(id);
    }

    private EditorLayoutNode split(boolean horizontal, float ratio, EditorLayoutNode first, EditorLayoutNode second)
    {
        return new EditorLayoutNode.SplitterNode(horizontal, this.clampRatio(ratio), first, second);
    }

    private EditorLayoutNode layoutHorizontalTop(float mainH, float editorH, EditorLayoutNode panelMain, EditorLayoutNode panelPreview, EditorLayoutNode panelEdit)
    {
        EditorLayoutNode lowerBand = this.split(false, 1F - editorH, panelPreview, panelEdit);
        return this.split(true, mainH, panelMain, lowerBand);
    }

    private EditorLayoutNode layoutHorizontalBottom(float mainH, float editorH, EditorLayoutNode panelMain, EditorLayoutNode panelPreview, EditorLayoutNode panelEdit)
    {
        EditorLayoutNode leftPanel = this.verticalLayoutInverted ? panelEdit : panelPreview;
        EditorLayoutNode rightPanel = this.verticalLayoutInverted ? panelPreview : panelEdit;
        EditorLayoutNode upperBand = this.split(false, 1F - editorH, leftPanel, rightPanel);

        return this.split(true, 1F - mainH, upperBand, panelMain);
    }

    private EditorLayoutNode layoutVerticalLeft(float mainV, float editorV, EditorLayoutNode panelMain, EditorLayoutNode panelPreview, EditorLayoutNode panelEdit)
    {
        EditorLayoutNode rightColumn = this.split(true, editorV, panelPreview, panelEdit);

        if (this.verticalLayoutInverted)
        {
            return this.split(false, 1F - mainV, rightColumn, panelMain);
        }

        return this.split(false, mainV, panelMain, rightColumn);
    }

    private EditorLayoutNode layoutVerticalRight(float mainV, float editorV, EditorLayoutNode panelMain, EditorLayoutNode panelPreview, EditorLayoutNode panelEdit)
    {
        EditorLayoutNode rightColumn = this.split(true, editorV, panelMain, panelEdit);
        float previewWidth = 1F - mainV;

        if (this.verticalLayoutInverted)
        {
            return this.split(false, previewWidth, rightColumn, panelPreview);
        }

        return this.split(false, previewWidth, panelPreview, rightColumn);
    }

    private EditorLayoutNode layoutVerticalMiddle(float mainV, float editorH, EditorLayoutNode panelMain, EditorLayoutNode panelPreview, EditorLayoutNode panelEdit)
    {
        float topWidth = mainV;
        float rightTopWidth = editorH;

        if (topWidth + rightTopWidth > 0.95F)
        {
            rightTopWidth = 0.95F - topWidth;
            if (rightTopWidth < 0.05F)
            {
                rightTopWidth = 0.05F;
                topWidth = 0.95F - rightTopWidth;
            }
        }

        float rightColumnWidth = Math.max(0.05F, 1F - topWidth);
        float rightRatio = this.clampRatio(rightTopWidth / rightColumnWidth);
        EditorLayoutNode trailing = this.middleLayoutInverted
            ? this.split(false, rightRatio, panelMain, panelPreview)
            : this.split(false, rightRatio, panelMain, panelEdit);

        if (this.middleLayoutInverted)
        {
            return this.split(false, topWidth, panelEdit, trailing);
        }

        return this.split(false, topWidth, panelPreview, trailing);
    }

    public void setHorizontalLayoutInverted(boolean horizontalLayoutInverted)
    {
        BaseValue.edit(this, (v) -> this.horizontalLayoutInverted = horizontalLayoutInverted);
    }

    public boolean isHorizontalLayoutInverted()
    {
        return this.horizontalLayoutInverted;
    }

    /* Particle editor layout (separate tree from the film editors). */

    public EditorLayoutNode getParticleLayoutRoot()
    {
        return this.particleLayoutRoot;
    }

    public void setParticleLayoutRoot(EditorLayoutNode root)
    {
        BaseValue.edit(this, (v) ->
        {
            this.particleLayoutRoot = root == null ? EditorLayoutNode.defaultParticleLayout() : root;
            this.rebuildParticleSplitters();
        });
    }

    public List<EditorLayoutNode.SplitterNode> getParticleSplitters()
    {
        return this.particleSplitters;
    }

    public List<EditorLayoutNode.SplitterNode> getParticleSplittersForWrite()
    {
        return this.particleSplitters;
    }

    public void setParticleSplitterRatio(int index, float ratio)
    {
        if (index < 0 || index >= this.particleSplitters.size())
        {
            return;
        }
        int i = index;
        BaseValue.edit(this, (v) -> this.particleSplitters.get(i).setRatio(MathUtils.clamp(ratio, 0.05F, 0.95F)));
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        // data.putBool("horizontal", this.horizontal);
        data.putInt("layout", this.layout);
        data.putBool("horizontal", this.isHorizontal());
        data.putBool("layout_locked", this.layoutLocked);
        /* data.putBool("main_on_top", this.mainOnTop);
        data.putBool("main_on_left", this.mainOnLeft); */
        data.putFloat("main_size_h", this.mainSizeH);
        data.putFloat("main_size_v", this.mainSizeV);
        data.putFloat("editor_size_h", this.editorSizeH);
        data.putFloat("editor_size_v", this.editorSizeV);
        data.putFloat("state_editor_size_h", this.stateEditorSizeH);
        data.putFloat("state_editor_size_v", this.stateEditorSizeV);
        data.putBool("middle_layout_inverted", this.middleLayoutInverted);
        data.putBool("vertical_layout_inverted", this.verticalLayoutInverted);
        data.putBool("horizontal_layout_inverted", this.horizontalLayoutInverted);
        data.put("film_layout", this.filmLayoutRoot.toData());
        data.put("particle_layout", this.particleLayoutRoot.toData());
        /* data.putFloat("new_film_sidebar_size", this.newFilmSidebarSize);
        data.putFloat("new_film_main_size_h", this.newFilmMainSizeH);
        data.putInt("film_layout_mode", this.filmLayoutMode); */

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        if (data.isMap())
        {
            MapType map = data.asMap();

            if (map.has("layout"))
            {
                this.layout = clampLayout(map.getInt("layout"));
            }
            else
            {
                this.layout = map.getBool("horizontal") ? LAYOUT_HORIZONTAL_BOTTOM : LAYOUT_VERTICAL_LEFT;
            }
            this.layoutLocked = map.getBool("layout_locked", false);

            /* this.horizontal = map.getBool("horizontal");
            this.layoutLocked = map.getBool("layout_locked");
            this.mainOnTop = map.getBool("main_on_top");
            this.mainOnLeft = map.getBool("main_on_left"); */
            this.mainSizeH = map.getFloat("main_size_h", 0.66F);
            this.mainSizeV = map.getFloat("main_size_v", 0.66F);
            this.editorSizeH = map.getFloat("editor_size_h", 0.5F);
            this.editorSizeV = map.getFloat("editor_size_v", 0.5F);
            this.stateEditorSizeH = map.getFloat("state_editor_size_h", 0.7F);
            this.stateEditorSizeV = map.getFloat("state_editor_size_v", 0.25F);
            this.middleLayoutInverted = map.getBool("middle_layout_inverted", false);
            this.verticalLayoutInverted = map.getBool("vertical_layout_inverted", false);
            this.horizontalLayoutInverted = map.getBool("horizontal_layout_inverted", false);

            if (map.has("film_layout"))
            {
                this.filmLayoutRoot = EditorLayoutNode.fromData(map.get("film_layout"));
            }
            else
            {
                this.filmLayoutRoot = this.buildFilmLayoutFromLegacyState();
            }
            this.rebuildFilmSplitters();

            if (map.has("particle_layout"))
            {
                this.particleLayoutRoot = EditorLayoutNode.fromData(map.get("particle_layout"));
            }
            else
            {
                this.particleLayoutRoot = EditorLayoutNode.defaultParticleLayout();
            }
            this.rebuildParticleSplitters();
            /* this.newFilmSidebarSize = map.getFloat("new_film_sidebar_size", 0.25F);
            this.newFilmMainSizeH = map.getFloat("new_film_main_size_h", 0.5F);
            this.filmLayoutMode = map.getInt("film_layout_mode", 0); */
        }
    }

    private void rebuildFilmSplitters()
    {
        if (this.filmLayoutRoot == null)
        {
            this.filmLayoutRoot = EditorLayoutNode.defaultFilmLayout();
        }

        this.filmSplitters.clear();
        EditorLayoutNode.collectSplitters(this.filmLayoutRoot, this.filmSplitters);
    }

    private int clampLayout(int layout)
    {
        //if (layout < LAYOUT_HORIZONTAL_BOTTOM || layout > LAYOUT_VERTICAL_RIGHT)
        if (layout < LAYOUT_HORIZONTAL_BOTTOM || layout > LAYOUT_VERTICAL_MIDDLE)
        {
            return LAYOUT_HORIZONTAL_BOTTOM;
        }

        return layout;
    }

    private void rebuildParticleSplitters()
    {
        if (this.particleLayoutRoot == null)
        {
            this.particleLayoutRoot = EditorLayoutNode.defaultParticleLayout();
        }

        this.particleSplitters.clear();
        EditorLayoutNode.collectSplitters(this.particleLayoutRoot, this.particleSplitters);
    }
}
