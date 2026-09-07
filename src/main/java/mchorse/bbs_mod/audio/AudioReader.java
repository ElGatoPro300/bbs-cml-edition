package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.audio.ogg.VorbisReader;
import mchorse.bbs_mod.audio.wav.WaveReader;
import mchorse.bbs_mod.events.register.RegisterAudioDecodersEvent;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;

import java.io.InputStream;
import java.util.Map;

public class AudioReader
{
    public static Wave read(AssetProvider provider, Link link) throws Exception
    {
        String pathLower = link.path.toLowerCase();

        RegisterAudioDecodersEvent.AudioDecoder customDecoder = null;

        for (Map.Entry<String, RegisterAudioDecodersEvent.AudioDecoder> entry : RegisterAudioDecodersEvent.getDecoders().entrySet())
        {
            if (pathLower.endsWith(entry.getKey()))
            {
                customDecoder = entry.getValue();
                break;
            }
        }

        if (!pathLower.endsWith(".wav") && !pathLower.endsWith(".ogg") && customDecoder == null)
        {
            return null;
        }

        /* System.out.println("Reading: " + link); */

        try (InputStream asset = provider.getAsset(link))
        {
            if (customDecoder != null)
            {
                return customDecoder.decode(link, asset);
            }
            else if (pathLower.endsWith(".wav"))
            {
                return new WaveReader().read(asset);
            }
            else if (pathLower.endsWith(".ogg"))
            {
                return VorbisReader.read(link, asset);
            }
        }

        throw new IllegalStateException("Given link " + link + " isn't a supported audio file!");
    }
}