package mchorse.bbs_mod.ui.selectors;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.selectors.EntitySelector;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UIDataUtils;

import net.minecraft.client.render.DiffuseLighting;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.List;
import java.util.function.Consumer;

public class UISelectorList extends UIList<EntitySelector>
{
    public UISelectorList(Consumer<List<EntitySelector>> callback)
    {
        super(callback);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.list.isEmpty())
        {
            UIDataUtils.renderRightClickHere(context, this.area);
        }
    }

    @Override
    protected String elementToString(UIContext context, int i, EntitySelector element)
    {
        String id = "-";

        if (element.entity != null)
        {
            id = element.name.isEmpty() ? element.entity.toString() : element.entity.toString() + " - " + element.name;
        }

        return id;
    }

    @Override
    protected void renderElementPart(UIContext context, EntitySelector element, int i, int x, int y, boolean hover, boolean selected)
    {
        super.renderElementPart(context, element, i, x, y, hover, selected);

        Form form = element.form;

        if (form != null)
        {
            x += this.area.w - 30;

            context.batcher.clip(x, y, 40, 20, context);

            y -= 10;

            Vector3f a = new Vector3f(0.85F, 0.85F, -1F).normalize();
            Vector3f b = new Vector3f(-0.85F, 0.85F, 1F).normalize();
            RenderSystem.setupLevelDiffuseLighting(a, b);
            FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);
            DiffuseLighting.disableGuiDepthLighting();

            context.batcher.unclip(context);
        }
    }
}
