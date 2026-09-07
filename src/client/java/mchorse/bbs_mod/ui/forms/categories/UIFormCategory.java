package mchorse.bbs_mod.ui.forms.categories;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataStringifier;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UIFormCategory extends UIElement
{
    public static final int HEADER_HEIGHT = 20;
    public static final int CELL_WIDTH = 60;
    public static final int CELL_HEIGHT = 80;
    public static final int MARKER_ICON_SIZE = 12;

    public UIFormList list;
    public FormCategory category;
    public Form selected;

    private int last;
    private String search = "";
    private List<Form> searched = new ArrayList<>();
    private List<Form> favoritesFiltered = new ArrayList<>();

    private boolean dragging;
    private int dragIndex = -1;
    private long dragStart;

    public UIFormCategory(FormCategory category, UIFormList list)
    {
        this.category = category;
        this.list = list;

        this.context((menu) ->
        {
            FormCategories formCategories = BBSModClient.getFormCategories();
            UserFormSection userForms = formCategories.getUserForms();

            menu.action(Icons.EDIT, UIKeys.GENERAL_EDIT, () ->
            {
                this.list.closeOpenedCategoryPopup();
                this.list.palette.toggleEditor();
            });

            if (this.selected instanceof ModelForm)
            {
                menu.action(Icons.FOLDER, UIKeys.FORMS_CATEGORIES_CONTEXT_OPEN_MODEL_FOLDER, () ->
                {
                    ModelForm form = (ModelForm) this.selected;

                    UIUtils.openFolder(BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + form.model.get() + "/"));
                });
            }

            if (this.selected != null)
            {
                menu.action(Icons.COPY, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_FORM, () -> Window.setClipboard(FormUtils.toData(this.selected)));
                menu.action(Icons.COPY, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_TO_CATEGORY, () ->
                {
                    this.getContext().replaceContextMenu((m) ->
                    {
                        for (UserFormCategory formCategory : userForms.categories)
                        {
                            if (formCategory == this.category)
                            {
                                continue;
                            }

                            m.action(Icons.ADD, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_TO.format(formCategory.getProcessedTitle()), () ->
                            {
                                formCategory.addForm(FormUtils.copy(this.selected));
                                this.list.refreshCategoryCards();
                            });
                        }
                    });
                });
                menu.action(Icons.COPY, UIKeys.FORMS_CATEGORIES_CONTEXT_COPY_COMMAND, () ->
                {
                    MapType data = FormUtils.toData(this.selected);
                    DataStringifier stringifier = new DataStringifier();
                    String name = MinecraftClient.getInstance().player.getGameProfile().getName();

                    stringifier.jsonLike();
                    stringifier.indent = "";

                    Window.setClipboard("/bbs morph " + name + " " + stringifier.toString(data));
                });

                Collection<PlayerListEntry> playerList = MinecraftClient.getInstance().getNetworkHandler().getPlayerList();

                if (playerList.size() > 1)
                {
                    menu.action(Icons.ARROW_RIGHT, UIKeys.FORMS_CATEGORIES_CONTEXT_SHARE_FORM, () ->
                    {
                        this.getContext().replaceContextMenu((newMenu) ->
                        {
                            for (PlayerListEntry entry : playerList)
                            {
                                if (entry.getProfile().getId().equals(MinecraftClient.getInstance().player.getGameProfile().getId()))
                                {
                                    continue;
                                }

                                newMenu.action(Icons.ARROW_RIGHT, IKey.constant(entry.getProfile().getName()), () ->
                                {
                                    ClientNetwork.sendSharedForm(this.selected, entry.getProfile().getId());
                                });
                            }
                        });
                    });
                }

                if (this.list.supportsFavorites())
                {
                    boolean hasCustomCategories = this.list.hasCustomFavoriteCategories();
                    boolean isFavorite = this.list.getFavoriteMarker(this.selected) != null;

                    if (isFavorite)
                    {
                        menu.action(Icons.FIVE_STAR, this.list.getRemoveFavoriteContextLabel(this.selected), Colors.RED, () ->
                        {
                            this.list.removeFavoriteForm(this.selected);
                        });

                        if (hasCustomCategories)
                        {
                            menu.action(Icons.REFRESH, this.list.getMoveFavoriteContextLabel(), Colors.YELLOW, () ->
                            {
                                this.list.openAddToCategoryPanel(this.selected);
                            });
                        }
                    }
                    else
                    {
                        menu.action(Icons.FIVE_STAR, this.list.getAddFavoriteContextLabel(), Colors.YELLOW, () ->
                        {
                            if (hasCustomCategories)
                            {
                                this.list.openAddToCategoryPanel(this.selected);
                            }
                            else
                            {
                                this.list.addFavoriteForm(this.selected);
                            }
                        });
                    }
                }
            }
        });

        this.h(20);
    }

    public void search(String search)
    {
        this.search = search.toLowerCase();

        this.searched.clear();

        if (search.isEmpty())
        {
            return;
        }

        for (Form form : this.category.getForms())
        {
            if (form.getFormId().toLowerCase().contains(search) || form.getDisplayName().toLowerCase().contains(search))
            {
                this.searched.add(form);
            }
        }
    }

    public List<Form> getForms()
    {
        List<Form> source = this.search.isEmpty() ? this.category.getForms() : this.searched;

        if (!this.list.supportsFavorites() || !this.list.isFavoritesOnly())
        {
            return source;
        }

        this.favoritesFiltered.clear();

        for (Form form : source)
        {
            if (this.list.shouldDisplayForm(form))
            {
                this.favoritesFiltered.add(form);
            }
        }

        return this.favoritesFiltered;
    }

    public int getIndexAt(int mouseX, int mouseY)
    {
        int x = mouseX - this.area.x;
        int y = mouseY - this.area.y - HEADER_HEIGHT;
        int perRow = this.area.w / CELL_WIDTH;

        if (x >= 0 && y >= 0)
        {
            x /= CELL_WIDTH;
            y /= CELL_HEIGHT;
            int i = x + y * perRow;
            int size = this.getForms().size();

            if (i >= 0 && i <= size)
            {
                return Math.min(i, size);
            }
        }
        
        // If below the last row, return size (append)
        if (y >= 0 && (mouseY - this.area.y) < this.area.h) {
             return this.getForms().size();
        }

        return -1;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context))
        {
            int x = context.mouseX - this.area.x;
            int y = context.mouseY - this.area.y - HEADER_HEIGHT;
            int perRow = this.area.w / CELL_WIDTH;

            if (y < 0)
            {
                if (x < this.area.x + 30 + context.batcher.getFont().getWidth(this.category.title.get()))
                {
                    this.category.visible.set(!this.category.visible.get());

                    return true;
                }
                else
                {
                    return super.subMouseClicked(context);
                }
            }

            x /= CELL_WIDTH;
            y /= CELL_HEIGHT;

            List<Form> forms = this.getForms();
            int i = x + y * perRow;

            if (i >= 0 && i < forms.size())
            {
                if (context.mouseButton == 0 && this.category instanceof UserFormCategory && this.search.isEmpty())
                {
                    this.dragIndex = i;
                    this.dragStart = System.currentTimeMillis();
                    this.dragging = false;
                }

                this.select(forms.get(i), true);
            }
            else
            {
                this.select(null, true);
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragIndex != -1)
        {
            if (this.dragging && this.category instanceof UserFormCategory)
            {
                int x = context.mouseX - this.area.x;
                int y = context.mouseY - this.area.y - HEADER_HEIGHT;
                int perRow = this.area.w / CELL_WIDTH;

                if (this.area.isInside(context.mouseX, context.mouseY))
                {
                    x /= CELL_WIDTH;
                    y /= CELL_HEIGHT;
                    int i = x + y * perRow;

                    if (i >= 0 && i < this.getForms().size())
                    {
                        ((UserFormCategory) this.category).moveForm(this.dragIndex, i);
                    }
                }
                else
                {
                     this.list.handleFormDrop(this, this.dragIndex, context.mouseX, context.mouseY);
                }
            }

            this.dragIndex = -1;
            this.dragging = false;
        }

        return super.subMouseReleased(context);
    }

    public void select(Form form, boolean notify)
    {
        if (this.list != null)
        {
            this.list.selectCategory(this, form, notify);
        }

        this.selected = form;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragIndex != -1 && !this.dragging && this.category instanceof UserFormCategory)
        {
            if (System.currentTimeMillis() - this.dragStart > 250)
            {
                this.dragging = true;
            }
        }

        super.render(context);

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

        context.batcher.textCard(this.category.getProcessedTitle(), this.area.x + 26, this.area.y + 6);

        if (this.category.visible.get())
        {
            context.batcher.icon(Icons.MOVE_DOWN, this.area.x + 16, this.area.y + 5, 0.5F, 0F);
        }
        else
        {
            context.batcher.icon(Icons.MOVE_UP, this.area.x + 16, this.area.y + 4, 0.5F, 0F);
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

        if (this.dragging && this.dragIndex != -1 && this.category instanceof UserFormCategory)
        {
            Form form = this.getForms().get(this.dragIndex);
            int cx = context.mouseX - CELL_WIDTH / 2;
            int cy = context.mouseY - CELL_HEIGHT / 2;

            context.batcher.box(cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT, Colors.A50 | BBSSettings.primaryColor.get());
            context.batcher.outline(cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT, Colors.A50 | BBSSettings.primaryColor.get(), 2);

            this.list.renderFormThumbnail(context, form, cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT);
        }
    }

    protected void renderFavoriteMarkerIcon(UIContext context, UIFormList.FavoriteMarker marker, int cx, int cy)
    {
        if (marker == null || marker.icon == null || marker.icon.w <= 0 || marker.icon.h <= 0)
        {
            return;
        }

        /* Render at native icon size to avoid UV resampling artifacts/cropping look */
        context.batcher.icon(marker.icon, Colors.WHITE, cx + CELL_WIDTH - 3, cy + 2, 1F, 0F);
    }
}
