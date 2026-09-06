package mchorse.bbs_mod.forms.renderers.utils;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

public class StructureVirtualBlockRenderView extends VirtualBlockRenderView
{
    private final List<BlockPos> emitters = new ArrayList<>();
    private final List<Integer> emitterLevels = new ArrayList<>();

    private boolean virtualMode = false;
    private int virtualAmbient = 15;
    private boolean ignoreWorldBlockLight = false;

    public StructureVirtualBlockRenderView(List<Entry> entries)
    {
        super(entries);

        for (Entry e : entries)
        {
            BlockState state = e.state;

            if (state != null)
            {
                int lum = state.getLuminance();

                if (lum > 0)
                {
                    this.emitters.add(e.pos);
                    this.emitterLevels.add(lum);
                }
            }
        }
    }

    public StructureVirtualBlockRenderView setVirtualMode(boolean enabled, int intensity)
    {
        this.virtualMode = enabled;
        this.virtualAmbient = Math.max(0, Math.min(15, intensity));

        return this;
    }

    public StructureVirtualBlockRenderView setIgnoreWorldBlockLight(boolean ignore)
    {
        this.ignoreWorldBlockLight = ignore;

        return this;
    }

    @Override
    public int getLightLevel(LightType type, BlockPos pos)
    {
        int base = super.getLightLevel(type, pos);

        if (type == LightType.BLOCK && this.ignoreWorldBlockLight)
        {
            base = 0;
        }

        if (!this.virtualMode || type != LightType.BLOCK || this.emitters.isEmpty())
        {
            return base;
        }

        int max = 0;

        for (int i = 0; i < this.emitters.size(); i++)
        {
            BlockPos sp = this.emitters.get(i);
            int L = this.emitterLevels.get(i);

            int dx = Math.abs(sp.getX() - pos.getX());
            int dy = Math.abs(sp.getY() - pos.getY());
            int dz = Math.abs(sp.getZ() - pos.getZ());
            int dist = dx + dy + dz;

            int contrib = Math.max(0, L - dist);

            if (contrib > max)
            {
                max = contrib;
            }
        }

        max = Math.min(max, this.virtualAmbient);

        return Math.max(base, max);
    }

    @Override
    public int getBaseLightLevel(BlockPos pos, int ambientDarkness)
    {
        int sky = this.getLightLevel(LightType.SKY, pos);

        if (!this.isForceMaxSkyLight() && MinecraftClient.getInstance().world != null)
        {
            sky = Math.max(0, sky - ambientDarkness);
        }

        int block = this.getLightLevel(LightType.BLOCK, pos);

        return Math.max(sky, block);
    }
}

