package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.presets.DataManager;

/**
 * Named IK setups saved beside a model, the same way poses and shape keys are:
 * one {@code ik_presets.json} per group, holding whole {@link LimbConstraintDef}
 * documents (every limb plus the per-bone joint freedom) under animator-chosen
 * names.
 *
 * <p>The group is the model's POSE GROUP, not its id, so rigs configured to share
 * poses share their IK presets too — which is what makes a preset reusable at all,
 * since a saved limb refers to bones by name. For rigs that do not share a group,
 * the context menu's copy/paste carries a setup across models through the
 * clipboard instead.
 */
public class IKPresetManager extends DataManager
{
    public static final IKPresetManager INSTANCE = new IKPresetManager();

    /** Clipboard group for the context menu's copy/paste, kept distinct from poses. */
    public static final String CLIPBOARD = "_CopyIK";

    @Override
    protected Link getFile(String group)
    {
        return Link.assets(ModelManager.MODELS_PREFIX + group + "/ik_presets.json");
    }
}
