package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.minecraft.client.gui.screen.Screen;

import org.joml.Vector2d;
import org.joml.Vector2i;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class UIChalkboard extends UIElement
{
    private UIColor picker;
    private UITrackpad size;

    private Pixels pixels;
    private Texture texture;

    private int color = Colors.setA(Colors.RED, 1F);
    private boolean drawing;

    private int lastX;
    private int lastY;
    private int startX;
    private int startY;
    private boolean isChalkEnabled;
    private boolean lineMode;
    private boolean placingLine;

    private List<Pixels> undo = new ArrayList<>();
    private List<Pixels> redo = new ArrayList<>();

    public UIChalkboard()
    {
        super();

        this.texture = new Texture();
        this.texture.setFilter(GL11.GL_NEAREST);

        this.picker = new UIColor((c) -> this.color = c).withAlpha();
        this.picker.direction(Direction.TOP).withTarget(this).setColor(this.color);
        this.picker.relative(this).x(10).y(1F, -30).wh(60, 20);
        this.picker.setEnabled(false);

        this.size = new UITrackpad((v) -> {});
        this.size.limit(1, 100, true).setValue(5);
        this.size.relative(this).x(80).y(1F, -30).w(60);
        this.size.setEnabled(false);

        this.add(this.picker, this.size);

        this.keys().register(Keys.DELETE, this::clear).active(() -> this.isChalkEnabled);
        this.keys().register(new KeyCombo("", UIKeys.PANELS_KEYS_TOGGLE_CHALKBOARD, GLFW.GLFW_KEY_F10), this::toggleChalk);
        this.keys().register(new KeyCombo("undo", UIKeys.RAW_UNDO, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_LEFT_CONTROL), this::undo).active(() -> this.isChalkEnabled);
        this.keys().register(new KeyCombo("redo", UIKeys.RAW_REDO, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_LEFT_CONTROL), this::redo).active(() -> this.isChalkEnabled);
        this.keys().register(new KeyCombo("line_mode", UIKeys.RAW_LINE_MODE, GLFW.GLFW_KEY_L), () -> this.lineMode = !this.lineMode).active(() -> this.isChalkEnabled);
    }

    private void clear()
    {
        this.saveUndo();
        this.resize();
    }

    private void undo()
    {
        if (this.undo.isEmpty())
        {
            return;
        }

        this.redo.add(this.pixels);
        this.pixels = this.undo.remove(this.undo.size() - 1);

        this.updateTexture();
    }

    private void redo()
    {
        if (this.redo.isEmpty())
        {
            return;
        }

        this.undo.add(this.pixels);
        this.pixels = this.redo.remove(this.redo.size() - 1);

        this.updateTexture();
    }

    private void saveUndo()
    {
        if (this.pixels == null)
        {
            return;
        }

        if (this.undo.size() >= 20)
        {
            this.undo.remove(0).delete();
        }

        this.undo.add(this.pixels.createCopy(0, 0, this.pixels.width, this.pixels.height));
        
        if (!this.redo.isEmpty())
        {
            for (Pixels pixels : this.redo)
            {
                pixels.delete();
            }

            this.redo.clear();
        }
    }

    private void updateTexture()
    {
        this.pixels.rewindBuffer();
        this.texture.bind();
        this.texture.updateTexture(this.pixels);
    }

    private void toggleChalk()
    {
        this.isChalkEnabled = !this.isChalkEnabled;
        this.picker.setEnabled(this.isChalkEnabled);
        this.size.setEnabled(this.isChalkEnabled);
        
        if (!this.isChalkEnabled)
        {
            this.placingLine = false;
        }
    }

    public boolean isChalkDisabled()
    {
        return !this.isChalkEnabled;
    }

    /* Input handling */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.isChalkDisabled())
        {
            return false;
        }

        if (context.mouseButton == 0)
        {
            if (this.lineMode)
            {
                if (this.placingLine)
                {
                    this.finishLine(context);
                }
                else
                {
                    this.saveUndo();
                    this.placingLine = true;
                    this.startX = context.mouseX;
                    this.startY = context.mouseY;
                }
            }
            else
            {
                this.saveUndo();
                this.drawing = true;

                this.lastX = context.mouseX;
                this.lastY = context.mouseY;
                this.startX = context.mouseX;
                this.startY = context.mouseY;
            }

            return true;
        }
        else if (context.mouseButton == 1)
        {
            this.placingLine = false;
        }

        return super.subMouseClicked(context);
    }

    private void finishLine(UIContext context)
    {
        int scale = Math.round(BBSSettings.userIntefaceScale.get());
        int x = context.mouseX;
        int y = context.mouseY;
        
        if (Screen.hasShiftDown())
        {
            Vector2i snapped = this.snap(this.startX, this.startY, x, y);
            
            x = snapped.x;
            y = snapped.y;
        }
        
        this.drawStroke(this.startX, this.startY, x, y, scale);
        this.updateTexture();
        this.placingLine = false;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.isChalkDisabled())
        {
            return false;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.isChalkDisabled())
        {
            return false;
        }

        if (context.mouseButton == 0)
        {
            if (this.lineMode && this.placingLine)
            {
                /* If dragged more than 5 pixels, consider it a drag action */
                if (Math.hypot(context.mouseX - this.startX, context.mouseY - this.startY) > 5)
                {
                    this.finishLine(context);
                }
            }

            this.drawing = false;
        }

        return super.subMouseReleased(context);
    }

    @Override
    public void resize()
    {
        super.resize();

        if (this.pixels != null)
        {
            this.pixels.delete();
        }
        
        for (Pixels pixels : this.undo)
        {
            pixels.delete();
        }
        
        for (Pixels pixels : this.redo)
        {
            pixels.delete();
        }
        
        this.undo.clear();
        this.redo.clear();

        int scale = Math.round(BBSSettings.userIntefaceScale.get());

        this.pixels = Pixels.fromSize(this.area.w * scale, this.area.h * scale);
        this.updateTexture();
    }

    private Vector2i snap(int x1, int y1, int x2, int y2)
    {
        int dx = x2 - x1;
        int dy = y2 - y1;
        double angle = Math.atan2(dy, dx);
        double snappedAngle = Math.round(angle / (Math.PI / 4)) * (Math.PI / 4);
        double length = Math.sqrt(dx * dx + dy * dy);
        
        return new Vector2i(
            x1 + (int) (Math.cos(snappedAngle) * length),
            y1 + (int) (Math.sin(snappedAngle) * length)
        );
    }

    private void drawStroke(int x1, int y1, int x2, int y2, int scale)
    {
        double distance = new Vector2d(x2, y2).distance(x1, y1);
        int size = (int) this.size.getValue();
        int half = size / 2;

        for (int i = 0; i < distance; i++)
        {
            int xx = (int) (Lerps.lerp(x2 * scale, x1 * scale, i / distance));
            int yy = (int) (Lerps.lerp(y2 * scale, y1 * scale, i / distance));

            this.pixels.drawRect(xx - half, yy - half, size, size, this.color);
        }
        
        /* Draw the last pixel to ensure end point is covered */
        this.pixels.drawRect(x2 * scale - half, y2 * scale - half, size, size, this.color);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.isChalkDisabled())
        {
            return;
        }

        int scale = Math.round(BBSSettings.userIntefaceScale.get());
        int x = context.mouseX;
        int y = context.mouseY;

        if (this.pixels != null)
        {
            if (this.drawing)
            {
                this.drawStroke(this.lastX, this.lastY, x, y, scale);
                this.updateTexture();

                this.lastX = x;
                this.lastY = y;
            }

            context.batcher.fullTexturedBox(this.texture, this.area.x, this.area.y, this.area.w, this.area.h);

            if (this.lineMode && this.placingLine)
            {
                int ex = x;
                int ey = y;

                if (Screen.hasShiftDown())
                {
                    Vector2i snapped = this.snap(this.startX, this.startY, ex, ey);

                    ex = snapped.x;
                    ey = snapped.y;
                }

                context.batcher.line(this.startX, this.startY, ex, ey, (float) this.size.getValue(), this.color);
            }
        }
        
        if (this.lineMode)
        {
            context.batcher.icon(Icons.MAXIMIZE, x + 8, y + 8, Colors.WHITE);
        }

        context.batcher.box(x - 1, y - 1, x + 1, y + 1, this.color);

        super.render(context);
    }
}