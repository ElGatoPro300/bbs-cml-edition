package mchorse.bbs_mod.ui.model;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.JointLimitConfig;
import mchorse.bbs_mod.cubic.constraints.JointLimitEnforcer;
import mchorse.bbs_mod.cubic.constraints.JointLimitSerializer;
import mchorse.bbs_mod.cubic.model.ModelConfig;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Joint-limit constraints editor for the model panel.
 *
 * LEFT: skeleton bone hierarchy. Enabling a bone stores an angular limit entry.
 * RIGHT: min/max X/Y/Z trackpads plus optional apply-to-children.
 */
public class UIModelConstraintsPanel extends UIElement
{
    private static final int SIDE_MARGIN = 10;
    private static final int LEFT_WIDTH = 220;
    private static final int RIGHT_WIDTH = 260;

    private static final float DEFAULT_LOWER = -180F;
    private static final float DEFAULT_UPPER = 180F;

    private final UIStringList boneList;
    private final UIScrollView detailScroll;
    private final UILabel noSelectionLabel;
    private final UILabel boneNameLabel;

    private final UIToggle activeToggle;
    private final UITrackpad minXPad;
    private final UITrackpad minYPad;
    private final UITrackpad minZPad;
    private final UITrackpad maxXPad;
    private final UITrackpad maxYPad;
    private final UITrackpad maxZPad;
    private final UIButton applyChildrenButton;

    private final IUIModelPanelHost editor;
    private ModelConfig config;
    private final Map<String, JointLimitData> joints = new HashMap<>();
    private final List<String> boneNames = new ArrayList<>();
    private String selectedBone;
    private boolean suppressCommit;

    public UIModelConstraintsPanel(IUIModelPanelHost editor)
    {
        this.editor = editor;
        this.relative(editor.getMainView()).w(1F).h(1F);

        UILabel listTitle = UI.label(UIKeys.MODELS_CONSTRAINTS_EDITOR).background();
        listTitle.relative(this).x(SIDE_MARGIN).y(10).w(LEFT_WIDTH).h(12);

        this.boneList = new UIStringList((items) ->
        {
            if (items != null && !items.isEmpty())
            {
                this.selectBone(this.boneFromDisplay(items.get(0)));
            }
        });
        this.boneList.relative(this)
            .x(SIDE_MARGIN).y(26)
            .w(LEFT_WIDTH).h(1F, -36);
        this.boneList.background();
        this.boneList.scroll.scrollItemSize = 18;

        UILabel editorTitle = UI.label(UIKeys.MODELS_CONSTRAINTS_EDITOR).background();
        editorTitle.relative(this)
            .x(1F, -RIGHT_WIDTH - SIDE_MARGIN).y(10)
            .w(RIGHT_WIDTH).h(12);

        this.boneNameLabel = UI.label(IKey.raw("-"));
        this.boneNameLabel.relative(editorTitle).y(1F, 4).w(1F).h(12);

        this.noSelectionLabel = UI.label(UIKeys.MODELS_CONSTRAINTS_NO_SELECTION);
        this.noSelectionLabel.relative(this)
            .x(1F, -RIGHT_WIDTH - SIDE_MARGIN)
            .y(38)
            .w(RIGHT_WIDTH).h(20);

        this.detailScroll = UI.scrollView(20, 8);
        this.detailScroll.scroll.cancelScrolling().opposite().scrollSpeed *= 3;
        this.detailScroll.relative(this)
            .x(1F, -RIGHT_WIDTH - SIDE_MARGIN).y(38)
            .w(RIGHT_WIDTH).h(1F, -48);

        UIElement fields = new UIElement();
        fields.relative(this.detailScroll).w(1F);
        fields.column().stretch().vertical().height(20).padding(4);

        this.activeToggle = new UIToggle(UIKeys.MODELS_CONSTRAINTS_ACTIVE, (b) -> this.onActiveChanged(b.getValue()));

        this.minXPad = this.buildPad((v) -> this.updateLimit((d) -> d.minX = v.floatValue()), -180D, 180D);
        this.minYPad = this.buildPad((v) -> this.updateLimit((d) -> d.minY = v.floatValue()), -180D, 180D);
        this.minZPad = this.buildPad((v) -> this.updateLimit((d) -> d.minZ = v.floatValue()), -180D, 180D);
        this.maxXPad = this.buildPad((v) -> this.updateLimit((d) -> d.maxX = v.floatValue()), -180D, 180D);
        this.maxYPad = this.buildPad((v) -> this.updateLimit((d) -> d.maxY = v.floatValue()), -180D, 180D);
        this.maxZPad = this.buildPad((v) -> this.updateLimit((d) -> d.maxZ = v.floatValue()), -180D, 180D);

        this.applyChildrenButton = new UIButton(UIKeys.MODELS_PHYS_BONES_APPLY_DESCENDANTS, (b) -> this.applyLimitsToChildren());
        this.applyChildrenButton.tooltip(UIKeys.MODELS_PHYS_BONES_APPLY_DESCENDANTS_TOOLTIP);

        fields.add(
            this.activeToggle,
            UI.label(UIKeys.MODELS_CONSTRAINTS_MIN),
            UI.row(this.minXPad, this.minYPad, this.minZPad),
            UI.label(UIKeys.MODELS_CONSTRAINTS_MAX),
            UI.row(this.maxXPad, this.maxYPad, this.maxZPad),
            this.applyChildrenButton
        );
        this.detailScroll.add(fields);

        this.add(listTitle, this.boneList);
        this.add(editorTitle, this.boneNameLabel, this.noSelectionLabel, this.detailScroll);

        this.setDetailVisible(false);
    }

    private UITrackpad buildPad(Consumer<Double> callback, double min, double max)
    {
        UITrackpad pad = new UITrackpad((v) ->
        {
            if (!this.suppressCommit)
            {
                callback.accept(v.doubleValue());
            }
        });
        pad.limit(min, max).values(1D, 0.1D, 5D);

        return pad;
    }

    private void updateLimit(Consumer<JointLimitData> mutator)
    {
        JointLimitData data = this.getOrCreateSelected();

        if (data != null)
        {
            mutator.accept(data);
            this.commitChanges();
        }
    }

    public void onBoneSelected(String bone)
    {
        if (bone == null || bone.isEmpty() || this.config == null)
        {
            return;
        }

        this.boneList.setCurrent(this.displayName(bone));
        this.selectBone(bone);
    }

    private void onActiveChanged(boolean active)
    {
        if (this.suppressCommit || this.selectedBone == null || this.selectedBone.isEmpty())
        {
            return;
        }

        if (active)
        {
            this.joints.putIfAbsent(this.selectedBone, JointLimitData.createDefault());
        }
        else
        {
            this.joints.remove(this.selectedBone);
        }

        this.refreshBoneList();
        this.refreshDetailFields();
        this.commitChanges();
    }

    private JointLimitData getSelectedData()
    {
        if (this.selectedBone == null)
        {
            return null;
        }

        return this.joints.get(this.selectedBone);
    }

    private JointLimitData getOrCreateSelected()
    {
        if (this.selectedBone == null || this.selectedBone.isEmpty())
        {
            return null;
        }

        JointLimitData data = this.joints.get(this.selectedBone);

        if (data == null)
        {
            data = JointLimitData.createDefault();
            this.joints.put(this.selectedBone, data);
            this.refreshBoneList();
            this.activeToggle.setValue(true);
        }

        return data;
    }

    private void selectBone(String bone)
    {
        this.selectedBone = bone;
        this.refreshDetailFields();
    }

    private void setDetailVisible(boolean visible)
    {
        this.detailScroll.setEnabled(visible);
        this.detailScroll.setVisible(visible);
        this.noSelectionLabel.setVisible(!visible);
        this.boneNameLabel.setVisible(visible);
    }

    private void refreshDetailFields()
    {
        if (this.selectedBone == null || this.selectedBone.isEmpty())
        {
            this.setDetailVisible(false);
            return;
        }

        this.setDetailVisible(true);
        this.boneNameLabel.label = IKey.raw(this.selectedBone);

        JointLimitData data = this.getSelectedData();
        boolean active = data != null;

        this.suppressCommit = true;

        try
        {
            this.activeToggle.setValue(active);

            if (data != null)
            {
                this.minXPad.setValue(data.minX);
                this.minYPad.setValue(data.minY);
                this.minZPad.setValue(data.minZ);
                this.maxXPad.setValue(data.maxX);
                this.maxYPad.setValue(data.maxY);
                this.maxZPad.setValue(data.maxZ);
            }
            else
            {
                this.minXPad.setValue(DEFAULT_LOWER);
                this.minYPad.setValue(DEFAULT_LOWER);
                this.minZPad.setValue(DEFAULT_LOWER);
                this.maxXPad.setValue(DEFAULT_UPPER);
                this.maxYPad.setValue(DEFAULT_UPPER);
                this.maxZPad.setValue(DEFAULT_UPPER);
            }
        }
        finally
        {
            this.suppressCommit = false;
        }

        this.minXPad.setEnabled(active);
        this.minYPad.setEnabled(active);
        this.minZPad.setEnabled(active);
        this.maxXPad.setEnabled(active);
        this.maxYPad.setEnabled(active);
        this.maxZPad.setEnabled(active);
        this.applyChildrenButton.setEnabled(active);
    }

    private void refreshBoneList()
    {
        List<String> display = new ArrayList<>();

        for (String bone : this.boneNames)
        {
            display.add(this.displayName(bone));
        }

        String current = this.selectedBone == null ? null : this.displayName(this.selectedBone);

        this.boneList.setList(display);
        this.boneList.update();

        if (current != null)
        {
            this.boneList.setCurrent(current);
        }
    }

    private String displayName(String bone)
    {
        return this.joints.containsKey(bone) ? "* " + bone : bone;
    }

    private String boneFromDisplay(String display)
    {
        if (display != null && display.startsWith("* "))
        {
            return display.substring(2);
        }

        return display;
    }

    private void applyLimitsToChildren()
    {
        JointLimitData selected = this.getSelectedData();

        if (this.config == null || selected == null || this.selectedBone == null)
        {
            return;
        }

        ModelInstance instance = this.editor.resolveEditingModel(this.config);

        if (instance == null || instance.getModel() == null)
        {
            return;
        }

        List<String> descendants = new ArrayList<>();
        instance.getModel().collectDescendants(this.selectedBone, descendants);

        /* collectDescendants includes the bone itself — skip index 0 */
        boolean changed = false;

        for (int i = 1; i < descendants.size(); i++)
        {
            String child = descendants.get(i);
            JointLimitData copy = JointLimitData.copyOf(selected);
            this.joints.put(child, copy);
            changed = true;
        }

        if (changed)
        {
            this.refreshBoneList();
            this.commitChanges();
        }
    }

    private void commitChanges()
    {
        if (this.suppressCommit || this.config == null)
        {
            return;
        }

        Map<String, JointLimitConfig.JointLimit> out = new HashMap<>();

        for (Map.Entry<String, JointLimitData> entry : this.joints.entrySet())
        {
            JointLimitData data = entry.getValue();

            out.put(entry.getKey(), new JointLimitConfig.JointLimit(
                true,
                data.minX,
                data.minY,
                data.minZ,
                data.maxX,
                data.maxY,
                data.maxZ
            ));
        }

        JointLimitConfig jointConfig = out.isEmpty() ? null : new JointLimitConfig(out);
        MapType map = JointLimitSerializer.serialize(jointConfig);

        this.config.constraints.set(out.isEmpty() ? null : map);
        this.editor.dirty();
        this.editor.getModelRenderer().syncAnimationsAndResetAnimator();
        JointLimitEnforcer.clearCache();
        this.editor.getModelRenderer().dirty();
    }

    private void ensureBoneNames()
    {
        if (this.config == null || !this.boneNames.isEmpty())
        {
            return;
        }

        ModelInstance instance = this.editor.resolveEditingModel(this.config);

        if (instance != null && instance.getModel() != null)
        {
            this.boneNames.addAll(instance.getModel().getGroupKeysInHierarchyOrder());
            this.refreshBoneList();
        }
    }

    public void setConfig(ModelConfig config)
    {
        this.config = config;
        this.selectedBone = null;
        this.joints.clear();
        this.boneNames.clear();

        if (config != null)
        {
            BaseType raw = config.constraints.get();

            if (raw instanceof MapType)
            {
                JointLimitConfig jointConfig = JointLimitSerializer.deserialize((MapType) raw);

                if (jointConfig != null && jointConfig.joints() != null)
                {
                    for (Map.Entry<String, JointLimitConfig.JointLimit> entry : jointConfig.joints().entrySet())
                    {
                        this.joints.put(entry.getKey(), JointLimitData.fromLimit(entry.getValue()));
                    }
                }
            }

            ModelInstance instance = this.editor.resolveEditingModel(config);

            if (instance != null && instance.getModel() != null)
            {
                this.boneNames.addAll(instance.getModel().getGroupKeysInHierarchyOrder());
            }
        }

        this.refreshBoneList();
        this.setDetailVisible(false);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.config != null && this.boneNames.isEmpty())
        {
            this.ensureBoneNames();
        }

        int x = this.area.x;
        int y = this.area.y;
        int ey = this.area.ey();

        context.batcher.box(
            x + SIDE_MARGIN - 2, y + 6,
            x + SIDE_MARGIN + LEFT_WIDTH + 2, ey - 6,
            0xaa000000
        );

        int rx = x + this.area.w - SIDE_MARGIN - RIGHT_WIDTH;
        context.batcher.box(
            rx - 2, y + 6,
            rx + RIGHT_WIDTH + 2, ey - 6,
            0xaa000000
        );

        super.render(context);
    }

    private static class JointLimitData
    {
        float minX = DEFAULT_LOWER;
        float minY = DEFAULT_LOWER;
        float minZ = DEFAULT_LOWER;
        float maxX = DEFAULT_UPPER;
        float maxY = DEFAULT_UPPER;
        float maxZ = DEFAULT_UPPER;

        static JointLimitData createDefault()
        {
            return new JointLimitData();
        }

        static JointLimitData copyOf(JointLimitData source)
        {
            JointLimitData data = new JointLimitData();
            data.minX = source.minX;
            data.minY = source.minY;
            data.minZ = source.minZ;
            data.maxX = source.maxX;
            data.maxY = source.maxY;
            data.maxZ = source.maxZ;

            return data;
        }

        static JointLimitData fromLimit(JointLimitConfig.JointLimit limit)
        {
            JointLimitData data = new JointLimitData();
            data.minX = limit.minX();
            data.minY = limit.minY();
            data.minZ = limit.minZ();
            data.maxX = limit.maxX();
            data.maxY = limit.maxY();
            data.maxZ = limit.maxZ();

            return data;
        }
    }
}
