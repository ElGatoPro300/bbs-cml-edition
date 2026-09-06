package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class UIListOverlayPanel extends UIOverlayPanel
{
    public Consumer<List<String>> callback;

    public UISearchList<String> list;

    public UIListOverlayPanel(IKey title, Consumer<String> callback)
    {
        super(title);

        this.callback((l) ->
        {
            if (callback != null && !l.isEmpty())
            {
                callback.accept(l.get(0));
            }
        });

        this.list = new UISearchList<>(new UIStringList((l) ->
        {
            if (this.callback != null)
            {
                this.callback.accept(l);
            }
        }));
        this.list.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -6);

        this.content.add(this.list);
    }

    public UIListOverlayPanel callback(Consumer<List<String>> callback)
    {
        this.callback = callback;

        return this;
    }

    public UIListOverlayPanel setValue(String value)
    {
        this.list.list.setCurrentScroll(value);

        return this;
    }

    public UIListOverlayPanel addValues(Collection<String> values)
    {
        this.list.list.add(values);

        return this;
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        this.getContext().focus(this.list.search);
    }
}