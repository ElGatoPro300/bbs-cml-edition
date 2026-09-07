package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.ArrayList;
import java.util.List;

/**
 * Event for addons to append custom UI elements/properties into Keyframe Factory inspectors.
 */
public class RegisterKeyframeFactoryUIEvent
{
    @FunctionalInterface
    public interface KeyframeFactoryUIAppender
    {
        public void onBuildFactoryUI(UIKeyframeFactory<?> factory, UIKeyframes editor, Keyframe<?> keyframe);
    }

    private static final List<KeyframeFactoryUIAppender> appenders = new ArrayList<>();

    public void register(KeyframeFactoryUIAppender appender)
    {
        if (appender != null)
        {
            appenders.add(appender);
        }
    }

    public static void post(UIKeyframeFactory<?> factory, UIKeyframes editor, Keyframe<?> keyframe)
    {
        for (KeyframeFactoryUIAppender appender : appenders)
        {
            try
            {
                appender.onBuildFactoryUI(factory, editor, keyframe);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
