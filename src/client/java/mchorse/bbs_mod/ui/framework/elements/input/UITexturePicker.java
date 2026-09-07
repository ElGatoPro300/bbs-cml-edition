package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.importers.IImportPathProvider;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.UITexturePainter;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIFileLinkList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIFilteredLinkList;
import mchorse.bbs_mod.ui.framework.elements.input.multilink.UIMultiLinkEditor;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.navigation.UIIconTabButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMcmetaEditorPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.ui.utils.presets.UIPresetContextMenu;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.resources.FilteredLink;
import mchorse.bbs_mod.utils.resources.LinkUtils;
import mchorse.bbs_mod.utils.resources.MultiLink;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;

/**
 * Texture picker GUI
 * 
 * This bad boy allows picking a texture from the file browser, and also 
 * it allows creating multi-skins. See {@link MultiLink} for more information.
 */
public class UITexturePicker extends UIElement implements IImportPathProvider
{
    public UIElement right;
    public UITextbox text;
    public UIIcon close;
    public UIIcon folder;
    public UIIcon pixelEdit;
    public UIIcon viewMode;
    public UIIcon previewSettings;
    public UIElement headerIcons;
    public UIElement editorToolbar;
    public UIFileLinkList picker;
    public UIElement textureHeader;
    public UIElement textureTabs;
    public UIIconTabButton tabFiles;

    public UIButton multi;
    public UIFilteredLinkList multiList;
    public UIMultiLinkEditor editor;
    public UITexturePainter pixelEditor;

    public UIElement buttons;
    public UIIcon add;
    public UIIcon remove;
    public UIIcon edit;

    public UIElement options;
    public UIElement texturePreview;
    public UIElement texturePreviewPopup;
    public UIToggle linear;
    public UIToggle mipmap;
    public UIElement formPreviewArea;
    public UIFormRenderer formPreview;

    public Consumer<Link> callback;

    public boolean notifyingCallbackClose;

    public MultiLink multiLink;
    public FilteredLink currentFiltered;
    public Link current;

    private Timer lastTyped = new Timer(1000);
    private Timer lastChecked = new Timer(1000);
    private String typed = "";
    private boolean canBeClosed = true;
    private boolean pixelEditorEnabled = true;
    private boolean multiSkinEnabled = true;
    private Supplier<Form> formPreviewSupplier;
    private Supplier<List<Link>> highlighted;
    private static final int FORM_PREVIEW_WIDTH = 150;
    private static final int LIST_ITEM_SIZE_SMALL = 16;
    private static final int TAB_WIDTH_FILES = 88;
    private static final int TOP_TABS_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 44;
    private static final int TOP_ROW_Y = 22;
    private static final int MULTI_BUTTON_WIDTH = 100;
    private static final int MULTI_SIDEBAR_WIDTH = 120;
    private static final int MULTI_SIDEBAR_TOP_GAP = 4;
    private static final int CONTENT_Y_FILES = HEADER_HEIGHT + 4;
    private static final int CONTENT_Y_EDITOR = TOP_TABS_HEIGHT + 2;
    private static final int TAB_FILES = 0;
    private static final int TAB_EDITOR = 1;
    private static final int PREVIEW_POPUP_WIDTH = 220;
    private static final int PREVIEW_POPUP_HEIGHT = 190;

    private UICopyPasteController copyPasteController;
    private int activeTab = TAB_FILES;
    private Link activeEditorTexture;
    private final List<EditorTabEntry> editorTabs = new ArrayList<>();

    private static class EditorTabEntry
    {
        public final Link texture;
        public final UIIconTabButton tab;

        public EditorTabEntry(Link texture, UIIconTabButton tab)
        {
            this.texture = texture;
            this.tab = tab;
        }
    }

    public static UITexturePicker open(UIContext context, Link current, Consumer<Link> callback)
    {
        return open(context.menu.overlay, current, callback);
    }

    public static UITexturePicker open(UIElement parent, Link current, Consumer<Link> callback)
    {
        if (!parent.getChildren(UITexturePicker.class).isEmpty())
        {
            return null;
        }

        UITexturePicker picker = new UITexturePicker(callback);

        picker.full(parent);
        picker.resize();
        picker.fill(current);

        parent.add(picker);

        return picker;
    }

    public static void findAllTextures(UIContext context, Link current, Consumer<String> callback)
    {
        List<String> list = new ArrayList<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets("")))
        {
            String string = link.toString();
            String lower = string.toLowerCase();

            if (!string.contains(":textures/banners/") && (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) list.add(string);
        }

        for (Link link : BBSMod.getProvider().getLinksFromPath(new Link("http", "")))
        {
            String string = link.toString();
            String lower = string.toLowerCase();

            if (lower.contains(".png") || lower.contains(".jpg") || lower.contains(".jpeg")) list.add(string);
        }

        for (Link link : BBSMod.getProvider().getLinksFromPath(new Link("https", "")))
        {
            String string = link.toString();
            String lower = string.toLowerCase();

            if (lower.contains(".png") || lower.contains(".jpg") || lower.contains(".jpeg")) list.add(string);
        }

        UIListOverlayPanel panel = new UIListOverlayPanel(UIKeys.TEXTURE_FIND_TITLE, callback);
        panel.resizable().minSize(360, 240);

        panel.addValues(list);
        panel.list.list.sort();

        if (current != null)
        {
            panel.setValue(current.toString());
        }

        UIOverlay.addOverlay(context, panel);
    }

    public UITexturePicker(Consumer<Link> callback)
    {
        super();

        this.copyPasteController = new UICopyPasteController(PresetManager.TEXTURES, "_CopyTexture")
            .supplier(this::copyLink)
            .consumer((data, x, y) -> this.pasteLink(this.parseLink(data)))
            .canCopy(() -> this.current != null);

        this.right = new UIElement();
        this.textureHeader = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                int topRowColor = Colors.CONTROL_BAR;
                int bottomRowColor = Colors.mulRGB(Colors.CONTROL_BAR, 0.86F);

                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + TOP_TABS_HEIGHT, topRowColor);
                context.batcher.box(this.area.x, this.area.y + TOP_TABS_HEIGHT, this.area.ex(), this.area.ey(), bottomRowColor);
                context.batcher.box(this.area.x, this.area.y + TOP_TABS_HEIGHT, this.area.ex(), this.area.y + TOP_TABS_HEIGHT + 1, Colors.A50);
                context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), Colors.A75);

                super.render(context);
            }
        };
        this.textureTabs = new UIElement();
        this.tabFiles = new UIIconTabButton(UIKeys.TEXTURES_TAB_FILES, Icons.FOLDER, (b) -> this.setActiveTab(TAB_FILES));
        this.text = new UITextbox(1000, (str) -> this.selectCurrent(str.isEmpty() ? null : LinkUtils.create(str)));
        this.text.delayedInput().context((menu) ->
        {
            menu.custom(new UIPresetContextMenu(this.copyPasteController)
                .labels(UIKeys.TEXTURE_EDITOR_CONTEXT_COPY, UIKeys.TEXTURE_EDITOR_CONTEXT_PASTE));

            if (this.current != null)
            {
                menu.action(Icons.COPY, UIKeys.TEXTURES_COPY, () -> Window.setClipboard(this.current.toString()));
            }

            File file = BBSMod.getProvider().getFile(this.current);

            if (file != null && file.isFile() && file.getName().endsWith(".png"))
            {
                File mcmeta = new File(file.getAbsolutePath() + ".mcmeta");

                if (!mcmeta.exists())
                {
                    menu.action(Icons.ADD, UIKeys.TEXTURES_CREATE_MCMETA, () ->
                    {
                        MapType data = DataToString.mapFromString("{\"animation\":{\"frametime\":2}}");

                        DataToString.writeSilently(mcmeta, data, true);
                    });
                }
                else
                {
                    menu.action(Icons.EDIT, UIKeys.TEXTURES_MCMETA_EDIT, () ->
                    {
                    UIOverlay.addOverlay(this.getContext(), new UIMcmetaEditorPanel(mcmeta), 240, 160);
                });
                }
            }

            menu.action(Icons.DOWNLOAD, UIKeys.TEXTURES_DOWNLOAD, () -> this.download(""));
        });
        this.close = new UIIcon(Icons.CLOSE, (b) -> this.close());
        this.folder = new UIIcon(Icons.FOLDER, (b) -> this.openFolder());
        this.folder.tooltip(UIKeys.TEXTURE_OPEN_FOLDER, Direction.BOTTOM);
        this.pixelEdit = new UIIcon(Icons.EDIT, (b) -> this.togglePixelEditor());
        this.previewSettings = new UIIcon(Icons.IMAGE, (b) -> this.openTexturePreviewPanel());
        this.previewSettings.tooltip(UIKeys.TEXTURES_PREVIEW_TOOLTIP, Direction.BOTTOM);
        this.viewMode = new UIIcon(() -> this.picker != null && this.picker.getItemSize() <= UIFileLinkList.VIEW_LIST ? Icons.LIST : Icons.BLOCK, (b) ->
        {
            this.openViewPresetMenu();
        });
        this.picker = new UIFileLinkList(this::selectCurrent)
        {
            @Override
            public void setPath(Link folder, boolean fastForward)
            {
                super.setPath(folder, fastForward);
                UITexturePicker.this.updateFolderButton();
            }

            @Override
            public void setItemSize(int size)
            {
                super.setItemSize(size);

                if (BBSSettings.texturePickerItemSize != null)
                {
                    BBSSettings.texturePickerItemSize.set(this.getItemSize());
                }
            }
        };
        this.picker.filter((l) ->
        {
            String path = l.path.toLowerCase();

            return path.endsWith("/") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg");
        }).cancelScrollEdge();
        int savedItemSize = BBSSettings.texturePickerItemSize == null ? LIST_ITEM_SIZE_SMALL : BBSSettings.texturePickerItemSize.get();
        this.picker.setItemSize(savedItemSize);

        this.linear = new UIToggle(UIKeys.TEXTURES_LINEAR, (b) ->
        {
            Link link = this.current;

            /* Draw preview */
            if (link != null)
            {
                Texture texture = BBSModClient.getTextures().getTexture(link);
                int filter = b.getValue() ? GL11.GL_LINEAR : GL11.GL_NEAREST;

                if (texture.isReallyMipmap())
                {
                    filter = b.getValue() ? GL30.GL_LINEAR_MIPMAP_NEAREST : GL30.GL_NEAREST_MIPMAP_NEAREST;
                }

                texture.bind();
                texture.setFilter(filter);
            }
        });

        this.mipmap = new UIToggle(UIKeys.TEXTURES_MIPMAP, (b) ->
        {
            Link link = this.current;

            /* Draw preview */
            if (link != null)
            {
                Texture texture = BBSModClient.getTextures().getTexture(link);

                texture.bind();

                if (!texture.isMipmap())
                {
                    texture.generateMipmap();
                }

                texture.setParameter(GL30.GL_TEXTURE_MAX_LEVEL, b.getValue() ? 4 : 0);
            }
        });
        this.options = UI.column(5, 10, this.linear, this.mipmap);
        this.texturePreview = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                this.area.render(context.batcher, Colors.A25);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50);

                if (UITexturePicker.this.current == null)
                {
                    String label = UIKeys.TEXTURES_PREVIEW_EMPTY.get();
                    FontRenderer font = context.batcher.getFont();

                    context.batcher.textShadow(label, this.area.mx(font.getWidth(label)), this.area.my() - font.getHeight() / 2);

                    return;
                }

                Texture texture = context.render.getTextures().getTexture(UITexturePicker.this.current);
                int w = Math.max(1, texture.width);
                int h = Math.max(1, texture.height);
                int maxW = Math.max(8, this.area.w - 12);
                int maxH = Math.max(8, this.area.h - 12);
                float scale = Math.min(maxW / (float) w, maxH / (float) h);
                int fw = Math.max(1, Math.round(w * scale));
                int fh = Math.max(1, Math.round(h * scale));
                int x = this.area.mx() - fw / 2;
                int y = this.area.my() - fh / 2;

                context.batcher.iconArea(Icons.CHECKBOARD, x, y, fw, fh);
                context.batcher.fullTexturedBox(texture, x, y, fw, fh);
            }
        };
        this.texturePreviewPopup = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                int background = Colors.mulRGB(Colors.CONTROL_BAR, 0.86F);

                this.area.render(context.batcher, background);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A75);
                context.batcher.box(this.area.x, this.area.y + 1, this.area.ex(), this.area.y + 2, Colors.A50);

                super.render(context);
            }
        };
        this.texturePreviewPopup.setVisible(false);

        this.formPreviewArea = new UIElement();
        this.formPreview = new UIFormRenderer();
        this.formPreview.grid = false;
        this.formPreview.setDistance(14);
        this.formPreview.setPosition(0F, 1F, 0F);
        this.formPreview.setRotation(34F, 8F);
        this.formPreviewArea.add(this.formPreview);
        this.formPreview.relative(this.formPreviewArea).full(this.formPreviewArea);
        this.formPreviewArea.setVisible(false);

        this.multi = new UIButton(UIKeys.TEXTURE_MULTISKIN, (b) -> this.toggleMulti());
        this.multiList = new UIFilteredLinkList((list) -> this.setFilteredLink(list.get(0)));
        this.multiList.sorting();

        this.editor = new UIMultiLinkEditor(this);
        this.editor.setVisible(false);

        this.buttons = new UIElement();
        this.add = new UIIcon(Icons.ADD, (b) -> this.addMulti());
        this.remove = new UIIcon(Icons.REMOVE, (b) -> this.removeMulti());
        this.edit = new UIIcon(Icons.EDIT, (b) -> this.toggleEditor());

        this.headerIcons = UI.row(0, this.viewMode, this.previewSettings, this.pixelEdit, this.folder, this.close);

        this.textureHeader.relative(this.right).x(0).y(0).w(1F).h(HEADER_HEIGHT);
        this.textureTabs.relative(this.textureHeader).x(10).y(0).w(0).h(TOP_TABS_HEIGHT).row(0).resize();
        this.tabFiles.w(TAB_WIDTH_FILES).h(TOP_TABS_HEIGHT);
        this.textureTabs.add(this.tabFiles);
        this.textureHeader.add(this.textureTabs);
        this.texturePreviewPopup.relative(this.right).x(1F, -10).y(CONTENT_Y_FILES + 2).wh(PREVIEW_POPUP_WIDTH, PREVIEW_POPUP_HEIGHT).anchorX(1F);
        this.texturePreview.relative(this.texturePreviewPopup).set(8, 8, 0, 0).w(1F, -16).h(1F, -62);
        this.options.relative(this.texturePreviewPopup).set(8, 0, 0, 42).w(1F, -16).y(1F, -50);
        this.texturePreviewPopup.add(this.texturePreview, this.options);

        this.headerIcons.row().preferred(0);
        this.headerIcons.relative(this.textureHeader).x(1F, -10).y(TOP_ROW_Y).w(100).h(20).anchorX(1F);

        this.right.full(this);
        this.multi.relative(this.textureHeader).set(10, TOP_ROW_Y, MULTI_BUTTON_WIDTH, 20);
        this.text.relative(this.textureHeader).set(10 + MULTI_BUTTON_WIDTH + 4, TOP_ROW_Y, 0, 20).wTo(this.headerIcons.area);
        this.picker.relative(this.right).set(10, CONTENT_Y_FILES, 0, 0).w(1F, -10).h(1F, -CONTENT_Y_FILES);
        this.formPreviewArea.relative(this.right).x(1F, -FORM_PREVIEW_WIDTH).y(CONTENT_Y_FILES).w(FORM_PREVIEW_WIDTH - 10).h(1F, -(CONTENT_Y_FILES + 10));

        this.multiList.relative(this).set(10, HEADER_HEIGHT + MULTI_SIDEBAR_TOP_GAP, MULTI_SIDEBAR_WIDTH - 20, 0).hTo(this.buttons.getFlex());
        this.editor.relative(this).set(120, 0, 0, 0).w(1F, -120).h(1F);

        this.buttons.relative(this).x(0).y(1F, -20).w(MULTI_SIDEBAR_WIDTH).h(20);
        this.add.relative(this.buttons).set(0, 0, 20, 20);
        this.remove.relative(this.add).set(20, 0, 20, 20);
        this.edit.relative(this.buttons).wh(20, 20).x(1F, -20);

        this.right.add(this.textureHeader, this.headerIcons, this.multi, this.text, this.picker, this.formPreviewArea, this.texturePreviewPopup);
        this.buttons.add(this.add, this.remove, this.edit);
        this.add(this.multiList, this.right, this.editor, this.buttons);
        this.setActiveTab(TAB_FILES);

        this.callback = callback;

        this.keys().register(Keys.TEXTURE_PICKER_FIND, () ->
        {
            findAllTextures(this.getContext(), this.current, (s) ->
            {
                this.selectCurrent(Link.create(s));
                this.displayCurrent(Link.create(s), true);
            });
        });

        this.fill(null);
        this.markContainer().eventPropagataion(EventPropagation.BLOCK);
    }

    /**
     * Supplies extra links that should be rendered with the selection highlight in
     * the file list (used by multi-texture picking, e.g. illusion textures, so all
     * the Shift + clicked textures show up as selected).
     */
    public UITexturePicker highlighted(Supplier<List<Link>> highlighted)
    {
        this.highlighted = highlighted;

        return this;
    }

    public UITexturePicker withFormPreview(Supplier<Form> supplier)
    {
        this.formPreviewSupplier = supplier;
        /* Keep 3D preview out of "Pick a texture" panel; it's rendered only in texture editor. */
        this.formPreviewArea.setVisible(false);
        this.picker.w(1F, -10);
        this.resize();

        return this;
    }

    public UITexturePicker cantBeClosed()
    {
        this.close.removeFromParent();
        this.eventPropagataion(EventPropagation.PASS);

        this.canBeClosed = false;

        return this;
    }

    public UITexturePicker disablePixelEditor()
    {
        this.pixelEditorEnabled = false;
        this.pixelEdit.removeFromParent();

        return this;
    }

    public UITexturePicker disableMultiSkin()
    {
        this.multiSkinEnabled = false;
        this.multiLink = null;
        this.multi.setVisible(false);
        this.multiList.setVisible(false);
        this.buttons.setVisible(false);
        this.updateHeaderRowLayout();
        this.updateMultiSidebarLayout(false);
        this.resize();

        return this;
    }

    private Link parseLink(MapType map)
    {
        return map == null ? null : LinkUtils.create(map.get("link"));
    }

    private MapType copyLink()
    {
        BaseType base = LinkUtils.toData(this.multiLink != null ? this.multiLink : this.current);

        if (base == null)
        {
            return null;
        }

        MapType map = new MapType();

        map.put("link", base);

        return map;
    }

    private void pasteLink(Link location)
    {
        this.setMulti(location, true);
    }

    private void download(String inputUrl)
    {
        Link path = this.picker.path;

        if (!Link.isAssets(path))
        {
            return;
        }

        UITextbox textboxFilename = new UITextbox();
        UITextbox textboxUrl = new UITextbox(1000, (t) ->
        {
            String newFilename = StringUtils.fileName(t).replaceAll("[^\\w\\d_\\-.]+", "");

            textboxFilename.setText(newFilename);
        });
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.TEXTURES_DOWNLOAD_TITLE, UIKeys.TEXTURES_DOWNLOAD_DESCRIPTION, (b) ->
        {
            if (b)
            {
                String url = textboxUrl.getText();
                String filename = textboxFilename.getText();
                Link urlLink = path.combine(filename);

                try (InputStream stream = URLSourcePack.downloadImage(Link.create(url)))
                {
                    File file = BBSMod.getProvider().getFile(urlLink);

                    try (OutputStream outputStream = new FileOutputStream(file))
                    {
                        IOUtils.copy(stream, outputStream);
                    }
                }
                catch (Exception e)
                {}
            }
        });

        if (!inputUrl.isEmpty())
        {
            String newFilename = StringUtils.fileName(inputUrl).replaceAll("[^\\w\\d_\\-.]+", "");

            textboxUrl.setText(inputUrl);
            textboxFilename.setText(newFilename);
            textboxFilename.textbox.selectFilename();
        }

        textboxFilename.placeholder(UIKeys.TEXTURES_DOWNLOAD_FILENAME);
        textboxUrl.placeholder(UIKeys.TEXTURES_DOWNLOAD_URL);

        textboxFilename.relative(panel.confirm).y(-5).w(1F).anchorY(1F);
        textboxUrl.relative(textboxFilename).y(-5).w(1F).anchorY(1F);
        panel.confirm.w(1F, -10);
        panel.content.add(textboxFilename, textboxUrl);

        UIContext context = this.getContext();

        UIOverlay.addOverlay(context, panel);
        context.focus(textboxFilename);
    }

    public void close()
    {
        boolean wasVisible = this.hasParent();

        this.closeAllEditorTabs();

        this.editor.close();
        this.removeFromParent();

        if (this.callback != null && wasVisible)
        {
            if (this.multiLink != null)
            {
                this.multiLink.recalculateId();
            }

            this.notifyingCallbackClose = true;
            this.callback.accept(this.multiLink != null ? this.multiLink : this.current);
            this.notifyingCallbackClose = false;
        }
    }

    @Override
    public File getImporterPath()
    {
        File target = BBSMod.getProvider().getFile(this.picker.path);

        if (target == null || !target.isDirectory())
        {
            return null;
        }

        return target;
    }

    public void refresh()
    {
        this.picker.update();
        this.updateFolderButton();
    }

    public void openFolder()
    {
        File target = BBSMod.getProvider().getFile(this.picker.path);

        if (target != null && target.isDirectory())
        {
            UIUtils.openFolder(target);
        }
    }

    public void togglePixelEditor()
    {
        if (!this.pixelEditorEnabled || this.current == null)
        {
            return;
        }

        this.ensurePixelEditor();
        this.openEditorTab(this.current);
    }

    private void ensurePixelEditor()
    {
        if (this.pixelEditor != null)
        {
            return;
        }

        this.pixelEditor = new UITexturePainter((l) ->
        {
            this.selectCurrent(l);
            this.openEditorTab(l);
        });
        this.pixelEditor.withFormPreview(this.formPreviewSupplier);
        this.pixelEditor.relative(this.right).set(0, CONTENT_Y_FILES, 0, 0).w(1F).h(1F, -CONTENT_Y_FILES);
        this.pixelEditor.resize();
        this.right.add(this.pixelEditor);

        this.editorToolbar = this.pixelEditor.getHeaderToolbar();
        this.editorToolbar.relative(this.textureHeader).x(10).y(TOP_ROW_Y).w(1F, -20).h(20);
        this.editorToolbar.setVisible(false);
        this.textureHeader.add(this.editorToolbar);
        this.editorToolbar.resize();
        this.textureHeader.resize();
    }

    @Override
    protected IUIElement childrenKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            if (this.activeTab == TAB_EDITOR || !this.canBeClosed)
            {
                return null;
            }

            this.close();

            return this;
        }

        return super.childrenKeyPressed(context);
    }

    private void openEditorTab(Link link)
    {
        if (link == null)
        {
            return;
        }

        EditorTabEntry entry = this.findEditorTabByLink(link);

        if (entry == null)
        {
            String title = this.getEditorTabTitle(link);
            UIIconTabButton tab = new UIIconTabButton(IKey.constant(title), Icons.EDIT, (b) -> this.switchEditorTo(link))
                .removable(this::closeEditorTab);

            tab.w(132).h(TOP_TABS_HEIGHT);
            this.textureTabs.add(tab);
            this.textureTabs.resize();

            entry = new EditorTabEntry(link, tab);
            this.editorTabs.add(entry);
        }

        this.switchEditorTo(entry.texture);
    }

    private void switchEditorTo(Link link)
    {
        if (link == null)
        {
            return;
        }

        this.activeEditorTexture = link;

        if (this.pixelEditor != null)
        {
            this.pixelEditor.fillTexture(link);
        }

        this.displayCurrent(link);
        this.setActiveTab(TAB_EDITOR);
    }

    private void closeEditorTab(UIIconTabButton tab)
    {
        int index = this.findEditorTabIndex(tab);

        if (index < 0)
        {
            return;
        }

        EditorTabEntry entry = this.editorTabs.remove(index);
        boolean wasActive = this.areLinksEqual(entry.texture, this.activeEditorTexture);

        tab.removeFromParent();
        this.textureTabs.resize();

        if (this.editorTabs.isEmpty())
        {
            this.closeAllEditorTabs();

            return;
        }

        if (wasActive)
        {
            int nextIndex = Math.max(0, Math.min(index, this.editorTabs.size() - 1));

            this.switchEditorTo(this.editorTabs.get(nextIndex).texture);
        }
        else
        {
            this.updateTextureTabs();
        }
    }

    private void closeAllEditorTabs()
    {
        if (this.pixelEditor != null)
        {
            if (this.editorToolbar != null)
            {
                this.editorToolbar.removeFromParent();
                this.editorToolbar = null;
            }

            this.pixelEditor.discardPreviewTextureChanges();
            this.pixelEditor.fillTexture(null);
            this.pixelEditor.removeFromParent();
            this.pixelEditor = null;
        }

        for (EditorTabEntry entry : this.editorTabs)
        {
            entry.tab.removeFromParent();
        }

        this.editorTabs.clear();
        this.activeEditorTexture = null;
        this.textureTabs.resize();
        this.setActiveTab(TAB_FILES);
    }

    private EditorTabEntry findEditorTabByLink(Link link)
    {
        for (EditorTabEntry entry : this.editorTabs)
        {
            if (this.areLinksEqual(entry.texture, link))
            {
                return entry;
            }
        }

        return null;
    }

    private int findEditorTabIndex(UIIconTabButton tab)
    {
        for (int i = 0; i < this.editorTabs.size(); i++)
        {
            if (this.editorTabs.get(i).tab == tab)
            {
                return i;
            }
        }

        return -1;
    }

    private boolean areLinksEqual(Link a, Link b)
    {
        if (a == b)
        {
            return true;
        }

        if (a == null || b == null)
        {
            return false;
        }

        return a.toString().equals(b.toString());
    }

    private String getEditorTabTitle(Link link)
    {
        String fileName = StringUtils.fileName(link.path);

        if (fileName == null || fileName.isEmpty())
        {
            fileName = link.toString();
        }

        return fileName;
    }

    private void setActiveTab(int tab)
    {
        this.activeTab = tab;

        boolean files = tab == TAB_FILES;
        boolean editor = tab == TAB_EDITOR && this.pixelEditor != null;
        boolean showMultiButton = files && this.multiSkinEnabled;
        boolean showMultiSidebar = files && this.multiSkinEnabled && this.multiLink != null;

        this.text.setVisible(files);
        this.picker.setVisible(files);
        this.formPreviewArea.setVisible(false);
        this.viewMode.setVisible(files);
        this.pixelEdit.setVisible(files);
        this.folder.setVisible(files);
        this.multi.setVisible(showMultiButton);
        this.multiList.setVisible(showMultiSidebar);
        this.buttons.setVisible(showMultiSidebar);
        this.previewSettings.setVisible(files);
        this.headerIcons.setVisible(files);

        if (!files)
        {
            this.texturePreviewPopup.setVisible(false);
        }

        if (this.pixelEditor != null)
        {
            this.pixelEditor.setVisible(editor);
        }

        if (this.editorToolbar != null)
        {
            this.editorToolbar.setVisible(editor);
            this.editorToolbar.resize();
            this.textureHeader.resize();
        }

        this.updateHeaderRowLayout();
        this.updateMultiSidebarLayout(showMultiSidebar);
        this.updateContentLayout(CONTENT_Y_FILES);
        this.updateTextureTabs();
    }

    private void updateContentLayout(int contentY)
    {
        int headerHeight = HEADER_HEIGHT;

        this.textureHeader.h(headerHeight);
        this.texturePreviewPopup.y(contentY + 2);
        this.picker.y(contentY).h(1F, -contentY);
        this.formPreviewArea.y(contentY).h(1F, -(contentY + 10));

        if (this.pixelEditor != null)
        {
            this.pixelEditor.y(contentY).h(1F, -contentY);
        }
    }

    private void updateTextureTabs()
    {
        this.tabFiles.background(true);
        this.tabFiles.color(this.activeTab == TAB_FILES ? BBSSettings.primaryColor.get() : 0x2d2d2d);

        for (EditorTabEntry entry : this.editorTabs)
        {
            entry.tab.background(true);

            boolean selected = this.activeTab == TAB_EDITOR && this.areLinksEqual(entry.texture, this.activeEditorTexture);

            entry.tab.color(selected ? BBSSettings.primaryColor.get() : 0x2d2d2d);
        }
    }

    private void openViewPresetMenu()
    {
        int currentPreset = this.getCurrentViewPreset();

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.BLOCK, UIKeys.TEXTURES_VIEW_PRESETS_VERY_LARGE, currentPreset == UIFileLinkList.VIEW_ICONS_VERY_LARGE, () -> this.picker.setItemSize(UIFileLinkList.VIEW_ICONS_VERY_LARGE));
            menu.action(Icons.BLOCK, UIKeys.TEXTURES_VIEW_PRESETS_LARGE, currentPreset == UIFileLinkList.VIEW_ICONS_LARGE, () -> this.picker.setItemSize(UIFileLinkList.VIEW_ICONS_LARGE));
            menu.action(Icons.BLOCK, UIKeys.TEXTURES_VIEW_PRESETS_MEDIUM, currentPreset == UIFileLinkList.VIEW_ICONS_MEDIUM, () -> this.picker.setItemSize(UIFileLinkList.VIEW_ICONS_MEDIUM));
            menu.action(Icons.BLOCK, UIKeys.TEXTURES_VIEW_PRESETS_SMALL, currentPreset == UIFileLinkList.VIEW_ICONS_SMALL, () -> this.picker.setItemSize(UIFileLinkList.VIEW_ICONS_SMALL));
            menu.action(Icons.LIST, UIKeys.TEXTURES_VIEW_PRESETS_LIST, currentPreset == UIFileLinkList.VIEW_LIST, () -> this.picker.setItemSize(UIFileLinkList.VIEW_LIST));
        });
    }

    private void openTexturePreviewPanel()
    {
        this.texturePreviewPopup.setVisible(!this.texturePreviewPopup.isVisible());
        this.updateOptions();
    }

    private int getCurrentViewPreset()
    {
        int size = this.picker.getItemSize();

        if (size <= UIFileLinkList.VIEW_LIST)
        {
            return UIFileLinkList.VIEW_LIST;
        }

        if (size >= UIFileLinkList.VIEW_ICONS_VERY_LARGE)
        {
            return UIFileLinkList.VIEW_ICONS_VERY_LARGE;
        }

        if (size >= UIFileLinkList.VIEW_ICONS_LARGE)
        {
            return UIFileLinkList.VIEW_ICONS_LARGE;
        }

        if (size >= UIFileLinkList.VIEW_ICONS_MEDIUM)
        {
            return UIFileLinkList.VIEW_ICONS_MEDIUM;
        }

        return UIFileLinkList.VIEW_ICONS_SMALL;
    }

    public void updateFolderButton()
    {
        File target = BBSMod.getProvider().getFile(this.picker.path);

        this.folder.setEnabled(target != null && target.isDirectory());
    }

    public void fill(Link link)
    {
        this.picker.clearNavigationHistory();
        this.setMulti(link, false, true);
    }

    /**
     * Add a {@link Link} to the MultiLink
     */
    private void addMulti()
    {
        FilteredLink filtered = this.currentFiltered.copyFiltered();

        this.multiList.add(filtered);
        this.multiList.setIndex(this.multiList.getList().size() - 1);
        this.setFilteredLink(this.multiList.getCurrent().get(0));
    }

    /**
     * Remove currently selected {@link Link} from multiLink
     */
    private void removeMulti()
    {
        int index = this.multiList.getIndex();

        if (index >= 0 && this.multiList.getList().size() > 1)
        {
            this.multiList.getList().remove(index);
            this.multiList.update();
            this.multiList.setIndex(index - 1);

            if (this.multiList.getIndex() >= 0)
            {
                this.setFilteredLink(this.multiList.getCurrent().get(0));
            }
        }
    }

    private void setFilteredLink(FilteredLink location)
    {
        this.setFilteredLink(location, false);
    }

    private void setFilteredLink(FilteredLink location, boolean scroll)
    {
        this.currentFiltered = location;
        this.displayCurrent(location.path);
        this.editor.setLink(location);
    }

    private void toggleEditor()
    {
        this.editor.toggleVisible();
        this.right.setVisible(!this.editor.isVisible());

        if (this.editor.isVisible())
        {
            this.editor.resetView();
        }
    }

    protected void displayCurrent(Link link)
    {
        this.displayCurrent(link, false);
    }

    /**
     * Display current resource location (it's just for visual, not 
     * logic)
     */
    protected void displayCurrent(Link link, boolean scroll)
    {
        this.current = link;

        this.text.setText(link == null ? "" : link.toString());
        this.text.textbox.moveCursorToStart();

        this.picker.setPath(link == null ? null : link.parent());
        this.picker.setCurrent(link, scroll);

        this.updateOptions();
        this.refreshFormPreview();
    }

    /**
     * Select current resource location
     */
    protected void selectCurrent(Link link)
    {
        if (link != null && !BBSModClient.getTextures().has(link))
        {
            return;
        }

        this.current = link;

        if (this.multiLink != null)
        {
            if (link == null && this.multiLink.children.size() == 1)
            {
                this.currentFiltered.path = null;
                this.toggleMulti();
            }
            else
            {
                this.currentFiltered.path = link;
            }
        }
        else if (this.callback != null)
        {
            this.callback.accept(link);
        }

        this.picker.setCurrent(link);
        this.text.setText(link == null ? "" : link.toString());
        this.updateOptions();
        this.refreshFormPreview();
    }

    protected void updateOptions()
    {
        this.linear.setEnabled(this.current != null);
        this.mipmap.setEnabled(this.current != null);

        if (this.current == null) return;

        Texture texture = BBSModClient.getTextures().getTexture(this.current);

        if (texture != null)
        {
            texture.bind();

            this.linear.setValue(texture.isLinear());
            this.mipmap.setValue(texture.isReallyMipmap());
        }
    }

    protected void toggleMulti()
    {
        if (!this.multiSkinEnabled)
        {
            return;
        }

        if (this.multiLink != null)
        {
            this.setMulti(this.multiLink.children.get(0).path, true);
        }
        else if (this.current != null)
        {
            this.setMulti(new MultiLink(this.current.toString()), true);
        }
        else
        {
            UIFileLinkList.FileLink link = this.picker.getCurrentFirst();

            if (link != null)
            {
                this.setMulti(link.link, true);
            }
        }
    }

    protected void setMulti(Link skin, boolean notify)
    {
        this.setMulti(skin, notify, false);
    }

    protected void setMulti(Link skin, boolean notify, boolean scroll)
    {
        if (!this.multiSkinEnabled && skin instanceof MultiLink)
        {
            MultiLink multi = (MultiLink) skin;

            skin = multi.children.isEmpty() ? null : multi.children.get(0).path;
        }

        if (this.editor.isVisible())
        {
            this.toggleEditor();
        }

        if (skin instanceof MultiLink)
        {
            this.closeAllEditorTabs();
        }

        boolean show = this.multiSkinEnabled && skin instanceof MultiLink;

        if (show)
        {
            this.multiLink = (MultiLink) ((MultiLink) skin).copy();
            this.setFilteredLink(this.multiLink.children.get(0), scroll);

            this.multiList.setIndex(this.multiLink.children.isEmpty() ? -1 : 0);
            this.multiList.setList(this.multiLink.children);

            if (this.current != null)
            {
                this.multiList.setIndex(0);
            }

        }
        else
        {
            this.multiLink = null;
            this.displayCurrent(skin, scroll);
        }

        if (notify)
        {
            if (show && this.callback != null)
            {
                this.multiLink.recalculateId();
                this.callback.accept(skin);
            }
            else
            {
                this.selectCurrent(skin);
            }
        }

        this.multiList.setVisible(show);
        this.buttons.setVisible(show);
        this.updateMultiSidebarLayout(this.activeTab == TAB_FILES && show);

        this.resize();
        this.updateFolderButton();
        this.refreshFormPreview();
    }

    private void updateMultiSidebarLayout(boolean showSidebar)
    {
        int leftOffset = showSidebar ? MULTI_SIDEBAR_WIDTH : 0;
        int rightPadding = 10;

        this.picker.x(10 + leftOffset).w(1F, -(10 + leftOffset + rightPadding));

        if (this.pixelEditor != null)
        {
            this.pixelEditor.x(10 + leftOffset).w(1F, -(10 + leftOffset + rightPadding));
        }
    }

    private void updateHeaderRowLayout()
    {
        int textX = this.multiSkinEnabled ? 10 + MULTI_BUTTON_WIDTH + 4 : 10;

        this.text.x(textX).wTo(this.headerIcons.area);
    }

    private void refreshFormPreview()
    {
        if (this.formPreviewSupplier == null)
        {
            this.formPreview.form = null;

            return;
        }

        Form source = this.formPreviewSupplier.get();
        this.formPreview.form = source == null ? null : FormUtils.copy(source);
    }

    public boolean tryMouseBack(UIContext context)
    {
        if (!this.isEnabled() || !this.canBeSeen() || !this.area.isInside(context))
        {
            return false;
        }

        if (this.activeTab == TAB_FILES)
        {
            this.picker.navigateBack();

            return true;
        }

        return false;
    }

    public boolean tryMouseForward(UIContext context)
    {
        if (!this.isEnabled() || !this.canBeSeen() || !this.area.isInside(context))
        {
            return false;
        }

        if (this.activeTab == TAB_FILES)
        {
            this.picker.navigateForward();

            return true;
        }

        return false;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ENTER))
        {
            UIFileLinkList.FileLink link = this.picker.getCurrentFirst();

            if (link != null && link.folder)
            {
                this.picker.setPath(link.link);
            }
            else if (link != null)
            {
                this.selectCurrent(link.link);
            }

            this.typed = "";

            return true;
        }
        else if (context.isHeld(GLFW.GLFW_KEY_UP))
        {
            return this.moveCurrent(-1, Window.isShiftPressed());
        }
        else if (context.isHeld(GLFW.GLFW_KEY_DOWN))
        {
            return this.moveCurrent(1, Window.isShiftPressed());
        }
        else if (context.isPressed(GLFW.GLFW_KEY_ESCAPE) && this.canBeClosed)
        {
            this.close();

            return true;
        }
        else if (context.isPressed(Keys.PASTE.getMainKey()) && Window.isCtrlPressed())
        {
            this.download(Window.getClipboard());

            return true;
        }

        return super.subKeyPressed(context);
    }

    protected boolean moveCurrent(int factor, boolean top)
    {
        int index = this.picker.getIndex() + factor;
        int length = this.picker.getList().size();

        if (length <= 0)
        {
            return true;
        }

        if (index < 0) index = length - 1;
        else if (index >= length) index = 0;

        if (top) index = factor > 0 ? length - 1 : 0;

        this.picker.setIndex(index);
        this.picker.scroll.scrollIntoView(index * this.picker.scroll.scrollItemSize);

        UIFileLinkList.FileLink link = this.picker.getCurrentFirst();

        if (link != null && !link.folder)
        {
            this.selectCurrent(link.link);
        }

        this.typed = "";

        return true;
    }

    @Override
    public boolean subTextInput(UIContext context)
    {
        return this.pickByTyping(context, context.getInputCharacter());
    }

    protected boolean pickByTyping(UIContext context, char inputChar)
    {
        if (this.lastTyped.checkReset())
        {
            this.typed = "";
        }

        this.typed += Character.toString(inputChar);
        this.lastTyped.mark();

        for (UIFileLinkList.FileLink entry : this.picker.getList())
        {
            String name = entry.title;

            if (name.startsWith(this.typed))
            {
                this.picker.setCurrentScroll(entry);

                return true;
            }
        }

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        /* Refresh the list */
        if (this.lastChecked.checkRepeat())
        {
            File file = BBSMod.getProvider().getFile(this.picker.path);
            int scroll = (int) this.picker.scroll.getScroll();

            if (file != null)
            {
                UIFileLinkList.FileLink selected = this.picker.getCurrentFirst();

                this.picker.setPath(this.picker.path, false);

                if (selected != null)
                {
                    this.picker.setCurrent(selected.link);
                }
            }

            this.picker.scroll.setScroll(scroll);
        }

        /* Highlight the multi-picked textures (e.g. illusion textures) as selected */
        if (this.highlighted != null)
        {
            List<Link> links = this.highlighted.get();
            List<UIFileLinkList.FileLink> files = this.picker.getList();

            this.picker.deselect();

            for (int i = 0; i < files.size(); i++)
            {
                UIFileLinkList.FileLink file = files.get(i);

                if (!file.folder && links.contains(file.link))
                {
                    this.picker.addIndex(i);
                }
            }
        }

        /* Draw the background */
        context.batcher.gradientVBox(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50, Colors.A100);

        if (this.multiList.isVisible())
        {
            context.batcher.box(this.area.x, this.area.y + HEADER_HEIGHT, this.area.x + MULTI_SIDEBAR_WIDTH, this.area.ey(), 0xff181818);
            context.batcher.box(this.area.x, this.area.y + HEADER_HEIGHT, this.area.x + MULTI_SIDEBAR_WIDTH, this.area.y + HEADER_HEIGHT + 20, Colors.A25);
            context.batcher.gradientVBox(this.area.x, this.area.ey() - 20, this.buttons.area.ex(), this.area.ey(), 0, Colors.A50);
        }

        if (this.editor.isVisible())
        {
            this.edit.area.render(context.batcher, Colors.A50 | BBSSettings.primaryColor.get());
        }

        if (this.formPreviewArea.isVisible())
        {
            this.formPreviewArea.area.render(context.batcher, Colors.A25);
            context.batcher.outline(this.formPreviewArea.area.x, this.formPreviewArea.area.y, this.formPreviewArea.area.ex(), this.formPreviewArea.area.ey(), Colors.A50);
        }

        super.render(context);

        /* Draw the overlays */
        if (this.right.isVisible())
        {
            FontRenderer font = context.batcher.getFont();

            if (this.picker.getList().isEmpty())
            {
                String label = UIKeys.TEXTURE_NO_DATA.get();
                int w = font.getWidth(label);

                context.batcher.text(label, this.picker.area.mx(w), this.picker.area.my() - 8);
            }

            if (!this.lastTyped.check() && this.lastTyped.enabled)
            {
                int w = font.getWidth(this.typed);
                int x = this.text.area.x;
                int y = this.text.area.ey();

                context.batcher.box(x, y, x + w + 4, y + 4 + font.getHeight(), Colors.A50 | BBSSettings.primaryColor.get());
                context.batcher.textShadow(this.typed, x + 2, y + 2);
            }

        }
    }
}
