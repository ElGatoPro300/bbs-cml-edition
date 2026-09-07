package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.ModelFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class UIModelFormCategory extends UIFormCategory
{
    private static final int INDENT = 10;

    private int last;

    public UIModelFormCategory(FormCategory category, UIFormList list)
    {
        super(category, list);

        this.context((menu) ->
        {
            if (this.selected == null)
            {
                return;
            }

            menu.action(Icons.UPLOAD, UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL, () ->
            {
                ModelForm modelForm = (ModelForm) this.selected;
                ModelInstance model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    MapType map = CubicLoader.toData(model);

                    try
                    {
                        File path = BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelForm.model.get() + "/exported._bbs.json");

                        IOUtils.writeText(path, DataToString.toString(map, true));

                        UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                            UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL_TITLE,
                            UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL_DESCRIPTION,
                            path.getParentFile()
                        );

                        UIOverlay.addOverlay(this.getContext(), overlayPanel);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            });

        });
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.isHidden())
        {
            return false;
        }

        if (this.area.isInside(context))
        {
            int indent = this.getIndent();
            int x = context.mouseX - this.area.x;
            int y = context.mouseY - this.area.y - HEADER_HEIGHT;
            int perRow = this.area.w / CELL_WIDTH;

            if (y < 0)
            {
                int width = context.batcher.getFont().getWidth(this.category.getProcessedTitle());

                if (x < indent + 30 + width)
                {
                    this.category.visible.set(!this.category.visible.get());

                    return true;
                }
                else
                {
                    return false;
                }
            }

            x /= CELL_WIDTH;
            y /= CELL_HEIGHT;

            List<Form> forms = this.getForms();
            int i = x + y * perRow;

            if (i >= 0 && i < forms.size())
            {
                this.select(forms.get(i), true);
            }
            else
            {
                this.select(null, true);
            }
        }

        return false;
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.isHidden())
        {
            return false;
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.isHidden())
        {
            if (this.area.h != 0)
            {
                this.last = 0;
                this.h(0);
                UIElement container = this.getParentContainer();

                if (container != null)
                {
                    container.resize();
                }
            }

            return;
        }

        int indent = this.getIndent();
        List<Form> forms = this.getForms();
        boolean hideEmptyInFavorites = this.list.isFavoritesOnly() && forms.isEmpty();

        if (hideEmptyInFavorites)
        {
            if (this.last != 0 || this.area.h != 0)
            {
                this.last = 0;
                this.h(0);
                UIElement container = this.getParentContainer();

                if (container != null)
                {
                    container.resize();
                }
            }

            return;
        }

        context.batcher.textCard(this.category.getProcessedTitle(), this.area.x + 26 + indent, this.area.y + 6);

        if (this.category.visible.get())
        {
            context.batcher.icon(Icons.MOVE_DOWN, this.area.x + 16 + indent, this.area.y + 5, 0.5F, 0F);
        }
        else
        {
            context.batcher.icon(Icons.MOVE_UP, this.area.x + 16 + indent, this.area.y + 4, 0.5F, 0F);
        }

        int h = HEADER_HEIGHT;
        int x = 0;
        int i = 0;
        int perRow = this.area.w / CELL_WIDTH;

        if (!forms.isEmpty() && this.category.visible.get())
        {
            for (Form form : forms)
            {
                if (i == perRow)
                {
                    h += CELL_HEIGHT;
                    x = 0;
                    i = 0;
                }

                int cx = this.area.x + x;
                int cy = this.area.y + h;
                boolean isSelected = this.selected == form;

                context.batcher.clip(cx, cy, CELL_WIDTH, CELL_HEIGHT, context);

                if (isSelected)
                {
                    context.batcher.box(cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT, Colors.A50 | BBSSettings.primaryColor.get());
                    context.batcher.outline(cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT, Colors.A50 | BBSSettings.primaryColor.get(), 2);
                }

                this.list.renderFormThumbnail(context, form, cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT);
                context.batcher.unclip(context);

                UIFormList.FavoriteMarker marker = this.list.getFavoriteMarker(form);

                if (marker != null)
                {
                    context.batcher.outline(cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT, marker.color, 1);
                    this.renderFavoriteMarkerIcon(context, marker, cx, cy);
                }

                x += CELL_WIDTH;
                i += 1;
            }

            h += CELL_HEIGHT;
        }

        if (this.last != h)
        {
            this.last = h;

            UIElement container = this.getParentContainer();

            if (container != null)
            {
                this.h(h);
                container.resize();
            }
        }
    }

    private int getIndent()
    {
        ModelFormCategory.Folder folder = this.getFolder();

        if (folder == null)
        {
            return 0;
        }

        return Math.max(0, folder.getDepth()) * INDENT;
    }

    private boolean isHidden()
    {
        ModelFormCategory.Folder folder = this.getFolder();

        if (folder == null)
        {
            return false;
        }

        ModelFormCategory.Folder parent = folder.getParent();

        while (parent != null)
        {
            if (!parent.visible.get())
            {
                return true;
            }

            parent = parent.getParent();
        }

        return false;
    }

    private ModelFormCategory.Folder getFolder()
    {
        if (this.category instanceof ModelFormCategory.Folder)
        {
            return (ModelFormCategory.Folder) this.category;
        }

        return null;
    }
}
