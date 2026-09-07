package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.utils.colors.Color;

import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.List;
import java.util.Locale;

/**
 * BSL shader patch for BBS form glow Size/Spread. Complementary uses
 * {@link ComplementaryFormGlowPatch} (Euphoria-style dedicated pack patch).
 */
public final class FormGlowBloomPatch
{
    public static final String INTENSITY = "form_glow_intensity";
    public static final String SIZE = "form_glow_size";
    public static final String SPREAD = "form_glow_spread";

    private static final String U_INTENSITY = ShaderCurves.UNIFORM_IDENTIFIER + INTENSITY;
    private static final String U_SIZE = ShaderCurves.UNIFORM_IDENTIFIER + SIZE;
    private static final String U_SPREAD = ShaderCurves.UNIFORM_IDENTIFIER + SPREAD;
    private static final String HELPER_GUARD = "BBS_FORM_GLOW_BLOOM_HELPERS";
    private static final String APPLY = "bbsFormGlowEmissionBoost";
    private static final String AFTER_LIGHT = "bbsFormGlowAfterLighting";
    private static final String COMPOSITE_GUARD = "BBS_FORM_GLOW_COMPOSITE";

    private static float intensity;
    private static float size;
    private static float spread;
    private static float frameIntensity;
    private static float frameSize;
    private static float frameSpread;
    /* Last completed frame's max — Iris samples PER_FRAME custom uniforms at frame start,
     * before any form draw sets the live values. Without this, composite always reads 0
     * and the Size/Spread bloom patch never activates. */
    private static float publishedIntensity;
    private static float publishedSize;
    private static float publishedSpread;
    private static boolean patchedThisPack;

    private FormGlowBloomPatch()
    {
    }

    public static void beginFrame()
    {
        publishedIntensity = frameIntensity;
        publishedSize = frameSize;
        publishedSpread = frameSpread;
        frameIntensity = 0F;
        frameSize = 0F;
        frameSpread = 0F;
        clear();
    }

    public static void set(float intensityValue, float sizeValue, float spreadValue)
    {
        intensity = Math.max(0F, intensityValue);
        size = sizeValue;
        spread = Math.max(0F, Math.min(1F, spreadValue));

        if (intensity > 0.001F)
        {
            frameIntensity = Math.max(frameIntensity, intensity);
            /* Last active glow wins Size/Spread for composite bloom this frame. */
            frameSize = size;
            frameSpread = spread;
        }
    }

    public static void setFromGlow(GlowSettings glow, Color legacyGlow)
    {
        if (glow == null)
        {
            clear();

            return;
        }

        float glowIntensity = glow.resolveIntensity(legacyGlow);

        if (glowIntensity <= 0F)
        {
            clear();

            return;
        }

        set(glowIntensity, glow.resolveSize(), glow.resolveSpread());
    }

    public static void clear()
    {
        intensity = 0F;
        size = 0F;
        spread = 0F;
    }

    /** Live draw values, else this frame's max, else last frame's published max (composite). */
    public static float getIntensity()
    {
        return Math.max(intensity, Math.max(frameIntensity, publishedIntensity));
    }

    public static float getSize()
    {
        if (intensity > 0.001F)
        {
            return size;
        }

        if (frameIntensity > 0.001F)
        {
            return frameSize;
        }

        return publishedSize;
    }

    public static float getSpread()
    {
        if (intensity > 0.001F)
        {
            return spread;
        }

        if (frameIntensity > 0.001F)
        {
            return frameSpread;
        }

        return publishedSpread;
    }

    public static boolean isActive()
    {
        return getIntensity() > 0.001F;
    }

    public static void resetPackState()
    {
        patchedThisPack = false;
        ComplementaryFormGlowPatch.resetPackState();
        beginFrame();
    }

    public static boolean shouldPatchPack()
    {
        if (BBSSettings.irisFormGlowBloomPatch != null
            && !BBSSettings.irisFormGlowBloomPatch.get())
        {
            return false;
        }

        String pack = resolvePackName();

        if (pack.isEmpty())
        {
            return false;
        }

        String lower = pack.toLowerCase(Locale.ROOT);

        /* Complementary uses ComplementaryFormGlowPatch (Euphoria-style). This class keeps BSL. */
        return lower.contains("bsl");
    }

    public static boolean isPackPatched()
    {
        return (patchedThisPack && shouldPatchPack()) || ComplementaryFormGlowPatch.isPackPatched();
    }

    /**
     * Always draw BBS Size shells / albedo boost. Pack bloom is a bonus only — skipping BBS
     * emission made glow invisible when composite uniforms were 0 or billboards drew after bloom.
     */
    public static boolean shouldSkipGeometrySizeShells()
    {
        return false;
    }

    public static boolean shouldSkipAlbedoBrighten()
    {
        return false;
    }

    public static void addUniforms(List<CachedUniform> list)
    {
        list.add(new FloatCachedUniform(U_INTENSITY, UniformUpdateFrequency.PER_FRAME, FormGlowBloomPatch::getIntensity));
        list.add(new FloatCachedUniform(U_SIZE, UniformUpdateFrequency.PER_FRAME, FormGlowBloomPatch::getSize));
        list.add(new FloatCachedUniform(U_SPREAD, UniformUpdateFrequency.PER_FRAME, FormGlowBloomPatch::getSpread));
    }

    private static String resolvePackName()
    {
        String loading = ShaderOpacityPatch.getLoadingPackName();

        if (loading != null && !loading.isEmpty())
        {
            return loading;
        }

        try
        {
            String current = net.irisshaders.iris.Iris.getCurrentPackName();

            return current == null ? "" : current;
        }
        catch (Throwable t)
        {
            return "";
        }
    }

    public static String processSource(String source)
    {
        if (source == null || source.isEmpty())
        {
            return source;
        }

        /* Complementary gets its own Euphoria-style Size/Spread bloom patch. */
        if (ComplementaryFormGlowPatch.isComplementaryPack())
        {
            return ComplementaryFormGlowPatch.processSource(source);
        }

        if (!shouldPatchPack())
        {
            return source;
        }

        /* Thin world composite*.fsh wrappers: declare uniforms before #include. */
        if (source.contains("COMPOSITE4") || source.contains("COMPOSITE5")
            || source.contains("/program/composite4.glsl") || source.contains("/program/composite5.glsl"))
        {
            if (!source.contains(COMPOSITE_GUARD))
            {
                source = insertCompositeUniforms(source);
                patchedThisPack = true;
            }
        }

        if (isBloomComposite(source))
        {
            return processBloomComposite(source);
        }

        if (!isEntityOrBlockGbufferFragment(source))
        {
            return source;
        }

        if (source.contains(APPLY + "()") && source.contains(U_INTENSITY) && source.contains(AFTER_LIGHT + "("))
        {
            patchedThisPack = true;

            return source;
        }

        String patched = insertGbufferHelpers(source);

        patched = injectEmissionBoost(patched);
        patched = injectAfterLighting(patched);

        if (patched.contains(APPLY + "()") || patched.contains(AFTER_LIGHT + "(") || patched.contains(U_INTENSITY))
        {
            patchedThisPack = true;
        }

        return patched;
    }

    private static boolean isBloomComposite(String source)
    {
        return source.contains("BloomTile(")
            || source.contains("void DoBloom(")
            || source.contains("GetBloomTile(");
    }

    private static String processBloomComposite(String source)
    {
        if (source.contains(COMPOSITE_GUARD) && source.contains(U_SIZE))
        {
            patchedThisPack = true;

            return source;
        }

        String patched = insertCompositeUniforms(source);

        patched = patchBloomTileRadius(patched);
        patched = patchDoBloomMix(patched);

        if (patched.contains(U_SIZE) || patched.contains(COMPOSITE_GUARD))
        {
            patchedThisPack = true;
        }

        return patched;
    }

    private static boolean isEntityOrBlockGbufferFragment(String source)
    {
        if (ShaderOpacityPatch.isShadowCasterSourcePublic(source))
        {
            return false;
        }

        boolean looksLikeFragment = source.contains("gl_FragData")
            || source.contains("FRAGMENT_SHADER")
            || source.contains("layout(location = 0) out")
            || source.contains("colortex0Out");

        if (!looksLikeFragment)
        {
            return false;
        }

        if (source.contains("GBUFFERS_TERRAIN") || source.contains("GBUFFERS_WATER")
            || source.contains("GBUFFERS_SKY") || source.contains("GBUFFERS_CLOUDS")
            || source.contains("GBUFFERS_WEATHER") || source.contains("GBUFFERS_BASIC"))
        {
            return false;
        }

        if (source.contains("GBUFFERS_ENTITIES") || source.contains("GBUFFERS_BLOCK")
            || source.contains("entityColor") || source.contains("currentRenderedItemId"))
        {
            return true;
        }

        return source.contains("DoLighting") && source.contains("entityId");
    }

    private static String insertGbufferHelpers(String source)
    {
        if (source.contains(HELPER_GUARD))
        {
            return source;
        }

        String helpers =
            "uniform float " + U_INTENSITY + ";\n"
                + "uniform float " + U_SIZE + ";\n"
                + "uniform float " + U_SPREAD + ";\n"
                + "#ifndef " + HELPER_GUARD + "\n"
                + "#define " + HELPER_GUARD + "\n"
                + "float " + APPLY + "(){\n"
                + " if(" + U_INTENSITY + "<=0.001) return 0.0;\n"
                + " float soft=" + U_INTENSITY + "/(1.0+" + U_INTENSITY + "*0.05);\n"
                + " float sizeM=clamp(" + U_SIZE + ",-4.0,12.0);\n"
                + " float spreadM=clamp(" + U_SPREAD + ",0.0,1.0);\n"
                + " /* Strong emission seed so Complementary bloom atlas sees the form. */\n"
                + " return soft*(1.35+max(sizeM,0.0)*0.55)*(0.9+spreadM*0.35);\n"
                + "}\n"
                + "vec3 " + AFTER_LIGHT + "(vec3 rgb){\n"
                + " if(" + U_INTENSITY + "<=0.001) return rgb;\n"
                + " float soft=" + U_INTENSITY + "/(1.0+" + U_INTENSITY + "*0.05);\n"
                + " float sizeM=clamp(" + U_SIZE + ",-4.0,12.0);\n"
                + " float spreadM=clamp(" + U_SPREAD + ",0.0,1.0);\n"
                + " rgb += rgb*soft*(0.2+max(sizeM,0.0)*0.18);\n"
                + " float choke=mix(0.88,1.35,spreadM);\n"
                + " return mix(rgb, rgb*choke, soft*mix(0.3,0.65,spreadM));\n"
                + "}\n"
                + "#endif\n";

        return insertAfterVersionOrPrepend(source, helpers);
    }

    private static String insertCompositeUniforms(String source)
    {
        if (source.contains(COMPOSITE_GUARD))
        {
            return source;
        }

        String block =
            "uniform float " + U_INTENSITY + ";\n"
                + "uniform float " + U_SIZE + ";\n"
                + "uniform float " + U_SPREAD + ";\n"
                + "#ifndef " + COMPOSITE_GUARD + "\n"
                + "#define " + COMPOSITE_GUARD + "\n"
                + "#endif\n";

        return insertAfterVersionOrPrepend(source, block);
    }

    /**
     * Complementary/BSL bloom lives in {@code program/composite*.glsl} includes with no
     * {@code #version} line — still inject uniforms at file head so Size/Spread compile.
     */
    private static String insertAfterVersionOrPrepend(String source, String block)
    {
        int version = source.indexOf("#version");

        if (version >= 0)
        {
            int nextNewLine = source.indexOf('\n', version);

            if (nextNewLine >= 0)
            {
                return source.substring(0, nextNewLine + 1) + block + source.substring(nextNewLine + 1);
            }
        }

        return block + source;
    }

    private static String injectEmissionBoost(String source)
    {
        if (source.contains("emission += " + APPLY + "()"))
        {
            return source;
        }

        String marker = "DoLighting(";
        int index = source.lastIndexOf(marker);

        if (index < 0)
        {
            return source;
        }

        return source.substring(0, index) + "emission += " + APPLY + "();\n " + source.substring(index);
    }

    private static String injectAfterLighting(String source)
    {
        if (source.contains("color.rgb = " + AFTER_LIGHT + "(color.rgb)"))
        {
            return source;
        }

        String marker = "DoLighting(";
        int index = source.lastIndexOf(marker);

        if (index < 0)
        {
            return source;
        }

        int semi = source.indexOf(';', index);

        if (semi < 0)
        {
            return source;
        }

        String insert = "\n color.rgb = " + AFTER_LIGHT + "(color.rgb);";

        return source.substring(0, semi + 1) + insert + source.substring(semi + 1);
    }

    /**
     * Do NOT scale BloomTile sample offsets — that samples outside bloom-atlas tiles and
     * creates blocky rectangular blotches when Size is raised. Size/Spread live in DoBloom mix.
     */
    private static String patchBloomTileRadius(String source)
    {
        return source;
    }

    /**
     * Size → favor wide vs tight bloom mips; Spread → choke/softness of the mix.
     * Complementary DoBloom is handled by {@link ComplementaryFormGlowPatch}.
     */
    private static String patchDoBloomMix(String source)
    {
        if (source.contains("BBS_GLOW_DO_BLOOM") || source.contains("BBS_GLOW_BSL_BLOOM"))
        {
            return source;
        }

        return patchBslBloomMix(source);
    }

    /**
     * BSL composite5 uses weighted blur mixes + {@code mix(color, blur, 0.2 * BLOOM_STRENGTH)}.
     */
    private static String patchBslBloomMix(String source)
    {
        if (!source.contains("GetBloomTile(") || source.contains("BBS_GLOW_BSL_BLOOM"))
        {
            return patchBloomStrengthOnly(source);
        }

        String[] blurLines = new String[] {
            "vec3 blur = (blur1 * 4.00 + blur2 * 2.82 + blur3 * 2.00 + blur4 * 1.41 + blur5) / 11.23;",
            "vec3 blur = (blur1 * 4.00 + blur2 * 3.03 + blur3 * 2.30 + blur4 * 1.74 + blur5 * 1.32 + blur6) / 13.39;",
            "vec3 blur = (blur1 * 4.00 + blur2 * 3.18 + blur3 * 2.52 + blur4 * 2.00 + blur5 * 1.59 + blur6 * 1.26 + blur7) / 15.55;"
        };

        boolean replaced = false;

        for (String from : blurLines)
        {
            if (!source.contains(from))
            {
                continue;
            }

            String to =
                "float bbsGlowSize = clamp(" + U_SIZE + ", -8.0, 16.0);\n"
                    + "\tfloat bbsGlowSpread = clamp(" + U_SPREAD + ", 0.0, 1.0);\n"
                    + "\tfloat bbsGlowIntensity = max(" + U_INTENSITY + ", 0.0);\n"
                    + "\tfloat bbsSizeT = clamp(bbsGlowSize / 8.0, -1.0, 2.0);\n"
                    + "\tvec3 bbsDefaultBlur = " + from.substring("vec3 blur = ".length()) + "\n"
                    + "\tvec3 bbsTight = blur1;\n"
                    + "\tvec3 bbsWide = blur5;\n"
                    + "\tvec3 bbsSized = mix(bbsTight, bbsWide, clamp(bbsSizeT * 0.75, 0.0, 1.0));\n"
                    + "\tbbsSized = mix(bbsSized, bbsTight, clamp(-bbsSizeT, 0.0, 1.0));\n"
                    + "\tvec3 bbsSoft = mix(bbsSized, bbsWide, 0.55);\n"
                    + "\tvec3 bbsSharp = mix(bbsSized, bbsTight * 1.25, 0.8);\n"
                    + "\tvec3 blur = mix(bbsDefaultBlur, mix(bbsSoft, bbsSharp, bbsGlowSpread), step(0.001, bbsGlowIntensity)); /* BBS_GLOW_BSL_BLOOM */";

            source = source.replace(from, to);
            replaced = true;
        }

        String mixFrom = "color = mix(color, blur, 0.2 * BLOOM_STRENGTH);";
        String mixTo =
            "float bbsBloomAmt = 0.2 * BLOOM_STRENGTH;\n"
                + "\tif (max(" + U_INTENSITY + ", 0.0) > 0.001) {\n"
                + "\t bbsBloomAmt *= 1.0 + clamp(" + U_INTENSITY + ", 0.0, 24.0) * 0.14 + max(clamp(" + U_SIZE + ", -8.0, 16.0), 0.0) * 0.45;\n"
                + "\t bbsBloomAmt *= mix(1.55, 0.5, clamp(" + U_SPREAD + ", 0.0, 1.0));\n"
                + "\t}\n"
                + "\tcolor = mix(color, blur, bbsBloomAmt); /* BBS_GLOW_BSL_STRENGTH */";

        if (source.contains(mixFrom) && !source.contains("BBS_GLOW_BSL_STRENGTH"))
        {
            source = source.replace(mixFrom, mixTo);
            replaced = true;
        }

        if (!replaced)
        {
            return patchBloomStrengthOnly(source);
        }

        return source;
    }

    private static String patchBloomStrengthOnly(String source)
    {
        String strengthFrom = "float bloomStrength = BLOOM_STRENGTH + 0.2 * darknessFactor;";

        if (!source.contains(strengthFrom) || source.contains("BBS_GLOW_STRENGTH"))
        {
            return source;
        }

        String strengthTo =
            "float bloomStrength = BLOOM_STRENGTH + 0.2 * darknessFactor;\n"
                + "        bloomStrength *= (1.0 + clamp(" + U_INTENSITY + ", 0.0, 24.0) * 0.12 + max(clamp(" + U_SIZE + ", -8.0, 16.0), 0.0) * 0.5);\n"
                + "        bloomStrength *= mix(1.55, 0.55, clamp(" + U_SPREAD + ", 0.0, 1.0)); /* BBS_GLOW_STRENGTH */";

        return source.replace(strengthFrom, strengthTo);
    }

    public static void uploadToCurrentProgram()
    {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        if (program <= 0)
        {
            return;
        }

        uploadUniform(program, U_INTENSITY, getIntensity());
        uploadUniform(program, U_SIZE, getSize());
        uploadUniform(program, U_SPREAD, getSpread());
    }

    private static void uploadUniform(int program, String name, float value)
    {
        int location = GL20.glGetUniformLocation(program, name);

        if (location >= 0)
        {
            GL20.glUniform1f(location, value);
        }
    }
}
