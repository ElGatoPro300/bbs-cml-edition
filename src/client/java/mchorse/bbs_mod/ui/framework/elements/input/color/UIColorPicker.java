package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Color picker: SV square, horizontal hue bar, RGB / HSV / Hex modes,
 * plus favorite + recent palettes.
 */
public class UIColorPicker extends UIElement
{
    public static final int COLOR_PICKER_SIZE = 140;
    public static final int COLOR_PICKER_TOP = 5;
    public static final int COLOR_PICKER_GAP = 4;
    public static final int COLOR_PICKER_BAR_HEIGHT = 12;
    public static final int COLOR_PICKER_BAR_WIDTH = 14;
    public static final int MODE_ROW_HEIGHT = 18;
    public static final int LABEL_ROW_HEIGHT = 14;
    public static final int FIELD_ROW_HEIGHT = 26;
    public static final int CHANNEL_BLOCK_HEIGHT = LABEL_ROW_HEIGHT + 3 + FIELD_ROW_HEIGHT;
    /** Panel fill — UI control-bar grey, not pure black. */
    public static final int PANEL_BACKGROUND = Colors.CONTROL_BAR;

    public enum ColorMode
    {
        RGB, HSV, HEX
    }

    public static ValueColors recentColors = new ValueColors("recent");

    public Color color = new Color();
    public Consumer<Integer> callback;

    public UITextbox input;
    public UIColorPalette recent;
    public UIColorPalette favorite;

    public UIButton modeRgb;
    public UIButton modeHsv;
    public UIButton modeHex;
    public UIElement modeRow;
    public UIElement fieldRow;
    public UIElement labelRow;
    public UIElement valueRow;
    public UILabel labelA;
    public UILabel labelB;
    public UILabel labelC;
    public UILabel labelAlpha;
    public UITrackpad fieldA;
    public UITrackpad fieldB;
    public UITrackpad fieldC;
    public UITrackpad fieldAlpha;

    public boolean editAlpha;
    public ColorMode mode = ColorMode.RGB;

    public Area red = new Area();
    public Area green = new Area();
    public Area blue = new Area();
    public Area alpha = new Area();

    protected int dragging = -1;
    protected Color hsv = new Color();
    protected boolean fillingFields;

    public static void renderAlphaPreviewQuad(Batcher2D batcher, int x1, int y1, int x2, int y2, Color color)
    {
        Matrix4f matrix4f = batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();

        builder.vertex(matrix4f, x1, y1, 0F).color(color.r, color.g, color.b, 1);
        builder.vertex(matrix4f, x1, y2, 0F).color(color.r, color.g, color.b, 1);
        builder.vertex(matrix4f, x2, y1, 0F).color(color.r, color.g, color.b, 1);
        builder.vertex(matrix4f, x2, y1, 0F).color(color.r, color.g, color.b, color.a);
        builder.vertex(matrix4f, x1, y2, 0F).color(color.r, color.g, color.b, color.a);
        builder.vertex(matrix4f, x2, y2, 0F).color(color.r, color.g, color.b, color.a);

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public UIColorPicker(Consumer<Integer> callback)
    {
        super();

        this.callback = callback;

        this.input = new UITextbox(7, (string) ->
        {
            if (this.fillingFields)
            {
                return;
            }

            this.setValue(Colors.parse(string));
            this.callback();
            /* Refresh RGB/HSV pads only — rewriting the hex box while focused blocks typing. */
            this.refreshChannelFieldsFromColor(false);
        });
        this.input.border().h(FIELD_ROW_HEIGHT);
        this.input.context((menu) -> menu.action(Icons.FAVORITE, UIKeys.COLOR_CONTEXT_FAVORITES_ADD, () -> this.addToFavorites(this.color)));

        this.modeRgb = new UIButton(UIKeys.COLOR_MODE_RGB, (b) -> this.setMode(ColorMode.RGB));
        this.modeHsv = new UIButton(UIKeys.COLOR_MODE_HSV, (b) -> this.setMode(ColorMode.HSV));
        this.modeHex = new UIButton(UIKeys.COLOR_MODE_HEX, (b) -> this.setMode(ColorMode.HEX));
        this.modeRgb.background(true).h(MODE_ROW_HEIGHT);
        this.modeHsv.background(true).h(MODE_ROW_HEIGHT);
        this.modeHex.background(true).h(MODE_ROW_HEIGHT);
        this.modeRow = UI.row(2, this.modeRgb, this.modeHsv, this.modeHex);

        this.labelA = UI.label(IKey.constant("R"), LABEL_ROW_HEIGHT).labelAnchor(0.5F, 0.5F);
        this.labelB = UI.label(IKey.constant("G"), LABEL_ROW_HEIGHT).labelAnchor(0.5F, 0.5F);
        this.labelC = UI.label(IKey.constant("B"), LABEL_ROW_HEIGHT).labelAnchor(0.5F, 0.5F);
        this.labelAlpha = UI.label(IKey.constant("A"), LABEL_ROW_HEIGHT).labelAnchor(0.5F, 0.5F);
        this.fieldA = new UITrackpad((v) -> this.applyChannelField(0, v.floatValue()));
        this.fieldB = new UITrackpad((v) -> this.applyChannelField(1, v.floatValue()));
        this.fieldC = new UITrackpad((v) -> this.applyChannelField(2, v.floatValue()));
        this.fieldA.integer().limit(0, 255).plainFormat().h(FIELD_ROW_HEIGHT);
        this.fieldB.integer().limit(0, 255).plainFormat().h(FIELD_ROW_HEIGHT);
        this.fieldC.integer().limit(0, 255).plainFormat().h(FIELD_ROW_HEIGHT);
        this.fieldAlpha = new UITrackpad((v) -> this.applyAlphaField(v.floatValue()));
        this.fieldAlpha.integer().limit(0, 255).plainFormat().h(FIELD_ROW_HEIGHT);

        /* Three equal columns fill the panel edge-to-edge; alpha is added only in editAlpha(). */
        this.labelRow = UI.row(4, this.labelA, this.labelB, this.labelC);
        this.valueRow = UI.row(4, this.fieldA, this.fieldB, this.fieldC);
        this.labelRow.h(LABEL_ROW_HEIGHT);
        this.valueRow.h(FIELD_ROW_HEIGHT);
        this.fieldRow = UI.column(3, this.labelRow, this.valueRow);
        this.fieldRow.h(CHANNEL_BLOCK_HEIGHT);

        this.recent = new UIColorPalette((color) ->
        {
            this.setColor(color.getARGBColor());
            this.updateColor();
        }).colors(recentColors.getCurrentColors());

        this.recent.context((menu) ->
        {
            int index = this.recent.getIndex(this.getContext());

            if (this.recent.hasColor(index))
            {
                menu.action(Icons.FAVORITE, UIKeys.COLOR_CONTEXT_FAVORITES_ADD, () -> this.addToFavorites(this.recent.colors.get(index)));
            }
        });

        this.favorite = new UIColorPalette((color) ->
        {
            this.setColor(color.getARGBColor());
            this.updateColor();
        }).colors(BBSSettings.favoriteColors.getCurrentColors());

        this.favorite.context((menu) ->
        {
            int index = this.favorite.getIndex(this.getContext());

            if (this.favorite.hasColor(index))
            {
                menu.action(Icons.REMOVE, UIKeys.COLOR_CONTEXT_FAVORITES_REMOVE, () -> this.removeFromFavorites(index));
            }
        });

        this.modeRow.relative(this).x(5).w(1F, -10).h(MODE_ROW_HEIGHT);
        this.fieldRow.relative(this).x(5).w(1F, -10).h(CHANNEL_BLOCK_HEIGHT);
        this.input.relative(this).x(5).w(1F, -10).h(FIELD_ROW_HEIGHT);
        this.favorite.relative(this).xy(5, 0).w(1F, -10);
        this.recent.relative(this.favorite).w(1F);

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE).add(
            this.modeRow, this.fieldRow, this.input, this.favorite, this.recent
        );

        this.setMode(ColorMode.RGB);
        this.refreshFieldsFromColor();
    }

    public UIColorPicker editAlpha()
    {
        this.editAlpha = true;
        this.input.textbox.setLength(9);

        if (!this.labelAlpha.hasParent())
        {
            this.labelRow.add(this.labelAlpha);
            this.valueRow.add(this.fieldAlpha);
        }

        this.labelAlpha.setVisible(true);
        this.fieldAlpha.setVisible(true);
        this.refreshFieldsFromColor();
        this.setupSize();
        this.resize();

        return this;
    }

    public void setMode(ColorMode mode)
    {
        this.mode = mode == null ? ColorMode.RGB : mode;
        this.syncModeButtons();
        this.syncModeFieldsVisibility();
        this.refreshFieldsFromColor();
        this.setupSize();
        this.resize();
    }

    private void syncModeButtons()
    {
        int active = 0xFF4A90D9;
        int idle = Colors.GRAY;

        this.modeRgb.color(this.mode == ColorMode.RGB ? active : idle);
        this.modeHsv.color(this.mode == ColorMode.HSV ? active : idle);
        this.modeHex.color(this.mode == ColorMode.HEX ? active : idle);
        this.modeRgb.textColor(this.mode == ColorMode.RGB ? Colors.WHITE : Colors.LIGHTEST_GRAY, false);
        this.modeHsv.textColor(this.mode == ColorMode.HSV ? Colors.WHITE : Colors.LIGHTEST_GRAY, false);
        this.modeHex.textColor(this.mode == ColorMode.HEX ? Colors.WHITE : Colors.LIGHTEST_GRAY, false);
    }

    private void syncModeFieldsVisibility()
    {
        boolean hex = this.mode == ColorMode.HEX;

        this.input.setVisible(hex);
        this.input.setEnabled(hex);
        this.fieldRow.setVisible(!hex);
        this.fieldRow.setEnabled(!hex);

        if (this.mode == ColorMode.RGB)
        {
            this.labelA.label = IKey.constant("R");
            this.labelB.label = IKey.constant("G");
            this.labelC.label = IKey.constant("B");
            this.labelA.color(0xFFFF5555, false);
            this.labelB.color(0xFF55FF55, false);
            this.labelC.color(0xFF5555FF, false);
        }
        else if (this.mode == ColorMode.HSV)
        {
            this.labelA.label = IKey.constant("H");
            this.labelB.label = IKey.constant("S");
            this.labelC.label = IKey.constant("V");
            this.labelA.color(Colors.WHITE, false);
            this.labelB.color(Colors.WHITE, false);
            this.labelC.color(Colors.WHITE, false);
        }
    }

    public void updateField()
    {
        if (!this.input.isFocused())
        {
            this.input.setText(this.color.stringify(this.editAlpha));
        }

        this.refreshChannelFieldsFromColor(false);
    }

    public void updateColor()
    {
        this.updateField();
        this.callback();
    }

    protected void callback()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.editAlpha ? this.color.getARGBColor() : this.color.getRGBColor());
        }
    }

    public void setColor(int color)
    {
        if (this.dragging >= 0)
        {
            return;
        }

        this.setValue(color);
        this.updateField();
    }

    public void setValue(int color)
    {
        this.color.set(color, this.editAlpha);

        float prevH = this.hsv.r;
        float prevS = this.hsv.g;

        Colors.RGBtoHSV(this.hsv, this.color.r, this.color.g, this.color.b);
        this.hsv.a = this.color.a;

        if (this.color.r == this.color.g && this.color.g == this.color.b)
        {
            this.hsv.r = prevH;

            if (this.color.r == 0F)
            {
                this.hsv.g = prevS;
            }
        }
    }

    private void refreshFieldsFromColor()
    {
        this.refreshChannelFieldsFromColor(true);
    }

    private void refreshChannelFieldsFromColor(boolean updateHexText)
    {
        this.fillingFields = true;

        try
        {
            if (this.mode == ColorMode.RGB)
            {
                this.fieldA.setValue(Math.round(MathUtils.clamp(this.color.r, 0F, 1F) * 255F));
                this.fieldB.setValue(Math.round(MathUtils.clamp(this.color.g, 0F, 1F) * 255F));
                this.fieldC.setValue(Math.round(MathUtils.clamp(this.color.b, 0F, 1F) * 255F));
            }
            else if (this.mode == ColorMode.HSV)
            {
                this.fieldA.setValue(Math.round(MathUtils.clamp(this.hsv.r, 0F, 1F) * 255F));
                this.fieldB.setValue(Math.round(MathUtils.clamp(this.hsv.g, 0F, 1F) * 255F));
                this.fieldC.setValue(Math.round(MathUtils.clamp(this.hsv.b, 0F, 1F) * 255F));
            }

            if (this.editAlpha)
            {
                this.fieldAlpha.setValue(Math.round(MathUtils.clamp(this.color.a, 0F, 1F) * 255F));
            }

            if (updateHexText && !this.input.isFocused())
            {
                this.input.setText(this.color.stringify(this.editAlpha));
            }
        }
        finally
        {
            this.fillingFields = false;
        }
    }

    private void applyChannelField(int channel, float value)
    {
        if (this.fillingFields)
        {
            return;
        }

        float t = MathUtils.clamp(value, 0F, 255F) / 255F;

        if (this.mode == ColorMode.RGB)
        {
            if (channel == 0)
            {
                this.color.r = t;
            }
            else if (channel == 1)
            {
                this.color.g = t;
            }
            else
            {
                this.color.b = t;
            }

            float prevH = this.hsv.r;
            float prevS = this.hsv.g;

            Colors.RGBtoHSV(this.hsv, this.color.r, this.color.g, this.color.b);
            this.hsv.a = this.color.a;

            if (this.color.r == this.color.g && this.color.g == this.color.b)
            {
                this.hsv.r = prevH;

                if (this.color.r == 0F)
                {
                    this.hsv.g = prevS;
                }
            }
        }
        else if (this.mode == ColorMode.HSV)
        {
            if (channel == 0)
            {
                this.hsv.r = t;
            }
            else if (channel == 1)
            {
                this.hsv.g = t;
            }
            else
            {
                this.hsv.b = t;
            }

            Colors.HSVtoRGB(this.color, this.hsv.r, this.hsv.g, this.hsv.b);
            this.color.a = this.hsv.a;
        }

        this.updateColor();
    }

    private void applyAlphaField(float value)
    {
        if (this.fillingFields || !this.editAlpha)
        {
            return;
        }

        this.color.a = MathUtils.clamp(value, 0F, 255F) / 255F;
        this.hsv.a = this.color.a;
        this.updateColor();
    }

    public void setup(int x, int y)
    {
        this.xy(x, y);
        this.setupSize();
    }

    protected void setupSize()
    {
        int width = 10 + COLOR_PICKER_SIZE;
        int recent = this.recent.isVisible() && !this.recent.colors.isEmpty() ? this.recent.getHeight(width - 10) : 0;
        int favorite = this.favorite.isVisible() && !this.favorite.colors.isEmpty() ? this.favorite.getHeight(width - 10) : 0;
        int fieldsBlock = this.mode == ColorMode.HEX ? FIELD_ROW_HEIGHT : CHANNEL_BLOCK_HEIGHT;
        int pickerBlock = COLOR_PICKER_TOP + COLOR_PICKER_SIZE + COLOR_PICKER_GAP
            + COLOR_PICKER_BAR_HEIGHT + COLOR_PICKER_GAP
            + (this.editAlpha ? COLOR_PICKER_BAR_HEIGHT + COLOR_PICKER_GAP : 0)
            + MODE_ROW_HEIGHT + COLOR_PICKER_GAP
            + fieldsBlock + 10;
        int base = pickerBlock;

        this.w(width);
        base += favorite > 0 ? favorite + 15 : 0;
        base += recent > 0 ? recent + 15 : 0;

        this.h(base);
        this.favorite.h(favorite);
        this.recent.h(recent);

        int modeY = COLOR_PICKER_TOP + COLOR_PICKER_SIZE + COLOR_PICKER_GAP + COLOR_PICKER_BAR_HEIGHT + COLOR_PICKER_GAP
            + (this.editAlpha ? COLOR_PICKER_BAR_HEIGHT + COLOR_PICKER_GAP : 0);
        int fieldsY = modeY + MODE_ROW_HEIGHT + COLOR_PICKER_GAP;

        this.modeRow.y(modeY);
        this.fieldRow.y(fieldsY);
        this.input.y(fieldsY);
        this.favorite.y(pickerBlock);

        if (favorite > 0)
        {
            this.recent.y(1F, 15);
        }
        else
        {
            this.recent.y(0);
        }
    }

    /* Managing recent and favorite colors */

    private void addToRecent()
    {
        recentColors.addColor(this.color);
    }

    private void addToFavorites(Color color)
    {
        BBSSettings.favoriteColors.addColor(color);

        this.setupSize();
        this.resize();
    }

    private void removeFromFavorites(int index)
    {
        BBSSettings.favoriteColors.remove(index);

        this.setupSize();
        this.resize();
    }

    @Override
    public void resize()
    {
        super.resize();

        int x = this.area.x + 5;
        int y = this.area.y + COLOR_PICKER_TOP;
        int width = this.area.w - 10;
        int square = Math.max(40, Math.min(COLOR_PICKER_SIZE, width));

        this.red.set(x, y, square, square);
        this.green.set(x, this.red.ey() + COLOR_PICKER_GAP, square, COLOR_PICKER_BAR_HEIGHT);

        if (this.editAlpha)
        {
            this.alpha.set(x, this.green.ey() + COLOR_PICKER_GAP, square, COLOR_PICKER_BAR_HEIGHT);
        }
        else
        {
            this.alpha.set(0, 0, 0, 0);
        }

        this.blue.set(0, 0, 0, 0);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.red.isInside(context))
        {
            this.dragging = 1;

            return true;
        }
        else if (this.green.isInside(context))
        {
            this.dragging = 2;

            return true;
        }
        else if (this.alpha.isInside(context) && this.editAlpha)
        {
            this.dragging = 4;

            return true;
        }

        if (!this.area.isInside(context))
        {
            this.removeFromParent();
            this.addToRecent();
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.dragging = -1;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.removeFromParent();
            this.addToRecent();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.dragging >= 0)
        {
            if (this.dragging == 1)
            {
                float saturation = MathUtils.clamp((context.mouseX - this.red.x) / (float) this.red.w, 0F, 1F);
                float value = 1F - MathUtils.clamp((context.mouseY - this.red.y) / (float) this.red.h, 0F, 1F);

                this.hsv.g = saturation;
                this.hsv.b = value;
            }
            else if (this.dragging == 2)
            {
                float hue = MathUtils.clamp((context.mouseX - this.green.x) / (float) this.green.w, 0F, 1F);

                this.hsv.r = hue;
            }
            else if (this.dragging == 4 && this.editAlpha)
            {
                float alpha = MathUtils.clamp((context.mouseX - this.alpha.x) / (float) this.alpha.w, 0F, 1F);

                this.hsv.a = alpha;
            }

            Colors.HSVtoRGB(this.color, this.hsv.r, this.hsv.g, this.hsv.b);
            this.color.a = this.hsv.a;
            this.updateColor();
        }

        this.area.render(context.batcher, PANEL_BACKGROUND);

        Color temp = new Color();

        Colors.HSVtoRGB(temp, this.hsv.r, 1F, 1F);
        context.batcher.box(this.red.x, this.red.y, this.red.ex(), this.red.ey(), temp.getARGBColor());
        context.batcher.gradientHBox(this.red.x, this.red.y, this.red.ex(), this.red.ey(), Colors.WHITE, Colors.setA(Colors.WHITE, 0F));
        context.batcher.gradientVBox(this.red.x, this.red.y, this.red.ex(), this.red.ey(), 0, 0xff000000);

        for (int i = 0; i < 6; i++)
        {
            Colors.HSVtoRGB(temp, i / 6F, 1F, 1F);
            int left = temp.getARGBColor();

            Colors.HSVtoRGB(temp, (i + 1) / 6F, 1F, 1F);
            int right = temp.getARGBColor();
            int x1 = this.green.x + (int) (this.green.w * (i / 6F));
            int x2 = this.green.x + (int) (this.green.w * ((i + 1) / 6F));

            context.batcher.gradientHBox(x1, this.green.y, x2, this.green.ey(), left, right);
        }

        if (this.editAlpha)
        {
            context.batcher.iconArea(Icons.CHECKBOARD, this.alpha.x, this.alpha.y, this.alpha.w, this.alpha.h);
            Colors.HSVtoRGB(temp, this.hsv.r, this.hsv.g, this.hsv.b);
            temp.a = 0F;
            int left = temp.getARGBColor();
            temp.a = 1F;
            int right = temp.getARGBColor();

            context.batcher.gradientHBox(this.alpha.x, this.alpha.y, this.alpha.ex(), this.alpha.ey(), left, right);
        }

        context.batcher.outline(this.red.x, this.red.y, this.red.ex(), this.red.ey(), 0x44000000);
        context.batcher.outline(this.green.x, this.green.y, this.green.ex(), this.green.ey(), 0x44000000);

        if (this.editAlpha)
        {
            context.batcher.outline(this.alpha.x, this.alpha.y, this.alpha.ex(), this.alpha.ey(), 0x44000000);
        }

        this.renderMarker(context.batcher, this.red.x + (int) (this.red.w * this.hsv.g), this.red.y + (int) (this.red.h * (1F - this.hsv.b)));
        this.renderMarker(context.batcher, this.green.x + (int) (this.green.w * this.hsv.r), this.green.my());

        if (this.editAlpha)
        {
            this.renderMarker(context.batcher, this.alpha.x + (int) (this.alpha.w * this.hsv.a), this.alpha.my());
        }

        if (this.favorite.isVisible() && !this.favorite.colors.isEmpty())
        {
            context.batcher.text(UIKeys.COLOR_FAVORITE.get(), this.favorite.area.x, this.favorite.area.y - 10, Colors.GRAY);
        }

        if (this.recent.isVisible() && !this.recent.colors.isEmpty())
        {
            context.batcher.text(UIKeys.COLOR_RECENT.get(), this.recent.area.x, this.recent.area.y - 10, Colors.GRAY);
        }

        super.render(context);
    }

    public void renderRect(Batcher2D batcher, int x1, int y1, int x2, int y2)
    {
        if (this.editAlpha)
        {
            batcher.iconArea(Icons.CHECKBOARD, x1, y1, x2 - x1, y2 - y1);
            renderAlphaPreviewQuad(batcher, x1, y1, x2, y2, this.color);
        }
        else
        {
            batcher.box(x1, y1, x2, y2, this.color.getARGBColor());
        }
    }

    private void renderMarker(Batcher2D batcher, int x, int y)
    {
        batcher.box(x - 4, y - 4, x + 4, y + 4, Colors.A100);
        batcher.box(x - 3, y - 3, x + 3, y + 3, Colors.WHITE);
        batcher.box(x - 2, y - 2, x + 2, y + 2, Colors.LIGHTEST_GRAY);
    }
}
