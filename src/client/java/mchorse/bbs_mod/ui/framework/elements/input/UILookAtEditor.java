package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.forms.forms.utils.LookAt;
import mchorse.bbs_mod.forms.forms.utils.LookAtBone;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * "Look at" editor: bone list and per-bone lock settings (like the pose menu layout).
 */
public class UILookAtEditor extends UIElement
{
    public UIToggle translate;
    public UISearchList<String> bones;
    public UIToggle enabled;
    public UITrackpad blend;
    public UIButton pickTarget;
    public UIButton pickAttachment;

    private Supplier<LookAt> getter;
    private Consumer<Consumer<LookAt>> editor;
    private Supplier<UIFilmPanel> filmPanelSupplier;
    private String currentBone;

    public UILookAtEditor()
    {
        this.translate = new UIToggle(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_TRANSLATE, (b) -> this.edit((lookAt) -> lookAt.translate = b.getValue()));
        this.translate.tooltip(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_TRANSLATE_TOOLTIP);

        this.bones = new UISearchList<>(new LookAtBoneList((l) -> this.pickBone(l.get(0)))
        {
            @Override
            public void render(UIContext context)
            {
                super.render(context);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
            }
        });
        this.bones.label(UIKeys.GENERAL_SEARCH);
        this.bones.h(UIStringList.DEFAULT_HEIGHT * 8 + 12);
        this.bones.list.background(0xFF141418);
        this.bones.list.scroll.cancelScrolling();

        this.enabled = new UIToggle(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_ENABLED, (b) -> this.editBone((bone) -> bone.enabled = b.getValue()));
        this.enabled.tooltip(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_ENABLED_TOOLTIP);
        this.blend = new UITrackpad((v) -> this.editBone((bone) -> bone.blend = v.floatValue() / 100F));
        this.blend.limit(0D, 100D).tooltip(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_BLEND_TOOLTIP);
        this.pickTarget = new UIButton(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_PICK_TARGET, (b) -> this.displayActors());
        this.pickAttachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) -> this.displayAttachments());
        this.pickAttachment.tooltip(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_ATTACHMENT_TOOLTIP);

        this.pickTarget.w(1F);
        this.pickAttachment.w(1F);

        this.column().vertical().stretch();
        this.w(1F);
        this.resize();
    }

    public void layout(int width)
    {
        this.removeAll();

        if (width > 240)
        {
            UIElement left = UI.column(
                this.translate,
                this.enabled,
                UI.label(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_BLEND),
                this.blend,
                this.pickTarget,
                this.pickAttachment
            ).w(1F);

            UIElement right = UI.column(
                UI.label(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_BONES),
                this.bones
            ).w(1F);

            this.add(UI.row(left, right).w(1F));
        }
        else
        {
            this.add(
                UI.label(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_BONES),
                this.bones,
                this.translate,
                this.enabled,
                UI.label(UIKeys.GENERIC_KEYFRAMES_LOOK_AT_BLEND),
                this.blend,
                this.pickTarget,
                this.pickAttachment
            );
        }
    }

    @Override
    public void resize()
    {
        int width = this.area.w > 0 ? this.area.w : this.getFlex().getW();

        this.layout(width);
        super.resize();
    }

    public UILookAtEditor callbacks(Supplier<LookAt> getter, Consumer<Consumer<LookAt>> editor)
    {
        this.getter = getter;
        this.editor = editor;

        return this;
    }

    public UILookAtEditor filmPanel(Supplier<UIFilmPanel> filmPanelSupplier)
    {
        this.filmPanelSupplier = filmPanelSupplier;

        return this;
    }

    public void fillBones(Collection<String> bones)
    {
        this.bones.list.clear();
        this.bones.list.add(bones);
        this.bones.list.sort();

        List<String> list = this.bones.list.getList();

        if (!list.isEmpty())
        {
            this.bones.list.setIndex(0);
            this.pickBone(list.get(0));
        }
        else
        {
            this.bones.list.setIndex(-1);
            this.pickBone(null);
        }
    }

    public void selectBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return;
        }

        this.bones.list.setCurrentScroll(bone);
        this.pickBone(bone);
    }

    public String getCurrentBone()
    {
        return this.currentBone;
    }

    /**
     * Re-reads the current bone's values (for example after an undo/redo or
     * keyframe selection change).
     */
    public void refresh()
    {
        this.pickBone(this.currentBone);

        LookAt lookAt = this.getLookAt();

        this.translate.setValue(lookAt != null && lookAt.translate);
    }

    private LookAt getLookAt()
    {
        return this.getter == null ? null : this.getter.get();
    }

    private LookAtBone getBone(String bone)
    {
        LookAt lookAt = this.getLookAt();

        return lookAt == null || bone == null ? null : lookAt.bones.get(bone);
    }

    private void edit(Consumer<LookAt> consumer)
    {
        if (this.editor != null)
        {
            this.editor.accept(consumer);
        }
    }

    private void editBone(Consumer<LookAtBone> consumer)
    {
        if (this.currentBone == null)
        {
            return;
        }

        String bone = this.currentBone;

        this.edit((lookAt) -> consumer.accept(lookAt.get(bone)));
    }

    private void pickBone(String bone)
    {
        this.currentBone = bone;

        boolean present = bone != null;

        this.enabled.setEnabled(present);
        this.blend.setEnabled(present);
        this.pickTarget.setEnabled(present);
        this.pickAttachment.setEnabled(present);

        LookAtBone lookAtBone = this.getBone(bone);

        this.enabled.setValue(lookAtBone != null && lookAtBone.enabled);
        this.blend.setValue(lookAtBone == null ? 0F : lookAtBone.blend * 100F);

        LookAt lookAt = this.getLookAt();

        this.translate.setValue(lookAt != null && lookAt.translate);
    }

    private UIFilmPanel getFilmPanel()
    {
        if (this.filmPanelSupplier != null)
        {
            UIFilmPanel panel = this.filmPanelSupplier.get();

            if (panel != null)
            {
                return panel;
            }
        }

        UIElement element = this;

        while (element != null)
        {
            if (element instanceof UIFilmPanel panel)
            {
                return panel;
            }

            element = element.getParent();
        }

        UIContext context = this.getMenuContext();

        if (context != null && context.menu != null)
        {
            List<UIFilmPanel> children = context.menu.main.getChildren(UIFilmPanel.class);

            if (!children.isEmpty())
            {
                return children.get(0);
            }
        }

        return null;
    }

    private UIContext getMenuContext()
    {
        UIContext context = this.pickTarget.getContext();

        if (context != null)
        {
            return context;
        }

        return this.pickAttachment.getContext();
    }

    private void displayActors()
    {
        if (this.currentBone == null)
        {
            return;
        }

        UIFilmPanel panel = this.getFilmPanel();
        LookAtBone bone = this.getBone(this.currentBone);
        int current = bone == null ? LookAt.NO_TARGET : bone.replay;
        UIContext context = this.getMenuContext();

        if (panel != null && context != null)
        {
            UIAnchorKeyframeFactory.displayActors(context, panel.getController().getEntities(), current, (actor) -> this.editBone((b) -> b.replay = actor));

            return;
        }

        if (context == null)
        {
            return;
        }

        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, Colors.NEGATIVE, () -> this.editBone((b) -> b.replay = LookAt.NO_TARGET));
        });
    }

    private void displayAttachments()
    {
        UIFilmPanel panel = this.getFilmPanel();

        if (panel == null || this.currentBone == null)
        {
            return;
        }

        LookAtBone bone = this.getBone(this.currentBone);

        if (bone == null || bone.replay == LookAt.NO_TARGET)
        {
            return;
        }

        UIAnchorKeyframeFactory.displayAttachments(this.getMenuContext(), panel, bone.replay, bone.attachment, (attachment) -> this.editBone((b) -> b.attachment = attachment));
    }

    /**
     * Bone list that renders a checkmark before every bone (bright when that
     * bone's look at lock is enabled), just like the pose editor's bone list.
     */
    private class LookAtBoneList extends UIStringList
    {
        public LookAtBoneList(Consumer<List<String>> callback)
        {
            super(callback);
        }

        @Override
        protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
        {
            LookAtBone bone = UILookAtEditor.this.getBone(element);
            boolean locked = bone != null && bone.enabled;
            int iconX = x + 2;
            int iconY = y + (this.scroll.scrollItemSize - 16) / 2;
            int iconColor = locked ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.35F);

            RenderSystem.enableBlend();
            context.batcher.icon(Icons.CHECKMARK, iconColor, iconX, iconY);

            int textX = x + 22;
            int maxWidth = this.area.w - 24;
            String displayText = element;
            int textWidth = context.batcher.getFont().getWidth(displayText);

            if (textWidth > maxWidth)
            {
                displayText = context.batcher.getFont().limitToWidth(displayText, maxWidth);
            }

            context.batcher.textShadow(displayText, textX, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);
        }
    }
}
