package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.ExportParticleFreeze;
import mchorse.bbs_mod.client.ItemUseRenderState;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.MorphFireRenderer;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.events.register.RegisterFilmSimulationEvent;
import mchorse.bbs_mod.film.replays.ActorReplayStateSync;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.Illusion;
import mchorse.bbs_mod.forms.forms.utils.LookAt;
import mchorse.bbs_mod.forms.forms.utils.LookAtBone;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.ShadowSettings;
import mchorse.bbs_mod.forms.renderers.FormIllusionRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.FormDeathTilt;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.mixin.client.ClientPlayerEntityAccessor;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoMatrixUtils;
import mchorse.bbs_mod.ui.utils.gizmo.TransformOrientation;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

public abstract class BaseFilmController
{
    public final Film film;

    protected IntObjectMap<IEntity> entities = new IntObjectHashMap<>();
    protected Map<String, Replay> replayMap = new HashMap<>();

    public boolean paused;
    public int exception = -1;

    /**
     * Last film tick at which each replay already evaluated a step sound.
     * Film editor keeps calling {@link #update()} while the playhead is parked, so
     * without this edge the same step tick would spam audio every client tick.
     */
    private final Map<String, Integer> lastStepSoundTicks = new HashMap<>();
    private final Map<String, Integer> lastItemUseParticleTicks = new HashMap<>();

    /**
     * Last resolved physical actor entity id per replay. Used to one-shot snap
     * world position when Actor mode binds a new entity (toggle on / respawn).
     */
    private final Map<String, Integer> lastSeenActorEntityIds = new HashMap<>();

    /* Rendering helpers */

    public static void renderEntity(FilmControllerContext context)
    {
        IntObjectMap<IEntity> entities = context.entities;
        IEntity entity = context.entity;
        Camera camera = context.camera;
        MatrixStack stack = context.stack;
        float transition = context.transition;

        Form form = entity.getForm();

        if (form == null || !form.render.get() || !form.visible.get())
        {
            return;
        }

        FormDeathTilt.pushSample(context);

        try
        {
            renderEntityBody(context, entities, entity, camera, stack, transition, form);
        }
        finally
        {
            FormDeathTilt.popSample();
        }
    }

    private static void renderEntityBody(FilmControllerContext context, IntObjectMap<IEntity> entities, IEntity entity, Camera camera, MatrixStack stack, float transition, Form form)
    {
        applyGroupPaintGlow(form, context.groupPaint, context.groupGlow);
        applyGroupColorGrade(form, context.groupColorGrade);
        applyGroupIllusion(form, context.groupIllusion);

        Vector3d position = Vectors.TEMP_3D.set(
            Lerps.lerp(entity.getPrevX(), entity.getX(), transition),
            Lerps.lerp(entity.getPrevY(), entity.getY(), transition),
            Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition)
        );

        double cx = camera.getPos().x;
        double cy = camera.getPos().y;
        double cz = camera.getPos().z;

        boolean relative = context.replay != null && context.relative;

        if (relative)
        {
            if (context.map != null)
            {
                cx = context.replay.keyframes.x.interpolate(0F) + context.replay.relativeOffset.get().x;
                cy = context.replay.keyframes.y.interpolate(0F) + context.replay.relativeOffset.get().y;
                cz = context.replay.keyframes.z.interpolate(0F) + context.replay.relativeOffset.get().z;
            }
            else
            {
                cx = position.x + context.replay.relativeOffset.get().x;
                cy = position.y + context.replay.relativeOffset.get().y;
                cz = position.z + context.replay.relativeOffset.get().z;
            }

            if (context.isShadowPass)
            {
                cx += camera.getPos().x;
                cy += camera.getPos().y;
                cz += camera.getPos().z;
            }
        }

        Matrix4f target = null;
        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, cx, cy, cz, transition);
        float opacity = 1F;

        if (!relative)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(entities, form.anchor.get(), defaultMatrix, cx, cy, cz, transition, 0);

            target = pair.a;
            opacity = pair.b;
        }

        if (target != null)
        {
            Vector3f v = target.getTranslation(new Vector3f());
            Vector3f v2 = defaultMatrix.getTranslation(new Vector3f());

            position.x += v.x - v2.x;
            position.y += v.y - v2.y;
            position.z += v.z - v2.z;
        }
        else
        {
            target = defaultMatrix;
        }

        if (!relative && !context.physicalActor)
        {
            applyLookAt(context, form, position, target);
            InverseKinematicsApplier.apply(context, form);
        }

        if (context.localGroupTransform != null && !context.isShadowPass)
        {
            target.mul(context.localGroupTransform);
        }

        World world = entity.getWorld();

        if (world == null)
        {
            world = MinecraftClient.getInstance().world;
        }

        if (world == null)
        {
            return;
        }

        /* MobForm stubs must match ActorEntity / EntityRenderer.getLight (eye height +
         * WorldRenderer). Other film forms keep the historical feet+0.5 sample. */
        int light;

        if (form instanceof MobForm)
        {
            BlockPos pos = BlockPos.ofFloored(position.x, position.y + entity.getEyeHeight(), position.z);

            light = WorldRenderer.getLightmapCoordinates(world, pos);
        }
        else
        {
            BlockPos pos = BlockPos.ofFloored(position.x, position.y + 0.5D, position.z);
            int sky = world.getLightLevel(LightType.SKY, pos);
            int torch = world.getLightLevel(LightType.BLOCK, pos);

            light = LightmapTextureManager.pack(torch, sky);
        }

        int overlay = OverlayTexture.packUv(OverlayTexture.getU(0F), OverlayTexture.getV(entity.getHurtTimer() > 0));

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.ENTITY, entity, stack, light, overlay, transition)
            .camera(camera)
            .stencilMap(context.map)
            .color(context.color);

        formContext.relative = relative;
        formContext.isShadowPass = context.isShadowPass;
        formContext.viewMatrix = context.viewMatrix;

        /* World pass: physical ActorEntity already draws the body — only capture gizmos.
         * Stencil pass (map != null): still draw the form so bone pick/highlight match the actor. */
        boolean drawBody = !context.physicalActor || context.map != null;

        stack.push();

        try
        {
            if (relative)
            {
                if (!context.isShadowPass)
                {
                    stack.peek().getPositionMatrix().identity();
                    stack.peek().getNormalMatrix().identity();
                }

                if (context.map == null)
                {
                    stack.multiply(camera.getRotation());
                }
            }

            MatrixStackUtils.multiply(stack, target);

            /* IRLights 1.21+ reads FormRenderingContext.world (absolute) for light poses.
             * Rebuild that root in true world space (independent of any render camera or viewport)
             * so light registration cannot mix the film actor frame with the spectator/player view. */
            if (drawBody)
            {
                syncIrlAbsoluteWorldMatrix(formContext, context, entity, relative, transition);
            }

            ModelFormRenderer lookAtRenderer = (relative || context.physicalActor) ? null : applyLookAtPose(context, form, position);

            if (drawBody && context.isShadowPass)
            {
                if (context.shadowOpacity <= 0.001F || (context.shadowRadiusX <= 0F && context.shadowRadiusZ <= 0F))
                {
                    return;
                }

                /* Form Opacity is applied once in the form renderer (applyFormOpacity). Do not
                 * multiply it here or caster alpha becomes opacity² and ground shadows fade too fast. */
                if (form.getFormOpacity() <= 0.001F)
                {
                    return;
                }

                float shadowAlpha = Colors.getA(formContext.color) * context.shadowOpacity;

                if (shadowAlpha <= 0.001F)
                {
                    return;
                }

                /* Replay shadowOpacity only — Color-track effects must not crush caster alpha
                 * (Iris would drop the ground shadow). Opacity 0 already returned above. */
                formContext.color(Colors.setA(formContext.color, MathUtils.clamp(shadowAlpha, 0F, 1F)));

                if (context.shadowOffsetX != 0F || context.shadowOffsetY != 0F || context.shadowOffsetZ != 0F)
                {
                    stack.translate(context.shadowOffsetX, context.shadowOffsetY, context.shadowOffsetZ);
                }

                /* Independent X/Z scale from default radius 0.5 — stretch wide or long under Iris. */
                float scaleX = Math.max(0.001F, context.shadowRadiusX / 0.5F);
                float scaleZ = Math.max(0.001F, context.shadowRadiusZ / 0.5F);

                if (Math.abs(scaleX - 1F) > 0.001F || Math.abs(scaleZ - 1F) > 0.001F)
                {
                    stack.scale(scaleX, 1F, scaleZ);
                }
            }

            FormIllusionRenderer.Extras illusionExtras = null;

            if (drawBody && context.replay != null && !Float.isNaN(context.propertyTick))
            {
                illusionExtras = new FormIllusionRenderer.Extras();
                illusionExtras.propertyTick = context.propertyTick;
                illusionExtras.applyFormAtTick = (tick) ->
                {
                    context.replay.properties.resetProperties(form);
                    context.replay.properties.applyProperties(form, tick);
                };
                illusionExtras.restoreFormTick = () ->
                {
                    context.replay.properties.resetProperties(form);
                    context.replay.properties.applyProperties(form, context.propertyTick);
                };
            }

            if (drawBody)
            {
                if (context.isShadowPass)
                {
                    ShaderOpacityPatch.beginShadowForm();
                }

                try
                {
                    /* Illusions are drawn inside FormUtilsClient for model blocks / morphs / preview too. */
                    FormUtilsClient.render(form, formContext, context.map == null ? illusionExtras : null);

                    if (!context.isShadowPass && context.map == null && entity.getFireTicks() > 0)
                    {
                        MorphFireRenderer.render(stack, context.consumers, entity, form, transition, camera, relative);
                    }
                }
                finally
                {
                    if (context.isShadowPass)
                    {
                        ShaderOpacityPatch.endShadowForm();
                    }
                }
            }

            if (lookAtRenderer != null)
            {
                lookAtRenderer.setLookAtPose(null);
            }

            if (UIBaseMenu.renderAxes)
            {
                if (context.bone != null && context.orientation == TransformOrientation.PARENT)
                {
                    Form root = FormUtils.getRoot(form);
                    MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, transition);
                    MatrixCacheEntry entry = map.get(context.bone);

                    Matrix4f matrix = entry.origin();

                    if (matrix == null)
                    {
                        matrix = entry.matrix();
                    }

                    if (matrix != null)
                    {
                        stack.push();
                        MatrixStackUtils.multiply(stack, matrix);

                        if (context.map == null)
                        {
                            BaseFilmController.renderGizmo(stack, null);
                        }
                        else
                        {
                            BaseFilmController.renderGizmo(stack, context.map);
                        }

                        RenderSystem.enableDepthTest();
                        stack.pop();
                    }
                }
                if (context.bone != null) renderAxes(context.bone, context.orientation, context.map, form, entity, transition, stack);
                if (context.bone2 != null && context.map == null) renderAxes(context.bone2, context.orientation2, context.map, form, entity, transition, stack);
            }
        }
        finally
        {
            stack.pop();
        }

        /* Soft-opacity / glow / UI-style form passes can leave depthMask false or
         * depthFunc=ALWAYS — blob shadows and nametags then ignore walls and draw on top
         * of the body. Actors avoid this via the vanilla entity pass. */
        restoreFilmOverlayDepthState();

        /* Vanilla blob shadows only without Iris shaders — Comp/BSL use the shadow map.
         * Blob opacity is the Shadow track only; form Opacity must not fade the ground circle.
         * Size X/Z are independent (matrix scale); vanilla API only has one radius. */
        if (drawBody && !relative && context.map == null && opacity > 0F
            && (context.shadowRadiusX > 0F || context.shadowRadiusZ > 0F)
            && form.render.get() && form.visible.get()
            && !context.isShadowPass && !BBSRendering.isIrisShadersEnabled())
        {
            float shadowOpacity = MathUtils.clamp(opacity * context.shadowOpacity, 0F, 1F);

            if (shadowOpacity > 0F)
            {
                /* X/Z offset moves the ground sample; Y offset must lift the PNG via matrix
                 * translate — putting it into entity Y only fades the vanilla blob in place. */
                double sx = position.x + context.shadowOffsetX;
                double sy = position.y;
                double sz = position.z + context.shadowOffsetZ;

                stack.push();
                stack.translate(sx - cx, sy - cy, sz - cz);

                ModelBlockEntityRenderer.renderShadow(context.consumers, stack, transition, sx, sy, sz, 0F, context.shadowOffsetY, 0F, context.shadowRadiusX, context.shadowRadiusZ, shadowOpacity);

                stack.pop();
            }
        }

        if (drawBody && !relative && !context.nameTag.isEmpty())
        {
            stack.push();
            stack.translate(position.x - cx, position.y - cy, position.z - cz);

            renderNameTag(entity, Text.literal(StringUtils.processColoredText(context.nameTag)), stack, context.consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE);

            stack.pop();
        }

        restoreFilmOverlayDepthState();
    }

    /**
     * Depth state expected by vanilla ground shadows and name labels after a form draw.
     */
    private static void restoreFilmOverlayDepthState()
    {
        BBSRendering.restoreWorldRenderState();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    /**
     * World-space point of a replay's attachment, used by look-at and inverse kinematics.
     */
    public static Vector3d resolveReplayAttachmentPoint(FilmControllerContext context, int replayIndex, String attachment)
    {
        if (context == null || replayIndex < 0)
        {
            return null;
        }

        IEntity targetEntity = context.entities.get(replayIndex);

        if (targetEntity == null)
        {
            return null;
        }

        return getLookAtTargetPoint(targetEntity, attachment, context.transition);
    }

    /**
     * World-space orientation of a replay's attachment, used by inverse kinematics
     * angle targets.
     */
    public static Quaternionf resolveReplayAttachmentRotation(FilmControllerContext context, int replayIndex, String attachment)
    {
        if (context == null || replayIndex < 0)
        {
            return null;
        }

        IEntity targetEntity = context.entities.get(replayIndex);

        if (targetEntity == null)
        {
            return null;
        }

        return getLookAtTargetRotation(targetEntity, attachment, context.transition);
    }

    private static void applyLookAt(FilmControllerContext context, Form form, Vector3d position, Matrix4f target)
    {
        LookAt lookAt = form.lookAt.get();

        if (lookAt == null || !lookAt.translate || context.film == null)
        {
            return;
        }

        LookAtBone strongest = null;

        for (LookAtBone bone : lookAt.bones.values())
        {
            if (bone.isActive() && (strongest == null || bone.blend > strongest.blend))
            {
                strongest = bone;
            }
        }

        if (strongest == null)
        {
            return;
        }

        IEntity targetEntity = context.entities.get(strongest.replay);

        if (targetEntity == null || targetEntity == context.entity)
        {
            return;
        }

        Replay targetReplay = CollectionUtils.getSafe(context.film.replays.getList(), strongest.replay);

        if (targetReplay == null)
        {
            return;
        }

        float transition = context.transition;
        float blend = MathUtils.clamp(strongest.blend, 0F, 1F);
        float restorePropertyTick = getLookAtRestorePropertyTick(context, targetReplay, transition);

        Vector3d pointNow = getLookAtTargetPoint(targetEntity, strongest.attachment, transition);
        Vector3d pointBase = getLookAtTargetPointAtPropertyTick(targetReplay, targetEntity, strongest.attachment, 0F, transition, restorePropertyTick);
        double dx = (pointNow.x - pointBase.x) * blend;
        double dy = (pointNow.y - pointBase.y) * blend;
        double dz = (pointNow.z - pointBase.z) * blend;

        position.add(dx, dy, dz);

        Vector3f translation = target.getTranslation(new Vector3f());

        target.setTranslation(translation.x + (float) dx, translation.y + (float) dy, translation.z + (float) dz);
    }

    /**
     * Computes the extra yaw/pitch (in the model's local space) that would make the
     * entity fully face the look at target, or null when the direction is degenerate.
     */
    private static Vector2f getLookAtRotation(IEntity entity, IEntity targetEntity, String attachment, Vector3d position, float transition)
    {
        Vector3d targetPoint = getLookAtTargetPoint(targetEntity, attachment, transition);
        double dirX = targetPoint.x - position.x;
        double dirY = targetPoint.y - position.y;
        double dirZ = targetPoint.z - position.z;
        double horizontal = Math.sqrt(dirX * dirX + dirZ * dirZ);

        if (horizontal * horizontal + dirY * dirY < 0.0001D)
        {
            return null;
        }

        /* Entities face (-sin(yaw), 0, cos(yaw)), and the matrix contains rotateY(-bodyYaw),
         * so the desired matrix rotation that faces the target is atan2(dirX, dirZ) */
        float desiredYaw = (float) Math.atan2(dirX, dirZ);
        float currentYaw = MathUtils.toRad(-Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition));
        float deltaYaw = desiredYaw - currentYaw;

        /* Wrap into -PI..PI so the blended rotation takes the shortest path */
        deltaYaw = (float) Math.atan2(Math.sin(deltaYaw), Math.cos(deltaYaw));

        float pitch = (float) Math.atan2(dirY, horizontal);

        return new Vector2f(deltaYaw, pitch);
    }

    /**
     * Property tick at which the target replay's form properties should be restored
     * after temporarily sampling another tick for look at translate follow.
     */
    private static float getLookAtRestorePropertyTick(FilmControllerContext context, Replay targetReplay, float transition)
    {
        if (context.filmTick >= 0)
        {
            return targetReplay.getTick(context.filmTick) + transition;
        }

        if (targetReplay == context.replay && !Float.isNaN(context.propertyTick))
        {
            return context.propertyTick;
        }

        return Float.NaN;
    }

    /**
     * Visual offset matrix for a look at target: a picked attachment bone, the form
     * root (including transform overlays), or the anchor attachment as fallback.
     */
    private static Matrix4f getLookAtVisualMatrix(MatrixCache map, Form targetForm, String attachment)
    {
        Matrix4f visualMatrix = null;

        if (attachment != null && !attachment.isEmpty())
        {
            MatrixCacheEntry entry = map.get(attachment.replace("#origin", ""));

            if (entry != null)
            {
                visualMatrix = entry.origin() != null ? entry.origin() : entry.matrix();
            }
        }
        else
        {
            MatrixCacheEntry entry = map.get("");

            if (entry != null)
            {
                visualMatrix = entry.origin() != null ? entry.origin() : entry.matrix();
            }

            if (visualMatrix == null)
            {
                Anchor anchor = targetForm.anchor.get();

                if (anchor != null && !anchor.attachment.isEmpty())
                {
                    entry = map.get(anchor.attachment.replace("#origin", ""));

                    if (entry != null)
                    {
                        visualMatrix = entry.origin() != null ? entry.origin() : entry.matrix();
                    }
                }
            }
        }

        return visualMatrix;
    }

    /**
     * Entity matrix from replay position keyframes at the given property tick, without
     * using the entity's live coordinates.
     */
    private static Matrix4f getMatrixForReplayKeyframes(Replay replay, float propertyTick, float transition)
    {
        double x = replay.keyframes.x.interpolate(propertyTick);
        double y = replay.keyframes.y.interpolate(propertyTick);
        double z = replay.keyframes.z.interpolate(propertyTick);
        double prevX = replay.keyframes.x.interpolate(propertyTick - 1F);
        double prevY = replay.keyframes.y.interpolate(propertyTick - 1F);
        double prevZ = replay.keyframes.z.interpolate(propertyTick - 1F);
        float bodyYaw = replay.keyframes.bodyYaw.interpolate(propertyTick).floatValue();
        float prevBodyYaw = replay.keyframes.bodyYaw.interpolate(propertyTick - 1F).floatValue();
        Matrix4f matrix = new Matrix4f();

        matrix.translate(
            (float) Lerps.lerp(prevX, x, transition),
            (float) Lerps.lerp(prevY, y, transition),
            (float) Lerps.lerp(prevZ, z, transition)
        );
        float yaw = (float) Lerps.lerpYaw(prevBodyYaw, bodyYaw, transition);

        matrix.rotateY(MathUtils.toRad(-yaw));

        return matrix;
    }

    /**
     * World position of the look at target. When an attachment bone is picked, the
     * bone's matrix is used (which reacts to the target's pose animation), otherwise
     * the target form's full visual transform is taken into account.
     */
    private static Vector3d getLookAtTargetPoint(IEntity targetEntity, String attachment, float transition)
    {
        Matrix4f matrix = getMatrixForRenderWithRotation(targetEntity, 0D, 0D, 0D, transition);
        Form targetForm = targetEntity.getForm();

        if (targetForm != null)
        {
            MatrixCache map = FormUtilsClient.getRenderer(targetForm).collectMatrices(targetEntity, transition);
            Matrix4f visualMatrix = getLookAtVisualMatrix(map, targetForm, attachment);

            if (visualMatrix != null)
            {
                matrix.mul(visualMatrix);
            }
        }

        Vector3f translation = matrix.getTranslation(new Vector3f());

        return new Vector3d(translation);
    }

    private static Quaternionf getLookAtTargetRotation(IEntity targetEntity, String attachment, float transition)
    {
        Matrix4f matrix = getMatrixForRenderWithRotation(targetEntity, 0D, 0D, 0D, transition);
        Form targetForm = targetEntity.getForm();

        if (targetForm != null)
        {
            MatrixCache map = FormUtilsClient.getRenderer(targetForm).collectMatrices(targetEntity, transition);
            Matrix4f visualMatrix = getLookAtVisualMatrix(map, targetForm, attachment);

            if (visualMatrix != null)
            {
                matrix.mul(visualMatrix);
            }
        }

        return matrix.getNormalizedRotation(new Quaternionf());
    }

    /**
     * Same as {@link #getLookAtTargetPoint} but samples the target replay at a specific
     * property tick (for example tick 0 as the translate follow baseline). Restores the
     * target form's properties afterward when {@code restorePropertyTick} is not NaN.
     */
    private static Vector3d getLookAtTargetPointAtPropertyTick(Replay replay, IEntity targetEntity, String attachment, float propertyTick, float transition, float restorePropertyTick)
    {
        Form form = targetEntity.getForm();
        Matrix4f matrix = getMatrixForReplayKeyframes(replay, propertyTick, transition);

        if (form != null)
        {
            replay.properties.resetProperties(form);
            replay.properties.applyProperties(form, propertyTick);

            MatrixCache map = FormUtilsClient.getRenderer(form).collectMatrices(targetEntity, transition);
            Matrix4f visualMatrix = getLookAtVisualMatrix(map, form, attachment);

            if (visualMatrix != null)
            {
                matrix.mul(visualMatrix);
            }

            if (!Float.isNaN(restorePropertyTick))
            {
                replay.properties.resetProperties(form);
                replay.properties.applyProperties(form, restorePropertyTick);
            }
        }

        Vector3f translation = matrix.getTranslation(new Vector3f());

        return new Vector3d(translation);
    }

    /**
     * Sets a temporary look at pose on the form's renderer. Every locked bone gets
     * rotated toward its own target (replay and optionally attachment), scaled by
     * its own lock strength. The returned renderer must be cleared with
     * setLookAtPose(null) after rendering.
     */
    private static ModelFormRenderer applyLookAtPose(FilmControllerContext context, Form form, Vector3d position)
    {
        LookAt lookAt = form.lookAt.get();

        if (lookAt == null || !lookAt.isActive())
        {
            return null;
        }

        if (!(FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer))
        {
            return null;
        }

        Pose pose = new Pose();

        for (Map.Entry<String, LookAtBone> entry : lookAt.bones.entrySet())
        {
            LookAtBone bone = entry.getValue();

            if (!bone.isActive())
            {
                continue;
            }

            IEntity targetEntity = context.entities.get(bone.replay);

            if (targetEntity == null || targetEntity == context.entity)
            {
                continue;
            }

            Vector2f rotation = getLookAtRotation(context.entity, targetEntity, bone.attachment, position, context.transition);

            if (rotation == null)
            {
                continue;
            }

            float blend = MathUtils.clamp(bone.blend, 0F, 1F);
            PoseTransform poseTransform = pose.get(entry.getKey());

            poseTransform.rotate.y = rotation.x * blend;
            poseTransform.rotate.x = rotation.y * blend;
        }

        if (pose.isEmpty())
        {
            return null;
        }

        renderer.setLookAtPose(pose);

        return renderer;
    }

    private static void renderGizmo(MatrixStack stack, StencilMap stencilMap)
    {
        if (stencilMap == null)
        {
            /* Visual is drawn later in the panel UI pass (Gizmo#renderInterface). */
            Gizmo.INSTANCE.captureVisual(stack);
        }
        else
        {
            Gizmo.INSTANCE.renderStencil(stack, stencilMap);
        }
    }

    private static void renderAxes(String bone, TransformOrientation space, StencilMap stencilMap, Form form, IEntity entity, float transition, MatrixStack stack)
    {
        Form root = FormUtils.getRoot(form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, transition);
        MatrixCacheEntry entry = map.get(bone);

        if (entry == null)
        {
            return;
        }

        Matrix4f matrix;
        Form rootForm = FormUtils.getRoot(form);
        boolean bobj = rootForm instanceof ModelForm modelForm && ModelFormRenderer.isBobjModel(modelForm);

        matrix = GizmoMatrixUtils.resolveFilmPoseBoneMatrix(entry, space, bobj);

        if (matrix != null)
        {
            Gizmo.INSTANCE.setActiveOrientation(space);
            stack.push();
            MatrixStackUtils.multiply(stack, matrix);

            if (stencilMap == null)
            {
                BaseFilmController.renderGizmo(stack, null);
            }
            else
            {
                BaseFilmController.renderGizmo(stack, stencilMap);
            }

            RenderSystem.enableDepthTest();
            stack.pop();
        }
    }

    public static Pair<Matrix4f, Float> getTotalMatrix(IntObjectMap<IEntity> entities, Anchor value, Matrix4f defaultMatrix, double cx, double cy, double cz, float transition, int i)
    {
        /* Stupid recursion stop, I don't think anyone would need more than that */
        if (i > 5)
        {
            return new Pair<>(defaultMatrix, 1F);
        }

        boolean same = value.previous == null || Objects.equals(value, value.previous);
        boolean only = value.x <= 0F && value.previous != null;
        Pair<Matrix4f, Float> result = new Pair<>(null, 1F);

        if (same || only)
        {
            Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, same ? value : value.previous, defaultMatrix, transition, i);

            matrix = applyAnchorTransform(matrix, same ? value : value.previous);

            if (matrix != defaultMatrix)
            {
                result.a = matrix;
                result.b = 0F;
            }
        }
        else
        {
            Matrix4f matrix = getEntityMatrix(entities, cx, cy, cz, value, defaultMatrix, transition, i);
            Matrix4f lastMatrix = getEntityMatrix(entities, cx, cy, cz, value.previous, defaultMatrix, transition, i);

            matrix = applyAnchorTransform(matrix, value);
            lastMatrix = applyAnchorTransform(lastMatrix, value.previous);

            result.a = value.x >= 1F ? matrix : Matrices.lerp(lastMatrix, matrix, value.x);

            if (value.isFadeOut()) result.b = value.x;
            else if (value.isFadeIn()) result.b = 1F - value.x;
            else result.b = 0F;
        }

        return result;
    }

    private static Matrix4f applyAnchorTransform(Matrix4f matrix, Anchor anchor)
    {
        if (matrix == null || anchor == null || anchor.transform.isDefault())
        {
            return matrix;
        }

        return matrix.mul(anchor.transform.createMatrix());
    }

    public static Matrix4f getEntityMatrix(IntObjectMap<IEntity> entities, double cameraX, double cameraY, double cameraZ, Anchor anchor, Matrix4f defaultMatrix, float transition, int i)
    {
        IEntity entity = entities.get(anchor.replay);

        if (entity != null)
        {
            Matrix4f basic = getMatrixForRenderWithRotation(entity, cameraX, cameraY, cameraZ, transition);

            Form form = entity.getForm();

            if (form != null)
            {
                Pair<Matrix4f, Float> totalMatrix = getTotalMatrix(entities, form.anchor.get(), basic, cameraX, cameraY, cameraZ, transition, i + 1);

                if (totalMatrix.a != null)
                {
                    basic = totalMatrix.a;
                }

                MatrixCache map = FormUtilsClient.getRenderer(form).collectMatrices(entity, transition);
                boolean forceOrigin = anchor.attachment != null && anchor.attachment.endsWith("#origin");
                String core = anchor.attachment == null ? null : anchor.attachment.replace("#origin", "");
                
                MatrixCacheEntry entry = map.get(core);
                Matrix4f matrix = null;

                if (entry != null)
                {
                    if (forceOrigin)
                    {
                        matrix = entry.origin();
                    }
                    else if (anchor.translate)
                    {
                        matrix = entry.origin();
                        if (matrix == null)
                        {
                            matrix = entry.matrix();
                        }
                    }
                    else
                    {
                        matrix = entry.matrix();
                        if (matrix == null)
                        {
                            matrix = entry.origin();
                        }
                    }
                }

                if (matrix != null)
                {
                    basic.mul(matrix);

                    if (anchor.scale)
                    {
                        Matrix3f mat = new Matrix3f();
                        Vector3f v = new Vector3f();
                        basic.get3x3(mat);

                        mat.getColumn(0, v); v.normalize(); mat.setColumn(0, v);
                        mat.getColumn(1, v); v.normalize(); mat.setColumn(1, v);
                        mat.getColumn(2, v); v.normalize(); mat.setColumn(2, v);

                        basic.set3x3(mat);
                    }

                    if (anchor.translate)
                    {
                        Vector3f t = new Vector3f();
                        basic.getTranslation(t);
                        basic.set(defaultMatrix);
                        basic.setTranslation(t);
                    }
                }
            }

            return basic;
        }

        return defaultMatrix;
    }

    /**
     * IRLights resolves point/spotlight poses from {@link FormRenderingContext#world}.
     * Rebuild the actor's absolute world matrix stack in true world coordinates
     * (independent of any render camera or viewport) so lights remain fixed in place.
     */
    private static void syncIrlAbsoluteWorldMatrix(FormRenderingContext formContext, FilmControllerContext context, IEntity entity, boolean relative, float transition)
    {
        if (formContext == null || formContext.world == null || entity == null || context == null)
        {
            return;
        }

        if (relative)
        {
            return;
        }

        Vector3d position = Vectors.TEMP_3D.set(
            Lerps.lerp(entity.getPrevX(), entity.getX(), transition),
            Lerps.lerp(entity.getPrevY(), entity.getY(), transition),
            Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition)
        );

        Form form = entity.getForm();
        Matrix4f defaultMatrix = getMatrixForRenderWithRotation(entity, 0D, 0D, 0D, transition);
        Matrix4f worldTarget = null;

        if (context.entities != null && form != null && form.anchor.get() != null)
        {
            Pair<Matrix4f, Float> pair = getTotalMatrix(context.entities, form.anchor.get(), defaultMatrix, 0D, 0D, 0D, transition, 0);

            worldTarget = pair.a;
        }

        if (worldTarget != null)
        {
            Vector3f v = worldTarget.getTranslation(new Vector3f());
            Vector3f v2 = defaultMatrix.getTranslation(new Vector3f());

            position.x += v.x - v2.x;
            position.y += v.y - v2.y;
            position.z += v.z - v2.z;
        }
        else
        {
            worldTarget = defaultMatrix;
        }

        if (form != null)
        {
            applyLookAt(context, form, position, worldTarget);
        }

        if (context.localGroupTransform != null)
        {
            worldTarget.mul(context.localGroupTransform);
        }

        formContext.world.peek().getPositionMatrix().set(worldTarget);
        formContext.world.peek().getNormalMatrix().set(new Matrix3f(worldTarget));
    }

    public static Matrix4f getMatrixForRenderWithRotation(IEntity entity, double cameraX, double cameraY, double cameraZ, float tickDelta)
    {
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), tickDelta) - cameraX;
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), tickDelta) - cameraY;
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), tickDelta) - cameraZ;

        Matrix4f matrix = new Matrix4f();

        float bodyYaw = (float) Lerps.lerpYaw(entity.getPrevBodyYaw(), entity.getBodyYaw(), tickDelta);

        matrix.translate((float) x, (float) y, (float) z);
        matrix.rotateY(MathUtils.toRad(-bodyYaw));
        /* Float death_time tip (film sample or actor keyframes / combat). */
        FormDeathTilt.apply(matrix, entity, entity.getForm(), tickDelta);

        return matrix;
    }

    /**
     * Stub-replay name tags — mirror vanilla {@code EntityRenderer.renderLabelIfPresent}:
     * standing = SEE_THROUGH fade behind walls + NORMAL on top; sneaking = NORMAL only
     * (hidden when occluded). Do not disable depth test (that caused permanent x-ray).
     */
    private static void renderNameTag(IEntity entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        boolean seeThrough = !entity.isSneaking();
        float hitboxH = (float) entity.getPickingHitbox().h + (entity.isSneaking() ? 0.25F : 0.5F);

        matrices.push();
        matrices.translate(0F, hitboxH, 0F);
        matrices.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
        matrices.scale(0.025F, -0.025F, 0.025F);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        float opacity = MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F);
        int background = (int) (opacity * 255F) << 24;
        float h = (float) (-textRenderer.getWidth(text) / 2);
        /* Same translucent white vanilla uses for the see-through / background pass. */
        int translucentColor = 0x20FFFFFF;
        TextRenderer.TextLayerType firstLayer = seeThrough
            ? TextRenderer.TextLayerType.SEE_THROUGH
            : TextRenderer.TextLayerType.NORMAL;

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

        textRenderer.draw(text, h, 0, translucentColor, false, matrix4f, consumers, firstLayer, background, light);
        consumers.draw();

        if (seeThrough)
        {
            textRenderer.draw(text, h, 0, -1, false, matrix4f, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
            consumers.draw();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    /* Film controller */

    public BaseFilmController(Film film)
    {
        this.film = film;
    }

    public IntObjectMap<IEntity> getEntities()
    {
        return this.entities;
    }

    public void togglePause()
    {
        this.paused = !this.paused;
    }

    public void createEntities()
    {
        this.entities.clear();
        this.replayMap.clear();
        this.lastStepSoundTicks.clear();
        this.lastItemUseParticleTicks.clear();
        this.lastSeenActorEntityIds.clear();

        if (this.film == null)
        {
            return;
        }

        int i = 0;
        /* Apply the playhead tick — not 0. Actor toggle calls this and tick 0
         * would wipe equipment/pose that only exists on later keyframes. */
        int tick = this.getTick();

        for (Replay replay : this.film.replays.getList())
        {
            MobCemPoseCapture.syncReplay(replay);
            this.replayMap.put(replay.uuid.get(), replay);
            this.replayMap.put(replay.getId(), replay);

            if (this.isReplayEnabled(replay))
            {
                World world = MinecraftClient.getInstance().world;
                IEntity entity = new StubEntity(world);
                int replayTick = replay.getTick(tick);

                entity.setForm(FormUtils.copy(replay.form.get()));
                replay.keyframes.apply(replayTick, entity);
                entity.setPrevX(entity.getX());
                entity.setPrevY(entity.getY());
                entity.setPrevZ(entity.getZ());

                entity.setPrevYaw(entity.getYaw());
                entity.setPrevHeadYaw(entity.getHeadYaw());
                entity.setPrevPitch(entity.getPitch());
                entity.setPrevBodyYaw(entity.getBodyYaw());

                this.entities.put(i, entity);
            }

            i += 1;
        }

        RegisterFilmSimulationEvent.postSetup(this);
    }

    public abstract Map<String, Integer> getActors();

    public abstract int getTick();

    /**
     * Live world entity used when a replay has Actor mode on ({@link ActorEntity}
     * or first-person player morph). {@code null} when the actor is not spawned yet.
     */
    public IEntity getPhysicalActorEntity(Replay replay)
    {
        if (replay == null || !replay.actor.get())
        {
            return null;
        }

        Map<String, Integer> actors = this.getActors();

        if (actors == null || MinecraftClient.getInstance().world == null)
        {
            return null;
        }

        Integer entityId = actors.get(replay.getId());

        if (entityId == null)
        {
            return null;
        }

        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

        if (anEntity instanceof ActorEntity actor)
        {
            return actor.getEntity();
        }

        if (anEntity instanceof PlayerEntity player)
        {
            Morph morph = Morph.getMorph(player);

            return morph == null ? null : morph.entity;
        }

        return null;
    }

    /**
     * Entity whose form/pose should drive gizmos, bone picking and highlights.
     * Prefers the physical actor when Actor mode is on; otherwise the stub.
     */
    public IEntity getRenderEntity(Replay replay, IEntity stub)
    {
        IEntity physical = this.getPhysicalActorEntity(replay);

        return physical != null ? physical : stub;
    }

    /**
     * Actor-mode replays must not fall back to the stub for picking/highlight after
     * combat death — that left a standing invisible ghost (yellow form / blue limbs).
     * Also blocks picking for the whole death animation once {@code deathTime} starts,
     * including keyframed {@code death_time} (scrubbed death without combat HP).
     */
    public boolean isActorPickingBlocked(Replay replay)
    {
        if (replay == null || !replay.actor.get())
        {
            return false;
        }

        Map<String, Integer> actors = this.getActors();

        if (actors == null || MinecraftClient.getInstance().world == null)
        {
            return true;
        }

        Integer entityId = actors.get(replay.getId());

        if (entityId == null)
        {
            return true;
        }

        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

        if (!(anEntity instanceof LivingEntity living) || living.isRemoved())
        {
            return true;
        }

        if (living.isDead() || living.getHealth() <= 0F || living.deathTime > 0)
        {
            return true;
        }

        /* Keyframed death tip without combat death — same gizmo/pick block so FormDeathTilt
         * cannot detach the bone gizmo while the actor is still "alive" on HP. */
        if (replay.keyframes != null && !replay.keyframes.deathTime.isEmpty())
        {
            float propertyTick = replay.getTick(this.getTick());

            if (replay.keyframes.deathTime.interpolate(propertyTick).floatValue() > 0F)
            {
                return true;
            }
        }

        return false;
    }

    public boolean hasFinished()
    {
        return false;
    }

    public void update()
    {
        this.updateEntities(this.getTick());
    }

    /**
     * Whether actor replays should keep receiving playback motion. When false,
     * live actors hold still so vanilla limb swing can settle naturally.
     */
    protected boolean isActorPlaybackActive()
    {
        return !this.paused;
    }

    /**
     * How many natural-animation ticks to advance on actor forms while the
     * timeline is parked (film editor scrub). World films return 0.
     */
    protected int getPausedAnimationAdvanceSteps()
    {
        return 0;
    }

    /**
     * Film tick before the current scrub step (editor only).
     */
    protected int getPausedAnimationFromTick()
    {
        return this.getTick();
    }

    /**
     * While timeline-paused with actor-pause-animations: freeze limb/form clocks
     * to this film tick. Scrubbing also snaps the body to keyframes (deterministic
     * like alt-hover). Pause-in-place must not re-apply keyframe position — during
     * play the visible ActorEntity follows ActionPlayer (with the editor's
     * {@code cursor+1} convention), so snapping to {@code cursor} jumps ~1 tick.
     */
    private void applyPausedActorNaturalMotion(ActorEntity actor, Replay replay, int toReplayTick)
    {
        ReplayKeyframes keyframes = replay.keyframes;

        if (keyframes == null)
        {
            return;
        }

        boolean mounted = actor.hasVehicle();
        int steps = this.getPausedAnimationAdvanceSteps();

        if (steps > 0)
        {
            double x = keyframes.x.interpolate(toReplayTick);
            double y = keyframes.y.interpolate(toReplayTick);
            double z = keyframes.z.interpolate(toReplayTick);

            actor.setPosition(x, y, z);
            actor.prevX = x;
            actor.prevY = y;
            actor.prevZ = z;
            actor.lastRenderX = x;
            actor.lastRenderY = y;
            actor.lastRenderZ = z;
        }
        else
        {
            /* Hold the pose playback already showed; only kill render interpolation. */
            double x = actor.getX();
            double y = actor.getY();
            double z = actor.getZ();

            actor.prevX = x;
            actor.prevY = y;
            actor.prevZ = z;
            actor.lastRenderX = x;
            actor.lastRenderY = y;
            actor.lastRenderZ = z;
        }

        actor.prevYaw = actor.getYaw();
        actor.prevHeadYaw = actor.headYaw;
        actor.prevBodyYaw = actor.bodyYaw;
        actor.prevPitch = actor.getPitch();
        actor.setVelocity(0D, 0D, 0D);

        if (steps > 0)
        {
            actor.applyTimelineLimbPhase(keyframes, toReplayTick, mounted);
            actor.syncTimelineFormTick(toReplayTick);
        }
        else
        {
            /* Pause-in-place: freeze whatever play already showed. Re-binding age or
             * limb phase to the playhead restarts BOBJ/emoticon ActionPlayback. */
            actor.anchorTimelinePauseState(toReplayTick);
        }
    }

    protected void updateEntities(int ticks)
    {
        List<Replay> replays = this.film.replays.getList();

        MorphMountSync.assignMountTargets(this.entities, replays, ticks);

        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                int replayTick = replay.getTick(ticks);

                this.applyReplay(replay, replayTick, entity);
            }
        }

        MorphMountSync.syncMountedState(this.entities, replays, ticks);

        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                int replayTick = replay.getTick(ticks);

                this.updateEntityAndForm(entity, replayTick);

                boolean spawned = false;
                boolean mounted = entity.getMountTarget() != null;

                Map<String, Integer> actors = this.getActors();

                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());

                    if (entityId == null)
                    {
                        this.lastSeenActorEntityIds.remove(replay.getId());
                    }
                    else
                    {
                        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                        if (anEntity instanceof ActorEntity actor)
                        {
                            /* Record only once the entity exists — otherwise a late spawn
                             * after the actors packet would skip the one-shot position snap. */
                            Integer previousEntityId = this.lastSeenActorEntityIds.put(replay.getId(), entityId);
                            boolean actorEntityJustBound = !Objects.equals(previousEntityId, entityId);

                            /* HP syncs on scrub revive; deathTime does not — clear leftover corpse visuals. */
                            actor.clearStaleCombatDeathIfAlive();

                            boolean combatDead = actor.isDead() || actor.getHealth() <= 0F || actor.deathTime > 0;

                            if (combatDead)
                            {
                                /* Keep server combat-death pose; do not overlay alive keyframes. */
                                actor.updateTick(replayTick);
                                actor.setPauseNaturalAnimations(false);
                                actor.setVelocity(0D, 0D, 0D);
                                actor.syncNameTag(replay);
                                actor.syncShadow(replay.shadow.get(), BaseFilmController.resolveShadowSettings(replay, replayTick));

                                /* Same as live control: the puppeteer owns swings / client clips. */
                                if (this.shouldEmitReplayMotionFx(entity) && this.shouldApplyClientActions(entity))
                                {
                                    replay.applyClientActions(replayTick, new MCEntity(anEntity), this.film);
                                }

                                spawned = true;
                            }
                            else
                            {
                                boolean controlling = !this.shouldEmitReplayMotionFx(entity);
                                boolean timelineAnims = BBSSettings.editorActorPauseAnimations != null
                                    && BBSSettings.editorActorPauseAnimations.get();
                                boolean pauseAnims = timelineAnims
                                    && !this.isActorPlaybackActive()
                                    && !controlling;

                                actor.updateTick(replayTick);
                                actor.setPauseNaturalAnimations(pauseAnims);

                                /* IEntity already has mount rotation applied by MorphMountSync */
                                actor.setYaw(entity.getYaw());
                                actor.setHeadYaw(entity.getHeadYaw());
                                actor.setBodyYaw(entity.getBodyYaw());
                                actor.setPitch(entity.getPitch());
                                /* Actor-control: copy the live player's LimbAnimator (vanilla).
                                 * Playback: do not hard-copy stub limbs — that fights forward
                                 * coast velocity and snaps swing. LivingEntity.tick + server
                                 * applyFromKeyframes use updateLimbs(target, 0.4) toward the
                                 * same motion as the body (vanilla stop/accel ease).
                                 * Skip while a live hurt swing spike is active so procedural
                                 * damage uses the same limbSpeed amplification as vanilla. */
                                boolean syncLimbs = controlling
                                    && !actor.shouldPreserveLiveHurtLimbSwing();

                                ActorReplayStateSync.syncFromSource(actor, entity, syncLimbs);
                                /* Keep keyframed equipment on the visible ActorEntity — stub
                                 * already received applyReplay; without this, actor toggle can
                                 * show empty armor until the server respawns the actor. */
                                this.syncActorEquipmentFromStub(actor, entity);

                                if (controlling)
                                {
                                    /* Live item-use (bow pull, crossbow charge, eating) lives on
                                     * the player; flags-only sync leaves remaining use-time at 0. */
                                    this.syncActorItemUseFromSource(actor, entity);
                                }

                                /* Only gate vanilla sprint dust — do not clear sprinting (run anim). */
                                actor.setSuppressSprintParticles(controlling);

                                if (actorEntityJustBound)
                                {
                                    /* One-shot: Actor toggle / respawn can leave the body at an
                                     * old server pose while yaw/limbs already follow the stub.
                                     * Do not heal every tick — that fights walk velocity. */
                                    this.syncActorWorldPositionFromStub(actor, entity);
                                    actor.setVelocity(0D, 0D, 0D);
                                }

                                if (pauseAnims)
                                {
                                    this.applyPausedActorNaturalMotion(actor, replay, replayTick);
                                }
                                else if (controlling)
                                {
                                    /* Actor-control: keep the visible ActorEntity on the live
                                     * player pose (server ActionPlayer skips this replay via PUPPET).
                                     * Physics velocity stays zero so LivingEntity.tick does not ice-
                                     * slide on top of the snap. Pass the live velocity as an
                                     * animation hint so emoticons/procedural jump+fall still fire. */
                                    this.syncActorWorldPositionFromStub(actor, entity);
                                    actor.setVelocity(0D, 0D, 0D);
                                    actor.setAnimationVelocityHint(entity.getVelocity());
                                }
                                else if (!this.isActorPlaybackActive())
                                {
                                    actor.setVelocity(0D, 0D, 0D);

                                    /* Toggle off: clear sprint so emoticon/BOBJ leave run for
                                     * idle (unless legacy run-in-place is enabled). Limb swing is
                                     * left alone so procedural forms decay naturally. */
                                    if (!timelineAnims && BBSSettings.shouldSettleActorNaturalStopWhenPaused())
                                    {
                                        ActorReplayStateSync.settleNaturalStop(actor);
                                    }
                                }

                                /* Timeline-freeze skips ActorEntity.tick, so vanilla sprint dust
                                 * never runs — emit keyframe dust while the body clock is frozen. */
                                if (pauseAnims && this.shouldEmitReplayMotionFx(entity))
                                {
                                    this.spawnSprintParticles(replay, replayTick, actor, true);
                                }

                                /* Keep label in sync while editing name_tag in the film UI. */
                                actor.syncNameTag(replay);
                                /* Same shadow toggle / size / offset as stub film blobs. */
                                actor.syncShadow(replay.shadow.get(), BaseFilmController.resolveShadowSettings(replay, replayTick));
                                ActorEntityRenderer.updateShadowRadius(actor);

                                /* While actor-controlling (incl. viewport record), do not replay
                                 * timeline Swipe/etc. on the puppet — Outside uses exception for
                                 * the same idea. Other replays still play their clips. */
                                if (!controlling && this.shouldApplyClientActions(entity))
                                {
                                    replay.applyClientActions(replayTick, new MCEntity(anEntity), this.film);
                                }

                                spawned = true;
                            }
                        }
                        else if (anEntity instanceof PlayerEntity player)
                        {
                            if (!mounted)
                            {
                                double x = replay.keyframes.x.interpolate(replayTick);
                                double y = replay.keyframes.y.interpolate(replayTick);
                                double z = replay.keyframes.z.interpolate(replayTick);
                                double prevX = replay.keyframes.x.interpolate(replayTick - 1);
                                double prevY = replay.keyframes.y.interpolate(replayTick - 1);
                                double prevZ = replay.keyframes.z.interpolate(replayTick - 1);

                                player.setVelocity(x - prevX, y - prevY, z - prevZ);

                                if (this.shouldEmitReplayMotionFx(entity))
                                {
                                    this.spawnSprintParticles(replay, replayTick, player);
                                }
                            }
                            else
                            {
                                player.setVelocity(0D, 0D, 0D);
                            }

                            spawned = true;
                        }
                    }
                }

                if (!spawned && !mounted && this.shouldEmitReplayMotionFx(entity))
                {
                    World world = MinecraftClient.getInstance().world;
                    Form form = replay.form.get();
                    double width = form != null ? form.hitboxWidth.get() : 0.6D;

                    this.spawnReplayStepSound(replay, replayTick, world);
                    this.spawnSprintParticles(replay, replayTick, world, width);
                    this.spawnReplayItemUseParticles(replay, replayTick, entity, null);
                }
            }
        }

        RegisterFilmSimulationEvent.postTick(this, ticks);
    }

    public void updateEndWorld()
    {
        int ticks = this.getTick();

        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            List<Replay> replays = this.film.replays.getList();
            Replay replay = CollectionUtils.getSafe(replays, i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                int replayTick = replay.getTick(ticks);

                Map<String, Integer> actors = this.getActors();

                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());

                    if (entityId != null)
                    {
                        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                        if (anEntity instanceof PlayerEntity player)
                        {
                            double x = replay.keyframes.x.interpolate(replayTick);
                            double y = replay.keyframes.y.interpolate(replayTick);
                            double z = replay.keyframes.z.interpolate(replayTick);
                            boolean sneaking = replay.keyframes.sneaking.interpolate(replayTick) > 0;
                            boolean sprinting = replay.keyframes.sprinting.interpolate(replayTick) > 0;
                            boolean swimming = replay.keyframes.swimming.interpolate(replayTick) > 0;
                            boolean flying = replay.keyframes.flying.interpolate(replayTick) > 0;
                            boolean fallFlying = replay.keyframes.fallFlying.interpolate(replayTick) > 0;
                            boolean crawling = replay.keyframes.crawling.interpolate(replayTick) > 0;
                            boolean climbing = replay.keyframes.climbing.interpolate(replayTick) > 0;
                            boolean blocking = replay.keyframes.blocking.interpolate(replayTick) > 0;
                            boolean sleeping = replay.keyframes.sleeping.interpolate(replayTick) > 0;
                            boolean riptide = replay.keyframes.riptide.interpolate(replayTick) > 0;
                            boolean grounded = replay.keyframes.grounded.interpolate(replayTick) > 0;

                            Vec3d pos = player.getPos();
                            double dx = x - pos.x;
                            double dy = y - pos.y;
                            double dz = z - pos.z;
                            boolean shouldStep = !this.paused
                                && (BBSSettings.editorReplayStepSound == null || BBSSettings.editorReplayStepSound.get())
                                && (dx * dx + dy * dy + dz * dz) > 1.0E-8D;

                            if (shouldStep)
                            {
                                String replayId = replay.getId();
                                Integer lastTick = this.lastStepSoundTicks.get(replayId);

                                /* Same edge as spawnReplayStepSound: parked playhead must not
                                 * call move() every client tick (vanilla step spam). */
                                if (lastTick == null || lastTick.intValue() != replayTick)
                                {
                                    this.lastStepSoundTicks.put(replayId, replayTick);
                                    player.setOnGround(grounded);
                                    player.move(MovementType.SELF, new Vec3d(dx, dy, dz));
                                }
                            }

                            player.setPosition(x, y, z);

                            player.setSneaking(sneaking);
                            player.setSprinting(sprinting);
                            player.setSwimming(swimming);
                            player.getAbilities().flying = flying;
                            player.setFlag(7, fallFlying);
                            player.setFlag(4, riptide);

                            if (crawling)
                            {
                                player.setPose(EntityPose.SWIMMING);
                            }
                            else if (sleeping)
                            {
                                player.setPose(EntityPose.SLEEPING);
                            }

                            if (blocking)
                            {
                                player.setLivingFlag(1, true);
                            }

                            player.setOnGround(grounded);

                            if (player instanceof ClientPlayerEntityAccessor accessor)
                            {
                                accessor.bbs$setIsSneakingPose(sneaking);
                            }

                            if (player instanceof ClientPlayerEntity playerEntity)
                            {
                                playerEntity.input.sneaking = sneaking;
                            }

                            player.fallDistance = replay.keyframes.fall.interpolate(replayTick).floatValue();

                            if (replay.fp.get())
                            {
                                this.syncFirstPersonItemUse(player, entity);
                                this.spawnReplayItemUseParticles(replay, replayTick, entity, player);
                            }

                            /* Vanilla hurt camera / overlay read the local player's hurtTime.
                             * FP hides the stub body, so push keyframe (+ live) damage onto the
                             * bound player or shake never appears in first-person playback. */
                            int hurtTimer = entity.getHurtTimer();

                            if (BBSSettings.shouldKeepActorLiveHurtTime())
                            {
                                player.hurtTime = Math.max(player.hurtTime, hurtTimer);
                            }
                            else
                            {
                                player.hurtTime = hurtTimer;
                            }

                            if (player.hurtTime > 0 && player.maxHurtTime < player.hurtTime)
                            {
                                player.maxHurtTime = Math.max(10, player.hurtTime);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        entity.setAge(tick);
        entity.update();

        if (entity.getForm() != null)
        {
            entity.getForm().update(entity);
        }
    }

    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        replay.keyframes.apply(ticks, entity);
        replay.applyClientActions(ticks, entity, this.film);
    }

    private void syncActorEquipmentFromStub(ActorEntity actor, IEntity stub)
    {
        actor.equipStack(EquipmentSlot.MAINHAND, stub.getEquipmentStack(EquipmentSlot.MAINHAND));
        actor.equipStack(EquipmentSlot.OFFHAND, stub.getEquipmentStack(EquipmentSlot.OFFHAND));
        actor.equipStack(EquipmentSlot.HEAD, stub.getEquipmentStack(EquipmentSlot.HEAD));
        actor.equipStack(EquipmentSlot.CHEST, stub.getEquipmentStack(EquipmentSlot.CHEST));
        actor.equipStack(EquipmentSlot.LEGS, stub.getEquipmentStack(EquipmentSlot.LEGS));
        actor.equipStack(EquipmentSlot.FEET, stub.getEquipmentStack(EquipmentSlot.FEET));
    }

    /**
     * Push replay item-use onto the bound player so vanilla first-person
     * eating/drinking transforms can run while the replay stub body is hidden.
     */
    private void syncFirstPersonItemUse(PlayerEntity player, IEntity source)
    {
        Hand hand = source.getActiveHand();
        EquipmentSlot slot = hand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        ItemStack stack = source.getEquipmentStack(slot);

        ItemUseRenderState.syncItemUse(player, source, hand, stack);
    }

    /**
     * Copy live item-use remaining time onto the actor so vanilla item predicates
     * (bow pull, crossbow charge, trident) match the puppeteer.
     */
    private void syncActorItemUseFromSource(ActorEntity actor, IEntity source)
    {
        Hand hand = source.getActiveHand();
        EquipmentSlot slot = hand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        ItemStack stack = source.getEquipmentStack(slot);

        ItemUseRenderState.syncItemUse(actor, source, hand, stack);
    }

    /**
     * Copy world position from the client stub onto the visible actor (toggle bind /
     * actor-control). Not used every playback tick — continuous snaps fight walk velocity.
     */
    private void syncActorWorldPositionFromStub(ActorEntity actor, IEntity stub)
    {
        actor.setPosition(stub.getX(), stub.getY(), stub.getZ());
        actor.prevX = stub.getPrevX();
        actor.prevY = stub.getPrevY();
        actor.prevZ = stub.getPrevZ();
        actor.lastRenderX = stub.getPrevX();
        actor.lastRenderY = stub.getPrevY();
        actor.lastRenderZ = stub.getPrevZ();
    }

    private void spawnSprintParticles(Replay replay, int ticks, Entity entity)
    {
        this.spawnSprintParticles(replay, ticks, entity, false);
    }

    private void spawnSprintParticles(Replay replay, int ticks, Entity entity, boolean force)
    {
        if (entity == null)
        {
            return;
        }

        /* Prefer the visible body pose (actor hold can lag the playhead keyframe). */
        this.spawnSprintParticles(replay, ticks, entity.getWorld(), entity.getWidth(), force, entity);
    }

    private void spawnSprintParticles(Replay replay, int ticks, World world, double width)
    {
        this.spawnSprintParticles(replay, ticks, world, width, false, null);
    }

    private void spawnSprintParticles(Replay replay, int ticks, World world, double width, boolean force, Entity atEntity)
    {
        if (ExportParticleFreeze.isFrozen())
        {
            return;
        }

        if ((!force && !BBSSettings.editorReplaySprintParticles.get()) || replay == null || world == null)
        {
            return;
        }

        if (!this.isReplayVisible(replay, ticks))
        {
            return;
        }

        if (replay.keyframes.sprinting.interpolate(ticks) <= 0D)
        {
            return;
        }

        if (replay.keyframes.grounded.interpolate(ticks) <= 0D)
        {
            return;
        }

        double vX = replay.keyframes.vX.interpolate(ticks);
        double vZ = replay.keyframes.vZ.interpolate(ticks);

        if ((vX * vX + vZ * vZ) < 0.001D)
        {
            return;
        }

        double xPos = atEntity != null ? atEntity.getX() : replay.keyframes.x.interpolate(ticks);
        double yPos = atEntity != null ? atEntity.getY() : replay.keyframes.y.interpolate(ticks);
        double zPos = atEntity != null ? atEntity.getZ() : replay.keyframes.z.interpolate(ticks);

        BlockPos pos = BlockPos.ofFloored(xPos, yPos - 0.2D, zPos);

        if (world.isAir(pos))
        {
            return;
        }

        double x = xPos + (world.random.nextDouble() - 0.5D) * width;
        double y = yPos + 0.1D;
        double z = zPos + (world.random.nextDouble() - 0.5D) * width;

        world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(pos)), x, y, z, 0D, 0.1D, 0D);
    }

    private void spawnReplayItemUseParticles(Replay replay, int ticks, IEntity source, Entity atEntity)
    {
        if (this.paused || replay == null || source == null || !source.isParticlesEnabled())
        {
            return;
        }

        if (!this.isReplayVisible(replay, ticks))
        {
            return;
        }

        Hand hand = source.getActiveHand();
        EquipmentSlot slot = hand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        ItemStack stack = source.getEquipmentStack(slot);

        if (stack == null || stack.isEmpty())
        {
            return;
        }

        UseAction action = stack.getUseAction();

        if (action != UseAction.EAT && action != UseAction.DRINK)
        {
            return;
        }

        LivingEntity living = atEntity instanceof LivingEntity entity ? entity : null;
        int elapsed = ItemUseRenderState.getItemUseElapsed(source, living, stack);

        if (elapsed <= 0 || elapsed % 4 != 0)
        {
            return;
        }

        String replayId = replay.getId();
        Integer lastTick = this.lastItemUseParticleTicks.get(replayId);

        if (lastTick != null && lastTick.intValue() == ticks)
        {
            return;
        }

        this.lastItemUseParticleTicks.put(replayId, ticks);

        World world = atEntity != null ? atEntity.getWorld() : MinecraftClient.getInstance().world;

        if (world == null)
        {
            return;
        }

        /* Match LivingEntity.spawnItemParticles: local-space offset + velocity,
         * then rotate by pitch/yaw so crumbs fan out from the eating pose. */
        float pitch = atEntity != null ? atEntity.getPitch() : source.getPitch();
        float yaw = atEntity != null ? atEntity.getYaw() : source.getYaw();
        double originX = atEntity != null ? atEntity.getX() : source.getX();
        double originY = atEntity != null ? atEntity.getEyeY() : source.getY() + source.getEyeHeight();
        double originZ = atEntity != null ? atEntity.getZ() : source.getZ();
        ItemStackParticleEffect effect = new ItemStackParticleEffect(ParticleTypes.ITEM, stack.copy());

        for (int i = 0; i < 5; i++)
        {
            Vec3d velocity = new Vec3d(
                ((double) world.random.nextFloat() - 0.5D) * 0.1D,
                world.random.nextDouble() * 0.1D + 0.1D,
                0D
            );
            velocity = velocity.rotateX(-MathUtils.toRad(pitch));
            velocity = velocity.rotateY(-MathUtils.toRad(yaw));

            double localY = (double) (-world.random.nextFloat()) * 0.6D - 0.3D;
            Vec3d pos = new Vec3d(
                ((double) world.random.nextFloat() - 0.5D) * 0.3D,
                localY,
                0.6D
            );
            pos = pos.rotateX(-MathUtils.toRad(pitch));
            pos = pos.rotateY(-MathUtils.toRad(yaw));
            pos = pos.add(originX, originY, originZ);

            world.addParticle(effect, pos.x, pos.y, pos.z, velocity.x, velocity.y + 0.05D, velocity.z);
        }
    }

    /**
     * Whether film-pose motion FX (BBS keyframe sprint dust / step sounds) may emit
     * for this replay entity. Defaults to true; the film editor turns it off for the
     * entity currently under actor-control so dust is not sprayed at the parked pose.
     */
    protected boolean shouldEmitReplayMotionFx(IEntity entity)
    {
        return true;
    }

    /**
     * Whether timeline client action clips (swipe, etc.) may run for this entity.
     * Film editor suppresses one pass after soft-seeking back from a viewport record.
     */
    protected boolean shouldApplyClientActions(IEntity entity)
    {
        return true;
    }

    private void spawnReplayStepSound(Replay replay, int ticks, World world)
    {
        if (BBSSettings.editorReplayStepSound == null || !BBSSettings.editorReplayStepSound.get() || replay == null || world == null)
        {
            return;
        }

        if (this.paused)
        {
            return;
        }

        String replayId = replay.getId();
        Integer lastTick = this.lastStepSoundTicks.get(replayId);

        /* One evaluation per film tick (scrub once, play once; parked playhead = silence). */
        if (lastTick != null && lastTick.intValue() == ticks)
        {
            return;
        }

        this.lastStepSoundTicks.put(replayId, ticks);

        if (!this.isReplayVisible(replay, ticks))
        {
            return;
        }

        if (replay.keyframes.grounded.interpolate(ticks) <= 0D)
        {
            return;
        }

        /* Approximate vanilla stepping cadence while the timeline is advancing. */
        if ((ticks & 7) != 0)
        {
            return;
        }

        double vX = replay.keyframes.vX.interpolate(ticks);
        double vZ = replay.keyframes.vZ.interpolate(ticks);

        if ((vX * vX + vZ * vZ) < 0.01D)
        {
            return;
        }

        double xPos = replay.keyframes.x.interpolate(ticks);
        double yPos = replay.keyframes.y.interpolate(ticks);
        double zPos = replay.keyframes.z.interpolate(ticks);
        BlockPos pos = BlockPos.ofFloored(xPos, yPos - 0.2D, zPos);

        if (world.isAir(pos))
        {
            return;
        }

        var soundGroup = world.getBlockState(pos).getSoundGroup();

        world.playSound(
            xPos,
            yPos,
            zPos,
            soundGroup.getStepSound(),
            SoundCategory.PLAYERS,
            soundGroup.getVolume() * 0.15F,
            soundGroup.getPitch(),
            false
        );
    }

    public boolean isReplayVisible(Replay replay, int ticks)
    {
        if (!this.isReplayEnabled(replay))
        {
            return false;
        }

        if (!this.isReplayVisibleAt(replay, ticks))
        {
            return false;
        }

        if (!replay.group.get().isEmpty())
        {
            String[] groups = replay.group.get().split("/");

            for (String uuid : groups)
            {
                Replay groupReplay = this.replayMap.get(uuid);

                if (groupReplay != null)
                {
                    if (!this.isReplayEnabled(groupReplay))
                    {
                        return false;
                    }

                    int groupTick = groupReplay.getTick(this.getTick());

                    if (!this.isReplayVisibleAt(groupReplay, groupTick))
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean isReplayEnabled(Replay replay)
    {
        if (replay == null || !replay.enabled.get())
        {
            return false;
        }

        if (!replay.group.get().isEmpty())
        {
            String[] groups = replay.group.get().split("/");

            for (String uuid : groups)
            {
                Replay groupReplay = this.replayMap.get(uuid);

                if (groupReplay != null && !groupReplay.enabled.get())
                {
                    return false;
                }
            }
        }

        return true;
    }

    protected boolean isReplayVisibleAt(Replay replay, float tick)
    {
        /* Visible + Enabled (render) both gate groups, same as form.visible && form.render.
         * Empty channel = default on. KeyframeChannel already holds the first keyframe's
         * value before its tick — do not force true before the first keyframe. */
        return this.evaluateGroupBooleanChannel(replay, "visible", tick)
            && this.evaluateGroupBooleanChannel(replay, "render", tick);
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateGroupBooleanChannel(Replay replay, String key, float tick)
    {
        BaseValue value = replay.properties.get(key);

        if (!(value instanceof KeyframeChannel))
        {
            return true;
        }

        KeyframeChannel<Boolean> channel = (KeyframeChannel<Boolean>) value;

        if (channel.isEmpty())
        {
            return true;
        }

        Boolean result = channel.interpolate(tick, true);

        return result == null || result;
    }


    public void startRenderFrame(float transition)
    {
        for (Map.Entry<Integer, IEntity> entry : this.entities.entrySet())
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = this.film.replays.getList().get(i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.PROPERTIES))
            {
                continue;
            }

            float delta = this.getTransition(entity, transition);
            int tick = replay.getTick(this.getTick());

            /* Apply property */
            Form form1 = entity.getForm();
            replay.properties.resetProperties(form1);
            replay.properties.applyProperties(form1, tick + delta);

            if (MobCemPoseCapture.isActive(replay))
            {
                MobCemPoseCapture.applyPlaybackPose(replay, form1, entity, tick + delta);
            }

            Map<String, Integer> actors = this.getActors();

            if (actors != null)
            {
                Integer entityId = actors.get(replay.getId());

                if (entityId != null)
                {
                    Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                    if (anEntity instanceof ActorEntity actor)
                    {
                        Form form = actor.getForm();
                        replay.properties.resetProperties(form);
                        replay.properties.applyProperties(form, tick + delta);
                    }
                    else if (anEntity instanceof PlayerEntity player)
                    {
                        Morph morph = Morph.getMorph(player);

                        if (morph != null)
                        {
                            Form form = morph.getForm();
                            replay.properties.resetProperties(form);
                            replay.properties.applyProperties(form, tick + delta);
                        }

                        float yawHead = replay.keyframes.headYaw.interpolate(tick + delta).floatValue();
                        float yawBody = replay.keyframes.bodyYaw.interpolate(tick + delta).floatValue();
                        float pitch = replay.keyframes.pitch.interpolate(tick + delta).floatValue();

                        player.setYaw(yawHead);
                        player.setHeadYaw(yawHead);
                        player.setPitch(pitch);
                        player.setBodyYaw(yawBody);
                        player.prevYaw = yawHead;
                        player.prevHeadYaw = yawHead;
                        player.prevPitch = pitch;
                        player.prevBodyYaw = yawBody;
                    }
                }
            }
        }
    }

    protected float getTransition(IEntity entity, float transition)
    {
        return this.paused ? 0F : transition;
    }

    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        if (this.paused && (updateMode == UpdateMode.UPDATE))
        {
            return false;
        }

        return i != this.exception;
    }

    public void render(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        /* Farther entities first so translucency composites correctly. */
        List<Map.Entry<Integer, IEntity>> sorted = new ArrayList<>(this.entities.entrySet());
        Camera camera = context.camera();
        float transition = context.tickCounter().getTickDelta(false);

        sorted.sort(Comparator
            .comparing((Map.Entry<Integer, IEntity> entry) ->
                this.getEntityCameraDistanceSq(entry.getValue(), camera, transition)
            ).reversed()
            .thenComparing(Map.Entry::getKey)
        );

        for (Map.Entry<Integer, IEntity> entry : sorted)
        {
            int i = entry.getKey();
            IEntity entity = entry.getValue();
            Replay replay = this.film.replays.getList().get(i);

            if (!this.canUpdate(i, replay, entity, UpdateMode.RENDER))
            {
                continue;
            }

            this.renderEntity(context, replay, entity, i);
        }

        RegisterFilmSimulationEvent.postRender(this, context);
    }

    private double getEntityCameraDistanceSq(IEntity entity, Camera camera, float transition)
    {
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition);
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);
        double dx = x - camera.getPos().x;
        double dy = y - camera.getPos().y;
        double dz = z - camera.getPos().z;

        return dx * dx + dy * dy + dz * dz;
    }

    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity, int index)
    {
        if (!replay.actor.get())
        {
            int replayTick = replay.getTick(this.getTick());

            if (!this.isReplayVisible(replay, replayTick))
            {
                return;
            }

            FilmControllerContext filmContext = getFilmControllerContext(context, replay, entity);

            filmContext.transition = getTransition(entity, context.tickCounter().getTickDelta(false));

            filmContext.stack.push();

            try
            {
                if (!this.applyGroupProperties(replay, filmContext))
                {
                    return;
                }

                renderEntity(filmContext);
            }
            finally
            {
                filmContext.stack.pop();
            }
        }
    }

    protected Replay getGroupPivot(String groupUuid)
    {
        for (Replay replay : this.film.replays.getList())
        {
            if (replay.group.get().contains(groupUuid))
            {
                return replay;
            }
        }

        return null;
    }

    protected boolean applyGroupProperties(Replay replay, FilmControllerContext context)
    {
        if (replay.group.get().isEmpty())
        {
            return true;
        }

        String[] groups = replay.group.get().split("/");
        int finalColor = Colors.WHITE;
        Matrix4f localTransform = new Matrix4f().identity();
        PaintSettings groupPaint = null;
        GlowSettings groupGlow = null;
        Color groupColorGrade = null;
        Illusion groupIllusion = null;
        boolean groupShadowSize = false;
        boolean groupShadowOpacity = false;
        float shadowRadiusX = context.shadowRadiusX;
        float shadowRadiusZ = context.shadowRadiusZ;
        float shadowOpacity = context.shadowOpacity;
        float shadowOffsetX = context.shadowOffsetX;
        float shadowOffsetY = context.shadowOffsetY;
        float shadowOffsetZ = context.shadowOffsetZ;

        for (String uuid : groups)
        {
            Replay groupReplay = this.replayMap.get(uuid);

            if (groupReplay != null)
            {
                if (!groupReplay.enabled.get())
                {
                    return false;
                }

                double tick = groupReplay.getTick(this.getTick()) + context.transition;

                if (!this.isReplayVisibleAt(groupReplay, (float) tick))
                {
                    return false;
                }

                BaseValue colorValue = groupReplay.properties.get("color");

                if (colorValue instanceof KeyframeChannel)
                {
                    KeyframeChannel<Color> color = (KeyframeChannel<Color>) colorValue;

                    if (!color.isEmpty())
                    {
                        int groupColor = color.interpolate((float) tick).getARGBColor();
                        finalColor = this.mulColors(finalColor, groupColor);
                    }
                }

                Transform groupTransform = this.getGroupTransform(groupReplay, (float) tick);

                if (!groupTransform.isDefault())
                {
                    Matrix4f local = new Matrix4f();

                    /* Keep group translation on the mesh matrix only — never on the render
                     * stack — so entity shadows stay at the replay world position. */
                    local.translate(groupTransform.translate.x, groupTransform.translate.y, groupTransform.translate.z);

                    if (groupTransform.pivot.x != 0F || groupTransform.pivot.y != 0F || groupTransform.pivot.z != 0F)
                    {
                        local.translate(groupTransform.pivot);
                    }

                    local.rotateZ(groupTransform.rotate.z);
                    local.rotateY(groupTransform.rotate.y);
                    local.rotateX(groupTransform.rotate.x);
                    local.rotateZ(groupTransform.rotate2.z);
                    local.rotateY(groupTransform.rotate2.y);
                    local.rotateX(groupTransform.rotate2.x);
                    local.scale(groupTransform.scale);

                    if (groupTransform.pivot.x != 0F || groupTransform.pivot.y != 0F || groupTransform.pivot.z != 0F)
                    {
                        local.translate(-groupTransform.pivot.x, -groupTransform.pivot.y, -groupTransform.pivot.z);
                    }

                    localTransform.mul(local);
                }

                PaintSettings paint = this.getGroupPaintSettings(groupReplay, (float) tick);

                if (paint != null)
                {
                    groupPaint = groupPaint == null ? paint : this.mergePaintSettings(groupPaint, paint);
                }

                GlowSettings glow = this.getGroupGlowSettings(groupReplay, (float) tick);

                if (glow != null)
                {
                    groupGlow = groupGlow == null ? glow : this.mergeGlowSettings(groupGlow, glow);
                }

                Color grade = this.getGroupColorGrade(groupReplay, (float) tick);

                if (grade != null)
                {
                    groupColorGrade = groupColorGrade == null ? grade : this.mergeColorGrade(groupColorGrade, grade);
                }

                Illusion illusion = this.getGroupIllusion(groupReplay, (float) tick);

                if (illusion != null)
                {
                    groupIllusion = illusion;
                }

                if (!groupReplay.keyframes.shadowSize.isEmpty())
                {
                    ShadowSettings size = groupReplay.keyframes.shadowSize.interpolate((float) tick);

                    if (size != null)
                    {
                        /* Additive vs form shadow: group 0.5 / 0 offset is identity. */
                        shadowRadiusX = Math.max(0F, shadowRadiusX + (size.widthX - 0.5F));
                        shadowRadiusZ = Math.max(0F, shadowRadiusZ + (size.widthZ - 0.5F));
                        shadowOffsetX += size.offsetX;
                        shadowOffsetY += size.offsetY;
                        shadowOffsetZ += size.offsetZ;
                        groupShadowSize = true;
                    }
                }

                if (!groupReplay.keyframes.shadowOpacity.isEmpty())
                {
                    Double opacity = groupReplay.keyframes.shadowOpacity.interpolate((float) tick);

                    if (opacity != null)
                    {
                        /* Multiply so group 1 keeps the form opacity unchanged. */
                        shadowOpacity *= MathUtils.clamp(opacity.floatValue(), 0F, 1F);
                        groupShadowOpacity = true;
                    }
                }
            }
        }

        if (finalColor != Colors.WHITE)
        {
            context.color(this.mulColors(context.color, finalColor));
        }

        if (!localTransform.equals(new Matrix4f().identity()))
        {
            context.localGroupTransform = localTransform;
        }

        if (groupShadowSize || groupShadowOpacity)
        {
            context.shadow(true, shadowRadiusX, shadowRadiusZ, shadowOpacity, shadowOffsetX, shadowOffsetY, shadowOffsetZ);
        }

        context.groupPaint = groupPaint;
        context.groupGlow = groupGlow;
        context.groupColorGrade = groupColorGrade;
        context.groupIllusion = groupIllusion;

        return true;
    }

    private Transform getGroupTransform(Replay groupReplay, float tick)
    {
        Transform transform = new Transform();

        BaseValue transformValue = groupReplay.properties.get("transform");

        if (transformValue instanceof KeyframeChannel)
        {
            KeyframeChannel<Transform> channel = (KeyframeChannel<Transform>) transformValue;

            if (!channel.isEmpty())
            {
                transform.copy(channel.interpolate(tick));
            }
        }

        this.applyGroupTransformOverlay(transform, groupReplay, "transform_overlay", tick);

        for (int i = 0; i < BBSSettings.getTransformOverlaysCount(); i++)
        {
            this.applyGroupTransformOverlay(transform, groupReplay, "transform_overlay" + i, tick);
        }

        return transform;
    }

    private void applyGroupTransformOverlay(Transform transform, Replay groupReplay, String key, float tick)
    {
        BaseValue overlayValue = groupReplay.properties.get(key);

        if (overlayValue instanceof KeyframeChannel)
        {
            KeyframeChannel<Transform> channel = (KeyframeChannel<Transform>) overlayValue;

            if (!channel.isEmpty())
            {
                Transform overlay = channel.interpolate(tick);

                transform.translate.add(overlay.translate);
                transform.scale.add(overlay.scale).sub(1F, 1F, 1F);
                transform.rotate.add(overlay.rotate);
                transform.rotate2.add(overlay.rotate2);
                transform.pivot.add(overlay.pivot);
            }
        }
    }

    private PaintSettings getGroupPaintSettings(Replay groupReplay, float tick)
    {
        BaseValue paintValue = groupReplay.properties.get("paint");

        if (paintValue instanceof KeyframeChannel)
        {
            KeyframeChannel<PaintSettings> channel = (KeyframeChannel<PaintSettings>) paintValue;

            if (!channel.isEmpty())
            {
                PaintSettings settings = channel.interpolate(tick);

                return settings == null ? null : settings.copy();
            }
        }

        return null;
    }

    private GlowSettings getGroupGlowSettings(Replay groupReplay, float tick)
    {
        BaseValue glowValue = groupReplay.properties.get("glow");

        if (glowValue instanceof KeyframeChannel)
        {
            KeyframeChannel<GlowSettings> channel = (KeyframeChannel<GlowSettings>) glowValue;

            if (!channel.isEmpty())
            {
                GlowSettings settings = channel.interpolate(tick);

                return settings == null ? null : settings.copy();
            }
        }

        return null;
    }

    private Color getGroupColorGrade(Replay groupReplay, float tick)
    {
        BaseValue gradeValue = groupReplay.properties.get("color_grade");

        if (gradeValue instanceof KeyframeChannel)
        {
            KeyframeChannel<Color> channel = (KeyframeChannel<Color>) gradeValue;

            if (!channel.isEmpty())
            {
                Color grade = channel.interpolate(tick);

                return grade == null ? null : grade.copy();
            }
        }

        return null;
    }

    private Illusion getGroupIllusion(Replay groupReplay, float tick)
    {
        BaseValue illusionValue = groupReplay.properties.get("illusion");

        if (illusionValue instanceof KeyframeChannel)
        {
            KeyframeChannel<Illusion> channel = (KeyframeChannel<Illusion>) illusionValue;

            if (!channel.isEmpty())
            {
                Illusion illusion = channel.interpolate(tick);

                return illusion == null ? null : illusion.copy();
            }
        }

        return null;
    }

    private Color mergeColorGrade(Color base, Color overlay)
    {
        Color merged = base.copy();

        merged.brightness += overlay.brightness;
        merged.contrast += overlay.contrast;
        merged.hue += overlay.hue;
        merged.saturation += overlay.saturation;
        merged.brightnessTransform = overlay.brightnessTransform == null ? new EffectTransform() : overlay.brightnessTransform.copy();
        merged.contrastTransform = overlay.contrastTransform == null ? new EffectTransform() : overlay.contrastTransform.copy();
        merged.hueTransform = overlay.hueTransform == null ? new EffectTransform() : overlay.hueTransform.copy();
        merged.saturationTransform = overlay.saturationTransform == null ? new EffectTransform() : overlay.saturationTransform.copy();

        return merged;
    }

    private PaintSettings mergePaintSettings(PaintSettings base, PaintSettings overlay)
    {
        PaintSettings merged = base.copy();

        merged.r *= overlay.r;
        merged.g *= overlay.g;
        merged.b *= overlay.b;
        merged.intensity += overlay.intensity;
        merged.sync = merged.sync || overlay.sync;
        merged.shaderShadow = PaintSettings.resolveAutoShaderShadow(merged.intensity);

        return merged;
    }

    private GlowSettings mergeGlowSettings(GlowSettings base, GlowSettings overlay)
    {
        GlowSettings merged = base.copy();

        merged.r *= overlay.r;
        merged.g *= overlay.g;
        merged.b *= overlay.b;
        merged.intensity += overlay.intensity;
        merged.sync = merged.sync || overlay.sync;
        merged.paintOnly = merged.paintOnly || overlay.paintOnly;
        merged.radius = Math.max(merged.radius, overlay.radius);
        merged.width = Math.max(merged.width, overlay.width);
        merged.height = Math.max(merged.height, overlay.height);

        return merged;
    }

    private static void applyGroupPaintGlow(Form form, PaintSettings groupPaint, GlowSettings groupGlow)
    {
        if (groupPaint != null)
        {
            PaintSettings current = form.paintSettings.get().copy();

            current.r *= groupPaint.r;
            current.g *= groupPaint.g;
            current.b *= groupPaint.b;
            current.intensity += groupPaint.intensity;
            current.sync = current.sync || groupPaint.sync;
            current.shaderShadow = PaintSettings.resolveAutoShaderShadow(current.intensity);
            form.paintSettings.setRuntimeValue(current);
            /* Keep casting; paint.shaderShadow float is the Complementary flag only. */
            form.shaderShadow.setRuntimeValue(null);
        }

        if (groupGlow != null)
        {
            GlowSettings current = form.glowSettings.get().copy();

            current.r *= groupGlow.r;
            current.g *= groupGlow.g;
            current.b *= groupGlow.b;
            current.intensity += groupGlow.intensity;
            current.sync = current.sync || groupGlow.sync;
            current.paintOnly = current.paintOnly || groupGlow.paintOnly;
            current.radius = Math.max(current.radius, groupGlow.radius);
            current.width = Math.max(current.width, groupGlow.width);
            current.height = Math.max(current.height, groupGlow.height);
            form.glowSettings.setRuntimeValue(current);
        }
    }

    private static void applyGroupColorGrade(Form form, Color groupGrade)
    {
        if (groupGrade == null)
        {
            return;
        }

        BaseValue colorValue = form.get("color");

        if (!(colorValue instanceof ValueColor valueColor))
        {
            return;
        }

        Color runtime = valueColor.getRuntimeValue() instanceof Color runtimeColor
            ? runtimeColor
            : null;

        if (runtime == null)
        {
            Color base = valueColor.getOriginalValue();

            runtime = base == null ? new Color(1F, 1F, 1F, 1F) : base.copy();
            valueColor.setRuntimeValue(runtime);
        }

        runtime.brightness = groupGrade.brightness;
        runtime.contrast = groupGrade.contrast;
        runtime.hue = groupGrade.hue;
        runtime.saturation = groupGrade.saturation;
        runtime.brightnessTransform = groupGrade.brightnessTransform == null ? new EffectTransform() : groupGrade.brightnessTransform.copy();
        runtime.contrastTransform = groupGrade.contrastTransform == null ? new EffectTransform() : groupGrade.contrastTransform.copy();
        runtime.hueTransform = groupGrade.hueTransform == null ? new EffectTransform() : groupGrade.hueTransform.copy();
        runtime.saturationTransform = groupGrade.saturationTransform == null ? new EffectTransform() : groupGrade.saturationTransform.copy();
    }

    private static void applyGroupIllusion(Form form, Illusion groupIllusion)
    {
        if (groupIllusion == null)
        {
            return;
        }

        Illusion current = form.illusion.get();

        if (current == null || current.count <= 0)
        {
            form.illusion.setRuntimeValue(groupIllusion.copy());
        }
        else
        {
            form.illusionOverlay.setRuntimeValue(groupIllusion.copy());
        }
    }

    private int mulColors(int c1, int c2)
    {
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = (c1) & 0xFF;

        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = (c2) & 0xFF;

        int a = (a1 * a2) / 255;
        int r = (r1 * r2) / 255;
        int g = (g1 * g2) / 255;
        int b = (b1 * b2) / 255;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    protected FilmControllerContext getFilmControllerContext(WorldRenderContext context, Replay replay, IEntity entity)
    {
        float tick = replay.getTick(this.getTick()) + this.getTransition(entity, context.tickCounter().getTickDelta(false));
        ShadowSettings shadow = resolveShadowSettings(replay, tick);

        return FilmControllerContext.instance
            .setup(this.entities, entity, replay, context)
            .film(this.film)
            .propertyTick(tick)
            .filmTick(this.getTick())
            .shadow(replay.shadow.get(), shadow)
            .nameTag(replay.nameTag.get())
            .relative(replay.isCameraRelative());
    }

    /**
     * Interpolated replay shadow settings at {@code tick} (includes keyframes).
     * Always returns a fresh copy — factory interpolation reuses a shared instance.
     */
    public static ShadowSettings resolveShadowSettings(Replay replay, float tick)
    {
        ShadowSettings settings = new ShadowSettings(replay.shadowOpacity.get(), replay.shadowSize.get(), replay.shadowSizeZ.get());

        settings.offsetX = replay.shadowOffsetX.get();
        settings.offsetY = replay.shadowOffsetY.get();
        settings.offsetZ = replay.shadowOffsetZ.get();

        if (!replay.keyframes.shadowSize.isEmpty())
        {
            ShadowSettings size = replay.keyframes.shadowSize.interpolate(tick);

            if (size != null)
            {
                settings.widthX = Math.max(0F, size.widthX);
                settings.widthZ = Math.max(0F, size.widthZ);
                settings.offsetX = size.offsetX;
                settings.offsetY = size.offsetY;
                settings.offsetZ = size.offsetZ;
            }
        }

        if (!replay.keyframes.shadowOpacity.isEmpty())
        {
            settings.opacity = MathUtils.clamp(replay.keyframes.shadowOpacity.interpolate(tick).floatValue(), 0F, 1F);
        }

        settings.widthX = Math.max(0F, settings.widthX);
        settings.widthZ = Math.max(0F, settings.widthZ);
        settings.opacity = MathUtils.clamp(settings.opacity, 0F, 1F);

        return settings;
    }

    public static float resolveShadowSize(Replay replay, float tick)
    {
        return resolveShadowSettings(replay, tick).widthX;
    }

    public static float resolveShadowSizeZ(Replay replay, float tick)
    {
        return resolveShadowSettings(replay, tick).widthZ;
    }

    public static float resolveShadowOpacity(Replay replay, float tick)
    {
        return resolveShadowSettings(replay, tick).opacity;
    }

    public void shutdown()
    {
        RegisterFilmSimulationEvent.postShutdown(this);
    }

    public static enum UpdateMode
    {
        UPDATE, RENDER, PROPERTIES;
    }
}
