package mchorse.bbs_mod.client;

import net.minecraft.client.gl.ShaderProgram;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Std140 layout shared with bbs:model_effects.glsl. Values belong to the configured program. */
public final class ModelEffectUniforms
{
    private record Field(int offset, String type) {}

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Map<ShaderProgram, ByteBuffer> VALUES = new WeakHashMap<>();

    public static final int SIZE = 1328;

    static
    {
        FIELDS.put("ModelViewMat", new Field(0, "mat4"));
        FIELDS.put("NormalMat", new Field(64, "mat3"));
        FIELDS.put("FogMat", new Field(112, "mat4"));
        FIELDS.put("ProjMat", new Field(176, "mat4"));
        FIELDS.put("FormRootInverse", new Field(240, "mat4"));
        FIELDS.put("FogShape", new Field(304, "int"));
        FIELDS.put("ColorModulator", new Field(320, "vec4"));
        FIELDS.put("FogStart", new Field(336, "float"));
        FIELDS.put("FogEnd", new Field(340, "float"));
        FIELDS.put("TextureBlendFactor", new Field(344, "float"));
        FIELDS.put("TextureBlendActive", new Field(348, "float"));
        FIELDS.put("PaintColor", new Field(352, "vec4"));
        FIELDS.put("GlowingColor", new Field(368, "vec4"));
        FIELDS.put("PaintOverlay", new Field(384, "float"));
        FIELDS.put("GlowPaintOnly", new Field(388, "float"));
        FIELDS.put("PaintEffectInverse", new Field(400, "mat4"));
        FIELDS.put("PaintEffectActive", new Field(464, "float"));
        FIELDS.put("PaintMaskHalf", new Field(480, "vec3"));
        FIELDS.put("PaintMaskBottomAnchored", new Field(492, "float"));
        FIELDS.put("PaintMaskShape", new Field(496, "float"));
        FIELDS.put("GlowEffectInverse", new Field(512, "mat4"));
        FIELDS.put("GlowEffectActive", new Field(576, "float"));
        FIELDS.put("GlowMaskHalf", new Field(592, "vec3"));
        FIELDS.put("GlowMaskBottomAnchored", new Field(604, "float"));
        FIELDS.put("GlowMaskShape", new Field(608, "float"));
        FIELDS.put("ColorEffectInverse", new Field(624, "mat4"));
        FIELDS.put("ColorEffectActive", new Field(688, "float"));
        FIELDS.put("ColorMaskHalf", new Field(704, "vec3"));
        FIELDS.put("ColorMaskBottomAnchored", new Field(716, "float"));
        FIELDS.put("ColorMaskShape", new Field(720, "float"));
        FIELDS.put("FormColorTint", new Field(736, "vec4"));
        FIELDS.put("ColorTintMasked", new Field(752, "float"));
        FIELDS.put("ColorTintOverlay", new Field(756, "float"));
        FIELDS.put("ColorGradeOverlay", new Field(760, "float"));
        FIELDS.put("FormColorGrade", new Field(768, "vec4"));
        FIELDS.put("GradeBrightnessInverse", new Field(784, "mat4"));
        FIELDS.put("GradeBrightnessActive", new Field(848, "float"));
        FIELDS.put("GradeBrightnessHalf", new Field(864, "vec3"));
        FIELDS.put("GradeBrightnessBottomAnchored", new Field(876, "float"));
        FIELDS.put("GradeBrightnessShape", new Field(880, "float"));
        FIELDS.put("GradeContrastInverse", new Field(896, "mat4"));
        FIELDS.put("GradeContrastActive", new Field(960, "float"));
        FIELDS.put("GradeContrastHalf", new Field(976, "vec3"));
        FIELDS.put("GradeContrastBottomAnchored", new Field(988, "float"));
        FIELDS.put("GradeContrastShape", new Field(992, "float"));
        FIELDS.put("GradeHueInverse", new Field(1008, "mat4"));
        FIELDS.put("GradeHueActive", new Field(1072, "float"));
        FIELDS.put("GradeHueHalf", new Field(1088, "vec3"));
        FIELDS.put("GradeHueBottomAnchored", new Field(1100, "float"));
        FIELDS.put("GradeHueShape", new Field(1104, "float"));
        FIELDS.put("GradeSaturationInverse", new Field(1120, "mat4"));
        FIELDS.put("GradeSaturationActive", new Field(1184, "float"));
        FIELDS.put("GradeSaturationHalf", new Field(1200, "vec3"));
        FIELDS.put("GradeSaturationBottomAnchored", new Field(1212, "float"));
        FIELDS.put("GradeSaturationShape", new Field(1216, "float"));
        FIELDS.put("IViewRotMat", new Field(1232, "mat3"));
        FIELDS.put("Target", new Field(1280, "int"));
        FIELDS.put("ColorGradeActive", new Field(1284, "float"));
        FIELDS.put("GlowScale", new Field(1288, "float"));
        FIELDS.put("GlowOverlayColor", new Field(1296, "vec4"));
        FIELDS.put("ColorMaskFalloff", new Field(1312, "float"));
    }

    public static ShaderProgram register(ShaderProgram program)
    {
        if (program != null && program != ShaderProgram.INVALID)
        {
            VALUES.computeIfAbsent(program, ignored -> createDefaults());
        }

        return program;
    }

    private static ByteBuffer createDefaults()
    {
        ByteBuffer data = BufferUtils.createByteBuffer(SIZE);

        for (Field field : FIELDS.values())
        {
            if (field.type.startsWith("mat"))
            {
                int dimensions = field.type.equals("mat4") ? 4 : 3;

                for (int i = 0; i < dimensions; i++)
                {
                    data.putFloat(field.offset + i * 20, 1F);
                }
            }
        }

        for (String name : new String[] {"ColorModulator", "FormColorTint"})
        {
            int offset = FIELDS.get(name).offset;

            for (int i = 0; i < 4; i++)
            {
                data.putFloat(offset + i * 4, 1F);
            }
        }

        return data;
    }

    public static boolean contains(ShaderProgram program, String name)
    {
        return VALUES.containsKey(program) && FIELDS.containsKey(name);
    }

    public static float value(ShaderProgram program, String name)
    {
        return contains(program, name) ? VALUES.get(program).getFloat(FIELDS.get(name).offset) : 0F;
    }

    public static boolean set(ShaderProgram program, String name, float... values)
    {
        if (!contains(program, name))
        {
            return false;
        }

        ByteBuffer data = VALUES.get(program);
        Field field = FIELDS.get(name);

        for (int i = 0; i < values.length; i++)
        {
            if (field.type.equals("int"))
            {
                data.putInt(field.offset + i * 4, (int) values[i]);
            }
            else
            {
                data.putFloat(field.offset + i * 4, values[i]);
            }
        }

        return true;
    }

    public static boolean set(ShaderProgram program, String name, Matrix4f matrix)
    {
        return set(program, name, matrix.get(new float[16]));
    }

    public static boolean set(ShaderProgram program, String name, Matrix3f matrix)
    {
        float[] packed = matrix.get(new float[9]);
        float[] padded = new float[12];

        for (int i = 0; i < 9; i++)
        {
            padded[(i / 3) * 4 + i % 3] = packed[i];
        }

        return set(program, name, padded);
    }

    public static ByteBuffer data(ShaderProgram program)
    {
        return VALUES.get(program).duplicate();
    }
}
