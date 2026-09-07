package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.List;

/**
 * @deprecated use {@link ModelBlockSolidCollisions} — kept as a thin delegate for existing call sites.
 */
@Deprecated
public final class StructureModelBlockCollisions
{
    private StructureModelBlockCollisions()
    {}

    public static void updateRegistration(ModelBlockEntity entity)
    {
        ModelBlockSolidCollisions.updateRegistration(entity);
    }

    public static void unregister(ModelBlockEntity entity)
    {
        ModelBlockSolidCollisions.unregister(entity);
    }

    public static boolean hasSolidStructureHitbox(ModelBlockEntity entity)
    {
        return ModelBlockSolidCollisions.hasSolidFormHitbox(entity);
    }

    public static void appendShapes(Entity entity, Box swept, World world, List<VoxelShape> collisions)
    {
        ModelBlockSolidCollisions.appendShapes(entity, swept, world, collisions);
    }

    public static List<VoxelShape> wrapMutable(List<VoxelShape> collisions)
    {
        return ModelBlockSolidCollisions.wrapMutable(collisions);
    }

    public static Box sweptBox(Entity entity, Vec3d movement)
    {
        return ModelBlockSolidCollisions.sweptBox(entity, movement);
    }
}
