package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.film.BaseFilmController;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Event providing film simulation lifecycle hooks (physics, cloth, ragdolls, particles)
 * executed in sync with film controllers.
 */
public class RegisterFilmSimulationEvent
{
    private static final List<Consumer<BaseFilmController>> setupListeners = new ArrayList<>();
    private static final List<BiConsumer<BaseFilmController, Integer>> tickListeners = new ArrayList<>();
    private static final List<BiConsumer<BaseFilmController, WorldRenderContext>> renderListeners = new ArrayList<>();
    private static final List<Consumer<BaseFilmController>> shutdownListeners = new ArrayList<>();

    public void registerSetup(Consumer<BaseFilmController> listener)
    {
        if (listener != null)
        {
            setupListeners.add(listener);
        }
    }

    public void registerTick(BiConsumer<BaseFilmController, Integer> listener)
    {
        if (listener != null)
        {
            tickListeners.add(listener);
        }
    }

    public void registerRender(BiConsumer<BaseFilmController, WorldRenderContext> listener)
    {
        if (listener != null)
        {
            renderListeners.add(listener);
        }
    }

    public void registerShutdown(Consumer<BaseFilmController> listener)
    {
        if (listener != null)
        {
            shutdownListeners.add(listener);
        }
    }

    public static void postSetup(BaseFilmController controller)
    {
        for (Consumer<BaseFilmController> listener : setupListeners)
        {
            try
            {
                listener.accept(controller);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postTick(BaseFilmController controller, int ticks)
    {
        for (BiConsumer<BaseFilmController, Integer> listener : tickListeners)
        {
            try
            {
                listener.accept(controller, ticks);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postRender(BaseFilmController controller, WorldRenderContext context)
    {
        for (BiConsumer<BaseFilmController, WorldRenderContext> listener : renderListeners)
        {
            try
            {
                listener.accept(controller, context);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postShutdown(BaseFilmController controller)
    {
        for (Consumer<BaseFilmController> listener : shutdownListeners)
        {
            try
            {
                listener.accept(controller);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
