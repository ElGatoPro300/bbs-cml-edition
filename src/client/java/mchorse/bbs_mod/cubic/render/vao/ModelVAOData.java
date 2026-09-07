package mchorse.bbs_mod.cubic.render.vao;

public record ModelVAOData(float[] vertices, float[] normals, float[] tangents, float[] texCoords, float[] colors)
{
    public ModelVAOData(float[] vertices, float[] normals, float[] tangents, float[] texCoords)
    {
        this(vertices, normals, tangents, texCoords, null);
    }

    public boolean hasColors()
    {
        return this.colors != null && this.colors.length >= 4;
    }
}
