package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.ModelFormCategory;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.l10n.keys.LangKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIParticleFormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIRecentFormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIUserFormCategory;
import mchorse.bbs_mod.ui.forms.overlays.UIFavoriteCategoryOverlayPanel;
import mchorse.bbs_mod.ui.forms.overlays.UIRemoveFavoriteCategoryOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.navigation.UIControlBar;
import mchorse.bbs_mod.ui.framework.elements.navigation.UIIconTabButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;

import org.joml.Matrix3f;
import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class UIFormList extends UIElement
{
    private static final String FAVORITES_CATEGORY_ID = "favorites";
    private static final int FAVORITES_TOP_BAR_HEIGHT = 20;
    private static final int FAVORITES_TOP_BAR_BOTTOM_SPACING = 10;
    private static final int TAB_WIDTH_ALL = 88;
    private static final int TAB_WIDTH_FAVORITES = 108;
    private static final int TAB_WIDTH_CUSTOM = 122;
    private static final int TAB_WIDTH_ADD = 24;
    private static final int ACTIONS_BAR_HEIGHT = 20;
    private static final int SELECTED_INFO_WIDTH = 200;
    private static final int CATEGORY_CARD_WIDTH = 180;
    private static final int CATEGORY_CARD_HEIGHT = 140;
    private static final int CATEGORY_CARD_GAP = 10;
    private static final int CATEGORY_PREVIEW_PADDING = 8;
    private static final int CATEGORY_PREVIEW_GAP = 4;
    private static final int CATEGORY_PREVIEW_COLUMNS = 2;
    private static final int CATEGORY_PREVIEW_ROWS = 2;

    private static final int POPUP_CELL_WIDTH = 80;
    private static final int POPUP_CELL_HEIGHT = 100;
    private static final int POPUP_TARGETS_BAR_HEIGHT = 20;
    private static final long POPUP_DRAG_DELAY_MS = 250L;
    private static final int MAX_CATEGORY_NAME_LENGTH = 20;
    private static final int MAX_TAB_TITLE_LENGTH = 10;
    private static final long SEARCH_DEBOUNCE_MS = 150L;
    private static final int CATEGORY_VIRTUALIZATION_BUFFER_ROWS = 1;
    private static final int CATEGORY_PREVIEW_TOGGLE_SIZE = 14;
    private static final int CATEGORY_HIDDEN_ICON_SIZE = 50;
    private static final int CATEGORY_GROUP_HEADER_HEIGHT = 14;
    private static final int CATEGORY_SECTION_GAP = 16;
    private static final int POPUP_TARGET_BUTTON_GAP = 4;
    private static final int CATEGORY_MOVE_HANDLE_ICON_SIZE = 60;
    private static final int CATEGORY_DRAG_MOVE_ICON_SIZE = 60;

    public IUIFormList palette;

    public UIScrollView forms;
    public UIScrollView categoryCardsView;

    public UIElement tabsBar;
    public UIElement tabs;
    public UIElement bar;
    public UITextbox search;
    public UIButton allTab;
    public UIButton favoritesTab;
    public UIButton addCategoryTab;
    public UIIcon toggleAllPreviews;
    public UIIcon toggleCategoryOrderLock;
    public UIIcon edit;
    public UIIcon close;

    private UIFormCategory recent;
    private List<UIFormCategory> categories = new ArrayList<>();
    private final Set<String> favoriteModelForms = new HashSet<>();
    private final LinkedHashMap<String, FavoriteCategory> customFavoriteCategories = new LinkedHashMap<>();
    private final Map<String, Set<String>> customCategoryForms = new HashMap<>();
    private final List<UIIconTabButton> customCategoryTabs = new ArrayList<>();
    private final Map<UIIconTabButton, String> customCategoryTabIds = new HashMap<>();
    private String activeFavoriteCategoryId;
    private boolean favoritesUiVisible = true;
    private String syncedFavoriteCategoriesData = "";
    private final Set<String> syncedFavoriteModels = new HashSet<>();
    private Consumer<String> favoriteCategoryChanged;

    private long lastUpdate;
    private int lastScroll;
    private UIElement categoryPopup;
    private UICategoryCardsGrid categoryCards;
    private final Set<String> hiddenCategoryPreviews = new HashSet<>();
    private final List<String> modelCategoryOrder = new ArrayList<>();
    private String activeModelFolderPath;
    private String pendingSearchQuery = "";
    private String appliedSearchQuery = "";
    private long pendingSearchDeadline = -1L;
    private boolean userCategoryOrderUnlocked;

    private UIFormCategory expandedCategory;
    private ModelFormCategory.Folder activeExpandedFolder;


    public UIFormList(IUIFormList palette)
    {
        this.palette = palette;
        this.loadFavoriteDataFromSettings();

        this.forms = UI.scrollView(0, 0);
        this.forms.scroll.cancelScrolling();
        this.categoryCardsView = UI.scrollView(0, 0);
        this.categoryCardsView.scroll.cancelScrolling();
        this.tabsBar = new UIControlBar();
        this.tabs = new UIElement();
        this.categoryCards = new UICategoryCardsGrid();
        this.bar = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Matrix3x2fStack matrices = context.batcher.getContext().getMatrices();
                matrices.pushMatrix();
                /* TODO(1.21.11): Matrix3x2fStack does not support 3D translate
                context.batcher.getContext().getMatrices().translate(0, 0, 200);
                */
                this.area.render(context.batcher, Colors.CONTROL_BAR);
                super.render(context);

                Form selected = UIFormList.this.getSelected();

                if (selected != null)
                {
                    FontRenderer font = context.batcher.getFont();
                    int x = this.area.x + 4;
                    int y = this.area.y;
                    int maxTextWidth = SELECTED_INFO_WIDTH - 10;
                    String display = font.limitToWidth(selected.getDisplayName() == null ? "" : selected.getDisplayName(), maxTextWidth);
                    String valueId = font.limitToWidth(selected.getFormId() == null ? "" : selected.getFormId(), maxTextWidth);

                    context.batcher.textShadow(display, x, y + 1);
                    context.batcher.textShadow(valueId, x, y + 10, Colors.LIGHTEST_GRAY);
                }

                matrices.popMatrix();
            }
        };
        this.search = new UITextbox(100, this::search).placeholder(UIKeys.FORMS_LIST_SEARCH);
        this.allTab = new UIIconTabButton(UIKeys.FORMS_LIST_TAB_ALL, Icons.LIST, (b) -> this.setActiveFavoriteCategory(null));
        this.favoritesTab = new UIIconTabButton(UIKeys.FORMS_LIST_TAB_FAVORITES, Icons.FIVE_STAR, (b) -> this.setActiveFavoriteCategory(FAVORITES_CATEGORY_ID));
        this.addCategoryTab = new UIIconTabButton(IKey.constant(""), Icons.ADD, (b) -> this.openCreateCategoryPanel());
        this.toggleAllPreviews = new UIIcon(() -> this.areAllCategoryPreviewsVisible() ? Icons.VISIBLE : Icons.INVISIBLE, this::toggleAllCategoryPreviews);
        this.toggleAllPreviews.tooltip(UIKeys.FORMS_LIST_TOGGLE_ALL_PREVIEWS, Direction.TOP);
        this.toggleCategoryOrderLock = new UIIcon(() -> this.userCategoryOrderUnlocked ? Icons.UNLOCKED : Icons.LOCKED, (b) -> this.userCategoryOrderUnlocked = !this.userCategoryOrderUnlocked);
        this.toggleCategoryOrderLock.tooltip(UIKeys.FORMS_LIST_TOGGLE_CATEGORY_ORDER_LOCK, Direction.TOP);
        this.edit = new UIIcon(Icons.EDIT, this::edit);
        this.edit.tooltip(UIKeys.FORMS_LIST_EDIT, Direction.TOP);
        this.close = new UIIcon(Icons.CLOSE, this::close);

        this.forms.relative(this).x(0).y(ACTIONS_BAR_HEIGHT).w(1F).h(1F, -ACTIONS_BAR_HEIGHT);
        this.categoryCardsView.relative(this).x(0).y(ACTIONS_BAR_HEIGHT).w(1F).h(1F, -ACTIONS_BAR_HEIGHT);
        this.categoryCards.relative(this.categoryCardsView).x(0).y(0).w(1F).h(1F);
        this.tabsBar.relative(this).x(0).y(0).w(1F).h(FAVORITES_TOP_BAR_HEIGHT);
        this.tabs.relative(this.tabsBar).x(10).y(0).w(1F, -20).h(FAVORITES_TOP_BAR_HEIGHT).row(0).resize();
        this.bar.relative(this).x(0).y(FAVORITES_TOP_BAR_HEIGHT).w(1F).h(ACTIONS_BAR_HEIGHT);

        this.allTab.w(TAB_WIDTH_ALL).h(FAVORITES_TOP_BAR_HEIGHT);
        this.favoritesTab.w(TAB_WIDTH_FAVORITES).h(FAVORITES_TOP_BAR_HEIGHT);
        this.addCategoryTab.w(TAB_WIDTH_ADD).h(FAVORITES_TOP_BAR_HEIGHT);
        this.addCategoryTab.background(false);
        this.rebuildCategoryTabs();
        this.tabsBar.add(this.tabs);
        this.tabsBar.setVisible(false);
        this.bar.add(this.search, this.toggleAllPreviews, this.toggleCategoryOrderLock, this.edit, this.close);
        this.categoryCardsView.add(this.categoryCards);
        this.forms.setVisible(false);
        this.layoutActionBar();
        this.add(this.forms, this.categoryCardsView, this.bar, this.tabsBar);

        this.applyFavoritesLayout(this.isFavoritesFeatureEnabled());
        this.updateTabs();


        this.markContainer();
        this.setupForms(BBSModClient.getFormCategories());
    }

    public void focusSearch()
    {
        this.search.clickItself();
    }

    private UIFormCategory findMatchingCategory(UIFormCategory other)
    {
        if (other == null)
        {
            return null;
        }

        for (UIFormCategory category : this.categories)
        {
            if (other.category instanceof ModelFormCategory.Folder otherFolder && category.category instanceof ModelFormCategory.Folder thisFolder)
            {
                if (Objects.equals(otherFolder.path, thisFolder.path))
                {
                    return category;
                }
            }
            else
            {
                String otherTitle = other.category.getProcessedTitle();
                String thisTitle = category.category.getProcessedTitle();

                if (Objects.equals(otherTitle, thisTitle))
                {
                    return category;
                }
            }
        }

        return null;
    }

    public void setupForms(FormCategories forms)
    {
        this.closeCategoryPopup();

        UIFormCategory oldExpanded = this.expandedCategory;

        this.categories.clear();
        this.forms.removeAll();
        List<FormCategory> allCategories = new ArrayList<>(forms.getAllCategories());

        this.applyStoredModelCategoryOrder(allCategories);

        for (FormCategory category : allCategories)
        {
            UIFormCategory uiCategory = category.createUI(this);

            this.forms.add(uiCategory);
            this.categories.add(uiCategory);

            if (uiCategory instanceof UIRecentFormCategory)
            {
                this.recent = uiCategory;
            }
        }

        this.categories.get(this.categories.size() - 1).marginBottom(40);
        this.syncModelFolderNavigationState();
        this.hiddenCategoryPreviews.retainAll(this.getAvailableCategoryPreviewKeys());

        UIFormCategory matched = this.findMatchingCategory(oldExpanded);

        if (matched != null)
        {
            this.categoryCards.expandCategory(matched, matched.category instanceof ModelFormCategory.Folder folder ? folder : null);
        }
        else
        {
            this.categoryCards.collapseExpandedCategoryInstantly();
        }

        this.applySearchNow(this.appliedSearchQuery);
        this.categoryCardsView.scroll.scrollTo(0);
        this.resize();

        this.lastUpdate = forms.getLastUpdate();
    }

    public boolean supportsFavorites()
    {
        return this.isFavoritesFeatureEnabled();
    }

    public boolean isFavoritesOnly()
    {
        return this.isFavoritesFeatureEnabled() && this.activeFavoriteCategoryId != null;
    }

    public boolean isFavoriteForm(Form form)
    {
        return this.getFavoriteMarker(form) != null;
    }

    public boolean hasCustomFavoriteCategories()
    {
        return !this.customFavoriteCategories.isEmpty();
    }

    public FavoriteMarker getFavoriteMarker(Form form)
    {
        String key = this.getFavoriteKey(form);
        String legacyKey = this.getLegacyFavoriteKey(form);

        if (key == null && legacyKey == null)
        {
            return null;
        }

        FavoriteCategory customCategory = this.findCustomCategoryForKeys(key, legacyKey);

        if (customCategory != null)
        {
            return new FavoriteMarker(customCategory.id, customCategory.name, this.getIconByName(customCategory.icon), (customCategory.color & Colors.RGB) | Colors.A100);
        }

        if (this.favoriteModelForms.contains(key) || (legacyKey != null && this.favoriteModelForms.contains(legacyKey)))
        {
            return new FavoriteMarker(FAVORITES_CATEGORY_ID, UIKeys.FORMS_LIST_TAB_FAVORITES.get(), Icons.FIVE_STAR, Colors.YELLOW | Colors.A100);
        }

        return null;
    }

    public boolean addFavoriteForm(Form form)
    {
        if (this.hasCustomFavoriteCategories())
        {
            this.openAddToCategoryPanel(form);

            return false;
        }

        String key = this.getFavoriteKey(form);

        if (key == null)
        {
            return false;
        }

        Set<String> categoryForms = this.getFormsForCategory(this.getCurrentMarkerCategoryId(), true);

        if (categoryForms.add(key))
        {
            this.persistFavoriteData();
            this.refreshCategoryCards();

            return true;
        }

        return false;
    }

    public boolean addFavoriteFormToCategory(Form form, String categoryId)
    {
        String key = this.getFavoriteKey(form);
        String legacyKey = this.getLegacyFavoriteKey(form);

        if (key == null || categoryId == null)
        {
            return false;
        }

        if (!FAVORITES_CATEGORY_ID.equals(categoryId) && !this.customFavoriteCategories.containsKey(categoryId))
        {
            return false;
        }

        boolean changed = false;

        for (Set<String> forms : this.customCategoryForms.values())
        {
            changed = forms.remove(key) || changed;

            if (legacyKey != null)
            {
                changed = forms.remove(legacyKey) || changed;
            }
        }

        changed = this.favoriteModelForms.remove(key) || changed;

        if (legacyKey != null)
        {
            changed = this.favoriteModelForms.remove(legacyKey) || changed;
        }

        if (FAVORITES_CATEGORY_ID.equals(categoryId))
        {
            changed = this.favoriteModelForms.add(key) || changed;
        }
        else
        {
            Set<String> categoryForms = this.getFormsForCategory(categoryId, true);

            if (categoryForms != null)
            {
                changed = categoryForms.add(key) || changed;
            }
        }

        if (changed)
        {
            this.persistFavoriteData();
            this.refreshCategoryCards();
        }

        return changed;
    }

    public boolean isFormInCustomFavoriteCategory(Form form)
    {
        FavoriteMarker marker = this.getFavoriteMarker(form);

        return marker != null && !FAVORITES_CATEGORY_ID.equals(marker.categoryId);
    }

    public boolean removeFavoriteForm(Form form)
    {
        String key = this.getFavoriteKey(form);
        String legacyKey = this.getLegacyFavoriteKey(form);

        if (key == null)
        {
            return false;
        }

        Set<String> currentCategoryForms = this.getFormsForCategory(this.getCurrentMarkerCategoryId(), false);

        if (currentCategoryForms != null && (currentCategoryForms.remove(key) || (legacyKey != null && currentCategoryForms.remove(legacyKey))))
        {
            this.persistFavoriteData();
            this.refreshCategoryCards();

            return true;
        }

        FavoriteCategory customCategory = this.findCustomCategoryForKeys(key, legacyKey);

        if (customCategory != null)
        {
            Set<String> customForms = this.customCategoryForms.get(customCategory.id);

            if (customForms != null && (customForms.remove(key) || (legacyKey != null && customForms.remove(legacyKey))))
            {
                this.persistFavoriteData();
                this.refreshCategoryCards();

                return true;
            }
        }

        if (this.favoriteModelForms.remove(key) || (legacyKey != null && this.favoriteModelForms.remove(legacyKey)))
        {
            this.persistFavoriteData();
            this.refreshCategoryCards();

            return true;
        }

        return false;
    }

    public IKey getAddFavoriteContextLabel()
    {
        if (this.hasCustomFavoriteCategories())
        {
            return UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_TO_PICK;
        }

        return this.getAddToCurrentCategoryLabel();
    }

    public IKey getMoveFavoriteContextLabel()
    {
        return UIKeys.FORMS_CATEGORIES_CONTEXT_MOVE_TO_PICK;
    }

    public IKey getRemoveFavoriteContextLabel(Form form)
    {
        FavoriteMarker marker = this.getFavoriteMarker(form);

        if (marker == null || FAVORITES_CATEGORY_ID.equals(marker.categoryId))
        {
            return UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FROM_FAVORITES;
        }

        return UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FROM_CATEGORY.format(marker.name);
    }

    public void openAddToCategoryPanel(Form form)
    {
        if (form == null || this.customFavoriteCategories.isEmpty())
        {
            return;
        }

        List<FavoriteMarker> options = this.getCategoryPickOptions();
        UICategoryPickOverlayPanel panel = new UICategoryPickOverlayPanel(options, (marker) ->
        {
            if (marker != null)
            {
                this.addFavoriteFormToCategory(form, marker.categoryId);
            }
        });

        FavoriteMarker marker = this.getFavoriteMarker(form);

        if (marker != null)
        {
            panel.setCurrent(marker.categoryId);
        }

        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.6F);
    }

    public boolean shouldDisplayForm(Form form)
    {
        if (!this.isFavoritesOnly())
        {
            return true;
        }

        String key = this.getFavoriteKey(form);
        String legacyKey = this.getLegacyFavoriteKey(form);
        Set<String> categoryForms = this.getFormsForCategory(this.activeFavoriteCategoryId, false);

        return key != null && categoryForms != null && (categoryForms.contains(key) || (legacyKey != null && categoryForms.contains(legacyKey)));
    }

    public IKey getAddToCurrentCategoryLabel()
    {
        if (this.activeFavoriteCategoryId == null || FAVORITES_CATEGORY_ID.equals(this.activeFavoriteCategoryId))
        {
            return UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_TO_FAVORITES;
        }

        FavoriteCategory category = this.customFavoriteCategories.get(this.activeFavoriteCategoryId);
        String title = category == null ? UIKeys.FORMS_LIST_TAB_FAVORITES.get() : category.name;

        return UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_TO_CATEGORY.format(title);
    }

    public IKey getRemoveFromCurrentCategoryLabel()
    {
        if (this.activeFavoriteCategoryId == null || FAVORITES_CATEGORY_ID.equals(this.activeFavoriteCategoryId))
        {
            return UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FROM_FAVORITES;
        }

        FavoriteCategory category = this.customFavoriteCategories.get(this.activeFavoriteCategoryId);
        String title = category == null ? UIKeys.FORMS_LIST_TAB_FAVORITES.get() : category.name;

        return UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_FROM_CATEGORY.format(title);
    }

    private void setActiveFavoriteCategory(String categoryId)
    {
        if (!this.isFavoritesFeatureEnabled() || Objects.equals(this.activeFavoriteCategoryId, categoryId))
        {
            return;
        }

        Form selected = this.getSelected();
        String query = this.search.getText();

        this.activeFavoriteCategoryId = categoryId;
        this.notifyFavoriteCategoryChanged();
        this.updateTabs();
        this.setupForms(BBSModClient.getFormCategories());
        this.applySearchNow(query);
        this.restoreSelectedIfPresent(selected);
        this.forms.scroll.scrollTo(0);
        this.forms.resize();
        this.resize();
    }

    private void updateTabs()
    {
        if (!this.isFavoritesFeatureEnabled())
        {
            return;
        }

        this.allTab.color(this.activeFavoriteCategoryId == null ? BBSSettings.primaryColor.get() : 0x2d2d2d);
        this.favoritesTab.color(FAVORITES_CATEGORY_ID.equals(this.activeFavoriteCategoryId) ? Colors.YELLOW : 0x2d2d2d);

        for (UIIconTabButton tab : this.customCategoryTabs)
        {
            String categoryId = this.customCategoryTabIds.get(tab);
            FavoriteCategory category = this.customFavoriteCategories.get(categoryId);
            int tabColor = category == null ? 0x2d2d2d : category.color;

            tab.color(Objects.equals(this.activeFavoriteCategoryId, categoryId) ? tabColor : 0x2d2d2d);
        }

        this.addCategoryTab.color(0x2d2d2d);
    }

    private void rebuildCategoryTabs()
    {
        this.customCategoryTabs.clear();
        this.customCategoryTabIds.clear();
        this.tabs.removeAll();
        this.tabs.add(this.allTab, this.favoritesTab);

        for (FavoriteCategory category : this.customFavoriteCategories.values())
        {
            UIIconTabButton tab = new UIIconTabButton(IKey.constant(this.getCategoryTabTitle(category.name)), this.getIconByName(category.icon), (b) -> this.setActiveFavoriteCategory(category.id));
            tab.w(TAB_WIDTH_CUSTOM).h(FAVORITES_TOP_BAR_HEIGHT);
            tab.removable((b) -> this.openRemoveCustomCategoryPanel(category));
            tab.context((menu) -> menu.action(Icons.EDIT, UIKeys.FORMS_LIST_CATEGORIES_EDIT_ACTION, () -> this.openEditCustomCategoryPanel(category.id)));
            this.customCategoryTabs.add(tab);
            this.customCategoryTabIds.put(tab, category.id);
            this.tabs.add(tab);
        }

        this.tabs.add(this.addCategoryTab);
        this.tabs.resize();
        this.updateTabs();
    }

    private void removeCustomCategory(String categoryId)
    {
        if (categoryId == null || FAVORITES_CATEGORY_ID.equals(categoryId))
        {
            return;
        }

        if (this.customFavoriteCategories.remove(categoryId) == null)
        {
            return;
        }

        this.customCategoryForms.remove(categoryId);

        if (Objects.equals(this.activeFavoriteCategoryId, categoryId))
        {
            this.activeFavoriteCategoryId = FAVORITES_CATEGORY_ID;
        }

        this.persistFavoriteData();
        this.rebuildCategoryTabs();

        Form selected = this.getSelected();
        String query = this.search.getText();
        this.setupForms(BBSModClient.getFormCategories());
        this.applySearchNow(query);
        this.restoreSelectedIfPresent(selected);
    }

    private void openRemoveCustomCategoryPanel(FavoriteCategory category)
    {
        if (category == null)
        {
            return;
        }

        UIRemoveFavoriteCategoryOverlayPanel panel = new UIRemoveFavoriteCategoryOverlayPanel(category.name, (confirm) ->
        {
            if (confirm)
            {
                this.removeCustomCategory(category.id);
            }
        });

        UIOverlay.addOverlay(this.getContext(), panel, 320, 140);
    }

    private void openCreateCategoryPanel()
    {
        UIFavoriteCategoryOverlayPanel panel = new UIFavoriteCategoryOverlayPanel(this::createCustomCategory);

        UIOverlay.addOverlay(this.getContext(), panel, 260, 160);
    }

    private void openEditCustomCategoryPanel(String categoryId)
    {
        FavoriteCategory category = this.customFavoriteCategories.get(categoryId);

        if (category == null)
        {
            return;
        }

        UIFavoriteCategoryOverlayPanel.FavoriteCategoryData data = new UIFavoriteCategoryOverlayPanel.FavoriteCategoryData(category.name, category.icon, category.color);
        UIFavoriteCategoryOverlayPanel panel = new UIFavoriteCategoryOverlayPanel(
            UIKeys.FORMS_LIST_CATEGORIES_EDIT_TITLE,
            UIKeys.GENERAL_EDIT,
            data,
            (updatedData) -> this.editCustomCategory(categoryId, updatedData)
        );

        UIOverlay.addOverlay(this.getContext(), panel, 260, 160);
    }

    private boolean createCustomCategory(UIFavoriteCategoryOverlayPanel.FavoriteCategoryData data)
    {
        String name = data.name == null ? "" : data.name.trim();

        if (name.isEmpty())
        {
            return false;
        }

        if (name.length() > MAX_CATEGORY_NAME_LENGTH)
        {
            name = name.substring(0, MAX_CATEGORY_NAME_LENGTH);
        }

        if (this.hasCategoryName(name))
        {
            UIOverlay.addOverlay(this.getContext(), new UIMessageOverlayPanel(
                UIKeys.GENERAL_WARNING,
                UIKeys.FORMS_LIST_CATEGORIES_CREATE_EXISTS.format(name)
            ), 320, 120);

            return false;
        }

        String id = this.generateUniqueCategoryId(name);
        FavoriteCategory category = new FavoriteCategory(id, name, data.iconName, data.color & Colors.RGB);

        this.customFavoriteCategories.put(id, category);
        this.customCategoryForms.put(id, new HashSet<>());
        this.activeFavoriteCategoryId = id;
        this.persistFavoriteData();
        this.rebuildCategoryTabs();

        Form selected = this.getSelected();
        String query = this.search.getText();
        this.setupForms(BBSModClient.getFormCategories());
        this.applySearchNow(query);
        this.restoreSelectedIfPresent(selected);

        return true;
    }

    private boolean editCustomCategory(String categoryId, UIFavoriteCategoryOverlayPanel.FavoriteCategoryData data)
    {
        FavoriteCategory current = this.customFavoriteCategories.get(categoryId);

        if (current == null)
        {
            return false;
        }

        String name = data.name == null ? "" : data.name.trim();

        if (name.isEmpty())
        {
            return false;
        }

        if (name.length() > MAX_CATEGORY_NAME_LENGTH)
        {
            name = name.substring(0, MAX_CATEGORY_NAME_LENGTH);
        }

        if (this.hasCategoryName(name, categoryId))
        {
            UIOverlay.addOverlay(this.getContext(), new UIMessageOverlayPanel(
                UIKeys.GENERAL_WARNING,
                UIKeys.FORMS_LIST_CATEGORIES_CREATE_EXISTS.format(name)
            ), 320, 120);

            return false;
        }

        FavoriteCategory updated = new FavoriteCategory(categoryId, name, data.iconName, data.color & Colors.RGB);

        this.customFavoriteCategories.put(categoryId, updated);
        this.persistFavoriteData();
        this.rebuildCategoryTabs();

        Form selected = this.getSelected();
        String query = this.search.getText();
        this.setupForms(BBSModClient.getFormCategories());
        this.applySearchNow(query);
        this.restoreSelectedIfPresent(selected);

        return true;
    }

    private boolean hasCategoryName(String name)
    {
        return this.hasCategoryName(name, null);
    }

    private boolean hasCategoryName(String name, String ignoreCategoryId)
    {
        for (FavoriteCategory category : this.customFavoriteCategories.values())
        {
            if (!Objects.equals(category.id, ignoreCategoryId) && category.name.equalsIgnoreCase(name))
            {
                return true;
            }
        }

        return false;
    }

    private String getCategoryTabTitle(String name)
    {
        if (name == null)
        {
            return "";
        }

        String trimmed = name.trim();

        if (trimmed.length() <= MAX_TAB_TITLE_LENGTH)
        {
            return trimmed;
        }

        return trimmed.substring(0, MAX_TAB_TITLE_LENGTH) + "...";
    }

    private FavoriteCategory findCustomCategoryForKeys(String key, String legacyKey)
    {
        if (key == null && legacyKey == null)
        {
            return null;
        }

        for (FavoriteCategory category : this.customFavoriteCategories.values())
        {
            Set<String> forms = this.customCategoryForms.get(category.id);

            if (forms != null && (forms.contains(key) || (legacyKey != null && forms.contains(legacyKey))))
            {
                return category;
            }
        }

        return null;
    }

    private List<FavoriteMarker> getCategoryPickOptions()
    {
        List<FavoriteMarker> options = new ArrayList<>();

        options.add(new FavoriteMarker(FAVORITES_CATEGORY_ID, UIKeys.FORMS_LIST_TAB_FAVORITES.get(), Icons.FIVE_STAR, Colors.YELLOW | Colors.A100));

        for (FavoriteCategory category : this.customFavoriteCategories.values())
        {
            options.add(new FavoriteMarker(category.id, category.name, this.getIconByName(category.icon), (category.color & Colors.RGB) | Colors.A100));
        }

        return options;
    }

    private void persistFavoriteData()
    {
        BBSSettings.favoriteModelForms.set(new HashSet<>(this.favoriteModelForms));

        MapType root = new MapType();
        ListType categories = new ListType();
        MapType entries = new MapType();

        for (FavoriteCategory category : this.customFavoriteCategories.values())
        {
            MapType item = new MapType();

            item.putString("id", category.id);
            item.putString("name", category.name);
            item.putString("icon", category.icon);
            item.putInt("color", category.color);
            categories.add(item);
        }

        entries.put(FAVORITES_CATEGORY_ID, this.toList(this.favoriteModelForms));

        for (Map.Entry<String, Set<String>> entry : this.customCategoryForms.entrySet())
        {
            if (this.customFavoriteCategories.containsKey(entry.getKey()))
            {
                entries.put(entry.getKey(), this.toList(entry.getValue()));
            }
        }

        root.putInt("version", 1);
        root.put("categories", categories);
        root.put("entries", entries);
        root.put("hidden_previews", this.toList(this.hiddenCategoryPreviews));
        root.put("model_categories_order", this.toList(this.modelCategoryOrder));
        BBSSettings.favoriteFormCategoriesData.set(DataToString.toString(root));
        this.syncedFavoriteCategoriesData = BBSSettings.favoriteFormCategoriesData.get();
        this.syncedFavoriteModels.clear();
        this.syncedFavoriteModels.addAll(this.favoriteModelForms);
    }

    private void syncFavoriteData()
    {
        String categoriesData = BBSSettings.favoriteFormCategoriesData.get();
        Set<String> sharedFavorites = BBSSettings.favoriteModelForms.get();

        if (Objects.equals(this.syncedFavoriteCategoriesData, categoriesData) && this.syncedFavoriteModels.equals(sharedFavorites))
        {
            return;
        }

        Form selected = this.getSelected();
        String query = this.search.getText();

        this.loadFavoriteDataFromSettings();
        this.rebuildCategoryTabs();

        if (this.isFavoritesOnly())
        {
            this.setupForms(BBSModClient.getFormCategories());
            this.applySearchNow(query);
            this.restoreSelectedIfPresent(selected);
            this.forms.resize();
            this.resize();
        }
    }

    private void loadFavoriteDataFromSettings()
    {
        this.favoriteModelForms.clear();
        this.favoriteModelForms.addAll(BBSSettings.favoriteModelForms.get());
        this.customFavoriteCategories.clear();
        this.customCategoryForms.clear();
        this.hiddenCategoryPreviews.clear();
        this.modelCategoryOrder.clear();

        String raw = BBSSettings.favoriteFormCategoriesData.get();
        MapType root = DataToString.mapFromString(raw);

        if (root != null && root.has("categories", BaseType.TYPE_LIST))
        {
            ListType categories = root.getList("categories");

            for (BaseType type : categories)
            {
                if (!type.isMap())
                {
                    continue;
                }

                MapType item = type.asMap();
                String id = item.getString("id", "").trim().toLowerCase(Locale.ROOT);
                String name = item.getString("name", "").trim();

                if (id.isEmpty() || name.isEmpty() || FAVORITES_CATEGORY_ID.equals(id))
                {
                    continue;
                }

                FavoriteCategory category = new FavoriteCategory(id, name, item.getString("icon", "five_star"), item.getInt("color", BBSSettings.primaryColor.get()) & Colors.RGB);
                this.customFavoriteCategories.put(category.id, category);
                this.customCategoryForms.put(category.id, new HashSet<>());
            }
        }

        if (root != null && root.has("entries", BaseType.TYPE_MAP))
        {
            MapType entries = root.getMap("entries");

            if (entries.has(FAVORITES_CATEGORY_ID, BaseType.TYPE_LIST))
            {
                this.favoriteModelForms.clear();
                this.favoriteModelForms.addAll(this.fromList(entries.getList(FAVORITES_CATEGORY_ID)));
            }

            for (Map.Entry<String, BaseType> entry : entries)
            {
                if (!entry.getValue().isList() || FAVORITES_CATEGORY_ID.equals(entry.getKey()))
                {
                    continue;
                }

                if (this.customFavoriteCategories.containsKey(entry.getKey()))
                {
                    this.customCategoryForms.put(entry.getKey(), this.fromList(entry.getValue().asList()));
                }
            }
        }

        if (root != null && root.has("hidden_previews", BaseType.TYPE_LIST))
        {
            this.hiddenCategoryPreviews.addAll(this.fromList(root.getList("hidden_previews")));
        }

        if (root != null && root.has("model_categories_order", BaseType.TYPE_LIST))
        {
            this.modelCategoryOrder.addAll(this.fromListOrdered(root.getList("model_categories_order")));
        }

        if (this.activeFavoriteCategoryId != null && !FAVORITES_CATEGORY_ID.equals(this.activeFavoriteCategoryId) && !this.customFavoriteCategories.containsKey(this.activeFavoriteCategoryId))
        {
            this.activeFavoriteCategoryId = FAVORITES_CATEGORY_ID;
        }

        this.syncedFavoriteCategoriesData = raw;
        this.syncedFavoriteModels.clear();
        this.syncedFavoriteModels.addAll(this.favoriteModelForms);
    }

    private Set<String> getFormsForCategory(String categoryId, boolean create)
    {
        if (categoryId == null)
        {
            return null;
        }

        if (FAVORITES_CATEGORY_ID.equals(categoryId))
        {
            return this.favoriteModelForms;
        }

        if (!this.customFavoriteCategories.containsKey(categoryId))
        {
            return null;
        }

        if (create)
        {
            return this.customCategoryForms.computeIfAbsent(categoryId, (id) -> new HashSet<>());
        }

        return this.customCategoryForms.get(categoryId);
    }

    private String getCurrentMarkerCategoryId()
    {
        return this.activeFavoriteCategoryId == null ? FAVORITES_CATEGORY_ID : this.activeFavoriteCategoryId;
    }

    private Icon getIconByName(String iconName)
    {
        return Icons.ICONS.getOrDefault(iconName, Icons.FIVE_STAR);
    }

    private String generateUniqueCategoryId(String name)
    {
        StringBuilder slug = new StringBuilder();

        for (char c : name.toLowerCase(Locale.ROOT).toCharArray())
        {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))
            {
                slug.append(c);
            }
            else if (slug.length() == 0 || slug.charAt(slug.length() - 1) != '_')
            {
                slug.append('_');
            }
        }

        String base = slug.toString().replaceAll("^_+|_+$", "");

        if (base.isEmpty())
        {
            base = "category";
        }

        String id = base;
        int index = 2;

        while (this.customFavoriteCategories.containsKey(id) || FAVORITES_CATEGORY_ID.equals(id))
        {
            id = base + "_" + index;
            index += 1;
        }

        return id;
    }

    private ListType toList(Set<String> values)
    {
        ListType list = new ListType();

        for (String value : values)
        {
            list.addString(value);
        }

        return list;
    }

    private ListType toList(Iterable<String> values)
    {
        ListType list = new ListType();

        for (String value : values)
        {
            list.addString(value);
        }

        return list;
    }

    private Set<String> fromList(ListType list)
    {
        Set<String> values = new HashSet<>();

        for (BaseType type : list)
        {
            if (type.isString())
            {
                values.add(type.asString());
            }
        }

        return values;
    }

    private List<String> fromListOrdered(ListType list)
    {
        List<String> values = new ArrayList<>();

        for (BaseType type : list)
        {
            if (type.isString())
            {
                values.add(type.asString());
            }
        }

        return values;
    }

    private String getFavoriteKey(Form form)
    {
        String legacyKey = this.getLegacyFavoriteKey(form);

        if (form == null)
        {
            return null;
        }

        MapType data = FormUtils.toData(form);

        if (data != null)
        {
            return "data:" + DataToString.toString(data);
        }

        return legacyKey;
    }

    private String getLegacyFavoriteKey(Form form)
    {
        if (form == null)
        {
            return null;
        }

        if (form instanceof ModelForm modelForm)
        {
            String model = modelForm.model.get();

            if (model == null || model.isBlank())
            {
                return null;
            }

            return model.toLowerCase();
        }

        if (form instanceof ParticleForm particleForm)
        {
            String effect = particleForm.effect.get();

            if (effect == null || effect.isBlank())
            {
                return null;
            }

            return "particle:" + effect.toLowerCase();
        }

        String formId = form.getFormId();

        if (formId == null || formId.isBlank())
        {
            return null;
        }

        return "form:" + formId.toLowerCase();
    }

    private boolean isFavoritesFeatureEnabled()
    {
        return false;
    }

    private void applyFavoritesLayout(boolean enabled)
    {
        if (this.favoritesUiVisible == enabled)
        {
            return;
        }

        this.favoritesUiVisible = enabled;
        this.tabsBar.setVisible(enabled);
        this.tabs.setVisible(enabled);

        if (!enabled)
        {
            this.activeFavoriteCategoryId = null;
            this.notifyFavoriteCategoryChanged();
        }

        int barY = enabled ? FAVORITES_TOP_BAR_HEIGHT : 0;
        int topOffset = ACTIONS_BAR_HEIGHT + (enabled ? FAVORITES_TOP_BAR_HEIGHT + FAVORITES_TOP_BAR_BOTTOM_SPACING : 0);

        this.bar.relative(this).x(0).y(barY).w(1F).h(ACTIONS_BAR_HEIGHT);
        this.layoutActionBar();
        this.bar.resize();

        this.forms.relative(this).x(0).y(topOffset).w(1F).h(1F, -topOffset);
        this.categoryCardsView.relative(this).x(0).y(topOffset).w(1F).h(1F, -topOffset);
        this.forms.resize();
        this.categoryCardsView.resize();
        this.resize();
    }

    private void layoutActionBar()
    {
        int rightOffset = 0;

        if (this.close.getParent() == this.bar && this.close.isVisible())
        {
            this.close.relative(this.bar).x(1F, -20 - rightOffset).y(0).w(20).h(ACTIONS_BAR_HEIGHT);
            rightOffset += 20;
        }

        if (this.edit.getParent() == this.bar && this.edit.isVisible())
        {
            this.edit.relative(this.bar).x(1F, -20 - rightOffset).y(0).w(20).h(ACTIONS_BAR_HEIGHT);
            rightOffset += 20;
        }

        for (IUIElement child : this.bar.getChildren())
        {
            if (!(child instanceof UIIcon) || !child.isVisible())
            {
                continue;
            }

            UIIcon icon = (UIIcon) child;

            if (icon == this.edit || icon == this.close)
            {
                continue;
            }

            icon.relative(this.bar).x(1F, -20 - rightOffset).y(0).w(20).h(ACTIONS_BAR_HEIGHT);
            rightOffset += 20;
        }

        int searchX = SELECTED_INFO_WIDTH + 6;
        this.search.relative(this.bar).x(searchX).y(0).w(1F, -(searchX + rightOffset)).h(ACTIONS_BAR_HEIGHT);

        /* Apply flex immediately so icons added after construction (e.g. morph actions)
         * are visible on the first open, not only after a later resize/reopen. */
        this.bar.resize();
    }

    public void refreshActionBar()
    {
        this.layoutActionBar();
    }

    public void setFavoriteCategoryChangedListener(Consumer<String> callback)
    {
        this.favoriteCategoryChanged = callback;
    }

    public String getActiveFavoriteCategoryId()
    {
        return this.activeFavoriteCategoryId;
    }

    public boolean hasFavoriteCategory(String categoryId)
    {
        if (categoryId == null || FAVORITES_CATEGORY_ID.equals(categoryId))
        {
            return true;
        }

        return this.customFavoriteCategories.containsKey(categoryId);
    }

    public void setActiveFavoriteCategoryWithFallback(String categoryId)
    {
        String target = this.hasFavoriteCategory(categoryId) ? categoryId : null;

        this.setActiveFavoriteCategory(target);
    }

    private void notifyFavoriteCategoryChanged()
    {
        if (this.favoriteCategoryChanged != null)
        {
            this.favoriteCategoryChanged.accept(this.activeFavoriteCategoryId);
        }
    }

    private void search(String search)
    {
        this.pendingSearchQuery = search == null ? "" : search.trim();
        this.pendingSearchDeadline = System.currentTimeMillis() + SEARCH_DEBOUNCE_MS;
    }

    private void applySearchNow(String search)
    {
        search = search.trim();
        this.pendingSearchQuery = search;
        this.appliedSearchQuery = search;
        this.pendingSearchDeadline = -1L;

        for (UIFormCategory category : this.categories)
        {
            category.search(search);
        }

        this.invalidateCategoryCards();
    }

    private void flushPendingSearch()
    {
        if (this.pendingSearchDeadline >= 0L && System.currentTimeMillis() >= this.pendingSearchDeadline)
        {
            this.applySearchNow(this.pendingSearchQuery);
        }
    }

    private void invalidateCategoryCards()
    {
        double scroll = this.categoryCardsView.scroll.getScroll();
        this.categoryCards.invalidateCache();
        this.categoryCardsView.resize();
        this.categoryCardsView.scroll.setScroll(scroll);
    }

    /**
     * Rebuild category cards / expanded form grids after forms are added, removed,
     * or filtered (favorites). Without this, expanded panels keep a stale snapshot
     * until the category is collapsed and opened again.
     */
    public void refreshCategoryCards()
    {
        this.invalidateCategoryCards();
    }

    private String getCategoryPreviewKey(UIFormCategory category)
    {
        if (category == null || category.category == null)
        {
            return "";
        }

        String id = category.category.visible.getId();

        if (id != null)
        {
            id = id.trim().toLowerCase(Locale.ROOT);
        }

        if (id != null && !id.isEmpty())
        {
            return id;
        }

        String title = category.category.getProcessedTitle();

        return title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> getAvailableCategoryPreviewKeys()
    {
        Set<String> keys = new HashSet<>();

        for (UIFormCategory category : this.categories)
        {
            String key = this.getCategoryPreviewKey(category);

            if (!key.isEmpty())
            {
                keys.add(key);
            }
        }

        return keys;
    }

    private boolean isCategoryPreviewVisible(UIFormCategory category)
    {
        return !this.hiddenCategoryPreviews.contains(this.getCategoryPreviewKey(category));
    }

    private void toggleCategoryPreview(UIFormCategory category)
    {
        String key = this.getCategoryPreviewKey(category);

        if (key.isEmpty())
        {
            return;
        }

        if (this.hiddenCategoryPreviews.contains(key))
        {
            this.hiddenCategoryPreviews.remove(key);
        }
        else
        {
            this.hiddenCategoryPreviews.add(key);
        }

        this.persistFavoriteData();
        this.invalidateCategoryCards();
    }

    private boolean areAllCategoryPreviewsVisible()
    {
        Set<String> keys = this.getAvailableCategoryPreviewKeys();

        if (keys.isEmpty())
        {
            return true;
        }

        for (String key : keys)
        {
            if (this.hiddenCategoryPreviews.contains(key))
            {
                return false;
            }
        }

        return true;
    }

    private void toggleAllCategoryPreviews(UIIcon button)
    {
        Set<String> keys = this.getAvailableCategoryPreviewKeys();

        if (keys.isEmpty())
        {
            return;
        }

        if (this.areAllCategoryPreviewsVisible())
        {
            this.hiddenCategoryPreviews.addAll(keys);
        }
        else
        {
            this.hiddenCategoryPreviews.removeAll(keys);
        }

        this.persistFavoriteData();
        this.invalidateCategoryCards();
    }

    private void restoreSelectedIfPresent(Form form)
    {
        if (form == null)
        {
            this.deselect();

            return;
        }

        this.deselect();

        for (UIFormCategory category : this.categories)
        {
            List<Form> forms = category.getForms();
            int index = forms.indexOf(form);

            if (index >= 0)
            {
                category.select(forms.get(index), false);

                return;
            }
        }
    }

    private void edit(UIIcon b)
    {
        this.closeCategoryPopup();
        this.palette.toggleEditor();
    }

    private void close(UIIcon b)
    {
        this.palette.exit();
    }

    public void closeOpenedCategoryPopup()
    {
        this.categoryCards.collapseExpandedCategory();
    }


    private void openCreateUserCategoryPanel()
    {
        FormCategories formCategories = BBSModClient.getFormCategories();
        UserFormSection userForms = formCategories.getUserForms();

        UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
            UIKeys.FORMS_CATEGORIES_ADD_CATEGORY_TITLE,
            UIKeys.FORMS_CATEGORIES_ADD_CATEGORY_DESCRIPTION,
            (str) ->
            {
                userForms.addUserCategory(new UserFormCategory(IKey.constant(str), formCategories.visibility.get(UUID.randomUUID().toString()), userForms));
                this.setupForms(formCategories);
            }
        ));
    }

    private void openRenameUserCategoryPanel(UIUserFormCategory category)
    {
        if (category == null)
        {
            return;
        }

        UserFormSection userForms = BBSModClient.getFormCategories().getUserForms();
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_TITLE,
            UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_DESCRIPTION,
            (str) ->
            {
                category.category.title = IKey.constant(str);
                userForms.writeUserCategories((UserFormCategory) category.category);
                this.invalidateCategoryCards();
            }
        );

        panel.text.setText(category.category.title.get());
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void openRemoveUserCategoryPanel(UIUserFormCategory category)
    {
        if (category == null)
        {
            return;
        }

        FormCategories formCategories = BBSModClient.getFormCategories();
        UserFormSection userForms = formCategories.getUserForms();
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
            UIKeys.FORMS_CATEGORIES_REMOVE_CATEGORY_TITLE.format(category.category.getProcessedTitle()),
            UIKeys.FORMS_CATEGORIES_REMOVE_CATEGORY_DESCRIPTION,
            (confirm) ->
            {
                if (!confirm)
                {
                    return;
                }

                userForms.removeUserCategory((UserFormCategory) category.category);
                this.setupForms(formCategories);
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void openCategoryCardContextMenu(UIContext context, UIFormCategory category)
    {
        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_CATEGORY, this::openCreateUserCategoryPanel);

            if (category instanceof UIUserFormCategory userCategory)
            {
                menu.action(Icons.EDIT, UIKeys.FORMS_CATEGORIES_CONTEXT_RENAME_CATEGORY, () -> this.openRenameUserCategoryPanel(userCategory));
                menu.action(Icons.TRASH, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_CATEGORY, () -> this.openRemoveUserCategoryPanel(userCategory));
            }
        });
    }

    private boolean moveUserCategory(UIUserFormCategory sourceCategory, UIUserFormCategory targetCategory)
    {
        if (sourceCategory == null || targetCategory == null || sourceCategory == targetCategory)
        {
            return false;
        }

        UserFormSection userForms = BBSModClient.getFormCategories().getUserForms();
        int from = userForms.categories.indexOf((UserFormCategory) sourceCategory.category);
        int to = userForms.categories.indexOf((UserFormCategory) targetCategory.category);

        if (!userForms.moveUserCategory(from, to))
        {
            return false;
        }

        Form selected = this.getSelected();
        String query = this.search.getText();

        this.setupForms(BBSModClient.getFormCategories());
        this.applySearchNow(query);
        this.restoreSelectedIfPresent(selected);

        return true;
    }

    private boolean isMovableUserCategory(UIFormCategory category)
    {
        return category != null && category.category instanceof UserFormCategory;
    }

    private boolean isMovableModelCategory(UIFormCategory category)
    {
        return category != null && category.category instanceof ModelFormCategory;
    }

    private boolean isMovableCategory(UIFormCategory category)
    {
        return this.isMovableUserCategory(category) || this.isMovableModelCategory(category);
    }

    private boolean canDropCategoryOn(UIFormCategory sourceCategory, UIFormCategory targetCategory)
    {
        if (sourceCategory == null || targetCategory == null || sourceCategory == targetCategory)
        {
            return false;
        }

        if (this.isMovableUserCategory(sourceCategory))
        {
            return this.isMovableUserCategory(targetCategory);
        }

        if (this.isMovableModelCategory(sourceCategory))
        {
            return this.isMovableModelCategory(targetCategory);
        }

        return false;
    }

    private boolean moveCategory(UIFormCategory sourceCategory, UIFormCategory targetCategory)
    {
        if (!this.canDropCategoryOn(sourceCategory, targetCategory))
        {
            return false;
        }

        if (sourceCategory instanceof UIUserFormCategory sourceUser && targetCategory instanceof UIUserFormCategory targetUser)
        {
            return this.moveUserCategory(sourceUser, targetUser);
        }

        return this.moveModelCategory(sourceCategory, targetCategory);
    }

    private boolean moveModelCategory(UIFormCategory sourceCategory, UIFormCategory targetCategory)
    {
        String sourceKey = this.getModelCategoryOrderKey(sourceCategory.category);
        String targetKey = this.getModelCategoryOrderKey(targetCategory.category);

        if (sourceKey == null || targetKey == null || sourceKey.equals(targetKey))
        {
            return false;
        }

        List<String> mergedOrder = this.mergeModelCategoryOrderWithCurrent();
        int from = mergedOrder.indexOf(sourceKey);
        int to = mergedOrder.indexOf(targetKey);

        if (from < 0 || to < 0 || from == to)
        {
            return false;
        }

        String moved = mergedOrder.remove(from);
        mergedOrder.add(to, moved);

        this.modelCategoryOrder.clear();
        this.modelCategoryOrder.addAll(mergedOrder);

        this.persistFavoriteData();

        Form selected = this.getSelected();
        String query = this.search.getText();

        this.setupForms(BBSModClient.getFormCategories());
        this.applySearchNow(query);
        this.restoreSelectedIfPresent(selected);

        return true;
    }

    private List<String> mergeModelCategoryOrderWithCurrent()
    {
        List<String> current = new ArrayList<>();

        for (UIFormCategory category : this.categories)
        {
            String key = this.getModelCategoryOrderKey(category.category);

            if (key != null && !current.contains(key))
            {
                current.add(key);
            }
        }

        List<String> merged = new ArrayList<>();

        for (String key : this.modelCategoryOrder)
        {
            if (current.contains(key) && !merged.contains(key))
            {
                merged.add(key);
            }
        }

        for (String key : current)
        {
            if (!merged.contains(key))
            {
                merged.add(key);
            }
        }

        return merged;
    }

    private void applyStoredModelCategoryOrder(List<FormCategory> allCategories)
    {
        if (allCategories.isEmpty())
        {
            return;
        }

        List<String> mergedOrder = this.mergeModelCategoryOrderWithCurrent(allCategories);
        Map<String, Integer> ranks = new HashMap<>();

        for (int i = 0; i < mergedOrder.size(); i++)
        {
            ranks.put(mergedOrder.get(i), i);
        }

        List<Integer> positions = new ArrayList<>();
        List<FormCategory> modelCategories = new ArrayList<>();

        for (int i = 0; i < allCategories.size(); i++)
        {
            FormCategory category = allCategories.get(i);

            if (this.getModelCategoryOrderKey(category) != null)
            {
                positions.add(i);
                modelCategories.add(category);
            }
        }

        modelCategories.sort((a, b) ->
        {
            String aKey = this.getModelCategoryOrderKey(a);
            String bKey = this.getModelCategoryOrderKey(b);
            int aRank = ranks.getOrDefault(aKey, Integer.MAX_VALUE);
            int bRank = ranks.getOrDefault(bKey, Integer.MAX_VALUE);

            return Integer.compare(aRank, bRank);
        });

        for (int i = 0; i < positions.size() && i < modelCategories.size(); i++)
        {
            allCategories.set(positions.get(i), modelCategories.get(i));
        }
    }

    private List<String> mergeModelCategoryOrderWithCurrent(List<FormCategory> allCategories)
    {
        List<String> current = new ArrayList<>();

        for (FormCategory category : allCategories)
        {
            String key = this.getModelCategoryOrderKey(category);

            if (key != null && !current.contains(key))
            {
                current.add(key);
            }
        }

        List<String> merged = new ArrayList<>();

        for (String key : this.modelCategoryOrder)
        {
            if (current.contains(key) && !merged.contains(key))
            {
                merged.add(key);
            }
        }

        for (String key : current)
        {
            if (!merged.contains(key))
            {
                merged.add(key);
            }
        }

        this.modelCategoryOrder.clear();
        this.modelCategoryOrder.addAll(merged);

        return merged;
    }

    private String getModelCategoryOrderKey(FormCategory category)
    {
        if (!(category instanceof ModelFormCategory))
        {
            return null;
        }

        if (category instanceof ModelFormCategory.Folder folder)
        {
            return "folder:" + folder.path;
        }

        for (Form form : category.getForms())
        {
            if (form instanceof ModelForm modelForm)
            {
                String modelKey = modelForm.model.get();
                int slash = modelKey == null ? -1 : modelKey.lastIndexOf('/');
                String path = slash >= 0 ? modelKey.substring(0, slash) : "";

                return "path:" + path;
            }
        }

        String title = category.getProcessedTitle();

        return "title:" + (title == null ? "" : title.toLowerCase(Locale.ROOT));
    }

    private boolean isModelFolderNavigationActive()
    {
        return this.activeModelFolderPath != null && !this.activeModelFolderPath.isEmpty();
    }

    private void syncModelFolderNavigationState()
    {
        if (!this.isModelFolderNavigationActive())
        {
            this.activeModelFolderPath = null;
            return;
        }

        UIFormCategory activeFolder = this.findModelFolderCategory(this.activeModelFolderPath);

        if (activeFolder == null || !(activeFolder.category instanceof ModelFormCategory.Folder))
        {
            this.activeModelFolderPath = null;
            return;
        }
    }

    private UIFormCategory findModelFolderCategory(String path)
    {
        if (path == null)
        {
            return null;
        }

        for (UIFormCategory category : this.categories)
        {
            if (!(category.category instanceof ModelFormCategory.Folder folder))
            {
                continue;
            }

            if (Objects.equals(folder.path, path))
            {
                return category;
            }
        }

        return null;
    }

    private boolean isTopLevelModelFolder(ModelFormCategory.Folder folder)
    {
        return !folder.path.isEmpty() && folder.getParent() == null;
    }

    private boolean isVisibleModelFolderInActiveNavigation(ModelFormCategory.Folder folder)
    {
        if (!this.isModelFolderNavigationActive())
        {
            return false;
        }

        if (Objects.equals(folder.path, this.activeModelFolderPath))
        {
            return !folder.getForms().isEmpty();
        }

        return folder.getParent() != null && Objects.equals(folder.getParent().path, this.activeModelFolderPath);
    }

    private boolean isCategoryVisibleInCards(UIFormCategory category)
    {
        if (category.category instanceof ModelFormCategory.Folder folder)
        {
            return folder.getParent() == null || folder.path.isEmpty();
        }

        return true;
    }

    private boolean shouldEnterModelFolder(UIFormCategory category)
    {
        return false;
    }


    private void enterModelFolder(UIFormCategory category)
    {
        if (!(category.category instanceof ModelFormCategory.Folder folder))
        {
            return;
        }

        ModelFormCategory.Folder targetFolder = this.resolveModelFolderNavigationTarget(folder);
        this.activeModelFolderPath = targetFolder.path;
        this.syncModelFolderNavigationState();
        this.categoryCardsView.scroll.scrollTo(0);
        this.invalidateCategoryCards();
    }

    private ModelFormCategory.Folder resolveModelFolderNavigationTarget(ModelFormCategory.Folder folder)
    {
        ModelFormCategory.Folder current = folder;

        while (current.getForms().isEmpty() && current.getChildren().size() == 1)
        {
            ModelFormCategory.Folder child = current.getChildren().get(0);

            if (child == null)
            {
                break;
            }

            String currentName = current.name == null ? "" : current.name;
            String childName = child.name == null ? "" : child.name;

            if (!currentName.equalsIgnoreCase(childName))
            {
                break;
            }

            current = child;
        }

        return current;
    }

    private void navigateUpModelFolder()
    {
        if (!this.isModelFolderNavigationActive())
        {
            return;
        }

        UIFormCategory activeCategory = this.findModelFolderCategory(this.activeModelFolderPath);

        if (!(activeCategory != null && activeCategory.category instanceof ModelFormCategory.Folder activeFolder))
        {
            this.activeModelFolderPath = null;
        }
        else if (activeFolder.getParent() == null || activeFolder.getParent().path.isEmpty())
        {
            this.activeModelFolderPath = null;
        }
        else
        {
            this.activeModelFolderPath = activeFolder.getParent().path;
        }

        this.syncModelFolderNavigationState();
        this.categoryCardsView.scroll.scrollTo(0);
        this.invalidateCategoryCards();
    }

    private void openCategoryPopup(UIFormCategory category)
    {
        if (category == null)
        {
            return;
        }

        this.closeCategoryPopup();

        UIContext context = this.getContext();

        if (context == null || context.menu == null || context.menu.overlay == null)
        {
            return;
        }

        UIElement overlay = context.menu.overlay;
        UIElement[] contentHolder = new UIElement[1];

        UIElement popup = new UIElement()
        {
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 0 && contentHolder[0] != null && !contentHolder[0].area.isInside(context))
                {
                    UIFormList.this.closeCategoryPopup();

                    return true;
                }

                return super.subMouseClicked(context);
            }

            @Override
            public boolean subKeyPressed(UIContext context)
            {
                if (context.isPressed(Keys.CLOSE))
                {
                    UIFormList.this.closeCategoryPopup();

                    return true;
                }

                return super.subKeyPressed(context);
            }

            @Override
            public void render(UIContext context)
            {
                this.area.render(context.batcher, Colors.A50);
                super.render(context);
            }
        };
        popup.full(overlay);
        popup.markContainer().mouseEventPropagataion(EventPropagation.BLOCK).keyboardEventPropagataion(EventPropagation.PASS);

        UIElement content = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                this.area.render(context.batcher, Colors.A25);
                context.batcher.outline(this.area.x - 1, this.area.y - 1, this.area.ex() + 1, this.area.ey() + 1, Colors.A100);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
                context.batcher.textCard(category.category.getProcessedTitle(), this.area.x + 6, this.area.y + 6);
                super.render(context);
            }
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (super.subMouseClicked(context))
                {
                    return true;
                }

                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    UIFormList.this.deselect();

                    return true;
                }

                return false;
            }
        };
        content.relative(popup).set(20, 20, 0, 0).w(1F, -40).h(1F, -40);
        contentHolder[0] = content;

        UIIcon closePopup = new UIIcon(Icons.CLOSE, (button) -> this.closeCategoryPopup());
        closePopup.relative(content).x(1F, -20).y(0).w(20).h(20).tooltip(UIKeys.GENERAL_CLOSE, Direction.LEFT);

        Map<UIElement, UIFormCategory> popupDropTargets = new HashMap<>();
        UICategoryPopupGrid[] popupGridHolder = new UICategoryPopupGrid[1];

        UIElement targetsBar = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                super.render(context);

                UICategoryPopupGrid grid = popupGridHolder[0];

                if (grid == null || !grid.isDraggingModel())
                {
                    return;
                }

                UIFormCategory hoveredTarget = grid.getHoveredDropCategory(context.mouseX, context.mouseY);

                for (Map.Entry<UIElement, UIFormCategory> entry : popupDropTargets.entrySet())
                {
                    UIFormCategory targetCategory = entry.getValue();

                    if (targetCategory == category || !(targetCategory.category instanceof UserFormCategory))
                    {
                        continue;
                    }

                    UIElement target = entry.getKey();
                    boolean hovered = targetCategory == hoveredTarget;
                    int fill = hovered ? (Colors.A50 | BBSSettings.primaryColor.get()) : Colors.A25;
                    int outline = hovered ? (Colors.A100 | BBSSettings.primaryColor.get()) : Colors.A100;

                    context.batcher.box(target.area.x, target.area.y, target.area.ex(), target.area.ey(), fill);
                    context.batcher.outline(target.area.x, target.area.y, target.area.ex(), target.area.ey(), outline, hovered ? 2 : 1);
                }
            }
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (super.subMouseClicked(context))
                {
                    return true;
                }

                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    UIFormList.this.deselect();

                    return true;
                }

                return false;
            }
        };
        List<UIButton> targetButtons = new ArrayList<>();
        boolean userCategoryPopup = category instanceof UIUserFormCategory || category.category instanceof UserFormCategory;

        targetsBar.relative(content).x(6).y(20).w(1F, -12).h(POPUP_TARGETS_BAR_HEIGHT);

        if (userCategoryPopup)
        {
            for (UIFormCategory targetCategory : this.categories)
            {
                if (!(targetCategory.category instanceof UserFormCategory))
                {
                    continue;
                }

                UIButton targetButton = new UIButton(IKey.constant(this.getCategoryTabTitle(targetCategory.category.getProcessedTitle())), (button) -> this.openCategoryPopup(targetCategory));
                targetButton.h(POPUP_TARGETS_BAR_HEIGHT);
                targetButton.color(targetCategory == category ? BBSSettings.primaryColor.get() : 0x2d2d2d);
                targetsBar.add(targetButton);
                targetButtons.add(targetButton);
                popupDropTargets.put(targetButton, targetCategory);
            }
        }

        UIScrollView popupScroll = UI.scrollView(0, 0);
        if (userCategoryPopup)
        {
            popupScroll.relative(content).x(0).y(24 + POPUP_TARGETS_BAR_HEIGHT).w(1F).h(1F, -(24 + POPUP_TARGETS_BAR_HEIGHT));
        }
        else
        {
            popupScroll.relative(content).x(0).y(24).w(1F).h(1F, -24);
        }
        popupScroll.scroll.cancelScrolling();

        UICategoryPopupGrid popupGrid = new UICategoryPopupGrid(category, popupDropTargets);
        popupGridHolder[0] = popupGrid;
        popupGrid.relative(popupScroll).x(0).y(0).w(1F).h(0);
        popupScroll.add(popupGrid);
        popupGrid.h(1);

        if (userCategoryPopup)
        {
            content.add(closePopup, targetsBar, popupScroll);
        }
        else
        {
            content.add(closePopup, popupScroll);
        }
        popup.add(content);
        overlay.add(popup);
        popup.resize();
        content.resize();
        if (userCategoryPopup)
        {
            this.layoutPopupTargetButtons(targetsBar, targetButtons);
        }
        popupScroll.resize();
        popupGrid.resize();
        this.categoryPopup = popup;
    }

    private void layoutPopupTargetButtons(UIElement targetsBar, List<UIButton> buttons)
    {
        if (buttons.isEmpty() || targetsBar.area.w <= 0)
        {
            return;
        }

        int totalGap = POPUP_TARGET_BUTTON_GAP * (buttons.size() - 1);
        int availableWidth = Math.max(64 * buttons.size(), targetsBar.area.w - totalGap);
        int buttonWidth = Math.max(64, availableWidth / buttons.size());
        int usedWidth = buttonWidth * buttons.size() + totalGap;
        int startX = Math.max(0, (targetsBar.area.w - usedWidth) / 2);
        int x = 0;

        for (UIButton button : buttons)
        {
            button.relative(targetsBar).xy(startX + x, 0).wh(buttonWidth, POPUP_TARGETS_BAR_HEIGHT);
            button.resize();
            x += buttonWidth + POPUP_TARGET_BUTTON_GAP;
        }

        targetsBar.resize();
    }

    private void closeCategoryPopup()
    {
        if (this.categoryPopup != null)
        {
            this.categoryPopup.removeFromParent();
            this.categoryPopup = null;
        }
    }

    public void selectCategory(UIFormCategory category, Form form, boolean notify)
    {
        this.deselect();

        category.selected = form;

        if (notify)
        {
            this.palette.accept(form);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (super.subMouseClicked(context))
        {
            return true;
        }

        if (context.mouseButton == 0 && (this.forms.area.isInside(context) || this.categoryCardsView.area.isInside(context)))
        {
            this.deselect();

            return true;
        }

        return false;
    }

    public void deselect()
    {
        for (UIFormCategory category : this.categories)
        {
            category.selected = null;
        }
    }

    public UIFormCategory getSelectedCategory()
    {
        for (UIFormCategory category : this.categories)
        {
            if (category.selected != null)
            {
                return category;
            }
        }

        return null;
    }

    public Form getSelected()
    {
        UIFormCategory category = this.getSelectedCategory();

        return category == null ? null : category.selected;
    }

    /**
     * Morph/form-list thumbnails. When Optimized morph menu is off: live draw (current default).
     * When on: selected animates live; hovered orbits live; others use a fixed-angle cache
     * so mouse move does not thrash fills.
     */
    public void renderFormThumbnail(UIContext context, Form form, int x1, int y1, int x2, int y2)
    {
        this.renderFormThumbnail(context, form, x1, y1, x2, y2, false);
    }

    public void renderFormThumbnail(UIContext context, Form form, int x1, int y1, int x2, int y2, boolean hovered)
    {
        if (form == null)
        {
            return;
        }

        if (!BBSSettings.optimizedMorphMenu.get())
        {
            FormUtilsClient.renderUI(form, context, x1, y1, x2, y2);

            return;
        }

        if (form == this.getSelected())
        {
            FormUtilsClient.renderUI(form, context, x1, y1, x2, y2, true);
        }
        else if (hovered)
        {
            FormUtilsClient.renderUI(form, context, x1, y1, x2, y2, false);
        }
        else
        {
            FormUtilsClient.renderUICachedStatic(form, context, x1, y1, x2, y2);
        }
    }

    /**
     * Tiny category-card previews — always fixed-angle cache when Optimized morph menu is on.
     */
    public void renderFormThumbnailStatic(UIContext context, Form form, int x1, int y1, int x2, int y2)
    {
        if (form == null)
        {
            return;
        }

        if (BBSSettings.optimizedMorphMenu.get())
        {
            FormUtilsClient.renderUICachedStatic(form, context, x1, y1, x2, y2);
        }
        else
        {
            FormUtilsClient.renderUI(form, context, x1, y1, x2, y2);
        }
    }

    public void setSelected(Form form)
    {
        boolean found = false;

        this.deselect();

        for (UIFormCategory category : this.categories)
        {
            int index = category.category.getForms().indexOf(form);

            if (index == -1)
            {
                category.selected = null;
            }
            else
            {
                found = true;

                category.select(category.category.getForms().get(index), false);
                this.expandedCategory = category;

                if (category.category instanceof ModelFormCategory.Folder folder)
                {
                    this.activeExpandedFolder = folder;
                }
                else
                {
                    this.activeExpandedFolder = null;
                }
                if (this.categoryCards != null)
                {
                    this.categoryCards.expandCategory(category, this.activeExpandedFolder);
                }
            }
        }

        if (!found && form != null)
        {
            Form copy = FormUtils.copy(form);

            this.recent.category.addForm(copy);
            this.recent.select(copy, false);
            this.expandedCategory = this.recent;
            this.activeExpandedFolder = null;

            if (this.categoryCards != null)
            {
                this.categoryCards.expandCategory(this.recent, null);
            }

            this.refreshCategoryCards();
        }
    }

    public UIFormCategory getUIFormCategory(FormCategory category)
    {
        for (UIFormCategory uiCategory : this.categories)
        {
            if (uiCategory.category == category)
            {
                return uiCategory;
            }
        }

        return null;
    }


    public boolean handleFormDrop(UIFormCategory source, int sourceIndex, int mouseX, int mouseY)
    {
        for (UIFormCategory category : this.categories)
        {
            if (category != source && category.area.isInside(mouseX, mouseY) && category.category instanceof UserFormCategory)
            {
                int index = category.getIndexAt(mouseX, mouseY);
                
                if (index != -1)
                {
                    Form form = source.category.getForms().get(sourceIndex);
                    
                    ((UserFormCategory) category.category).addForm(index, form);
                    source.category.removeForm(form);
                    this.refreshCategoryCards();

                    return true;
                }
            }
        }
        
        return false;
    }

    @Override
    public void render(UIContext context)
    {
        FormCategories categories = BBSModClient.getFormCategories();
        this.applyFavoritesLayout(this.isFavoritesFeatureEnabled());
        this.layoutActionBar();
        this.syncModelFolderNavigationState();
        this.syncFavoriteData();
        this.updateTabs();
        this.flushPendingSearch();

        if (this.lastScroll >= 0)
        {
            this.forms.scroll.scrollTo(this.lastScroll);

            this.lastScroll = -1;
        }

        if (this.lastUpdate != categories.getLastUpdate())
        {
            this.lastScroll = (int) this.forms.scroll.getScroll();

            Form selected = this.getSelected();

            this.setupForms(categories);
            this.setSelected(selected);
        }

        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);

        super.render(context);

        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.LEVEL);

    }

    private class UICategoryCardsGrid extends UIElement
    {
        private static final int EXPANDED_CELL_WIDTH = 80;
        private static final int EXPANDED_CELL_HEIGHT = 100;
        private static final int EXPANDED_HEADER_HEIGHT = 24;

        private final List<ExpandedItem> expandedItems = new ArrayList<>();
        private int expandedPanelX;
        private int expandedPanelY;
        private int expandedPanelW;
        private int expandedPanelH;

        private int dragFormIndex = -1;
        private long dragFormStart = -1L;
        private boolean draggingForm;
        private int contentHeight;

        private final List<ExpandedItem> oldExpandedItems = new ArrayList<>();
        private int oldExpandedPerRow;
        private float folderTransition = 1F;
        private int transitionDirection = 1;

        private float expansionTransition = 0F;
        private float targetExpansion = 0F;
        private long lastRenderTime = -1L;



        private final List<UIFormCategory> filteredCategories = new ArrayList<>();
        private final List<CategoryCell> cardCells = new ArrayList<>();
        private final List<GroupDivider> groupDividers = new ArrayList<>();
        private final Map<UIFormCategory, List<Form>> previewCache = new HashMap<>();
        private boolean dirty = true;
        private int lastHeight;
        private int cachedPerRow = 1;
        private UIFormCategory dragCategory;
        private CategoryCell dragCell;
        private long dragStart = -1L;
        private boolean draggingCategory;


        private enum CardGroup
        {
            RECENT(UIKeys.FORMS_LIST_GROUP_RECENT),
            USER_CREATED(UIKeys.FORMS_LIST_GROUP_USER),
            MODELS(UIKeys.FORMS_LIST_GROUP_MODELS),
            PARTICLES(UIKeys.FORMS_LIST_GROUP_PARTICLES),
            BBS(UIKeys.FORMS_LIST_GROUP_MISC);

            private final IKey label;

            CardGroup(IKey label)
            {
                this.label = label;
            }
        }

        public void invalidateCache()
        {
            this.dirty = true;
            this.lastHeight = 0;
            this.previewCache.clear();
        }

        public void collapseExpandedCategory()
        {
            this.targetExpansion = 0F;
            this.invalidateCache();
        }

        public void collapseExpandedCategoryInstantly()
        {
            this.targetExpansion = 0F;
            this.expansionTransition = 0F;
            UIFormList.this.expandedCategory = null;
            UIFormList.this.activeExpandedFolder = null;
            this.invalidateCache();
        }

        public void expandCategory(UIFormCategory category, ModelFormCategory.Folder folder)
        {
            UIFormList.this.expandedCategory = category;
            UIFormList.this.activeExpandedFolder = folder;
            this.targetExpansion = 1F;

            boolean simplify = BBSSettings.editorSimplifyAnimations != null && BBSSettings.editorSimplifyAnimations.get();

            if (simplify)
            {
                this.expansionTransition = 1F;
                this.folderTransition = 1F;
            }

            this.invalidateCache();
        }

        private void navigateFolder(ModelFormCategory.Folder targetFolder, boolean goingDeeper)
        {
            if (BBSSettings.editorSimplifyAnimations != null && BBSSettings.editorSimplifyAnimations.get())
            {
                UIFormList.this.activeExpandedFolder = targetFolder;
                this.folderTransition = 1F;
                this.invalidateCache();

                return;
            }

            this.oldExpandedItems.clear();
            this.oldExpandedItems.addAll(this.expandedItems);
            this.oldExpandedPerRow = Math.max(1, (this.expandedPanelW - CATEGORY_CARD_GAP * 2) / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP));

            UIFormList.this.activeExpandedFolder = targetFolder;
            this.folderTransition = 0F;
            this.transitionDirection = goingDeeper ? 1 : -1;
            this.invalidateCache();
        }

        private void updateAnimations(UIContext context)
        {
            long now = System.currentTimeMillis();
            float delta = this.lastRenderTime > 0L ? (now - this.lastRenderTime) / 1000F : 0F;

            this.lastRenderTime = now;

            if (BBSSettings.editorSimplifyAnimations != null && BBSSettings.editorSimplifyAnimations.get())
            {
                if (this.expansionTransition != this.targetExpansion)
                {
                    this.expansionTransition = this.targetExpansion;
                    this.invalidateCache();
                }

                if (this.folderTransition != 1F)
                {
                    this.folderTransition = 1F;
                    this.invalidateCache();
                }

                if (this.expansionTransition == 0F && this.targetExpansion == 0F && UIFormList.this.expandedCategory != null)
                {
                    UIFormList.this.expandedCategory = null;
                    UIFormList.this.activeExpandedFolder = null;
                    this.invalidateCache();
                }

                return;
            }

            if (this.expansionTransition < this.targetExpansion)
            {
                this.expansionTransition = Math.min(this.targetExpansion, this.expansionTransition + delta * 4F);
                this.invalidateCache();
            }
            else if (this.expansionTransition > this.targetExpansion)
            {
                this.expansionTransition = Math.max(this.targetExpansion, this.expansionTransition - delta * 4F);
                this.invalidateCache();
            }

            if (this.folderTransition < 1F)
            {
                this.folderTransition = Math.min(1F, this.folderTransition + delta * 4F);
                this.invalidateCache();
            }

            if (this.expansionTransition == 0F && this.targetExpansion == 0F && UIFormList.this.expandedCategory != null)
            {
                UIFormList.this.expandedCategory = null;
                UIFormList.this.activeExpandedFolder = null;
                this.invalidateCache();
            }
        }

        /**
         * Keep the user's scroll when the drawer opens or closes. Shrinking used to
         * subtract the lost height from scroll every animation frame, which yanked the
         * view upward on close. Opening still grows the panel under the category row.
         */
        private void applyContentHeight()
        {
            this.rebuildIfNeeded();

            int contentHeight = this.contentHeight;

            if (this.lastHeight == contentHeight)
            {
                return;
            }

            this.lastHeight = contentHeight;
            this.h(contentHeight);

            UIElement parent = this.getParent();

            if (parent == null)
            {
                return;
            }

            UIScrollView scrollView = parent instanceof UIScrollView view ? view : null;
            Scroll scroll = scrollView == null ? null : scrollView.scroll;
            double scrollBefore = scroll == null ? 0D : scroll.getScroll();

            parent.resize();

            if (scroll != null)
            {
                /* Ensure the scroll range matches drawer height (column resizer can lag). */
                scroll.scrollSize = Math.max(scroll.scrollSize, contentHeight);
                scroll.setScroll(scrollBefore);
                scroll.clamp();
            }
        }

        private float easeOutCubic(float t)
        {
            float f = t - 1F;

            return f * f * f + 1F;
        }


        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (!this.area.isInside(context) || (context.mouseButton != 0 && context.mouseButton != 1))
            {
                return false;
            }

            if (UIFormList.this.expandedCategory != null || !UIFormList.this.appliedSearchQuery.isEmpty())
            {
                if (context.mouseX >= this.expandedPanelX && context.mouseX < this.expandedPanelX + this.expandedPanelW
                    && context.mouseY >= this.expandedPanelY && context.mouseY < this.expandedPanelY + this.expandedPanelH)
                {
                    if (this.handleExpandedPanelClick(context))
                    {
                        return true;
                    }
                }
            }

            CategoryCell cell = this.getCategoryCellAt(context.mouseX, context.mouseY);

            if (cell == null)
            {
                if (context.mouseButton == 1)
                {
                    UIFormList.this.openCategoryCardContextMenu(context, null);

                    return true;
                }

                return false;
            }

            if (this.isToggleButtonAt(context.mouseX, context.mouseY, cell.x, cell.y))
            {
                UIFormList.this.toggleCategoryPreview(cell.category);

                return true;
            }

            if (context.mouseButton == 1)
            {
                UIFormList.this.openCategoryCardContextMenu(context, cell.category);

                return true;
            }

            if (UIFormList.this.userCategoryOrderUnlocked && UIFormList.this.isMovableCategory(cell.category))
            {
                this.dragCategory = cell.category;
                this.dragCell = cell;
                this.dragStart = System.currentTimeMillis();
                this.draggingCategory = false;

                return true;
            }

            if (UIFormList.this.expandedCategory == cell.category)
            {
                boolean simplify = BBSSettings.editorSimplifyAnimations != null && BBSSettings.editorSimplifyAnimations.get();

                if (simplify)
                {
                    this.collapseExpandedCategoryInstantly();
                }
                else
                {
                    this.collapseExpandedCategory();
                }
            }
            else
            {
                boolean wasExpanded = UIFormList.this.expandedCategory != null && this.expansionTransition >= 1F;
                boolean simplify = BBSSettings.editorSimplifyAnimations != null && BBSSettings.editorSimplifyAnimations.get();

                if (wasExpanded && !simplify)
                {
                    int oldIdx = UIFormList.this.categories.indexOf(UIFormList.this.expandedCategory);
                    int newIdx = UIFormList.this.categories.indexOf(cell.category);

                    this.oldExpandedItems.clear();
                    this.oldExpandedItems.addAll(this.expandedItems);
                    this.oldExpandedPerRow = Math.max(1, (this.expandedPanelW - CATEGORY_CARD_GAP * 2) / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP));

                    this.folderTransition = 0F;
                    this.transitionDirection = newIdx > oldIdx ? 1 : -1;
                }

                ModelFormCategory.Folder folder = cell.category.category instanceof ModelFormCategory.Folder f ? f : null;

                this.expandCategory(cell.category, folder);

                if (!wasExpanded && !simplify)
                {
                    this.expansionTransition = 0F;
                }
            }

            return true;
        }


        private boolean handleExpandedPanelClick(UIContext context)
        {
            /* Block clicks while any transition animation is active */
            if (this.folderTransition < 1F || this.expansionTransition < 1F || this.targetExpansion == 0F)
            {
                return true;
            }

            boolean hasBackArrow = UIFormList.this.activeExpandedFolder != null && UIFormList.this.activeExpandedFolder != UIFormList.this.expandedCategory.category;

            if (hasBackArrow && context.mouseX >= this.expandedPanelX + 4 && context.mouseX < this.expandedPanelX + 24
                && context.mouseY >= this.expandedPanelY + 4 && context.mouseY < this.expandedPanelY + 24)
            {
                if (context.mouseButton == 0)
                {
                    ModelFormCategory.Folder parent = UIFormList.this.activeExpandedFolder.getParent();
                    this.navigateFolder(parent, false);
                }

                return true;
            }

            int gridY = this.expandedPanelY + EXPANDED_HEADER_HEIGHT + CATEGORY_CARD_GAP;
            int localX = context.mouseX - this.expandedPanelX - CATEGORY_CARD_GAP;
            int localY = context.mouseY - gridY;

            if (localX < 0 || localY < 0)
            {
                return false;
            }

            int expandedPerRow = Math.max(1, (this.expandedPanelW - CATEGORY_CARD_GAP * 2) / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP));
            int col = localX / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP);
            int row = localY / (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP);
            int index = col + row * expandedPerRow;

            if (index >= 0 && index < this.expandedItems.size())
            {
                ExpandedItem item = this.expandedItems.get(index);

                if (context.mouseButton == 0)
                {
                    if (item.isFolder())
                    {
                        this.navigateFolder(item.folder, true);
                    }
                    else if (item.form != null)
                    {
                        UIFormCategory activeCategory = UIFormList.this.expandedCategory;
                        if (activeCategory == null)
                        {
                            for (UIFormCategory cat : UIFormList.this.categories)
                            {
                                if (cat.category.getForms().contains(item.form))
                                {
                                    activeCategory = cat;
                                    break;
                                }
                            }
                        }

                        if (activeCategory != null)
                        {
                            if (activeCategory.category instanceof UserFormCategory)
                            {
                                this.dragFormIndex = index;
                                this.dragFormStart = System.currentTimeMillis();
                                this.draggingForm = false;
                            }

                            activeCategory.select(item.form, true);
                        }
                    }
                }
                else if (context.mouseButton == 1)
                {
                    if (item.form != null)
                    {
                        UIFormCategory activeCategory = UIFormList.this.expandedCategory;
                        if (activeCategory == null)
                        {
                            for (UIFormCategory cat : UIFormList.this.categories)
                            {
                                if (cat.category.getForms().contains(item.form))
                                {
                                    activeCategory = cat;
                                    break;
                                }
                            }
                        }

                        if (activeCategory != null)
                        {
                            activeCategory.select(item.form, true);

                            UIContextMenu menu = activeCategory.createContextMenu(context);

                            if (menu != null && !menu.isEmpty())
                            {
                                context.setContextMenu(menu);
                            }
                        }
                    }
                }

                return true;
            }

            return false;
        }

        @Override
        public boolean subMouseReleased(UIContext context)
        {
            if (context.mouseButton != 0)
            {
                return super.subMouseReleased(context);
            }

            if (this.dragFormIndex != -1)
            {
                boolean handled = false;

                if (this.draggingForm && UIFormList.this.expandedCategory.category instanceof UserFormCategory)
                {
                    CategoryCell hoveredCard = this.getCategoryCellAt(context.mouseX, context.mouseY);

                    if (hoveredCard != null && hoveredCard.category != UIFormList.this.expandedCategory && hoveredCard.category.category instanceof UserFormCategory)
                    {
                        ExpandedItem dragItem = this.expandedItems.get(this.dragFormIndex);

                        if (dragItem.form != null)
                        {
                            ((UserFormCategory) hoveredCard.category.category).addForm(dragItem.form);
                            UIFormList.this.expandedCategory.category.removeForm(dragItem.form);
                            UIFormList.this.expandedCategory = hoveredCard.category;

                            if (hoveredCard.category.category instanceof ModelFormCategory.Folder folder)
                            {
                                UIFormList.this.activeExpandedFolder = folder;
                            }
                            else
                            {
                                UIFormList.this.activeExpandedFolder = null;
                            }

                            this.invalidateCache();
                            handled = true;
                        }
                    }
                    else
                    {
                        int gridY = this.expandedPanelY + EXPANDED_HEADER_HEIGHT + CATEGORY_CARD_GAP;
                        int localX = context.mouseX - this.expandedPanelX - CATEGORY_CARD_GAP;
                        int localY = context.mouseY - gridY;

                        if (localX >= 0 && localY >= 0)
                        {
                            int expandedPerRow = Math.max(1, (this.expandedPanelW - CATEGORY_CARD_GAP * 2) / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP));
                            int col = localX / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP);
                            int row = localY / (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP);
                            int hoverIdx = col + row * expandedPerRow;

                            if (hoverIdx >= 0 && hoverIdx < this.expandedItems.size() && hoverIdx != this.dragFormIndex)
                            {
                                ExpandedItem dragItem = this.expandedItems.get(this.dragFormIndex);
                                ExpandedItem targetItem = this.expandedItems.get(hoverIdx);

                                if (dragItem.form != null && targetItem.form != null)
                                {
                                    FormCategory targetFolder = UIFormList.this.activeExpandedFolder != null ? UIFormList.this.activeExpandedFolder : UIFormList.this.expandedCategory.category;
                                    int dragFormListIdx = targetFolder.getForms().indexOf(dragItem.form);
                                    int targetFormListIdx = targetFolder.getForms().indexOf(targetItem.form);

                                    if (dragFormListIdx >= 0 && targetFormListIdx >= 0)
                                    {
                                        ((UserFormCategory) targetFolder).moveForm(dragFormListIdx, targetFormListIdx);
                                        this.invalidateCache();
                                        handled = true;
                                    }
                                }
                            }
                        }
                    }
                }

                this.dragFormIndex = -1;
                this.dragFormStart = -1L;
                this.draggingForm = false;

                return handled || super.subMouseReleased(context);
            }

            if (this.dragCategory == null)
            {
                return super.subMouseReleased(context);
            }

            boolean handled = false;

            if (this.draggingCategory)
            {
                CategoryCell hovered = this.getCategoryCellAt(context.mouseX, context.mouseY);

                if (hovered != null && !hovered.isBackCard())
                {
                    handled = UIFormList.this.moveCategory(this.dragCategory, hovered.category);
                }
            }
            else if (this.dragCell != null
                && context.mouseX >= this.dragCell.x
                && context.mouseX < this.dragCell.x + CATEGORY_CARD_WIDTH
                && context.mouseY >= this.dragCell.y
                && context.mouseY < this.dragCell.y + CATEGORY_CARD_HEIGHT)
            {
                if (UIFormList.this.expandedCategory == this.dragCell.category)
                {
                    this.collapseExpandedCategory();
                }
                else
                {
                    UIFormList.this.expandedCategory = this.dragCell.category;

                    if (this.dragCell.category.category instanceof ModelFormCategory.Folder folder)
                    {
                        UIFormList.this.activeExpandedFolder = folder;
                    }
                    else
                    {
                        UIFormList.this.activeExpandedFolder = null;
                    }
                }

                this.invalidateCache();
                handled = true;
            }

            this.clearCategoryDragState();

            return handled || super.subMouseReleased(context);
        }

        @Override
        public void render(UIContext context)
        {
            this.updateAnimations(context);
            this.applyContentHeight();

            if (this.cardCells.isEmpty() && UIFormList.this.appliedSearchQuery.isEmpty())
            {
                return;
            }

            int scrollY = 0;
            int viewportHeight = this.area.h;

            if (this.getParent() instanceof UIScrollView scrollView)
            {
                scrollY = (int) scrollView.scroll.getScroll();
                viewportHeight = scrollView.area.h;
            }

            int top = Math.max(0, scrollY - CATEGORY_CARD_GAP);
            int bottom = Math.max(0, scrollY + viewportHeight + CATEGORY_CARD_GAP);

            /* Film viewport / previous UI 3D must not poison the first Batcher2D.box. */
            BBSRendering.restoreGuiRenderState();

            for (GroupDivider divider : this.groupDividers)
            {
                int dividerTop = divider.y - this.area.y;

                if (dividerTop + 12 < top || dividerTop > bottom)
                {
                    continue;
                }

                context.batcher.textShadow(divider.group.label.get(), this.area.x + CATEGORY_CARD_GAP, divider.y, Colors.LIGHTEST_GRAY);
                context.batcher.box(this.area.x + CATEGORY_CARD_GAP, divider.y + 10, this.area.ex() - CATEGORY_CARD_GAP, divider.y + 11, Colors.A100 | BBSSettings.primaryColor.get());
            }

            int buffer = CATEGORY_VIRTUALIZATION_BUFFER_ROWS * (CATEGORY_CARD_HEIGHT + CATEGORY_CARD_GAP);

            /* Two-pass: all 2D card chrome first, then all 3D form thumbnails.
             * Interleaving Batcher2D.box after FormUtilsClient.renderUI hard-crashes some
             * AMD drivers (atio6axx null deref in glDrawElements) on the next card's fill. */
            for (CategoryCell cell : this.cardCells)
            {
                int cellTop = cell.y - this.area.y;
                int cellBottom = cellTop + CATEGORY_CARD_HEIGHT;

                if (cellBottom < top - buffer || cellTop > bottom + buffer)
                {
                    continue;
                }

                this.renderCategoryCardChrome(context, cell.category, cell.x, cell.y);
            }

            BBSRendering.restoreGuiRenderState();

            for (CategoryCell cell : this.cardCells)
            {
                int cellTop = cell.y - this.area.y;
                int cellBottom = cellTop + CATEGORY_CARD_HEIGHT;

                if (cellBottom < top - buffer || cellTop > bottom + buffer)
                {
                    continue;
                }

                this.renderCategoryCardPreviews(context, cell.category, cell.x, cell.y);
            }

            BBSRendering.restoreGuiRenderState();

            if (UIFormList.this.expandedCategory != null || !UIFormList.this.appliedSearchQuery.isEmpty())
            {
                this.renderExpandedPanel(context);
            }

            BBSRendering.restoreGuiRenderState();

            if (this.dragCategory != null && !this.draggingCategory && UIFormList.this.userCategoryOrderUnlocked && System.currentTimeMillis() - this.dragStart > POPUP_DRAG_DELAY_MS)
            {
                this.draggingCategory = true;
            }

            if (this.draggingCategory && this.dragCategory != null)
            {
                CategoryCell hovered = this.getCategoryCellAt(context.mouseX, context.mouseY);

                if (hovered != null && !hovered.isBackCard() && UIFormList.this.canDropCategoryOn(this.dragCategory, hovered.category))
                {
                    context.batcher.box(hovered.x, hovered.y, hovered.x + CATEGORY_CARD_WIDTH, hovered.y + CATEGORY_CARD_HEIGHT, Colors.A25 | BBSSettings.primaryColor.get());
                    context.batcher.outline(hovered.x, hovered.y, hovered.x + CATEGORY_CARD_WIDTH, hovered.y + CATEGORY_CARD_HEIGHT, Colors.A100 | BBSSettings.primaryColor.get(), 2);
                }

                int previewX = context.mouseX - CATEGORY_CARD_WIDTH / 2;
                int previewY = context.mouseY - CATEGORY_CARD_HEIGHT / 2;
                String title = context.batcher.getFont().limitToWidth(this.dragCategory.category.getProcessedTitle(), CATEGORY_CARD_WIDTH - 16);

                context.batcher.box(previewX, previewY, previewX + CATEGORY_CARD_WIDTH, previewY + CATEGORY_CARD_HEIGHT, Colors.A50 | BBSSettings.primaryColor.get());
                context.batcher.outline(previewX, previewY, previewX + CATEGORY_CARD_WIDTH, previewY + CATEGORY_CARD_HEIGHT, Colors.A100 | BBSSettings.primaryColor.get(), 2);
                context.batcher.textShadow(title, previewX + 6, previewY + 6, Colors.WHITE);
                this.renderScaledIcon(context, Icons.ALL_DIRECTIONS, Colors.WHITE, previewX + CATEGORY_CARD_WIDTH / 2, previewY + CATEGORY_CARD_HEIGHT / 2, CATEGORY_DRAG_MOVE_ICON_SIZE);
            }
        }

        private void renderExpandedPanel(UIContext context)
        {
            if (this.expandedPanelH <= 0)
            {
                return;
            }

            /* Clip the entire panel to its animated height */
            context.batcher.clip(this.expandedPanelX, this.expandedPanelY, this.expandedPanelW, this.expandedPanelH, context);

            context.batcher.box(this.expandedPanelX, this.expandedPanelY, this.expandedPanelX + this.expandedPanelW, this.expandedPanelY + this.expandedPanelH, Colors.A50 | 0x111111);
            context.batcher.outline(this.expandedPanelX, this.expandedPanelY, this.expandedPanelX + this.expandedPanelW, this.expandedPanelY + this.expandedPanelH, Colors.A50 | BBSSettings.primaryColor.get());

            boolean hasBackArrow = false;
            String folderTitle;

            if (!UIFormList.this.appliedSearchQuery.isEmpty())
            {
                folderTitle = UIKeys.FORMS_LIST_SEARCH.get() + " (" + this.expandedItems.size() + ")";
            }
            else
            {
                hasBackArrow = UIFormList.this.activeExpandedFolder != null && UIFormList.this.activeExpandedFolder != UIFormList.this.expandedCategory.category;

                if (hasBackArrow)
                {
                    boolean hoverBack = context.mouseX >= this.expandedPanelX + 4 && context.mouseX < this.expandedPanelX + 24
                        && context.mouseY >= this.expandedPanelY + 4 && context.mouseY < this.expandedPanelY + 24;

                    if (hoverBack)
                    {
                        context.batcher.box(this.expandedPanelX + 4, this.expandedPanelY + 4, this.expandedPanelX + 24, this.expandedPanelY + 24, Colors.A25);
                        context.batcher.outline(this.expandedPanelX + 4, this.expandedPanelY + 4, this.expandedPanelX + 24, this.expandedPanelY + 24, Colors.A50);
                    }

                    context.batcher.icon(Icons.ARROW_LEFT, Colors.WHITE, this.expandedPanelX + 14, this.expandedPanelY + 14, 0.5F, 0.5F);
                }

                folderTitle = UIFormList.this.activeExpandedFolder != null ? UIFormList.this.activeExpandedFolder.getProcessedTitle() : UIFormList.this.expandedCategory.category.getProcessedTitle();
            }

            context.batcher.textShadow(folderTitle, this.expandedPanelX + (hasBackArrow ? 30 : 10), this.expandedPanelY + 8, Colors.WHITE);

            int gridY = this.expandedPanelY + EXPANDED_HEADER_HEIGHT + CATEGORY_CARD_GAP;
            int expandedPerRow = Math.max(1, (this.expandedPanelW - CATEGORY_CARD_GAP * 2) / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP));

            /* Render old items sliding out during a folder transition */
            if (this.folderTransition < 1F && !this.oldExpandedItems.isEmpty())
            {
                float easedFolder = this.easeOutCubic(this.folderTransition);

                this.renderExpandedItems(context, this.oldExpandedItems, this.oldExpandedPerRow, gridY, 1F - easedFolder, -this.transitionDirection);
            }

            /* Render current items (sliding in if folder transition is active) */
            if (!this.expandedItems.isEmpty())
            {
                float easedFolder = this.easeOutCubic(this.folderTransition);
                float newAlpha = easedFolder;
                int slideDirection = this.folderTransition < 1F ? this.transitionDirection : 0;

                this.renderExpandedItems(context, this.expandedItems, expandedPerRow, gridY, newAlpha, slideDirection);
            }
            else if (this.folderTransition >= 1F)
            {
                String msg = "No models found";
                context.batcher.textShadow(msg, this.expandedPanelX + (this.expandedPanelW - context.batcher.getFont().getWidth(msg)) / 2, gridY + 6, Colors.LIGHTEST_GRAY);
            }

            /* Clean up old items once transition finishes */
            if (this.folderTransition >= 1F && !this.oldExpandedItems.isEmpty())
            {
                this.oldExpandedItems.clear();
            }

            this.renderExpandedDragOverlay(context, expandedPerRow, gridY);

            context.batcher.unclip(context);

            float animT = this.expansionTransition < 1F ? this.expansionTransition : this.folderTransition;

            if (animT > 0F && animT < 1F)
            {
                this.renderAnimatingPreviews(context);
            }
        }

        private void renderAnimatingPreviews(UIContext context)
        {
            if (UIFormList.this.expandedCategory == null)
            {
                return;
            }

            CategoryCell cell = null;

            for (CategoryCell c : this.cardCells)
            {
                if (c.category == UIFormList.this.expandedCategory)
                {
                    cell = c;
                    break;
                }
            }

            if (cell == null)
            {
                return;
            }

            List<Form> categoryForms = UIFormList.this.expandedCategory.getForms();
            int shown = Math.min(4, categoryForms.size());

            if (shown <= 0)
            {
                return;
            }

            int previewAreaX = cell.x + CATEGORY_PREVIEW_PADDING;
            int previewAreaY = cell.y + 20;
            int previewAreaW = CATEGORY_CARD_WIDTH - CATEGORY_PREVIEW_PADDING * 2;
            int previewAreaH = CATEGORY_CARD_HEIGHT - 28;
            int cellW = (previewAreaW - CATEGORY_PREVIEW_GAP * (CATEGORY_PREVIEW_COLUMNS - 1)) / CATEGORY_PREVIEW_COLUMNS;
            int cellH = (previewAreaH - CATEGORY_PREVIEW_GAP * (CATEGORY_PREVIEW_ROWS - 1)) / CATEGORY_PREVIEW_ROWS;

            int gridY = this.expandedPanelY + EXPANDED_HEADER_HEIGHT + CATEGORY_CARD_GAP;
            int expandedPerRow = Math.max(1, (this.expandedPanelW - CATEGORY_CARD_GAP * 2) / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP));
            float animT = this.expansionTransition < 1F ? this.expansionTransition : this.folderTransition;
            float t = this.easeOutCubic(animT);

            for (int i = 0; i < shown; i++)
            {
                Form form = categoryForms.get(i);
                int itemIdx = -1;

                for (int j = 0; j < this.expandedItems.size(); j++)
                {
                    if (this.expandedItems.get(j).form == form)
                    {
                        itemIdx = j;
                        break;
                    }
                }

                if (itemIdx == -1)
                {
                    continue;
                }

                int col = itemIdx % expandedPerRow;
                int row = itemIdx / expandedPerRow;
                int cx = this.expandedPanelX + CATEGORY_CARD_GAP + col * (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP);
                int cy = gridY + row * (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP);

                int px = previewAreaX + (i % CATEGORY_PREVIEW_COLUMNS) * (cellW + CATEGORY_PREVIEW_GAP);
                int py = previewAreaY + (i / CATEGORY_PREVIEW_COLUMNS) * (cellH + CATEGORY_PREVIEW_GAP);

                int renderX = (int) (px + (cx - px) * t);
                int renderY = (int) (py + (cy - py) * t);
                int renderW = (int) (cellW + (EXPANDED_CELL_WIDTH - cellW) * t);
                int renderH = (int) (cellH + (EXPANDED_CELL_HEIGHT - cellH) * t);

                float alpha = 1F;
                int bgAlpha = (int) (alpha * 0x22) << 24;
                int outlineAlpha = (int) (alpha * 0x80) << 24;
                boolean selected = UIFormList.this.expandedCategory.selected == form;

                if (selected)
                {
                    float blink = 0.6F + 0.15F * (float) Math.sin(System.currentTimeMillis() * 0.005D);
                    int selAlpha = (int) (alpha * blink * 255) << 24;
                    context.batcher.box(renderX, renderY, renderX + renderW, renderY + renderH, selAlpha | (BBSSettings.primaryColor.get() & Colors.RGB));
                    context.batcher.outline(renderX, renderY, renderX + renderW, renderY + renderH, selAlpha | (BBSSettings.primaryColor.get() & Colors.RGB), 2);
                }
                else
                {
                    context.batcher.box(renderX, renderY, renderX + renderW, renderY + renderH, bgAlpha);
                    context.batcher.outline(renderX, renderY, renderX + renderW, renderY + renderH, outlineAlpha);
                }

                context.batcher.clip(renderX, renderY, renderW, renderH, context);
                UIFormList.this.renderFormThumbnail(context, form, renderX, renderY, renderX + renderW, renderY + renderH);
                context.batcher.unclip(context);
                BBSRendering.restoreGuiRenderState();

                FavoriteMarker marker = UIFormList.this.getFavoriteMarker(form);

                if (marker != null)
                {
                    context.batcher.outline(renderX, renderY, renderX + renderW, renderY + renderH, marker.color, 1);
                    context.batcher.icon(marker.icon, Colors.WHITE, renderX + renderW - 3, renderY + 2, 1F, 0F);
                }
            }
        }

        private void renderExpandedItems(UIContext context, List<ExpandedItem> items, int perRow, int gridY, float alpha, int slideDir)
        {
            if (items.isEmpty() || alpha <= 0F)
            {
                return;
            }

            int slideOffset = (int) ((1F - alpha) * this.expandedPanelW) * slideDir;

            if (this.dragFormIndex >= items.size())
            {
                this.dragFormIndex = -1;
                this.draggingForm = false;
                this.dragFormStart = -1L;
            }

            if (this.dragFormIndex != -1 && !this.draggingForm && UIFormList.this.expandedCategory.category instanceof UserFormCategory && System.currentTimeMillis() - this.dragFormStart > POPUP_DRAG_DELAY_MS)
            {
                this.draggingForm = true;
            }

            int localX = context.mouseX - this.expandedPanelX - CATEGORY_CARD_GAP - slideOffset;
            int localY = context.mouseY - gridY;
            int hoverIdx = -1;

            if (localX >= 0 && localY >= 0 && alpha >= 1F)
            {
                int col = localX / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP);
                int row = localY / (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP);
                int idx = col + row * perRow;

                if (idx >= 0 && idx < items.size())
                {
                    hoverIdx = idx;
                }
            }

            float animT = this.expansionTransition < 1F ? this.expansionTransition : this.folderTransition;

            /* Pass 1: cell chrome (no 3D). Pass 2: form thumbnails + favorite markers. */
            for (int idx = 0; idx < items.size(); idx++)
            {
                ExpandedItem item = items.get(idx);

                if (item.form != null && UIFormList.this.expandedCategory != null && animT < 1F)
                {
                    List<Form> categoryForms = UIFormList.this.expandedCategory.getForms();
                    int previewIdx = categoryForms.indexOf(item.form);

                    if (previewIdx >= 0 && previewIdx < 4)
                    {
                        continue;
                    }
                }

                int col = idx % perRow;
                int row = idx / perRow;
                int cx = this.expandedPanelX + CATEGORY_CARD_GAP + col * (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP) + slideOffset;
                int cy = gridY + row * (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP);

                this.renderExpandedItemChrome(context, item, cx, cy, idx == hoverIdx, alpha);
            }

            BBSRendering.restoreGuiRenderState();

            for (int idx = 0; idx < items.size(); idx++)
            {
                ExpandedItem item = items.get(idx);

                if (item.form == null)
                {
                    continue;
                }

                if (UIFormList.this.expandedCategory != null && animT < 1F)
                {
                    List<Form> categoryForms = UIFormList.this.expandedCategory.getForms();
                    int previewIdx = categoryForms.indexOf(item.form);

                    if (previewIdx >= 0 && previewIdx < 4)
                    {
                        continue;
                    }
                }

                int col = idx % perRow;
                int row = idx / perRow;
                int cx = this.expandedPanelX + CATEGORY_CARD_GAP + col * (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP) + slideOffset;
                int cy = gridY + row * (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP);

                this.renderExpandedItemForm(context, item, cx, cy, idx == hoverIdx);
            }
        }

        private void renderExpandedItemChrome(UIContext context, ExpandedItem item, int cx, int cy, boolean hover, float alpha)
        {
            int bgAlpha = (int) (alpha * 0x22) << 24;
            int outlineAlpha = (int) (alpha * 0x80) << 24;
            int textAlpha = (int) (alpha * 255) << 24;
            boolean selected = item.form != null && UIFormList.this.getSelected() == item.form;

            if (selected)
            {
                float blink = 0.6F + 0.15F * (float) Math.sin(System.currentTimeMillis() * 0.005D);
                int selAlpha = (int) (alpha * blink * 255) << 24;
                context.batcher.box(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, selAlpha | (BBSSettings.primaryColor.get() & Colors.RGB));
                context.batcher.outline(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, selAlpha | (BBSSettings.primaryColor.get() & Colors.RGB), 2);
            }
            else if (hover)
            {
                context.batcher.box(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, bgAlpha);
                context.batcher.outline(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, textAlpha);
            }
            else
            {
                context.batcher.box(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, bgAlpha);
                context.batcher.outline(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, outlineAlpha);
            }

            if (item.isFolder())
            {
                context.batcher.icon(Icons.FOLDER, textAlpha | (Colors.WHITE & Colors.RGB), cx + EXPANDED_CELL_WIDTH / 2, cy + EXPANDED_CELL_HEIGHT / 2 - 8, 0.5F, 0.5F);

                String folderName = item.folder.name;
                String limited = context.batcher.getFont().limitToWidth(folderName, EXPANDED_CELL_WIDTH - 8);
                int textX = cx + (EXPANDED_CELL_WIDTH - context.batcher.getFont().getWidth(limited)) / 2;
                int textY = cy + EXPANDED_CELL_HEIGHT - 16;

                context.batcher.textShadow(limited, textX, textY, textAlpha | (Colors.WHITE & Colors.RGB));
            }
        }

        private void renderExpandedItemForm(UIContext context, ExpandedItem item, int cx, int cy, boolean hover)
        {
            if (item.form == null)
            {
                return;
            }

            context.batcher.clip(cx, cy, EXPANDED_CELL_WIDTH, EXPANDED_CELL_HEIGHT, context);
            UIFormList.this.renderFormThumbnail(context, item.form, cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, hover);
            context.batcher.unclip(context);
            BBSRendering.restoreGuiRenderState();

            FavoriteMarker marker = UIFormList.this.getFavoriteMarker(item.form);

            if (marker != null)
            {
                context.batcher.outline(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, marker.color, 1);
                context.batcher.icon(marker.icon, Colors.WHITE, cx + EXPANDED_CELL_WIDTH - 3, cy + 2, 1F, 0F);
            }
        }

        private void renderExpandedDragOverlay(UIContext context, int expandedPerRow, int gridY)
        {
            if (!this.draggingForm || this.dragFormIndex < 0 || this.dragFormIndex >= this.expandedItems.size())
            {
                return;
            }

            ExpandedItem dragItem = this.expandedItems.get(this.dragFormIndex);

            if (dragItem.form == null)
            {
                return;
            }

            int localX = context.mouseX - this.expandedPanelX - CATEGORY_CARD_GAP;
            int localY = context.mouseY - gridY;
            int hoverIdx = -1;

            if (localX >= 0 && localY >= 0)
            {
                int col = localX / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP);
                int row = localY / (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP);
                int idx = col + row * expandedPerRow;

                if (idx >= 0 && idx < this.expandedItems.size())
                {
                    hoverIdx = idx;
                }
            }

            if (hoverIdx >= 0 && hoverIdx < this.expandedItems.size() && hoverIdx != this.dragFormIndex)
            {
                ExpandedItem hoverItem = this.expandedItems.get(hoverIdx);

                if (hoverItem.form != null)
                {
                    int px = this.expandedPanelX + CATEGORY_CARD_GAP + (hoverIdx % expandedPerRow) * (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP);
                    int py = gridY + (hoverIdx / expandedPerRow) * (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP);

                    context.batcher.box(px, py, px + EXPANDED_CELL_WIDTH, py + EXPANDED_CELL_HEIGHT, Colors.A25 | BBSSettings.primaryColor.get());
                    context.batcher.outline(px, py, px + EXPANDED_CELL_WIDTH, py + EXPANDED_CELL_HEIGHT, Colors.A100 | BBSSettings.primaryColor.get(), 2);
                }
            }

            int cx = context.mouseX - EXPANDED_CELL_WIDTH / 2;
            int cy = context.mouseY - EXPANDED_CELL_HEIGHT / 2;

            context.batcher.box(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, Colors.A50 | BBSSettings.primaryColor.get());
            context.batcher.outline(cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT, Colors.A100 | BBSSettings.primaryColor.get(), 2);
            UIFormList.this.renderFormThumbnail(context, dragItem.form, cx, cy, cx + EXPANDED_CELL_WIDTH, cy + EXPANDED_CELL_HEIGHT);
        }


        private void rebuildIfNeeded()
        {
            int perRow = Math.max(1, (this.area.w - CATEGORY_CARD_GAP) / (CATEGORY_CARD_WIDTH + CATEGORY_CARD_GAP));

            if (!this.dirty && this.cachedPerRow == perRow)
            {
                return;
            }

            this.cachedPerRow = perRow;
            this.filteredCategories.clear();
            this.cardCells.clear();
            this.groupDividers.clear();
            this.previewCache.clear();

            if (!UIFormList.this.appliedSearchQuery.isEmpty())
            {
                this.expandedItems.clear();
                for (UIFormCategory category : UIFormList.this.categories)
                {
                    List<Form> forms = category.getForms();
                    for (Form form : forms)
                    {
                        this.expandedItems.add(new ExpandedItem(form));
                    }
                }

                int expandedPerRow = Math.max(1, (this.area.w - CATEGORY_CARD_GAP * 2) / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP));
                int itemCount = this.expandedItems.size();
                int expandedRows = (itemCount + expandedPerRow - 1) / expandedPerRow;
                this.expandedPanelH = Math.max(20, expandedRows * (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP)) + EXPANDED_HEADER_HEIGHT + CATEGORY_CARD_GAP * 2;
                this.contentHeight = this.expandedPanelH + CATEGORY_CARD_GAP * 2;

                this.expandedPanelX = this.area.x + CATEGORY_CARD_GAP;
                this.expandedPanelY = this.area.y + CATEGORY_CARD_GAP;
                this.expandedPanelW = this.area.w - CATEGORY_CARD_GAP * 2;
                this.expansionTransition = 1F;
                this.targetExpansion = 1F;
                this.folderTransition = 1F;
                this.dirty = false;
                return;
            }

            Map<CardGroup, List<UIFormCategory>> groups = new LinkedHashMap<>();

            for (CardGroup group : CardGroup.values())
            {
                groups.put(group, new ArrayList<>());
            }

            for (UIFormCategory category : UIFormList.this.categories)
            {
                if (!UIFormList.this.isCategoryVisibleInCards(category))
                {
                    continue;
                }

                List<Form> forms = category.getForms();
                boolean isUserCategory = category instanceof UIUserFormCategory || category.category instanceof UserFormCategory;
                boolean isModelFolder = category.category instanceof ModelFormCategory.Folder;
                boolean includeWhenEmpty = !UIFormList.this.isFavoritesOnly() && (isUserCategory || isModelFolder);

                if (!forms.isEmpty() || includeWhenEmpty)
                {
                    this.filteredCategories.add(category);
                    this.previewCache.put(category, forms);
                    groups.get(this.getCardGroup(category)).add(category);
                }
            }

            if (UIFormList.this.expandedCategory != null && !this.filteredCategories.contains(UIFormList.this.expandedCategory))
            {
                UIFormList.this.expandedCategory = null;
                UIFormList.this.activeExpandedFolder = null;
                this.targetExpansion = 0F;
                this.expansionTransition = 0F;
                this.expandedPanelH = 0;
                this.expandedItems.clear();
                this.oldExpandedItems.clear();
            }

            int step = CATEGORY_CARD_HEIGHT + CATEGORY_CARD_GAP;
            int currentY = this.area.y + CATEGORY_CARD_GAP;
            boolean firstGroup = true;

            CardGroup expandedGroup = UIFormList.this.expandedCategory != null ? this.getCardGroup(UIFormList.this.expandedCategory) : null;

            for (Map.Entry<CardGroup, List<UIFormCategory>> entry : groups.entrySet())
            {
                List<UIFormCategory> groupCategories = entry.getValue();

                if (groupCategories.isEmpty())
                {
                    continue;
                }

                if (!firstGroup)
                {
                    currentY += CATEGORY_SECTION_GAP;
                }

                this.groupDividers.add(new GroupDivider(entry.getKey(), currentY));
                currentY += CATEGORY_GROUP_HEADER_HEIGHT;

                int cardsCount = groupCategories.size();
                boolean hasExpandedInGroup = entry.getKey() == expandedGroup && UIFormList.this.expandedCategory != null;
                int expandedIdx = hasExpandedInGroup ? groupCategories.indexOf(UIFormList.this.expandedCategory) : -1;
                int expandedRowNum = expandedIdx >= 0 ? expandedIdx / this.cachedPerRow : -1;
                int totalRows = (cardsCount + this.cachedPerRow - 1) / this.cachedPerRow;

                for (int rowNum = 0; rowNum < totalRows; rowNum++)
                {
                    int startI = rowNum * this.cachedPerRow;
                    int endI = Math.min(startI + this.cachedPerRow, cardsCount);

                    for (int i = startI; i < endI; i++)
                    {
                        int column = i % this.cachedPerRow;
                        int x = this.area.x + CATEGORY_CARD_GAP + column * (CATEGORY_CARD_WIDTH + CATEGORY_CARD_GAP);

                        this.cardCells.add(new CategoryCell(groupCategories.get(i), x, currentY, false));
                    }

                    currentY += step;

                    if (rowNum == expandedRowNum)
                    {
                        this.expandedPanelX = this.area.x + CATEGORY_CARD_GAP;
                        this.expandedPanelY = currentY;
                        this.expandedPanelW = this.area.w - CATEGORY_CARD_GAP * 2;

                        this.expandedItems.clear();

                        if (UIFormList.this.activeExpandedFolder != null)
                        {
                            for (ModelFormCategory.Folder subfolder : UIFormList.this.activeExpandedFolder.getChildren())
                            {
                                this.expandedItems.add(new ExpandedItem(subfolder));
                            }
                        }

                        FormCategory activeFolder = UIFormList.this.activeExpandedFolder != null ? UIFormList.this.activeExpandedFolder : UIFormList.this.expandedCategory.category;
                        UIFormCategory uiCategory = UIFormList.this.getUIFormCategory(activeFolder);
                        List<Form> forms = uiCategory != null ? uiCategory.getForms() : activeFolder.getForms();

                        for (Form form : forms)
                        {
                            this.expandedItems.add(new ExpandedItem(form));
                        }

                        int expandedPerRow = Math.max(1, (this.expandedPanelW - CATEGORY_CARD_GAP * 2) / (EXPANDED_CELL_WIDTH + CATEGORY_CARD_GAP));
                        int itemCount = this.expandedItems.size();
                        int expandedRows = (itemCount + expandedPerRow - 1) / expandedPerRow;
                        int gridHeight = Math.max(20, expandedRows * (EXPANDED_CELL_HEIGHT + CATEGORY_CARD_GAP));
                        int fullPanelH = EXPANDED_HEADER_HEIGHT + gridHeight + CATEGORY_CARD_GAP * 2;

                        this.expandedPanelH = (int) (fullPanelH * this.easeOutCubic(this.expansionTransition));
                        currentY += this.expandedPanelH + CATEGORY_CARD_GAP;
                    }
                }

                firstGroup = false;
            }

            this.contentHeight = currentY - this.area.y;
            this.dirty = false;
        }



        private CategoryCell getCategoryCellAt(int mouseX, int mouseY)
        {
            this.rebuildIfNeeded();

            for (CategoryCell cell : this.cardCells)
            {
                if (mouseX >= cell.x && mouseX < cell.x + CATEGORY_CARD_WIDTH && mouseY >= cell.y && mouseY < cell.y + CATEGORY_CARD_HEIGHT)
                {
                    return cell;
                }
            }

            return null;
        }

        private CardGroup getCardGroup(UIFormCategory category)
        {
            if (category instanceof UIRecentFormCategory)
            {
                return CardGroup.RECENT;
            }

            if (category.category instanceof UserFormCategory)
            {
                return CardGroup.USER_CREATED;
            }

            if (category instanceof UIParticleFormCategory)
            {
                return CardGroup.PARTICLES;
            }

            if (this.isBbsCategory(category))
            {
                return CardGroup.BBS;
            }

            return CardGroup.MODELS;
        }

        private boolean isBbsCategory(UIFormCategory category)
        {
            if (category.category.title instanceof LangKey langKey)
            {
                String key = langKey.key;
                if ("bbs.ui.forms.categories.extra".equals(key) ||
                    "bbs.ui.forms.categories.mobs_animals".equals(key) ||
                    "bbs.ui.forms.categories.mobs_neutral".equals(key) ||
                    "bbs.ui.forms.categories.mobs_hostile".equals(key) ||
                    "bbs.ui.forms.categories.mobs_misc".equals(key))
                {
                    return true;
                }
            }

            String title = this.normalize(category.category.getProcessedTitle());

            if (title.equals("miscelaneo") || title.equals("miscellaneous"))
            {
                return true;
            }

            return title.equals("mobs (animales)") || title.equals("mobs (neutrales)") || title.equals("mobs (hostiles)") || title.equals("mobs (miscelaneos)") || title.equals("mobs (misceláneos)") ||
                   title.equals("mobs (animals)") || title.equals("mobs (neutral)") || title.equals("mobs (hostile)") || title.equals("mobs (miscellaneous)");
        }

        private String normalize(String value)
        {
            if (value == null)
            {
                return "";
            }

            return value.toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .trim();
        }

        private boolean isToggleButtonAt(int mouseX, int mouseY, int cardX, int cardY)
        {
            int iconX = cardX + CATEGORY_CARD_WIDTH - CATEGORY_PREVIEW_TOGGLE_SIZE - 6;
            int iconY = cardY + 4;

            return mouseX >= iconX && mouseX < iconX + CATEGORY_PREVIEW_TOGGLE_SIZE && mouseY >= iconY && mouseY < iconY + CATEGORY_PREVIEW_TOGGLE_SIZE;
        }

        private boolean shouldShowModelBackCard()
        {
            return UIFormList.this.isModelFolderNavigationActive();
        }

        private void renderModelBackCard(UIContext context, int x, int y)
        {
            boolean hovered = context.mouseX >= x && context.mouseX < x + CATEGORY_CARD_WIDTH && context.mouseY >= y && context.mouseY < y + CATEGORY_CARD_HEIGHT;
            int baseColor = hovered ? (Colors.A50 | BBSSettings.primaryColor.get()) : Colors.A25;
            int outlineColor = hovered ? (Colors.A100 | BBSSettings.primaryColor.get()) : Colors.A100;
            String title = context.batcher.getFont().limitToWidth(UIKeys.FORMS_LIST_MODEL_FOLDER_BACK.get(), CATEGORY_CARD_WIDTH - 28);

            context.batcher.box(x, y, x + CATEGORY_CARD_WIDTH, y + CATEGORY_CARD_HEIGHT, baseColor);
            context.batcher.outline(x, y, x + CATEGORY_CARD_WIDTH, y + CATEGORY_CARD_HEIGHT, outlineColor);
            context.batcher.textShadow(title, x + 6, y + 4, Colors.LIGHTEST_GRAY | Colors.A100);
            this.renderScaledIcon(context, Icons.ARROW_LEFT, Colors.WHITE, x + CATEGORY_CARD_WIDTH / 2, y + CATEGORY_CARD_HEIGHT / 2 + 4, CATEGORY_MOVE_HANDLE_ICON_SIZE);
        }

        private void renderCategoryCardChrome(UIContext context, UIFormCategory category, int x, int y)
        {
            boolean previewsVisible = UIFormList.this.isCategoryPreviewVisible(category);
            boolean isOpen = UIFormList.this.expandedCategory == category;
            UIFormCategory selectedCategory = UIFormList.this.getSelectedCategory();
            boolean containsSelected = selectedCategory == category;
            boolean userCategory = category.category instanceof UserFormCategory;
            boolean movableCategory = UIFormList.this.isMovableCategory(category);
            boolean hoverMoveHandle = UIFormList.this.userCategoryOrderUnlocked && movableCategory && context.mouseX >= x && context.mouseX < x + CATEGORY_CARD_WIDTH && context.mouseY >= y && context.mouseY < y + CATEGORY_CARD_HEIGHT;
            int baseColor = Colors.A25;
            int outlineColor = Colors.A100;
            int titleColor = userCategory ? (Colors.LIGHTEST_GRAY | Colors.A100) : Colors.WHITE;

            if (isOpen || containsSelected)
            {
                int primary = BBSSettings.primaryColor.get() & Colors.RGB;

                if (containsSelected)
                {
                    /* Pulsating highlight */
                    float blink = 0.6F + 0.15F * (float) Math.sin(System.currentTimeMillis() * 0.005D);
                    int fillAlpha = (int) (blink * 0.5D * 255) << 24;
                    int lineAlpha = (int) (blink * 1.0D * 255) << 24;

                    baseColor = fillAlpha | primary;
                    outlineColor = lineAlpha | primary;
                }
                else
                {
                    /* Solid open highlight */
                    baseColor = Colors.A50 | primary;
                    outlineColor = Colors.A100 | primary;
                }
            }

            context.batcher.box(x, y, x + CATEGORY_CARD_WIDTH, y + CATEGORY_CARD_HEIGHT, baseColor);
            context.batcher.outline(x, y, x + CATEGORY_CARD_WIDTH, y + CATEGORY_CARD_HEIGHT, outlineColor);

            String title = context.batcher.getFont().limitToWidth(category.category.getProcessedTitle(), CATEGORY_CARD_WIDTH - 28);
            context.batcher.textShadow(title, x + 6, y + 4, titleColor);

            int iconX = x + CATEGORY_CARD_WIDTH - CATEGORY_PREVIEW_TOGGLE_SIZE - 6;
            int iconY = y + 4;
            int hoverColor = this.isToggleButtonAt(context.mouseX, context.mouseY, x, y) ? Colors.A100 : Colors.A50;
            context.batcher.box(iconX - 1, iconY - 1, iconX + CATEGORY_PREVIEW_TOGGLE_SIZE + 1, iconY + CATEGORY_PREVIEW_TOGGLE_SIZE + 1, hoverColor);
            context.batcher.icon(previewsVisible ? Icons.VISIBLE : Icons.INVISIBLE, Colors.WHITE, iconX + CATEGORY_PREVIEW_TOGGLE_SIZE / 2, iconY + CATEGORY_PREVIEW_TOGGLE_SIZE / 2, 0.5F, 0.5F);

            if (previewsVisible)
            {
                List<Form> forms = this.previewCache.getOrDefault(category, category.getForms());
                int previewAreaX = x + CATEGORY_PREVIEW_PADDING;
                int previewAreaY = y + 20;
                int previewAreaW = CATEGORY_CARD_WIDTH - CATEGORY_PREVIEW_PADDING * 2;
                int previewAreaH = CATEGORY_CARD_HEIGHT - 28;
                int cellW = (previewAreaW - CATEGORY_PREVIEW_GAP * (CATEGORY_PREVIEW_COLUMNS - 1)) / CATEGORY_PREVIEW_COLUMNS;
                int cellH = (previewAreaH - CATEGORY_PREVIEW_GAP * (CATEGORY_PREVIEW_ROWS - 1)) / CATEGORY_PREVIEW_ROWS;
                int maxPreview = CATEGORY_PREVIEW_COLUMNS * CATEGORY_PREVIEW_ROWS;
                int shown = Math.min(maxPreview, forms.size());
                float animT = this.expansionTransition < 1F ? this.expansionTransition : this.folderTransition;

                if (!(category == UIFormList.this.expandedCategory && animT > 0F))
                {
                    for (int i = 0; i < shown; i++)
                    {
                        int px = previewAreaX + (i % CATEGORY_PREVIEW_COLUMNS) * (cellW + CATEGORY_PREVIEW_GAP);
                        int py = previewAreaY + (i / CATEGORY_PREVIEW_COLUMNS) * (cellH + CATEGORY_PREVIEW_GAP);

                        context.batcher.box(px, py, px + cellW, py + cellH, Colors.A25);
                    }
                }
            }
            else
            {
                int hiddenIconX = x + (CATEGORY_CARD_WIDTH - CATEGORY_HIDDEN_ICON_SIZE) / 2;
                int hiddenIconY = y + (CATEGORY_CARD_HEIGHT - CATEGORY_HIDDEN_ICON_SIZE) / 2 + 6;
                context.batcher.texturedBox(
                    BBSModClient.getTextures().getTexture(Icons.CROSSED_OUT_EYE.texture),
                    Colors.LIGHTEST_GRAY,
                    hiddenIconX,
                    hiddenIconY,
                    CATEGORY_HIDDEN_ICON_SIZE,
                    CATEGORY_HIDDEN_ICON_SIZE,
                    Icons.CROSSED_OUT_EYE.x,
                    Icons.CROSSED_OUT_EYE.y,
                    Icons.CROSSED_OUT_EYE.x + Icons.CROSSED_OUT_EYE.w,
                    Icons.CROSSED_OUT_EYE.y + Icons.CROSSED_OUT_EYE.h,
                    Icons.CROSSED_OUT_EYE.textureW,
                    Icons.CROSSED_OUT_EYE.textureH
                );
            }

            if (hoverMoveHandle)
            {
                this.renderScaledIcon(context, Icons.ALL_DIRECTIONS, Colors.A100 | BBSSettings.primaryColor.get(), x + CATEGORY_CARD_WIDTH / 2, y + CATEGORY_CARD_HEIGHT / 2, CATEGORY_MOVE_HANDLE_ICON_SIZE);
            }
        }

        private void renderCategoryCardPreviews(UIContext context, UIFormCategory category, int x, int y)
        {
            if (!UIFormList.this.isCategoryPreviewVisible(category))
            {
                return;
            }

            List<Form> forms = this.previewCache.getOrDefault(category, category.getForms());
            int previewAreaX = x + CATEGORY_PREVIEW_PADDING;
            int previewAreaY = y + 20;
            int previewAreaW = CATEGORY_CARD_WIDTH - CATEGORY_PREVIEW_PADDING * 2;
            int previewAreaH = CATEGORY_CARD_HEIGHT - 28;
            int cellW = (previewAreaW - CATEGORY_PREVIEW_GAP * (CATEGORY_PREVIEW_COLUMNS - 1)) / CATEGORY_PREVIEW_COLUMNS;
            int cellH = (previewAreaH - CATEGORY_PREVIEW_GAP * (CATEGORY_PREVIEW_ROWS - 1)) / CATEGORY_PREVIEW_ROWS;
            int maxPreview = CATEGORY_PREVIEW_COLUMNS * CATEGORY_PREVIEW_ROWS;
            int shown = Math.min(maxPreview, forms.size());
            float animT = this.expansionTransition < 1F ? this.expansionTransition : this.folderTransition;

            if (category == UIFormList.this.expandedCategory && animT > 0F)
            {
                return;
            }

            for (int i = 0; i < shown; i++)
            {
                int px = previewAreaX + (i % CATEGORY_PREVIEW_COLUMNS) * (cellW + CATEGORY_PREVIEW_GAP);
                int py = previewAreaY + (i / CATEGORY_PREVIEW_COLUMNS) * (cellH + CATEGORY_PREVIEW_GAP);

                context.batcher.clip(px, py, cellW, cellH, context);
                UIFormList.this.renderFormThumbnailStatic(context, forms.get(i), px, py, px + cellW, py + cellH);
                context.batcher.unclip(context);
            }

            BBSRendering.restoreGuiRenderState();

            for (int i = 0; i < shown; i++)
            {
                int px = previewAreaX + (i % CATEGORY_PREVIEW_COLUMNS) * (cellW + CATEGORY_PREVIEW_GAP);
                int py = previewAreaY + (i / CATEGORY_PREVIEW_COLUMNS) * (cellH + CATEGORY_PREVIEW_GAP);
                FavoriteMarker marker = UIFormList.this.getFavoriteMarker(forms.get(i));

                if (marker != null)
                {
                    context.batcher.outline(px, py, px + cellW, py + cellH, marker.color);
                }
                else
                {
                    context.batcher.outline(px, py, px + cellW, py + cellH, Colors.A50);
                }
            }

            if (forms.size() > maxPreview)
            {
                String count = "+" + (forms.size() - maxPreview);
                int width = context.batcher.getFont().getWidth(count) + 6;
                int badgeX = x + CATEGORY_CARD_WIDTH - width - 6;
                int badgeY = y + CATEGORY_CARD_HEIGHT - 16;

                context.batcher.box(badgeX, badgeY, badgeX + width, badgeY + 12, Colors.A100 | 0x1a1a1a);
                context.batcher.textShadow(count, badgeX + 3, badgeY + 2);
            }
        }

        private void renderScaledIcon(UIContext context, Icon icon, int color, int centerX, int centerY, int size)
        {
            int half = Math.max(1, size / 2);
            int x = centerX - half;
            int y = centerY - half;

            context.batcher.texturedBox(
                BBSModClient.getTextures().getTexture(icon.texture),
                color,
                x,
                y,
                size,
                size,
                icon.x,
                icon.y,
                icon.x + icon.w,
                icon.y + icon.h,
                icon.textureW,
                icon.textureH
            );
        }

        private void clearCategoryDragState()
        {
            this.dragCategory = null;
            this.dragCell = null;
            this.dragStart = -1L;
            this.draggingCategory = false;
        }

        private class CategoryCell
        {
            private final UIFormCategory category;
            private final int x;
            private final int y;
            private final boolean backCard;

            private CategoryCell(UIFormCategory category, int x, int y, boolean backCard)
            {
                this.category = category;
                this.x = x;
                this.y = y;
                this.backCard = backCard;
            }

            private boolean isBackCard()
            {
                return this.backCard;
            }
        }

        private class GroupDivider
        {
            private final CardGroup group;
            private final int y;

            private GroupDivider(CardGroup group, int y)
            {
                this.group = group;
                this.y = y;
            }
        }
    }

    private class UICategoryPopupGrid extends UIElement
    {
        private final UIFormCategory category;
        private final Map<UIElement, UIFormCategory> dropTargets;
        private int lastHeight;
        private int dragIndex = -1;
        private long dragStart = -1L;
        private boolean dragging;

        public UICategoryPopupGrid(UIFormCategory category, Map<UIElement, UIFormCategory> dropTargets)
        {
            this.category = category;
            this.dropTargets = dropTargets;
        }

        public boolean isDraggingModel()
        {
            return this.dragging && this.dragIndex >= 0 && this.dragIndex < this.category.getForms().size() && this.category.category instanceof UserFormCategory;
        }

        public UIFormCategory getHoveredDropCategory(int mouseX, int mouseY)
        {
            if (!this.isDraggingModel())
            {
                return null;
            }

            for (Map.Entry<UIElement, UIFormCategory> entry : this.dropTargets.entrySet())
            {
                UIFormCategory targetCategory = entry.getValue();

                if (targetCategory == this.category || !(targetCategory.category instanceof UserFormCategory))
                {
                    continue;
                }

                if (entry.getKey().area.isInside(mouseX, mouseY))
                {
                    return targetCategory;
                }
            }

            return null;
        }

        private int getHoverDropIndex(int mouseX, int mouseY, int size)
        {
            if (!this.isDraggingModel() || size <= 0 || !this.area.isInside(mouseX, mouseY))
            {
                return -1;
            }

            int perRow = Math.max(1, this.area.w / POPUP_CELL_WIDTH);
            int localX = Math.max(0, mouseX - this.area.x);
            int localY = Math.max(0, mouseY - this.area.y);
            int column = Math.min(perRow - 1, localX / POPUP_CELL_WIDTH);
            int row = Math.max(0, localY / POPUP_CELL_HEIGHT);

            return Math.min(size - 1, column + row * perRow);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (!this.area.isInside(context) || (context.mouseButton != 0 && context.mouseButton != 1))
            {
                return false;
            }

            List<Form> forms = this.category.getForms();
            int x = context.mouseX - this.area.x;
            int y = context.mouseY - this.area.y;
            int perRow = Math.max(1, this.area.w / POPUP_CELL_WIDTH);
            int index = x / POPUP_CELL_WIDTH + (y / POPUP_CELL_HEIGHT) * perRow;

            if (context.mouseButton == 0)
            {
                if (index < 0 || index >= forms.size())
                {
                    UIFormList.this.deselect();

                    return true;
                }

                if (this.category.category instanceof UserFormCategory)
                {
                    this.dragIndex = index;
                    this.dragStart = System.currentTimeMillis();
                    this.dragging = false;
                }

                this.category.select(forms.get(index), true);

                return true;
            }

            if (index >= 0 && index < forms.size())
            {
                this.category.select(forms.get(index), true);
            }
            else
            {
                this.category.select(null, false);
            }

            UIContextMenu menu = this.category.createContextMenu(context);

            if (menu != null && !menu.isEmpty())
            {
                context.setContextMenu(menu);
            }

            return true;
        }

        @Override
        public void render(UIContext context)
        {
            List<Form> forms = this.category.getForms();
            int perRow = Math.max(1, this.area.w / POPUP_CELL_WIDTH);
            int h = 0;
            int x = 0;
            int i = 0;

            if (this.dragIndex >= forms.size())
            {
                this.dragIndex = -1;
                this.dragging = false;
                this.dragStart = -1L;
            }

            if (this.dragIndex != -1 && !this.dragging && this.category.category instanceof UserFormCategory && System.currentTimeMillis() - this.dragStart > POPUP_DRAG_DELAY_MS)
            {
                this.dragging = true;
            }

            for (Form form : forms)
            {
                if (i == perRow)
                {
                    h += POPUP_CELL_HEIGHT;
                    x = 0;
                    i = 0;
                }

                int cx = this.area.x + x;
                int cy = this.area.y + h;
                boolean selected = this.category.selected == form;

                if (selected)
                {
                    float blink = 0.6F + 0.15F * (float) Math.sin(System.currentTimeMillis() * 0.005D);
                    int selAlpha = (int) (blink * 255) << 24;
                    context.batcher.box(cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT, selAlpha | (BBSSettings.primaryColor.get() & Colors.RGB));
                    context.batcher.outline(cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT, selAlpha | (BBSSettings.primaryColor.get() & Colors.RGB), 2);
                }
                else
                {
                    context.batcher.box(cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT, Colors.A25);
                }

                x += POPUP_CELL_WIDTH;
                i += 1;
            }

            BBSRendering.restoreGuiRenderState();

            x = 0;
            i = 0;
            h = 0;

            for (Form form : forms)
            {
                if (i == perRow)
                {
                    h += POPUP_CELL_HEIGHT;
                    x = 0;
                    i = 0;
                }

                int cx = this.area.x + x;
                int cy = this.area.y + h;

                context.batcher.clip(cx, cy, POPUP_CELL_WIDTH, POPUP_CELL_HEIGHT, context);
                UIFormList.this.renderFormThumbnail(context, form, cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT);
                context.batcher.unclip(context);
                BBSRendering.restoreGuiRenderState();

                FavoriteMarker marker = UIFormList.this.getFavoriteMarker(form);

                if (marker != null)
                {
                    context.batcher.outline(cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT, marker.color);
                }
                else
                {
                    context.batcher.outline(cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT, Colors.A50);
                }

                x += POPUP_CELL_WIDTH;
                i += 1;
            }

            h += POPUP_CELL_HEIGHT;

            if (this.lastHeight != h)
            {
                this.lastHeight = h;
                this.h(h);

                if (this.getParent() != null)
                {
                    this.getParent().resize();
                }
            }

            if (this.dragging && this.dragIndex >= 0 && this.dragIndex < forms.size() && this.category.category instanceof UserFormCategory)
            {
                int hoverDropIndex = this.getHoverDropIndex(context.mouseX, context.mouseY, forms.size());

                if (hoverDropIndex >= 0 && hoverDropIndex < forms.size() && hoverDropIndex != this.dragIndex)
                {
                    int previewX = this.area.x + (hoverDropIndex % perRow) * POPUP_CELL_WIDTH;
                    int previewY = this.area.y + (hoverDropIndex / perRow) * POPUP_CELL_HEIGHT;

                    context.batcher.box(previewX, previewY, previewX + POPUP_CELL_WIDTH, previewY + POPUP_CELL_HEIGHT, Colors.A25 | BBSSettings.primaryColor.get());
                    context.batcher.outline(previewX, previewY, previewX + POPUP_CELL_WIDTH, previewY + POPUP_CELL_HEIGHT, Colors.A100 | BBSSettings.primaryColor.get(), 2);
                }

                int cx = context.mouseX - POPUP_CELL_WIDTH / 2;
                int cy = context.mouseY - POPUP_CELL_HEIGHT / 2;

                context.batcher.box(cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT, Colors.A50 | BBSSettings.primaryColor.get());
                context.batcher.outline(cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT, Colors.A100 | BBSSettings.primaryColor.get(), 2);
                UIFormList.this.renderFormThumbnail(context, forms.get(this.dragIndex), cx, cy, cx + POPUP_CELL_WIDTH, cy + POPUP_CELL_HEIGHT);
            }
        }

        @Override
        public boolean subMouseReleased(UIContext context)
        {
            if (this.dragIndex != -1)
            {
                if (this.dragging && this.category.category instanceof UserFormCategory)
                {
                    List<Form> forms = this.category.getForms();

                    if (!forms.isEmpty())
                    {
                        UIFormCategory targetCategory = this.getHoveredDropCategory(context.mouseX, context.mouseY);

                        if (targetCategory != null)
                        {
                            Form form = forms.get(this.dragIndex);
                            ((UserFormCategory) targetCategory.category).addForm(form);
                            this.category.category.removeForm(form);
                            UIFormList.this.openCategoryPopup(targetCategory);
                            this.dragIndex = -1;
                            this.dragStart = -1L;
                            this.dragging = false;

                            return true;
                        }

                        if (this.area.isInside(context.mouseX, context.mouseY))
                        {
                            int targetIndex = this.getHoverDropIndex(context.mouseX, context.mouseY, forms.size());

                            if (targetIndex >= 0 && targetIndex < forms.size() && targetIndex != this.dragIndex)
                            {
                                ((UserFormCategory) this.category.category).moveForm(this.dragIndex, targetIndex);
                            }
                        }
                    }
                }

                this.dragIndex = -1;
                this.dragStart = -1L;
                this.dragging = false;
            }

            return super.subMouseReleased(context);
        }
    }

    public static class ExpandedItem
    {
        public final ModelFormCategory.Folder folder;
        public final Form form;

        public ExpandedItem(ModelFormCategory.Folder folder)
        {
            this.folder = folder;
            this.form = null;
        }

        public ExpandedItem(Form form)
        {
            this.folder = null;
            this.form = form;
        }

        public boolean isFolder()
        {
            return this.folder != null;
        }
    }

    public static class FavoriteMarker

    {
        public final String categoryId;
        public final String name;
        public final Icon icon;
        public final int color;

        public FavoriteMarker(String categoryId, String name, Icon icon, int color)
        {
            this.categoryId = categoryId;
            this.name = name;
            this.icon = icon == null ? Icons.FIVE_STAR : icon;
            this.color = color;
        }
    }

    private static class UICategoryPickOverlayPanel extends UIOverlayPanel
    {
        private final List<FavoriteMarker> options;
        private final UICategoryPickList list;

        public UICategoryPickOverlayPanel(List<FavoriteMarker> options, Consumer<FavoriteMarker> callback)
        {
            super(UIKeys.FORMS_LIST_CATEGORIES_PICK_CATEGORY);

            this.options = options == null ? List.of() : options;
            this.list = new UICategoryPickList(this.options, (marker) ->
            {
                if (callback != null)
                {
                    callback.accept(marker);
                }

                this.close();
            });

            UISearchList<FavoriteMarker> searchList = new UISearchList<>(this.list);
            searchList.label(UIKeys.GENERAL_SEARCH).full(this.content).x(6).w(1F, -12).h(1F, -6);
            this.content.add(searchList);
        }

        public void setCurrent(String categoryId)
        {
            this.list.setCurrentById(categoryId);
        }
    }

    private static class UICategoryPickList extends UIList<FavoriteMarker>
    {
        private final Consumer<FavoriteMarker> onPicked;

        public UICategoryPickList(List<FavoriteMarker> options, Consumer<FavoriteMarker> callback)
        {
            super((values) ->
            {
                if (callback != null && !values.isEmpty())
                {
                    callback.accept(values.get(0));
                }
            });

            this.onPicked = callback;
            this.add(options == null ? List.of() : options);
            this.scroll.scrollItemSize = 20;
        }

        public void setCurrentById(String categoryId)
        {
            if (categoryId == null)
            {
                return;
            }

            for (int i = 0; i < this.list.size(); i++)
            {
                if (categoryId.equals(this.list.get(i).categoryId))
                {
                    this.setCurrentScroll(this.list.get(i));

                    return;
                }
            }
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            boolean result = super.subMouseClicked(context);

            if (result)
            {
                return true;
            }

            if (context.mouseButton != 0 || !this.area.isInside(context) || this.list.isEmpty())
            {
                return false;
            }

            int lastIndex = this.list.size() - 1;
            int index = this.scroll.getIndex(context.mouseX, context.mouseY);

            if (index == -2 || index > lastIndex)
            {
                this.setIndex(lastIndex);

                if (this.onPicked != null)
                {
                    this.onPicked.accept(this.list.get(lastIndex));
                }

                return true;
            }

            return false;
        }

        @Override
        public void renderListElement(UIContext context, FavoriteMarker marker, int i, int x, int y, boolean hover, boolean selected)
        {
            int base = marker == null ? BBSSettings.primaryColor.get() : marker.color & Colors.RGB;
            int stripeColor = Colors.A100 | base;
            int gradientTo = selected ? Colors.mulRGB(base, 0.9F) : base;

            if (selected)
            {
                context.batcher.box(x, y, x + this.area.w, y + this.scroll.scrollItemSize, Colors.A25);
            }

            context.batcher.box(x, y, x + 2, y + this.scroll.scrollItemSize, stripeColor);
            context.batcher.gradientHBox(x + 2, y, x + 24, y + this.scroll.scrollItemSize, Colors.A25 | base, gradientTo);

            this.renderElementPart(context, marker, i, x, y, hover, selected);
        }

        @Override
        protected void renderElementPart(UIContext context, FavoriteMarker marker, int i, int x, int y, boolean hover, boolean selected)
        {
            int textColor = hover || selected ? Colors.HIGHLIGHT : Colors.WHITE;

            if (marker == null)
            {
                super.renderElementPart(context, marker, i, x, y, hover, selected);
                return;
            }

            context.batcher.icon(marker.icon, Colors.WHITE, x + 12, y + this.scroll.scrollItemSize / 2, 0.5F, 0.5F);
            context.batcher.textShadow(marker.name, x + 24, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, textColor);
        }

        @Override
        protected String elementToString(UIContext context, int i, FavoriteMarker marker)
        {
            return marker == null ? "" : marker.name;
        }
    }

    private static class FavoriteCategory
    {
        public final String id;
        public final String name;
        public final String icon;
        public final int color;

        public FavoriteCategory(String id, String name, String icon, int color)
        {
            this.id = id;
            this.name = name;
            this.icon = icon == null || icon.isBlank() ? "five_star" : icon;
            this.color = color;
        }
    }

}
