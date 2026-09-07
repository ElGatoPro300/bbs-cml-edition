package mchorse.bbs_mod.client.screen;

import mchorse.bbs_mod.camera.clips.screen.ColorEffect;
import mchorse.bbs_mod.camera.clips.screen.GrainEffect;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

public class ColorGradeRenderer
{
    private static final String VERT = """
            #version 150

            in vec2 a_pos;
            in vec2 a_uv;
            out vec2 v_uv;

            void main()
            {
                v_uv = a_uv;
                gl_Position = vec4(a_pos, 0.0, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 150

            in vec2 v_uv;
            out vec4 fragColor;

            uniform sampler2D u_sampler;

            /* Vignette */
            uniform float u_vigStr;
            uniform float u_vigSmooth;
            uniform vec3 u_vigColor;

            /* Color grade */
            uniform float u_brightness;
            uniform float u_contrast;
            uniform float u_saturation;
            uniform float u_hue;
            uniform vec3 u_lift;
            uniform vec3 u_gamma;
            uniform vec3 u_gain;

            /* Film grain */
            uniform float u_grainStr;
            uniform float u_grainSize;
            uniform float u_grainSeed;

            /* UV distortion */
            uniform vec2 u_distort;

            /* Cinematic effects */
            uniform float u_aberration;
            uniform float u_aberrationAngle;
            uniform float u_aberrationDirectional;
            uniform float u_aberrationRadius;
            uniform float u_aberrationHardness;
            uniform float u_aberrationBalance;
            uniform vec2 u_aberrationCenter;
            uniform float u_aberrationGreen;
            uniform float u_aberrationSpectrum;
            uniform float u_vhs;
            uniform float u_lensDistortion;
            uniform float u_lensRadiusX;
            uniform float u_lensRadiusY;
            uniform float u_lensHardness;
            uniform float u_lensSharpen;
            uniform float u_vintage;
            uniform float u_radialBlur;
            uniform float u_rain;
            uniform float u_dust;
            uniform float u_lightLeak;
            uniform float u_heatStrength;
            uniform float u_heatSpeed;
            uniform float u_heatScale;
            uniform float u_time;

            /* --- HSL helpers --- */

            vec3 rgb2hsl(vec3 c)
            {
                float maxC = max(c.r, max(c.g, c.b));
                float minC = min(c.r, min(c.g, c.b));
                float delta = maxC - minC;
                float l = (maxC + minC) * 0.5;
                float s = delta < 1e-5 ? 0.0 : delta / (1.0 - abs(2.0 * l - 1.0));
                float h = 0.0;
                if (delta > 1e-5)
                {
                    if      (maxC == c.r) h = mod((c.g - c.b) / delta, 6.0) / 6.0;
                    else if (maxC == c.g) h = ((c.b - c.r) / delta + 2.0) / 6.0;
                    else                  h = ((c.r - c.g) / delta + 4.0) / 6.0;
                }
                return vec3(h, s, l);
            }

            vec3 hsl2rgb(vec3 c)
            {
                float h = c.x, s = c.y, l = c.z;
                float C = (1.0 - abs(2.0 * l - 1.0)) * s;
                float X = C * (1.0 - abs(mod(h * 6.0, 2.0) - 1.0));
                float m = l - C * 0.5;
                vec3 rgb;
                if      (h < 1.0 / 6.0) rgb = vec3(C, X, 0.0);
                else if (h < 2.0 / 6.0) rgb = vec3(X, C, 0.0);
                else if (h < 3.0 / 6.0) rgb = vec3(0.0, C, X);
                else if (h < 4.0 / 6.0) rgb = vec3(0.0, X, C);
                else if (h < 5.0 / 6.0) rgb = vec3(X, 0.0, C);
                else                     rgb = vec3(C, 0.0, X);
                return rgb + m;
            }

            float hash(vec2 p)
            {
                return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
            }

            float heatNoise(vec2 p)
            {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);

                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));

                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            float heatFbm(vec2 p)
            {
                float value = 0.0;
                float amplitude = 0.5;
                float frequency = 1.0;

                for (int i = 0; i < 4; i++)
                {
                    value += amplitude * heatNoise(p * frequency);
                    frequency *= 2.0;
                    amplitude *= 0.5;
                }

                return value;
            }

            void main()
            {
                /* Fisheye on the copied native-FOV frame. Radius/hardness define a
                 * local lens mask; the warp is fit in UV space so it never stretches
                 * framebuffer edges into the rim. */
                vec2 distortedUV = v_uv;
                float lensMask = 0.0;
                if (abs(u_lensDistortion) > 0.001 && (u_lensRadiusX > 0.001 || u_lensRadiusY > 0.001))
                {
                    vec2 uvOffset = v_uv - vec2(0.5);
                    float k = u_lensDistortion;
                    float cornerRadius = 0.70710678;
                    float radiusX = max(u_lensRadiusX * cornerRadius, 1.0e-6);
                    float radiusY = max(u_lensRadiusY * cornerRadius, 1.0e-6);
                    vec2 scaled = uvOffset / vec2(radiusX, radiusY);
                    float rNorm = length(scaled);
                    float localR2 = min(0.5, 0.5 * dot(scaled, scaled));
                    float hardness = clamp(u_lensHardness, 0.0, 1.0);
                    /* Relative feather so circular X=Y matches the previous radius behavior. */
                    float feather = (1.0 - hardness) * 0.75;

                    if (feather < 0.0001)
                    {
                        lensMask = step(rNorm, 1.0);
                    }
                    else
                    {
                        lensMask = 1.0 - smoothstep(max(0.0, 1.0 - feather), 1.0 + feather, rNorm);
                    }

                    vec2 passthroughUV = v_uv;
                    vec2 warpedUV = v_uv;

                    if (k > 0.0)
                    {
                        /* Isotropic fit (one scale for X and Y) so the warp keeps its
                         * radial direction — per-axis squash breaks the radius look.
                         * extent caps at the screen half-edge so radius>1 (common when
                         * unlocking X/Y) still keeps samples inside [0,1]. */
                        float extent = min(0.5, max(radiusX, radiusY) * (1.0 + feather));
                        float fitScale = max(1.0, extent * (1.0 + k * 0.5) / 0.5);
                        vec2 raw = uvOffset * (1.0 + k * localR2) / fitScale;
                        /* Safety: shrink isotropically if anything still exceeds the
                         * UV square (feather / extreme ellipses). Preserves angle. */
                        float box = max(abs(raw.x), abs(raw.y));

                        if (box > 0.5)
                        {
                            raw *= 0.5 / box;
                        }

                        warpedUV = raw + vec2(0.5);
                    }
                    else
                    {
                        /* Negative intensity: use 1/(1+|k|·r²) instead of 1+k·r².
                         * The linear form hits a singularity near k≈-2 (UI ≈-7.8) and
                         * then looks inverted at the rim; the reciprocal keeps zooming
                         * in smoothly for arbitrarily strong negatives. */
                        float scale = 1.0 / max(1.0 - k * localR2, 0.001);
                        vec2 raw = uvOffset * scale;
                        float box = max(abs(raw.x), abs(raw.y));

                        if (box > 0.5)
                        {
                            raw *= 0.5 / box;
                        }

                        warpedUV = raw + vec2(0.5);
                    }

                    distortedUV = mix(passthroughUV, warpedUV, clamp(lensMask, 0.0, 1.0));
                }

                vec2 sampleUV = distortedUV + u_distort;

                /* VHS Horizontal Glitch displacement before sampling */
                if (u_vhs > 0.001)
                {
                    float glitchNoise = hash(vec2(floor(sampleUV.y * 80.0), floor(u_time * 12.0)));
                    if (glitchNoise > 0.95 - (u_vhs * 0.05))
                    {
                        sampleUV.x += sin(sampleUV.y * 30.0 + u_time * 10.0) * 0.02 * u_vhs;
                    }
                }

                distortedUV = sampleUV;

                /* Lens Dirt & Rain Overlay (Procedural raindrops and static spots refraction) */
                if (u_rain > 0.001)
                {
                    // Falling rain droplets grid
                    vec2 rainUV = distortedUV * vec2(8.0, 4.5);
                    rainUV.y += u_time * 1.2;
                    vec2 cell = fract(rainUV) - vec2(0.5);
                    vec2 id = floor(rainUV);
                    float dropSeed = hash(id);
                    if (dropSeed > 0.45)
                    {
                        float size = 0.22 * (0.4 + 0.6 * sin(u_time * 1.5 + dropSeed * 6.28));
                        float d = length(cell);
                        if (d < size)
                        {
                            vec2 refractOffset = cell * (size - d) * 1.5;
                            distortedUV += refractOffset * u_rain;
                        }
                    }

                    // Static lens dirt / condensation drops
                    vec2 dirtUV = distortedUV * vec2(12.0, 9.0);
                    vec2 dirtCell = fract(dirtUV) - vec2(0.5);
                    vec2 dirtId = floor(dirtUV);
                    float dirtSeed = hash(dirtId);
                    if (dirtSeed > 0.72)
                    {
                        float d = length(dirtCell);
                        float size = 0.12 * dirtSeed;
                        if (d < size)
                        {
                            distortedUV += dirtCell * (size - d) * 0.4 * u_rain;
                        }
                    }
                }

                /* Heat distortion waves (Mine-imator style) */
                if (u_heatStrength > 0.001)
                {
                    float heatTime = u_time * u_heatSpeed;
                    vec2 distortCoord = distortedUV * u_heatScale + vec2(0.0, heatTime * 0.1);
                    float noiseX = heatFbm(distortCoord + vec2(heatTime * 0.3, 0.0));
                    float noiseY = heatFbm(distortCoord + vec2(0.0, heatTime * 0.2));
                    vec2 heatOffset = (vec2(noiseX, noiseY) * 2.0 - 1.0) * u_heatStrength;
                    distortedUV = clamp(distortedUV + heatOffset, 0.0, 1.0);
                }

                /* Chromatic Aberration splitting */
                vec2 uvRed = distortedUV;
                vec2 uvGreen = distortedUV;
                vec2 uvBlue = distortedUV;
                if (u_aberration > 0.001)
                {
                    vec2 delta = distortedUV - u_aberrationCenter;
                    float dist = length(delta);
                    vec2 radialDir = dist > 1.0e-6 ? delta / dist : vec2(1.0, 0.0);
                    float angle = radians(u_aberrationAngle);
                    vec2 linearDir = vec2(cos(angle), sin(angle));
                    vec2 splitDir = mix(radialDir, linearDir, clamp(u_aberrationDirectional, 0.0, 1.0));
                    float splitLen = length(splitDir);

                    splitDir = splitLen > 1.0e-6 ? splitDir / splitLen : radialDir;

                    float cornerRadius = 0.70710678;
                    float radius = max(u_aberrationRadius * cornerRadius, 1.0e-6);
                    float rNorm = dist / radius;
                    float hardness = clamp(u_aberrationHardness, 0.0, 1.0);
                    float feather = (1.0 - hardness) * 0.75;
                    float mask = 1.0;

                    if (u_aberrationRadius < 0.999 || feather > 0.0001)
                    {
                        if (feather < 0.0001)
                        {
                            mask = step(rNorm, 1.0);
                        }
                        else
                        {
                            mask = 1.0 - smoothstep(max(0.0, 1.0 - feather), 1.0 + feather, rNorm);
                        }
                    }

                    float amount = dist * dist * u_aberration * clamp(mask, 0.0, 1.0);
                    float balance = clamp(u_aberrationBalance, -1.0, 1.0);
                    float redScale = max(0.0, 1.0 + balance);
                    float blueScale = max(0.0, 1.0 - balance);
                    float spectrum = clamp(u_aberrationSpectrum, 0.0, 1.0);
                    float green = max(0.0, u_aberrationGreen);
                    float greenAmount = max(green, spectrum * 0.5);
                    vec2 perp = vec2(-splitDir.y, splitDir.x);
                    vec2 redDir = normalize(mix(splitDir, splitDir + perp * 0.5, spectrum));
                    vec2 blueDir = normalize(mix(-splitDir, -splitDir + perp * 0.5, spectrum));
                    vec2 greenDir = greenAmount > 1.0e-6 ? perp : vec2(0.0);

                    uvRed += redDir * amount * redScale;
                    uvBlue += blueDir * amount * blueScale;
                    uvGreen += greenDir * amount * greenAmount;
                    uvRed = clamp(uvRed, 0.0, 1.0);
                    uvGreen = clamp(uvGreen, 0.0, 1.0);
                    uvBlue = clamp(uvBlue, 0.0, 1.0);
                }

                float r = texture(u_sampler, uvRed).r;
                float g = texture(u_sampler, uvGreen).g;
                float b = texture(u_sampler, uvBlue).b;
                vec3 rgb = vec3(r, g, b);

                /* Radial center sharpen for positive fisheye (soft center from FOV-widen). */
                if (u_lensSharpen > 0.001 && u_lensDistortion > 0.001)
                {
                    vec2 texel = 1.0 / vec2(textureSize(u_sampler, 0));
                    vec3 blur = texture(u_sampler, clamp(distortedUV + vec2(texel.x, 0.0), 0.0, 1.0)).rgb
                        + texture(u_sampler, clamp(distortedUV - vec2(texel.x, 0.0), 0.0, 1.0)).rgb
                        + texture(u_sampler, clamp(distortedUV + vec2(0.0, texel.y), 0.0, 1.0)).rgb
                        + texture(u_sampler, clamp(distortedUV - vec2(0.0, texel.y), 0.0, 1.0)).rgb;
                    blur *= 0.25;
                    vec3 sharp = rgb + (rgb - blur) * u_lensSharpen;
                    float centerW = 1.0 - smoothstep(0.0, 0.85, length(v_uv - vec2(0.5)) / 0.70710678);
                    float sharpenW = clamp(u_lensSharpen, 0.0, 2.0) * centerW * max(lensMask, 0.0);
                    rgb = mix(rgb, sharp, clamp(sharpenW, 0.0, 1.0));
                }

                /* Radial Action Blur */
                if (u_radialBlur > 0.001)
                {
                    vec2 blurDir = (distortedUV - vec2(0.5)) * u_radialBlur * 0.12;
                    vec3 blurRGB = vec3(0.0);
                    blurRGB += texture(u_sampler, clamp(distortedUV - blurDir * 2.0, 0.0, 1.0)).rgb;
                    blurRGB += texture(u_sampler, clamp(distortedUV - blurDir, 0.0, 1.0)).rgb;
                    blurRGB += rgb;
                    blurRGB += texture(u_sampler, clamp(distortedUV + blurDir, 0.0, 1.0)).rgb;
                    blurRGB += texture(u_sampler, clamp(distortedUV + blurDir * 2.0, 0.0, 1.0)).rgb;
                    rgb = blurRGB / 5.0;
                }

                /* 1 — Lift / Gamma / Gain */
                rgb = rgb * (vec3(1.0) + u_gain);
                rgb = sign(rgb) * pow(max(abs(rgb), vec3(1e-4)), max(vec3(1e-4), vec3(1.0) / (vec3(1.0) + u_gamma)));
                rgb = rgb + u_lift;

                /* 2 — Brightness and contrast */
                rgb = rgb + u_brightness;
                rgb = vec3(0.5) + (1.0 + u_contrast) * (rgb - vec3(0.5));

                /* 3 — Saturation */
                float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                rgb = mix(vec3(luma), rgb, 1.0 + u_saturation);

                /* 4 — Hue rotation */
                if (abs(u_hue) > 0.01)
                {
                    vec3 hsl = rgb2hsl(rgb);
                    hsl.x = fract(hsl.x + u_hue / 360.0);
                    rgb = hsl2rgb(hsl);
                }

                /* 5 — Vignette (radial, smooth) */
                if (u_vigStr > 0.001)
                {
                    vec2 uv = v_uv - vec2(0.5);
                    float dist = length(uv) * 2.0 / sqrt(2.0);
                    float inner = max(0.0, 1.0 - u_vigSmooth);
                    float alpha = smoothstep(inner, 1.0, dist) * u_vigStr;
                    rgb = mix(rgb, u_vigColor, clamp(alpha, 0.0, 1.0));
                }

                /* 6 — Film grain */
                if (u_grainStr > 0.001)
                {
                    vec2 texSize = vec2(textureSize(u_sampler, 0));
                    vec2 grainUV = floor(v_uv * texSize / max(1.0, u_grainSize));
                    float noise = hash(grainUV + vec2(u_grainSeed));
                    rgb += (noise - 0.5) * u_grainStr * 2.0;
                }

                /* Vintage Film Flicker & Scratches */
                if (u_vintage > 0.001)
                {
                    float flicker = sin(u_time * 73.0) * cos(u_time * 59.0) * 0.07 * u_vintage;
                    rgb += vec3(flicker);

                    float scratchX = hash(vec2(floor(distortedUV.x * 250.0), floor(u_time * 16.0)));
                    if (scratchX > 0.993)
                    {
                        rgb *= mix(1.0, 0.45, u_vintage);
                    }
                }

                /* 7 — VHS Scanlines and Static noise */
                if (u_vhs > 0.001)
                {
                    float scanline = sin(distortedUV.y * 300.0 - u_time * 15.0) * 0.08 * u_vhs;
                    rgb -= vec3(scanline);

                    float vhsNoise = hash(distortedUV + vec2(u_time * 0.01));
                    rgb = mix(rgb, vec3(vhsNoise), 0.03 * u_vhs);
                }

                /* 8 — Cinematic Light Leak Flare */
                if (u_lightLeak > 0.001)
                {
                    float leakGrad = smoothstep(1.2, 0.0, length(distortedUV - vec2(0.0, 0.4)));
                    float leakPulse = 0.65 + 0.35 * sin(u_time * 1.8 + cos(u_time * 1.2));
                    vec3 leakColor = vec3(0.95, 0.48, 0.12) * leakGrad * leakPulse * u_lightLeak;

                    float blueGrad = smoothstep(1.5, 0.0, length(distortedUV - vec2(1.0, 0.7)));
                    vec3 blueColor = vec3(0.12, 0.35, 0.95) * blueGrad * (0.8 + 0.2 * cos(u_time * 0.9)) * u_lightLeak * 0.45;

                    rgb += leakColor + blueColor;
                }

                /* 9 — Projector Dust & Specks (60s tape/projector) */
                if (u_dust > 0.001)
                {
                    float dustTime = floor(u_time * 12.0);

                    for (int i = 0; i < 3; i++)
                    {
                        vec2 randPos = vec2(
                            hash(vec2(dustTime, float(i) * 15.3)),
                            hash(vec2(dustTime, float(i) * 31.7))
                        );

                        float spawnProb = hash(vec2(dustTime, float(i) * 7.9));
                        if (spawnProb < u_dust)
                        {
                            vec2 diff = distortedUV - randPos;
                            diff.x *= 1.77;

                            // Randomly rotate the coordinates for each speck
                            float rotAngle = hash(vec2(dustTime, float(i) * 19.3)) * 6.28318;
                            float cosA = cos(rotAngle);
                            float sinA = sin(rotAngle);
                            vec2 rotatedDiff = vec2(
                                diff.x * cosA - diff.y * sinA,
                                diff.x * sinA + diff.y * cosA
                            );

                            float typeDecider = hash(vec2(dustTime, float(i) * 88.1));

                            if (typeDecider < 0.33)
                            {
                                // Type A: Rounded / Irregular Speck (soot flake)
                                float angle = atan(rotatedDiff.y, rotatedDiff.x);
                                float deform = 1.0 + 0.4 * sin(angle * 4.0) + 0.3 * cos(angle * 7.0 + 0.8);
                                float rLimit = 0.008 * u_dust * deform;
                                if (length(rotatedDiff) < rLimit)
                                {
                                    rgb = mix(rgb, vec3(1.0), 0.95);
                                }
                            }
                            else if (typeDecider < 0.66)
                            {
                                // Type B: Thread / Curved Lint Hair
                                float hairLength = 0.022 * u_dust;
                                float hairThickness = 0.0010 * u_dust;
                                float bend = sin(rotatedDiff.x * 180.0) * 0.005;
                                if (abs(rotatedDiff.x) < hairLength && abs(rotatedDiff.y - bend) < hairThickness)
                                {
                                    rgb = mix(rgb, vec3(1.0), 0.95);
                                }
                            }
                            else
                            {
                                // Type C: Deformed Elongated Ellipse Speck (dust fiber clump)
                                vec2 stretched = vec2(rotatedDiff.x * 2.8, rotatedDiff.y);
                                float angle = atan(stretched.y, stretched.x);
                                float deform = 1.0 + 0.35 * sin(angle * 3.0);
                                float rLimit = 0.012 * u_dust * deform;
                                if (length(stretched) < rLimit)
                                {
                                    rgb = mix(rgb, vec3(1.0), 0.95);
                                }
                            }
                        }
                    }
                }

                fragColor = vec4(clamp(rgb, 0.0, 1.0), texture(u_sampler, distortedUV).a);
            }
            """;

    private static final int SHADER_VERSION = 21;
    private static int loadedShaderVersion;
    private static boolean initialized;
    private static boolean failed;
    private static int program;
    private static int vao;
    private static int vbo;
    private static Texture tempTex;

    private static int uSampler;
    private static int uVigStr;
    private static int uVigSmooth;
    private static int uVigColor;
    private static int uBrightness;
    private static int uContrast;
    private static int uSaturation;
    private static int uHue;
    private static int uLift;
    private static int uGamma;
    private static int uGain;
    private static int uGrainStr;
    private static int uGrainSize;
    private static int uGrainSeed;
    private static int uDistort;
    private static int uAberration;
    private static int uAberrationAngle;
    private static int uAberrationDirectional;
    private static int uAberrationRadius;
    private static int uAberrationHardness;
    private static int uAberrationBalance;
    private static int uAberrationCenter;
    private static int uAberrationGreen;
    private static int uAberrationSpectrum;
    private static int uVHS;
    private static int uLensDistortion;
    private static int uLensRadiusX;
    private static int uLensRadiusY;
    private static int uLensHardness;
    private static int uLensSharpen;
    private static int uVintage;
    private static int uRadialBlur;
    private static int uRain;
    private static int uDust;
    private static int uLightLeak;
    private static int uHeatStrength;
    private static int uHeatSpeed;
    private static int uHeatScale;
    private static int uTime;

    public static void apply(List<ColorEffect> effects, List<GrainEffect> grainEffects)
    {
        boolean needVignette = false;
        boolean needGrade = false;
        boolean needGrain = false;
        boolean needDistort = false;
        boolean needCinematic = false;

        for (ColorEffect e : effects)
        {
            if (e.hasVignette) needVignette = true;
            if (e.hasGrade) needGrade = true;
            if (e.hasDistort) needDistort = true;
            if (e.hasCinematic) needCinematic = true;
        }

        for (GrainEffect e : grainEffects)
        {
            if (e.strength > 0F) needGrain = true;
        }

        if (!needVignette && !needGrade && !needGrain && !needDistort && !needCinematic)
        {
            return;
        }

        if (!initialized || loadedShaderVersion != SHADER_VERSION)
        {
            if (program != 0)
            {
                GL20.glDeleteProgram(program);
                program = 0;
            }

            failed = false;
            init();
            loadedShaderVersion = SHADER_VERSION;
        }

        if (failed)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.gl.Framebuffer fb = mc.getFramebuffer();
        int fbW = fb.textureWidth;
        int fbH = fb.textureHeight;

        /* Copy current framebuffer content to tempTex */
        if (tempTex == null)
        {
            tempTex = new Texture();
            tempTex.setFormat(TextureFormat.RGB_U8);
            tempTex.setFilter(GL11.GL_LINEAR);
            /* Prevent fisheye UVs that slightly leave [0,1] from tiling the scene. */
            tempTex.setWrap(GL12.GL_CLAMP_TO_EDGE);
        }

        tempTex.bind();

        if (tempTex.width != fbW || tempTex.height != fbH)
        {
            tempTex.setSize(fbW, fbH);
        }

        /* 1.21.11: Framebuffer.beginRead removed — copy from color attachment via temp read FBO if needed. */
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int sourceId = ((GlTexture) fb.getColorAttachment()).getGlId();
        int captureFbo = GL30.glGenFramebuffers();

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, captureFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, sourceId, 0);
        GL30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fbW, fbH);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glDeleteFramebuffers(captureFbo);
        tempTex.unbind();

        /* Accumulate color effects */
        float vigStr = 0F;
        float vigSmooth = 0.5F;
        float vigR = 0F;
        float vigG = 0F;
        float vigB = 0F;
        float brightness = 0F;
        float contrast = 0F;
        float saturation = 0F;
        float hue = 0F;
        float liftR = 0F, liftG = 0F, liftB = 0F;
        float gammaR = 0F, gammaG = 0F, gammaB = 0F;
        float gainR = 0F, gainG = 0F, gainB = 0F;

        for (ColorEffect e : effects)
        {
            if (e.hasVignette)
            {
                vigStr = Math.max(vigStr, e.vignetteStrength);
                vigSmooth = e.vignetteSmoothness;
                vigR = ((e.vignetteColor >> 16) & 0xFF) / 255.0F;
                vigG = ((e.vignetteColor >> 8) & 0xFF) / 255.0F;
                vigB = (e.vignetteColor & 0xFF) / 255.0F;
            }

            if (e.hasGrade)
            {
                brightness += e.brightness;
                contrast += e.contrast;
                saturation += e.saturation;
                hue += e.hue;
                liftR += e.liftR;
                liftG += e.liftG;
                liftB += e.liftB;
                gammaR += e.gammaR;
                gammaG += e.gammaG;
                gammaB += e.gammaB;
                gainR += e.gainR;
                gainG += e.gainG;
                gainB += e.gainB;
            }
        }

        /* Accumulate grain effects */
        float grainStr = 0F;
        float grainSize = 1F;

        for (GrainEffect e : grainEffects)
        {
            grainStr += e.strength;
            grainSize = e.size;
        }

        float grainSeed = (System.nanoTime() & 0xFFFFL) / 65536.0F;

        /* Accumulate distortion */
        float distortX = 0F;
        float distortY = 0F;

        for (ColorEffect e : effects)
        {
            if (e.hasDistort)
            {
                distortX += e.distortX;
                distortY += e.distortY;
            }
        }

        /* Accumulate cinematic effects */
        float aberration = 0F;
        float aberrationAngle = 0F;
        float aberrationDirectional = 0F;
        float aberrationRadius = 1F;
        float aberrationHardness = 1F;
        float aberrationBalance = 0F;
        float aberrationCenterX = 0.5F;
        float aberrationCenterY = 0.5F;
        float aberrationGreen = 0F;
        float aberrationSpectrum = 0F;
        float vhs = 0F;
        float lensDistortion = 0F;
        float lensRadiusX = 1F;
        float lensRadiusY = 1F;
        float lensHardness = 1F;
        float lensSharpen = 0F;
        float vintage = 0F;
        float radialBlur = 0F;
        float rain = 0F;
        float dust = 0F;
        float lightLeak = 0F;
        float heatStrength = 0F;
        float heatSpeed = 0F;
        float heatScale = 0F;
        float time = 0F;
        for (ColorEffect e : effects)
        {
            if (e.hasCinematic)
            {
                aberration = Math.max(aberration, e.aberration);
                aberrationAngle = e.aberrationAngle;
                aberrationDirectional = Math.max(aberrationDirectional, e.aberrationDirectional);
                aberrationRadius = e.aberrationRadius;
                aberrationHardness = e.aberrationHardness;
                aberrationBalance = e.aberrationBalance;
                aberrationCenterX = e.aberrationCenterX;
                aberrationCenterY = e.aberrationCenterY;
                aberrationGreen = Math.max(aberrationGreen, e.aberrationGreen);
                aberrationSpectrum = Math.max(aberrationSpectrum, e.aberrationSpectrum);
                vhs = Math.max(vhs, e.vhs);
                lensDistortion = Math.abs(e.lensDistortion) > Math.abs(lensDistortion) ? e.lensDistortion : lensDistortion;
                lensRadiusX = e.lensRadiusX;
                lensRadiusY = e.lensRadiusY;
                lensHardness = e.lensHardness;
                lensSharpen = Math.max(lensSharpen, e.lensSharpen);
                vintage = Math.max(vintage, e.vintage);
                radialBlur = Math.max(radialBlur, e.radialBlur);
                rain = Math.max(rain, e.rain);
                dust = Math.max(dust, e.dust);
                lightLeak = Math.max(lightLeak, e.lightLeak);
                heatStrength = Math.max(heatStrength, e.heatStrength);
                heatSpeed = Math.max(heatSpeed, e.heatSpeed);
                heatScale = Math.max(heatScale, e.heatScale);
                time = e.time;
            }
        }

        /* Save and set viewport */
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        GL11.glViewport(0, 0, fbW, fbH);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

        GL20.glUseProgram(program);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        tempTex.bind();
        glUniform1iSafe(uSampler, 0);

        glUniform1fSafe(uVigStr, vigStr);
        glUniform1fSafe(uVigSmooth, vigSmooth);
        glUniform3fSafe(uVigColor, vigR, vigG, vigB);
        glUniform1fSafe(uBrightness, brightness);
        glUniform1fSafe(uContrast, contrast);
        glUniform1fSafe(uSaturation, saturation);
        glUniform1fSafe(uHue, hue);
        glUniform3fSafe(uLift, liftR, liftG, liftB);
        glUniform3fSafe(uGamma, gammaR, gammaG, gammaB);
        glUniform3fSafe(uGain, gainR, gainG, gainB);
        glUniform1fSafe(uGrainStr, grainStr);
        glUniform1fSafe(uGrainSize, grainSize);
        glUniform1fSafe(uGrainSeed, grainSeed);
        glUniform2fSafe(uDistort, distortX, distortY);
        glUniform1fSafe(uAberration, aberration);
        glUniform1fSafe(uAberrationAngle, aberrationAngle);
        glUniform1fSafe(
            uAberrationDirectional,
            Math.max(0F, Math.min(1F, aberrationDirectional))
        );
        glUniform1fSafe(uAberrationRadius, Math.max(0F, aberrationRadius));
        glUniform1fSafe(
            uAberrationHardness,
            Math.max(0F, Math.min(1F, aberrationHardness))
        );
        glUniform1fSafe(
            uAberrationBalance,
            Math.max(-1F, Math.min(1F, aberrationBalance))
        );
        glUniform2fSafe(
            uAberrationCenter,
            Math.max(0F, Math.min(1F, aberrationCenterX)),
            Math.max(0F, Math.min(1F, aberrationCenterY))
        );
        glUniform1fSafe(uAberrationGreen, Math.max(0F, aberrationGreen));
        glUniform1fSafe(
            uAberrationSpectrum,
            Math.max(0F, Math.min(1F, aberrationSpectrum))
        );
        glUniform1fSafe(uVHS, vhs);
        glUniform1fSafe(uLensDistortion, lensDistortion);
        glUniform1fSafe(uLensRadiusX, Math.max(0F, lensRadiusX));
        glUniform1fSafe(uLensRadiusY, Math.max(0F, lensRadiusY));
        glUniform1fSafe(uLensHardness, Math.max(0F, Math.min(1F, lensHardness)));
        glUniform1fSafe(uLensSharpen, Math.max(0F, lensSharpen));
        glUniform1fSafe(uVintage, vintage);
        glUniform1fSafe(uRadialBlur, radialBlur);
        glUniform1fSafe(uRain, rain);
        glUniform1fSafe(uDust, dust);
        glUniform1fSafe(uLightLeak, lightLeak);
        glUniform1fSafe(uHeatStrength, heatStrength * 0.006F);
        glUniform1fSafe(uHeatSpeed, 0.5F + heatSpeed * 2.0F);
        glUniform1fSafe(uHeatScale, 2.0F + heatScale * 35.0F);
        glUniform1fSafe(uTime, time);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);
        tempTex.unbind();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
    }

    /**
     * ColorGrade binds shaders/textures via raw GL, which desyncs {@link RenderSystem}'s
     * tracker (GL may have texture 0 while RenderSystem still thinks a previous id is bound).
     * Subtitle text then skips rebinding the font atlas and bakes a black atlas.
     * <p>
     * Image / Hotbar / a second Subtitle only appear to "fix" this because they issue a
     * {@code PositionTexColor} draw first. Emulate that with an invisible textured pixel.
     */
    public static void resyncMinecraftState(Batcher2D batcher)
    {
        if (batcher == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        /*
         * Invalidate unit 0 so the following textured draw must call glBindTexture.
         * A PositionColor-only box is not enough — text needs a live Sampler0 bind path.
         */
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        AbstractTexture atlas = mc.getTextureManager().getTexture(Identifier.of("minecraft", "textures/atlas/blocks.png"));
        int textureId = atlas == null ? 0 : ((GlTexture) atlas.getGlTexture()).getGlId();

        if (textureId != 0)
        {
            /* Fully transparent 1x1 — no visible flash, forces drawWithGlobalProgram. */
            batcher.texturedBox(textureId, 0x00000000, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1);
        }
    }

    private static void init()
    {
        initialized = true;

        int vert = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vert, VERT);
        GL20.glCompileShader(vert);

        if (GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
        {
            System.err.println("[ColorGradeRenderer] Vertex shader failed:\n" + GL20.glGetShaderInfoLog(vert));
            GL20.glDeleteShader(vert);
            failed = true;

            return;
        }

        int frag = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(frag, FRAG);
        GL20.glCompileShader(frag);

        if (GL20.glGetShaderi(frag, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
        {
            System.err.println("[ColorGradeRenderer] Fragment shader failed:\n" + GL20.glGetShaderInfoLog(frag));
            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            failed = true;

            return;
        }

        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vert);
        GL20.glAttachShader(program, frag);
        GL20.glBindAttribLocation(program, 0, "a_pos");
        GL20.glBindAttribLocation(program, 1, "a_uv");
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
        {
            System.err.println("[ColorGradeRenderer] Link failed:\n" + GL20.glGetProgramInfoLog(program));
            GL20.glDeleteProgram(program);
            failed = true;

            return;
        }

        uSampler = GL20.glGetUniformLocation(program, "u_sampler");
        uVigStr = GL20.glGetUniformLocation(program, "u_vigStr");
        uVigSmooth = GL20.glGetUniformLocation(program, "u_vigSmooth");
        uVigColor = GL20.glGetUniformLocation(program, "u_vigColor");
        uBrightness = GL20.glGetUniformLocation(program, "u_brightness");
        uContrast = GL20.glGetUniformLocation(program, "u_contrast");
        uSaturation = GL20.glGetUniformLocation(program, "u_saturation");
        uHue = GL20.glGetUniformLocation(program, "u_hue");
        uLift = GL20.glGetUniformLocation(program, "u_lift");
        uGamma = GL20.glGetUniformLocation(program, "u_gamma");
        uGain = GL20.glGetUniformLocation(program, "u_gain");
        uGrainStr = GL20.glGetUniformLocation(program, "u_grainStr");
        uGrainSize = GL20.glGetUniformLocation(program, "u_grainSize");
        uGrainSeed = GL20.glGetUniformLocation(program, "u_grainSeed");
        uDistort = GL20.glGetUniformLocation(program, "u_distort");
        uAberration = GL20.glGetUniformLocation(program, "u_aberration");
        uAberrationAngle = GL20.glGetUniformLocation(program, "u_aberrationAngle");
        uAberrationDirectional = GL20.glGetUniformLocation(program, "u_aberrationDirectional");
        uAberrationRadius = GL20.glGetUniformLocation(program, "u_aberrationRadius");
        uAberrationHardness = GL20.glGetUniformLocation(program, "u_aberrationHardness");
        uAberrationBalance = GL20.glGetUniformLocation(program, "u_aberrationBalance");
        uAberrationCenter = GL20.glGetUniformLocation(program, "u_aberrationCenter");
        uAberrationGreen = GL20.glGetUniformLocation(program, "u_aberrationGreen");
        uAberrationSpectrum = GL20.glGetUniformLocation(program, "u_aberrationSpectrum");
        uVHS = GL20.glGetUniformLocation(program, "u_vhs");
        uLensDistortion = GL20.glGetUniformLocation(program, "u_lensDistortion");
        uLensRadiusX = GL20.glGetUniformLocation(program, "u_lensRadiusX");
        uLensRadiusY = GL20.glGetUniformLocation(program, "u_lensRadiusY");
        uLensHardness = GL20.glGetUniformLocation(program, "u_lensHardness");
        uLensSharpen = GL20.glGetUniformLocation(program, "u_lensSharpen");
        uVintage = GL20.glGetUniformLocation(program, "u_vintage");
        uRadialBlur = GL20.glGetUniformLocation(program, "u_radialBlur");
        uRain = GL20.glGetUniformLocation(program, "u_rain");
        uDust = GL20.glGetUniformLocation(program, "u_dust");
        uLightLeak = GL20.glGetUniformLocation(program, "u_lightLeak");
        uHeatStrength = GL20.glGetUniformLocation(program, "u_heatStrength");
        uHeatSpeed = GL20.glGetUniformLocation(program, "u_heatSpeed");
        uHeatScale = GL20.glGetUniformLocation(program, "u_heatScale");
        uTime = GL20.glGetUniformLocation(program, "u_time");

        /* Fullscreen quad VAO/VBO (NDC coords + UV) */
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        FloatBuffer buf = MemoryUtil.memAllocFloat(24);

        buf.put(new float[] {
            -1F, -1F,  0F, 0F,
             1F, -1F,  1F, 0F,
             1F,  1F,  1F, 1F,
            -1F, -1F,  0F, 0F,
             1F,  1F,  1F, 1F,
            -1F,  1F,  0F, 1F,
        }).flip();

        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);

        int stride = 4 * Float.BYTES;

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, (long) (2 * Float.BYTES));

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static void glUniform1iSafe(int location, int v0)
    {
        if (location >= 0)
        {
            GL20.glUniform1i(location, v0);
        }
    }

    private static void glUniform1fSafe(int location, float v0)
    {
        if (location >= 0)
        {
            GL20.glUniform1f(location, v0);
        }
    }

    private static void glUniform2fSafe(int location, float v0, float v1)
    {
        if (location >= 0)
        {
            GL20.glUniform2f(location, v0, v1);
        }
    }

    private static void glUniform3fSafe(int location, float v0, float v1, float v2)
    {
        if (location >= 0)
        {
            GL20.glUniform3f(location, v0, v1, v2);
        }
    }
}
