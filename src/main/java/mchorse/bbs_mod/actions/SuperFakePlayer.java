package mchorse.bbs_mod.actions;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stat;
import net.minecraft.util.math.BlockPos;

import com.mojang.authlib.GameProfile;

import com.google.common.collect.MapMaker;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

public class SuperFakePlayer extends ServerPlayerEntity
{
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("12345678-9ABC-DEF1-2345-6789ABCDEF69"), "[BBS Player]");
    private static final Map<SuperFakePlayer.FakePlayerKey, SuperFakePlayer> FAKE_PLAYER_MAP = new MapMaker().weakValues().makeMap();
    private final Map<String, BlockPos> replayChestPositions = new HashMap<>();

    public static SuperFakePlayer get(ServerWorld world)
    {
        Objects.requireNonNull(world, "World may not be null.");

        return FAKE_PLAYER_MAP.computeIfAbsent(new SuperFakePlayer.FakePlayerKey(world, PROFILE), key -> new SuperFakePlayer(key.world, key.profile));
    }

    protected SuperFakePlayer(ServerWorld world, GameProfile profile)
    {
        super(world.getServer(), world, profile, SyncedClientOptions.createDefault());

        this.networkHandler = new SuperFakePlayerNetworkHandler(this);
    }

    @Override
    public int getPermissionLevel()
    {
        return 2;
    }

    public boolean shouldBroadcastConsoleToOps()
    {
        return false;
    }

    public boolean shouldReceiveFeedback()
    {
        return false;
    }

    @Override
    public void tick()
    {}

    @Override
    public void setClientOptions(SyncedClientOptions settings)
    {}

    @Override
    public void increaseStat(Stat<?> stat, int amount)
    {}

    @Override
    public void resetStat(Stat<?> stat)
    {}

    public boolean isInvulnerableTo(DamageSource damageSource)
    {
        return true;
    }

    @Nullable
    @Override
    public Team getScoreboardTeam()
    {
        return null;
    }

    @Override
    public void sleep(BlockPos pos)
    {}

    @Override
    public boolean startRiding(Entity entity, boolean force)
    {
        return false;
    }

    @Override
    public void openEditSignScreen(SignBlockEntity sign, boolean front)
    {}

    @Override
    public OptionalInt openHandledScreen(@Nullable NamedScreenHandlerFactory factory)
    {
        return super.openHandledScreen(factory);
    }

    @Override
    public void openHorseInventory(AbstractHorseEntity horse, Inventory inventory)
    {}

    public void openReplayChest(String replayId, BlockPos pos)
    {
        if (replayId == null || replayId.isBlank() || pos == null)
        {
            return;
        }

        this.closeReplayChest(replayId);

        BlockState state = this.getWorld().getBlockState(pos);

        if (state.getBlock() instanceof ChestBlock)
        {
            this.getWorld().addSyncedBlockEvent(pos, state.getBlock(), 1, 1);
            this.getWorld().playSound(null, pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F, this.getWorld().getRandom().nextFloat() * 0.1F + 0.9F);
            this.replayChestPositions.put(replayId, pos.toImmutable());
        }
    }

    public void closeReplayChest(String replayId)
    {
        if (replayId == null || replayId.isBlank())
        {
            return;
        }

        BlockPos replayChestPos = this.replayChestPositions.remove(replayId);

        if (replayChestPos == null)
        {
            return;
        }

        BlockState state = this.getWorld().getBlockState(replayChestPos);

        if (state.getBlock() instanceof ChestBlock)
        {
            this.getWorld().addSyncedBlockEvent(replayChestPos, state.getBlock(), 1, 0);
            this.getWorld().playSound(null, replayChestPos, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, this.getWorld().getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }

    private record FakePlayerKey(ServerWorld world, GameProfile profile)
    {}
}
