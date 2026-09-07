package mchorse.bbs_mod.ui.framework.notifications;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UINotifications
{
    public List<Notification> notifications = new ArrayList<>();

    public void post(IKey message, int background)
    {
        this.post(message, background, Colors.WHITE);
    }

    public void post(IKey message, int background, int color)
    {
        this.notifications.add(new Notification(message, background, color));
    }

    public void update()
    {
        Iterator<Notification> it = this.notifications.iterator();

        while (it.hasNext())
        {
            Notification notification = it.next();

            notification.update();

            if (notification.isExpired())
            {
                it.remove();
            }
        }
    }

    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        int w = 300;
        int y = 10;
        int padding = 8;
        int lineMargin = 5;
        int outlineMargin = 2;
        int lineHeight = font.getHeight() + lineMargin;
        int color = BBSSettings.primaryColor.get();

        for (int i = this.notifications.size() - 1; i >= 0; i--)
        {
            Notification notification = this.notifications.get(i);
            List<String> splits = font.wrap(notification.message.get(), w - padding * 2);
            int ly = padding;
            int h = padding * 2 + splits.size() * lineHeight - lineMargin;
            int x = (int) Lerps.lerp(context.menu.width, context.menu.width / 2 - w / 2, notification.getFactor(context.getTransition()));

            context.batcher.dropShadow(x + 3, y + 3, x + w - 3, y + h - 3, 10, Colors.A25 | color, color);
            context.batcher.box(x + 1, y, x + w - 1, y + h, Colors.mulRGB(color | Colors.A100, 0.1F));
            context.batcher.box(x, y + 1, x + w, y + h - 1, Colors.mulRGB(color | Colors.A100, 0.1F));
            context.batcher.outline(x + outlineMargin, y + outlineMargin, x + w - outlineMargin, y + h - outlineMargin, notification.background);

            for (String line : splits)
            {
                context.batcher.textShadow(line, x + padding, y + ly, notification.color);

                ly += lineHeight;
            }

            y += h + 10;
        }
    }
}