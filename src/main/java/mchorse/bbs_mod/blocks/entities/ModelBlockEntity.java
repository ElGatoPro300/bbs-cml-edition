package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.ModelBlock;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.resources.Link;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

public class ModelBlockEntity extends BlockEntity
{
    private ModelProperties properties = new ModelProperties();
    private IEntity entity = new StubEntity();

    private float lastYaw = Float.NaN;
    private float currentYaw = Float.NaN;
    private int lastLightLevel = -1;

    public ModelBlockEntity(BlockPos pos, BlockState state)
    {
        super(BBSMod.MODEL_BLOCK_ENTITY, pos, state);
    }

    public String getName()
    {
        BlockPos pos = this.getPos();
        Form form = this.getProperties().getForm();
        String s = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
        String customName = this.getProperties().getName();

        if (!customName.isEmpty())
        {
            return s + " " + customName;
        }

        if (form != null)
        {
            s += " " + form.getDisplayName();
        }

        return s;
    }

    public ModelProperties getProperties()
    {
        return this.properties;
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public void setLookYaw(float yaw)
    {
        this.lastYaw = yaw;
        this.currentYaw = yaw;
    }

    public float updateLookYawContinuous(float yaw)
    {
        if (Float.isNaN(this.currentYaw))
        {
            this.setLookYaw(yaw);

            return this.currentYaw;
        }

        float diff = yaw - this.lastYaw;

        while (diff > Math.PI) diff -= (float) (Math.PI * 2);
        while (diff < -Math.PI) diff += (float) (Math.PI * 2);

        this.currentYaw += diff;
        this.lastYaw = yaw;

        return this.currentYaw;
    }

    public void resetLookYaw()
    {
        this.lastYaw = this.currentYaw = Float.NaN;
    }

    public void snapLookYawToBase(float lastYaw, float currentYaw)
    {
        this.lastYaw = lastYaw;
        this.currentYaw = currentYaw;
    }

    public static void tick(World world, BlockPos pos, BlockState state, ModelBlockEntity blockEntity)
    {
        ModelBlockEntityUpdateCallback.EVENT.invoker().update(blockEntity);
        /* Asegura que el StubEntity tenga posición y mundo correctos para cálculos de luz/bioma.
         * Sin esto, el entity se queda en (0,0,0) y los renders toman luz de esa zona,
         * provocando oscurecimiento en editor, miniatura y bloque de modelo. */
        blockEntity.entity.setWorld(world);

        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;

        blockEntity.entity.setPosition(x, y, z);

        /* Initialize previous position/yaw on the very first tick to avoid
         * a huge movement delta (spike) when the block is placed. */
        try
        {
            if (blockEntity.entity.getAge() == 0)
            {
                blockEntity.entity.setPrevX(x);
                blockEntity.entity.setPrevY(y);
                blockEntity.entity.setPrevZ(z);

                blockEntity.entity.setPrevYaw(blockEntity.entity.getYaw());
                blockEntity.entity.setPrevHeadYaw(blockEntity.entity.getHeadYaw());
                blockEntity.entity.setPrevPitch(blockEntity.entity.getPitch());
                blockEntity.entity.setPrevBodyYaw(blockEntity.entity.getBodyYaw());
                blockEntity.entity.setPrevPrevBodyYaw(blockEntity.entity.getPrevBodyYaw());

                float[] extra = blockEntity.entity.getExtraVariables();
                float[] prevExtra = blockEntity.entity.getPrevExtraVariables();

                if (extra != null && prevExtra != null)
                {
                    for (int i = 0; i < Math.min(extra.length, prevExtra.length); i++)
                    {
                        prevExtra[i] = extra[i];
                    }
                }
            }
        }
        catch (Exception e) {}

        blockEntity.entity.update();
        blockEntity.properties.update(blockEntity.entity);
        if (!world.isClient)
        {
            int target = blockEntity.properties.getLightLevel();
            Form form = blockEntity.properties.getForm();

            if (form instanceof LightForm lightForm && lightForm.enabled.get())
            {
                int level = lightForm.level.get();

                if (level < 0)
                {
                    level = 0;
                }
                else if (level > 15)
                {
                    level = 15;
                }

                target = level;
            }
            else if (form instanceof BlockForm blockForm)
            {
                StructureLightSettings sl = blockForm.structureLight.get();
                boolean enabled = (sl != null) ? sl.enabled : blockForm.emitLight.get();
                int intensity = (sl != null) ? sl.intensity : blockForm.lightIntensity.get();
                BlockState formState = blockForm.blockState.get();

                if (enabled && formState != null && formState.getLuminance() > 0)
                {
                    target = Math.max(0, Math.min(15, Math.min(formState.getLuminance(), intensity)));
                }
            }

            if (target != blockEntity.lastLightLevel)
            {
                blockEntity.lastLightLevel = target;
                blockEntity.properties.setLightLevel(target);

                try
                {
                    world.setBlockState(pos, state.with(ModelBlock.LIGHT_LEVEL, target), Block.NOTIFY_LISTENERS);
                }
                catch (Exception e) {}
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket()
    {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt()
    {
        return this.createNbtWithId();
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

        MapType data = this.properties.toData();

        DataStorageUtils.writeToNbtCompound(nbt, "Properties", data);
    }

    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        BaseType baseType = DataStorageUtils.readFromNbtCompound(nbt, "Properties");

        if (baseType instanceof MapType mapType)
        {
            this.properties.fromData(mapType);
        }
        /* Ensure block state reflects stored light level when chunk/block is loaded */
        if (this.world != null && !this.world.isClient)
        {
            try
            {
                int level = this.properties.getLightLevel();
                BlockPos pos = this.getPos();
                BlockState state = this.world.getBlockState(pos);

                if (state.getBlock() instanceof Block)
                {
                    this.world.setBlockState(pos, state.with(ModelBlock.LIGHT_LEVEL, level), Block.NOTIFY_LISTENERS);
                }
            }
            catch (Exception e) {}
        }
    }

    public void updateForm(MapType data, World world)
    {
        WrapperLookup registries = world != null ? world.getRegistryManager() : null;

        WrapperLookup prev = BBSMod.getRegistryManager();
        if (registries != null && prev != registries)
        {
            BBSMod.setRegistryManager(registries);
        }

        try
        {
            this.properties.fromData(data, registries);
        }
        finally
        {
            if (registries != null && prev != registries)
            {
                BBSMod.setRegistryManager(prev);
            }
        }

        BlockPos pos = this.getPos();
        BlockState blockState = world.getBlockState(pos);
        int level = this.properties.getLightLevel();
        BlockState newState = blockState.with(ModelBlock.LIGHT_LEVEL, level);

        this.markDirty();
        world.markDirty(pos);

        if (blockState != newState)
        {
            world.setBlockState(pos, newState, Block.NOTIFY_LISTENERS);
        }
        else
        {
            world.updateListeners(pos, blockState, newState, Block.NOTIFY_LISTENERS);
        }
    }
}
