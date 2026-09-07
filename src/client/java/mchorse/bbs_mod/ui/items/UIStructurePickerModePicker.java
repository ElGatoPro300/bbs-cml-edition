package mchorse.bbs_mod.ui.items;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.StructurePickerClient;
import mchorse.bbs_mod.items.StructurePickerMode;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Vertical mode list: soft colored glow on the left, icon, label.
 * Selected row only darkens its background.
 */
public class UIStructurePickerModePicker extends UIElement
{
    private static final int ROW_H = 28;
    private static final int STRIPE_W = 4;
    private static final int GLOW_W = 20;
    private static final int ICON = 16;
    private static final int PAD = 8;

    private static final int COLOR_ROW = 0x00000000;
    private static final int COLOR_ROW_HOVER = 0x33000000;
    private static final int COLOR_ROW_SELECTED = 0x66000000;

    /* Same / Brush sit under Block for paint-style selection. */
    private static final StructurePickerMode[] MODE_ORDER = new StructurePickerMode[]
    {
        StructurePickerMode.BLOCK,
        StructurePickerMode.SAME,
        StructurePickerMode.BRUSH,
        StructurePickerMode.RECTANGLE,
        StructurePickerMode.CUBE,
        StructurePickerMode.CIRCLE,
        StructurePickerMode.SPHERE,
        StructurePickerMode.TRIANGLE,
        StructurePickerMode.CONE,
        StructurePickerMode.CYLINDER
    };

    private static final Icon[] MODE_ICONS = new Icon[]
    {
        Icons.SP_BLOCK,
        Icons.BUCKET,
        Icons.BRUSH,
        Icons.SP_RECTANGLE,
        Icons.SP_CUBE,
        Icons.SP_CIRCLE,
        Icons.SP_SPHERE,
        Icons.SP_TRIANGLE,
        Icons.SP_CONE,
        Icons.SP_CYLINDER
    };

    /* Scrambled distinct accents — not hue-ordered (avoids rainbow / pride-flag look). */
    private static final int[] STRIPE_COLORS = new int[]
    {
        0xFF14B8A6, /* teal - Block */
        0xFF6366F1, /* indigo - Same */
        0xFFEC4899, /* pink - Brush */
        0xFFF97316, /* orange - Rectangle */
        0xFFA78BFA, /* light purple - Cube */
        0xFF84CC16, /* lime - Circle */
        0xFFE11D48, /* crimson - Sphere */
        0xFF0EA5E9, /* sky - Triangle */
        0xFFEAB308, /* gold - Cone */
        0xFFC026D3  /* fuchsia - Cylinder */
    };

    public UIStructurePickerModePicker()
    {
        super();

        this.wh(120, MODE_ORDER.length * ROW_H + PAD * 2);
        this.mouseEventPropagataion(EventPropagation.BLOCK);
    }

    public int preferredHeight()
    {
        return MODE_ORDER.length * ROW_H + PAD * 2;
    }

    private static StructurePickerMode modeAt(int index)
    {
        return MODE_ORDER[MathUtils.clamp(index, 0, MODE_ORDER.length - 1)];
    }

    private static Icon iconAt(int index)
    {
        return MODE_ICONS[MathUtils.clamp(index, 0, MODE_ICONS.length - 1)];
    }

    private static IKey labelAt(int index)
    {
        StructurePickerMode mode = modeAt(index);

        return UIKeys.STRUCTURE_PICKER_MODE_LABELS[MathUtils.clamp(mode.index, 0, UIKeys.STRUCTURE_PICKER_MODE_LABELS.length - 1)];
    }

    private static int stripeAt(int index)
    {
        return STRIPE_COLORS[MathUtils.clamp(index, 0, STRIPE_COLORS.length - 1)];
    }

    private int hitTest(int mouseY)
    {
        int local = mouseY - this.area.y - PAD;

        if (local < 0 || local >= MODE_ORDER.length * ROW_H)
        {
            return -1;
        }

        return MathUtils.clamp(local / ROW_H, 0, MODE_ORDER.length - 1);
    }

    private void selectIndex(int index)
    {
        StructurePickerClient.setMode(modeAt(index));
    }

    private int selectedRow()
    {
        StructurePickerMode current = StructurePickerClient.getMode();

        for (int i = 0; i < MODE_ORDER.length; i++)
        {
            if (MODE_ORDER[i] == current)
            {
                return i;
            }
        }

        return 0;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.area.isInside(context))
        {
            int index = this.hitTest(context.mouseY);

            if (index >= 0)
            {
                this.selectIndex(index);

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        int selected = this.selectedRow();
        int hovered = this.area.isInside(context) ? this.hitTest(context.mouseY) : -1;

        for (int i = 0; i < MODE_ORDER.length; i++)
        {
            int y = this.area.y + PAD + i * ROW_H;
            int x = this.area.x;
            int ex = this.area.ex();
            int ey = y + ROW_H;
            boolean isSelected = i == selected;
            boolean isHovered = i == hovered;
            int bg = isSelected ? COLOR_ROW_SELECTED : (isHovered ? COLOR_ROW_HOVER : COLOR_ROW);

            if (Colors.getA(bg) > 0F)
            {
                context.batcher.box(x, y, ex, ey, bg);
            }

            /* Solid accent bar, then soft glow fading toward the icon. */
            int accent = stripeAt(i);

            context.batcher.box(x, y, x + STRIPE_W, ey, accent);

            int glowLeft = Colors.setA(accent, 0.55F);
            int glowMid = Colors.setA(accent, 0.22F);
            int glowRight = Colors.setA(accent, 0F);
            float glowX = x + STRIPE_W;

            context.batcher.gradientHBox(glowX, y, glowX + GLOW_W * 0.4F, ey, glowLeft, glowMid);
            context.batcher.gradientHBox(glowX + GLOW_W * 0.4F, y, glowX + GLOW_W, ey, glowMid, glowRight);

            Icon icon = iconAt(i);
            float iconX = x + STRIPE_W + 6;
            float iconY = y + (ROW_H - ICON) * 0.5F;

            context.batcher.texturedBox(
                BBSModClient.getTextures().getTexture(icon.texture),
                Colors.WHITE,
                iconX,
                iconY,
                ICON,
                ICON,
                icon.x,
                icon.y,
                icon.x + icon.w,
                icon.y + icon.h,
                icon.textureW,
                icon.textureH
            );

            String text = labelAt(i).get();
            int textX = (int) (iconX + ICON + 6);
            int textY = y + (ROW_H - context.batcher.getFont().getHeight()) / 2;

            context.batcher.textShadow(text, textX, textY, Colors.WHITE);
        }

        super.render(context);
    }
}
