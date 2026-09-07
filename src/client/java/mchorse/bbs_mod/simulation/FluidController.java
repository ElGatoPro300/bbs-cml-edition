package mchorse.bbs_mod.simulation;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class FluidController
{
    /* Debug fields */
    public final List<FluidSample> lastDebugSamples = new ArrayList<>();
    public boolean debugEnabled = true;

    public void update(IEntity entity, FluidSimulation simulation, float scaleX, float scaleZ, float sensitivity, Matrix4f surfaceMatrixWorld, Iterable<BaseFilmController> controllers)
    {
        if (this.debugEnabled)
        {
            this.lastDebugSamples.clear();
        }
        if (entity == null || simulation == null)
        {
            return;
        }

        if (surfaceMatrixWorld == null)
        {
            return;
        }

        Matrix4f inverseSurface = new Matrix4f(surfaceMatrixWorld);

        if (Math.abs(inverseSurface.determinant()) < 1e-8f)
        {
            return;
        }

        inverseSurface.invert();

        Vector3f surfaceCenter = surfaceMatrixWorld.getTranslation(new Vector3f());
        Vector3f axisX = surfaceMatrixWorld.getColumn(0, new Vector3f());
        Vector3f axisZ = surfaceMatrixWorld.getColumn(2, new Vector3f());

        double worldHalfX = axisX.length() * (scaleX * 0.5);
        double worldHalfZ = axisZ.length() * (scaleZ * 0.5);
        double maxRadius = Math.max(worldHalfX, worldHalfZ) * 1.5;

        List<FluidSample> samples = new ArrayList<>();

        World world = entity.getWorld();

        if (world != null)
        {
            double minX = surfaceCenter.x - maxRadius;
            double maxX = surfaceCenter.x + maxRadius;
            double minZ = surfaceCenter.z - maxRadius;
            double maxZ = surfaceCenter.z + maxRadius;
            double minY = surfaceCenter.y - 2.0;
            double maxY = surfaceCenter.y + 2.0;

            Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
            Entity mcEntity = entity instanceof MCEntity ? ((MCEntity) entity).getMcEntity() : null;
            List<Entity> entities = world.getOtherEntities(mcEntity, box);

            for (Entity e : entities)
            {
                samples.add(new FluidSample(e.getPos(), 0.1));
            }
        }

        for (BaseFilmController controller : controllers)
        {
            for (IEntity replayEntity : controller.getEntities().values())
            {
                if (replayEntity == entity)
                {
                    continue;
                }

                Form form = replayEntity.getForm();

                if (form == null)
                {
                    continue;
                }

                var renderer = FormUtilsClient.getRenderer(form);

                if (renderer == null)
                {
                    continue;
                }

                MatrixCache map = renderer.collectMatrices(replayEntity, 0F);

                Matrix4f defaultMatrix = BaseFilmController.getMatrixForRenderWithRotation(replayEntity, 0, 0, 0, 0F);
                var totalMatrix = BaseFilmController.getTotalMatrix(controller.getEntities(), form.anchor.get(), defaultMatrix, 0, 0, 0, 0F, 0);
                Matrix4f entityMatrix = totalMatrix != null && totalMatrix.a != null ? totalMatrix.a : defaultMatrix;

                if (form instanceof ModelForm)
                {
                    this.updateModelInteraction(samples, (ModelForm) form, map, entityMatrix);
                }
                else
                {
                    for (var mapEntry : map.entrySet())
                    {
                        MatrixCacheEntry entry = mapEntry.getValue();

                        if (entry == null || entry.matrix() == null)
                        {
                            continue;
                        }

                        Matrix4f boneMatrix = new Matrix4f(entityMatrix).mul(entry.matrix());
                        Vector3f t = boneMatrix.getTranslation(new Vector3f());

                        samples.add(new FluidSample(new Vec3d(t.x, t.y, t.z), 0.1));
                    }
                }
            }
        }

        for (FluidSample sample : samples)
        {
            Vec3d p = sample.pos;
            Vector3f local = inverseSurface.transformPosition((float) p.x, (float) p.y, (float) p.z, new Vector3f());
            
            sample.localPos = local;

            if (Math.abs(local.y) > 1.5f)
            {
                continue;
            }

            float u = (local.x / scaleX) + 0.5f;
            float v = (local.z / scaleZ) + 0.5f;

            if (u >= 0 && u <= 1 && v >= 0 && v <= 1)
            {
                int gx = (int) (u * simulation.getWidth());
                int gz = (int) (v * simulation.getHeight());

                float force = sensitivity;

                simulation.addForce(gx, gz, force);
                
                if (this.debugEnabled)
                {
                    this.lastDebugSamples.add(sample);
                }
            }
        }
    }

    private void updateModelInteraction(List<FluidSample> samples, ModelForm form, MatrixCache map, Matrix4f entityMatrix)
    {
        ModelInstance modelInstance = ModelFormRenderer.getModel(form);

        if (modelInstance == null)
        {
            return;
        }

        /* Check for realistic interaction setting */
        if (!BBSSettings.fluidRealisticModelInteraction.get())
        {
            /* Entity-like interaction: Use just the root bone/transform */
            Matrix4f transform = new Matrix4f(entityMatrix);
            
            /* Try to find root bone if possible */
            IModel model = modelInstance.getModel();
            
            if (model instanceof Model)
            {
                /* For standard models, try to use the first group or root */
                Model standardModel = (Model) model;
                
                for (ModelGroup group : standardModel.getOrderedGroups())
                {
                    MatrixCacheEntry entry = map.get(group.id);
                    
                    if (entry != null && entry.matrix() != null)
                    {
                        /* Use the first valid bone matrix found as the "center" */
                        transform.mul(entry.matrix());
                        break;
                    }
                }
            }
            else if (model instanceof BOBJModel)
            {
                BOBJModel bobjModel = (BOBJModel) model;
                BOBJArmature armature = bobjModel.getArmature();
                
                if (armature != null && !armature.orderedBones.isEmpty())
                {
                     BOBJBone root = armature.orderedBones.get(0);
                     MatrixCacheEntry entry = map.get(root.name);
                     
                     if (entry != null && entry.matrix() != null)
                     {
                         transform.mul(entry.matrix());
                     }
                }
            }

            Vector3f pos = new Vector3f(0, 0, 0).mulPosition(transform);
            samples.add(new FluidSample(new Vec3d(pos.x, pos.y, pos.z), 0.5));
            
            return;
        }

        IModel model = modelInstance.getModel();

        if (model instanceof Model)
        {
            Model standardModel = (Model) model;

            for (ModelGroup group : standardModel.getOrderedGroups())
            {
                MatrixCacheEntry entry = map.get(group.id);

                if (entry == null || entry.matrix() == null)
                {
                    continue;
                }

                Matrix4f boneMatrix = new Matrix4f(entityMatrix).mul(entry.matrix());

                for (ModelCube cube : group.cubes)
                {
                    this.addCubeSamples(samples, cube, boneMatrix);
                }

                for (ModelMesh mesh : group.meshes)
                {
                    int vertexCount = mesh.baseData.vertices.size();
                    
                    for (int i = 0; i < vertexCount; i += 3)
                    {
                        if (i + 2 >= vertexCount) break;

                        Vector3f v1 = new Vector3f(mesh.baseData.vertices.get(i));
                        Vector3f v2 = new Vector3f(mesh.baseData.vertices.get(i + 1));
                        Vector3f v3 = new Vector3f(mesh.baseData.vertices.get(i + 2));

                        /* Calculate centroid */
                        Vector3f centroid = new Vector3f(v1).add(v2).add(v3).div(3);

                        /* Transform and add samples */
                        Vector3f[] points = new Vector3f[] {v1, v2, v3, centroid};

                        for (Vector3f p : points)
                        {
                            p.mulPosition(boneMatrix);
                            samples.add(new FluidSample(new Vec3d(p.x, p.y, p.z), 0.1));
                        }
                    }
                }
            }
        }
        else if (model instanceof BOBJModel)
        {
            BOBJModel bobjModel = (BOBJModel) model;
            BOBJArmature armature = bobjModel.getArmature();

            if (armature == null)
            {
                return;
            }

            for (BOBJLoader.CompiledData data : bobjModel.getMeshes())
            {
                if (data == null)
                {
                    continue;
                }

                int stride = 5; 
                int vertexCount = data.posData.length / 3;

                for (int i = 0; i < vertexCount; i += stride)
                {
                    float x = data.posData[i * 3];
                    float y = data.posData[i * 3 + 1];
                    float z = data.posData[i * 3 + 2];
                    Vector3f finalPos = new Vector3f();

                    /* Skinning */
                    for (int w = 0; w < 4; w++)
                    {
                        float weight = data.weightData[i * 4 + w];

                        if (weight > 0F)
                        {
                            int boneIndex = data.boneIndexData[i * 4 + w];
                            
                            if (boneIndex >= 0 && boneIndex < armature.orderedBones.size())
                            {
                                BOBJBone bone = armature.orderedBones.get(boneIndex);
                                MatrixCacheEntry entry = map.get(bone.name);

                                if (entry != null && entry.matrix() != null)
                                {
                                    Matrix4f boneMatrix = new Matrix4f(entityMatrix).mul(entry.matrix());
                                    Vector3f weightedPos = new Vector3f(x, y, z).mulPosition(boneMatrix).mul(weight);
                                    finalPos.add(weightedPos);
                                }
                            }
                        }
                    }
                    
                    if (finalPos.lengthSquared() > 0F)
                    {
                        samples.add(new FluidSample(new Vec3d(finalPos.x, finalPos.y, finalPos.z), 0.1D));
                    }
                }
            }
        }
    }

    private void addCubeSamples(List<FluidSample> samples, ModelCube cube, Matrix4f transform)
    {
        Vector3f min = new Vector3f(cube.origin);
        Vector3f max = new Vector3f(min).add(cube.size);
        Vector3f size = cube.size;

        /* 8 Corners */
        Vector3f[] corners = new Vector3f[] {
            new Vector3f(min.x, min.y, min.z),
            new Vector3f(max.x, min.y, min.z),
            new Vector3f(min.x, max.y, min.z),
            new Vector3f(max.x, max.y, min.z),
            new Vector3f(min.x, min.y, max.z),
            new Vector3f(max.x, min.y, max.z),
            new Vector3f(min.x, max.y, max.z),
            new Vector3f(max.x, max.y, max.z)
        };

        for (Vector3f corner : corners)
        {
            corner.mulPosition(transform);
            samples.add(new FluidSample(new Vec3d(corner.x, corner.y, corner.z), 0.1));
        }

        /* 6 Face Centers */
        Vector3f[] centers = new Vector3f[] {
            new Vector3f(min.x + size.x / 2, min.y + size.y / 2, min.z),          // Front
            new Vector3f(min.x + size.x / 2, min.y + size.y / 2, max.z),          // Back
            new Vector3f(min.x, min.y + size.y / 2, min.z + size.z / 2),          // Left
            new Vector3f(max.x, min.y + size.y / 2, min.z + size.z / 2),          // Right
            new Vector3f(min.x + size.x / 2, min.y, min.z + size.z / 2),          // Bottom
            new Vector3f(min.x + size.x / 2, max.y, min.z + size.z / 2)           // Top
        };

        for (Vector3f center : centers)
        {
            center.mulPosition(transform);
            samples.add(new FluidSample(new Vec3d(center.x, center.y, center.z), 0.1));
        }
    }

    public static class FluidSample
    {
        public final Vec3d pos;
        public final double radius;
        public Vector3f localPos;

        public FluidSample(Vec3d pos, double radius)
        {
            this.pos = pos;
            this.radius = radius;
        }
    }
}
