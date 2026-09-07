package mchorse.bbs_mod.ui.morphing.camera;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class ImmersiveMorphingCameraController implements ICameraController
{
    private Supplier<UIModelRenderer> modelRenderer;

    public ImmersiveMorphingCameraController(Supplier<UIModelRenderer> modelRenderer)
    {
        this.modelRenderer = modelRenderer;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        UIModelRenderer renderer = this.modelRenderer.get();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        float bodyYaw = MathUtils.toRad(Lerps.lerp(player.prevBodyYaw, player.bodyYaw, transition));

        camera.position.set(player.prevX, player.prevY, player.prevZ);
        camera.position.lerp(new Vector3d(player.getPos().x, player.getPos().y, player.getPos().z), transition);
        camera.rotation.set(0, bodyYaw, 0);

        if (renderer == null)
        {
            Vector3f rotation = Matrices.rotation(0F, -bodyYaw);

            rotation.mul(2F);
            camera.position.add(rotation.x, rotation.y + 1F, rotation.z);
            camera.setFov(MinecraftClient.getInstance().options.getFov().getValue());
        }
        else
        {
            renderer.setupPosition();

            Camera rendererCamera = renderer.camera;

            Vector3f rotate = Matrices.rotate(new Vector3f().set(rendererCamera.position), 0F, -bodyYaw);

            camera.position.add(rotate);
            camera.rotation.add(rendererCamera.rotation);
            camera.fov = rendererCamera.fov;
        }
    }

    @Override
    public int getPriority()
    {
        return 100500;
    }

    @Override
    public void update()
    {}
}