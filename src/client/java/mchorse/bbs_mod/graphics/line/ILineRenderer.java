package mchorse.bbs_mod.graphics.line;

import net.minecraft.client.render.BufferBuilder;

import org.joml.Matrix4f;

public interface ILineRenderer <T>
{
    public void render(BufferBuilder builder, Matrix4f matrix, LinePoint<T> point);
}