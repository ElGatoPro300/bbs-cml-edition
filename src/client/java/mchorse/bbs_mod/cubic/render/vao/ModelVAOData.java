package mchorse.bbs_mod.cubic.render.vao;

public record ModelVAOData(float[] vertices, float[] normals, float[] tangents, float[] texCoords)
{
    public float[] midTexCoords()
    {
        return calculateMidTexCoords(this.texCoords);
    }

    public static float[] calculateMidTexCoords(float[] texCoords)
    {
        if (texCoords.length % 6 != 0)
        {
            return texCoords;
        }

        float[] midTexCoords = new float[texCoords.length];

        for (int i = 0, c = texCoords.length / 6; i < c; i++)
        {
            int offset = i * 6;
            float u = (texCoords[offset] + texCoords[offset + 2] + texCoords[offset + 4]) / 3F;
            float v = (texCoords[offset + 1] + texCoords[offset + 3] + texCoords[offset + 5]) / 3F;

            midTexCoords[offset] = u;
            midTexCoords[offset + 1] = v;
            midTexCoords[offset + 2] = u;
            midTexCoords[offset + 3] = v;
            midTexCoords[offset + 4] = u;
            midTexCoords[offset + 5] = v;
        }

        return midTexCoords;
    }
}
