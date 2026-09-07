package mchorse.bbs_mod.ui.addons;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.addons.AddonInfo;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.util.Util;

public class UIAddonsOverlayPanel extends UIOverlayPanel
{
    private static boolean registeredIcons = false;

    public UIScrollView addons;
    public UIIcon reload;

    public UIAddonsOverlayPanel()
    {
        super(UIKeys.ADDONS_TITLE);
        this.resizable();

        if (!registeredIcons)
        {
            BBSMod.getProvider().register(new InternalAssetsSourcePack("mod_icons", "assets", BBSMod.class));
            registeredIcons = true;
        }

        this.addons = new UIScrollView();
        this.addons.relative(this.content).w(1F).h(1F);
        this.addons.column(5).vertical().stretch().scroll().padding(10);

        this.reload = new UIIcon(Icons.REFRESH, (b) -> this.reload());
        this.reload.tooltip(UIKeys.ADDONS_RELOAD);

        this.icons.prepend(this.reload);
        this.content.add(this.addons);

        this.reload();
        this.markContainer();
    }

    public void reload()
    {
        this.addons.removeAll();

        for (AddonInfo info : BBSModClient.registeredAddons)
        {
            this.addons.add(new UIAddonEntry(info));
        }

        if (this.addons.getChildren().isEmpty())
        {
            UILabel noAddons = new UILabel(UIKeys.ADDONS_NO_ADDONS);
            noAddons.color(Colors.LIGHTER_GRAY);
            noAddons.relative(this.addons).x(0.5F).y(0.5F).anchor(0.5F, 0.5F);
            this.addons.add(noAddons);
        }

        this.addons.resize();
    }

    public static class UIAddonEntry extends UIElement
    {
        public AddonInfo mod;
        public Texture icon;

        public UIAddonEntry(AddonInfo mod)
        {
            this.mod = mod;

            this.h(80);

            if (mod.icon != null)
            {
                this.icon = BBSModClient.getTextures().getTexture(mod.icon);
            }

            int textX = this.icon != null ? 80 : 10;

            UILabel version = new UILabel(IKey.raw("v" + mod.version).format(Colors.GRAY));
            version.relative(this).x(1F, -10).y(10).anchorX(1F);

            UILabel description = new UILabel(IKey.raw(mod.description).format(Colors.LIGHTER_GRAY));
            description.relative(this).x(textX).y(30).w(1F, -10 - textX);

            String authors = String.join(", ", mod.authors);
            UILabel authorLabel = new UILabel(UIKeys.ADDONS_AUTHOR.format(IKey.raw(authors)).format(Colors.LIGHTER_GRAY));
            authorLabel.relative(this).x(textX).y(1F, -10).anchorY(1F).w(1F, -10 - textX);

            this.add(version, description, authorLabel);

            int x = 0;

            if (!mod.website.isEmpty())
            {
                UIIcon web = new UIIcon(Icons.GLOBE, (b) -> openLink(mod.website));
                web.tooltip(UIKeys.ADDONS_WEBSITE);
                web.relative(this).x(1F, -10 - x).y(1F, -5).anchor(1F, 1F).w(20).h(20);
                this.add(web);
                x += 24;
            }

            if (!mod.issues.isEmpty())
            {
                UIIcon issues = new UIIcon(Icons.EXCLAMATION, (b) -> openLink(mod.issues));
                issues.tooltip(UIKeys.ADDONS_ISSUES);
                issues.relative(this).x(1F, -10 - x).y(1F, -5).anchor(1F, 1F).w(20).h(20);
                this.add(issues);
                x += 24;
            }

            if (!mod.source.isEmpty())
            {
                UIIcon source = new UIIcon(Icons.CODE, (b) -> openLink(mod.source));
                source.tooltip(UIKeys.ADDONS_SOURCE);
                source.relative(this).x(1F, -10 - x).y(1F, -5).anchor(1F, 1F).w(20).h(20);
                this.add(source);
                x += 24;
            }
        }

        private static void openLink(String url)
        {
            try
            {
                Util.getOperatingSystem().open(url);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public void render(UIContext context)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50 | Colors.LIGHTER_GRAY);

            if (this.icon != null)
            {
                context.batcher.fullTexturedBox(this.icon, this.area.x + 10, this.area.y + 10, 60, 60);
            }

            int textX = this.icon != null ? 80 : 10;
            String name = this.mod.name;

            context.batcher.getContext().getMatrices().push();
            context.batcher.getContext().getMatrices().translate(this.area.x + textX, this.area.y + 10, 0);
            context.batcher.getContext().getMatrices().scale(1.5F, 1.5F, 1.5F);
            context.batcher.text(name, 0, 0, Colors.WHITE);
            context.batcher.getContext().getMatrices().pop();

            super.render(context);
        }
    }
}
