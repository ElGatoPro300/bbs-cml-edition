package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Event allowing addons to inject custom property values/channels into Forms
 * (e.g. smear frames, motion lines, extra constraints) without bytecode mixins.
 */
public class RegisterFormChannelsEvent
{
    private static final Map<Class<? extends Form>, List<Consumer<? extends Form>>> formListeners = new HashMap<>();
    private static final List<Consumer<ModelForm>> modelFormListeners = new ArrayList<>();
    private static final List<Consumer<Form>> allFormListeners = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public <T extends Form> void register(Class<T> formClass, Consumer<T> listener)
    {
        if (formClass != null && listener != null)
        {
            formListeners.computeIfAbsent(formClass, (k) -> new ArrayList<>()).add((Consumer<? extends Form>) listener);
        }
    }

    public void registerModelForm(Consumer<ModelForm> listener)
    {
        if (listener != null)
        {
            modelFormListeners.add(listener);
        }
    }

    public void registerAll(Consumer<Form> listener)
    {
        if (listener != null)
        {
            allFormListeners.add(listener);
        }
    }

    @SuppressWarnings("unchecked")
    public static void postFormConstructed(Form form)
    {
        if (form == null)
        {
            return;
        }

        for (Consumer<Form> listener : allFormListeners)
        {
            try
            {
                listener.accept(form);
            }
            catch (Throwable ignored)
            {}
        }

        List<Consumer<? extends Form>> listeners = formListeners.get(form.getClass());

        if (listeners != null)
        {
            for (Consumer<? extends Form> listener : listeners)
            {
                try
                {
                    ((Consumer<Form>) listener).accept(form);
                }
                catch (Throwable ignored)
                {}
            }
        }
    }

    public static void postModelFormConstructed(ModelForm form)
    {
        if (form == null)
        {
            return;
        }

        for (Consumer<ModelForm> listener : modelFormListeners)
        {
            try
            {
                listener.accept(form);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
