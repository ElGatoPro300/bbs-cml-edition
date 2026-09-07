package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.forms.shape.ValueShapeGraph;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueEnum;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

import org.joml.Vector2f;

public class ShapeForm extends Form
{
    public enum ShapeType
    {
        SPHERE, BOX, CYLINDER, CAPSULE
    }

    public enum ParticleType
    {
        TEXTURE, SPHERE, BLOCK, DUST
    }

    /* Geometry */
    public final ValueEnum<ShapeType> type = new ValueEnum<>("type", ShapeType.class, ShapeType.SPHERE);
    public final ValueFloat sizeX = new ValueFloat("sizeX", 1F);
    public final ValueFloat sizeY = new ValueFloat("sizeY", 1F);
    public final ValueFloat sizeZ = new ValueFloat("sizeZ", 1F);
    public final ValueInt subdivisions = new ValueInt("subdivisions", 16, 4, 64);

    /* Appearance */
    public final ValueColor color = new ValueColor("color", new Color(1F, 1F, 1F, 1F));
    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueFloat textureScale = new ValueFloat("textureScale", 1F);
    public final ValueFloat textureScrollX = new ValueFloat("textureScrollX", 0F);
    public final ValueFloat textureScrollY = new ValueFloat("textureScrollY", 0F);
    public final ValueBoolean lighting = new ValueBoolean("lighting", false);
    
    /* Particles */
    public final ValueBoolean particles = new ValueBoolean("particles", false);
    public final ValueEnum<ParticleType> particleType = new ValueEnum<>("particleType", ParticleType.class, ParticleType.TEXTURE);
    public final ValueFloat particleScale = new ValueFloat("particleScale", 10F);
    public final ValueFloat particleDensity = new ValueFloat("particleDensity", 0.5F);
    public final ValueFloat particleSize = new ValueFloat("particleSize", 0.2F);
    
    /* Graph */
    public final ValueShapeGraph graph = new ValueShapeGraph("graph");

    public ShapeForm()
    {
        super();

        this.add(this.type);
        this.add(this.sizeX);
        this.add(this.sizeY);
        this.add(this.sizeZ);
        this.add(this.subdivisions);
        
        this.add(this.color);
        this.add(this.texture);
        this.add(this.textureScale);
        this.add(this.textureScrollX);
        this.add(this.textureScrollY);
        this.add(this.lighting);
        
        this.add(this.particles);
        this.add(this.particleType);
        this.add(this.particleScale);
        this.add(this.particleDensity);
        this.add(this.particleSize);
        
        this.add(this.graph);
    }
}
