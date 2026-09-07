package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.mc.ValueBlockState;
import mchorse.bbs_mod.settings.values.misc.ValueStructureLightSettings;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

public class BlockForm extends Form
{
    public final ValueBlockState blockState = new ValueBlockState("block_state");
    public final ValueString blockEntityNbt = new ValueString("block_entity_nbt", "");
    public final ValueColor color = new ValueColor("color", new Color(1F, 1F, 1F, 1F));
    public final ValueString biomeId = new ValueString("biome_id", "");
    public final ValueInt breaking = new ValueInt("breaking", 0, 0, 10);
    public final ValueInt repeatX = new ValueInt("repeat_x", 1, 1, 64);
    public final ValueInt repeatY = new ValueInt("repeat_y", 1, 1, 64);
    public final ValueInt repeatZ = new ValueInt("repeat_z", 1, 1, 64);
    public final ValueBoolean repeatCenterX = new ValueBoolean("repeat_center_x", false);
    public final ValueBoolean repeatCenterY = new ValueBoolean("repeat_center_y", false);
    public final ValueBoolean repeatCenterZ = new ValueBoolean("repeat_center_z", false);
    public final ValueBoolean emitLight = new ValueBoolean("emit_light", false);
    public final ValueInt lightIntensity = new ValueInt("light_intensity", 15);
    public final ValueStructureLightSettings structureLight = new ValueStructureLightSettings("structure_light", new StructureLightSettings(false, 15));
    /** When true, adjacent repeated fluid cells cull shared faces like vanilla water. */
    public final ValueBoolean cullFluid = new ValueBoolean("cull_fluid", true);
    /** When true, outer side and bottom walls of the fluid volume are drawn. When false, only the top surface. */
    public final ValueBoolean outerFluidWalls = new ValueBoolean("outer_fluid_walls", true);
    /** When true, fluid faces pressed against solid world block faces are dropped (vanilla-like merging). */
    public final ValueBoolean interactBlocks = new ValueBoolean("interact_blocks", false);

    public static int repeatAxisStart(int count, boolean centered)
    {
        if (!centered || count <= 1)
        {
            return 0;
        }

        /* Even counts: -count/2 centers the volume on the origin (e.g. 2 → -1..0). */
        return -(count / 2);
    }

    public BlockForm()
    {
        this.add(this.blockState);
        this.add(this.blockEntityNbt);
        this.add(this.color);
        this.registerColorOverlays();
        this.add(this.biomeId);
        this.add(this.breaking);
        this.add(this.repeatX);
        this.add(this.repeatY);
        this.add(this.repeatZ);
        this.add(this.repeatCenterX);
        this.add(this.repeatCenterY);
        this.add(this.repeatCenterZ);
        this.add(this.cullFluid);
        this.add(this.outerFluidWalls);
        this.add(this.interactBlocks);
        this.add(this.emitLight);
        this.add(this.lightIntensity);
        this.add(this.structureLight);
        this.emitLight.invisible();
        this.lightIntensity.invisible();
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return Registries.BLOCK.getId(this.blockState.get().getBlock()).toString();
    }

    @Override
    public String getTrackName(String property)
    {
        int slash = property.lastIndexOf('/');
        String prefix = slash == -1 ? "" : property.substring(0, slash + 1);
        String last = slash == -1 ? property : property.substring(slash + 1);

        String mapped = last;

        if ("biome_id".equals(last))
        {
            mapped = "biome";
        }

        return super.getTrackName(prefix + mapped);
    }
}