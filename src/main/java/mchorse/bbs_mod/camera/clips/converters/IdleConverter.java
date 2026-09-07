package mchorse.bbs_mod.camera.clips.converters;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;

public class IdleConverter
{
    public static final IClipConverter CONVERTER = (clip) ->
    {
        IdleClip idle = new IdleClip();

        idle.copy(clip);

        if (clip instanceof CameraClip)
        {
            ((CameraClip) clip).apply(new CameraClipContext().setup(clip.tick.get(), 0, 0), idle.position.get());
        }

        return idle;
    };
}