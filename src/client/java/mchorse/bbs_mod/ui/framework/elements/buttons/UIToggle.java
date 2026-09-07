package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.ITextColoring;
import mchorse.bbs_mod.ui.framework.theme.IUIStyleProvider;
import mchorse.bbs_mod.ui.framework.theme.UIThemeManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class UIToggle extends UIClickable<UIToggle> implements ITextColoring
{
    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 12;
    private static final int LINE_HEIGHT = 12;
    private static final int MIN_HEIGHT = 14;

    public IKey label;
    public int color = Colors.WHITE;
    public boolean textShadow = true;
    private boolean value;
    private float currentKnobX = -1F;
    private boolean wrapping = true;
    private List<String> wrappedLines;
    private String lastWrappedText;
    private int lastWrapWidth = -1;
    private int wrappedHeight = MIN_HEIGHT;

    public UIToggle(IKey label, Consumer<UIToggle> callback)
    {
        this(label, false, callback);
    }

    public UIToggle(IKey label, boolean value, Consumer<UIToggle> callback)
    {
        super(callback);

        this.label = label;
        this.value = value;
        this.h(MIN_HEIGHT);
    }

    @Override
    public void setColor(int color, boolean shadow)
    {
        this.color(color, shadow);
    }

    public UIToggle label(IKey label)
    {
        this.label = label;
        this.invalidateWrappedLabel();

        return this;
    }

    /**
     * Wrap long labels onto multiple lines instead of truncating with ellipsis.
     * Enabled by default for narrow panels such as the film clip inspector.
     */
    public UIToggle wrapping()
    {
        return this.wrapping(true);
    }

    public UIToggle wrapping(boolean wrapping)
    {
        this.wrapping = wrapping;
        this.invalidateWrappedLabel();

        return this;
    }

    /**
     * Measure wrapped height from the current area so popup menus can grow
     * around long toggle labels before the first render.
     */
    public UIToggle wrapToWidth()
    {
        int labelWidth = Math.max(0, this.area.w - SWITCH_WIDTH - 8);

        this.wrapping(true);
        this.ensureWrappedLabel(Batcher2D.getDefaultTextRenderer(), labelWidth);

        return this;
    }

    public UIToggle setValue(boolean value)
    {
        this.value = value;

        return this;
    }

    public UIToggle color(int color)
    {
        return this.color(color, true);
    }

    public UIToggle color(int color, boolean textShadow)
    {
        this.color = color;
        this.textShadow = textShadow;

        return this;
    }

    public boolean getValue()
    {
        return this.value;
    }

    @Override
    protected void click(int mouseWheel)
    {
        this.value = !this.value;

        super.click(mouseWheel);
    }

    @Override
    protected UIToggle get()
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

    private int interpolateColor(int c1, int c2, float t)
    {
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
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
        int height = Math.max(MIN_HEIGHT, lineCount * LINE_HEIGHT - (LINE_HEIGHT - font.getHeight()) + 2);

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
        int maxLines = Math.max(1, UIButton.MAX_LABEL_LINES);

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
        IUIStyleProvider theme = UIThemeManager.getActiveTheme();

        if (theme != null && theme.renderToggleSkin(context, this))
        {
            return;
        }
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        /* Square (cube-shaped) toggle switch. */
        int w = SWITCH_WIDTH;
        int h = SWITCH_HEIGHT;
        int x = this.area.ex() - w - 2;
        int y = this.area.my() - h / 2;
        int labelWidth = Math.max(0, this.area.w - w - 8);

        this.ensureWrappedLabel(font, labelWidth);

        if (!this.wrappedLines.isEmpty())
        {
            int textHeight = this.wrappedLines.size() * LINE_HEIGHT - (LINE_HEIGHT - font.getHeight());
            int textY = this.area.my() - textHeight / 2;

            for (String line : this.wrappedLines)
            {
                batcher.text(line, this.area.x, textY, this.color, this.textShadow);
                textY += LINE_HEIGHT;
            }
        }

        /* Track — primary color when on, dark grey when off, with a 1px border. */
        int primary = 0xFF000000 | BBSSettings.primaryColor.get();

        float targetKnobX = this.value ? 1.0F : 0.0F;

        if (this.currentKnobX < 0)
        {
            this.currentKnobX = targetKnobX;
        }

        if (BBSSettings.editorSimplifyAnimations.get())
        {
            this.currentKnobX = targetKnobX;
        }
        else
        {
            this.currentKnobX += (targetKnobX - this.currentKnobX) * 0.25F;
        }

        int finalActive = this.hover ? Colors.mulRGB(primary, 1.15F) : primary;
        int finalInactive = this.hover ? 0xFF4A4A52 : 0xFF3B3B43;
        int trackColor = this.interpolateColor(finalInactive, finalActive, this.currentKnobX);

        batcher.box(x, y, x + w, y + h, trackColor);
        batcher.outline(x, y, x + w, y + h, 0xFF000000 | Colors.mulRGB(trackColor, 0.55F));

        /* Knob — a square block that slides to the right when on, with a
           1px drop shadow for depth. */
        int knobSize = h - 4;
        int knobX = x + 2 + (int) (this.currentKnobX * (w - knobSize - 4));
        int knobY = y + 2;

        batcher.box(knobX, knobY + 1, knobX + knobSize, knobY + knobSize + 1, 0x66000000);
        batcher.box(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFFFFFFF);
        batcher.box(knobX, knobY + knobSize - 2, knobX + knobSize, knobY + knobSize, 0xFFD2D2D6);

        if (!this.isEnabled())
        {
            boolean labeled = this.label != null && !this.label.get().isEmpty();

            /* Compact / unlabeled toggles only dim; labeled ones keep the lock cue. */
            batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), labeled ? 0xAA000000 : 0x99000000);

            if (labeled)
            {
                batcher.outlinedIcon(Icons.LOCKED, this.area.mx(), this.area.my(), 0.5F, 0.5F);
            }
        }
    }
}
