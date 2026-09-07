package mchorse.bbs_mod.ui.model;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ModelConfig;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class UIModelItemsSection extends UIModelSection
{
    public UIModelItemsSection(IUIModelPanelHost editor)
    {
        super(editor);

        this.title.label = UIKeys.MODELS_ITEMS;
        this.fields.add(UI.row(
            new UIButton(UIKeys.MODELS_ITEMS_MAIN, (b) -> this.openContextMenu(this.config.itemsMain)),
            new UIButton(UIKeys.MODELS_ITEMS_OFF, (b) -> this.openContextMenu(this.config.itemsOff))
        ));
        this.fields.add(new UIButton(UIKeys.MODELS_HANDS_EDIT, (b) ->
        {
            this.editor.openTransformEditor(new UIModelItemsTransformEditor(this.editor, this.config));
        }));
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.MODELS_ITEMS;
    }

    private void openContextMenu(ValueList<ArmorSlot> valueList)
    {
        if (this.config == null) return;

        ModelInstance model = BBSModClient.getModels().getModel(this.config.getId());
        if (model == null) return;

        List<String> groups = new ArrayList<>(model.getModel().getAllGroupKeys());
        Collections.sort(groups);
        groups.add(0, "<none>");

        this.getContext().replaceContextMenu(new UIStringListContextMenu(groups, 
            () -> valueList.getList().stream().map(v -> v.group.get()).collect(Collectors.toSet()), 
            (group) ->
            {
                boolean existed = valueList.getList().stream().anyMatch(v -> v.group.get().equals(group));
                valueList.getAllTyped().clear();

                if (!group.equals("<none>") && !existed)
                {
                    ArmorSlot slot = new ArmorSlot("0");

                    slot.group.set(group);
                    valueList.add(slot);
                }

                this.editor.dirty();
            }));
    }

    public static class UIStringListContextMenu extends UIContextMenu
    {
        public UISearchList<String> list;

        public UIStringListContextMenu(List<String> groups, Supplier<Collection<String>> selected, Consumer<String> callback)
        {
            this.list = new UISearchList<>(new UIStringList((l) -> {
                if (l.get(0) != null) callback.accept(l.get(0));
            }) {
                @Override
                protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selectedState)
                {
                    if (selected.get().contains(element))
                    {
                        context.batcher.box(x, y, x + this.area.w, y + this.scroll.scrollItemSize, Colors.A50 | BBSSettings.primaryColor.get());
                    }
                    super.renderElementPart(context, element, i, x, y, hover, selectedState);
                }
            });
            this.list.list.setList(groups);
            this.list.list.background = 0xaa000000;
            this.list.relative(this).xy(5, 5).w(1F, -10).h(1F, -10);
            this.list.search.placeholder(UIKeys.POSE_CONTEXT_NAME);
            this.add(this.list);
        }

        @Override
        public boolean isEmpty()
        {
            return this.list.list.getList().isEmpty();
        }

        @Override
        public void setMouse(UIContext context)
        {
            this.xy(context.mouseX(), context.mouseY()).w(120).h(200).bounds(context.menu.overlay, 5);
        }
    }
}
