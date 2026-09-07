package mchorse.bbs_mod.film;

import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared helpers for recording world block changes into replay action clips.
 */
public final class RecorderWorldEffectCapture
{
    private static final int SNOW_TRAIL_RADIUS = 1;
    private static final List<String> SUMMON_NBT_STRIP_KEYS = Arrays.asList("Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown", "UUID", "Dimension", "World");

    private RecorderWorldEffectCapture()
    {}

    public static void captureSnowTrail(Replay replay, Map<Long, BlockState> snapshots, LivingEntity entity, int tick, ClientWorld world)
    {
        BlockPos footing = entity.getBlockPos();

        for (int dx = -SNOW_TRAIL_RADIUS; dx <= SNOW_TRAIL_RADIUS; dx++)
        {
            for (int dz = -SNOW_TRAIL_RADIUS; dz <= SNOW_TRAIL_RADIUS; dz++)
            {
                BlockPos offset = footing.add(dx, 0, dz);

                RecorderWorldEffectCapture.checkSnowLayer(replay, snapshots, tick, world, offset);
                RecorderWorldEffectCapture.checkSnowLayer(replay, snapshots, tick, world, offset.down());
            }
        }
    }

    private static void checkSnowLayer(Replay replay, Map<Long, BlockState> snapshots, int tick, ClientWorld world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        if (!state.isOf(Blocks.SNOW))
        {
            return;
        }

        long key = pos.asLong();
        BlockState previous = snapshots.get(key);
        int layers = state.get(SnowBlock.LAYERS);

        if (layers <= 0)
        {
            return;
        }

        if (previous != null && previous.isOf(Blocks.SNOW) && previous.get(SnowBlock.LAYERS) >= layers)
        {
            return;
        }

        snapshots.put(key, state);
        RecorderWorldEffectCapture.addSetblockCommand(replay, tick, pos, state);
    }

    public static void addSetblockCommand(Replay replay, int tick, BlockPos pos, BlockState state)
    {
        BaseValue.edit(replay.actions, (actions) ->
        {
            CommandActionClip commandClip = new CommandActionClip();

            commandClip.tick.set(tick);
            commandClip.duration.set(1);
            commandClip.command.set("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + RecorderWorldEffectCapture.formatSetblockState(state));
            replay.actions.addClip(commandClip);
        });
    }

    public static void addSummonCommand(Replay replay, int tick, Entity entity)
    {
        Identifier typeId = Registries.ENTITY_TYPE.getId(entity.getType());
        NbtCompound nbt = new NbtCompound();

        entity.writeNbt(nbt);

        for (String key : SUMMON_NBT_STRIP_KEYS)
        {
            nbt.remove(key);
        }

        Vec3d velocity = entity.getVelocity();
        NbtList motion = new NbtList();

        motion.add(NbtDouble.of(velocity.x));
        motion.add(NbtDouble.of(velocity.y));
        motion.add(NbtDouble.of(velocity.z));
        nbt.put("Motion", motion);

        StringBuilder command = new StringBuilder();

        command.append("summon ");
        command.append(typeId);
        command.append(String.format(Locale.US, " %.5f %.5f %.5f", entity.getX(), entity.getY(), entity.getZ()));

        if (!nbt.isEmpty())
        {
            command.append(' ');
            command.append(nbt);
        }

        BaseValue.edit(replay.actions, (actions) ->
        {
            CommandActionClip commandClip = new CommandActionClip();

            commandClip.tick.set(tick);
            commandClip.duration.set(1);
            commandClip.command.set(command.toString());
            replay.actions.addClip(commandClip);
        });
    }

    public static String formatSetblockState(BlockState state)
    {
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        String properties = state.getEntries().entrySet().stream()
            .map((entry) -> entry.getKey().getName() + "=" + entry.getValue().toString())
            .collect(Collectors.joining(","));

        if (properties.isEmpty())
        {
            return id;
        }

        return id + "[" + properties + "]";
    }
}
