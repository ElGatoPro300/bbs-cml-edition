package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.QuaternionMath;
import mchorse.bbs_mod.utils.pose.Transform;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BOBJBone
{
    /* Meta information */
    public int index;
    public String name;
    public String parent;
    public BOBJBone parentBone;

    /* Transformations */
    public final Transform transform = new Transform();

    public float lighting;
    public boolean noshadingOpacity;
    public final Color color = new Color(1, 1, 1, 1);
    public final Color paintColor = new Color().set(1F, 1F, 1F, 0F);
    public final Color glowingColor = new Color().set(1F, 1F, 1F, 1F);
    public float glowIntensity;
    public float glowRadius;
    public float shaderShadow = PaintSettings.SHADER_SHADOW_DEFAULT;
    public Link texture;
    public float textureBlend = 1F;

    /**
     * Computed bone matrix which is used for transformations. This 
     * matrix isn't multiplied by inverse bone matrix. 
     */
    public Matrix4f mat = new Matrix4f();

    public Matrix4f originMat = new Matrix4f();

    /**
     * Bone matrix 
     */
    public Matrix4f boneMat;

    /**
     * Inverse bone matrix 
     */
    public Matrix4f invBoneMat = new Matrix4f();

    /**
     * Relative-to-parent bone matrix
     */
    public Matrix4f relBoneMat = new Matrix4f();

    /**
     * Transient full local orientation from IK, applied in place of euler rotate.
     * Null when not IK-driven this frame.
     */
    public Quaternionf orient;

    /**
     * Transient cumulative world translation for IK stretch on the skinning matrix.
     * Null when unused this frame.
     */
    public Vector3f offset;

    public BOBJBone(int index, String name, String parent, Matrix4f boneMat)
    {
        this.index = index;
        this.name = name;
        this.parent = parent;
        this.boneMat = boneMat;

        this.invBoneMat.set(boneMat);
        this.invBoneMat.invert();

        this.relBoneMat.identity();
    }

    public Matrix4f compute()
    {
        Matrix4f mat = this.computeMatrix(new Matrix4f());

        this.mat.set(mat);
        mat.mul(this.invBoneMat);

        /* Stretch shifts only the skinning matrix — skeleton frames stay nominal. */
        if (this.offset != null)
        {
            mat.translateLocal(this.offset);
        }

        return mat;
    }

    public Matrix4f computeMatrix(Matrix4f m)
    {
        this.mat.set(this.relBoneMat);
        this.originMat.set(this.relBoneMat);
        this.applyTransformations();

        if (this.parentBone != null)
        {
            m.set(this.parentBone.mat).mul(this.originMat);
            this.originMat.set(m);
            m.identity().set(this.parentBone.mat);
        }

        m.mul(this.mat);

        return m;
    }

    public void applyTransformations()
    {
        this.mat.translate(this.transform.translate);
        this.originMat.translate(this.transform.translate);

        if (this.orient != null)
        {
            /* orient already folds rotate2, so the euler triples are skipped. */
            this.mat.rotate(this.orient);
        }
        else
        {
            if (this.transform.rotate.z != 0F) this.mat.rotateZ(this.transform.rotate.z);
            if (this.transform.rotate.y != 0F) this.mat.rotateY(this.transform.rotate.y);
            if (this.transform.rotate.x != 0F) this.mat.rotateX(this.transform.rotate.x);

            if (this.transform.rotate2.z != 0F) this.mat.rotateZ(this.transform.rotate2.z);
            if (this.transform.rotate2.y != 0F) this.mat.rotateY(this.transform.rotate2.y);
            if (this.transform.rotate2.x != 0F) this.mat.rotateX(this.transform.rotate2.x);
        }

        this.mat.scale(this.transform.scale);
    }

    /**
     * The bone's evaluated local rotation as of this point in the pipeline — {@link #orient}
     * when set, otherwise the euler channels ({@code rotate} folded with {@code rotate2};
     * BOBJ channels are radians). THE read for every constraint-stack stage; see
     * {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#evaluatedRotation()}. Returns a
     * fresh instance safe to mutate.
     */
    public Quaternionf evaluatedRotation()
    {
        if (this.orient != null)
        {
            return new Quaternionf(this.orient);
        }

        Quaternionf rotation = QuaternionMath.composeFromEulerZYXRadians(this.transform.rotate.x, this.transform.rotate.y, this.transform.rotate.z);

        if (this.transform.rotate2.x != 0F || this.transform.rotate2.y != 0F || this.transform.rotate2.z != 0F)
        {
            rotation.mul(QuaternionMath.composeFromEulerZYXRadians(this.transform.rotate2.x, this.transform.rotate2.y, this.transform.rotate2.z));
        }

        return rotation;
    }

    /**
     * Composes one rotation layer into {@link #orient} (BOBJ rotations are radians).
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

    public BOBJBone copy()
    {
        BOBJBone bone = new BOBJBone(this.index, this.name, this.parent, new Matrix4f(this.boneMat));

        bone.transform.copy(this.transform);
        bone.lighting = this.lighting;
        bone.noshadingOpacity = this.noshadingOpacity;
        bone.color.copy(this.color);
        bone.paintColor.copy(this.paintColor);
        bone.glowingColor.copy(this.glowingColor);
        bone.glowIntensity = this.glowIntensity;
        bone.glowRadius = this.glowRadius;
        bone.shaderShadow = this.shaderShadow;
        bone.texture = this.texture;
        bone.textureBlend = this.textureBlend;
        bone.mat.set(this.mat);
        bone.originMat.set(this.originMat);
        bone.invBoneMat.set(this.invBoneMat);
        bone.relBoneMat.set(this.relBoneMat);

        return bone;
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
        this.texture = null;
        this.textureBlend = 1F;
        this.transform.identity();
        this.orient = null;
        this.offset = null;
    }
}
