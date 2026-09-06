#version 330

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float TextureBlendFactor;
uniform float TextureBlendActive;

/* rgb = paint color, a = paint strength (−1 = full darken, 0 = off, 1 = full override).
   PaintOverlay = 1 during Iris second pass. */
uniform vec4 PaintColor;
uniform vec4 GlowingColor;
uniform float PaintOverlay;
uniform float GlowPaintOnly;

uniform mat4 PaintEffectInverse;
uniform float PaintEffectActive;
uniform vec3 PaintMaskHalf;
uniform float PaintMaskBottomAnchored;
uniform float PaintMaskShape;
uniform mat4 GlowEffectInverse;
uniform float GlowEffectActive;
uniform vec3 GlowMaskHalf;
uniform float GlowMaskBottomAnchored;
uniform float GlowMaskShape;
uniform mat4 ColorEffectInverse;
uniform float ColorEffectActive;
uniform vec3 ColorMaskHalf;
uniform float ColorMaskBottomAnchored;
uniform float ColorMaskShape;
uniform vec4 FormColorTint;
uniform float ColorTintMasked;
/* 1 = multiply Iris-lit framebuffer by FormColorTint inside the color mask (keeps pack lighting). */
uniform float ColorTintOverlay;
/* 1 = replace Iris-lit model pixels with FormColorGrade(sceneColor) — keeps pack lighting/shadows. */
uniform float ColorGradeOverlay;
/* x = brightness, y = contrast, z = hue degrees, w = saturation. Neutral = 0. */
uniform vec4 FormColorGrade;
uniform mat4 GradeBrightnessInverse;
uniform float GradeBrightnessActive;
uniform vec3 GradeBrightnessHalf;
uniform float GradeBrightnessBottomAnchored;
uniform float GradeBrightnessShape;
uniform mat4 GradeContrastInverse;
uniform float GradeContrastActive;
uniform vec3 GradeContrastHalf;
uniform float GradeContrastBottomAnchored;
uniform float GradeContrastShape;
uniform mat4 GradeHueInverse;
uniform float GradeHueActive;
uniform vec3 GradeHueHalf;
uniform float GradeHueBottomAnchored;
uniform float GradeHueShape;
uniform mat4 GradeSaturationInverse;
uniform float GradeSaturationActive;
uniform vec3 GradeSaturationHalf;
uniform float GradeSaturationBottomAnchored;
uniform float GradeSaturationShape;

in float vertexDistance;
in vec4 vertexColor;
in vec4 rawVertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 formRootPos;

out vec4 fragColor;

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor)
{
    if (vertexDistance <= fogStart)
    {
        return inColor;
    }

    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

vec3 bbsRgb2Hsl(vec3 c)
{
    float maxc = max(c.r, max(c.g, c.b));
    float minc = min(c.r, min(c.g, c.b));
    float l = (maxc + minc) * 0.5;
    float d = maxc - minc;

    if (d < 1e-5)
    {
        return vec3(0.0, 0.0, l);
    }

    float s = l > 0.5 ? d / (2.0 - maxc - minc) : d / (maxc + minc);
    float h;

    if (maxc == c.r)
    {
        h = (c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0);
    }
    else if (maxc == c.g)
    {
        h = (c.b - c.r) / d + 2.0;
    }
    else
    {
        h = (c.r - c.g) / d + 4.0;
    }

    return vec3(h / 6.0, s, l);
}

float bbsHue2Rgb(float p, float q, float t)
{
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
    if (t < 0.5) return q;
    if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;

    return p;
}

vec3 bbsHsl2Rgb(vec3 hsl)
{
    float h = hsl.x;
    float s = hsl.y;
    float l = hsl.z;

    if (s < 1e-5)
    {
        return vec3(l);
    }

    float q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
    float p = 2.0 * l - q;

    return vec3(
        bbsHue2Rgb(p, q, h + 1.0 / 3.0),
        bbsHue2Rgb(p, q, h),
        bbsHue2Rgb(p, q, h - 1.0 / 3.0)
    );
}

float bbsSdTriangle2D(vec2 p, vec2 a, vec2 b, vec2 c)
{
    vec2 e0 = b - a;
    vec2 e1 = c - b;
    vec2 e2 = a - c;
    vec2 v0 = p - a;
    vec2 v1 = p - b;
    vec2 v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / max(dot(e0, e0), 0.0001), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / max(dot(e1, e1), 0.0001), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / max(dot(e2, e2), 0.0001), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));

    return -sqrt(max(d.x, 0.0)) * sign(d.y);
}

float bbsPaintEffectMask(vec3 rootPos, mat4 effectInverse, float activeFlag, vec3 halfExtents, float bottomAnchored, float shape)
{
    if (activeFlag < 0.5)
    {
        return 1.0;
    }

    vec3 local = (effectInverse * vec4(rootPos, 1.0)).xyz;

    if (bottomAnchored > 0.5)
    {
        local.y -= halfExtents.y;
    }

    float dist;
    float maxHalf = max(halfExtents.x, max(halfExtents.y, halfExtents.z));

    /* Scale 0 → empty mask. */
    if (maxHalf < 0.001)
    {
        return 0.0;
    }

    if (shape > 1.5)
    {
        /* Front-facing triangle in XY (apex up), thickness along Z. */
        vec2 halfXY = max(halfExtents.xy, vec2(0.001));
        vec2 a = vec2(0.0, halfXY.y);
        vec2 b = vec2(-halfXY.x, -halfXY.y);
        vec2 c = vec2(halfXY.x, -halfXY.y);
        float dTri = bbsSdTriangle2D(local.xy, a, b, c);
        float dZ = abs(local.z) - halfExtents.z;

        dist = length(max(vec2(dTri, dZ), 0.0)) + min(max(dTri, dZ), 0.0);
    }
    else if (shape > 0.5)
    {
        /* Soft ellipsoid sized by half extents. */
        vec3 safeHalf = max(halfExtents, vec3(0.001));
        float radius = length(local / safeHalf);

        dist = (radius - 1.0) * maxHalf;
    }
    else
    {
        vec3 d = abs(local) - halfExtents;

        dist = length(max(d, 0.0)) + min(max(max(d.x, d.y), d.z), 0.0);
    }

    float falloff = max(maxHalf * 0.15, 0.001);

    return 1.0 - smoothstep(0.0, falloff, dist);
}

vec3 bbsPreserveLitShadow(vec3 inputRgb, vec3 gradedRgb)
{
    return gradedRgb;
}

vec3 bbsApplyFormColorGrade(vec3 rgb, vec3 rootPos)
{
    if (abs(FormColorGrade.x) < 0.001 && abs(FormColorGrade.y) < 0.001 && abs(FormColorGrade.z) < 0.001 && abs(FormColorGrade.w) < 0.001)
    {
        return rgb;
    }

    vec3 outRgb = rgb;

    if (abs(FormColorGrade.x) >= 0.001)
    {
        float mask = bbsPaintEffectMask(rootPos, GradeBrightnessInverse, GradeBrightnessActive, GradeBrightnessHalf, GradeBrightnessBottomAnchored, GradeBrightnessShape);
        vec3 next = outRgb + FormColorGrade.x;

        outRgb = mix(outRgb, next, mask);
    }

    if (abs(FormColorGrade.y) >= 0.001)
    {
        float mask = bbsPaintEffectMask(rootPos, GradeContrastInverse, GradeContrastActive, GradeContrastHalf, GradeContrastBottomAnchored, GradeContrastShape);
        vec3 next = vec3(0.5) + (1.0 + FormColorGrade.y) * (outRgb - vec3(0.5));

        outRgb = mix(outRgb, next, mask);
    }

    if (abs(FormColorGrade.w) >= 0.001)
    {
        float mask = bbsPaintEffectMask(rootPos, GradeSaturationInverse, GradeSaturationActive, GradeSaturationHalf, GradeSaturationBottomAnchored, GradeSaturationShape);
        vec3 hsl = bbsRgb2Hsl(clamp(outRgb, 0.0, 1.0));

        hsl.y = clamp(hsl.y * (1.0 + FormColorGrade.w), 0.0, 1.0);
        vec3 next = bbsHsl2Rgb(hsl);

        outRgb = mix(outRgb, next, mask);
    }

    if (abs(FormColorGrade.z) > 0.01)
    {
        float mask = bbsPaintEffectMask(rootPos, GradeHueInverse, GradeHueActive, GradeHueHalf, GradeHueBottomAnchored, GradeHueShape);
        vec3 hsl = bbsRgb2Hsl(clamp(outRgb, 0.0, 1.0));

        hsl.x = fract(hsl.x + FormColorGrade.z / 360.0);
        outRgb = mix(outRgb, bbsHsl2Rgb(hsl), mask);
    }

    return clamp(outRgb, 0.0, 1.0);
}

vec3 bbsApplyGlow(vec3 color, float strength)
{
    if (abs(strength) < 0.001)
    {
        return color;
    }

    vec3 glowRgb = GlowingColor.rgb;

    if (strength > 0.0)
    {
        if (strength >= 1.0)
        {
            return color + glowRgb * strength;
        }

        vec3 emissive = color + glowRgb;

        return mix(color, emissive, strength);
    }

    float factor = max(0.0, 1.0 + strength);

    return color * factor;
}

void main()
{
    vec4 texSample = texture(Sampler0, texCoord0);

    if (TextureBlendActive > 0.5)
    {
        vec4 texBlend = texture(Sampler3, texCoord0);
        float blendFactor = TextureBlendFactor;
        float fromA = texSample.a;
        float toA = texBlend.a;

        texSample.rgb = mix(texSample.rgb, texBlend.rgb, blendFactor);
        /* Per-pixel crossfade: shared opaque pixels stay solid; exclusive pixels fade independently. */
        texSample.a = fromA * (1.0 - blendFactor) + toA * blendFactor;
    }

    /* Iris color-grade overlay: regrade already-lit pack pixels (Sampler3 = scene copy).
       Keeps Complementary/BSL lighting/shadows. Each grade channel has its own Transform mask. */
    if (ColorGradeOverlay > 0.5)
    {
        if (texSample.a < 0.1)
        {
            discard;
        }

        ivec2 sceneSize = textureSize(Sampler3, 0);
        vec2 sceneUv = clamp(gl_FragCoord.xy / vec2(max(sceneSize, ivec2(1))), vec2(0.0), vec2(1.0));
        vec3 lit = textureLod(Sampler3, sceneUv, 0.0).rgb;

        fragColor = vec4(bbsApplyFormColorGrade(lit, formRootPos), 1.0);

        return;
    }

    /* Iris color-mask overlay: multiply already-lit pack shading by FormColorTint inside the
       spatial mask (blend DST_COLOR/ZERO). Outside the mask this pass discards. */
    if (ColorTintOverlay > 0.5)
    {
        if (texSample.a < 0.1)
        {
            discard;
        }

        float cmask = bbsPaintEffectMask(formRootPos, ColorEffectInverse, ColorEffectActive, ColorMaskHalf, ColorMaskBottomAnchored, ColorMaskShape);

        if (cmask < 0.001)
        {
            discard;
        }

        /* FormColorTint.a is traditional form opacity — fade mask tint with the form. */
        float opacity = clamp(FormColorTint.a, 0.0, 1.0);
        float strength = cmask * opacity;
        vec3 tintRgb = mix(vec3(1.0), FormColorTint.rgb, strength);

        fragColor = vec4(bbsApplyFormColorGrade(tintRgb, formRootPos), 1.0);

        return;
    }

    /* Shader-pack paint overlay pass: alpha-blend over the Iris first pass.
       Positive: mix toward PaintColor.rgb (SRC_ALPHA ≈ mix(lit, paint, strength)).
       Negative: darken lit dst toward black (SRC_ALPHA black ≈ multiply by 1+|a|). */
    if (PaintOverlay > 0.5)
    {
        if (abs(PaintColor.a) < 0.001)
        {
            discard;
        }

        if (texSample.a < 0.1)
        {
            discard;
        }

        float paintStrength = clamp(abs(PaintColor.a), 0.0, 1.0);

        paintStrength *= bbsPaintEffectMask(formRootPos, PaintEffectInverse, PaintEffectActive, PaintMaskHalf, PaintMaskBottomAnchored, PaintMaskShape);

        float outAlpha = paintStrength * texSample.a * rawVertexColor.a * ColorModulator.a;

        if (outAlpha < 0.001)
        {
            discard;
        }

        vec3 outRgb;

        if (PaintColor.a < 0.0)
        {
            outRgb = vec3(0.0);
        }
        else
        {
            outRgb = PaintColor.rgb;

            if (abs(GlowingColor.a) > 0.001)
            {
                float glowStrength = GlowingColor.a;

                if (GlowPaintOnly > 0.5)
                {
                    glowStrength *= paintStrength;
                }

                glowStrength *= bbsPaintEffectMask(formRootPos, GlowEffectInverse, GlowEffectActive, GlowMaskHalf, GlowMaskBottomAnchored, GlowMaskShape);
                outRgb = bbsApplyGlow(outRgb, glowStrength);
            }
        }

        vec4 color = vec4(outRgb, outAlpha);

        fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);

        return;
    }

    /* Texture cutout only (same as no-shader). Form/vertex opacity is kept — Iris packs
     * discard low vertex alpha; this shader must not. */
    if (texSample.a < 0.1)
    {
        discard;
    }

    vec4 color = texSample;
    vec4 formTint = vec4(1.0);

    if (ColorTintMasked > 0.5)
    {
        float cmask = bbsPaintEffectMask(formRootPos, ColorEffectInverse, ColorEffectActive, ColorMaskHalf, ColorMaskBottomAnchored, ColorMaskShape);

        formTint.rgb = mix(vec3(1.0), FormColorTint.rgb, cmask);
        formTint.a = mix(1.0, FormColorTint.a, cmask);
        color *= vertexColor * formTint * ColorModulator;
    }
    else
    {
        color *= vertexColor * ColorModulator;
    }

    /* Paint strength must not change geometry alpha; only texture + vertex tint define opacity. */
    float modelAlpha = texSample.a * rawVertexColor.a * formTint.a * ColorModulator.a;

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    float paintStrength = clamp(abs(PaintColor.a), 0.0, 1.0);
    paintStrength *= bbsPaintEffectMask(formRootPos, PaintEffectInverse, PaintEffectActive, PaintMaskHalf, PaintMaskBottomAnchored, PaintMaskShape);

    color.rgb *= lightMapColor.rgb;

    /* Paint replaces the lit texture toward a flat paint color; strength 1 = no skin/texture visible. */
    if (PaintColor.a > 0.001)
    {
        color.rgb = mix(color.rgb, PaintColor.rgb, paintStrength);
    }
    else if (PaintColor.a < -0.001)
    {
        /* Negative darken like glow; mask 0 must leave the surface unchanged (same as positive). */
        float factor = max(0.0, 1.0 + PaintColor.a);
        float mask = bbsPaintEffectMask(formRootPos, PaintEffectInverse, PaintEffectActive, PaintMaskHalf, PaintMaskBottomAnchored, PaintMaskShape);

        color.rgb = mix(color.rgb, color.rgb * factor, mask);
    }

    color.a = modelAlpha;

    float strength = GlowingColor.a;

    if (GlowPaintOnly > 0.5)
    {
        strength *= paintStrength;
    }

    strength *= bbsPaintEffectMask(formRootPos, GlowEffectInverse, GlowEffectActive, GlowMaskHalf, GlowMaskBottomAnchored, GlowMaskShape);

    color.rgb = bbsApplyGlow(color.rgb, strength);

    /* Brightness/contrast/hue/saturation each respect their own Transform mask. */
    color.rgb = bbsApplyFormColorGrade(color.rgb, formRootPos);

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
