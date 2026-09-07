package mchorse.bbs_mod.ui.utility;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.l10n.keys.LangKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.input.text.utils.TextLine;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

public class UILanguageKey extends UIElement
{
    public UILabel key;
    public UIElement content;
    public UIText base;
    public UIText reference;

    private UILanguageEditorOverlayPanel panel;
    private LangKey langKey;
    private String original;
    private Runnable callback;

    public UILanguageKey(UILanguageEditorOverlayPanel panel, LangKey langKey, String base, String reference, Runnable callback)
    {
        this.panel = panel;
        this.langKey = langKey;
        this.original = base;
        this.callback = callback;

        this.key = UI.label(IKey.constant(langKey.key));
        this.key.labelAnchor(0, 0.5F).h(20);
        this.key.tooltip(IKey.constant(langKey.key), Direction.BOTTOM);

        if (langKey.content.length() >= 60)
        {
            UITextarea<TextLine> textarea = new UITextarea<>(this::setContent);

            textarea.setText(langKey.content);
            textarea.background().wrap();
            textarea.h(80);

            this.content = textarea;
        }
        else
        {
            UITextbox textbox = new UITextbox(10000, this::setContent);

            textbox.setText(langKey.content);

            this.content = textbox;
        }

        this.base = new UIText().text(base).padding(5);

        this.column(0).vertical().stretch();

        this.context((menu) ->
        {
            menu.action(Icons.COPY, UIKeys.LANGUAGE_EDITOR_CONTEXT_COPY_KEY, () -> Window.setClipboard(this.langKey.key));
            menu.action(Icons.COPY, UIKeys.LANGUAGE_EDITOR_CONTEXT_COPY_ORIGINAL, () -> Window.setClipboard(this.original));

            if (this.panel.hasMarked(this.langKey.key))
            {
                menu.action(Icons.CLOSE, UIKeys.LANGUAGE_EDITOR_CONTEXT_UNMARK_COMPLETED, () -> this.panel.setMarked(this.langKey.key, false));
            }
            else
            {
                menu.action(Icons.CHECKMARK, UIKeys.LANGUAGE_EDITOR_CONTEXT_MARK_COMPLETED, () -> this.panel.setMarked(this.langKey.key, true));
            }
        });

        this.add(this.key, this.content, this.base);

        if (!reference.isEmpty())
        {
            this.add(this.reference = new UIText().text(reference).padding(5));
        }
    }

    private void setContent(String t)
    {
        this.langKey.content = t;

        if (this.callback != null)
        {
            this.callback.run();
        }
    }

    public LangKey getLangKey()
    {
        return this.langKey;
    }

    @Override
    public void render(UIContext context)
    {
        int color = Colors.A100 | BBSSettings.primaryColor.get();

        this.base.area.render(context.batcher, Colors.mulRGB(color, 0.25F));

        if (this.reference != null)
        {
            this.reference.area.render(context.batcher, Colors.mulRGB(color, 0.125F));
        }

        if (!this.isStillSame())
        {
            int checkColor = Colors.A100 | Colors.POSITIVE;

            if (this.panel.hasMarked(this.langKey.key))
            {
                checkColor = Colors.A100 | Colors.ACTIVE;
            }

            context.batcher.icon(Icons.CHECKMARK, checkColor, this.key.area.ex() - 10, this.key.area.my(), 0.5F, 0.5F);
        }

        super.render(context);
    }

    public boolean isStillSame()
    {
        return this.langKey.content.equals(this.original) && !this.panel.hasMarked(this.langKey.key);
    }
}