package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.misc.ValueStructureLightSettings;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

import org.joml.Vector4f;

/**
 * StructureForm
 *
 * Un formulario ligero para renderizar estructuras guardadas en archivos NBT.
 * Minimiza archivos al encapsular únicamente las propiedades necesarias.
 */
public class StructureForm extends Form
{
    /** Ruta relativa dentro de assets al archivo de estructura (.nbt), por ejemplo: "structures/casa.nbt" */
    public final ValueString structureFile = new ValueString("structure_file", "");
    /** Color de tinte aplicado al renderizado (multiplicado) */
    public final ValueColor color = new ValueColor("color", new Color(1F, 1F, 1F, 1F));
    /** Bioma seleccionado para coloreo (override). Vacío para usar el del mundo */
    public final ValueString biomeId = new ValueString("biome_id", "");
    /** Alterna si los bloques de iluminación de la estructura emiten luz */
    public final ValueBoolean emitLight = new ValueBoolean("emit_light", false);
    /** Intensidad de luz emitida por los bloques de la estructura (1-15) */
    public final ValueInt lightIntensity = new ValueInt("light_intensity", 15);
    /** Pista unificada de luz de estructura (enabled + intensity) */
    public final ValueStructureLightSettings structureLight = new ValueStructureLightSettings("structure_light", new StructureLightSettings(false, 15));
    /** Aplica el tinte global también a Block Entities (cofres, carteles, etc.) */
    public final ValueBoolean tintBlockEntities = new ValueBoolean("tint_block_entities", false);
    /** Alterna el renderizado de fluidos (agua/lava) en la estructura */
    public final ValueBoolean renderFluid = new ValueBoolean("render_fluid", false);
    /** Pivote manual en coordenadas de bloque (permite decimales) */
    public final ValueFloat pivotX = new ValueFloat("pivot_x", 0f);
    public final ValueFloat pivotY = new ValueFloat("pivot_y", 0f);
    public final ValueFloat pivotZ = new ValueFloat("pivot_z", 0f);
    /** Structure size/scale multipliers (1 = original size) */
    public final ValueFloat scaleX = new ValueFloat("scale_x", 1f);
    public final ValueFloat scaleY = new ValueFloat("scale_y", 1f);
    public final ValueFloat scaleZ = new ValueFloat("scale_z", 1f);
    /**
     * When enabled on a Model Block, structure blocks get real voxel collision so players
     * can walk and stand on the structure as if it were placed in the world.
     */
    public final ValueBoolean solidHitbox = new ValueBoolean("solid_hitbox", false);

    public StructureForm()
    {
        super();

        this.add(this.structureFile);
        this.add(this.color);
        this.registerColorOverlays();
        this.add(this.biomeId);
        this.add(this.emitLight);
        this.add(this.lightIntensity);
        /* Ocultar del timeline el tinte de Block Entities */
        this.tintBlockEntities.invisible();
        this.add(this.tintBlockEntities);
        this.add(this.structureLight);
        /* Ocultar pistas escalares del timeline; se mantienen para UI manual */
        this.pivotX.invisible();
        this.pivotY.invisible();
        this.pivotZ.invisible();

        this.add(this.pivotX);
        this.add(this.pivotY);
        this.add(this.pivotZ);

        this.scaleX.invisible();
        this.scaleY.invisible();
        this.scaleZ.invisible();
        this.add(this.scaleX);
        this.add(this.scaleY);
        this.add(this.scaleZ);
        /* Ocultar del timeline */
        this.renderFluid.invisible();
        this.add(this.renderFluid);
        this.solidHitbox.invisible();
        this.add(this.solidHitbox);

        /* Nueva pista unificada de keyframes y ocultar pista booleana suelta */
        this.emitLight.invisible();
        this.lightIntensity.invisible();
    }

    @Override
    protected String getDefaultDisplayName()
    {
        String path = this.structureFile.get();

        if (path == null || path.isEmpty())
        {
            return super.getDefaultDisplayName();
        }

        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String prefix = slash >= 0 ? path.substring(0, slash + 1) : "";
        String name = slash >= 0 ? path.substring(slash + 1) : path;

        String base = name.toLowerCase().endsWith(".nbt") ? name.substring(0, name.length() - 4) : name;

        return prefix + base;
    }

    @Override
    public String getTrackName(String property)
    {
        int slash = property.lastIndexOf('/');
        String prefix = slash == -1 ? "" : property.substring(0, slash + 1);
        String last = slash == -1 ? property : property.substring(slash + 1);

        String mapped = last;
        if ("structure_file".equals(last)) mapped = "structure";
        else if ("biome_id".equals(last)) mapped = "biome";
        /* Mostrar el nombre visual como 'structure_light' en lugar de 'light' */
        else if ("structure_light".equals(last)) mapped = "structure_light";

        return super.getTrackName(prefix + mapped);
    }
}