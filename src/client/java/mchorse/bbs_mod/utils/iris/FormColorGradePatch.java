package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.utils.colors.ColorAdjustments;

import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patches Complementary / BSL entity (and block) gbuffer fragment shaders so form
 * brightness / contrast / hue / saturation grade the pack-lit albedo in-place.
 * <p>
 * Values are pushed per draw via {@link #uploadToCurrentProgram()} after the Iris program
 * binds, and also registered as Iris custom uniforms so the symbols stay live in the pack.
 */
public final class FormColorGradePatch
{
    public static final String BRIGHTNESS = "form_grade_brightness";
    public static final String CONTRAST = "form_grade_contrast";
    public static final String HUE = "form_grade_hue";
    public static final String SATURATION = "form_grade_saturation";

    private static final String U_BRIGHTNESS = ShaderCurves.UNIFORM_IDENTIFIER + BRIGHTNESS;
    private static final String U_CONTRAST = ShaderCurves.UNIFORM_IDENTIFIER + CONTRAST;
    private static final String U_HUE = ShaderCurves.UNIFORM_IDENTIFIER + HUE;
    private static final String U_SATURATION = ShaderCurves.UNIFORM_IDENTIFIER + SATURATION;
    private static final String HELPER_GUARD = "BBS_FORM_COLOR_GRADE_HELPERS";
    private static final String APPLY = "bbsApplyFormColorGrade";

    private static final Pattern FRAG_DATA0_COLOR = Pattern.compile(
        "gl_FragData\\[0]\\s*=\\s*color\\s*;"
    );
    private static final Pattern FRAG_DATA0_VEC4_COLOR = Pattern.compile(
        "gl_FragData\\[0]\\s*=\\s*vec4\\(\\s*color\\.rgb\\s*,\\s*color\\.a\\s*\\)\\s*;"
    );
    private static final Pattern FRAG_DATA0_VEC4_COLOR_A = Pattern.compile(
        "gl_FragData\\[0]\\s*=\\s*vec4\\(\\s*color\\.rgb\\s*,\\s*([^;]+?)\\)\\s*;"
    );
    private static final Pattern OUT_COLORTEX0 = Pattern.compile(
        "(colortex0Out|outColor0|framebuffer0)\\s*=\\s*vec4\\(\\s*color\\.rgb\\s*,\\s*([^;]+?)\\)\\s*;"
    );

    private static float brightness;
    private static float contrast;
    private static float hue;
    private static float saturation;
    private static boolean patchedThisPack;

    private FormColorGradePatch()
    {}

    public static void set(float brightnessValue, float contrastValue, float hueValue, float saturationValue)
    {
        brightness = brightnessValue;
        contrast = contrastValue;
        hue = hueValue;
        saturation = saturationValue;
    }

    public static void clear()
    {
        brightness = 0F;
        contrast = 0F;
        hue = 0F;
        saturation = 0F;
    }

    public static float getBrightness()
    {
        return brightness;
    }

    public static float getContrast()
    {
        return contrast;
    }

    public static float getHue()
    {
        return hue;
    }

    public static float getSaturation()
    {
        return saturation;
    }

    public static boolean isActive()
    {
        return ColorAdjustments.isActive(brightness, contrast, hue, saturation);
    }

    /**
     * True when Complementary / BSL is loaded — live Iris grading should be used so pack
     * lighting and shadows stay on the model.
     */
    public static boolean canUseLivePackGrade()
    {
        return shouldPatchPack();
    }

    /**
     * True when the loaded pack was rewritten with grade helpers (Complementary / BSL).
     */
    public static boolean isPackPatched()
    {
        return patchedThisPack && shouldPatchPack();
    }

    public static void resetPackState()
    {
        patchedThisPack = false;
        clear();
    }

    public static boolean shouldPatchPack()
    {
        String pack = resolvePackName();

        if (pack.isEmpty())
        {
            return false;
        }

        String lower = pack.toLowerCase(Locale.ROOT);

        return lower.contains("complementary") || lower.contains("bsl");
    }

    public static void addUniforms(List<CachedUniform> list)
    {
        list.add(new FloatCachedUniform(U_BRIGHTNESS, UniformUpdateFrequency.PER_FRAME, FormColorGradePatch::getBrightness));
        list.add(new FloatCachedUniform(U_CONTRAST, UniformUpdateFrequency.PER_FRAME, FormColorGradePatch::getContrast));
        list.add(new FloatCachedUniform(U_HUE, UniformUpdateFrequency.PER_FRAME, FormColorGradePatch::getHue));
        list.add(new FloatCachedUniform(U_SATURATION, UniformUpdateFrequency.PER_FRAME, FormColorGradePatch::getSaturation));
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

        if (!shouldPatchPack())
        {
            return source;
        }

        /* Run before grade early-return: Complementary GetCustomEmissionForIPBR reloads albedo
         * without glColor when a real specular map is bound (specular.a != 0), wiping form alpha. */
        source = restoreGlColorAfterCustomEmissionReload(source);

        if (!isEntityOrBlockGbufferFragment(source))
        {
            return source;
        }

        if (source.contains(APPLY + "(") && source.contains(U_BRIGHTNESS))
        {
            patchedThisPack = true;

            return source;
        }

        String patched = insertHelpers(source);
        String beforeApply = patched;

        patched = applyGradeToColorWrites(patched);

        if (patched.contains(APPLY + "("))
        {
            patchedThisPack = true;
        }
        else if (beforeApply.contains(U_BRIGHTNESS))
        {
            /* Helpers inserted but no call site — still mark so callers can detect pack intent. */
            patchedThisPack = true;
        }

        return patched;
    }

    /**
     * Complementary (IPBR emissive modes 2/3): {@code GetCustomEmissionForIPBR} does
     * {@code color = texture2D(tex, texCoord)} when specular.a != 0, dropping {@code glColor}
     * (form soft opacity / vertex alpha). Iris default specular uses a=0 so the early return
     * hides the bug until a real {@code *_s.png} is assigned. Re-apply {@code glColor} after
     * that reload.
     */
    private static String restoreGlColorAfterCustomEmissionReload(String source)
    {
        if (source.contains("BBS_PBR_EMIT_ALPHA") || !source.contains("GetCustomEmissionForIPBR"))
        {
            return source;
        }

        Pattern reload = Pattern.compile(
            "(if\\s*\\(\\s*specularMap\\.a\\s*==\\s*0\\.0\\s*\\)\\s*return\\s+emission\\s*;\\s*)"
                + "color\\s*=\\s*texture2D\\s*\\(\\s*tex\\s*,\\s*texCoord\\s*\\)\\s*;",
            Pattern.MULTILINE
        );
        Matcher matcher = reload.matcher(source);

        if (!matcher.find())
        {
            return source;
        }

        return matcher.replaceAll(
            "$1color = texture2D(tex, texCoord); color *= glColor; /* BBS_PBR_EMIT_ALPHA */"
        );
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

    private static String insertHelpers(String source)
    {
        if (source.contains(HELPER_GUARD))
        {
            return source;
        }

        int version = source.indexOf("#version");

        if (version < 0)
        {
            return source;
        }

        int nextNewLine = source.indexOf('\n', version);

        if (nextNewLine < 0)
        {
            return source;
        }

        String helpers =
            "uniform float " + U_BRIGHTNESS + ";\n"
                + "uniform float " + U_CONTRAST + ";\n"
                + "uniform float " + U_HUE + ";\n"
                + "uniform float " + U_SATURATION + ";\n"
                + "#ifndef " + HELPER_GUARD + "\n"
                + "#define " + HELPER_GUARD + "\n"
                + "vec3 bbsFormRgb2Hsl(vec3 c){\n"
                + " float maxc=max(c.r,max(c.g,c.b)); float minc=min(c.r,min(c.g,c.b)); float l=(maxc+minc)*0.5; float d=maxc-minc;\n"
                + " if(d<1e-5) return vec3(0.0,0.0,l);\n"
                + " float s=l>0.5?d/(2.0-maxc-minc):d/(maxc+minc); float h;\n"
                + " if(maxc==c.r) h=(c.g-c.b)/d+(c.g<c.b?6.0:0.0); else if(maxc==c.g) h=(c.b-c.r)/d+2.0; else h=(c.r-c.g)/d+4.0;\n"
                + " return vec3(h/6.0,s,l);\n"
                + "}\n"
                + "float bbsFormHue2Rgb(float p,float q,float t){\n"
                + " if(t<0.0)t+=1.0; if(t>1.0)t-=1.0;\n"
                + " if(t<1.0/6.0)return p+(q-p)*6.0*t; if(t<0.5)return q; if(t<2.0/3.0)return p+(q-p)*(2.0/3.0-t)*6.0; return p;\n"
                + "}\n"
                + "vec3 bbsFormHsl2Rgb(vec3 hsl){\n"
                + " float h=hsl.x; float s=hsl.y; float l=hsl.z;\n"
                + " if(s<1e-5) return vec3(l);\n"
                + " float q=l<0.5?l*(1.0+s):l+s-l*s; float p=2.0*l-q;\n"
                + " return vec3(bbsFormHue2Rgb(p,q,h+1.0/3.0),bbsFormHue2Rgb(p,q,h),bbsFormHue2Rgb(p,q,h-1.0/3.0));\n"
                + "}\n"
                + "vec3 bbsFormPreserveLitShadow(vec3 inputRgb, vec3 gradedRgb){\n"
                + " return gradedRgb;\n"
                + "}\n"
                + "vec3 " + APPLY + "(vec3 rgb){\n"
                + " if(abs(" + U_BRIGHTNESS + ")<0.001 && abs(" + U_CONTRAST + ")<0.001 && abs(" + U_HUE + ")<0.001 && abs(" + U_SATURATION + ")<0.001) return rgb;\n"
                + " rgb = rgb + " + U_BRIGHTNESS + ";\n"
                + " rgb = vec3(0.5) + (1.0 + " + U_CONTRAST + ") * (rgb - vec3(0.5));\n"
                + " vec3 satHsl = bbsFormRgb2Hsl(clamp(rgb, 0.0, 1.0));\n"
                + " satHsl.y = clamp(satHsl.y * (1.0 + " + U_SATURATION + "), 0.0, 1.0);\n"
                + " rgb = bbsFormHsl2Rgb(satHsl);\n"
                + " if(abs(" + U_HUE + ") > 0.01){\n"
                + "  vec3 hsl = bbsFormRgb2Hsl(clamp(rgb, 0.0, 1.0));\n"
                + "  hsl.x = fract(hsl.x + " + U_HUE + " / 360.0);\n"
                + "  rgb = bbsFormHsl2Rgb(hsl);\n"
                + " }\n"
                + " return clamp(rgb, 0.0, 1.0);\n"
                + "}\n"
                + "#endif\n";

        return source.substring(0, nextNewLine + 1) + helpers + source.substring(nextNewLine + 1);
    }

    private static String applyGradeToColorWrites(String source)
    {
        if (source.contains(APPLY + "(color.rgb)"))
        {
            return source;
        }

        String patched = source;

        patched = FRAG_DATA0_COLOR.matcher(patched).replaceAll(
            "gl_FragData[0] = vec4(" + APPLY + "(color.rgb), color.a);"
        );
        patched = FRAG_DATA0_VEC4_COLOR.matcher(patched).replaceAll(
            "gl_FragData[0] = vec4(" + APPLY + "(color.rgb), color.a);"
        );

        if (!patched.contains(APPLY + "(color.rgb)"))
        {
            Matcher alphaMatcher = FRAG_DATA0_VEC4_COLOR_A.matcher(patched);
            StringBuffer alphaBuffer = new StringBuffer();

            while (alphaMatcher.find())
            {
                alphaMatcher.appendReplacement(alphaBuffer,
                    Matcher.quoteReplacement("gl_FragData[0] = vec4(" + APPLY + "(color.rgb), " + alphaMatcher.group(1) + ");"));
            }

            alphaMatcher.appendTail(alphaBuffer);
            patched = alphaBuffer.toString();
        }

        if (!patched.contains(APPLY + "(color.rgb)"))
        {
            Matcher outMatcher = OUT_COLORTEX0.matcher(patched);
            StringBuffer outBuffer = new StringBuffer();

            while (outMatcher.find())
            {
                outMatcher.appendReplacement(outBuffer,
                    Matcher.quoteReplacement(outMatcher.group(1) + " = vec4(" + APPLY + "(color.rgb), " + outMatcher.group(2) + ");"));
            }

            outMatcher.appendTail(outBuffer);
            patched = outBuffer.toString();
        }

        if (!patched.contains(APPLY + "(color.rgb)"))
        {
            patched = injectAfterDoLighting(patched);
        }

        /* Last resort: grade albedo before lighting so pack still shades the graded color. */
        if (!patched.contains(APPLY + "("))
        {
            patched = injectBeforeDoLighting(patched);
        }

        return patched;
    }

    private static String injectAfterDoLighting(String source)
    {
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

        String insert = "\n color.rgb = " + APPLY + "(color.rgb);";

        return source.substring(0, semi + 1) + insert + source.substring(semi + 1);
    }

    private static String injectBeforeDoLighting(String source)
    {
        String marker = "DoLighting(";
        int index = source.lastIndexOf(marker);

        if (index < 0)
        {
            return source;
        }

        String insert = "color.rgb = " + APPLY + "(color.rgb);\n ";

        return source.substring(0, index) + insert + source.substring(index);
    }

    /**
     * Bind current grade values onto the active GL program (Iris entity/block after bind).
     */
    public static void uploadToCurrentProgram()
    {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        if (program <= 0)
        {
            return;
        }

        setUniform(program, U_BRIGHTNESS, brightness);
        setUniform(program, U_CONTRAST, contrast);
        setUniform(program, U_HUE, hue);
        setUniform(program, U_SATURATION, saturation);
    }

    private static void setUniform(int program, String name, float value)
    {
        int location = GL20.glGetUniformLocation(program, name);

        if (location >= 0)
        {
            GL20.glUniform1f(location, value);
        }
    }
}
