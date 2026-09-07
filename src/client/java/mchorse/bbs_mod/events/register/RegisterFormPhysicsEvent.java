package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Event for physics engines and ragdoll simulations to intercept form transforms and model bone poses.
 */
public class RegisterFormPhysicsEvent
{
    @FunctionalInterface
    public interface FormTransformStackInterceptor
    {
        /**
         * @return true if the transform was handled/substituted, cancelling the default transform.
         */
        public boolean onApplyTransform(FormRenderer<?> renderer, Form form, MatrixStack stack, boolean origin, float transition);
    }

    @FunctionalInterface
    public interface FormTransformMatrixInterceptor
    {
        /**
         * @return true if the matrix transform was handled/substituted, cancelling the default transform.
         */
        public boolean onApplyTransform(FormRenderer<?> renderer, Form form, Matrix4f matrix, float transition);
    }

    @FunctionalInterface
    public interface ModelRagdollPoseApplier
    {
        public void onApplyRagdollPose(ModelFormRenderer renderer, ModelForm form, ModelInstance model, float transition);
    }

    private static final List<FormTransformStackInterceptor> stackInterceptors = new ArrayList<>();
    private static final List<FormTransformMatrixInterceptor> matrixInterceptors = new ArrayList<>();
    private static final List<ModelRagdollPoseApplier> ragdollPoseAppliers = new ArrayList<>();

    public void registerStackTransform(FormTransformStackInterceptor interceptor)
    {
        if (interceptor != null)
        {
            stackInterceptors.add(interceptor);
        }
    }

    public void registerMatrixTransform(FormTransformMatrixInterceptor interceptor)
    {
        if (interceptor != null)
        {
            matrixInterceptors.add(interceptor);
        }
    }

    public void registerRagdollPose(ModelRagdollPoseApplier applier)
    {
        if (applier != null)
        {
            ragdollPoseAppliers.add(applier);
        }
    }

    public static boolean postStackTransform(FormRenderer<?> renderer, Form form, MatrixStack stack, boolean origin, float transition)
    {
        for (FormTransformStackInterceptor interceptor : stackInterceptors)
        {
            try
            {
                if (interceptor.onApplyTransform(renderer, form, stack, origin, transition))
                {
                    return true;
                }
            }
            catch (Throwable ignored)
            {}
        }

        return false;
    }

    public static boolean postMatrixTransform(FormRenderer<?> renderer, Form form, Matrix4f matrix, float transition)
    {
        for (FormTransformMatrixInterceptor interceptor : matrixInterceptors)
        {
            try
            {
                if (interceptor.onApplyTransform(renderer, form, matrix, transition))
                {
                    return true;
                }
            }
            catch (Throwable ignored)
            {}
        }

        return false;
    }

    public static void postRagdollPose(ModelFormRenderer renderer, ModelForm form, ModelInstance model, float transition)
    {
        for (ModelRagdollPoseApplier applier : ragdollPoseAppliers)
        {
            try
            {
                applier.onApplyRagdollPose(renderer, form, model, transition);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
