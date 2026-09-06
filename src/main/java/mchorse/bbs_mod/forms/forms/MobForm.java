package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.pose.Pose;

public class MobForm extends Form
{
    public final ValueString mobID = new ValueString("mobId", "minecraft:chicken");
    public final ValueString mobNBT = new ValueString("mobNbt", "");
    public final ValueString playerName = new ValueString("playerName", "");
    public final ValueString playerUuid = new ValueString("playerUuid", "");

    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueBoolean slim = new ValueBoolean("slim", false);

    public final ValuePose pose = new ValuePose("pose", new Pose());
    public final ValuePose poseOverlay = new ValuePose("pose_overlay", new Pose());

    public final ValueFloat pbrNormalIntensity = new ValueFloat("pbr_normal_intensity", 1F, 0F, 4F);
    public final ValueFloat pbrSpecularIntensity = new ValueFloat("pbr_specular_intensity", 1F, 0F, 4F);

    public MobForm()
    {
        this.slim.invisible();

        this.add(this.mobID);
        this.add(this.mobNBT);
        this.add(this.playerName);
        this.add(this.playerUuid);
        this.add(this.pose);
        this.add(this.poseOverlay);
        this.add(this.texture);
        this.add(this.slim);
        this.add(this.pbrNormalIntensity);
        this.add(this.pbrSpecularIntensity);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return this.mobID.get().isEmpty() ? super.getDefaultDisplayName() : this.mobID.get();
    }

    public boolean isPlayer()
    {
        return this.mobID.get().equals("minecraft:player");
    }
}