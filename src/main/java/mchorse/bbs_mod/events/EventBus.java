package mchorse.bbs_mod.events;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus
{
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscribers = new HashMap<>();

    /**
     * Registers the given subscriber to receive events, including inherited @Subscribe methods.
     */
    public void register(Object subscriber)
    {
        Class<?> clazz = subscriber.getClass();

        while (clazz != null && clazz != Object.class)
        {
            for (Method method : clazz.getDeclaredMethods())
            {
                this.subscribe(subscriber, method);
            }

            clazz = clazz.getSuperclass();
        }
    }

    private void subscribe(Object subscriber, Method method)
    {
        if (method.isAnnotationPresent(Subscribe.class))
        {
            if (method.getParameterCount() != 1)
            {
                return;
            }

            this.subscribers
                .computeIfAbsent(method.getParameterTypes()[0], (c) -> new CopyOnWriteArrayList<>())
                .add(new Subscription(subscriber, method));
        }
    }

    /**
     * Posts the given event to the event bus.
     */
    public void post(Object event)
    {
        CopyOnWriteArrayList<Subscription> eventSubscribers = this.subscribers.get(event.getClass());

        if (eventSubscribers == null || eventSubscribers.isEmpty())
        {
            return;
        }

        for (Subscription subscription : eventSubscribers)
        {
            try
            {
                subscription.method.invoke(subscription.target, event);
            }
            catch (Exception ignored)
            {}
        }
    }
}
