package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.ITextColoring;
import mchorse.bbs_mod.ui.framework.theme.IUIStyleProvider;
import mchorse.bbs_mod.ui.framework.theme.UIThemeManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIButton extends UIClickable<UIButton> implements ITextColoring
{
    private static final int MIN_HEIGHT = 20;
    private static final int LINE_HEIGHT = 12;
    private static final int VERTICAL_PADDING = 8;

    /**
     * Max wrapped label lines before the last line is ellipsized.
     * Adjustable if clip/panel layouts need a taller or shorter cap later.
     */
    public static int MAX_LABEL_LINES = 3;

    public IKey label;

    public int textColor = Colors.WHITE;
    public boolean textShadow = true;

    public boolean custom;
    public int customColor;
    public boolean background = true;

    private Supplier<Icon> leadingIcon;
    private boolean wrapping = true;
    private List<String> wrappedLines;
    private String lastWrappedText;
    private int lastWrapWidth = -1;
    private int wrappedHeight = MIN_HEIGHT;

    public UIButton(IKey label, Consumer<UIButton> callback)
    {
        super(callback);

        this.label = label;
        this.h(MIN_HEIGHT);
    }

    public UIButton color(int color)
    {
        this.custom = true;
        this.customColor = color & Colors.RGB;

        return this;
    }

    /**
     * Draws an icon inset on the left edge of the button (e.g. disclosure arrows).
     */
    public UIButton leadingIcon(Supplier<Icon> icon)
    {
        this.leadingIcon = icon;

        return this;
    }

    public UIButton leadingIcon(Icon icon)
    {
        this.leadingIcon = icon == null ? null : () -> icon;

        return this;
    }

    public UIButton textColor(int color, boolean shadow)
    {
        this.textColor = color;
        this.textShadow = shadow;

        return this;
    }

    public UIButton background(boolean background)
    {
        this.background = background;

        return this;
    }

    public UIButton label(IKey label)
    {
        this.label = label;
        this.invalidateWrappedLabel();

        return this;
    }

    /**
     * Wrap long labels onto multiple lines (up to {@link #MAX_LABEL_LINES})
     * and grow the button height instead of truncating with a single-line ellipsis.
     * Enabled by default for narrow panels such as the film clip inspector.
     */
    public UIButton wrapping()
    {
        return this.wrapping(true);
    }

    public UIButton wrapping(boolean wrapping)
    {
        this.wrapping = wrapping;
        this.invalidateWrappedLabel();

        return this;
    }

    @Override
    public void setColor(int color, boolean shadow)
    {
        this.textColor = color;
        this.textShadow = shadow;
    }

    @Override
    protected UIButton get()
    {
        return this;
    }

    @Override
    public void resize()
    {
        super.resize();

        this.invalidateWrappedLabel();
    }

    private void invalidateWrappedLabel()
    {
        this.wrappedLines = null;
        this.lastWrappedText = null;
        this.lastWrapWidth = -1;
    }

    private void ensureWrappedLabel(FontRenderer font, int maxWidth)
    {
        String text = this.label == null ? "" : this.label.get();

        if (this.wrappedLines != null && text.equals(this.lastWrappedText) && maxWidth == this.lastWrapWidth)
        {
            return;
        }

        List<String> lines;

        if (text.isEmpty() || maxWidth <= 0)
        {
            lines = Collections.emptyList();
        }
        else if (this.wrapping)
        {
            lines = this.limitWrappedLines(font, font.wrap(text, maxWidth), maxWidth);
        }
        else
        {
            lines = Collections.singletonList(font.limitToWidth(text, maxWidth));
        }

        int lineCount = Math.max(1, lines.isEmpty() ? 1 : lines.size());
        int textHeight = lineCount * LINE_HEIGHT - (LINE_HEIGHT - font.getHeight());
        int height = Math.max(MIN_HEIGHT, textHeight + VERTICAL_PADDING);

        if (height != this.wrappedHeight)
        {
            this.wrappedHeight = height;
            this.h(height);

            UIElement container = this.getParentContainer();

            if (container != null)
            {
                /* Parent resize clears caches via resize(); restore lines afterward. */
                container.resize();
            }
        }

        this.wrappedLines = lines;
        this.lastWrappedText = text;
        this.lastWrapWidth = maxWidth;
    }

    private List<String> limitWrappedLines(FontRenderer font, List<String> lines, int maxWidth)
    {
        int maxLines = Math.max(1, MAX_LABEL_LINES);

        if (lines.size() <= maxLines)
        {
            return lines;
        }

        List<String> limited = new ArrayList<>(lines.subList(0, maxLines));

        limited.set(maxLines - 1, font.limitToWidth(limited.get(maxLines - 1), maxWidth));

        return limited;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        Icon icon = this.leadingIcon == null ? null : this.leadingIcon.get();
        int iconPad = icon == null ? 0 : icon.w + 4;

        this.ensureWrappedLabel(font, Math.max(0, this.area.w - 6 - iconPad));

        IUIStyleProvider theme = UIThemeManager.getActiveTheme();

        if (theme != null && theme.renderButtonSkin(context, this))
        {
            return;
        }

        if (this.background)
        {
            int base = (this.custom ? this.customColor : BBSSettings.accentRgb()) & Colors.RGB;

            int fill;
            int border;

            if (this.hover)
            {
                fill = 0xFF000000 | Colors.mulRGB(base, 1.18F);
                border = 0xFF000000 | Colors.mulRGB(base, 1.5F);
            }
            else
            {
                fill = 0xFF000000 | base;
                border = 0xFF000000 | Colors.mulRGB(base, 0.5F);
            }

            int x1 = this.area.x;
            int y1 = this.area.y;
            int x2 = this.area.ex();
            int y2 = this.area.ey();

            /* Connected buttons (placed flush) merge into one shape: every
               border side that faces a neighbour is skipped, so a row of
               buttons shares a single continuous outline with no inner seams. */
            boolean nLeft = this.hasNeighbor(-1, 0);
            boolean nRight = this.hasNeighbor(1, 0);
            boolean nTop = this.hasNeighbor(0, -1);
            boolean nBottom = this.hasNeighbor(0, 1);

            /* Drop shadow behind the button. */
            if (!nBottom)
            {
                context.batcher.box(x1, y1 + 2, x2, y2 + 2, 0x55000000);
            }

            /* Flat fill. */
            context.batcher.box(x1, y1, x2, y2, fill);

            /* Bottom inner shading edge — slight cube depth. */
            if (!nBottom)
            {
                context.batcher.box(x1, y2 - 2, x2, y2, 0xFF000000 | Colors.mulRGB(base, this.hover ? 0.9F : 0.66F));
            }

            /* Border stroke — sides facing a connected neighbour are omitted. */
            if (!nTop)    context.batcher.box(x1, y1, x2, y1 + 1, border);
            if (!nBottom) context.batcher.box(x1, y2 - 1, x2, y2, border);
            if (!nLeft)   context.batcher.box(x1, y1, x1 + 1, y2, border);
            if (!nRight)  context.batcher.box(x2 - 1, y1, x2, y2, border);
        }

        if (icon != null)
        {
            context.batcher.icon(icon, Colors.WHITE, this.area.x + 2 + icon.w / 2, this.area.my(), 0.5F, 0.5F);
        }

        if (this.wrappedLines != null && !this.wrappedLines.isEmpty())
        {
            int contentLeft = this.area.x + iconPad;
            int textHeight = this.wrappedLines.size() * LINE_HEIGHT - (LINE_HEIGHT - font.getHeight());
            int textY = this.area.my() - textHeight / 2;

            for (String line : this.wrappedLines)
            {
                int tx = contentLeft + Math.max(0, (this.area.ex() - contentLeft - font.getWidth(line)) / 2);

                context.batcher.text(line, tx, textY, this.textColor, this.textShadow);
                textY += LINE_HEIGHT;
            }
        }

        this.renderLockedArea(context);
    }

    /* True when another backgrounded button sits flush against this one in the
       given direction: dx -1/+1 = left/right, dy -1/+1 = above/below. */
    private boolean hasNeighbor(int dx, int dy)
    {
        UIElement parent = this.getParent();

        if (parent == null)
        {
            return false;
        }

        for (IUIElement child : parent.getChildren())
        {
            if (child == this || !(child instanceof UIButton other))
            {
                continue;
            }

            if (!other.background || !other.isVisible())
            {
                continue;
            }

            if (dx > 0 && touchesV(other, this.area.ex(), other.area.x))
            {
                return true;
            }
            if (dx < 0 && touchesV(other, this.area.x, other.area.ex()))
            {
                return true;
            }
            if (dy > 0 && touchesH(other, this.area.ey(), other.area.y))
            {
                return true;
            }
            if (dy < 0 && touchesH(other, this.area.y, other.area.ey()))
            {
                return true;
            }
        }

        return false;
    }

    /* Vertically-stacked-edges helper: the two given X coords touch and the
       other button shares this one's vertical span. */
    private boolean touchesV(UIButton other, int edgeA, int edgeB)
    {
        return Math.abs(edgeA - edgeB) <= 1
            && other.area.y == this.area.y && other.area.h == this.area.h;
    }

    /* Horizontal-edges helper: the two given Y coords touch and the other
       button shares this one's horizontal span. */
    private boolean touchesH(UIButton other, int edgeA, int edgeB)
    {
        return Math.abs(edgeA - edgeB) <= 1
            && other.area.x == this.area.x && other.area.w == this.area.w;
    }
}
