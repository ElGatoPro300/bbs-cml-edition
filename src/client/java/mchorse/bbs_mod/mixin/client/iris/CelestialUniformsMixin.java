package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.client.SunPathRotation;

import net.irisshaders.iris.uniforms.CelestialUniforms;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Yaws Iris celestial / shadow-light vectors with the sky yaw ({@link SunPathRotation#getDegrees()}).
 * <p>
 * Must match the world light implied by {@code ShadowMatricesMixin}
 * ({@code MODELVIEW * R_y(lightYaw)} ⇒ light {@code R_y(sky) * sun}), and the GLSL
 * {@code bbsApplySunPathYaw} convention (angle={@code lightYaw}, formula ≡ {@code R_y(sky)}).
 * Using light yaw here made frustum culling fight the shadow map and caused long shadows to pop.
 */
@Mixin(value = CelestialUniforms.class, remap = false)
public class CelestialUniformsMixin
{
    @WrapOperation(
        method = "getCelestialPosition",
        at = @At(value = "NEW", target = "(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;"),
        require = 0
    )
    private Matrix4f bbs$applySunPathToViewMatrix(Matrix4fc source, Operation<Matrix4f> original)
    {
        Matrix4f matrix = original.call(source);

        SunPathRotation.applyY(matrix);

        return matrix;
    }

    @Inject(method = "getCelestialPositionInWorldSpace", at = @At("RETURN"), require = 0)
    private void bbs$applySunPathToWorldPosition(float y, CallbackInfoReturnable<Vector4f> info)
    {
        SunPathRotation.applyY(info.getReturnValue());
    }
}
