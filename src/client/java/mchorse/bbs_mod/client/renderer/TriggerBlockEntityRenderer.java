package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.graphics.Draw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.HashSet;
import java.util.Set;

public class TriggerBlockEntityRenderer implements BlockEntityRenderer<TriggerBlockEntity>
{
    public static final Set<TriggerBlockEntity> capturedTriggerBlocks = new HashSet<>();

    public TriggerBlockEntityRenderer(BlockEntityRendererFactory.Context ctx)
    {}

    @Override
    public void render(TriggerBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        capturedTriggerBlocks.add(entity);

        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (mc.getDebugHud().shouldShowDebugHud())
        {
            matrices.push();
            matrices.translate(0.5D, 0, 0.5D);
            /* Render green debug box for triggers */
            Draw.renderBox(matrices, -0.5D, 0, -0.5D, 1, 1, 1, 0, 1F, 0.5F, 0.5F);
            matrices.pop();

            if (entity.region.get())
            {
                Box box = entity.getRegionBoxRelative();

                /* Render white debug box for region triggers */
                RenderSystem.disableDepthTest();
                Draw.renderBox(matrices, box.minX, box.minY, box.minZ, box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ, 1F, 1F, 1F, 0.5F);
                RenderSystem.enableDepthTest();
            }
        }
    }
}
