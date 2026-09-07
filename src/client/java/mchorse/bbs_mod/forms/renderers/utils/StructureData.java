package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;

import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.GrassBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Encapsulates loaded structure geometry, NBT parsing, block categorization,
 * and virtual block render view cache.
 */
public class StructureData
{
    public static class BlockEntry
    {
        public final BlockState state;
        public final BlockPos pos;
        public final NbtCompound nbt;

        public BlockEntry(BlockState state, BlockPos pos, NbtCompound nbt)
        {
            this.state = state;
            this.pos = pos;
            this.nbt = nbt;
        }
    }

    private final List<BlockEntry> blocks = new ArrayList<>();
    private final List<BlockEntry> staticBlocks = new ArrayList<>();
    private final List<BlockEntry> animatedBlocks = new ArrayList<>();
    private final List<BlockEntry> biomeTintedBlocks = new ArrayList<>();
    private final List<BlockEntry> translucentBlocks = new ArrayList<>();
    private final List<BlockEntry> blockEntitiesList = new ArrayList<>();

    private String lastFile = null;

    private BlockPos size = BlockPos.ORIGIN;
    private BlockPos boundsMin = null;
    private BlockPos boundsMax = null;

    private boolean hasTranslucentLayer = false;
    private boolean hasCutoutLayer = false;
    private boolean hasAnimatedLayer = false;
    private boolean hasBiomeTintedLayer = false;
    private boolean hasLeavesLayer = false;
    private boolean hasBlockEntityLayer = false;

    private VirtualBlockRenderView.Entry[] entriesCache = null;
    private StructureVirtualBlockRenderView cachedView = null;

    public StructureData()
    {
    }

    public List<BlockEntry> getBlocks()
    {
        return this.blocks;
    }

    public List<BlockEntry> getAnimatedBlocks()
    {
        return this.animatedBlocks;
    }

    public List<BlockEntry> getStaticBlocks()
    {
        return this.staticBlocks;
    }

    public List<BlockEntry> getBiomeTintedBlocks()
    {
        return this.biomeTintedBlocks;
    }

    public List<BlockEntry> getTranslucentBlocks()
    {
        return this.translucentBlocks;
    }

    public List<BlockEntry> getBlockEntitiesList()
    {
        return this.blockEntitiesList;
    }

    public String getLastFile()
    {
        return this.lastFile;
    }

    public BlockPos getSize()
    {
        return this.size;
    }

    public BlockPos getBoundsMin()
    {
        return this.boundsMin;
    }

    public BlockPos getBoundsMax()
    {
        return this.boundsMax;
    }

    public boolean hasTranslucentLayer()
    {
        return this.hasTranslucentLayer;
    }

    public boolean hasCutoutLayer()
    {
        return this.hasCutoutLayer;
    }

    public boolean hasAnimatedLayer()
    {
        return this.hasAnimatedLayer;
    }

    public boolean hasBiomeTintedLayer()
    {
        return this.hasBiomeTintedLayer;
    }

    public boolean hasLeavesLayer()
    {
        return this.hasLeavesLayer;
    }

    public boolean hasBlockEntityLayer()
    {
        return this.hasBlockEntityLayer;
    }

    public VirtualBlockRenderView.Entry[] getEntriesCache()
    {
        return this.entriesCache;
    }

    public void setEntriesCache(VirtualBlockRenderView.Entry[] cache)
    {
        this.entriesCache = cache;
    }

    public StructureVirtualBlockRenderView getCachedView()
    {
        return this.cachedView;
    }

    public void setCachedView(StructureVirtualBlockRenderView view)
    {
        this.cachedView = view;
    }

    public boolean isEntirelyBlockEntities()
    {
        return this.hasBlockEntityLayer
            && !this.blockEntitiesList.isEmpty()
            && this.blockEntitiesList.size() >= this.blocks.size();
    }

    public void clear()
    {
        this.blocks.clear();
        this.animatedBlocks.clear();
        this.staticBlocks.clear();
        this.biomeTintedBlocks.clear();
        this.translucentBlocks.clear();
        this.blockEntitiesList.clear();
        this.size = BlockPos.ORIGIN;
        this.boundsMin = null;
        this.boundsMax = null;
        this.hasTranslucentLayer = false;
        this.hasCutoutLayer = false;
        this.hasAnimatedLayer = false;
        this.hasBiomeTintedLayer = false;
        this.hasLeavesLayer = false;
        this.hasBlockEntityLayer = false;
        this.entriesCache = null;
        this.cachedView = null;
        this.lastFile = null;
    }

    public boolean ensureLoaded(String file)
    {
        if (file == null || file.isEmpty())
        {
            this.clear();
            return false;
        }

        if (file.equals(this.lastFile) && !this.blocks.isEmpty())
        {
            return false;
        }

        this.clear();
        this.lastFile = file;

        File nbtFile = BBSMod.getProvider().getFile(Link.create(file));

        if (nbtFile != null && nbtFile.exists())
        {
            try
            {
                NbtCompound root = NbtIo.readCompressed(nbtFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                this.parseStructure(root);
                return true;
            }
            catch (IOException e)
            {
                /* Fall through */
            }
        }

        try (InputStream is = BBSMod.getProvider().getAsset(Link.create(file)))
        {
            try
            {
                NbtCompound root = NbtIo.readCompressed(is, NbtSizeTracker.ofUnlimitedBytes());
                this.parseStructure(root);
                return true;
            }
            catch (IOException e)
            {
                /* Fall through */
            }
        }
        catch (Exception e)
        {
            /* Fall through */
        }

        return true;
    }

    private void parseStructure(NbtCompound root)
    {
        if (root.contains("size"))
        {
            int[] sz = root.getIntArray("size").orElse(new int[0]);

            if (sz.length >= 3)
            {
                this.size = new BlockPos(sz[0], sz[1], sz[2]);
            }
        }

        List<BlockState> paletteStates = new ArrayList<>();

        if (root.contains("palette"))
        {
            NbtList palette = root.getListOrEmpty("palette");

            for (int i = 0; i < palette.size(); i++)
            {
                NbtCompound entry = palette.getCompoundOrEmpty(i);
                BlockState state = this.readBlockState(entry);
                paletteStates.add(state);
            }
        }

        if (root.contains("blocks"))
        {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            NbtList list = root.getListOrEmpty("blocks");

            StructureData.syncFancyGraphicsFromOptions();

            for (int i = 0; i < list.size(); i++)
            {
                NbtCompound be = list.getCompoundOrEmpty(i);
                BlockPos pos = this.readBlockPos(be.getListOrEmpty("pos"));
                int stateIndex = be.getInt("state", 0);

                if (stateIndex >= 0 && stateIndex < paletteStates.size())
                {
                    BlockState state = paletteStates.get(stateIndex);

                    if (state == null || state.isAir())
                    {
                        continue;
                    }

                    NbtCompound nbt = be.contains("nbt") ? be.getCompoundOrEmpty("nbt") : null;
                    BlockEntry blockEntry = new BlockEntry(state, pos, nbt);

                    this.blocks.add(blockEntry);

                    /* These blocks formerly existed only in the raw VAO. Keep a separate
                     * group so the modern base pass does not duplicate special layers. */
                    if (!StructureData.isAnimatedTexture(state) && !StructureData.isBiomeTinted(state)
                        && !StructureData.isTranslucentBlock(state))
                    {
                        this.staticBlocks.add(blockEntry);
                    }

                    if (!state.isOpaque())
                    {
                        this.hasCutoutLayer = true;
                    }

                    if (StructureData.isAnimatedTexture(state))
                    {
                        this.animatedBlocks.add(blockEntry);
                        this.hasAnimatedLayer = true;
                    }

                    if (StructureData.isBiomeTinted(state))
                    {
                        this.biomeTintedBlocks.add(blockEntry);
                        this.hasBiomeTintedLayer = true;
                    }

                    if (state.getBlock() instanceof LeavesBlock)
                    {
                        this.hasLeavesLayer = true;
                    }

                    if (StructureData.isTranslucentBlock(state))
                    {
                        this.translucentBlocks.add(blockEntry);
                        this.hasTranslucentLayer = true;
                    }

                    if (state.getBlock() instanceof BlockEntityProvider)
                    {
                        this.blockEntitiesList.add(blockEntry);
                        this.hasBlockEntityLayer = true;
                    }

                    if (pos.getX() < minX)
                    {
                        minX = pos.getX();
                    }

                    if (pos.getY() < minY)
                    {
                        minY = pos.getY();
                    }

                    if (pos.getZ() < minZ)
                    {
                        minZ = pos.getZ();
                    }

                    if (pos.getX() > maxX)
                    {
                        maxX = pos.getX();
                    }

                    if (pos.getY() > maxY)
                    {
                        maxY = pos.getY();
                    }

                    if (pos.getZ() > maxZ)
                    {
                        maxZ = pos.getZ();
                    }
                }
            }

            if (!this.blocks.isEmpty())
            {
                this.boundsMin = new BlockPos(minX, minY, minZ);
                this.boundsMax = new BlockPos(maxX, maxY, maxZ);
            }
        }
    }

    private BlockPos readBlockPos(NbtList list)
    {
        if (list == null || list.size() < 3)
        {
            return BlockPos.ORIGIN;
        }

        return new BlockPos(list.getInt(0, 0), list.getInt(1, 0), list.getInt(2, 0));
    }

    private BlockState readBlockState(NbtCompound entry)
    {
        String name = entry.getString("Name", "");
        Block block;
        BlockState state;

        try
        {
            Identifier id = Identifier.of(name);
            block = Registries.BLOCK.get(id);

            if (block == null)
            {
                block = Blocks.AIR;
            }
        }
        catch (Exception e)
        {
            block = Blocks.AIR;
        }

        if ("minecraft:jigsaw".equals(name) || block == Blocks.JIGSAW)
        {
            return Blocks.AIR.getDefaultState();
        }

        state = block.getDefaultState();

        if (entry.contains("Properties"))
        {
            NbtCompound props = entry.getCompoundOrEmpty("Properties");

            for (String key : props.getKeys())
            {
                String value = props.getString(key, "");
                Property<?> property = block.getStateManager().getProperty(key);

                if (property != null)
                {
                    Optional<?> parsed = property.parse(value);

                    if (parsed.isPresent())
                    {
                        try
                        {
                            @SuppressWarnings({"rawtypes", "unchecked"})
                            Property raw = property;
                            @SuppressWarnings("unchecked")
                            Comparable c = (Comparable) parsed.get();
                            state = state.with(raw, c);
                        }
                        catch (Exception ignored)
                        {
                            /* Ignore malformed property */
                        }
                    }
                }
            }
        }

        return state;
    }

    public static boolean isTranslucentBlock(BlockState state)
    {
        if (state == null || StructureData.isAnimatedTexture(state))
        {
            return false;
        }

        return state.isTransparent();
    }

    public static boolean isAnimatedTexture(BlockState state)
    {
        if (state == null)
        {
            return false;
        }

        if (state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE))
        {
            return true;
        }

        FluidState fs = state.getFluidState();

        if (fs != null)
        {
            if (fs.getFluid() == Fluids.WATER || fs.getFluid() == Fluids.FLOWING_WATER ||
                fs.getFluid() == Fluids.LAVA || fs.getFluid() == Fluids.FLOWING_LAVA)
            {
                return true;
            }
        }

        return false;
    }

    public static boolean isBiomeTinted(BlockState state)
    {
        if (state == null)
        {
            return false;
        }

        Block b = state.getBlock();

        return (b instanceof LeavesBlock)
            || (b instanceof GrassBlock)
            || (b instanceof VineBlock)
            || (b instanceof LilyPadBlock)
            || (b instanceof RedstoneWireBlock)
            || (b instanceof StemBlock)
            || (b instanceof AttachedStemBlock)
            || state.isOf(Blocks.FERN)
            || state.isOf(Blocks.SUGAR_CANE)
            || state.isOf(Blocks.SHORT_GRASS)
            || state.isOf(Blocks.TALL_GRASS)
            || state.isOf(Blocks.LARGE_FERN);
    }

    public static boolean isFancyGraphicsEnabled()
    {
        try
        {
            return MinecraftClient.getInstance().options.getPreset().getValue() != GraphicsMode.FAST;
        }
        catch (Throwable ignored)
        {
            return true;
        }
    }

    public static void syncFancyGraphicsFromOptions()
    {
        /* 1.21.11: RenderLayers option sync no longer required */
    }
}
