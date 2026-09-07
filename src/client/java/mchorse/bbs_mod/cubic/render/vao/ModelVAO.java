package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.client.BBSRendering;

import net.minecraft.client.render.VertexFormats;

import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL30;

public class ModelVAO implements IModelVAO
{
    private int vao;
    private int vao2;
    private int count;
    private ModelVAOData data;

    public ModelVAOData getData()
    {
        return this.data;
    }

    public ModelVAO(ModelVAOData data)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        this.upload(data);

        GL30.glBindVertexArray(currentVAO);
    }

    public void delete()
    {
        if (this.vao != 0)
        {
            GL30.glDeleteVertexArrays(this.vao);
            this.vao = 0;
        }

        if (this.vao2 != 0)
        {
            GL30.glDeleteVertexArrays(this.vao2);
            this.vao2 = 0;
        }
    }

    public void upload(ModelVAOData data)
    {
        this.data = data;
        this.vao = GL30.glGenVertexArrays();
        this.vao2 = GL30.glGenVertexArrays();

        GL30.glBindVertexArray(this.vao);

        int vertexBuffer = GL30.glGenBuffers();
        int normalBuffer = GL30.glGenBuffers();
        int tangentsBuffer = GL30.glGenBuffers();
        int texCoordBuffer = GL30.glGenBuffers();
        int midTexCoordBuffer = GL30.glGenBuffers();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vertexBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.vertices(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.POSITION, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, normalBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.normals(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.NORMAL, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, texCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.texCoords(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, tangentsBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.tangents(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.TANGENTS, 4, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, midTexCoordBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.midTexCoords(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.MID_TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glEnableVertexAttribArray(Attributes.POSITION);
        GL30.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glEnableVertexAttribArray(Attributes.NORMAL);

        GL30.glDisableVertexAttribArray(Attributes.COLOR);
        GL30.glDisableVertexAttribArray(Attributes.OVERLAY_UV);
        GL30.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);
        GL30.glDisableVertexAttribArray(Attributes.TANGENTS);
        GL30.glDisableVertexAttribArray(Attributes.MID_TEXTURE_UV);

        /* VertexFormats.POSITION_TEXTURE_LIGHT_COLOR */
        GL30.glBindVertexArray(this.vao2);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vertexBuffer);
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 0, 0);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, texCoordBuffer);
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, 0, 0);

        GL30.glEnableVertexAttribArray(0);
        GL30.glEnableVertexAttribArray(1);
        GL30.glDisableVertexAttribArray(2);
        GL30.glDisableVertexAttribArray(3);

        this.count = data.vertices().length / 3;
    }

    @Override
    public void render(VertexFormat format, float r, float g, float b, float a, int light, int overlay)
    {
        boolean hasShaders = isShadersEnabled();
        int vao = hasShaders || format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL ? this.vao : this.vao2;

        if (vao == 0 || !GL30.glIsVertexArray(vao))
        {
            return;
        }

        /* Restore previous binding — glBindVertexArray(0) leaves no array object active,
         * so the next Batcher2D/Sodium BufferBuilder path spam GL_INVALID_OPERATION
         * ("Array object is not active") once per form-list preview. */
        int previousVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        GL30.glBindVertexArray(vao);

        if (vao == this.vao)
        {
            /* Explicitly disable these attributes to ensure constant values are used */
            GL30.glDisableVertexAttribArray(Attributes.COLOR);
            GL30.glDisableVertexAttribArray(Attributes.LIGHTMAP_UV);
            GL30.glDisableVertexAttribArray(Attributes.OVERLAY_UV);

            GL30.glVertexAttrib4f(Attributes.COLOR, r, g, b, a);
            GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, overlay & '\uffff', overlay >> 16 & '\uffff');
            GL30.glVertexAttribI2i(Attributes.LIGHTMAP_UV, light & '\uffff', light >> 16 & '\uffff');
        }
        else
        {
            GL30.glVertexAttribI2i(2, light & '\uffff', light >> 16 & '\uffff');
            GL30.glVertexAttrib4f(3, r, g, b, a);
        }

        if (hasShaders) GL30.glEnableVertexAttribArray(Attributes.MID_TEXTURE_UV);
        else GL30.glDisableVertexAttribArray(Attributes.MID_TEXTURE_UV);

        if (hasShaders) GL30.glEnableVertexAttribArray(Attributes.TANGENTS);
        else GL30.glDisableVertexAttribArray(Attributes.TANGENTS);

        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, this.count);
        GL30.glBindVertexArray(previousVAO);
    }

    public static boolean isShadersEnabled()
    {
        return BBSRendering.isIrisShadersEnabled();
    }
}
