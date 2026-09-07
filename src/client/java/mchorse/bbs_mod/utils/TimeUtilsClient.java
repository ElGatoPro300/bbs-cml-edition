package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

public class TimeUtilsClient
{
    public static void configure(UITrackpad element, int defaultValue)
    {
        int mode = BBSSettings.editorTimeMode == null ? 0 : BBSSettings.editorTimeMode.get();

        if (mode == 1)
        {
            element.values(0.1D, 0.05D, 0.25D).limit(defaultValue / 20D, Double.POSITIVE_INFINITY, false);
        }
        else if (mode == 2)
        {
            element.values(1.0D, 0.1D, 0.5D).limit(defaultValue / 20D * BBSSettings.videoSettings.frameRate.get(), Double.POSITIVE_INFINITY, false);
        }
        else
        {
            element.values(1.0D).limit(defaultValue, Double.POSITIVE_INFINITY, true);
        }
    }

    /** Clip tick field: allow sub-tick values (e.g. 1.5) in all time display modes. */
    public static void configureClipTick(UITrackpad element)
    {
        int mode = BBSSettings.editorTimeMode == null ? 0 : BBSSettings.editorTimeMode.get();
        int fps = Math.max(1, BBSRendering.getVideoFrameRate());
        double frameStep = 20D / fps;

        if (mode == 1)
        {
            element.values(0.05D, 0.01D, 0.25D).limit(0D, Double.POSITIVE_INFINITY, false);
        }
        else if (mode == 2)
        {
            element.values(1.0D, 0.1D, 0.5D).limit(0D, Double.POSITIVE_INFINITY, false);
        }
        else
        {
            element.values(1.0D, frameStep, 0.25D).limit(0D, Double.POSITIVE_INFINITY, false);
        }
    }
}
