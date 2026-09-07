package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class ContextCategoryAction extends ContextAction
{
    public ContextCategoryAction(IKey label)
    {
        super(Icons.NONE, label, null);
    }

    @Override
    public int getWidth(FontRenderer font)
    {
        return 12 + font.getWidth(this.label.get());
    }

    @Override
    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        int primary = BBSSettings.primaryColor.get();
        // A premium left-border indicator for the category header
        context.batcher.box(x, y + 2, x + 3, y + h - 2, primary);
        // A subtle divider line next to the text
        int textWidth = font.getWidth(this.label.get());
        int lineStartX = x + 8 + textWidth + 6;
        if (lineStartX < x + w - 5)
        {
            context.batcher.box(lineStartX, y + h / 2, x + w - 5, y + h / 2 + 1, 0x22ffffff);
        }
        // Stylized category text
        context.batcher.textShadow(this.label.get(), x + 8, y + (h - font.getHeight()) / 2, 0xffbbbbbb);
    }
}
