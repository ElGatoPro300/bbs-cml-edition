package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.discord.DiscordPresenceManager;
import mchorse.bbs_mod.importers.IImportPathProvider;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.Importers;
import mchorse.bbs_mod.importers.types.IImporter;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.utils.IFileDropListener;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.FFMpegUtils;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UIScreen extends Screen implements IFileDropListener
{
    private UIBaseMenu menu;
    private UIRenderingContext context;

    private int lastGuiScale;
    private boolean appliedGameScale;

    public static void open(UIBaseMenu menu)
    {
        MinecraftClient.getInstance().setScreen(new UIScreen(Text.empty(), menu));
    }

    public static UIBaseMenu getCurrentMenu()
    {
        Screen currentScreen = MinecraftClient.getInstance().currentScreen;

        if (currentScreen instanceof UIScreen uiScreen)
        {
            return uiScreen.menu;
        }

        return null;
    }

    public UIScreen(Text title, UIBaseMenu menu)
    {
        super(title);

        MinecraftClient mc = MinecraftClient.getInstance();

        this.menu = menu;
        /* Placeholder DrawContext just so the UIRenderingContext/Batcher2D exist for layout/event wiring.
         * It is NEVER drawn into: render() swaps in vanilla's live per-frame DrawContext via
         * this.context.setContext(...) before any drawing happens (two-phase GUI, 1.21.6+). */
        this.context = new UIRenderingContext(new DrawContext(mc, new GuiRenderState(), mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight()));

        this.menu.context.setup(this.context);
    }

    public UIBaseMenu getMenu()
    {
        return this.menu;
    }

    public void update()
    {
        this.menu.update();
    }

    public void renderInWorld(WorldRenderContext context)
    {
        this.menu.renderInWorld(context);
    }

    @Override
    public void onFilesDropped(List<Path> paths)
    {
        super.onFilesDropped(paths);

        this.filesDragged(paths);
    }

    public void filesDragged(List<Path> paths)
    {
        String[] filePaths = new String[paths.size()];
        int i = 0;

        for (Path path : paths)
        {
            filePaths[i] = path.toAbsolutePath().toString();

            i += 1;
        }

        this.acceptFilePaths(filePaths);
    }

    @Override
    public void removed()
    {
        this.context.closeFormPreviews();
        this.restoreGuiScale();

        super.removed();

        /* Force overlay teardown so deferred close animations cannot skip onClose
         * (e.g. RecordingPauseHelper.pop) when setScreen(null) replaces this UI. */
        if (this.menu != null && this.menu.overlay != null)
        {
            for (UIOverlay overlay : this.menu.overlay.getChildren(UIOverlay.class))
            {
                overlay.forceClose();
            }
        }

        this.menu.onClose(null);
        DiscordPresenceManager.INSTANCE.onBbsUiClosed();

        if (this.menu.canHideHUD())
        {
            MinecraftClient.getInstance().options.hudHidden = false;
        }
    }

    @Override
    public void onDisplayed()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        this.lastGuiScale = client.options.getGuiScale().getValue();
        this.reapplyScale();

        super.onDisplayed();

        this.menu.onOpen(null);
        DiscordPresenceManager.INSTANCE.onBbsUiOpened(this.menu);

        client.options.hudHidden = this.menu.canHideHUD();
    }

    /**
     * Apply BBS scale for the current link-to-game setting. Independent mode never
     * writes Minecraft's GUI scale; linked mode always forces a window recalc so a
     * fractional BBS value (1.8 vs game 2) is not skipped by an int early-return.
     */
    public void reapplyScale()
    {
        if (BbsGuiScale.isLinkedToGame())
        {
            if (!this.appliedGameScale)
            {
                this.lastGuiScale = MinecraftClient.getInstance().options.getGuiScale().getValue();
            }

            this.applyGameGuiScale(BBSModClient.getGUIScale());
            this.appliedGameScale = true;
            this.menu.resize(this.client.getWindow().getScaledWidth(), this.client.getWindow().getScaledHeight());
        }
        else
        {
            this.restoreGuiScale();
            BbsGuiScale.resizeMenu(this.menu);
        }
    }

    private void applyGameGuiScale(int scale)
    {
        MinecraftClient client = MinecraftClient.getInstance();

        client.options.getGuiScale().setValue(scale);
        client.onResolutionChanged();
    }

    private void restoreGuiScale()
    {
        if (!this.appliedGameScale)
        {
            return;
        }

        this.appliedGameScale = false;
        BbsGuiScale.restoringGameScale(() -> this.applyGameGuiScale(this.lastGuiScale));
    }

    @Override
    public boolean shouldPause()
    {
        return this.menu.canPause();
    }

    @Override
    protected void init()
    {
        super.init();

        if (BbsGuiScale.isLinkedToGame())
        {
            this.menu.resize(this.width, this.height);
        }
        else
        {
            BbsGuiScale.resizeMenu(this.menu);
        }
    }

    @Override
    public void resize(int width, int height)
    {
        super.resize(width, height);

        if (BbsGuiScale.isLinkedToGame())
        {
            this.menu.resize(width, height);
        }
        else
        {
            BbsGuiScale.resizeMenu(this.menu);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled)
    {
        return this.menu.mouseClicked(BbsGuiScale.toBbsMouseX((int) click.x()), BbsGuiScale.toBbsMouseY((int) click.y()), click.button());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        return this.menu.mouseScrolled(BbsGuiScale.toBbsMouseX(mouseX), BbsGuiScale.toBbsMouseY(mouseY), horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(Click click)
    {
        return this.menu.mouseReleased(BbsGuiScale.toBbsMouseX((int) click.x()), BbsGuiScale.toBbsMouseY((int) click.y()), click.button());
    }

    @Override
    public boolean keyPressed(KeyInput input)
    {
        return this.menu.handleKey(input.key(), input.scancode(), BBSRendering.lastAction, input.modifiers());
    }

    @Override
    public boolean keyReleased(KeyInput input)
    {
        return this.menu.handleKey(input.key(), input.scancode(), GLFW.GLFW_RELEASE, input.modifiers());
    }

    @Override
    public boolean charTyped(CharInput input)
    {
        this.menu.handleTextInput(input.codepoint());

        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta)
    {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        super.render(context, mouseX, mouseY, delta);

        this.context.setContext(context);
        int bbsMouseX = BbsGuiScale.toBbsMouseX(mouseX);
        int bbsMouseY = BbsGuiScale.toBbsMouseY(mouseY);

        BbsGuiScale.withBbsWindowScale(() ->
        {
            this.menu.context.setTransition(this.client.getRenderTickCounter().getTickProgress(false));
            this.menu.renderMenu(this.context, bbsMouseX, bbsMouseY);
            this.menu.context.render.executeRunnables();
        });
        this.client.options.hudHidden = this.menu.canHideHUD();
    }

    @Override
    public void acceptFilePaths(String[] paths)
    {
        if (this.menu != null)
        {
            if (!FFMpegUtils.checkFFMPEG())
            {
                this.menu.context.notifyError(UIKeys.IMPORTER_FFMPEG_NOTIFICATION);

                return;
            }

            File directory = null;
            boolean open = true;

            for (IImportPathProvider provider : this.menu.getRoot().getChildren(IImportPathProvider.class))
            {
                directory = provider.getImporterPath();

                if (directory != null)
                {
                    open = false;

                    break;
                }
            }

            List<File> files = new ArrayList<>();

            for (String path : paths)
            {
                File file = new File(path);

                if (file.exists())
                {
                    files.add(file);
                }
            }

            ImporterContext context = new ImporterContext(files, directory);

            for (IImporter importer : Importers.getImporters())
            {
                if (importer.canImport(context))
                {
                    importer.importFiles(context);

                    if (open)
                    {
                        UIUtils.openFolder(context.getDestination(importer));
                    }

                    this.menu.context.notifySuccess(UIKeys.IMPORTER_SUCCESS_NOTIFICATION.format(importer.getName()));

                    return;
                }
            }
        }
    }
}
