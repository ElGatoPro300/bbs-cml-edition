package mchorse.bbs_mod.graphics.line;

import net.minecraft.client.render.VertexConsumer;

import org.joml.Matrix3x2fc;

public interface ILineRenderer <T>
{
    public void render(VertexConsumer builder, Matrix3x2fc matrix, LinePoint<T> point);
}