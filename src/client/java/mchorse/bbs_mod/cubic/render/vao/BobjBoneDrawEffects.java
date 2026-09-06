package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Lerps;

import net.minecraft.client.render.LightmapTextureManager;

/**
 * Per-bone paint / glow / tint / grade uniforms for BOBJ skinned meshes. BOBJ shares one VAO
 * across bones, so draws are split by dominant bone (same pattern as bone texture overrides).
 */
public final class BobjBoneDrawEffects
{
    private BobjBoneDrawEffects()
    {}

    public static boolean hasCustomColorEffects(BOBJBone bone)
    {
        if (bone == null)
        {
            return false;
        }

        if (bone.color.r != 1F || bone.color.g != 1F || bone.color.b != 1F || bone.color.a != 1F)
        {
            return true;
        }

        if (bone.color.hasActiveTransform() || bone.color.hasColorAdjustments())
        {
            return true;
        }

        if (bone.paintColor.a != 0F || EffectTransformMath.isTransformActive(bone.paintColor.transform))
        {
            return true;
        }

        if (bone.glowIntensity != 0F || EffectTransformMath.isTransformActive(bone.glowingColor.transform))
        {
            return true;
        }

        if (bone.lighting != 0F)
        {
            return true;
        }

        return false;
    }

    public static void applyGroupUniforms(BOBJBone bone)
    {
        float effectiveGlowStrength = resolveEffectiveGlowStrength(bone);
        float effectiveGlowR = resolveEffectiveGlowR(bone);
        float effectiveGlowG = resolveEffectiveGlowG(bone);
        float effectiveGlowB = resolveEffectiveGlowB(bone);
        float effectivePaintStrength = resolveEffectivePaintStrength(bone);
        float effectivePaintR = resolveEffectivePaintR(bone);
        float effectivePaintG = resolveEffectivePaintG(bone);
        float effectivePaintB = resolveEffectivePaintB(bone);

        ModelVAORenderer.setGroupPaint(effectivePaintR, effectivePaintG, effectivePaintB, effectivePaintStrength);
        ModelVAORenderer.setGroupPaintEffectTransform(bone.paintColor.transform);
        ModelVAORenderer.setGroupGlowing(effectiveGlowR, effectiveGlowG, effectiveGlowB, effectiveGlowStrength);
        ModelVAORenderer.setGroupGlowEffectTransform(bone.glowingColor.transform);
        ModelVAORenderer.setGroupFormColorGrade(bone.color);
        ModelVAORenderer.setGroupColorEffectTransform(bone.color.transform);
        ModelVAORenderer.setGroupFormColorTint(bone.color);
    }

    public static void restoreGroupUniforms()
    {
        ModelVAORenderer.setGroupPaint(
            ModelVAORenderer.getBasePaintR(),
            ModelVAORenderer.getBasePaintG(),
            ModelVAORenderer.getBasePaintB(),
            ModelVAORenderer.getBasePaintStrength()
        );
        ModelVAORenderer.setGroupPaintEffectTransform(null);
        ModelVAORenderer.setGroupGlowing(
            ModelVAORenderer.getBaseGlowingR(),
            ModelVAORenderer.getBaseGlowingG(),
            ModelVAORenderer.getBaseGlowingB(),
            ModelVAORenderer.getBaseGlowingStrength()
        );
        ModelVAORenderer.setGroupGlowEffectTransform(null);
        ModelVAORenderer.setGroupFormColorGrade(null);
        ModelVAORenderer.setGroupColorEffectTransform(null);
        ModelVAORenderer.setGroupFormColorTint(null);
    }

    public static void computeDrawColor(BOBJBone bone, float baseR, float baseG, float baseB, float baseA, Color output)
    {
        float r;
        float g;
        float b;
        float a;

        if (bone.color.hasActiveTransform())
        {
            r = baseR;
            g = baseG;
            b = baseB;
            a = baseA;
        }
        else
        {
            r = baseR * bone.color.r;
            g = baseG * bone.color.g;
            b = baseB * bone.color.b;
            a = baseA * bone.color.a;
        }

        float effectiveGlowStrength = resolveEffectiveGlowStrength(bone);
        boolean boneGlowMaskActive = bone.glowingColor != null
            && bone.glowingColor.transform != null
            && bone.glowingColor.transform.isActive();

        if (!ModelVAORenderer.isGlowingUniformActive())
        {
            if (effectiveGlowStrength != 0F && !boneGlowMaskActive && !ModelVAORenderer.isGlowEffectActive())
            {
                Color groupColor = output.set(r, g, b, a);
                Color glowColor = new Color().set(
                    resolveEffectiveGlowR(bone),
                    resolveEffectiveGlowG(bone),
                    resolveEffectiveGlowB(bone),
                    1F
                );

                FormColorEffects.blendBrighten(groupColor, glowColor, effectiveGlowStrength);

                r = groupColor.r;
                g = groupColor.g;
                b = groupColor.b;
                a = groupColor.a;
            }
        }

        output.set(r, g, b, a);
    }

    public static int computeDrawLight(BOBJBone bone, int light, StencilMap stencilMap)
    {
        float effectiveGlowStrength = resolveEffectiveGlowStrength(bone);
        boolean boneGlowMaskActive = bone.glowingColor != null
            && bone.glowingColor.transform != null
            && bone.glowingColor.transform.isActive();
        int groupLight = light;

        if (effectiveGlowStrength != 0F
            && !ModelVAORenderer.isGlowingUniformActive()
            && !ModelVAORenderer.isPaintOverlayPass()
            && !boneGlowMaskActive
            && !ModelVAORenderer.isGlowEffectActive())
        {
            float glowLightT = MathUtils.clamp(Math.abs(effectiveGlowStrength), 0F, 1F);
            int baseU = groupLight & '\uffff';
            int u = (int) Lerps.lerp(baseU, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, glowLightT);
            int v = groupLight >> 16 & '\uffff';

            groupLight = u | v << 16;
        }

        if (stencilMap != null)
        {
            groupLight = stencilMap.increment ? bone.index : 0;
        }
        else if (bone.lighting != 0F)
        {
            int u = (int) Lerps.lerp(groupLight & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(bone.lighting, 0F, 1F));
            int v = groupLight >> 16 & '\uffff';

            groupLight = u | v << 16;
        }

        return groupLight;
    }

    private static float resolveEffectiveGlowStrength(BOBJBone bone)
    {
        if (bone.glowIntensity != 0F)
        {
            return bone.glowIntensity;
        }

        return ModelVAORenderer.getBaseGlowingStrength();
    }

    private static float resolveEffectiveGlowR(BOBJBone bone)
    {
        if (bone.glowIntensity != 0F)
        {
            return bone.glowingColor.r;
        }

        return ModelVAORenderer.getBaseGlowingR();
    }

    private static float resolveEffectiveGlowG(BOBJBone bone)
    {
        if (bone.glowIntensity != 0F)
        {
            return bone.glowingColor.g;
        }

        return ModelVAORenderer.getBaseGlowingG();
    }

    private static float resolveEffectiveGlowB(BOBJBone bone)
    {
        if (bone.glowIntensity != 0F)
        {
            return bone.glowingColor.b;
        }

        return ModelVAORenderer.getBaseGlowingB();
    }

    private static float resolveEffectivePaintStrength(BOBJBone bone)
    {
        if (bone.paintColor.a != 0F)
        {
            return bone.paintColor.a;
        }

        return ModelVAORenderer.getBasePaintStrength();
    }

    private static float resolveEffectivePaintR(BOBJBone bone)
    {
        if (bone.paintColor.a != 0F)
        {
            return bone.paintColor.r;
        }

        return ModelVAORenderer.getBasePaintR();
    }

    private static float resolveEffectivePaintG(BOBJBone bone)
    {
        if (bone.paintColor.a != 0F)
        {
            return bone.paintColor.g;
        }

        return ModelVAORenderer.getBasePaintG();
    }

    private static float resolveEffectivePaintB(BOBJBone bone)
    {
        if (bone.paintColor.a != 0F)
        {
            return bone.paintColor.b;
        }

        return ModelVAORenderer.getBasePaintB();
    }
}
