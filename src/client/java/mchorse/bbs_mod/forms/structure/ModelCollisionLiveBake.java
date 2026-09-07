package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.pose.Pose;

import net.minecraft.client.MinecraftClient;

import org.joml.Vector3f;

import java.util.List;

/**
 * Client-side collision bake using the same posed model pipeline as rendering
 * (states + animator + pose, cubic cubes or skinned BOBJ).
 */
public final class ModelCollisionLiveBake
{
    private ModelCollisionLiveBake()
    {}

    public static void register()
    {
        ModelCollisionData.setLivePoseBaker(ModelCollisionLiveBake::bake);
    }

    private static ModelCollisionData bake(ModelForm form)
    {
        if (form == null)
        {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        /* Only bake on the client render/logic thread; integrated server keeps using static pose bake. */
        if (client == null || !client.isOnThread())
        {
            return null;
        }

        FormRenderer renderer = FormUtilsClient.getRenderer(form);

        if (!(renderer instanceof ModelFormRenderer modelRenderer))
        {
            return null;
        }

        ModelInstance instance = modelRenderer.getModel();

        if (instance == null || instance.model == null)
        {
            return null;
        }

        Vector3f scale = instance.scale != null ? instance.scale : new Vector3f(1F);

        /* Same temporary state merge as FormRenderer.render. */
        form.applyStates(0F);

        try
        {
            Pose pose = modelRenderer.getPose();
            IAnimator animator = modelRenderer.getAnimator();

            if (instance.model instanceof Model cubic)
            {
                synchronized (cubic)
                {
                    cubic.resetPose();

                    if (animator != null)
                    {
                        animator.applyActions(null, instance, 0F);
                    }

                    cubic.applyPose(pose);

                    return ModelCollisionData.bakeFromPosedModel(cubic, scale);
                }
            }

            if (instance.model instanceof BOBJModel bobj)
            {
                BOBJArmature armature = bobj.getArmature();
                List<BOBJLoader.CompiledData> meshes = bobj.getMeshes();

                if (armature == null || meshes == null || meshes.isEmpty())
                {
                    return null;
                }

                synchronized (bobj)
                {
                    bobj.resetPose();

                    if (animator != null)
                    {
                        animator.applyActions(null, instance, 0F);
                    }

                    bobj.applyPose(pose);
                    armature.setupMatrices();

                    return ModelCollisionData.bakeFromSkinnedBobj(meshes.get(0), armature, scale);
                }
            }
        }
        finally
        {
            form.unapplyStates();
        }

        return null;
    }
}
