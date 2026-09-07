package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSSettings;
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
}