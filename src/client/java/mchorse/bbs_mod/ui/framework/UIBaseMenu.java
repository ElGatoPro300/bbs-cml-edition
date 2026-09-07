package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.compat.HdrModCompat;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbar;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarPointerBlock;
import mchorse.bbs_mod.ui.framework.elements.IFocusedUIElement;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.IViewport;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIAnimatedCollapseShell;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.utils.IViewportStack;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.renderers.InputRenderer;
import mchorse.bbs_mod.utils.colors.Colors;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for GUI screens using this framework
 */
public abstract class UIBaseMenu
{
    public static boolean renderAxes = true;

    private static InputRenderer inputRenderer = new InputRenderer();

    private UIRootElement root;
    public UIElement main;
    public UIElement overlay;
    public UIContext context;
    public Area viewport = new Area();

    public int width;
    public int height;

    public UIBaseMenu()
    {
        this.context = new UIContext(this);

        this.root = new UIRootElement(this.context);
        this.root.markContainer().full(this.viewport);

        this.main = new UIElement();
        this.main.full(this.viewport);
        this.overlay = new UIElement();
        this.overlay.full(this.viewport);
        this.root.add(this.main, this.overlay);

        UIElement popka = new UIElement();

        popka.keys().register(Keys.KEYBINDS, () -> this.context.toggleKeybinds());
        popka.keys().register(Keys.TRANSFORMATIONS_TOGGLE_AXES, () ->
        {
            renderAxes = !renderAxes;

            if (!renderAxes)
            {
                Gizmo.INSTANCE.setHoveredIndex(-1);
                Gizmo.INSTANCE.stop();
            }
        });
        this.root.add(popka);

        this.context.keybinds.relative(this.viewport).wh(0.5F, 1F);
    }

    public UIRootElement getRoot()
    {
        return this.root;
    }

    public boolean canHideHUD()
    {
        return true;
    }

    /**
     * Whether the vanilla world should render while this menu is open. Most BBS editors draw an
     * opaque UI and do not need the world pass behind them.
     */
    public boolean needsWorldRender()
    {
        return false;
    }

    public boolean canPause()
    {
        return true;
    }

    public boolean canRefresh()
    {
        return true;
    }

    public void onOpen(UIBaseMenu oldMenu)
    {}

    public void onClose(UIBaseMenu nextMenu)
    {}

    public void update()
    {
        this.context.update();
    }

    public void resize(int width, int height)
    {
        this.width = width;
        this.height = height;

        this.viewport.set(0, 0, this.width, this.height);
        this.viewportSet();

        this.context.pushViewport(this.viewport);
        this.root.resize();
        this.context.popViewport();
    }

    protected void viewportSet()
    {}

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        this.context.setMouse(mouseX, mouseY, mouseButton);

        if (this.handleControlCaptureMouse(mouseButton, true))
        {
            return true;
        }

        if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_4)
        {
            if (this.tryTexturePickerMouseBack())
            {
                return true;
            }

            return this.handleKey(GLFW.GLFW_KEY_ESCAPE, 0, GLFW.GLFW_PRESS, 0);
        }

        if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_5 && this.tryTexturePickerMouseForward())
        {
            return true;
        }

        boolean result = false;

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);
            TimelineToolbarPointerBlock.prepare(this.context);

            IFocusedUIElement previouslyFocused = this.context.activeElement;
            IUIElement element = this.root.mouseClicked(this.context);

            if (previouslyFocused != null && this.context.activeElement == previouslyFocused)
            {
                boolean clickedFocused = false;

                if (element instanceof UIElement && previouslyFocused instanceof UIElement)
                {
                    UIElement clickedElement = (UIElement) element;
                    UIElement focusedElement = (UIElement) previouslyFocused;

                    clickedFocused = clickedElement == focusedElement || focusedElement.isDescendant(clickedElement);
                }

                if (!clickedFocused)
                {
                    this.context.unfocus();
                }
            }

            this.context.popViewport();

            result = element != null;
        }

        return result;
    }

    /**
     * Film actor-control captures LMB/RMB for vanilla swipe / shield while the
     * OS cursor is hidden. Override in menus that host the film editor.
     */
    protected boolean handleControlCaptureMouse(int button, boolean pressed)
    {
        return false;
    }

    /**
     * When true, render/hover uses parked mouse coords so invisible control
     * cursor cannot highlight timeline tracks and other widgets.
     */
    protected boolean shouldParkMouseWhileControlling()
    {
        return false;
    }

    /**
     * Texture picker Files tab uses the back mouse button for folder navigation instead of Escape.
     */
    private boolean tryTexturePickerMouseBack()
    {
        if (!this.root.isEnabled())
        {
            return false;
        }

        this.context.pushViewport(this.viewport);

        for (UITexturePicker picker : this.root.getChildren(UITexturePicker.class))
        {
            if (picker.tryMouseBack(this.context))
            {
                this.context.popViewport();

                return true;
            }
        }

        this.context.popViewport();

        return false;
    }

    private boolean tryTexturePickerMouseForward()
    {
        if (!this.root.isEnabled())
        {
            return false;
        }

        this.context.pushViewport(this.viewport);

        for (UITexturePicker picker : this.root.getChildren(UITexturePicker.class))
        {
            if (picker.tryMouseForward(this.context))
            {
                this.context.popViewport();

                return true;
            }
        }

        this.context.popViewport();

        return false;
    }

    public boolean mouseScrolled(int x, int y, double h, double v)
    {
        boolean result = false;

        this.context.setMouseWheel(x, y, v, h);

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);

            IUIElement element = this.root.mouseScrolled(this.context);

            this.context.popViewport();

            result = element != null;
        }

        return result;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton)
    {
        if (this.handleControlCaptureMouse(mouseButton, false))
        {
            return true;
        }

        boolean result = false;

        this.context.setMouse(mouseX, mouseY, mouseButton);

        if (this.root.isEnabled())
        {
            this.context.pushViewport(this.viewport);
            TimelineToolbarPointerBlock.prepare(this.context);

            IUIElement element = this.root.mouseReleased(this.context);

            this.context.popViewport();

            result = element != null;
        }

        Gizmo.INSTANCE.stop();

        return result;
    }

    public boolean handleKey(int key, int scanCode, int action, int mods)
    {
        if (action == GLFW.GLFW_PRESS)
        {
            inputRenderer.keyPressed(this.context, key);
        }

        this.context.setKeyEvent(key, scanCode, action);

        IUIElement element = this.root.keyPressed(this.context);

        if (this.root.isEnabled() && element != null)
        {
            return true;
        }

        if (this.context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            if (TimelineToolbar.cancelDockDragIfEscape(this.context))
            {
                return true;
            }

            this.closeMenu();

            return true;
        }

        return false;
    }

    public void handleTextInput(int key)
    {
        this.context.setKeyTyped((char) key);

        if (this.root.isEnabled())
        {
            this.root.textInput(this.context);
        }
    }

    /**
     * This method is called when this screen is about to get closed
     */
    protected void closeMenu()
    {
        MinecraftClient.getInstance().setScreen(null);
    }

    public void closeThisMenu()
    {
        this.closeMenu();
    }

    public void renderDefaultBackground()
    {
        this.context.batcher.box(0, 0, this.width, this.height, Colors.A50);
    }

    public void renderMenu(UIRenderingContext context, int mouseX, int mouseY)
    {
        if (this.shouldParkMouseWhileControlling())
        {
            mouseX = mouseY = -1;
        }

        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        if (HdrModCompat.isHdrPresentationActive())
        {
            BBSRendering.prepareGuiAfterWorldPresent();
        }

        this.context.resetMatrix();
        this.context.setMouse(mouseX, mouseY);
        this.context.resetCursor();

        this.preRenderMenu(context);
        UIAnimatedCollapseShell.tickAll();

        if (this.root.isVisible())
        {
            this.context.reset();
            TimelineToolbarPointerBlock.prepare(this.context);
            this.context.pushViewport(this.viewport);

            this.root.render(this.context);
            this.context.batcher.flushDraw();

            this.context.popViewport();
            this.context.postRender();
        }

        if (this.main.isVisible())
        {
            inputRenderer.render(this, mouseX, mouseY);
        }

        this.context.applyCursor();

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    protected void preRenderMenu(UIRenderingContext context)
    {}

    public void startRenderFrame(float tickDelta)
    {}

    public void renderInWorld(WorldRenderContext context)
    {}

    public static class UIRootElement extends UIElement implements IViewport
    {
        private UIContext context;

        public UIRootElement(UIContext context)
        {
            super();

            this.context = context;

            this.markContainer();
        }

        public UIContext getContext()
        {
            return this.context;
        }

        @Override
        public void apply(IViewportStack stack)
        {
            stack.pushViewport(this.area);
        }

        @Override
        public void unapply(IViewportStack stack)
        {
            stack.popViewport();
        }
        @Override
        public void render(UIContext context)
        {
            List<IUIElement> snapshot = new ArrayList<>(this.getChildren());

            for (IUIElement element : snapshot)
            {
                if (element.isVisible() && element.canBeRendered(context.getViewport()))
                {
                    element.render(context);

                    /* Commit DrawContext text before the overlay layer so modal panels cover background labels */
                    if (element == this.context.menu.main)
                    {
                        context.batcher.flushDraw();
                    }
                }
            }
        }
    }
}