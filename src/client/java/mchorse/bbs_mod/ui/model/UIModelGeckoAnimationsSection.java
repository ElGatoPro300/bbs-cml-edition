package mchorse.bbs_mod.ui.model;

import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.gecko.config.GeckoLimbAnimationConfig;
import mchorse.bbs_mod.cubic.model.ModelConfig;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.function.Consumer;

public class UIModelGeckoAnimationsSection extends UIModelSection
{
    public UIButton selectedBoneButton;
    public UIToggle geckoEnabled;
    public UITrackpad transitionSpeed;
    public UITrackpad previewWheelSpeed;
    public UIToggle swinging;
    public UIToggle swiping;
    public UIToggle lookX;
    public UIToggle lookY;
    public UIToggle idle;
    public UIToggle invert;
    public UIToggle wheel;
    public UICirculate wheelAxis;
    public UITrackpad wheelSpeed;

    private String selectedBone;

    public UIModelGeckoAnimationsSection(IUIModelPanelHost editor)
    {
        super(editor);

        this.selectedBoneButton = new UIButton(this.getSelectedBoneLabel(), (b) -> {});
        this.geckoEnabled = new UIToggle(UIKeys.MODELS_LOOK_AT_GECKO_ANIMATIONS, (b) ->
        {
            ActionsConfig actions = this.getActions();

            if (actions == null)
            {
                return;
            }

            actions.geckoAnimations.enabled = b.getValue();
            this.editor.dirty();
        });
        this.transitionSpeed = new UITrackpad((v) ->
        {
            ActionsConfig actions = this.getActions();

            if (actions == null)
            {
                return;
            }

            actions.geckoAnimations.transitionSpeed = v.floatValue();
            this.editor.dirty();
        });
        this.previewWheelSpeed = new UITrackpad((v) ->
        {
            ActionsConfig actions = this.getActions();

            if (actions == null)
            {
                return;
            }

            actions.geckoAnimations.previewWheelSpeed = v.floatValue();
            this.editor.dirty();
        });
        this.swinging = new UIToggle(UIKeys.MODELS_LOOK_AT_SWINGING, (b) -> this.editLimb((config) -> config.swinging = b.getValue()));
        this.swiping = new UIToggle(UIKeys.MODELS_LOOK_AT_SWIPING, (b) -> this.editLimb((config) -> config.swiping = b.getValue()));
        this.lookX = new UIToggle(UIKeys.MODELS_LOOK_AT_LOOK_X, (b) -> this.editLimb((config) -> config.lookX = b.getValue()));
        this.lookY = new UIToggle(UIKeys.MODELS_LOOK_AT_LOOK_Y, (b) -> this.editLimb((config) -> config.lookY = b.getValue()));
        this.idle = new UIToggle(UIKeys.MODELS_LOOK_AT_IDLE, (b) -> this.editLimb((config) -> config.idle = b.getValue()));
        this.invert = new UIToggle(UIKeys.MODELS_LOOK_AT_INVERT, (b) -> this.editLimb((config) -> config.invert = b.getValue()));
        this.wheel = new UIToggle(UIKeys.MODELS_LOOK_AT_WHEEL, (b) -> this.editLimb((config) -> config.wheel = b.getValue()));
        this.wheelAxis = new UICirculate((b) ->
        {
            int index = b.getValue();

            this.editLimb((config) ->
            {
                if (index == 0)
                {
                    config.wheelAxis = "x";
                }
                else if (index == 1)
                {
                    config.wheelAxis = "y";
                }
                else
                {
                    config.wheelAxis = "z";
                }
            });
        });
        this.wheelAxis.addLabel(UIKeys.MODELS_LOOK_AT_AXIS_X);
        this.wheelAxis.addLabel(UIKeys.MODELS_LOOK_AT_AXIS_Y);
        this.wheelAxis.addLabel(UIKeys.MODELS_LOOK_AT_AXIS_Z);
        this.wheelSpeed = new UITrackpad((v) -> this.editLimb((config) -> config.wheelSpeed = v.floatValue())).limit(0, 8);
        this.selectedBoneButton.tooltip(UIKeys.MODELS_LOOK_AT_SELECTED_BONE_TOOLTIP);
        this.geckoEnabled.tooltip(UIKeys.MODELS_LOOK_AT_GECKO_ENABLED_TOOLTIP);
        this.transitionSpeed.tooltip(UIKeys.MODELS_LOOK_AT_TRANSITION_SPEED_TOOLTIP);
        this.previewWheelSpeed.tooltip(UIKeys.MODELS_LOOK_AT_PREVIEW_WHEEL_SPEED_TOOLTIP);
        this.swinging.tooltip(UIKeys.MODELS_LOOK_AT_SWINGING_TOOLTIP);
        this.swiping.tooltip(UIKeys.MODELS_LOOK_AT_SWIPING_TOOLTIP);
        this.lookX.tooltip(UIKeys.MODELS_LOOK_AT_LOOK_X_TOOLTIP);
        this.lookY.tooltip(UIKeys.MODELS_LOOK_AT_LOOK_Y_TOOLTIP);
        this.idle.tooltip(UIKeys.MODELS_LOOK_AT_IDLE_TOOLTIP);
        this.invert.tooltip(UIKeys.MODELS_LOOK_AT_INVERT_TOOLTIP);
        this.wheel.tooltip(UIKeys.MODELS_LOOK_AT_WHEEL_TOOLTIP);
        this.wheelAxis.tooltip(UIKeys.MODELS_LOOK_AT_WHEEL_AXIS_TOOLTIP);
        this.wheelSpeed.tooltip(UIKeys.MODELS_LOOK_AT_WHEEL_SPEED_TOOLTIP);
        this.transitionSpeed.limit(0.01, 1).values(0.01, 0.005, 0.05);
        this.previewWheelSpeed.limit(0, 8).values(0.1, 0.05, 0.25);
        this.wheelSpeed.values(0.1, 0.05, 0.25);

        this.fields.add(this.geckoEnabled);
        this.fields.add(UI.label(UIKeys.MODELS_LOOK_AT_TRANSITION_SPEED), this.transitionSpeed);
        this.fields.add(UI.label(UIKeys.MODELS_LOOK_AT_PREVIEW_WHEEL_SPEED), this.previewWheelSpeed);
        this.fields.add(this.selectedBoneButton);
        this.fields.add(this.swinging, this.swiping, this.lookX, this.lookY, this.idle, this.invert, this.wheel);
        this.fields.add(UI.label(UIKeys.MODELS_LOOK_AT_WHEEL_AXIS), this.wheelAxis);
        this.fields.add(UI.label(UIKeys.MODELS_LOOK_AT_WHEEL_SPEED), this.wheelSpeed);
        this.updateButtonLabel();
        this.refreshAnimationFields();
    }

    private IKey getSelectedBoneLabel()
    {
        String selected = this.selectedBone == null || this.selectedBone.isEmpty() ? UIKeys.GENERAL_NONE.get() : this.selectedBone;

        return IKey.raw(UIKeys.MODELS_LOOK_AT_SELECTED_BONE.get() + ": " + selected);
    }

    private ActionsConfig getActions()
    {
        if (this.config == null)
        {
            return null;
        }

        return this.config.animations.get();
    }

    private GeckoLimbAnimationConfig getCurrentLimbConfig(boolean create)
    {
        ActionsConfig actions = this.getActions();

        if (actions == null || this.selectedBone == null || this.selectedBone.isEmpty())
        {
            return null;
        }

        GeckoLimbAnimationConfig config = actions.geckoAnimations.limbs.get(this.selectedBone);

        if (config == null && create)
        {
            config = new GeckoLimbAnimationConfig();
            actions.geckoAnimations.limbs.put(this.selectedBone, config);
        }

        return config;
    }

    private void editLimb(Consumer<GeckoLimbAnimationConfig> editor)
    {
        GeckoLimbAnimationConfig config = this.getCurrentLimbConfig(true);

        if (config == null)
        {
            return;
        }

        editor.accept(config);
        this.editor.dirty();
    }

    private void updateButtonLabel()
    {
        this.selectedBoneButton.label = this.getSelectedBoneLabel();
    }

    private void refreshAnimationFields()
    {
        ActionsConfig actions = this.getActions();

        if (actions == null)
        {
            return;
        }

        this.geckoEnabled.setValue(actions.geckoAnimations.enabled);
        this.transitionSpeed.setValue(actions.geckoAnimations.transitionSpeed);
        this.previewWheelSpeed.setValue(actions.geckoAnimations.previewWheelSpeed);

        GeckoLimbAnimationConfig limb = this.getCurrentLimbConfig(false);

        if (limb == null)
        {
            limb = new GeckoLimbAnimationConfig();
        }

        this.swinging.setValue(limb.swinging);
        this.swiping.setValue(limb.swiping);
        this.lookX.setValue(limb.lookX);
        this.lookY.setValue(limb.lookY);
        this.idle.setValue(limb.idle);
        this.invert.setValue(limb.invert);
        this.wheel.setValue(limb.wheel);
        this.wheelSpeed.setValue(limb.wheelSpeed);

        if ("y".equals(limb.wheelAxis))
        {
            this.wheelAxis.setValue(1);
        }
        else if ("z".equals(limb.wheelAxis))
        {
            this.wheelAxis.setValue(2);
        }
        else
        {
            this.wheelAxis.setValue(0);
        }
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.MODELS_LOOK_AT_GECKO_ANIMATIONS;
    }

    @Override
    public void onBoneSelected(String bone)
    {
        this.selectedBone = bone;
        this.updateButtonLabel();
        this.refreshAnimationFields();
    }

    @Override
    public void setConfig(ModelConfig config)
    {
        super.setConfig(config);
        this.updateButtonLabel();
        this.refreshAnimationFields();
    }
}
