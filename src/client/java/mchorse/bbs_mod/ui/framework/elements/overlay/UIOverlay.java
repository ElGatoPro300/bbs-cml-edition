package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import mchorse.bbs_mod.utils.colors.Colors;

import org.joml.Vector2i;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class UIOverlay extends UIElement
{
    private static final Map<String, Vector2i> offsets = new HashMap<>();
    private static final Map<String, Vector2i> sizes = new HashMap<>();
    private static boolean loaded = false;

    private int background = Colors.A50;
    private float openTransition = 0.0F;
    private boolean closing = false;
    private boolean instantClose = false;

    public float getOpenTransition()
    {
        return this.openTransition;
    }

    private static void ensureLoaded()
    {
        if (loaded)
        {
            return;
        }

        loaded = true;

        try
        {
            File file = BBSMod.getSettingsPath("overlay_sizes.json");

            if (!file.exists())
            {
                return;
            }

            BaseType type = DataToString.read(file);

            if (type instanceof MapType)
            {
                MapType data = (MapType) type;
                MapType sizesMap = data.getMap("sizes");
                MapType offsetsMap = data.getMap("offsets");

                if (sizesMap != null)
                {
                    for (String key : sizesMap.keys())
                    {
                        MapType vec = sizesMap.getMap(key);

                        if (vec != null)
                        {
                            sizes.put(key, new Vector2i(vec.getInt("x"), vec.getInt("y")));
                        }
                    }
                }

                if (offsetsMap != null)
                {
                    for (String key : offsetsMap.keys())
                    {
                        MapType vec = offsetsMap.getMap(key);

                        if (vec != null)
                        {
                            offsets.put(key, new Vector2i(vec.getInt("x"), vec.getInt("y")));
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void saveOverlayState(UIOverlayPanel panel)
    {
        if (panel == null)
        {
            return;
        }

        ensureLoaded();

        Vector2i offset = new Vector2i(panel.getFlex().x.offset, panel.getFlex().y.offset);
        Vector2i size = new Vector2i(panel.getFlex().w.offset, panel.getFlex().h.offset);
        String key = panel.getKey();

        offsets.put(key, offset);
        sizes.put(key, size);

        saveToFile();
    }

    private static void saveToFile()
    {
        try
        {
            MapType data = new MapType();
            MapType sizesMap = new MapType();
            MapType offsetsMap = new MapType();

            for (Map.Entry<String, Vector2i> entry : sizes.entrySet())
            {
                MapType vec = new MapType();

                vec.putInt("x", entry.getValue().x);
                vec.putInt("y", entry.getValue().y);
                sizesMap.put(entry.getKey(), vec);
            }

            for (Map.Entry<String, Vector2i> entry : offsets.entrySet())
            {
                MapType vec = new MapType();

                vec.putInt("x", entry.getValue().x);
                vec.putInt("y", entry.getValue().y);
                offsetsMap.put(entry.getKey(), vec);
            }

            data.put("sizes", sizesMap);
            data.put("offsets", offsetsMap);

            DataToString.writeSilently(BBSMod.getSettingsPath("overlay_sizes.json"), data, true);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(0.5F, 0.5F).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, float w, float h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(w, h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, int w, int h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).wh(w, h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlay(UIContext context, UIOverlayPanel panel, int w, float h)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).xy(0.5F, 0.5F).w(w).h(h).anchor(0.5F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlayLeft(UIContext context, UIOverlayPanel panel, int w)
    {
        return addOverlayLeft(context, panel, w, 10);
    }

    public static UIOverlay addOverlayLeft(UIContext context, UIOverlayPanel panel, int w, int padding)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).x(padding).y(padding).w(w).h(1F, -padding * 2).anchor(0F, 0F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static UIOverlay addOverlayRight(UIContext context, UIOverlayPanel panel, int w)
    {
        return addOverlayRight(context, panel, w, 10);
    }

    public static UIOverlay addOverlayRight(UIContext context, UIOverlayPanel panel, int w, int padding)
    {
        UIOverlay overlay = new UIOverlay();

        panel.relative(overlay).x(1F, -padding).y(padding).w(w).h(1F, -padding * 2).anchor(1F, 0F).bounds(overlay, 0);
        setupPanel(context, overlay, panel);

        return overlay;
    }

    public static void setupPanel(UIContext context, UIOverlay overlay, UIOverlayPanel panel)
    {
        if (panel.hasParent())
        {
            return;
        }

        ensureLoaded();

        Flex flex = panel.getFlex();
        String key = panel.getKey();
        Vector2i offset = offsets.get(key);
        Vector2i size = sizes.get(key);

        panel.setInitialOffset(flex.x.offset, flex.y.offset);
        panel.setInitialSizeOffset(flex.w.offset, flex.h.offset);

        if (offset != null)
        {
            flex.x.offset = offset.x;
            flex.y.offset = offset.y;
        }

        if (size != null)
        {
            flex.w.offset = size.x;
            flex.h.offset = size.y;
        }

        overlay.full(context.menu.overlay);
        context.menu.overlay.add(overlay);
        overlay.add(panel);
        context.menu.overlay.resize();
    }

    public static boolean has(UIContext context)
    {
        if (context == null || context.menu == null || context.menu.overlay == null)
        {
            return false;
        }

        /* Only count modal overlays under menu.overlay. Film/replays keep docked
         * UIOverlayPanel subclasses in the panel tree; treating those as "open"
         * blocked F6 and other overlay openers while editing a film. */
        for (IUIElement child : context.menu.overlay.getChildren())
        {
            if (child instanceof UIOverlay && child.isEnabled())
            {
                return true;
            }
        }

        return false;
    }

    public UIOverlay()
    {
        this.mouseEventPropagataion(EventPropagation.BLOCK).keyboardEventPropagataion(EventPropagation.PASS).markContainer();
    }

    public UIOverlay background(int background)
    {
        this.background = background;

        return this;
    }

    public UIOverlay noBackground()
    {
        return this.background(0);
    }

    /**
     * Skip the close-scale animation and run {@link #performClose()} in the same
     * input/event turn. Needed for world-behind overlays whose {@code onClose}
     * calls {@code setScreen(null)} — finishing close inside {@link #render} can
     * resize/clear framebuffers mid-frame and flash a dark view for one frame.
     */
    public UIOverlay instantClose()
    {
        this.instantClose = true;

        return this;
    }

    public void closeItself()
    {
        if (this.closing)
        {
            return;
        }

        UIUtils.playClick();

        if (this.instantClose || BBSSettings.editorSimplifyAnimations.get())
        {
            this.openTransition = 0.0F;
            this.closing = true;
            this.performClose();
        }
        else
        {
            this.closing = true;
        }
    }

    /**
     * Closes immediately and fires panel {@code onClose} handlers. Use when the
     * screen is being destroyed so deferred close animations cannot skip teardown.
     */
    public void forceClose()
    {
        if (!this.closing)
        {
            this.closing = true;
        }

        this.performClose();
    }

    private void performClose()
    {
        for (UIOverlayPanel element : this.getChildren(UIOverlayPanel.class))
        {
            saveOverlayState(element);

            element.removeFromParent();
            element.onClose();
        }

        this.removeFromParent();
    }

    /* Don't pass user input down the line... */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        this.closeItself();

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        float target = this.closing ? 0.0F : 1.0F;

        if (BBSSettings.editorSimplifyAnimations.get())
        {
            this.openTransition = target;
        }
        else
        {
            if (this.openTransition < 0.001F && !this.closing)
            {
                /* Fast start on first frame */
                this.openTransition = 0.01F;
            }
            this.openTransition += (target - this.openTransition) * 0.25F;
        }

        if (this.closing && this.openTransition <= 0.01F)
        {
            this.performClose();
            return;
        }

        context.batcher.flush();

        if (Colors.getA(this.background) > 0F)
        {
            int alpha = (int) (Colors.getA(this.background) * this.openTransition * 255);
            int finalBgColor = Colors.setA(this.background, alpha / 255.0F);
            this.area.render(context.batcher, finalBgColor);
        }

        super.render(context);
    }
}
