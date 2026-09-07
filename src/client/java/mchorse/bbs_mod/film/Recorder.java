package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.clips.overwrite.PathClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.MobCemPoseCapture;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Inventory;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;

public class Recorder extends WorldFilmController
{
    public ReplayKeyframes keyframes = new ReplayKeyframes("keyframes");
    public FormProperties properties = new FormProperties("properties");
    public Inventory inventory = new Inventory("inventory");
    public float hp;
    public float hunger;
    public int xpLevel;
    public float xpProgress;

    public Form lastForm;
    public Vector3d lastPosition;
    public Vector4f lastRotation;

    public int countdown;
    public final int initialTick;

    private final RecorderMobCapture mobCapture = new RecorderMobCapture();
    private final RecorderProjectileCapture projectileCapture = new RecorderProjectileCapture();

    public RecorderMobCapture getMobCapture()
    {
        return this.mobCapture;
    }

    public RecorderProjectileCapture getProjectileCapture()
    {
        return this.projectileCapture;
    }

    public static void renderCameraPreview(Position position, Camera camera, MatrixStack stack)
    {
        renderCameraPreview(position, camera, stack, 1F, 1F, 1F, 0.85F, true);
    }

    public static void renderCameraPreview(Position position, Camera camera, MatrixStack stack, float r, float g, float b, float a, boolean drawForward)
    {
        if (!BBSSettings.recordingOverlays.get())
        {
            return;
        }

        float x = (float) (position.point.x - camera.getPos().x);
        float y = (float) (position.point.y - camera.getPos().y);
        float z = (float) (position.point.z - camera.getPos().z);
        float fov = MathUtils.toRad(position.angle.fov);
        float aspect = BBSRendering.getVideoWidth() / (float) BBSRendering.getVideoHeight();
        float distance = 5.5F;
        float thickness = 0.025F;
        float halfHeight = (float) Math.tan(fov * 0.5F) * distance;
        float halfWidth = halfHeight * aspect;
        float yaw = MathUtils.toRad(position.angle.yaw + 180F);
        float pitch = MathUtils.toRad(position.angle.pitch);
        float fx = (float) (-Math.sin(yaw) * Math.cos(pitch));
        float fy = (float) (-Math.sin(pitch));
        float fz = (float) (Math.cos(yaw) * Math.cos(pitch));
        float rx = (float) Math.cos(yaw);
        float ry = 0F;
        float rz = (float) Math.sin(yaw);
        float rLen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);

        if (rLen < 0.0001F)
        {
            rx = 1F;
            ry = 0F;
            rz = 0F;
            rLen = 1F;
        }

        rx /= rLen;
        ry /= rLen;
        rz /= rLen;

        float ux = fy * rz - fz * ry;
        float uy = fz * rx - fx * rz;
        float uz = fx * ry - fy * rx;
        float uLen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);

        if (uLen < 0.0001F)
        {
            ux = 0F;
            uy = 1F;
            uz = 0F;
            uLen = 1F;
        }

        ux /= uLen;
        uy /= uLen;
        uz /= uLen;
        float roll = MathUtils.toRad(position.angle.roll);
        float cosRoll = (float) Math.cos(roll);
        float sinRoll = (float) Math.sin(roll);
        float rrx = rx * cosRoll + ux * sinRoll;
        float rry = ry * cosRoll + uy * sinRoll;
        float rrz = rz * cosRoll + uz * sinRoll;
        float uux = ux * cosRoll - rx * sinRoll;
        float uuy = uy * cosRoll - ry * sinRoll;
        float uuz = uz * cosRoll - rz * sinRoll;

        Vector4f topRight = frustumCorner(fx, fy, fz, rrx, rry, rrz, uux, uuy, uuz, distance, halfWidth, halfHeight);
        Vector4f topLeft = frustumCorner(fx, fy, fz, rrx, rry, rrz, uux, uuy, uuz, distance, -halfWidth, halfHeight);
        Vector4f bottomRight = frustumCorner(fx, fy, fz, rrx, rry, rrz, uux, uuy, uuz, distance, halfWidth, -halfHeight);
        Vector4f bottomLeft = frustumCorner(fx, fy, fz, rrx, rry, rrz, uux, uuy, uuz, distance, -halfWidth, -halfHeight);
        Vector4f forward = new Vector4f(fx * (distance + 100F), fy * (distance + 100F), fz * (distance + 100F), 1F);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        fillPreviewSegment(builder, stack, x, y, z, x + topRight.x, y + topRight.y, z + topRight.z, thickness, r, g, b, a);
        fillPreviewSegment(builder, stack, x, y, z, x + topLeft.x, y + topLeft.y, z + topLeft.z, thickness, r, g, b, a);
        fillPreviewSegment(builder, stack, x, y, z, x + bottomRight.x, y + bottomRight.y, z + bottomRight.z, thickness, r, g, b, a);
        fillPreviewSegment(builder, stack, x, y, z, x + bottomLeft.x, y + bottomLeft.y, z + bottomLeft.z, thickness, r, g, b, a);

        fillPreviewSegment(builder, stack, x + topRight.x, y + topRight.y, z + topRight.z, x + topLeft.x, y + topLeft.y, z + topLeft.z, thickness, r, g, b, a * 0.8F);
        fillPreviewSegment(builder, stack, x + topLeft.x, y + topLeft.y, z + topLeft.z, x + bottomLeft.x, y + bottomLeft.y, z + bottomLeft.z, thickness, r, g, b, a * 0.8F);
        fillPreviewSegment(builder, stack, x + bottomLeft.x, y + bottomLeft.y, z + bottomLeft.z, x + bottomRight.x, y + bottomRight.y, z + bottomRight.z, thickness, r, g, b, a * 0.8F);
        fillPreviewSegment(builder, stack, x + bottomRight.x, y + bottomRight.y, z + bottomRight.z, x + topRight.x, y + topRight.y, z + topRight.z, thickness, r, g, b, a * 0.8F);

        if (drawForward)
        {
            fillPreviewSegment(builder, stack, x, y, z, x + forward.x, y + forward.y, z + forward.z, thickness * 1.35F, 0F, 0.5F, 1F, 1F);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.enableDepthTest();
    }

    public static boolean sampleCameraPosition(Clips clips, int tick, float transition, Position output)
    {
        if (clips == null)
        {
            return false;
        }

        CameraClipContext context = new CameraClipContext();
        context.clips = clips;
        context.clipData.clear();
        context.setup(tick, transition);
        output.point.set(0, 0, 0);
        output.angle.set(0, 0, 0, 70);
        boolean hasClip = false;

        for (Clip clip : clips.getClips(tick))
        {
            context.apply(clip, output);
            hasClip = true;
        }

        context.currentLayer = 0;

        return hasClip;
    }

    public static void renderCameraPreviewTimeline(Clips clips, int tick, float transition, int duration, Position current, Camera camera, MatrixStack stack)
    {
        Clip active = findActiveCameraClip(clips, tick);
        int futureCount = Math.max(1, BBSSettings.recordingCameraPreviewFutureCount == null ? 3 : BBSSettings.recordingCameraPreviewFutureCount.get());
        int[] nextTicks = findNextPreviewTicks(clips, active, tick, duration, futureCount);

        renderCameraPreview(current, camera, stack, 1F, 1F, 1F, 0.9F, true);

        for (int i = 0; i < nextTicks.length; i++)
        {
            Position nextPosition = new Position();

            if (!sampleCameraPosition(clips, nextTicks[i], 0F, nextPosition))
            {
                continue;
            }

            float alpha = Math.max(0.28F, 0.78F - i * 0.2F);
            float r = i == 0 ? 0.2F : 1F;
            float g = i == 0 ? 1F : 0.6F;
            float b = i == 0 ? 0.2F : 0.08F;
            renderCameraPreview(nextPosition, camera, stack, r, g, b, alpha, false);
        }
    }

    private static int[] findNextPreviewTicks(Clips clips, Clip active, int tick, int duration, int maxCount)
    {
        int[] output = new int[maxCount];
        int count = 0;

        if (active == null)
        {
            int durationCap = Math.max(0, duration - 1);
            int searchStart = Math.max(0, tick + 1);

            while (count < maxCount)
            {
                Clip next = findNextCameraClipAfter(clips, searchStart);

                if (next == null)
                {
                    break;
                }

                int nextTick = Math.max(0, Math.min(next.tick.get(), durationCap));

                if (nextTick != tick)
                {
                    output[count++] = nextTick;
                }

                searchStart = next.tick.get() + 1;
            }

            return trimTicks(output, count);
        }

        if (active instanceof KeyframeClip keyframeClip)
        {
            int relative = Math.max(0, tick - active.tick.get());

            for (var channel : keyframeClip.channels)
            {
                for (Keyframe<Double> keyframe : channel.getKeyframes())
                {
                    int kfTick = Math.round(keyframe.getTick());

                    if (kfTick > relative)
                    {
                        count = insertTick(output, count, active.tick.get() + kfTick, tick);

                        if (count >= maxCount)
                        {
                            return trimTicks(output, count);
                        }
                    }
                }
            }
        }
        else if (active instanceof PathClip pathClip)
        {
            int points = pathClip.size();

            if (points > 1)
            {
                int localTick = Math.max(0, tick - active.tick.get());
                int durationTick = Math.max(1, active.duration.get());
                float progress = MathUtils.clamp(localTick / (float) durationTick, 0F, 1F);
                int currentPoint = Math.min(points - 1, (int) Math.floor(progress * (points - 1)));
                int maxPoint = Math.min(points - 1, currentPoint + maxCount);

                for (int nextPoint = currentPoint + 1; nextPoint <= maxPoint; nextPoint++)
                {
                    count = insertTick(output, count, active.tick.get() + pathClip.getTickForPoint(nextPoint), tick);
                }
            }
        }
        else
        {
            int durationCap = Math.max(0, duration - 1);
            int searchStart = active.tick.get() + active.duration.get();

            while (count < maxCount)
            {
                Clip next = findNextCameraClipAfter(clips, searchStart);

                if (next == null)
                {
                    break;
                }

                int nextTick = Math.max(0, Math.min(next.tick.get(), durationCap));

                if (nextTick != tick)
                {
                    output[count++] = nextTick;
                }

                searchStart = next.tick.get() + 1;
            }
        }

        return trimTicks(output, count);
    }

    private static int insertTick(int[] output, int count, int tickToInsert, int currentTick)
    {
        if (tickToInsert == currentTick)
        {
            return count;
        }

        for (int i = 0; i < count; i++)
        {
            if (output[i] == tickToInsert)
            {
                return count;
            }
        }

        if (count < output.length)
        {
            output[count] = tickToInsert;

            for (int i = count; i > 0; i--)
            {
                if (output[i] < output[i - 1])
                {
                    int temp = output[i - 1];
                    output[i - 1] = output[i];
                    output[i] = temp;
                }
            }

            return count + 1;
        }

        return count;
    }

    private static int[] trimTicks(int[] output, int count)
    {
        int[] result = new int[count];

        for (int i = 0; i < count; i++)
        {
            result[i] = output[i];
        }

        return result;
    }

    private static Clip findActiveCameraClip(Clips clips, int tick)
    {
        Clip active = null;

        for (Clip clip : clips.getClips(tick))
        {
            if (!isCameraClip(clip))
            {
                continue;
            }

            if (active == null || clip.layer.get() >= active.layer.get())
            {
                active = clip;
            }
        }

        return active;
    }

    private static Clip findNextCameraClipAfter(Clips clips, int tick)
    {
        Clip next = null;

        for (Clip clip : clips.get())
        {
            if (!isCameraClip(clip))
            {
                continue;
            }

            int clipStart = clip.tick.get();

            if (clipStart >= tick && (next == null || clipStart < next.tick.get()))
            {
                next = clip;
            }
        }

        return next;
    }

    private static boolean isCameraClip(Clip clip)
    {
        return clip instanceof IdleClip || clip instanceof DollyClip || clip instanceof PathClip || clip instanceof KeyframeClip;
    }

    private static Vector4f frustumCorner(float fx, float fy, float fz, float rx, float ry, float rz, float ux, float uy, float uz, float distance, float side, float up)
    {
        return new Vector4f(
            fx * distance + rx * side + ux * up,
            fy * distance + ry * side + uy * up,
            fz * distance + rz * side + uz * up,
            1F
        );
    }

    private static void fillPreviewSegment(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float r, float g, float b, float a)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance < 0.0001F)
        {
            return;
        }

        float nx = dx / distance;
        float ny = dy / distance;
        float nz = dz / distance;
        Quaternionf rotation = new Quaternionf().rotationTo(0F, 0F, 1F, nx, ny, nz);

        stack.push();
        stack.translate(x1, y1, z1);
        stack.multiply(rotation);
        Draw.fillBox(builder, stack, -thickness / 2F, -thickness / 2F, 0F, thickness / 2F, thickness / 2F, distance, r, g, b, a);
        stack.pop();
    }

    public Recorder(Film film, Form form, int replayId, int tick)
    {
        super(film);

        this.lastForm = FormUtils.copy(form);
        this.exception = replayId;
        this.tick = tick;
        this.countdown = TimeUtils.toTick(BBSSettings.recordingCountdown.get());
        this.initialTick = tick;

        /* WorldFilmController creates stubs in super() before this tick is set.
         * Rebuild once so other replays start recording from the same playhead. */
        this.createEntities();
    }

    public boolean hasNotStarted()
    {
        return this.countdown > 0;
    }

    public void update()
    {
        if (this.hasNotStarted())
        {
            this.countdown -= 1;

            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (this.lastPosition == null)
        {
            this.lastPosition = new Vector3d(player.getX(), player.getY(), player.getZ());
            this.lastRotation = new Vector4f(player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw());
            this.inventory.fromPlayer(player);

            this.hp = player.getHealth();
            this.hunger = player.getHungerManager().getSaturationLevel();
            this.xpLevel = player.experienceLevel;
            this.xpProgress = player.experienceProgress;
        }

        if (this.tick >= 0)
        {
            Morph morph = Morph.getMorph(player);

            this.mobCapture.ensurePlayerVehicleCaptured(this);
            this.keyframes.record(this.tick, morph.entity, null);
            Replay playerReplay = CollectionUtils.getSafe(this.film.replays.getList(), this.exception);

            if (playerReplay != null)
            {
                MobCemPoseCapture.syncReplay(playerReplay);

                if (MobCemPoseCapture.isActive(playerReplay))
                {
                    MobCemPoseCapture.recordPoseKeyframe(playerReplay, playerReplay.form.get(), morph.entity, this.tick, 0F);
                }
            }

            RecorderMobCapture.recordMountKeyframes(this.film.replays.getList(), 0, this.keyframes, morph.entity, this.tick);
            this.mobCapture.recordTick(this);
            this.projectileCapture.recordTick(this, this.mobCapture);
        }

        super.update();
    }

    public void render(WorldRenderContext context)
    {
        super.render(context);

        renderCameraPreview(this.position, context.camera(), context.matrixStack());
    }

    @Override
    public void shutdown()
    {
        Vector3d pos = this.lastPosition;

        if (pos != null)
        {
            Vector4f rot = this.lastRotation;

            PlayerUtils.teleport(pos.x, pos.y, pos.z, rot.z, rot.y);
            ClientNetwork.sendPlayerForm(this.lastForm);
        }

        super.shutdown();
    }
}
