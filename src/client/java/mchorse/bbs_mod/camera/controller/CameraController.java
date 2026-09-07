package mchorse.bbs_mod.camera.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.utils.MathUtils;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CameraController implements ICameraController
{
    public Camera camera = new Camera();
    private ICameraController current;
    private List<ICameraController> controllers = new ArrayList<>();

    public Vector3d getPosition()
    {
        return this.camera.position;
    }

    public float getYaw()
    {
        return MathUtils.toDeg(this.camera.rotation.y - MathUtils.PI);
    }

    public float getPitch()
    {
        return MathUtils.toDeg(this.camera.rotation.x);
    }

    public float getRoll()
    {
        return MathUtils.toDeg(this.camera.rotation.z);
    }

    public double getFOV()
    {
        return MathUtils.toDeg(this.camera.fov);
    }

    public void updateCurrent()
    {
        ICameraController current = null;

        for (ICameraController controller : this.controllers)
        {
            if (current == null)
            {
                current = controller;
            }
            else if (controller.getPriority() > current.getPriority())
            {
                current = controller;
            }
        }

        this.current = current;
    }

    public ICameraController getCurrent()
    {
        return this.current;
    }

    public void add(ICameraController controller)
    {
        this.controllers.add(controller);
        this.updateCurrent();
    }

    public void remove(Class clazz)
    {
        Iterator<ICameraController> it = this.controllers.iterator();

        while (it.hasNext())
        {
            if (it.next().getClass() == clazz)
            {
                it.remove();
            }
        }

        this.updateCurrent();
    }

    public ICameraController remove(ICameraController controller)
    {
        Iterator<ICameraController> it = this.controllers.iterator();
        ICameraController removed = null;

        while (it.hasNext())
        {
            ICameraController next = it.next();

            if (next == controller)
            {
                it.remove();

                removed = next;
            }
        }

        this.updateCurrent();

        return removed;
    }

    @Override
    public void update()
    {
        if (this.current != null)
        {
            this.current.update();
        }
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        if (this.current != null)
        {
            this.current.setup(camera, transition);
        }
    }

    public boolean has(ICameraController controller)
    {
        return this.controllers.contains(controller);
    }

    public boolean has(Class clazz)
    {
        for (ICameraController controller : this.controllers)
        {
            if (controller.getClass() == clazz)
            {
                return true;
            }
        }

        return false;
    }

    public void reset()
    {
        this.current = null;
    }
}