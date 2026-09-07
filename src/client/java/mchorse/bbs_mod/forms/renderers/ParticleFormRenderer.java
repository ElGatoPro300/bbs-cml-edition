package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.utils.Illusion;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Renders / ticks {@link ParticleForm}. With illusion
 * {@link Illusion#independentParticles}, each focus keeps its
 * own emitter so streams stay in sync (scheme, pause, texture, user vars, film delay lag).
 */
public class ParticleFormRenderer extends FormRenderer<ParticleForm> implements ITickable
{
    public static long lastUpdate = 0L;

    /** Soft cap on independent emitters (main + illusion copies) to avoid runaway cost. */
    public static final int MAX_INDEPENDENT_EMITTERS = 48;

    /** Max catch-up updates when a lagged illusion emitter is created mid-lifetime. */
    private static final int MAX_AGE_CATCH_UP = 40;

    private ParticleEmitter emitter;
    private final Map<Integer, ParticleEmitter> illusionEmitters = new HashMap<>();
    private final Map<Integer, Integer> illusionDelayLags = new HashMap<>();
    private boolean checked;
    private boolean restart;
    private boolean wasPaused;
    private long lastParticleUpdate = lastUpdate;
    private String loadedEffect;

    public ParticleFormRenderer(ParticleForm form)
    {
        super(form);
    }

    public ParticleEmitter getEmitter()
    {
        return this.emitter;
    }

    public void ensureEmitter(World world, float transition)
    {
        this.ensureEmitter(world, false);
    }

    /**
     * @param applySimulationState when true (tick path), sync pause across all emitters.
     *        Render path leaves simulation alone so film illusion delay can override
     *        appearance for one focus without pausing every emitter.
     */
    private void ensureEmitter(World world, boolean applySimulationState)
    {
        if (this.lastParticleUpdate < lastUpdate)
        {
            this.lastParticleUpdate = lastUpdate;
            this.checked = false;
        }

        String effect = this.form.effect.get();

        if (!Objects.equals(effect, this.loadedEffect))
        {
            this.checked = false;
        }

        if (!this.checked)
        {
            this.rebuildPrimaryEmitter(world, effect);
            this.checked = true;
        }
        else if (this.emitter != null)
        {
            this.emitter.setWorld(world);
        }

        this.syncIllusionEmitters(world);

        if (applySimulationState)
        {
            this.applySimulationStateToAll();
        }
    }

    private void rebuildPrimaryEmitter(World world, String effect)
    {
        this.illusionEmitters.clear();
        this.illusionDelayLags.clear();
        this.loadedEffect = effect;

        ParticleScheme scheme = BBSModClient.getParticles().load(effect);

        if (scheme != null)
        {
            this.emitter = new ParticleEmitter();
            this.emitter.setScheme(scheme);
            this.emitter.setWorld(world);
        }
        else
        {
            this.emitter = null;
        }
    }

    private void syncIllusionEmitters(World world)
    {
        if (this.emitter == null)
        {
            this.illusionEmitters.clear();
            this.illusionDelayLags.clear();

            return;
        }

        boolean independent = FormIllusionRenderer.shouldUseIndependentParticles(this.form);

        if (!independent)
        {
            this.illusionEmitters.clear();
            this.illusionDelayLags.clear();
            this.emitter.spawnRateScale = 1F;

            return;
        }

        List<FormIllusionRenderer.EmissionTrailSite> sites = FormIllusionRenderer.collectEmissionTrailSites(this.form);
        int siteCount = Math.min(sites.size(), MAX_INDEPENDENT_EMITTERS);
        float scale = FormIllusionRenderer.shouldDistributeParticles(this.form) && siteCount > 1
            ? 1F / siteCount
            : 1F;

        this.emitter.spawnRateScale = scale;

        Set<Integer> keep = new HashSet<>();

        keep.add(0);

        for (int s = 0; s < siteCount; s++)
        {
            FormIllusionRenderer.EmissionTrailSite site = sites.get(s);

            if (site.trailInstance == 0)
            {
                this.illusionDelayLags.put(0, 0);

                continue;
            }

            keep.add(site.trailInstance);
            this.illusionDelayLags.put(site.trailInstance, site.delayLagTicks);
            this.ensureSiteEmitter(site.trailInstance, world, scale);
        }

        Iterator<Map.Entry<Integer, ParticleEmitter>> it = this.illusionEmitters.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<Integer, ParticleEmitter> entry = it.next();

            if (!keep.contains(entry.getKey()))
            {
                entry.getValue().stop();
                this.illusionDelayLags.remove(entry.getKey());
                it.remove();
            }
        }
    }

    private void ensureSiteEmitter(int trailInstance, World world, float spawnRateScale)
    {
        ParticleScheme scheme = this.emitter.scheme;
        ParticleEmitter siteEmitter = this.illusionEmitters.get(trailInstance);
        boolean created = false;

        if (siteEmitter == null)
        {
            siteEmitter = new ParticleEmitter();
            siteEmitter.setScheme(scheme);
            siteEmitter.setWorld(world);
            this.illusionEmitters.put(trailInstance, siteEmitter);
            created = true;
        }
        else
        {
            siteEmitter.setWorld(world);

            if (siteEmitter.scheme != scheme)
            {
                siteEmitter.setScheme(scheme);
                created = true;
            }
        }

        siteEmitter.spawnRateScale = spawnRateScale;
        this.syncEmitterAppearance(siteEmitter, null);

        if (created)
        {
            this.catchUpEmitterAge(siteEmitter, this.lagFor(trailInstance));
        }
    }

    private int lagFor(int trailInstance)
    {
        Integer lag = this.illusionDelayLags.get(trailInstance);

        return lag == null ? 0 : lag;
    }

    private void catchUpEmitterAge(ParticleEmitter siteEmitter, int lag)
    {
        if (this.emitter == null || siteEmitter == null)
        {
            return;
        }

        int desiredAge = Math.max(0, this.emitter.age - lag);
        int steps = Math.min(desiredAge - siteEmitter.age, MAX_AGE_CATCH_UP);

        for (int i = 0; i < steps; i++)
        {
            this.syncEmitterAppearance(siteEmitter, null);
            siteEmitter.paused = this.form.paused.get();
            siteEmitter.update();
        }
    }

    private void applySimulationStateToAll()
    {
        if (this.emitter == null || BBSRendering.isIrisShadowPass())
        {
            return;
        }

        boolean paused = this.form.paused.get();

        if (this.wasPaused && !paused)
        {
            this.restart = true;
        }

        this.wasPaused = paused;
        this.emitter.paused = paused;

        for (ParticleEmitter siteEmitter : this.illusionEmitters.values())
        {
            siteEmitter.paused = paused;
        }
    }

    private void syncEmitterAppearance(ParticleEmitter emitter, FormRenderingContext context)
    {
        if (emitter == null)
        {
            return;
        }

        emitter.setUserVariables(
            this.form.user1.get(),
            this.form.user2.get(),
            this.form.user3.get(),
            this.form.user4.get(),
            this.form.user5.get(),
            this.form.user6.get()
        );

        Link texture = this.form.texture.get();

        if (context != null && context.textureOverride != null)
        {
            texture = context.textureOverride;
        }

        emitter.texture = texture;
    }

    private ParticleEmitter emitterForTrail(int trailInstance)
    {
        if (trailInstance == 0 || !FormIllusionRenderer.shouldUseIndependentParticles(this.form))
        {
            return this.emitter;
        }

        ParticleEmitter siteEmitter = this.illusionEmitters.get(trailInstance);

        return siteEmitter != null ? siteEmitter : this.emitter;
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEmitter(MinecraftClient.getInstance().world, false);

        ParticleEmitter emitter = this.emitter;

        if (emitter != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();
            int scale = (y2 - y1) / 2;

            stack.push();
            stack.translate((x2 + x1) / 2, (y2 + y1) / 2, 40);
            MatrixStackUtils.scaleStack(stack, scale, scale, scale);

            this.syncEmitterAppearance(emitter, null);
            emitter.lastGlobal.set(new Vector3f(0, 0, 0));
            emitter.rotation.identity();

            emitter.setGlow(this.form.glowSettings.get(), this.form.glowingColor.get(), 1F);
            emitter.renderUI(stack, context.getTransition());
            emitter.clearGlow();

            stack.pop();
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.ensureEmitter(MinecraftClient.getInstance().world, false);

        ParticleEmitter emitter = this.emitterForTrail(context.trailInstance);

        if (emitter != null)
        {
            /* Film illusion delay may have just applied form properties for this copy. */
            this.syncEmitterAppearance(emitter, context);

            boolean useGameCamera = !context.modelRenderer && context.type != FormRenderType.PREVIEW;

            if (useGameCamera)
            {
                /* For game rendering, use the main camera for emitter properties to ensure
                 * correct yaw/pitch for billboards (avoiding 180 degree flip in Camera wrapper) */
                emitter.setupCameraProperties(MinecraftClient.getInstance().gameRenderer.getCamera());
            }
            else
            {
                if (context.modelRenderer)
                {
                    float originalPitch = context.camera.rotation.x;
                    float originalYaw = context.camera.rotation.y;
                    double originalX = context.camera.position.x;
                    double originalY = context.camera.position.y;
                    double originalZ = context.camera.position.z;

                    context.camera.rotation.set(0, 0, 0);
                    context.camera.position.set(0, 0, 0);

                    emitter.setupCameraProperties(context.camera);

                    context.camera.rotation.x = originalPitch;
                    context.camera.rotation.y = originalYaw;
                    context.camera.position.set(originalX, originalY, originalZ);
                }
                else
                {
                    emitter.setupCameraProperties(context.camera);
                }
            }

            Matrix4f modelMatrix = new Matrix4f(context.stack.peek().getPositionMatrix());

            Vector3d translation = new Vector3d(modelMatrix.getTranslation(Vectors.TEMP_3F));

            if (!context.modelRenderer)
            {
                translation.add(context.camera.position.x, context.camera.position.y, context.camera.position.z);
            }

            GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

            gameRenderer.getLightmapTextureManager().enable();
            gameRenderer.getOverlayTexture().setupOverlayColor();

            context.stack.push();
            context.stack.loadIdentity();

            emitter.lastGlobal.set(translation);
            emitter.rotation.set(modelMatrix);
            emitter.modelRenderer = context.modelRenderer;

            Color glowTint = Colors.COLOR.set(context.color, true);

            emitter.setGlow(this.form.glowSettings.get(), this.form.glowingColor.get(), glowTint.a);

            if (!BBSRendering.isIrisShadowPass())
            {
                boolean shadersEnabled = BBSRendering.isIrisShadersEnabled();
                boolean billboard = shadersEnabled;

                VertexFormat format = billboard ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR_LIGHT;
                Supplier<ShaderProgram> shader = billboard
                    ? this.getShader(context, () -> { RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_ENTITY_TRANSLUCENT); return RenderSystem.getShader(); }, BBSShaders::getPickerBillboardProgram)
                    : this.getShader(context, () -> { RenderSystem.setShader(ShaderProgramKeys.PARTICLE); return RenderSystem.getShader(); }, BBSShaders::getPickerParticlesProgram);

                emitter.render(format, shader, context.stack, context.overlay, context.getTransition());
            }

            emitter.clearGlow();

            context.stack.pop();

            gameRenderer.getLightmapTextureManager().disable();
            gameRenderer.getOverlayTexture().teardownOverlayColor();
        }
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEmitter(entity.getWorld(), true);

        if (this.emitter == null)
        {
            return;
        }

        /* Rewind emitters if paused and resumed so once-style schemes replay. */
        if (this.restart)
        {
            this.restartEmitter(this.emitter);

            for (ParticleEmitter siteEmitter : this.illusionEmitters.values())
            {
                this.restartEmitter(siteEmitter);
            }

            this.restart = false;
        }

        this.syncEmitterAppearance(this.emitter, null);
        this.emitter.update();

        if (!FormIllusionRenderer.shouldUseIndependentParticles(this.form))
        {
            return;
        }

        int mainAge = this.emitter.age;

        for (Map.Entry<Integer, ParticleEmitter> entry : this.illusionEmitters.entrySet())
        {
            ParticleEmitter siteEmitter = entry.getValue();
            int lag = this.lagFor(entry.getKey());

            this.syncEmitterAppearance(siteEmitter, null);

            if (mainAge > lag)
            {
                siteEmitter.update();
            }
        }
    }

    private void restartEmitter(ParticleEmitter emitter)
    {
        emitter.stop();
        emitter.start();
        this.syncEmitterAppearance(emitter, null);
        emitter.paused = this.form.paused.get();
    }
}
