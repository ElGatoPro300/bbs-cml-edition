package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.LightmapModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import mchorse.bbs_mod.cubic.render.vao.StructureVAOCollector;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;

import net.minecraft.client.render.VertexConsumer;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages VAO generation, picking VAO generation, and global VAO caching for structures.
 */
public class StructureVaoManager
{
    public static class VaoHolder
    {
        public IModelVAO vao;
        public IModelVAO picking;
    }

    /* v4: translucent blocks excluded from main VAO (drawn as a live layer like animated/biome). */
    private static final int VAO_CACHE_VERSION = 4;
    private static final Map<String, VaoHolder> VAO_CACHE = new HashMap<>();
    private static final int LIGHTING_REVISION = 5;
    private static int cachedLightingRevision = -1;

    private boolean vaoDirty = true;
    private boolean vaoPickingDirty = true;
    private boolean capturingVAO = false;
    private boolean capturingIncludeSpecialBlocks = false;

    public static void clearAllCachedVaos()
    {
        for (VaoHolder holder : VAO_CACHE.values())
        {
            if (holder.vao instanceof ModelVAO)
            {
                ((ModelVAO) holder.vao).delete();
            }

            if (holder.vao instanceof LightmapModelVAO)
            {
                ((LightmapModelVAO) holder.vao).delete();
            }

            if (holder.picking instanceof ModelVAO)
            {
                ((ModelVAO) holder.picking).delete();
            }
        }

        VAO_CACHE.clear();
    }

    public static void ensureLightingRevision()
    {
        if (cachedLightingRevision != LIGHTING_REVISION)
        {
            StructureVaoManager.clearAllCachedVaos();
            cachedLightingRevision = LIGHTING_REVISION;
        }
    }

    public StructureVaoManager()
    {
    }

    public boolean isVaoDirty()
    {
        return this.vaoDirty;
    }

    public void setVaoDirty(boolean dirty)
    {
        this.vaoDirty = dirty;
    }

    public boolean isVaoPickingDirty()
    {
        return this.vaoPickingDirty;
    }

    public void setVaoPickingDirty(boolean dirty)
    {
        this.vaoPickingDirty = dirty;
    }

    public boolean isCapturingVAO()
    {
        return this.capturingVAO;
    }

    public boolean isCapturingIncludeSpecialBlocks()
    {
        return this.capturingIncludeSpecialBlocks;
    }

    public String vaoCacheKey(String lastFile)
    {
        return lastFile == null ? null : (VAO_CACHE_VERSION + ":" + lastFile);
    }

    public IModelVAO getStructureVao(String lastFile)
    {
        if (lastFile == null)
        {
            return null;
        }

        VaoHolder holder = VAO_CACHE.get(this.vaoCacheKey(lastFile));

        return holder != null ? holder.vao : null;
    }

    public IModelVAO getStructureVaoPicking(String lastFile)
    {
        if (lastFile == null)
        {
            return null;
        }

        VaoHolder holder = VAO_CACHE.get(this.vaoCacheKey(lastFile));

        return holder != null ? holder.picking : null;
    }

    public void clearCachedVao(String lastFile)
    {
        if (lastFile == null)
        {
            return;
        }

        VaoHolder holder = VAO_CACHE.remove(this.vaoCacheKey(lastFile));

        if (holder != null)
        {
            if (holder.vao instanceof ModelVAO)
            {
                ((ModelVAO) holder.vao).delete();
            }

            if (holder.vao instanceof LightmapModelVAO)
            {
                ((LightmapModelVAO) holder.vao).delete();
            }

            if (holder.picking instanceof ModelVAO)
            {
                ((ModelVAO) holder.picking).delete();
            }
        }
    }

    public void buildStructureVAO(String lastFile, Runnable renderCulledWorldPass)
    {
        CustomVertexConsumerProvider provider = FormUtilsClient.getProvider();
        StructureVAOCollector collector = new StructureVAOCollector();
        LightmapStructureVAOCollector lightWrapper = new LightmapStructureVAOCollector(collector);
        ModelVAOData data;

        provider.setSubstitute(vc -> lightWrapper);

        StructureData.syncFancyGraphicsFromOptions();

        this.capturingVAO = true;
        this.capturingIncludeSpecialBlocks = false;

        try
        {
            renderCulledWorldPass.run();
        }
        finally
        {
            this.capturingVAO = false;
            this.capturingIncludeSpecialBlocks = false;
        }

        provider.draw();
        provider.setSubstitute(null);

        data = collector.toData();

        if (lastFile != null)
        {
            VaoHolder holder = VAO_CACHE.computeIfAbsent(this.vaoCacheKey(lastFile), k -> new VaoHolder());

            if (holder.vao instanceof ModelVAO)
            {
                ((ModelVAO) holder.vao).delete();
            }

            if (holder.vao instanceof LightmapModelVAO)
            {
                ((LightmapModelVAO) holder.vao).delete();
            }

            holder.vao = new LightmapModelVAO(data, lightWrapper.getLightmapData());
        }

        this.vaoDirty = false;
    }

    public void buildStructureVAOPicking(String lastFile, StructureData data, Runnable renderCulledWorldPass, Runnable renderBEsOnlyPass, Consumer<StructureVAOCollector> appendPickCubesPass)
    {
        CustomVertexConsumerProvider provider = FormUtilsClient.getProvider();
        StructureVAOCollector collector = new StructureVAOCollector();
        ModelVAOData vaoData;

        provider.setSubstitute(vc -> collector);

        StructureData.syncFancyGraphicsFromOptions();

        this.capturingVAO = true;
        this.capturingIncludeSpecialBlocks = true;

        try
        {
            renderCulledWorldPass.run();
        }
        finally
        {
            this.capturingVAO = false;
            this.capturingIncludeSpecialBlocks = false;
        }

        if (data.hasBlockEntityLayer() && !data.getBlockEntitiesList().isEmpty())
        {
            try
            {
                provider.setSubstitute(vc -> collector);
                renderBEsOnlyPass.run();
            }
            catch (Throwable ignored)
            {
                /* Ignore BE picking pass failures */
            }

            if (appendPickCubesPass != null)
            {
                appendPickCubesPass.accept(collector);
            }
        }

        provider.draw();
        provider.setSubstitute(null);

        vaoData = collector.toData();

        if (lastFile != null)
        {
            VaoHolder holder = VAO_CACHE.computeIfAbsent(this.vaoCacheKey(lastFile), k -> new VaoHolder());

            if (holder.picking instanceof ModelVAO)
            {
                ((ModelVAO) holder.picking).delete();
            }

            holder.picking = new ModelVAO(vaoData);
        }

        this.vaoPickingDirty = false;
    }

    public static class LightmapStructureVAOCollector implements VertexConsumer
    {
        private final StructureVAOCollector delegate;
        private int[] lightData = new int[8192];
        private int lightSize = 0;
        private final int[] quadLights = new int[4];
        private int quadIndex = 0;

        public LightmapStructureVAOCollector(StructureVAOCollector delegate)
        {
            this.delegate = delegate;
        }

        public StructureVAOCollector getDelegate()
        {
            return this.delegate;
        }

        public int[] getLightmapData()
        {
            return Arrays.copyOf(this.lightData, this.lightSize);
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z)
        {
            this.delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer vertex(Matrix4fc matrix, float x, float y, float z)
        {
            this.delegate.vertex(matrix, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha)
        {
            this.delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer color(int argb)
        {
            this.delegate.color(argb);
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width)
        {
            this.delegate.lineWidth(width);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v)
        {
            this.delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v)
        {
            this.delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v)
        {
            this.quadLights[this.quadIndex] = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
            this.delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z)
        {
            this.delegate.normal(x, y, z);
            this.quadIndex++;

            if (this.quadIndex == 4)
            {
                this.addLight(this.quadLights[0]);
                this.addLight(this.quadLights[1]);
                this.addLight(this.quadLights[2]);

                this.addLight(this.quadLights[0]);
                this.addLight(this.quadLights[2]);
                this.addLight(this.quadLights[3]);

                this.quadIndex = 0;
            }

            return this;
        }

        private void addLight(int l)
        {
            if (this.lightSize >= this.lightData.length)
            {
                int[] n = new int[this.lightData.length * 2];
                System.arraycopy(this.lightData, 0, n, 0, this.lightSize);
                this.lightData = n;
            }

            this.lightData[this.lightSize++] = l;
        }
    }
}
