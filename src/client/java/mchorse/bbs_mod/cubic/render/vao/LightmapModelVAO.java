package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.client.BBSRendering;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import org.lwjgl.opengl.GL30;

public class LightmapModelVAO implements IModelVAO
{
    private int vao;
    private int count;

    public LightmapModelVAO(ModelVAOData data, int[] lightData)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        this.upload(data, lightData);
        GL30.glBindVertexArray(currentVAO);
    }

    public void delete()
    {
        if (this.vao != 0)
        {
            GL30.glDeleteVertexArrays(this.vao);
            this.vao = 0;
        }
    }

    private void upload(ModelVAOData data, int[] lightData)
    {
        this.vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(this.vao);

        int vertexBuffer = GL30.glGenBuffers();
        int normalBuffer = GL30.glGenBuffers();
        int texCoordBuffer = GL30.glGenBuffers();
        int tangentsBuffer = GL30.glGenBuffers();
        int midTexCoordBuffer = GL30.glGenBuffers();
        int lightBuffer = GL30.glGenBuffers();

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
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, data.texCoords(), GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribPointer(Attributes.MID_TEXTURE_UV, 2, GL30.GL_FLOAT, false, 0, 0);

        short[] light = new short[lightData.length * 2];
        for (int i = 0; i < lightData.length; i++)
        {
            int packed = lightData[i];
            light[i * 2] = (short) (packed & 0xFFFF);
            light[i * 2 + 1] = (short) ((packed >> 16) & 0xFFFF);
        }

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, lightBuffer);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, light, GL30.GL_STATIC_DRAW);
        GL30.glVertexAttribIPointer(Attributes.LIGHTMAP_UV, 2, GL30.GL_SHORT, 0, 0);

        GL30.glEnableVertexAttribArray(Attributes.POSITION);
        GL30.glEnableVertexAttribArray(Attributes.TEXTURE_UV);
        GL30.glEnableVertexAttribArray(Attributes.NORMAL);
        GL30.glEnableVertexAttribArray(Attributes.LIGHTMAP_UV);

        GL30.glDisableVertexAttribArray(Attributes.COLOR);
        GL30.glDisableVertexAttribArray(Attributes.OVERLAY_UV);

        this.count = data.vertices().length / 3;
    }

    @Override
    public void render(VertexFormat format, float r, float g, float b, float a, int light, int overlay)
    {
        if (this.vao == 0 || !GL30.glIsVertexArray(this.vao))
        {
            return;
        }

        boolean hasShaders = BBSRendering.isIrisShadersEnabled();
        int previousVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        GL30.glBindVertexArray(this.vao);

        GL30.glDisableVertexAttribArray(Attributes.COLOR);
        GL30.glDisableVertexAttribArray(Attributes.OVERLAY_UV);

        GL30.glVertexAttrib4f(Attributes.COLOR, r, g, b, a);
        GL30.glVertexAttribI2i(Attributes.OVERLAY_UV, overlay & 0xFFFF, overlay >> 16 & 0xFFFF);

        if (hasShaders)
        {
            GL30.glEnableVertexAttribArray(Attributes.MID_TEXTURE_UV);
            GL30.glEnableVertexAttribArray(Attributes.TANGENTS);
        }
        else
        {
            GL30.glDisableVertexAttribArray(Attributes.MID_TEXTURE_UV);
            GL30.glDisableVertexAttribArray(Attributes.TANGENTS);
        }

        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, this.count);
        GL30.glBindVertexArray(previousVAO);
    }
}

