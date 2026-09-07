package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.text.UIBaseTextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Factor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;

import org.lwjgl.glfw.GLFW;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class UITrackpad extends UIBaseTextbox
{
    private static final Set<Character> allowedNumberCharacters = ".-+/*^%() ".chars()
        .mapToObj((o) -> (char) o)
        .collect(Collectors.toSet());
    private static final Factor globalFactor = new Factor(20, 1, 40, (x) ->
    {
        if (x <= 10) return x / 100D;
        else if (x <= 20) return (x - 10) / 10D;
        else if (x <= 30) return (x - 20) / 1D;

        return (x - 30) * 10D;
    });

    private static final DecimalFormat FORMAT;
    private static final DecimalFormat FORMAT_2;
    private static final DecimalFormat FORMAT_1;
    private static final DecimalFormat FORMAT_0;

    public Consumer<Double> callback;

    protected double value;

    /* Trackpad options */
    public double strong = 1D;
    public double normal = 0.25D;
    public double weak = 0.05D;
    public double increment = 1D;
    public double min = Float.NEGATIVE_INFINITY;
    public double max = Float.POSITIVE_INFINITY;
    public boolean integer;
    public boolean delayedInput;
    public boolean onlyNumbers;

    public boolean relative;
    public boolean allowCanceling = true;
    public boolean fitFormat = true;
    public IKey forcedLabel;

    /* Value dragging fields */
    private boolean wasInside;
    private boolean dragging;
    private boolean warpedLeft;
    private boolean warpedRight;
    private double shiftX;
    private double initialX;
    private double lastValue;
    private int initialY;
    private int grabX;

    private long time;
    private Area plusOne = new Area();
    private Area minusOne = new Area();

    static
    {
        FORMAT = new DecimalFormat("#.###");
        FORMAT.setRoundingMode(RoundingMode.HALF_EVEN);
        FORMAT.setGroupingUsed(false);
        FORMAT.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.ENGLISH));

        FORMAT_2 = new DecimalFormat("#.##");
        FORMAT_2.setRoundingMode(RoundingMode.HALF_EVEN);
        FORMAT_2.setGroupingUsed(false);
        FORMAT_2.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.ENGLISH));

        FORMAT_1 = new DecimalFormat("#.#");
        FORMAT_1.setRoundingMode(RoundingMode.HALF_EVEN);
        FORMAT_1.setGroupingUsed(false);
        FORMAT_1.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.ENGLISH));

        FORMAT_0 = new DecimalFormat("#");
        FORMAT_0.setRoundingMode(RoundingMode.HALF_EVEN);
        FORMAT_0.setGroupingUsed(false);
        FORMAT_0.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.ENGLISH));
    }

    public static void updateAmplifier(UIContext context)
    {
        globalFactor.addX((int) context.mouseWheel);
        context.notifyOrUpdate(UIKeys.TRACKPAD_GLOBAL_AMPLIFIER.format(globalFactor.getValue()), Colors.BLUE);
    }

    public static String format(double number)
    {
        return FORMAT.format(number).replace(',', '.');
    }

    public UITrackpad()
    {
        this(null);
    }

    public UITrackpad(Consumer<Double> callback)
    {
        super();

        this.callback = callback;

        this.setValue(0);
        this.h(20);
    }

    public UITrackpad max(double max)
    {
        this.max = max;

        return this;
    }

    public UITrackpad limit(double min)
    {
        this.min = min;

        return this;
    }

    public UITrackpad limit(double min, double max)
    {
        this.min = min;
        this.max = max;

        return this;
    }

    public UITrackpad limit(ValueInt value)
    {
        return this.limit(value.getMin(), value.getMax(), true);
    }

    public UITrackpad limit(ValueFloat value)
    {
        return this.limit(value.getMin(), value.getMax(), false);
    }

    public UITrackpad limit(ValueDouble value)
    {
        return this.limit(value.getMin(), value.getMax(), false);
    }

    public UITrackpad limit(double min, double max, boolean integer)
    {
        this.integer = integer;

        return this.limit(min, max);
    }

    public UITrackpad integer()
    {
        this.integer = true;

        return this;
    }

    public UITrackpad increment(double increment)
    {
        this.increment = increment;

        return this;
    }

    public UITrackpad values(double normal)
    {
        this.normal = normal;
        this.weak = normal / 5F;
        this.strong = normal * 5F;

        return this;
    }

    public UITrackpad values(double normal, double weak, double strong)
    {
        this.normal = normal;
        this.weak = weak;
        this.strong = strong;

        return this;
    }

    public UITrackpad delayedInput()
    {
        this.delayedInput = true;

        return this;
    }

    public UITrackpad onlyNumbers()
    {
        this.onlyNumbers = true;

        return this;
    }

    public UITrackpad relative(boolean relative)
    {
        this.relative = relative;

        return this;
    }

    public UITrackpad forcedLabel(IKey label)
    {
        this.forcedLabel = label;

        return this;
    }

    /**
     * Show the full formatted value; skip scientific / compact shortening when the field is narrow.
     */
    public UITrackpad plainFormat()
    {
        this.fitFormat = false;

        return this;
    }

    public UITrackpad disableCanceling()
    {
        this.allowCanceling = false;

        return this;
    }

    /* Values presets */

    public UITrackpad degrees()
    {
        return this.increment(15D).values(1D, 0.1D, 5D  );
    }

    public UITrackpad block()
    {
        return this.increment(1 / 16D).values(1 / 32D, 1 / 128D, 1 / 2D);
    }

    public UITrackpad metric()
    {
        return this.values(0.1D, 0.01D, 1);
    }

    /**
     * Whether this trackpad is dragging
     */
    public boolean isDragging()
    {
        return this.dragging;
    }

    public boolean isDraggingTime()
    {
        return this.isDragging() && System.currentTimeMillis() - this.time > 150;
    }

    public double getValue()
    {
        return this.value;
    }

    /**
     * Set the value of the field. Always updates the numeric value. While this
     * trackpad is the active text editor, keep the textbox contents intact so
     * mid-typing refreshes cannot clobber input.
     */
    public void setValue(double value)
    {
        this.setValueInternal(value);

        if (!this.isActivelyEditing())
        {
            this.updateTextField();
        }
    }

    /**
     * True when this trackpad both shows a focused textbox and is the context's
     * active element (real keyboard target).
     */
    public boolean isActivelyEditing()
    {
        if (!this.textbox.isFocused())
        {
            return false;
        }

        UIContext context = this.getContext();

        return context != null && context.activeElement == this;
    }

    /**
     * If the textbox focus flag drifted from the UI context (e.g. after an
     * embedded layout toggle), clear it so clicks/drags work again.
     */
    private void syncStaleTextFocus(UIContext context)
    {
        if (this.textbox.isFocused() && (context == null || context.activeElement != this))
        {
            this.textbox.setFocused(false);
        }
    }

    private void updateTextField()
    {
        if (Window.isAltPressed())
        {
            this.textbox.setText(this.integer ? String.valueOf((int) this.value) : String.valueOf(this.value));
        }
        else
        {
            this.textbox.setText(this.integer ? format((int) this.value) : format(this.value));
        }
    }

    private void setValueInternal(double value)
    {
        value = MathUtils.clamp(value, this.min, this.max);

        if (this.integer)
        {
            value = (int) value;
        }

        this.value = value;
    }

    /**
     * Set value of this field and also notify the trackpad listener so it
     * could detect the value change.
     */
    public void setValueAndNotify(double value)
    {
        double oldValue = this.value;

        this.setValueInternal(value);
        this.updateTextField();

        if (this.isActivelyEditing())
        {
            this.textbox.moveCursorToEnd();
        }

        this.accept(this.value, oldValue);
    }

    private void accept(double value, double oldValue)
    {
        if (this.callback != null)
        {
            this.callback.accept(this.relative ? value - oldValue : this.value);
        }
    }

    @Override
    public void focus(UIContext context)
    {
        super.focus(context);

        this.updateTextField();
        this.textbox.setFocused(true);
        this.textbox.moveCursorToEnd();
    }

    @Override
    public void unfocus(UIContext context)
    {
        String text = this.textbox.getText().trim();

        if (text.isEmpty())
        {
            double oldValue = this.value;

            this.setValueInternal(0D);

            super.unfocus(context);

            this.textbox.setFocused(false);
            this.updateTextField();
            this.accept(this.value, oldValue);

            return;
        }
        
        this.evaluate();

        super.unfocus(context);

        this.textbox.setFocused(false);

        /* Reset the value in case it's out of range */
        if (this.delayedInput)
        {
            this.setValueAndNotify(this.value);
        }
        else
        {
            this.setValue(this.value);
        }
    }

    /**
     * Update the bounding box of this GUI field
     */
    @Override
    public void resize()
    {
        super.resize();

        /* Increment buttons sit on opposite edges. */
        int w = this.area.w < 60 ? 10 : 13;

        this.textbox.area.copy(this.area);
        this.plusOne.copy(this.area);
        this.minusOne.copy(this.area);
        this.plusOne.w = this.minusOne.w = w;
        this.plusOne.x = this.area.ex() - w;
        this.minusOne.x = this.area.x;
    }

    /**
     * Delegates mouse click to text field and initiate value dragging if the
     * cursor inside of trackpad's bounding box.
     */
    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.allowCanceling && context.mouseButton == 1 && this.isDragging())
        {
            this.setValueAndNotify(this.lastValue);

            this.wasInside = false;
            this.dragging = false;
            this.shiftX = 0D;
            this.warpedLeft = false;
            this.warpedRight = false;

            return true;
        }

        if (context.mouseButton == 2 && this.area.isInside(context))
        {
            this.setValueAndNotify(-this.value);

            return true;
        }

        this.wasInside = this.area.isInside(context);

        if (context.mouseButton == 0)
        {
            this.syncStaleTextFocus(context);

            if (this.textbox.isFocused())
            {
                this.textbox.mouseClicked(context.mouseX, context.mouseY, context.mouseButton);

                if (!this.textbox.isFocused())
                {
                    context.focus(null);
                }
            }

            if (this.wasInside && !this.textbox.isFocused())
            {
                if (Window.isCtrlPressed())
                {
                    this.setValueAndNotify(Math.round(this.value));
                    this.wasInside = false;

                    return true;
                }

                MinecraftClient mc = MinecraftClient.getInstance();
                double factor = context.menu.width <= 0 ? 1D : (double) mc.getWindow().getWidth() / context.menu.width;

                this.dragging = true;
                this.shiftX = 0D;
                this.warpedLeft = false;
                this.warpedRight = false;
                this.initialX = mc.mouse.getX() / factor;
                this.initialY = context.mouseY;
                this.grabX = context.mouseX;
                this.time = System.currentTimeMillis();

                /* Emit before caching lastValue so listeners can re-sync the
                 * numeric value from the model (e.g. keyframe tick). */
                this.getEvents().emit(new UITrackpadDragStartEvent(this));
                this.lastValue = this.value;
            }
        }

        return context.mouseButton == 0 && this.wasInside;
    }

    /**
     * Reset value dragging
     */
    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (context.mouseButton == 1 && this.isDragging())
        {
            this.setValueAndNotify(this.lastValue);

            this.wasInside = false;
            this.dragging = false;
            this.shiftX = 0D;
            this.warpedLeft = false;
            this.warpedRight = false;

            return true;
        }

        this.textbox.mouseReleased(context.mouseX, context.mouseY, context.mouseButton);
        this.syncStaleTextFocus(context);

        if (context.mouseButton == 0 && !this.isDraggingTime() && !this.isActivelyEditing())
        {
            if (this.wasInside)
            {
                if (this.plusOne.isInside(context))
                {
                    this.setValueAndNotify(this.value + this.getArrowStep());
                }
                else if (this.minusOne.isInside(context))
                {
                    this.setValueAndNotify(this.value - this.getArrowStep());
                }
                else
                {
                    context.focus(this);
                }
            }
        }

        if (this.delayedInput && this.isDraggingTime())
        {
            this.setValueAndNotify(this.value);
        }

        if (this.dragging)
        {
            this.getEvents().emit(new UITrackpadDragEndEvent(this));
        }

        this.wasInside = false;
        this.dragging = false;
        this.shiftX = 0D;
        this.warpedLeft = false;
        this.warpedRight = false;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        Area area = new Area();
        int w = this.area.w / 2;

        area.copy(this.area);
        area.x = area.mx() - w / 2;
        area.w = w;

        if (this.dragging)
        {
            updateAmplifier(context);

            return true;
        }
        else if (area.isInside(context) && context.hasNotScrolledForMore(500) && BBSSettings.enableTrackpadScrolling.get())
        {
            double step = this.getScrollStep();

            if (context.mouseWheel > 0)
            {
                this.setValueAndNotify(this.value + step);
            }
            else
            {
                this.setValueAndNotify(this.value - step);
            }

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        this.syncStaleTextFocus(context);

        if (this.isActivelyEditing())
        {
            if (context.isHeld(GLFW.GLFW_KEY_UP))
            {
                this.setValueAndNotify(this.value + this.getScrollStep());

                return true;
            }
            else if (context.isHeld(GLFW.GLFW_KEY_DOWN))
            {
                this.setValueAndNotify(this.value - this.getScrollStep());

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_TAB))
            {
                context.focus(this, Window.isShiftPressed() ? -1 : 1);

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                context.unfocus();

                return true;
            }
            else if (context.isPressed(GLFW.GLFW_KEY_ENTER))
            {
                context.unfocus();

                return true;
            }
        }
        else if (this.area.isInside(context))
        {
            if (!context.isFocused() && (context.isPressed(GLFW.GLFW_KEY_MINUS) || context.isPressed(GLFW.GLFW_KEY_KP_SUBTRACT)))
            {
                this.setValueAndNotify(-this.value);

                return true;
            }
        }

        String old = this.textbox.getText();
        boolean result = this.textbox.keyPressed(context);
        String text = this.textbox.getText();

        if (this.textbox.isFocused() && !text.equals(old))
        {
            if (text.isEmpty())
            {
                return result;
            }

            try
            {
                double oldValue = this.value;

                this.setValueInternal(Double.parseDouble(text));

                if (!this.delayedInput)
                {
                    this.accept(this.value, oldValue);
                }
            }
            catch (Exception e)
            {}
        }

        return result;
    }

    private void evaluate()
    {
        String text = this.textbox.getText().trim();

        try
        {
            Float.parseFloat(text);

            return;
        }
        catch (Exception e)
        {}

        try
        {
            MathBuilder builder = new MathBuilder();

            this.setValueAndNotify(builder.parse(text).get().doubleValue());
            this.textbox.moveCursorToEnd();
        }
        catch (Exception e)
        {}
    }

    @Override
    public boolean subTextInput(UIContext context)
    {
        char inputCharacter = context.getInputCharacter();

        if (this.onlyNumbers && this.isFocused() && !this.numberCharacterAllowed(inputCharacter))
        {
            context.unfocus();

            return false;
        }

        String old = this.textbox.getText();
        boolean result = this.textbox.textInput(inputCharacter);
        String text = this.textbox.getText();

        if (this.textbox.isFocused() && !text.equals(old))
        {
            if (text.isEmpty())
            {
                return result;
            }

            try
            {
                double oldValue = this.value;

                this.setValueInternal(Double.parseDouble(text));

                if (!this.delayedInput)
                {
                    this.accept(this.value, oldValue);
                }
            }
            catch (Exception e)
            {}
        }

        return result;
    }

    private boolean numberCharacterAllowed(char character)
    {
        return Character.isDigit(character) || allowedNumberCharacters.contains(character);
    }

    /**
     * Draw the trackpad
     *
     * This method will not only render the text box, background and title label,
     * but also dragging the numerical value based on the mouse input.
     */
    @Override
    public void render(UIContext context)
    {
        this.syncStaleTextFocus(context);

        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int h = this.area.h;
        int padding = 0;

        boolean dragging = this.isDraggingTime();
        boolean hovered = this.area.isInside(context);
        int accent = 0xFF000000 | BBSSettings.accentRgb();
        FontRenderer font = context.batcher.getFont();
        boolean wantsArrows = this.isEnabled() && BBSSettings.enableTrackpadIncrements.get() && hovered;
        boolean showArrows = wantsArrows && this.area.w >= this.minusOne.w + this.plusOne.w + 6;
        boolean showMinusArrow = showArrows;
        boolean showPlusArrow = showArrows;
        boolean plus = !dragging && showPlusArrow && this.plusOne.isInside(context);
        boolean minus = !dragging && showMinusArrow && this.minusOne.isInside(context);

        if (this.isActivelyEditing())
        {
            this.textbox.render(context);

            /* Accent border while editing the value. */
            context.batcher.outline(x, y, x + w, y + h, accent);
        }
        else
        {
            /* Flat dark background. */
            context.batcher.box(x, y, x + w, y + h, 0xFF1A1A20);

            if (dragging)
            {
                /* Draw the drag-delta fill from the grab point to the cursor. */
                int grab = MathUtils.clamp(this.grabX, this.area.x + padding, this.area.ex() - padding);
                int fx = MathUtils.clamp(context.mouseX, this.area.x + padding, this.area.ex() - padding);

                context.batcher.box(Math.min(fx, grab), this.area.y + padding, Math.max(fx, grab), this.area.ey() - padding, accent);
            }

            /* Value label — centered, clipped so it never runs under the
               increment buttons. */
            int textLeft = this.area.x + (showMinusArrow ? this.minusOne.w + 1 : 2);
            int textRight = this.area.ex() - (showPlusArrow ? this.plusOne.w + 1 : 2);
            int availableTextWidth = Math.max(1, textRight - textLeft);
            String raw = this.forcedLabel != null
                ? this.forcedLabel.get()
                : (this.fitFormat ? this.formatToFit(font, this.value, availableTextWidth) : format(this.value));
            String label = this.truncateToWidth(font, raw, availableTextWidth);

            int lx = textLeft + Math.max(0, (availableTextWidth - font.getWidth(label)) / 2);
            int ly = this.area.my() - font.getHeight() / 2;

            context.batcher.text(label, lx, ly, this.textbox.getColor());

            /* Increment / decrement chevrons appear only on the hovered side. */
            if (showMinusArrow)
            {
                this.minusOne.render(context.batcher, minus ? 0x28FFFFFF : 0x10FFFFFF, padding);

                int mColor = minus ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.5F);

                drawChevron(context, this.minusOne.mx(), this.minusOne.my(), true, mColor);
            }

            if (showPlusArrow)
            {
                this.plusOne.render(context.batcher, plus ? 0x28FFFFFF : 0x10FFFFFF, padding);

                int pColor = plus ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.5F);

                drawChevron(context, this.plusOne.mx(), this.plusOne.my(), false, pColor);
            }

            /* Border — accent when hovered or dragging, subtle grey otherwise. */
            int border = (dragging || hovered) ? accent : 0xFF3C3C3C;
            context.batcher.outline(x, y, x + w, y + h, border);
        }

        if (this.dragging)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            int ww = mc.getWindow().getWidth();

            double factor = context.menu.width <= 0 ? 1D : (double) ww / context.menu.width;
            int mouseXInt = context.globalX(context.mouseX);
            double mouseX = mc.mouse.getX() / factor;

            final int border = 5;
            final int borderPadding = border + 1;
            boolean stop = false;

            if (this.warpedRight)
            {
                if (mouseXInt <= context.menu.width / 2)
                {
                    this.shiftX += context.menu.width - borderPadding * 2;
                    this.warpedRight = false;
                }
                else
                {
                    stop = true;
                }
            }
            else if (this.warpedLeft)
            {
                if (mouseXInt >= context.menu.width / 2)
                {
                    this.shiftX -= context.menu.width - borderPadding * 2;
                    this.warpedLeft = false;
                }
                else
                {
                    stop = true;
                }
            }

            if (!stop && !this.warpedRight && !this.warpedLeft)
            {
                if (mouseXInt <= border)
                {
                    Window.moveCursor(ww - (int) (factor * borderPadding), (int) mc.mouse.getY());
                    this.warpedLeft = true;
                    stop = true;
                }
                else if (mouseXInt >= context.menu.width - border)
                {
                    Window.moveCursor((int) (factor * borderPadding), (int) mc.mouse.getY());
                    this.warpedRight = true;
                    stop = true;
                }
            }

            if (!stop)
            {
                if (this.isFocused())
                {
                    context.unfocus();
                }

                double dx = (this.shiftX + mouseX) - this.initialX;

                if (Math.abs(dx) > 0D)
                {
                    double value = this.getValueModifier() * globalFactor.getValue();

                    double diff = (Math.abs(dx) - 3D) * value;
                    double newValue = this.lastValue + (dx < 0D ? -diff : diff);

                    newValue = diff < 0D ? this.lastValue : newValue;

                    if (this.value != newValue)
                    {
                        if (this.delayedInput)
                        {
                            this.setValue(newValue);
                        }
                        else
                        {
                            this.setValueAndNotify(newValue);
                        }
                    }
                }
            }

            /* Draw active element */
            context.batcher.outlineCenter((int) this.initialX, this.initialY, 4, Colors.WHITE);
        }

        if (!this.isEnabled())
        {
            /* Soft dim without lock icon — number fields read better greyed-out. */
            context.batcher.box(x, y, x + w, y + h, 0x99000000);
        }

        super.render(context);
    }

    /* Draws a small 5px-tall chevron from stacked 2px box rows. pointLeft =
       true renders "<", false renders ">". */
    private static void drawChevron(UIContext context, int cx, int cy, boolean pointLeft, int color)
    {
        for (int i = -2; i <= 2; i++)
        {
            int depth = Math.abs(i);
            int bx = pointLeft ? (cx - 1 + depth) : (cx - 1 - depth);

            context.batcher.box(bx, cy + i, bx + 2, cy + i + 1, color);
        }
    }

    private String formatToFit(FontRenderer font, double value, int maxWidth)
    {
        String raw = format(value);

        if (font.getWidth(raw) <= maxWidth)
        {
            return raw;
        }

        if (this.integer)
        {
            return raw;
        }

        String raw2 = FORMAT_2.format(value).replace(',', '.');

        if (font.getWidth(raw2) <= maxWidth)
        {
            return raw2;
        }

        String raw1 = FORMAT_1.format(value).replace(',', '.');

        if (font.getWidth(raw1) <= maxWidth)
        {
            return raw1;
        }

        String raw0 = FORMAT_0.format(value).replace(',', '.');

        if (raw0.isEmpty())
        {
            raw0 = "0";
        }

        if (font.getWidth(raw0) <= maxWidth)
        {
            return raw0;
        }

        String compact = this.formatCompact(value);

        if (!compact.isEmpty() && font.getWidth(compact) <= maxWidth)
        {
            return compact;
        }

        String exp2 = String.format(Locale.ENGLISH, "%.2e", value);

        if (font.getWidth(exp2) <= maxWidth)
        {
            return exp2;
        }

        String exp1 = String.format(Locale.ENGLISH, "%.1e", value);

        if (font.getWidth(exp1) <= maxWidth)
        {
            return exp1;
        }

        return String.format(Locale.ENGLISH, "%.0e", value);
    }

    private String truncateToWidth(FontRenderer font, String text, int maxWidth)
    {
        if (text == null || text.isEmpty())
        {
            return "";
        }

        if (maxWidth <= 0)
        {
            return text.substring(0, 1);
        }

        if (font.getWidth(text) <= maxWidth)
        {
            return text;
        }

        int end = text.length();

        while (end > 1 && font.getWidth(text.substring(0, end)) > maxWidth)
        {
            end--;
        }

        return text.substring(0, end);
    }

    private String formatCompact(double value)
    {
        double abs = Math.abs(value);

        if (abs < 1000D)
        {
            return "";
        }

        String suffix;
        double scaled;

        if (abs >= 1_000_000_000D)
        {
            suffix = "B";
            scaled = value / 1_000_000_000D;
        }
        else if (abs >= 1_000_000D)
        {
            suffix = "M";
            scaled = value / 1_000_000D;
        }
        else
        {
            suffix = "k";
            scaled = value / 1000D;
        }

        String f2 = String.format(Locale.ENGLISH, "%.2f", scaled) + suffix;
        String f1 = String.format(Locale.ENGLISH, "%.1f", scaled) + suffix;
        String f0 = String.format(Locale.ENGLISH, "%.0f", scaled) + suffix;

        return f2.length() <= f1.length() ? (f2.length() <= f0.length() ? f2 : f0) : (f1.length() <= f0.length() ? f1 : f0);
    }

    public double getValueModifier()
    {
        double value = this.normal;

        if (Window.isShiftPressed())
        {
            value = this.strong;
        }
        else if (Window.isCtrlPressed())
        {
            value = this.increment;
        }
        else if (Window.isAltPressed())
        {
            value = this.weak;
        }

        return value;
    }

    private double getArrowStep()
    {
        double step = this.increment;

        if (Window.isShiftPressed())
        {
            step = this.increment * 10D;
        }
        else if (Window.isAltPressed())
        {
            step = this.increment / 10D;
        }

        if (this.integer)
        {
            step = Math.max(1D, Math.round(step));
        }

        return step;
    }

    /**
     * Wheel / keyboard step. Integer settings fields truncate toward zero when a
     * fractional modifier is applied, so enforce at least one unit there only.
     */
    private double getScrollStep()
    {
        double value = this.getValueModifier();

        if (this.integer)
        {
            value = Math.max(1D, Math.round(value));
        }

        return value;
    }
}
