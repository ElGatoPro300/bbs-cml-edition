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

    public static final int SIZE = 1264;

    static
    {
        FIELDS.put("ModelViewMat", new Field(0, "mat4"));
        FIELDS.put("NormalMat", new Field(64, "mat3"));
        FIELDS.put("FogMat", new Field(112, "mat4"));
        FIELDS.put("FormRootInverse", new Field(176, "mat4"));
        FIELDS.put("FogShape", new Field(240, "int"));
        FIELDS.put("ColorModulator", new Field(256, "vec4"));
        FIELDS.put("FogStart", new Field(272, "float"));
        FIELDS.put("FogEnd", new Field(276, "float"));
        FIELDS.put("TextureBlendFactor", new Field(280, "float"));
        FIELDS.put("TextureBlendActive", new Field(284, "float"));
        FIELDS.put("PaintColor", new Field(288, "vec4"));
        FIELDS.put("GlowingColor", new Field(304, "vec4"));
        FIELDS.put("PaintOverlay", new Field(320, "float"));
        FIELDS.put("GlowPaintOnly", new Field(324, "float"));
        FIELDS.put("PaintEffectInverse", new Field(336, "mat4"));
        FIELDS.put("PaintEffectActive", new Field(400, "float"));
        FIELDS.put("PaintMaskHalf", new Field(416, "vec3"));
        FIELDS.put("PaintMaskBottomAnchored", new Field(428, "float"));
        FIELDS.put("PaintMaskShape", new Field(432, "float"));
        FIELDS.put("GlowEffectInverse", new Field(448, "mat4"));
        FIELDS.put("GlowEffectActive", new Field(512, "float"));
        FIELDS.put("GlowMaskHalf", new Field(528, "vec3"));
        FIELDS.put("GlowMaskBottomAnchored", new Field(540, "float"));
        FIELDS.put("GlowMaskShape", new Field(544, "float"));
        FIELDS.put("ColorEffectInverse", new Field(560, "mat4"));
        FIELDS.put("ColorEffectActive", new Field(624, "float"));
        FIELDS.put("ColorMaskHalf", new Field(640, "vec3"));
        FIELDS.put("ColorMaskBottomAnchored", new Field(652, "float"));
        FIELDS.put("ColorMaskShape", new Field(656, "float"));
        FIELDS.put("FormColorTint", new Field(672, "vec4"));
        FIELDS.put("ColorTintMasked", new Field(688, "float"));
        FIELDS.put("ColorTintOverlay", new Field(692, "float"));
        FIELDS.put("ColorGradeOverlay", new Field(696, "float"));
        FIELDS.put("FormColorGrade", new Field(704, "vec4"));
        FIELDS.put("GradeBrightnessInverse", new Field(720, "mat4"));
        FIELDS.put("GradeBrightnessActive", new Field(784, "float"));
        FIELDS.put("GradeBrightnessHalf", new Field(800, "vec3"));
        FIELDS.put("GradeBrightnessBottomAnchored", new Field(812, "float"));
        FIELDS.put("GradeBrightnessShape", new Field(816, "float"));
        FIELDS.put("GradeContrastInverse", new Field(832, "mat4"));
        FIELDS.put("GradeContrastActive", new Field(896, "float"));
        FIELDS.put("GradeContrastHalf", new Field(912, "vec3"));
        FIELDS.put("GradeContrastBottomAnchored", new Field(924, "float"));
        FIELDS.put("GradeContrastShape", new Field(928, "float"));
        FIELDS.put("GradeHueInverse", new Field(944, "mat4"));
        FIELDS.put("GradeHueActive", new Field(1008, "float"));
        FIELDS.put("GradeHueHalf", new Field(1024, "vec3"));
        FIELDS.put("GradeHueBottomAnchored", new Field(1036, "float"));
        FIELDS.put("GradeHueShape", new Field(1040, "float"));
        FIELDS.put("GradeSaturationInverse", new Field(1056, "mat4"));
        FIELDS.put("GradeSaturationActive", new Field(1120, "float"));
        FIELDS.put("GradeSaturationHalf", new Field(1136, "vec3"));
        FIELDS.put("GradeSaturationBottomAnchored", new Field(1148, "float"));
        FIELDS.put("GradeSaturationShape", new Field(1152, "float"));
        FIELDS.put("IViewRotMat", new Field(1168, "mat3"));
        FIELDS.put("Target", new Field(1216, "int"));
        FIELDS.put("ColorGradeActive", new Field(1220, "float"));
        FIELDS.put("GlowScale", new Field(1224, "float"));
        FIELDS.put("GlowOverlayColor", new Field(1232, "vec4"));
        FIELDS.put("ColorMaskFalloff", new Field(1248, "float"));
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
