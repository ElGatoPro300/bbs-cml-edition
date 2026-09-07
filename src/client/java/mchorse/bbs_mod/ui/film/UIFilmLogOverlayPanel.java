package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmContributor;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class UIFilmLogOverlayPanel extends UIOverlayPanel
{
    private final UIFilmPanel filmPanel;

    public UIFilmLogOverlayPanel(UIFilmPanel filmPanel)
    {
        super(UIKeys.FILM_LOG_TITLE);

        this.filmPanel = filmPanel;
        this.resizable();
        this.minSize(280, 240);

        UIScrollView scroll = new UIScrollView();
        scroll.relative(this.content).w(1F).h(1F);
        scroll.column(4).vertical().stretch().scroll().padding(8);

        Film film = filmPanel.getData();

        if (film != null)
        {
            /* Film General Stats Header */
            UIElement statsHeader = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    int x = this.area.x + 6;
                    int y = this.area.y + 4;

                    context.batcher.textShadow(film.getId(), x, y, Colors.WHITE);
                    y += 14;

                    String timeStr = UIFilmLogOverlayPanel.formatTime(film.totalTimeWorked.get());
                    String label = UIKeys.FILM_TOTAL_TIME.format(timeStr).get();
                    context.batcher.textShadow(label, x, y, 0xAAFFFFFF);

                    super.render(context);
                }
            };
            statsHeader.h(36);

            scroll.add(statsHeader);

            /* Title for Contributors section */
            UIElement contributorsTitle = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    int x = this.area.x + 6;
                    int y = this.area.y + 4;

                    context.batcher.textShadow(UIKeys.FILM_CONTRIBUTORS.get().toUpperCase(), x, y, 0x88FFFFFF);
                    super.render(context);
                }
            };
            contributorsTitle.h(16);

            scroll.add(contributorsTitle);

            /* List contributors */
            if (film.contributors.getList().isEmpty())
            {
                UIElement emptyLabel = new UIElement()
                {
                    @Override
                    public void render(UIContext context)
                    {
                        context.batcher.textShadow(UIKeys.FILM_NO_CONTRIBUTORS.get(), this.area.x + 10, this.area.y + 4, Colors.GRAY);
                        super.render(context);
                    }
                };
                emptyLabel.h(20);
                scroll.add(emptyLabel);
            }
            else
            {
                for (FilmContributor contributor : film.contributors.getList())
                {
                    scroll.add(new UIContributorEntry(contributor));
                }
            }
        }

        this.content.add(scroll);
        this.markContainer();
    }

    public static String formatTime(int ticks)
    {
        int seconds = ticks / 20;
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainingSeconds = seconds % 60;

        if (hours > 0)
        {
            return String.format("%dh %dm %ds", hours, minutes, remainingSeconds);
        }
        else if (minutes > 0)
        {
            return String.format("%dm %ds", minutes, remainingSeconds);
        }
        else
        {
            return String.format("%ds", remainingSeconds);
        }
    }

    /* Entry UI Element for rendering a single contributor with player head skin */
    public static class UIContributorEntry extends UIElement
    {
        private final FilmContributor contributor;
        private Identifier skinTexture;
        private boolean profileResolved;

        public UIContributorEntry(FilmContributor contributor)
        {
            super();

            this.contributor = contributor;
            this.h(28);
        }

        private void resolveProfile()
        {
            if (this.profileResolved)
            {
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();

            try
            {
                GameProfile profile = null;

                if (mc.getNetworkHandler() != null)
                {
                    for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList())
                    {
                        if (entry.getProfile().getName().equalsIgnoreCase(this.contributor.name.get()))
                        {
                            profile = entry.getProfile();
                            break;
                        }
                    }
                }

                if (profile == null)
                {
                    UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.contributor.name.get()).getBytes(StandardCharsets.UTF_8));
                    profile = new GameProfile(uuid, this.contributor.name.get());
                }

                this.skinTexture = mc.getSkinProvider().getSkinTextures(profile).texture();
            }
            catch (Exception e)
            {}

            this.profileResolved = true;
        }

        private void drawPlayerHead(DrawContext drawContext, Identifier texture, int x, int y, int size)
        {
            drawContext.drawTexture(texture, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
            drawContext.drawTexture(texture, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
        }

        @Override
        public void render(UIContext context)
        {
            this.resolveProfile();

            /* Card background box */
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x1AFFFFFF);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x10FFFFFF);

            int headSize = 16;
            int headX = this.area.x + 6;
            int headY = this.area.y + (this.area.h - headSize) / 2;

            if (this.skinTexture != null)
            {
                this.drawPlayerHead(context.batcher.getContext(), this.skinTexture, headX, headY, headSize);
            }
            else
            {
                context.batcher.box(headX, headY, headX + headSize, headY + headSize, Colors.GRAY);
            }

            int textX = headX + headSize + 8;
            int textY = this.area.y + (this.area.h - context.batcher.getFont().getHeight()) / 2;

            context.batcher.textShadow(this.contributor.name.get(), textX, textY, Colors.WHITE);

            String timeStr = UIFilmLogOverlayPanel.formatTime(this.contributor.time.get());
            int timeW = context.batcher.getFont().getWidth(timeStr);
            context.batcher.textShadow(timeStr, this.area.ex() - timeW - 8, textY, 0xCCFFFFFF);

            super.render(context);
        }
    }
}