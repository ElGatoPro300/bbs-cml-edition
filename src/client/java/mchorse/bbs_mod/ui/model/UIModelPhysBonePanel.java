package mchorse.bbs_mod.ui.model;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ModelConfig;
import mchorse.bbs_mod.cubic.physics.SpringChainCompiler;
import mchorse.bbs_mod.cubic.physics.SpringChainDef;
import mchorse.bbs_mod.cubic.physics.SpringChainSerializer;
import mchorse.bbs_mod.cubic.physics.SpringChainsConfig;
import mchorse.bbs_mod.cubic.physics.WindDef;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Spring-chain (dynamic bone) editor for the model panel.
 * Class name kept as {@code UIModelPhysBonePanel} for registration compatibility.
 *
 * LEFT: skeleton bones. Enabling a bone creates a spring chain rooted there.
 * RIGHT: chain properties + global wind section.
 */
public class UIModelPhysBonePanel extends UIElement
{
    private static final int SIDE_MARGIN = 10;
    private static final int LEFT_WIDTH = 220;
    private static final int RIGHT_WIDTH = 260;

    private static final float DEFAULT_PULL_STRENGTH = 1F;
    private static final float DEFAULT_DRAG = 0.15F;
    private static final int DEFAULT_RELAX_STEPS = 4;
    private static final float DEFAULT_HIT_RADIUS = 0.1F;

    private final UIStringList boneList;
    private final UIToggle debugToggle;
    private final UIScrollView detailScroll;
    private final UILabel noSelectionLabel;
    private final UILabel boneNameLabel;

    private final UIToggle activeToggle;
    private final UIButton endBoneButton;
    private final UIButton pinTargetButton;
    private final UITrackpad pullStrengthPad;
    private final UITrackpad dragPad;
    private final UITrackpad springReturnPad;
    private final UITrackpad relaxStepsPad;
    private final UIToggle bodyRelativePullToggle;
    private final UITrackpad pullRotXPad;
    private final UITrackpad pullRotYPad;
    private final UITrackpad pullRotZPad;
    private final UIToggle hitDetectionToggle;
    private final UITrackpad hitRadiusPad;
    private final UITrackpad influencePad;

    private final UITrackpad windPowerPad;
    private final UITrackpad windDirXPad;
    private final UITrackpad windDirYPad;
    private final UITrackpad windDirZPad;
    private final UITrackpad windGustinessPad;
    private final UITrackpad windGustSpeedPad;
    private final UITrackpad windGustScalePad;
    private final UIToggle windModelRelativeToggle;

    private final IUIModelPanelHost editor;
    private ModelConfig config;
    private final Map<String, SpringChainData> chains = new HashMap<>();
    private WindData wind = WindData.createDefault();
    private final List<String> boneNames = new ArrayList<>();
    private String selectedBone;
    private boolean suppressCommit;

    public UIModelPhysBonePanel(IUIModelPanelHost editor)
    {
        this.editor = editor;
        this.relative(editor.getMainView()).w(1F).h(1F);

        UILabel listTitle = UI.label(UIKeys.MODELS_PHYS_BONES_EDITOR).background();
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

        /* Anchored to the PANEL, not to the bone list, so rebuilding the list cannot
         * displace it. The switch is a settings value, not part of a rig, so it sits
         * outside the per-chain fields and stays reachable with nothing selected. */
        this.debugToggle = new UIToggle(UIKeys.MODELS_DEBUG_SHOW, (b) -> BBSSettings.physicsDebug.enabled.set(b.getValue()));
        this.debugToggle.setValue(BBSSettings.physicsDebug.enabled.get());
        this.debugToggle.tooltip(UIKeys.MODELS_PHYS_BONES_DEBUG_TOOLTIP);
        this.debugToggle.context(() -> new UIDebugOverlayContextMenu(BBSSettings.physicsDebug));

        UIIcon debugSettings = new UIIcon(Icons.GEAR, (b) -> this.getContext().replaceContextMenu(new UIDebugOverlayContextMenu(BBSSettings.physicsDebug)));

        debugSettings.tooltip(UIKeys.MODELS_DEBUG_CONFIGURE);

        UIElement debugRow = UI.row(this.debugToggle, debugSettings);

        debugRow.relative(this).x(SIDE_MARGIN).y(1F, -30).w(LEFT_WIDTH).h(20);
        this.add(debugRow);

        UILabel editorTitle = UI.label(UIKeys.MODELS_PHYS_BONES_EDITOR).background();
        editorTitle.relative(this)
            .x(1F, -RIGHT_WIDTH - SIDE_MARGIN).y(10)
            .w(RIGHT_WIDTH).h(12);

        this.boneNameLabel = UI.label(IKey.raw("-"));
        this.boneNameLabel.relative(editorTitle).y(1F, 4).w(1F).h(12);

        this.noSelectionLabel = UI.label(UIKeys.MODELS_PHYS_BONES_NO_SELECTION);
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

        fields.add(UI.label(UIKeys.MODELS_PHYS_BONES_SPRING_CHAIN).background());

        this.activeToggle = new UIToggle(UIKeys.MODELS_PHYS_BONES_ENABLED, (b) -> this.onActiveChanged(b.getValue()));
        this.activeToggle.tooltip(UIKeys.MODELS_PHYS_BONES_ENABLED_TOOLTIP);

        this.endBoneButton = new UIButton(UIKeys.MODELS_PHYS_BONES_CHAIN_END, (b) ->
            this.openBonePicker((bone) ->
            {
                SpringChainData data = this.getOrCreateSelected();

                if (data != null)
                {
                    data.endBone = bone;
                    this.updateEndBoneLabel();
                    this.commitChanges();
                }
            })
        );
        this.endBoneButton.tooltip(UIKeys.MODELS_PHYS_BONES_END_BONE_TOOLTIP);
        this.endBoneButton.context((menu) -> menu.action(Icons.CLOSE, UIKeys.MODELS_PHYS_BONES_END_BONE_CLEAR, () ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.endBone = "";
                this.updateEndBoneLabel();
                this.commitChanges();
            }
        }));

        this.pinTargetButton = new UIButton(UIKeys.MODELS_PHYS_BONES_ANCHOR_END, (b) ->
            this.openBonePicker((bone) ->
            {
                SpringChainData data = this.getOrCreateSelected();

                if (data != null)
                {
                    data.pinTarget = bone;
                    this.updatePinTargetLabel();
                    this.commitChanges();
                }
            })
        );
        this.pinTargetButton.tooltip(UIKeys.MODELS_PHYS_BONES_ANCHOR_END_TOOLTIP);

        this.pullStrengthPad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.pullStrength = v.floatValue();
                this.commitChanges();
            }
        }, 0D, 5D, 0.05D, 0.01D, 0.25D);

        this.dragPad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.drag = v.floatValue();
                this.commitChanges();
            }
        }, 0D, 1D, 0.01D, 0.001D, 0.05D);

        this.springReturnPad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.springReturn = v.floatValue();
                this.commitChanges();
            }
        }, 0D, 1D, 0.01D, 0.001D, 0.05D);

        this.relaxStepsPad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.relaxSteps = v.intValue();
                this.commitChanges();
            }
        }, 1D, 16D, 1D, 1D, 4D);
        this.relaxStepsPad.integer();
        this.relaxStepsPad.tooltip(UIKeys.MODELS_PHYS_BONES_SOLVER_STEPS_TOOLTIP);

        this.bodyRelativePullToggle = new UIToggle(UIKeys.MODELS_PHYS_BONES_BODY_RELATIVE_PULL, (b) ->
        {
            SpringChainData data = this.getOrCreateSelected();

            if (data != null)
            {
                data.bodyRelativePull = b.getValue();
                this.commitChanges();
            }
        });

        this.pullRotXPad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.pullRotX = v.floatValue();
                this.commitChanges();
            }
        }, -180D, 180D, 1D, 0.1D, 5D);

        this.pullRotYPad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.pullRotY = v.floatValue();
                this.commitChanges();
            }
        }, -180D, 180D, 1D, 0.1D, 5D);

        this.pullRotZPad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.pullRotZ = v.floatValue();
                this.commitChanges();
            }
        }, -180D, 180D, 1D, 0.1D, 5D);

        this.hitDetectionToggle = new UIToggle(UIKeys.MODELS_PHYS_BONES_COLLISION, (b) ->
        {
            SpringChainData data = this.getOrCreateSelected();

            if (data != null)
            {
                data.hitDetection = b.getValue();
                this.commitChanges();
            }
        });
        this.hitDetectionToggle.tooltip(UIKeys.MODELS_PHYS_BONES_COLLISION_TOOLTIP);

        this.hitRadiusPad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.hitRadius = v.floatValue();
                this.commitChanges();
            }
        }, 0D, 10D, 0.05D, 0.01D, 0.25D);
        this.hitRadiusPad.tooltip(UIKeys.MODELS_PHYS_BONES_COLLISION_RADIUS_TOOLTIP);

        this.influencePad = this.buildPad((v) ->
        {
            SpringChainData data = this.getSelectedData();

            if (data != null)
            {
                data.influence = v.floatValue();
                this.commitChanges();
            }
        }, 0D, 1D, 0.05D, 0.01D, 0.1D);

        fields.add(
            this.activeToggle,
            UI.label(UIKeys.MODELS_PHYS_BONES_CHAIN_END), this.endBoneButton,
            UI.label(UIKeys.MODELS_PHYS_BONES_ANCHOR_END), this.pinTargetButton,
            UI.label(UIKeys.MODELS_PHYS_BONES_PULL_STRENGTH), this.pullStrengthPad,
            UI.label(UIKeys.MODELS_PHYS_BONES_DRAG), this.dragPad,
            UI.label(UIKeys.MODELS_PHYS_BONES_SPRING_RETURN), this.springReturnPad,
            UI.label(UIKeys.MODELS_PHYS_BONES_SOLVER_STEPS), this.relaxStepsPad,
            this.bodyRelativePullToggle,
            UI.label(UIKeys.MODELS_PHYS_BONES_PULL_ROTATION),
            UI.row(this.pullRotXPad, this.pullRotYPad, this.pullRotZPad),
            this.hitDetectionToggle,
            UI.label(UIKeys.MODELS_PHYS_BONES_COLLISION_RADIUS), this.hitRadiusPad,
            UI.label(UIKeys.MODELS_PHYS_BONES_INFLUENCE), this.influencePad
        );

        fields.add(UI.label(UIKeys.MODELS_PHYS_BONES_WIND).background());

        this.windPowerPad = this.buildPad((v) ->
        {
            this.wind.power = v.floatValue();
            this.commitChanges();
        }, 0D, 10D, 0.05D, 0.01D, 0.25D);

        this.windDirXPad = this.buildPad((v) ->
        {
            this.wind.dirX = v.floatValue();
            this.commitChanges();
        }, -5D, 5D, 0.05D, 0.01D, 0.25D);

        this.windDirYPad = this.buildPad((v) ->
        {
            this.wind.dirY = v.floatValue();
            this.commitChanges();
        }, -5D, 5D, 0.05D, 0.01D, 0.25D);

        this.windDirZPad = this.buildPad((v) ->
        {
            this.wind.dirZ = v.floatValue();
            this.commitChanges();
        }, -5D, 5D, 0.05D, 0.01D, 0.25D);

        this.windGustinessPad = this.buildPad((v) ->
        {
            this.wind.gustiness = v.floatValue();
            this.commitChanges();
        }, 0D, 1D, 0.05D, 0.01D, 0.1D);

        this.windGustSpeedPad = this.buildPad((v) ->
        {
            this.wind.gustSpeed = v.floatValue();
            this.commitChanges();
        }, 0D, 10D, 0.05D, 0.01D, 0.25D);

        this.windGustScalePad = this.buildPad((v) ->
        {
            this.wind.gustScale = v.floatValue();
            this.commitChanges();
        }, 0D, 10D, 0.05D, 0.01D, 0.25D);

        this.windModelRelativeToggle = new UIToggle(UIKeys.MODELS_PHYS_BONES_WIND_MODEL_RELATIVE, (b) ->
        {
            this.wind.modelRelative = b.getValue();
            this.commitChanges();
        });

        fields.add(
            UI.label(UIKeys.MODELS_PHYS_BONES_WIND_POWER), this.windPowerPad,
            UI.label(UIKeys.MODELS_PHYS_BONES_WIND_DIRECTION),
            UI.row(this.windDirXPad, this.windDirYPad, this.windDirZPad),
            UI.label(UIKeys.MODELS_PHYS_BONES_WIND_GUSTINESS), this.windGustinessPad,
            UI.label(UIKeys.MODELS_PHYS_BONES_WIND_GUST_SPEED), this.windGustSpeedPad,
            UI.label(UIKeys.MODELS_PHYS_BONES_WIND_GUST_SCALE), this.windGustScalePad,
            this.windModelRelativeToggle
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
            this.chains.putIfAbsent(this.selectedBone, SpringChainData.createDefault());
        }
        else
        {
            this.chains.remove(this.selectedBone);
        }

        this.refreshBoneList();
        this.refreshDetailFields();
        this.commitChanges();
    }

    private SpringChainData getSelectedData()
    {
        if (this.selectedBone == null)
        {
            return null;
        }

        return this.chains.get(this.selectedBone);
    }

    private SpringChainData getOrCreateSelected()
    {
        if (this.selectedBone == null || this.selectedBone.isEmpty())
        {
            return null;
        }

        SpringChainData data = this.chains.get(this.selectedBone);

        if (data == null)
        {
            data = SpringChainData.createDefault();
            this.chains.put(this.selectedBone, data);
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

    private void updateEndBoneLabel()
    {
        SpringChainData data = this.getSelectedData();

        if (data == null || data.endBone.isEmpty())
        {
            /* Blank is a real setting, not a gap to fill: the chain runs to the deepest
             * bone under the root. Say so rather than showing a bare prompt. */
            this.endBoneButton.label = UIKeys.MODELS_PHYS_BONES_END_BONE_AUTO;
            return;
        }

        this.endBoneButton.label = IKey.constant(data.endBone);
    }

    private void updatePinTargetLabel()
    {
        SpringChainData data = this.getSelectedData();

        if (data == null || data.pinTarget.isEmpty())
        {
            this.pinTargetButton.label = UIKeys.MODELS_PHYS_BONES_ANCHOR_END;
            return;
        }

        this.pinTargetButton.label = IKey.constant(data.pinTarget);
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

        SpringChainData data = this.getSelectedData();
        boolean active = data != null;

        this.suppressCommit = true;

        try
        {
            this.activeToggle.setValue(active);
            this.updateEndBoneLabel();
            this.updatePinTargetLabel();

            if (data != null)
            {
                this.pullStrengthPad.setValue(data.pullStrength);
                this.dragPad.setValue(data.drag);
                this.springReturnPad.setValue(data.springReturn);
                this.relaxStepsPad.setValue(data.relaxSteps);
                this.bodyRelativePullToggle.setValue(data.bodyRelativePull);
                this.pullRotXPad.setValue(data.pullRotX);
                this.pullRotYPad.setValue(data.pullRotY);
                this.pullRotZPad.setValue(data.pullRotZ);
                this.hitDetectionToggle.setValue(data.hitDetection);
                this.hitRadiusPad.setValue(data.hitRadius);
                this.influencePad.setValue(data.influence);
            }
            else
            {
                this.pullStrengthPad.setValue(DEFAULT_PULL_STRENGTH);
                this.dragPad.setValue(DEFAULT_DRAG);
                this.springReturnPad.setValue(SpringChainDef.DEFAULT_SPRING_RETURN);
                this.relaxStepsPad.setValue(DEFAULT_RELAX_STEPS);
                this.bodyRelativePullToggle.setValue(false);
                this.pullRotXPad.setValue(0D);
                this.pullRotYPad.setValue(0D);
                this.pullRotZPad.setValue(0D);
                this.hitDetectionToggle.setValue(false);
                this.hitRadiusPad.setValue(DEFAULT_HIT_RADIUS);
                this.influencePad.setValue(SpringChainDef.DEFAULT_INFLUENCE);
            }

            this.windPowerPad.setValue(this.wind.power);
            this.windDirXPad.setValue(this.wind.dirX);
            this.windDirYPad.setValue(this.wind.dirY);
            this.windDirZPad.setValue(this.wind.dirZ);
            this.windGustinessPad.setValue(this.wind.gustiness);
            this.windGustSpeedPad.setValue(this.wind.gustSpeed);
            this.windGustScalePad.setValue(this.wind.gustScale);
            this.windModelRelativeToggle.setValue(this.wind.modelRelative);
        }
        finally
        {
            this.suppressCommit = false;
        }

        this.setChainFieldsEnabled(active);
    }

    private void setChainFieldsEnabled(boolean enabled)
    {
        this.endBoneButton.setEnabled(enabled);
        this.pinTargetButton.setEnabled(enabled);
        this.pullStrengthPad.setEnabled(enabled);
        this.dragPad.setEnabled(enabled);
        this.springReturnPad.setEnabled(enabled);
        this.relaxStepsPad.setEnabled(enabled);
        this.bodyRelativePullToggle.setEnabled(enabled);
        this.pullRotXPad.setEnabled(enabled);
        this.pullRotYPad.setEnabled(enabled);
        this.pullRotZPad.setEnabled(enabled);
        this.hitDetectionToggle.setEnabled(enabled);
        this.hitRadiusPad.setEnabled(enabled);
        this.influencePad.setEnabled(enabled);
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
        return this.chains.containsKey(bone) ? "* " + bone : bone;
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

        UIModelIKPanel.UIBonePickerContextMenu menu = new UIModelIKPanel.UIBonePickerContextMenu(this.boneNames, callback);
        this.getContext().replaceContextMenu(menu);
        menu.xy(this.area.x + LEFT_WIDTH + SIDE_MARGIN + 4, this.area.y + 40)
            .w(180).h(220).bounds(this.getContext().menu.overlay, 5);
    }

    private void commitChanges()
    {
        if (this.suppressCommit || this.config == null)
        {
            return;
        }

        Map<String, SpringChainDef> out = new HashMap<>();

        for (Map.Entry<String, SpringChainData> entry : this.chains.entrySet())
        {
            SpringChainData data = entry.getValue();

            out.put(entry.getKey(), new SpringChainDef(
                data.endBone,
                data.pinTarget,
                data.pullStrength,
                data.drag,
                data.springReturn,
                data.relaxSteps,
                data.bodyRelativePull,
                data.pullRotX,
                data.pullRotY,
                data.pullRotZ,
                data.hitDetection,
                data.hitRadius,
                data.influence
            ));
        }

        WindDef windDef = new WindDef(
            this.wind.power,
            this.wind.dirX,
            this.wind.dirY,
            this.wind.dirZ,
            this.wind.gustiness,
            this.wind.gustSpeed,
            this.wind.gustScale,
            this.wind.modelRelative
        );

        SpringChainsConfig springsConfig = new SpringChainsConfig(out, windDef);
        MapType map = SpringChainSerializer.toData(springsConfig);

        boolean empty = out.isEmpty() && windDef.isDefault();
        this.config.springs.set(empty ? null : map);
        this.editor.dirty();
        this.editor.getModelRenderer().syncSolverConfig(this.config);
        SpringChainCompiler.clear();
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
        this.chains.clear();
        this.wind = WindData.createDefault();
        this.boneNames.clear();

        if (config != null)
        {
            BaseType raw = config.springs.get();

            if (raw instanceof MapType)
            {
                SpringChainsConfig springsConfig = SpringChainSerializer.fromData((MapType) raw);

                if (springsConfig != null)
                {
                    if (springsConfig.chains() != null)
                    {
                        for (Map.Entry<String, SpringChainDef> entry : springsConfig.chains().entrySet())
                        {
                            this.chains.put(entry.getKey(), SpringChainData.fromDef(entry.getValue()));
                        }
                    }

                    this.wind = WindData.fromDef(springsConfig.wind());
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

    private static class SpringChainData
    {
        String endBone = "";
        String pinTarget = "";
        float pullStrength = DEFAULT_PULL_STRENGTH;
        float drag = DEFAULT_DRAG;
        float springReturn = SpringChainDef.DEFAULT_SPRING_RETURN;
        int relaxSteps = DEFAULT_RELAX_STEPS;
        boolean bodyRelativePull;
        float pullRotX;
        float pullRotY;
        float pullRotZ;
        boolean hitDetection;
        float hitRadius = DEFAULT_HIT_RADIUS;
        float influence = SpringChainDef.DEFAULT_INFLUENCE;

        static SpringChainData createDefault()
        {
            return new SpringChainData();
        }

        static SpringChainData fromDef(SpringChainDef def)
        {
            SpringChainData data = new SpringChainData();
            data.endBone = def.endBone() == null ? "" : def.endBone();
            data.pinTarget = def.pinTarget() == null ? "" : def.pinTarget();
            data.pullStrength = def.pullStrength();
            data.drag = def.drag();
            data.springReturn = def.springReturn();
            data.relaxSteps = def.relaxSteps();
            data.bodyRelativePull = def.bodyRelativePull();
            data.pullRotX = def.pullRotX();
            data.pullRotY = def.pullRotY();
            data.pullRotZ = def.pullRotZ();
            data.hitDetection = def.hitDetection();
            data.hitRadius = def.hitRadius();
            data.influence = def.influence();

            return data;
        }
    }

    private static class WindData
    {
        float power = WindDef.NONE.power();
        float dirX = WindDef.NONE.dirX();
        float dirY = WindDef.NONE.dirY();
        float dirZ = WindDef.NONE.dirZ();
        float gustiness = WindDef.NONE.gustiness();
        float gustSpeed = WindDef.NONE.gustSpeed();
        float gustScale = WindDef.NONE.gustScale();
        boolean modelRelative = WindDef.NONE.modelRelative();

        static WindData createDefault()
        {
            return new WindData();
        }

        static WindData fromDef(WindDef def)
        {
            if (def == null)
            {
                return createDefault();
            }

            WindData data = new WindData();
            data.power = def.power();
            data.dirX = def.dirX();
            data.dirY = def.dirY();
            data.dirZ = def.dirZ();
            data.gustiness = def.gustiness();
            data.gustSpeed = def.gustSpeed();
            data.gustScale = def.gustScale();
            data.modelRelative = def.modelRelative();

            return data;
        }
    }
}
