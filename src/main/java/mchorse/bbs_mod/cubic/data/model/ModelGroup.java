package mchorse.bbs_mod.cubic.data.model;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.QuaternionMath;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ModelGroup implements IMapSerializable
{
    public final String id;
    public Model owner;
    public ModelGroup parent;
    public List<ModelGroup> children = new ArrayList<>();
    public List<ModelCube> cubes = new ArrayList<>();
    public List<ModelMesh> meshes = new ArrayList<>();
    public boolean visible = true;
    public boolean ikLocator = false; // If true, this group acts as an IK target locator
    public int index = -1;

    public float lighting = 0F;
    public boolean noshadingOpacity;
    public Color color = new Color().set(1F, 1F, 1F);
    public Color paintColor = new Color().set(1F, 1F, 1F, 0F);
    public Color glowingColor = new Color().set(1F, 1F, 1F, 1F);
    public float glowIntensity;
    public float glowRadius;
    public float shaderShadow = PaintSettings.SHADER_SHADOW_DEFAULT;
    public Link textureOverride;
    public Link textureBlendTo;
    public float textureBlend = 1F;
    public Transform initial = new Transform();
    public Transform current = new Transform();

    /* Transient full local orientation for this bone, applied raw in the render
     * matrix in place of the euler rotate triple. Null when unused this frame. */
    public Quaternionf orient;

    /* Transient parent-frame translation for IK stretch telescoping. Null when unused. */
    public Vector3f offset;

    public ModelGroup(String id)
    {
        this.id = id;
    }

    public void reset()
    {
        this.lighting = 0F;
        this.noshadingOpacity = false;
        this.color.set(1F, 1F, 1F);
        this.color.brightness = 0F;
        this.color.contrast = 0F;
        this.color.hue = 0F;
        this.color.saturation = 0F;
        this.color.transform = new EffectTransform();
        this.color.brightnessTransform = new EffectTransform();
        this.color.contrastTransform = new EffectTransform();
        this.color.hueTransform = new EffectTransform();
        this.color.saturationTransform = new EffectTransform();
        this.paintColor.set(1F, 1F, 1F, 0F);
        this.paintColor.transform = new EffectTransform();
        this.glowingColor.set(1F, 1F, 1F, 1F);
        this.glowingColor.transform = new EffectTransform();
        this.glowIntensity = 0F;
        this.glowRadius = 0F;
        this.shaderShadow = PaintSettings.SHADER_SHADOW_DEFAULT;
        this.textureOverride = null;
        this.textureBlendTo = null;
        this.textureBlend = 1F;
        this.current.copy(this.initial);
        this.orient = null;
        this.offset = null;
    }

    /**
     * The bone's evaluated local rotation as of this point in the pipeline — {@link #orient}
     * when a layer or constraint stage has composed one, otherwise the rotation the renderer
     * would reconstruct from the euler channels ({@code rotate} folded with {@code rotate2};
     * cubic channels are degrees). THE read for every constraint-stack stage: blend bases,
     * twist references and clamp inputs all start from this, so stages stack instead of
     * overwriting each other. Returns a fresh instance safe to mutate.
     */
    public Quaternionf evaluatedRotation()
    {
        if (this.orient != null)
        {
            return new Quaternionf(this.orient);
        }

        Quaternionf rotation = QuaternionMath.composeFromEulerZYX(this.current.rotate.x, this.current.rotate.y, this.current.rotate.z);

        if (this.current.rotate2.x != 0F || this.current.rotate2.y != 0F || this.current.rotate2.z != 0F)
        {
            rotation.mul(QuaternionMath.composeFromEulerZYX(this.current.rotate2.x, this.current.rotate2.y, this.current.rotate2.z));
        }

        return rotation;
    }

    /**
     * Composes one rotation layer into {@link #orient}. The first layer seeds from
     * the accumulated euler; later layers multiply their delta as a quaternion.
     */
    public void composeOrient(Quaternionf delta)
    {
        if (this.orient == null)
        {
            this.orient = this.evaluatedRotation();
        }
        else
        {
            this.orient.mul(delta);
        }
    }

    public ModelGroup copy(Model newOwner, ModelGroup newParent)
    {
        ModelGroup group = new ModelGroup(this.id);
        
        group.owner = newOwner;
        group.parent = newParent;
        group.visible = this.visible;
        group.ikLocator = this.ikLocator;
        group.index = this.index;
        
        group.lighting = this.lighting;
        group.noshadingOpacity = this.noshadingOpacity;
        group.color.copy(this.color);
        group.paintColor.copy(this.paintColor);
        group.glowingColor.copy(this.glowingColor);
        group.glowIntensity = this.glowIntensity;
        group.glowRadius = this.glowRadius;
        group.shaderShadow = this.shaderShadow;
        if (this.textureOverride != null) group.textureOverride = LinkUtils.copy(this.textureOverride);
        if (this.textureBlendTo != null) group.textureBlendTo = LinkUtils.copy(this.textureBlendTo);
        group.textureBlend = this.textureBlend;
        
        group.initial.copy(this.initial);
        group.current.copy(this.current);
        
        // Copy meshes and cubes (assuming they are immutable or can be shared?)
        // Meshes have data (Map) and texture (Link). 
        // Cubes have transforms.
        // If we modify meshes/cubes at runtime, we must clone them too.
        // Usually only transforms change.
        
        // Deep copy cubes just in case
        for (ModelCube cube : this.cubes)
        {
            group.cubes.add(cube.copy());
        }
        
        // Deep copy meshes just in case
        for (ModelMesh mesh : this.meshes)
        {
            group.meshes.add(mesh.copy());
        }
        
        for (ModelGroup child : this.children)
        {
            group.children.add(child.copy(newOwner, group));
        }
        
        return group;
    }

    @Override
    public void fromData(MapType data)
    {
        /* Setup initial transformations */
        if (data.has("origin")) this.initial.translate.set(DataStorageUtils.vector3fFromData(data.getList("origin")));
        if (data.has("rotate")) this.initial.rotate.set(DataStorageUtils.vector3fFromData(data.getList("rotate")));
        if (data.has("pivot")) this.initial.pivot.set(DataStorageUtils.vector3fFromData(data.getList("pivot")));
        else this.initial.pivot.set(this.initial.translate);
        if (data.has("ik_locator")) this.ikLocator = data.getBool("ik_locator");

        /* Setup cubes and meshes */
        if (data.has("cubes"))
        {
            for (BaseType element : data.getList("cubes"))
            {
                ModelCube cube = new ModelCube();

                cube.fromData((MapType) element);

                this.cubes.add(cube);
            }

        }

        if (data.has("meshes"))
        {
            for (BaseType element : data.getList("meshes"))
            {
                ModelMesh mesh = new ModelMesh();

                mesh.fromData((MapType) element);

                this.meshes.add(mesh);
            }
        }
    }

    @Override
    public void toData(MapType data)
    {
        data.put("origin", DataStorageUtils.vector3fToData(this.initial.translate));
        data.put("rotate", DataStorageUtils.vector3fToData(this.initial.rotate));
        data.put("pivot", DataStorageUtils.vector3fToData(this.initial.pivot));
        if (this.ikLocator) data.putBool("ik_locator", this.ikLocator);

        if (!this.cubes.isEmpty())
        {
            ListType list = new ListType();

            for (ModelCube cube : this.cubes)
            {
                list.add(cube.toData());
            }

            data.put("cubes", list);
        }

        if (!this.meshes.isEmpty())
        {
            ListType list = new ListType();

            for (ModelMesh mesh : this.meshes)
            {
                list.add(mesh.toData());
            }

            data.put("meshes", list);
        }
    }
}