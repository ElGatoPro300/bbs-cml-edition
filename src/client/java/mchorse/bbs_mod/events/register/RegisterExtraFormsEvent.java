package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Event allowing addons to register additional forms or form categories
 * into the ExtraFormSection palette without bytecode mixins.
 */
public class RegisterExtraFormsEvent
{
    private static final List<Form> extraForms = new ArrayList<>();
    private static final List<FormCategory> customCategories = new ArrayList<>();
    private static final List<Consumer<FormCategory>> extraCategoryConsumers = new ArrayList<>();

    public void register(Form form)
    {
        if (form != null)
        {
            extraForms.add(form);
        }
    }

    public void registerCategory(FormCategory category)
    {
        if (category != null)
        {
            customCategories.add(category);
        }
    }

    public void registerToExtraCategory(Consumer<FormCategory> consumer)
    {
        if (consumer != null)
        {
            extraCategoryConsumers.add(consumer);
        }
    }

    public static List<Form> getExtraForms()
    {
        return extraForms;
    }

    public static List<FormCategory> getCustomCategories()
    {
        return customCategories;
    }

    public static void populateExtraCategory(FormCategory extraCategory)
    {
        if (extraCategory == null)
        {
            return;
        }

        for (Form form : extraForms)
        {
            try
            {
                extraCategory.addForm(form);
            }
            catch (Throwable ignored)
            {}
        }

        for (Consumer<FormCategory> consumer : extraCategoryConsumers)
        {
            try
            {
                consumer.accept(extraCategory);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
