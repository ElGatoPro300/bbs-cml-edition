package mchorse.bbs_mod.client;

import net.minecraft.client.gl.ShaderProgram;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

/**
 * Utility for setting shader uniforms directly via raw GL calls.
 *
 * In 1.21.11 the vanilla {@code GlUniform} interface no longer carries
 * {@code set()} methods.  This helper resolves uniform locations by name
 * from a {@link ShaderProgram} and writes values with
 * {@code GL20.glUniform*} calls.  Always call
 * {@link BBSRendering#bindProgram(ShaderProgram)} before setting uniforms.
 */
public final class BBSUniform
{
    private static final FloatBuffer MAT4_BUF = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MAT3_BUF = BufferUtils.createFloatBuffer(9);

    private BBSUniform()
    {
    }

    private static boolean ensureProgram(ShaderProgram program)
    {
        if (program == null || program.getGlRef() <= 0)
        {
            return false;
        }

        if (GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program.getGlRef())
        {
            GL20.glUseProgram(program.getGlRef());
        }

        return true;
    }

    public static boolean hasUniform(ShaderProgram program, String name)
    {
        return ModelEffectUniforms.contains(program, name) || (program != null && getLocation(program, name) >= 0);
    }

    /* ---- location query ---- */

    /**
     * Returns the GL uniform location for the given name inside the program,
     * or {@code -1} if the program is null or the uniform is not found.
     */
    public static int getLocation(ShaderProgram program, String name)
    {
        if (program == null)
        {
            return -1;
        }

        return GL20.glGetUniformLocation(program.getGlRef(), name);
    }

    /* ---- scalar / vector setters ---- */

    public static void set(ShaderProgram program, String name, int value)
    {
        if (ModelEffectUniforms.set(program, name, value))
        {
            return;
        }

        if (!ensureProgram(program))
        {
            return;
        }

        int loc = getLocation(program, name);

        if (loc >= 0)
        {
            GL20.glUniform1i(loc, value);
        }
    }

    public static void set(ShaderProgram program, String name, float value)
    {
        if (ModelEffectUniforms.set(program, name, value))
        {
            return;
        }

        if (!ensureProgram(program))
        {
            return;
        }

        int loc = getLocation(program, name);

        if (loc >= 0)
        {
            GL20.glUniform1f(loc, value);
        }
    }

    public static void set(ShaderProgram program, String name, float x, float y)
    {
        if (ModelEffectUniforms.set(program, name, x, y))
        {
            return;
        }

        if (!ensureProgram(program))
        {
            return;
        }

        int loc = getLocation(program, name);

        if (loc >= 0)
        {
            GL20.glUniform2f(loc, x, y);
        }
    }

    public static void set(ShaderProgram program, String name, float x, float y, float z)
    {
        if (ModelEffectUniforms.set(program, name, x, y, z))
        {
            return;
        }

        if (!ensureProgram(program))
        {
            return;
        }

        int loc = getLocation(program, name);

        if (loc >= 0)
        {
            GL20.glUniform3f(loc, x, y, z);
        }
    }

    public static void set(ShaderProgram program, String name, float x, float y, float z, float w)
    {
        if (ModelEffectUniforms.set(program, name, x, y, z, w))
        {
            return;
        }

        if (!ensureProgram(program))
        {
            return;
        }

        int loc = getLocation(program, name);

        if (loc >= 0)
        {
            GL20.glUniform4f(loc, x, y, z, w);
        }
    }

    /* ---- matrix setters ---- */

    public static void setMatrix4f(ShaderProgram program, String name, Matrix4f matrix)
    {
        if (ModelEffectUniforms.set(program, name, matrix))
        {
            return;
        }

        if (!ensureProgram(program))
        {
            return;
        }

        int loc = getLocation(program, name);

        if (loc >= 0)
        {
            synchronized (MAT4_BUF)
            {
                MAT4_BUF.clear();
                matrix.get(MAT4_BUF);
                MAT4_BUF.rewind();
                GL20.glUniformMatrix4fv(loc, false, MAT4_BUF);
            }
        }
    }

    public static void setMatrix3f(ShaderProgram program, String name, Matrix3f matrix)
    {
        if (ModelEffectUniforms.set(program, name, matrix))
        {
            return;
        }

        if (!ensureProgram(program))
        {
            return;
        }

        int loc = getLocation(program, name);

        if (loc >= 0)
        {
            synchronized (MAT3_BUF)
            {
                MAT3_BUF.clear();
                matrix.get(MAT3_BUF);
                MAT3_BUF.rewind();
                GL20.glUniformMatrix3fv(loc, false, MAT3_BUF);
            }
        }
    }
}
