package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.Link;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Event allowing addons to register custom audio decoders
 * for additional sound formats (e.g. mp3, flac, qoa).
 */
public class RegisterAudioDecodersEvent
{
    @FunctionalInterface
    public interface AudioDecoder
    {
        public Wave decode(Link link, InputStream stream) throws Exception;
    }

    private static final Map<String, AudioDecoder> decoders = new HashMap<>();

    public void register(String extension, AudioDecoder decoder)
    {
        if (extension != null && decoder != null)
        {
            if (!extension.startsWith("."))
            {
                extension = "." + extension;
            }

            decoders.put(extension.toLowerCase(), decoder);
        }
    }

    public static Map<String, AudioDecoder> getDecoders()
    {
        return Collections.unmodifiableMap(decoders);
    }

    public static AudioDecoder getDecoder(String extension)
    {
        if (extension == null)
        {
            return null;
        }

        if (!extension.startsWith("."))
        {
            extension = "." + extension;
        }

        return decoders.get(extension.toLowerCase());
    }

    public static boolean hasDecoder(String extension)
    {
        return getDecoder(extension) != null;
    }
}
