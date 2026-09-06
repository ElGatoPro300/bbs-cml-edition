package mchorse.bbs_mod.cubic.model.bobj;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.MolangHelper;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.BOBJGPUSkinVAO;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelSimpleVAO;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BOBJModel implements IModel
{
    private BOBJArmature armature;
    private List<BOBJLoader.CompiledData> meshes;

    /* One VAO per mesh; each mesh's name is its material for per-mesh texture selection. */
    private List<BOBJModelVAO> vaos = new ArrayList<>();
    private boolean simple;
    private Set<Integer> deformingBones;

    public BOBJModel(BOBJArmature armature, List<BOBJLoader.CompiledData> meshes, boolean simple)
    {
        this.armature = armature;
        this.meshes = meshes;
        this.simple = simple;
    }

    /**
     * Whether any mesh vertex is weighted to this bone. Bare reach-marker bones
     * return false so IK stretch ends on the last deforming bone.
     */
    public boolean boneDeformsMesh(int boneIndex)
    {
        if (this.deformingBones == null)
        {
            this.deformingBones = new HashSet<>();

            for (BOBJLoader.CompiledData mesh : this.meshes)
            {
                if (mesh != null && mesh.boneIndexData != null && mesh.weightData != null)
                {
                    int[] indices = mesh.boneIndexData;
                    float[] weights = mesh.weightData;

                    for (int i = 0; i < indices.length; i++)
                    {
                        if (indices[i] >= 0 && weights[i] > 0F)
                        {
                            this.deformingBones.add(indices[i]);
                        }
                    }
                }
            }
        }

        return this.deformingBones.contains(boneIndex);
    }

    public BOBJArmature getArmature()
    {
        return this.armature;
    }

    public List<BOBJModelVAO> getVaos()
    {
        return this.vaos;
    }

    public List<BOBJLoader.CompiledData> getMeshes()
    {
        return this.meshes;
    }

    public void delete()
    {
        for (BOBJModelVAO vao : this.vaos)
        {
            vao.delete();
        }

        this.vaos.clear();
    }

    public void setup()
    {
        this.delete();

        for (BOBJLoader.CompiledData mesh : this.meshes)
        {
            this.vaos.add(this.simple
                ? new BOBJModelSimpleVAO(mesh, this.armature)
                : new BOBJGPUSkinVAO(mesh, this.armature));
        }

        this.armature.setupMatrices();
    }

    @Override
    public Pose createPose()
    {
        Pose pose = new Pose();

        for (String key : this.getAllGroupKeys())
        {
            PoseTransform poseTransform = pose.get(key);
            BOBJBone group = this.armature.bones.get(key);

            poseTransform.copy(group.transform);
        }

        return pose;
    }

    @Override
    public void resetPose()
    {
        for (BOBJBone orderedBone : this.armature.orderedBones)
        {
            orderedBone.reset();
        }
    }

    @Override
    public void applyPose(Pose pose)
    {
        if (pose.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform transform = entry.getValue();
            BOBJBone bone = this.armature.bones.get(entry.getKey());

            if (bone == null)
            {
                continue;
            }

            if (transform.fix > 0F)
            {
                bone.transform.lerp(Transform.DEFAULT, transform.fix);
            }

            bone.lighting = transform.lighting;
            bone.noshadingOpacity = transform.noshadingOpacity;
            bone.color.copy(transform.color);
            bone.paintColor.copy(transform.paintColor);
            bone.glowingColor.copy(transform.glowingColor);
            bone.glowIntensity = transform.glowIntensity;
            bone.glowRadius = transform.glowRadius;
            bone.shaderShadow = PaintSettings.resolveAutoShaderShadowForPoseAlpha(transform.paintColor.a);
            bone.texture = transform.texture;
            bone.textureBlend = transform.textureBlend;
            bone.transform.translate.add(transform.translate);
            bone.transform.scale.add(transform.scale).sub(1, 1, 1);
            bone.transform.rotate.add(transform.rotate);
            bone.transform.rotate2.add(transform.rotate2);
        }
    }

    @Override
    public IModel copy()
    {
        BOBJModel model = new BOBJModel(this.armature.copy(), this.meshes, this.simple);
        
        model.setup();
        
        return model;
    }

    @Override
    public Set<String> getShapeKeys()
    {
        return Set.of();
    }

    @Override
    public String getAnchor()
    {
        for (BOBJBone orderedBone : this.armature.orderedBones)
        {
            if (orderedBone.parentBone != null)
            {
                return orderedBone.name;
            }
        }

        return "";
    }

    @Override
    public Collection<String> getAllGroupKeys()
    {
        return this.armature.bones.keySet();
    }

    @Override
    public Collection<String> getAllChildrenKeys(String key)
    {
        BOBJBone group = this.armature.bones.get(key);
        List<String> groups = new ArrayList<>();

        this.collectChildrenKeys(group, groups);

        return groups;
    }

    private void collectChildrenKeys(BOBJBone group, List<String> groups)
    {
        for (BOBJBone bone : this.armature.orderedBones)
        {
            if (bone.parentBone == group)
            {
                groups.add(bone.name);
                this.collectChildrenKeys(bone, groups);
            }
        }
    }

    @Override
    public Collection<ModelGroup> getAllGroups()
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<BOBJBone> getAllBOBJBones()
    {
        return Collections.unmodifiableList(this.armature.orderedBones);
    }

    @Override
    public Collection<String> getAdjacentGroups(String groupName)
    {
        List<String> groups = new ArrayList<>();

        for (BOBJBone orderedBone : this.armature.orderedBones)
        {
            if (orderedBone.parent.equals(groupName))
            {
                groups.add(orderedBone.name);
            }
        }

        return groups;
    }

    @Override
    public Collection<String> getHierarchyGroups(String groupName)
    {
        BOBJBone group = this.armature.bones.get(groupName);
        List<String> groups = new ArrayList<>();

        while (group != null)
        {
            groups.add(group.name);

            group = group.parentBone;
        }

        return groups;
    }

    @Override
    public String getParentGroupKey(String key)
    {
        BOBJBone bone = this.armature.bones.get(key);

        if (bone == null || bone.parentBone == null)
        {
            return null;
        }

        return bone.parentBone.name;
    }

    @Override
    public Collection<String> getRootGroupKeys()
    {
        List<String> roots = new ArrayList<>();

        for (BOBJBone bone : this.armature.orderedBones)
        {
            if (bone.parentBone == null)
            {
                roots.add(bone.name);
            }
        }

        return roots;
    }

    @Override
    public Collection<String> getDirectChildrenKeys(String key)
    {
        BOBJBone parent = this.armature.bones.get(key);

        if (parent == null)
        {
            return Collections.emptyList();
        }

        List<String> children = new ArrayList<>();

        for (BOBJBone bone : this.armature.orderedBones)
        {
            if (bone.parentBone == parent)
            {
                children.add(bone.name);
            }
        }

        return children;
    }

    @Override
    public void apply(IEntity target, Animation action, float tick, float blend, float transition, boolean skipInitial)
    {
        MolangHelper.setMolangVariables(action.parser, target, tick, transition);
        BOBJModelAnimator.animate(this, action, tick, blend, skipInitial);
    }

    @Override
    public void postApply(IEntity target, Animation action, float tick, float transition)
    {
        MolangHelper.setMolangVariables(action.parser, target, tick, transition);
        BOBJModelAnimator.postAnimate(this, action, tick);
    }
}
