package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;

import java.util.function.Supplier;

/**
 * Event allowing addons to register custom camera controllers
 * (such as orbit cameras, runner cameras, or cinematic viewport modes).
 */
public class RegisterCameraControllersEvent
{
    private final CameraController cameraController;

    public RegisterCameraControllersEvent(CameraController cameraController)
    {
        this.cameraController = cameraController;
    }

    public CameraController getCameraController()
    {
        return this.cameraController;
    }

    public void register(ICameraController controller)
    {
        if (controller != null && this.cameraController != null)
        {
            this.cameraController.add(controller);
        }
    }

    public void register(Supplier<ICameraController> factory)
    {
        if (factory != null && this.cameraController != null)
        {
            ICameraController controller = factory.get();

            if (controller != null)
            {
                this.cameraController.add(controller);
            }
        }
    }
}
