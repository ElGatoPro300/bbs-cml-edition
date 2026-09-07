package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.model.UIModelPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.utility.audio.UIAudioEditorPanel;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIPanelSwitcher extends UIElement
{
    private final UIDashboard dashboard;

    public UIPanelSwitcher(UIDashboard dashboard)
    {
        this.dashboard = dashboard;

        UISwitcherButton home = new UISwitcherButton(Icons.FOLDER, () -> this.dashboard.getPanels().panel instanceof UIHomePanel, (b) -> this.dashboard.documentTabsBar.switchHomeType(null));
        UISwitcherButton films = new UISwitcherButton(Icons.FILM, () -> this.dashboard.getPanels().panel instanceof UIFilmPanel, (b) -> this.dashboard.documentTabsBar.switchHomeType(ContentType.FILMS));
        UISwitcherButton particles = new UISwitcherButton(Icons.PARTICLE, () -> this.dashboard.getPanels().panel instanceof UIParticleSchemePanel, (b) -> this.dashboard.documentTabsBar.switchHomeType(ContentType.PARTICLES));
        UISwitcherButton models = new UISwitcherButton(Icons.PLAYER, () -> this.dashboard.getPanels().panel instanceof UIModelPanel, (b) -> this.dashboard.documentTabsBar.switchHomeType(ContentType.MODELS));
        UISwitcherButton audios = new UISwitcherButton(Icons.SOUND, () -> this.dashboard.getPanels().panel instanceof UIAudioEditorPanel, (b) -> this.dashboard.documentTabsBar.switchHomeType(ContentType.SOUNDS));

        home.tooltip(UIKeys.RAW_HOME, Direction.TOP);
        films.tooltip(UIKeys.FILM_TITLE, Direction.TOP);
        particles.tooltip(UIKeys.PANELS_PARTICLES, Direction.TOP);
        models.tooltip(UIKeys.MODELS_TITLE, Direction.TOP);
        audios.tooltip(UIKeys.PANELS_AUDIOS, Direction.TOP);

        this.add(home, films, particles, models, audios);
        this.row(0).resize();
        this.setVisible(false);
    }

    private static class UISwitcherButton extends UIClickable<UISwitcherButton>
    {
        private final Icon icon;
        private final Supplier<Boolean> activeSupplier;

        public UISwitcherButton(Icon icon, Supplier<Boolean> activeSupplier, Consumer<UISwitcherButton> callback)
        {
            super(callback);
            this.icon = icon;
            this.activeSupplier = activeSupplier;
            this.wh(35, 24);
        }

        @Override
        protected UISwitcherButton get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            boolean active = this.activeSupplier.get();
            int bg;

            if (active)
            {
                bg = Colors.setA(BBSSettings.primaryColor.get(), 0.6F);
            }
            else
            {
                bg = this.hover ? Colors.setA(Colors.WHITE, 0.25F) : Colors.setA(0, 0.4F);
            }

            this.area.render(context.batcher, bg);

            if (active)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + 1, Colors.A100 | BBSSettings.primaryColor.get());
                context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), Colors.A100 | BBSSettings.primaryColor.get());
            }

            int color = this.isEnabled() ? Colors.LIGHTEST_GRAY : 0x44ffffff;

            if (this.hover && !active)
            {
                color = Colors.WHITE;
            }

            context.batcher.icon(this.icon, color, this.area.mx(), this.area.my(), 0.5F, 0.5F);
        }
    }
}
