package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSSettings;

import java.util.Locale;

/**
 * Euphoria-style runtime patch for <b>Complementary</b> only.
 * <p>
 * Adds Photoshop-like Outer Glow Size / Spread into Complementary's bloom pipeline
 * (same idea as Euphoria shipping extra features on top of Complementary — but applied
 * live through Iris GLSL preprocess instead of shipping a forked pack zip).
 * <ul>
 *   <li>{@code Size} — expands/contracts the bloom kernel in {@code composite4} BloomTile
 *       (samples scene {@code colortex0}, so no atlas-tile blotches) and reweights mip mix</li>
 *   <li>{@code Spread} — soft/wide (0) vs sharp/choked (1) halo shape</li>
 *   <li>{@code Intensity} — emission seed in entity/block gbuffers so bloom has something to blur</li>
 * </ul>
 * BSL is intentionally left to {@link FormGlowBloomPatch} (already working for the user).
 */
public final class ComplementaryFormGlowPatch
{
    private static final String U_INTENSITY = ShaderCurves.UNIFORM_IDENTIFIER + FormGlowBloomPatch.INTENSITY;
    private static final String U_SIZE = ShaderCurves.UNIFORM_IDENTIFIER + FormGlowBloomPatch.SIZE;
    private static final String U_SPREAD = ShaderCurves.UNIFORM_IDENTIFIER + FormGlowBloomPatch.SPREAD;

    private static final String PACK_GUARD = "BBS_COMPLEMENTARY_FORM_GLOW";
    private static final String HELPER_GUARD = "BBS_COMP_GLOW_HELPERS";
    private static final String COMPOSITE_GUARD = "BBS_COMP_GLOW_COMPOSITE";
    private static final String APPLY = "bbsCompFormGlowEmission";
    private static final String AFTER_LIGHT = "bbsCompFormGlowAfterLight";
    private static final String TILE_GUARD = "BBS_COMP_GLOW_BLOOM_TILE";
    private static final String DO_BLOOM_GUARD = "BBS_COMP_GLOW_DO_BLOOM";

    private static boolean patchedThisPack;

    private ComplementaryFormGlowPatch()
    {
    }

    public static void resetPackState()
    {
        patchedThisPack = false;
    }

    public static boolean isComplementaryPack()
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

        return pack.toLowerCase(Locale.ROOT).contains("complementary");
    }

    public static boolean isPackPatched()
    {
        return patchedThisPack && isComplementaryPack();
    }

    public static String processSource(String source)
    {
        if (source == null || source.isEmpty() || !isComplementaryPack())
        {
            return source;
        }

        /* Thin world wrappers that #include program/composite*.glsl */
        if (source.contains("COMPOSITE4") || source.contains("COMPOSITE5")
            || source.contains("/program/composite4.glsl") || source.contains("/program/composite5.glsl"))
        {
            if (!source.contains(COMPOSITE_GUARD))
            {
                source = insertUniforms(source, COMPOSITE_GUARD);
                patchedThisPack = true;
            }
        }

        if (isComposite4BloomAtlas(source))
        {
            return processComposite4(source);
        }

        if (isComposite5DoBloom(source))
        {
            return processComposite5(source);
        }

        if (!isEntityOrBlockGbufferFragment(source))
        {
            return source;
        }

        return processGbuffer(source);
    }

    private static boolean isComposite4BloomAtlas(String source)
    {
        return source.contains("vec3 BloomTile(") && source.contains("colortex0");
    }

    private static boolean isComposite5DoBloom(String source)
    {
        return source.contains("void DoBloom(") || source.contains("GetBloomTile(");
    }

    private static String processComposite4(String source)
    {
        String patched = insertUniforms(source, COMPOSITE_GUARD);

        patched = patchBloomTileKernel(patched);

        if (patched.contains(TILE_GUARD) || patched.contains(U_SIZE))
        {
            patchedThisPack = true;
        }

        return patched;
    }

    private static String processComposite5(String source)
    {
        String patched = insertUniforms(source, COMPOSITE_GUARD);

        patched = patchDoBloom(patched);

        if (patched.contains(DO_BLOOM_GUARD) || patched.contains(U_SIZE))
        {
            patchedThisPack = true;
        }

        return patched;
    }

    private static String processGbuffer(String source)
    {
        if (source.contains(APPLY + "()") && source.contains(U_INTENSITY))
        {
            patchedThisPack = true;

            return source;
        }

        String patched = insertGbufferHelpers(source);

        patched = injectEmissionBoost(patched);
        patched = injectAfterLighting(patched);

        if (patched.contains(APPLY + "()") || patched.contains(U_INTENSITY))
        {
            patchedThisPack = true;
        }

        return patched;
    }

    /**
     * Size/Spread scale the 7×7 BloomTile kernel that samples the HDR scene.
     * Safe: does NOT touch GetBloomTile atlas UVs (those caused blotches).
     */
    private static String patchBloomTileKernel(String source)
    {
        if (source.contains(TILE_GUARD))
        {
            return source;
        }

        String from = "vec2 pixelOffset = vec2(i, j) / view;";

        if (!source.contains(from))
        {
            return source;
        }

        String to =
            "float bbsGlowI = max(" + U_INTENSITY + ", 0.0);\n"
                + "                float bbsGlowSize = clamp(" + U_SIZE + ", -32.0, 24.0);\n"
                + "                float bbsGlowSpread = clamp(" + U_SPREAD + ", 0.0, 1.0);\n"
                + "                /* /16 so Size past -10 still moves (was hard-capped at -8). */\n"
                + "                float bbsSizeT = clamp(bbsGlowSize / 16.0, -2.0, 2.0);\n"
                + "                /* Size expands blur radius; Spread 0=soft/wide, 1=sharp/tight. */\n"
                + "                float bbsKernel = 1.0;\n"
                + "                if (bbsGlowI > 0.001) {\n"
                + "                 float bbsPos = clamp(bbsSizeT, 0.0, 1.0);\n"
                + "                 float bbsNeg = clamp(-bbsSizeT / 2.0, 0.0, 1.0);\n"
                + "                 bbsKernel = mix(1.0, mix(2.6, 1.2, bbsGlowSpread), bbsPos);\n"
                + "                 bbsKernel = mix(bbsKernel, mix(0.12, 0.4, bbsGlowSpread), bbsNeg);\n"
                + "                 bbsKernel = mix(bbsKernel, bbsKernel * 1.2, (1.0 - bbsGlowSpread) * bbsPos * 0.35);\n"
                + "                }\n"
                + "                vec2 pixelOffset = vec2(i, j) / view * bbsKernel; /* " + TILE_GUARD + " */";

        return source.replace(from, to);
    }

    private static String patchDoBloom(String source)
    {
        if (source.contains(DO_BLOOM_GUARD))
        {
            return source;
        }

        String from = "vec3 blur = (blur1 + blur2 + blur3 + blur4 + blur5 + blur6 + blur7) * 0.14;";

        if (!source.contains(from))
        {
            return source;
        }

        String to =
            "float bbsGlowSize = clamp(" + U_SIZE + ", -32.0, 24.0);\n"
                + "        float bbsGlowSpread = clamp(" + U_SPREAD + ", 0.0, 1.0);\n"
                + "        float bbsGlowIntensity = max(" + U_INTENSITY + ", 0.0);\n"
                + "        float bbsSizeT = clamp(bbsGlowSize / 16.0, -2.0, 2.0);\n"
                + "        vec3 bbsDefault = (blur1 + blur2 + blur3 + blur4 + blur5 + blur6 + blur7) * 0.14;\n"
                + "        /* Softer bases — keep white clean (less harsh tight-mip crush). */\n"
                + "        vec3 bbsTight = (blur1 * 1.6 + blur2 * 1.4 + blur3 * 1.1 + blur4 * 0.6) / 4.7;\n"
                + "        vec3 bbsMid = (blur2 + blur3 + blur4 + blur5) * 0.25;\n"
                + "        vec3 bbsWide = (blur3 * 0.7 + blur4 + blur5 * 1.2 + blur6 * 1.4 + blur7 * 1.5) / 5.8;\n"
                + "        float bbsPos = clamp(bbsSizeT, 0.0, 1.0);\n"
                + "        float bbsNeg = clamp(-bbsSizeT / 2.0, 0.0, 1.0);\n"
                + "        vec3 bbsSized = mix(bbsMid, bbsWide, bbsPos * 0.85);\n"
                + "        bbsSized = mix(bbsSized, bbsTight, bbsNeg);\n"
                + "        /* Spread: 0 soft/wide wash, 1 sharper core — still soft enough for clean white. */\n"
                + "        vec3 bbsSoft = mix(bbsSized, bbsWide, 0.55);\n"
                + "        vec3 bbsSharp = mix(bbsSized, bbsTight, 0.55);\n"
                + "        vec3 bbsGlowBlur = mix(bbsSoft, bbsSharp, bbsGlowSpread);\n"
                + "        /* Soft-cap intensity so HDR tonemap does not dirty pure white. */\n"
                + "        float bbsGlowAmt = smoothstep(0.001, 0.35, bbsGlowIntensity);\n"
                + "        vec3 blur = mix(bbsDefault, bbsGlowBlur, bbsGlowAmt); /* " + DO_BLOOM_GUARD + " */";

        source = source.replace(from, to);

        String strengthFrom = "float bloomStrength = BLOOM_STRENGTH + 0.2 * darknessFactor;";
        String strengthTo =
            "float bloomStrength = BLOOM_STRENGTH + 0.2 * darknessFactor;\n"
                + "        if (bbsGlowIntensity > 0.001) {\n"
                + "         float bbsISoft = bbsGlowIntensity / (1.0 + bbsGlowIntensity * 0.12);\n"
                + "         bloomStrength *= 1.0 + bbsISoft * 0.1 + max(bbsGlowSize, 0.0) * 0.28;\n"
                + "         bloomStrength *= mix(1.35, 0.7, bbsGlowSpread);\n"
                + "         /* Slight lift so whites stay clean under Complementary tonemap. */\n"
                + "         bloomStrength = min(bloomStrength * 1.05, BLOOM_STRENGTH * 2.4);\n"
                + "        } /* BBS_COMP_GLOW_STRENGTH */";

        if (source.contains(strengthFrom) && !source.contains("BBS_COMP_GLOW_STRENGTH"))
        {
            source = source.replace(strengthFrom, strengthTo);
        }

        return source;
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
                + "#define " + PACK_GUARD + " 1\n"
                + "float " + APPLY + "(){\n"
                + " if(" + U_INTENSITY + "<=0.001) return 0.0;\n"
                + " float soft=" + U_INTENSITY + "/(1.0+" + U_INTENSITY + "*0.08);\n"
                + " float sizeM=clamp(" + U_SIZE + ",-32.0,24.0);\n"
                + " float spreadM=clamp(" + U_SPREAD + ",0.0,1.0);\n"
                + " /* Soft emission seed — avoid overdrive that dirty-washes white. */\n"
                + " return soft*(1.25+max(sizeM,0.0)*0.45)*(0.95+spreadM*0.15);\n"
                + "}\n"
                + "vec3 " + AFTER_LIGHT + "(vec3 rgb){\n"
                + " if(" + U_INTENSITY + "<=0.001) return rgb;\n"
                + " float soft=" + U_INTENSITY + "/(1.0+" + U_INTENSITY + "*0.08);\n"
                + " float sizeM=clamp(" + U_SIZE + ",-32.0,24.0);\n"
                + " float spreadM=clamp(" + U_SPREAD + ",0.0,1.0);\n"
                + " /* Gentle lift toward glow tint; keep neutrals clean white. */\n"
                + " rgb += rgb*soft*(0.12+max(sizeM,0.0)*0.1);\n"
                + " float lift=mix(1.02,1.12,spreadM);\n"
                + " return mix(rgb, rgb*lift, soft*mix(0.2,0.4,spreadM));\n"
                + "}\n"
                + "#endif\n";

        return insertAfterVersionOrPrepend(source, helpers);
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

    private static String insertUniforms(String source, String guard)
    {
        if (source.contains(guard))
        {
            return source;
        }

        String block =
            "uniform float " + U_INTENSITY + ";\n"
                + "uniform float " + U_SIZE + ";\n"
                + "uniform float " + U_SPREAD + ";\n"
                + "#ifndef " + guard + "\n"
                + "#define " + guard + "\n"
                + "#define " + PACK_GUARD + " 1\n"
                + "#endif\n";

        return insertAfterVersionOrPrepend(source, block);
    }

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
}
