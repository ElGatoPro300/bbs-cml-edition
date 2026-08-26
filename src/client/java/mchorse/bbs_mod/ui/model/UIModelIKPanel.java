package mchorse.bbs_mod.ui.model;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.IKPresetManager;
import mchorse.bbs_mod.cubic.ik.LimbConstraintCompiler;
import mchorse.bbs_mod.cubic.ik.LimbConstraintDef;
import mchorse.bbs_mod.cubic.ik.LimbConstraintSerializer;
import mchorse.bbs_mod.cubic.model.ModelConfig;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Limb IK editor for the model panel.
 *
 * LEFT: skeleton bone hierarchy. Selecting a bone and enabling IK creates a tip entry.
 * RIGHT: limb properties for the selected tip bone. Centre stays free for the 3D viewport.
 */
public class UIModelIKPanel extends UIElement
{
    private static final int SIDE_MARGIN = 10;
    private static final int LEFT_WIDTH = 220;
    private static final int RIGHT_WIDTH = 260;

    private final UIStringList boneList;
    private final UIScrollView detailScroll;
    private final UILabel noSelectionLabel;
    private final UILabel boneNameLabel;

    private final UIToggle activeToggle;
    private final UIButton controllerButton;
    private final UITrackpad depthPad;
    private final UIToggle poleEnabledToggle;
    private final UIButton poleBoneButton;
    private final UITrackpad bendOffsetPad;
    private final UITrackpad flexibilityPad;
    private final UITrackpad influencePad;
    private final UIToggle orientTipToggle;
    private final UIToggle extensibleToggle;
    private final UIToggle classicToggle;
    private final UIToggle debugToggle;

    private final IUIModelPanelHost editor;
    private ModelConfig config;
    private final Map<String, LimbData> limbs = new HashMap<>();
    /** Per-bone joint freedom, held only so a save from this panel preserves it. */
    private final Map<String, LimbConstraintDef.JointDoF> joints = new HashMap<>();
    private final List<String> boneNames = new ArrayList<>();
    private String selectedBone;
    private boolean suppressCommit;

    public UIModelIKPanel(IUIModelPanelHost editor)
    {
        this.editor = editor;
        this.relative(editor.getMainView()).w(1F).h(1F);


        UILabel listTitle = UI.label(UIKeys.MODELS_IK_EDITOR).background();
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
            .w(LEFT_WIDTH).h(1F, -60);
        this.boneList.background();
        this.boneList.scroll.scrollItemSize = 18;
        /* Presets hang off the bone list's context menu, with no button of their own —
         * the same place BBS-FS puts them. */
        this.boneList.context(this::presetsMenu);

        /* The overlay's switch lives in the SETTINGS, not the model — it is a way of
         * looking at any rig, not part of one — so it sits under the bone list rather
         * than in the per-limb fields, and stays reachable with nothing selected.
         * Right-click it, or use the gear, for the overlay's look. */
        this.debugToggle = new UIToggle(IKey.raw("Show Debug"), (b) -> BBSSettings.ikDebug.enabled.set(b.getValue()));
        this.debugToggle.setValue(BBSSettings.ikDebug.enabled.get());
        this.debugToggle.tooltip(IKey.raw("Draw the IK bones over the model: the chain wires, its joints, and the controller and pole handles."));
        this.debugToggle.context(() -> new UIDebugOverlayContextMenu(BBSSettings.ikDebug));

        UIIcon debugSettings = new UIIcon(Icons.GEAR, (b) -> this.getContext().replaceContextMenu(new UIDebugOverlayContextMenu(BBSSettings.ikDebug)));

        debugSettings.tooltip(IKey.raw("Configure the overlay"));

        /* Anchored to the PANEL, not to the bone list: hung off the list it moved with
         * every list rebuild and only landed in place on the next resize, so loading a
         * preset made it vanish until you switched panels and back. */
        UIElement debugRow = UI.row(this.debugToggle, debugSettings);

        debugRow.relative(this).x(SIDE_MARGIN).y(1F, -30).w(LEFT_WIDTH).h(20);
        this.add(debugRow);

        UILabel editorTitle = UI.label(UIKeys.MODELS_IK_EDITOR).background();
        editorTitle.relative(this)
            .x(1F, -RIGHT_WIDTH - SIDE_MARGIN).y(10)
            .w(RIGHT_WIDTH).h(12);

        this.boneNameLabel = UI.label(IKey.raw("-"));
        this.boneNameLabel.relative(editorTitle).y(1F, 4).w(1F).h(12);

        this.noSelectionLabel = UI.label(UIKeys.MODELS_IK_NO_SELECTION);
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

        this.activeToggle = new UIToggle(UIKeys.MODELS_IK_ENABLED, (b) -> this.onActiveChanged(b.getValue()));
        this.activeToggle.tooltip(UIKeys.MODELS_IK_ENABLED_TOOLTIP);

        this.controllerButton = new UIButton(UIKeys.MODELS_IK_TARGET_BONE, (b) ->
            this.openBonePicker((bone) ->
            {
                LimbData data = this.getOrCreateSelected();

                if (data != null)
                {
                    data.controller = bone;
                    this.updateControllerLabel();
                    this.commitChanges();
                }
            })
        );
        this.controllerButton.tooltip(UIKeys.MODELS_IK_TARGET_BONE_TOOLTIP);

        this.depthPad = this.buildPad((v) ->
        {
            LimbData data = this.getSelectedData();

            if (data != null)
            {
                data.depth = v.intValue();
                this.commitChanges();
            }
        }, 0D, 32D, 1D, 1D, 5D);
        this.depthPad.integer();
        this.depthPad.tooltip(UIKeys.MODELS_IK_CHAIN_LENGTH_TOOLTIP);

        this.poleEnabledToggle = new UIToggle(UIKeys.MODELS_IK_POLE_ENABLED, (b) ->
        {
            LimbData data = this.getOrCreateSelected();

            if (data != null)
            {
                data.poleEnabled = b.getValue();
                this.commitChanges();
            }
        });

        this.poleBoneButton = new UIButton(UIKeys.MODELS_IK_POLE_BONE, (b) ->
            this.openBonePicker((bone) ->
            {
                LimbData data = this.getOrCreateSelected();

                if (data != null)
                {
                    data.poleBone = bone;
                    this.updatePoleBoneLabel();
                    this.commitChanges();
                }
            })
        );

        this.bendOffsetPad = this.buildPad((v) ->
        {
            LimbData data = this.getSelectedData();

            if (data != null)
            {
                data.bendOffset = v.floatValue();
                this.commitChanges();
            }
        }, -180D, 180D, 1D, 0.1D, 5D);

        this.flexibilityPad = this.buildPad((v) ->
        {
            LimbData data = this.getSelectedData();

            if (data != null)
            {
                data.flexibility = v.floatValue();
                this.commitChanges();
            }
        }, 0D, 1D, 0.01D, 0.001D, 0.05D);

        this.influencePad = this.buildPad((v) ->
        {
            LimbData data = this.getSelectedData();

            if (data != null)
            {
                data.influence = v.floatValue();
                this.commitChanges();
            }
        }, 0D, 1D, 0.05D, 0.01D, 0.1D);
        this.influencePad.tooltip(UIKeys.MODELS_IK_WEIGHT_TOOLTIP);

        this.orientTipToggle = new UIToggle(UIKeys.MODELS_IK_ORIENT_TIP, (b) ->
        {
            LimbData data = this.getOrCreateSelected();

            if (data != null)
            {
                data.orientTip = b.getValue();
                this.commitChanges();
            }
        });

        this.extensibleToggle = new UIToggle(UIKeys.MODELS_IK_EXTENSIBLE, (b) ->
        {
            LimbData data = this.getOrCreateSelected();

            if (data != null)
            {
                data.extensible = b.getValue();
                this.commitChanges();
            }
        });

        this.classicToggle = new UIToggle(IKey.raw("Classic Solver"), (b) ->
        {
            LimbData data = this.getOrCreateSelected();

            if (data != null)
            {
                data.classic = b.getValue();
                this.commitChanges();
            }
        });
        this.classicToggle.tooltip(IKey.raw("Solve this limb with the pre-redesign two-bone solver instead of the channel-space tree. Ignores per-bone joint freedom, and falls back to the tree anyway where this limb shares bones with another."));

        fields.add(
            this.activeToggle,
            UI.label(UIKeys.MODELS_IK_TARGET_BONE), this.controllerButton,
            UI.label(UIKeys.MODELS_IK_CHAIN_LENGTH), this.depthPad,
            this.poleEnabledToggle,
            UI.label(UIKeys.MODELS_IK_POLE_BONE), this.poleBoneButton,
            UI.label(UIKeys.MODELS_IK_BEND_OFFSET), this.bendOffsetPad,
            UI.label(UIKeys.MODELS_IK_FLEXIBILITY), this.flexibilityPad,
            UI.label(UIKeys.MODELS_IK_WEIGHT), this.influencePad,
            this.orientTipToggle,
            this.extensibleToggle,
            this.classicToggle
        );
        this.detailScroll.add(fields);

        this.add(listTitle, this.boneList);
        this.add(editorTitle, this.boneNameLabel, this.noSelectionLabel, this.detailScroll);

        this.setDetailVisible(false);
    }

    private UITrackpad buildPad(Consumer<Double> callback, double min, double max, double step, double smallStep, double bigStep)
    {
        UITrackpad pad = new UITrackpad((v) ->
        {
            if (!this.suppressCommit)
            {
                callback.accept(v.doubleValue());
            }
        });
        pad.limit(min, max).values(step, smallStep, bigStep);

        return pad;
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
            this.limbs.putIfAbsent(this.selectedBone, LimbData.createDefault());
        }
        else
        {
            this.limbs.remove(this.selectedBone);
        }

        this.refreshBoneList();
        this.refreshDetailFields();
        this.commitChanges();
    }

    private LimbData getSelectedData()
    {
        if (this.selectedBone == null)
        {
            return null;
        }

        return this.limbs.get(this.selectedBone);
    }

    private LimbData getOrCreateSelected()
    {
        if (this.selectedBone == null || this.selectedBone.isEmpty())
        {
            return null;
        }

        LimbData data = this.limbs.get(this.selectedBone);

        if (data == null)
        {
            data = LimbData.createDefault();
            this.limbs.put(this.selectedBone, data);
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

    private void updateControllerLabel()
    {
        LimbData data = this.getSelectedData();

        if (data == null || data.controller.isEmpty())
        {
            this.controllerButton.label = UIKeys.MODELS_IK_TARGET_BONE;
            return;
        }

        this.controllerButton.label = IKey.constant(data.controller);
    }

    private void updatePoleBoneLabel()
    {
        LimbData data = this.getSelectedData();

        if (data == null || data.poleBone.isEmpty())
        {
            this.poleBoneButton.label = UIKeys.MODELS_IK_POLE_BONE;
            return;
        }

        this.poleBoneButton.label = IKey.constant(data.poleBone);
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

        LimbData data = this.getSelectedData();
        boolean active = data != null;

        this.suppressCommit = true;

        try
        {
            this.activeToggle.setValue(active);
            this.updateControllerLabel();
            this.updatePoleBoneLabel();

            if (data != null)
            {
                this.depthPad.setValue(data.depth);
                this.poleEnabledToggle.setValue(data.poleEnabled);
                this.bendOffsetPad.setValue(data.bendOffset);
                this.flexibilityPad.setValue(data.flexibility);
                this.influencePad.setValue(data.influence);
                this.orientTipToggle.setValue(data.orientTip);
                this.extensibleToggle.setValue(data.extensible);
                this.classicToggle.setValue(data.classic);
            }
            else
            {
                this.depthPad.setValue(LimbConstraintDef.DEFAULT_DEPTH);
                this.poleEnabledToggle.setValue(true);
                this.bendOffsetPad.setValue(LimbConstraintDef.DEFAULT_BEND_OFFSET);
                this.flexibilityPad.setValue(LimbConstraintDef.DEFAULT_FLEXIBILITY);
                this.influencePad.setValue(LimbConstraintDef.DEFAULT_INFLUENCE);
                this.orientTipToggle.setValue(LimbConstraintDef.DEFAULT_ORIENT_TIP);
                this.extensibleToggle.setValue(LimbConstraintDef.DEFAULT_EXTENSIBLE);
                this.classicToggle.setValue(LimbConstraintDef.DEFAULT_CLASSIC);
            }
        }
        finally
        {
            this.suppressCommit = false;
        }

        this.setFieldEnabled(active);
    }

    private void setFieldEnabled(boolean enabled)
    {
        this.controllerButton.setEnabled(enabled);
        this.depthPad.setEnabled(enabled);
        this.poleEnabledToggle.setEnabled(enabled);
        this.poleBoneButton.setEnabled(enabled);
        this.bendOffsetPad.setEnabled(enabled);
        this.flexibilityPad.setEnabled(enabled);
        this.influencePad.setEnabled(enabled);
        this.orientTipToggle.setEnabled(enabled);
        this.extensibleToggle.setEnabled(enabled);
        this.classicToggle.setEnabled(enabled);
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
        return this.limbs.containsKey(bone) ? "* " + bone : bone;
    }

    private String boneFromDisplay(String display)
    {
        if (display != null && display.startsWith("* "))
        {
            return display.substring(2);
        }

        return display;
    }

    private void openBonePicker(Consumer<String> callback)
    {
        if (this.boneNames.isEmpty())
        {
            return;
        }

        UIBonePickerContextMenu menu = new UIBonePickerContextMenu(this.boneNames, callback);
        this.getContext().replaceContextMenu(menu);
        menu.xy(this.area.x + LEFT_WIDTH + SIDE_MARGIN + 4, this.area.y + 40)
            .w(180).h(220).bounds(this.getContext().menu.overlay, 5);
    }

    /**
     * The IK presets menu: save the WHOLE setup of this model — every limb plus the
     * per-bone joint freedom — under a name, load one back, or carry one to another
     * model through the clipboard. Presets live under the model's pose group, so
     * rigs that already share poses share these too.
     *
     * <p>{@code null} when there is no model config to read or write, which is also
     * when the button does nothing rather than opening an empty menu.
     */
    private UIContextMenu presetsMenu()
    {
        if (this.config == null)
        {
            return null;
        }

        String group = this.config.poseGroup.get();

        if (group.isEmpty())
        {
            group = this.config.getId();
        }

        return new UIDataContextMenu(IKPresetManager.INSTANCE, group, this::currentPreset, this::applyPreset)
            .tooltips(IKPresetManager.CLIPBOARD,
                IKey.raw("Copy this model's IK setup"),
                IKey.raw("Paste an IK setup"),
                IKey.raw("Clear every limb"),
                IKey.raw("Save the IK setup under the typed name"),
                IKey.raw("Preset name"));
    }

    /** This model's IK setup as a preset document — the serializer's own shape. */
    private MapType currentPreset()
    {
        List<LimbConstraintDef.Limb> list = new ArrayList<>();

        for (Map.Entry<String, LimbData> entry : this.limbs.entrySet())
        {
            list.add(entry.getValue().toLimb(entry.getKey()));
        }

        return LimbConstraintSerializer.toData(new LimbConstraintDef(list, this.joints));
    }

    /**
     * Loads a preset over the current setup — replacing it, not merging: a preset is
     * a whole IK rig, and half of one merged into half of another is a rig nobody
     * authored. An empty map is the menu's "reset", which clears every limb.
     *
     * <p>Limbs naming bones this model does not have are dropped, so a preset from a
     * near-miss rig lands with whatever fits instead of failing outright.
     */
    private void applyPreset(MapType data)
    {
        this.limbs.clear();
        this.joints.clear();

        LimbConstraintDef def = data == null || data.isEmpty() ? null : LimbConstraintSerializer.fromData(data);

        if (def != null)
        {
            this.ensureBoneNames();

            for (LimbConstraintDef.Limb limb : def.limbs())
            {
                if (this.boneNames.contains(limb.tipBone()))
                {
                    this.limbs.put(limb.tipBone(), LimbData.fromLimb(limb));
                }
            }

            for (Map.Entry<String, LimbConstraintDef.JointDoF> entry : def.joints().entrySet())
            {
                if (this.boneNames.contains(entry.getKey()))
                {
                    this.joints.put(entry.getKey(), entry.getValue());
                }
            }
        }

        this.commitChanges();
        this.refreshBoneList();
        this.refreshDetailFields();

        /* Rebuilding the list relayouts this panel's children; without this the new
         * layout only took effect on the next resize — i.e. after switching panels. */
        this.resize();
    }

    private void commitChanges()
    {
        if (this.suppressCommit || this.config == null)
        {
            return;
        }

        List<LimbConstraintDef.Limb> list = new ArrayList<>();

        for (Map.Entry<String, LimbData> entry : this.limbs.entrySet())
        {
            list.add(entry.getValue().toLimb(entry.getKey()));
        }

        LimbConstraintDef def = list.isEmpty() && this.joints.isEmpty() ? null : new LimbConstraintDef(list, this.joints);
        MapType map = LimbConstraintSerializer.toData(def);

        this.config.ik.set(map == null || map.isEmpty() ? null : map);
        this.editor.dirty();
        this.editor.getModelRenderer().syncSolverConfig(this.config);
        LimbConstraintCompiler.clear();
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
        this.limbs.clear();
        this.joints.clear();
        this.boneNames.clear();

        if (config != null)
        {
            BaseType raw = config.ik.get();

            if (raw instanceof MapType)
            {
                LimbConstraintDef def = LimbConstraintSerializer.fromData((MapType) raw);

                if (def != null && def.limbs() != null)
                {
                    for (LimbConstraintDef.Limb limb : def.limbs())
                    {
                        this.limbs.put(limb.tipBone(), LimbData.fromLimb(limb));
                    }
                }

                /* Held and written back untouched: per-bone joint freedom has no
                 * editor here yet, and a save from this panel must not wipe what
                 * was authored elsewhere. */
                if (def != null)
                {
                    this.joints.putAll(def.joints());
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

    private static class LimbData
    {
        String controller = "";
        int depth = LimbConstraintDef.DEFAULT_DEPTH;
        boolean poleEnabled = true;
        String poleBone = "";
        float bendOffset = LimbConstraintDef.DEFAULT_BEND_OFFSET;
        float flexibility = LimbConstraintDef.DEFAULT_FLEXIBILITY;
        float influence = LimbConstraintDef.DEFAULT_INFLUENCE;
        boolean active = true;
        boolean orientTip = LimbConstraintDef.DEFAULT_ORIENT_TIP;
        boolean extensible = LimbConstraintDef.DEFAULT_EXTENSIBLE;
        boolean classic = LimbConstraintDef.DEFAULT_CLASSIC;

        static LimbData createDefault()
        {
            return new LimbData();
        }

        static LimbData fromLimb(LimbConstraintDef.Limb limb)
        {
            LimbData data = new LimbData();
            data.controller = limb.controllerBone();
            data.depth = limb.depth();
            data.poleEnabled = limb.poleEnabled();
            data.poleBone = limb.poleBone();
            data.bendOffset = limb.bendOffset();
            data.flexibility = limb.flexibility();
            data.influence = limb.influence();
            data.active = limb.active();
            data.orientTip = limb.orientTip();
            data.extensible = limb.extensible();
            data.classic = limb.classic();

            return data;
        }

        LimbConstraintDef.Limb toLimb(String tipBone)
        {
            return new LimbConstraintDef.Limb(
                tipBone,
                this.controller,
                this.depth,
                this.poleEnabled,
                this.poleBone,
                this.bendOffset,
                this.flexibility,
                this.influence,
                this.active,
                this.orientTip,
                this.extensible,
                this.classic
            );
        }
    }

    public static class UIBonePickerContextMenu extends UIContextMenu
    {
        public UISearchList<String> list;

        public UIBonePickerContextMenu(List<String> bones, Consumer<String> callback)
        {
            this.list = new UISearchList<>(new UIStringList((items) ->
            {
                if (items != null && !items.isEmpty() && items.get(0) != null)
                {
                    callback.accept(items.get(0));
                }
            }));
            this.list.list.setList(bones);
            this.list.list.background = 0xaa000000;
            this.list.relative(this).xy(5, 5).w(1F, -10).h(1F, -10);
            this.list.search.placeholder(UIKeys.POSE_CONTEXT_NAME);
            this.add(this.list);
        }

        @Override
        public boolean isEmpty()
        {
            return this.list.list.getList().isEmpty();
        }

        @Override
        public void setMouse(UIContext context)
        {
            this.xy(context.mouseX(), context.mouseY()).w(180).h(220)
                .bounds(context.menu.overlay, 5);
        }
    }
}
