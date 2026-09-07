package mchorse.bbs_mod.forms.renderers.utils;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Adapts vanilla head-mounted item placement
 * ({@code HeadFeatureRenderer} + {@link ModelTransformationMode#HEAD}, and the spyglass path
 * from {@code PlayerHeldItemFeatureRenderer.renderSpyglass}) to BBS ModelForm head bone matrices.
 * <p>
 * {@code HeadFeatureRenderer.translate} / skull pre-transforms cannot be called as-is: BBS
 * {@code captureMatrices} already bake {@code rotateY(PI)}, and bone attachment space needs the
 * same {@code Rx(180)} that armor uses to reach ModelPart-like orientation.
 */
public final class BbsHeadItemSpace
{
    /** {@code PlayerHeldItemFeatureRenderer} HEAD_YAW. */
    public static final float SPYGLASS_LOOK_PITCH_MIN = -30F;
    /** {@code PlayerHeldItemFeatureRenderer} HEAD_ROLL. */
    public static final float SPYGLASS_LOOK_PITCH_MAX = 90F;

    /** Vanilla ±2.5/16 lateral bias by using arm. */
    private static final float ARM_BIAS = 2.5F / 16F;
    /** Neck pivot → eye line (3px − ¼px); spyglass-only substitute for {@code T(0,-0.25)}. */
    private static final float EYE_Y = 2.75F / 16F;
    /** {@code HeadFeatureRenderer.translate} uniform scale. */
    private static final float HAT_SCALE = 0.625F;
    /** Vanilla skull branch scale in {@code HeadFeatureRenderer}. */
    private static final float SKULL_SCALE = 1.1875F;
    /** Vanilla post-hat Y bias (−1/16). */
    private static final float HAT_Y = -1F / 16F;

    private BbsHeadItemSpace()
    {}

    public static float clampSpyglassLookPitch(float lookPitchDeg)
    {
        return MathHelper.clamp(lookPitchDeg, SPYGLASS_LOOK_PITCH_MIN, SPYGLASS_LOOK_PITCH_MAX);
    }

    /**
     * Align BBS head-bone attachment space with vanilla {@code ModelPart} head space
     * (same fix armor uses after {@code captureMatrices}).
     */
    private static void alignBoneToModelPart(MatrixStack stack)
    {
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180F));
    }

    /**
     * Vanilla {@code HeadFeatureRenderer.translate} adapted for ModelForm head bones (non-skull
     * items). Call after {@code MatrixStackUtils.multiply(stack, headBoneMatrix)}.
     */
    public static void applyHeadItem(MatrixStack stack)
    {
        alignBoneToModelPart(stack);

        /* Vanilla HeadFeatureRenderer.translate — Ry(180) omitted: captureMatrices baked it. */
        stack.translate(0F, -0.25F, 0F);
        stack.scale(HAT_SCALE, -HAT_SCALE, -HAT_SCALE);
    }

    /**
     * Vanilla skull branch pre-transform in {@code HeadFeatureRenderer} (scale 1.1875 +
     * {@code T(-0.5,0,-0.5)} before {@code SkullBlockEntityRenderer.renderSkull}).
     */
    public static void applySkull(MatrixStack stack)
    {
        alignBoneToModelPart(stack);

        stack.scale(SKULL_SCALE, -SKULL_SCALE, -SKULL_SCALE);
        stack.translate(-0.5F, 0F, -0.5F);
    }

    /**
     * Call after {@code MatrixStackUtils.multiply(stack, headBoneMatrix)}.
     * Stack is then ready for {@link #spyglassTransformationMode()} with
     * {@link #spyglassLeftHanded()}.
     * <p>
     * Spyglass must keep the dedicated eye placement path. Reusing {@link #applyHeadItem}'s
     * {@code Rx(180)} + negative hat scale inverts {@link ModelTransformationMode#HEAD} local
     * space and parks the barrel behind the head. Texture self-roll is fixed with {@code Rz(180)}
     * after placement instead.
     *
     * @param lookPitchDeg entity look pitch in degrees (positive = look down)
     * @param leftArm whether the active arm is the left (main-arm aware)
     */
    public static void applySpyglass(MatrixStack stack, float lookPitchDeg, boolean leftArm)
    {
        float clamped = clampSpyglassLookPitch(lookPitchDeg);

        /* captureMatrices attachment space: M_want = M * Rx(clamped − actual). */
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(clamped - lookPitchDeg));

        /* Neck → eye. Do not use HeadFeatureRenderer's T(0,−0.25) / applyHeadItem here. */
        stack.translate(0F, EYE_Y, 0F);

        /* Remainder of HeadFeatureRenderer.translate. Y180 is omitted because
         * captureMatrices already baked rotateY(PI) onto the bone matrix. */
        stack.scale(HAT_SCALE, -HAT_SCALE, -HAT_SCALE);
        /* Arm bias: vanilla left − / right +; BBS bake + negative hat scale flip X. */
        stack.translate(leftArm ? ARM_BIAS : -ARM_BIAS, HAT_Y, 0F);

        /* Cancel extra 180° barrel/texture roll without moving the eyepiece. */
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180F));
    }

    /**
     * Vanilla {@code HeadFeatureRenderer} / spyglass always uses {@link ModelTransformationMode#HEAD}.
     */
    public static ModelTransformationMode headItemTransformationMode()
    {
        return ModelTransformationMode.HEAD;
    }

    /**
     * Vanilla always uses {@link ModelTransformationMode#HEAD} for an active spyglass
     * (display: rotation 90°, translation [0,0,−16], scale 1.6).
     */
    public static ModelTransformationMode spyglassTransformationMode()
    {
        return headItemTransformationMode();
    }

    /**
     * Vanilla {@code HeldItemRenderer.renderItem} always passes {@code false} for HEAD mode.
     */
    public static boolean headItemLeftHanded()
    {
        return false;
    }

    /**
     * Vanilla {@code HeldItemRenderer.renderItem} always passes {@code false} for spyglass HEAD.
     */
    public static boolean spyglassLeftHanded()
    {
        return headItemLeftHanded();
    }
}
