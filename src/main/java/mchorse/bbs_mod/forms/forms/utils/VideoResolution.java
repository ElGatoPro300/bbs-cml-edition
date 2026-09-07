package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.forms.forms.VideoForm;

/**
 * Decode / display resolution presets for {@link VideoForm}.
 * {@code 0} means native (no artificial downscale). Other values are max long-side pixels.
 */
public final class VideoResolution
{
    public static final int NATIVE = 0;
    public static final int P1080 = 1920;
    public static final int P720 = 1280;
    public static final int P480 = 854;
    public static final int P360 = 640;
    public static final int P240 = 480;

    public static final int[] PRESETS = {
        NATIVE,
        P1080,
        P720,
        P480,
        P360,
        P240
    };

    private VideoResolution()
    {}

    public static int clampPreset(int value)
    {
        for (int preset : PRESETS)
        {
            if (preset == value)
            {
                return value;
            }
        }

        /* Migrate unknown / old values to nearest lower preset. */
        if (value <= 0)
        {
            return NATIVE;
        }

        int best = P240;

        for (int preset : PRESETS)
        {
            if (preset > 0 && preset <= value)
            {
                best = preset;
            }
        }

        return best;
    }

    public static int indexOf(int value)
    {
        int clamped = clampPreset(value);

        for (int i = 0; i < PRESETS.length; i++)
        {
            if (PRESETS[i] == clamped)
            {
                return i;
            }
        }

        return 0;
    }

    public static int fromIndex(int index)
    {
        if (index < 0 || index >= PRESETS.length)
        {
            return NATIVE;
        }

        return PRESETS[index];
    }

    /**
     * Scale width/height so the longer side is at most {@code maxLongSide}.
     * Returns null when no change is needed.
     */
    public static int[] fitLongSide(int width, int height, int maxLongSide)
    {
        if (maxLongSide <= 0 || width < 2 || height < 2)
        {
            return null;
        }

        int longSide = Math.max(width, height);

        if (longSide <= maxLongSide)
        {
            return null;
        }

        float scale = maxLongSide / (float) longSide;
        int w = Math.max(2, Math.round(width * scale) & ~1);
        int h = Math.max(2, Math.round(height * scale) & ~1);

        return new int[] {w, h};
    }

    /**
     * Hard decode cap for VideoForm FPS. Native / 1080 both decode at 720p max.
     * Lower presets keep their size. This is what keeps Minecraft render thread smooth.
     */
    public static int effectiveDecodeLongSide(int preset)
    {
        int clamped = clampPreset(preset);

        if (clamped <= 0 || clamped > P720)
        {
            return P720;
        }

        return clamped;
    }
}
