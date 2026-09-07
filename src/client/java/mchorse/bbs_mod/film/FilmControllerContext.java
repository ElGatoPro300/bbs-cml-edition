package mchorse.bbs_mod.film;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.Illusion;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.ShadowSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.gizmo.TransformOrientation;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import io.netty.util.collection.IntObjectMap;

public class FilmControllerContext
{
    public final static FilmControllerContext instance = new FilmControllerContext();

    public IntObjectMap<IEntity> entities;
    public IEntity entity;
    public Replay replay;
    public Film film;
    public Camera camera;
    public MatrixStack stack;
    public VertexConsumerProvider consumers;
    public StencilMap map;

    public float transition;
    public int color;
    public float shadowRadius;
    public float shadowRadiusX;
    public float shadowRadiusZ;
    public float shadowOpacity;
    public float shadowOffsetX;
    public float shadowOffsetY;
    public float shadowOffsetZ;

    /* Tick (with sub-tick transition) at which the replay's form properties were applied
     * this frame; NaN when the render path doesn't know it (illusion delay needs it) */
    public float propertyTick = Float.NaN;

    /* Film timeline tick used to restore other replays' form properties after temporary sampling */
    public int filmTick = -1;

    public String bone;
    public boolean local;
    public TransformOrientation orientation = TransformOrientation.PARENT;

    public String bone2;
    public boolean local2;
    public TransformOrientation orientation2 = TransformOrientation.PARENT;

    public String nameTag = "";
    public boolean relative;
    public boolean isShadowPass;
    /**
     * True when tools (gizmo / stencil) use a live {@code ActorEntity} / FP player
     * instead of the editor StubEntity. Skips look-at/IK so bone matrices match
     * {@code ActorEntityRenderer}, and skips drawing the body in the world pass
     * (the physical entity already draws it).
     */
    public boolean physicalActor;
    public Matrix4f localGroupTransform;
    public Matrix4f viewMatrix;
    public PaintSettings groupPaint;
    public GlowSettings groupGlow;
    public Color groupColorGrade;
    public Illusion groupIllusion;

    private FilmControllerContext()
    {}

    private void reset()
    {
        this.film = null;
        this.propertyTick = Float.NaN;
        this.filmTick = -1;
        this.map = null;
        this.shadowRadius = 0F;
        this.shadowRadiusX = 0F;
        this.shadowRadiusZ = 0F;
        this.shadowOpacity = 1F;
        this.shadowOffsetX = 0F;
        this.shadowOffsetY = 0F;
        this.shadowOffsetZ = 0F;
        this.color = Colors.WHITE;
        this.bone = null;
        this.local = false;
        this.orientation = TransformOrientation.PARENT;
        this.bone2 = null;
        this.local2 = false;
        this.orientation2 = TransformOrientation.PARENT;
        this.nameTag = "";
        this.relative = false;
        this.isShadowPass = false;
        this.physicalActor = false;
        this.localGroupTransform = null;
        this.viewMatrix = null;
        this.groupPaint = null;
        this.groupGlow = null;
        this.groupColorGrade = null;
        this.groupIllusion = null;
    }

    public FilmControllerContext setup(IntObjectMap<IEntity> entities, IEntity entity, Replay replay, WorldRenderContext context)
    {
        this.reset();

        this.entities = entities;
        this.entity = entity;
        this.replay = replay;
        this.camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        if (context.matrices() == null)
        {
            this.stack = new MatrixStack();
            MatrixStackUtils.multiply(this.stack, RenderSystem.getModelViewMatrix());
        }
        else if (!BBSRendering.isIrisShadersEnabled())
        {
            /* Match WorldRenderer entity pass: empty MatrixStack, then camera-relative
             * entity transform only. View rotation stays in ModelViewMat / BBSRendering.camera. */
            this.stack = new MatrixStack();
        }
        else
        {
            this.stack = context.matrices();
        }

        this.consumers = context.consumers();
        this.transition = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);

        return this;
    }

    public FilmControllerContext setup(IntObjectMap<IEntity> entities, IEntity entity, Replay replay, Camera camera, MatrixStack stack, VertexConsumerProvider consumers, float transition)
    {
        this.reset();

        this.entities = entities;
        this.entity = entity;
        this.replay = replay;
        this.camera = camera;
        this.stack = stack;
        this.consumers = consumers;
        this.transition = transition;

        return this;
    }

    public FilmControllerContext film(Film film)
    {
        this.film = film;

        return this;
    }

    public FilmControllerContext propertyTick(float propertyTick)
    {
        this.propertyTick = propertyTick;

        return this;
    }

    public FilmControllerContext filmTick(int filmTick)
    {
        this.filmTick = filmTick;

        return this;
    }

    public FilmControllerContext transition(float transition)
    {
        this.transition = transition;

        return this;
    }

    public FilmControllerContext stencil(StencilMap map)
    {
        this.map = map;

        return this;
    }

    public FilmControllerContext viewMatrix(Matrix4f viewMatrix)
    {
        this.viewMatrix = viewMatrix;

        return this;
    }

    public FilmControllerContext shadow(boolean shadow, float shadowRadius)
    {
        return this.shadow(shadow, shadowRadius, shadowRadius, 1F, 0F, 0F, 0F);
    }

    public FilmControllerContext shadow(boolean shadow, float shadowRadius, float shadowOpacity)
    {
        return this.shadow(shadow, shadowRadius, shadowRadius, shadowOpacity, 0F, 0F, 0F);
    }

    public FilmControllerContext shadow(boolean shadow, float shadowRadiusX, float shadowRadiusZ, float shadowOpacity)
    {
        return this.shadow(shadow, shadowRadiusX, shadowRadiusZ, shadowOpacity, 0F, 0F, 0F);
    }

    public FilmControllerContext shadow(boolean shadow, ShadowSettings settings)
    {
        if (settings == null)
        {
            return this.shadow(shadow, 0.5F, 0.5F, 1F, 0F, 0F, 0F);
        }

        return this.shadow(shadow, settings.widthX, settings.widthZ, settings.opacity, settings.offsetX, settings.offsetY, settings.offsetZ);
    }

    public FilmControllerContext shadow(boolean shadow, float shadowRadiusX, float shadowRadiusZ, float shadowOpacity, float offsetX, float offsetY, float offsetZ)
    {
        if (shadow)
        {
            this.shadowRadiusX = Math.max(0F, shadowRadiusX);
            this.shadowRadiusZ = Math.max(0F, shadowRadiusZ);
            this.shadowRadius = Math.max(this.shadowRadiusX, this.shadowRadiusZ);
            this.shadowOpacity = shadowOpacity;
            this.shadowOffsetX = offsetX;
            this.shadowOffsetY = offsetY;
            this.shadowOffsetZ = offsetZ;
        }
        else
        {
            this.shadowRadiusX = 0F;
            this.shadowRadiusZ = 0F;
            this.shadowRadius = 0F;
            this.shadowOpacity = 0F;
            this.shadowOffsetX = 0F;
            this.shadowOffsetY = 0F;
            this.shadowOffsetZ = 0F;
        }

        return this;
    }

    public FilmControllerContext shadow(float shadowRadius)
    {
        return this.shadow(true, shadowRadius, shadowRadius, 1F, 0F, 0F, 0F);
    }

    public FilmControllerContext color(int overlayColor)
    {
        this.color = overlayColor;

        return this;
    }

    public FilmControllerContext bone(String bone, boolean local)
    {
        return this.bone(bone, local ? TransformOrientation.LOCAL : TransformOrientation.PARENT);
    }

    public FilmControllerContext bone(String bone, TransformOrientation orientation)
    {
        this.bone = bone;
        this.orientation = orientation == null ? TransformOrientation.PARENT : orientation;
        this.local = this.orientation.isLocal();

        return this;
    }

    public FilmControllerContext bone2(String bone, boolean local)
    {
        return this.bone2(bone, local ? TransformOrientation.LOCAL : TransformOrientation.PARENT);
    }

    public FilmControllerContext bone2(String bone, TransformOrientation orientation)
    {
        this.bone2 = bone;
        this.orientation2 = orientation == null ? TransformOrientation.PARENT : orientation;
        this.local2 = this.orientation2.isLocal();

        return this;
    }

    public FilmControllerContext nameTag(String nameTag)
    {
        this.nameTag = nameTag;

        return this;
    }

    public FilmControllerContext relative(boolean relative)
    {
        this.relative = relative;

        return this;
    }

    public FilmControllerContext isShadowPass(boolean isShadowPass)
    {
        this.isShadowPass = isShadowPass;

        return this;
    }

    public FilmControllerContext physicalActor(boolean physicalActor)
    {
        this.physicalActor = physicalActor;

        return this;
    }
}