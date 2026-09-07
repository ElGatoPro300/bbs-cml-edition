package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.ui.film.UIFilmRecorder;
import mchorse.bbs_mod.utils.clips.Clips;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Event allowing addons to observe video recording and film export lifecycles
 * (e.g. generating SRT subtitle files, notifying external encoding tools).
 */
public class RegisterVideoRecordingEvent
{
    @FunctionalInterface
    public interface StartRecordingListener
    {
        public void onStartRecording(String movieName, Path exportFolder, File filmAudioFile, int width, int height, int fps);
    }

    @FunctionalInterface
    public interface StopRecordingListener
    {
        public void onStopRecording(String movieName, Path exportFolder, File outputVideo);
    }

    @FunctionalInterface
    public interface FilmRecorderContextListener
    {
        public void onFilmRecorderContext(UIFilmRecorder recorder, Clips cameraClips, int loopStartTick);
    }

    private static final List<StartRecordingListener> startListeners = new ArrayList<>();
    private static final List<StopRecordingListener> stopListeners = new ArrayList<>();
    private static final List<FilmRecorderContextListener> contextListeners = new ArrayList<>();

    public void registerStart(StartRecordingListener listener)
    {
        if (listener != null)
        {
            startListeners.add(listener);
        }
    }

    public void registerStop(StopRecordingListener listener)
    {
        if (listener != null)
        {
            stopListeners.add(listener);
        }
    }

    public void registerContext(FilmRecorderContextListener listener)
    {
        if (listener != null)
        {
            contextListeners.add(listener);
        }
    }

    public static void postStart(String movieName, Path exportFolder, File filmAudioFile, int width, int height, int fps)
    {
        for (StartRecordingListener listener : startListeners)
        {
            try
            {
                listener.onStartRecording(movieName, exportFolder, filmAudioFile, width, height, fps);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postStop(String movieName, Path exportFolder, File outputVideo)
    {
        for (StopRecordingListener listener : stopListeners)
        {
            try
            {
                listener.onStopRecording(movieName, exportFolder, outputVideo);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postContext(UIFilmRecorder recorder, Clips cameraClips, int loopStartTick)
    {
        for (FilmRecorderContextListener listener : contextListeners)
        {
            try
            {
                listener.onFilmRecorderContext(recorder, cameraClips, loopStartTick);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
