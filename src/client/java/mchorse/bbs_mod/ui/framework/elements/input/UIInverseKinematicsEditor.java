package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.forms.forms.utils.InverseKinematics;
import mchorse.bbs_mod.forms.forms.utils.InverseKinematicsBone;
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

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Mine-imator style inverse kinematics editor: per-bone target, angle target,
 * angle offset, blend and bend-X-as-offset.
 */
public class UIInverseKinematicsEditor extends UIElement
{
    public UISearchList<String> bones;
    public UIToggle enabled;
    public UIButton pickTarget;
    public UIButton pickTargetAttachment;
    public UIButton pickAngleTarget;
    public UIButton pickAngleAttachment;
    public UITrackpad angleOffset;
    public UITrackpad blend;
    public UIToggle bendXAsOffset;

    private Supplier<InverseKinematics> getter;
    private Consumer<Consumer<InverseKinematics>> editor;
    private Supplier<UIFilmPanel> filmPanelSupplier;
    private String currentBone;

    public UIInverseKinematicsEditor()
    {
        this.bones = new UISearchList<>(new InverseKinematicsBoneList(this, (l) -> this.pickBone(l.get(0)))
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

        this.enabled = new UIToggle(UIKeys.GENERIC_KEYFRAMES_IK_ENABLED, (b) -> this.editBone((bone) -> bone.enabled = b.getValue()));
        this.enabled.tooltip(UIKeys.GENERIC_KEYFRAMES_IK_ENABLED_TOOLTIP);

        this.pickTarget = new UIButton(UIKeys.GENERIC_KEYFRAMES_IK_TARGET, (b) -> this.displayTargetActors());
        this.pickTarget.tooltip(UIKeys.GENERIC_KEYFRAMES_IK_TARGET_TOOLTIP);
        this.pickTargetAttachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) -> this.displayTargetAttachments());
        this.pickTargetAttachment.tooltip(UIKeys.GENERIC_KEYFRAMES_IK_TARGET_ATTACHMENT_TOOLTIP);

        this.pickAngleTarget = new UIButton(UIKeys.GENERIC_KEYFRAMES_IK_ANGLE_TARGET, (b) -> this.displayAngleActors());
        this.pickAngleTarget.tooltip(UIKeys.GENERIC_KEYFRAMES_IK_ANGLE_TARGET_TOOLTIP);
        this.pickAngleAttachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) -> this.displayAngleAttachments());
        this.pickAngleAttachment.tooltip(UIKeys.GENERIC_KEYFRAMES_IK_ANGLE_ATTACHMENT_TOOLTIP);

        this.angleOffset = new UITrackpad((v) -> this.editBone((bone) -> bone.angleOffset = v.floatValue()));
        this.angleOffset.limit(-180D, 180D).tooltip(UIKeys.GENERIC_KEYFRAMES_IK_ANGLE_OFFSET_TOOLTIP);

        this.blend = new UITrackpad((v) -> this.editBone((bone) -> bone.blend = v.floatValue() / 100F));
        this.blend.limit(0D, 100D).tooltip(UIKeys.GENERIC_KEYFRAMES_IK_BLEND_TOOLTIP);

        this.bendXAsOffset = new UIToggle(UIKeys.GENERIC_KEYFRAMES_IK_BEND_X_AS_OFFSET, (b) -> this.editBone((bone) -> bone.bendXAsOffset = b.getValue()));
        this.bendXAsOffset.tooltip(UIKeys.GENERIC_KEYFRAMES_IK_BEND_X_AS_OFFSET_TOOLTIP);

        this.pickTarget.w(1F);
        this.pickTargetAttachment.w(1F);
        this.pickAngleTarget.w(1F);
        this.pickAngleAttachment.w(1F);

        this.column().vertical().stretch();
        this.w(1F);
        this.resize();
    }

    public void layout(int width)
    {
        this.removeAll();

        if (width > 280)
        {
            UIElement left = UI.column(
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_BONES),
                this.bones
            ).w(1F);

            UIElement right = UI.column(
                this.enabled,
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_TARGET),
                this.pickTarget,
                this.pickTargetAttachment,
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_ANGLE_TARGET),
                this.pickAngleTarget,
                this.pickAngleAttachment,
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_ANGLE_OFFSET),
                this.angleOffset,
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_BLEND),
                this.blend,
                this.bendXAsOffset
            ).w(1F);

            this.add(UI.row(left, right).w(1F));
        }
        else
        {
            this.add(
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_BONES),
                this.bones,
                this.enabled,
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_TARGET),
                this.pickTarget,
                this.pickTargetAttachment,
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_ANGLE_TARGET),
                this.pickAngleTarget,
                this.pickAngleAttachment,
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_ANGLE_OFFSET),
                this.angleOffset,
                UI.label(UIKeys.GENERIC_KEYFRAMES_IK_BLEND),
                this.blend,
                this.bendXAsOffset
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

    public UIInverseKinematicsEditor callbacks(Supplier<InverseKinematics> getter, Consumer<Consumer<InverseKinematics>> editor)
    {
        this.getter = getter;
        this.editor = editor;

        return this;
    }

    public UIInverseKinematicsEditor filmPanel(Supplier<UIFilmPanel> filmPanelSupplier)
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

    public void refresh()
    {
        this.pickBone(this.currentBone);
    }

    private InverseKinematics getInverseKinematics()
    {
        return this.getter == null ? null : this.getter.get();
    }

    private InverseKinematicsBone getBone(String bone)
    {
        InverseKinematics ik = this.getInverseKinematics();

        return ik == null || bone == null ? null : ik.bones.get(bone);
    }

    private void edit(Consumer<InverseKinematics> consumer)
    {
        if (this.editor != null)
        {
            this.editor.accept(consumer);
        }
    }

    private void editBone(Consumer<InverseKinematicsBone> consumer)
    {
        if (this.currentBone == null)
        {
            return;
        }

        String bone = this.currentBone;

        this.edit((ik) -> consumer.accept(ik.get(bone)));
    }

    private void pickBone(String bone)
    {
        this.currentBone = bone;

        boolean present = bone != null;

        this.enabled.setEnabled(present);
        this.pickTarget.setEnabled(present);
        this.pickTargetAttachment.setEnabled(present);
        this.pickAngleTarget.setEnabled(present);
        this.pickAngleAttachment.setEnabled(present);
        this.angleOffset.setEnabled(present);
        this.blend.setEnabled(present);
        this.bendXAsOffset.setEnabled(present);

        InverseKinematicsBone ikBone = this.getBone(bone);

        this.enabled.setValue(ikBone != null && ikBone.enabled);
        this.blend.setValue(ikBone == null ? 0F : ikBone.blend * 100F);
        this.angleOffset.setValue(ikBone == null ? 0D : ikBone.angleOffset);
        this.bendXAsOffset.setValue(ikBone != null && ikBone.bendXAsOffset);
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

        return this.pickAngleTarget.getContext();
    }

    private void displayTargetActors()
    {
        if (this.currentBone == null)
        {
            return;
        }

        UIFilmPanel panel = this.getFilmPanel();
        InverseKinematicsBone bone = this.getBone(this.currentBone);
        int current = bone == null ? InverseKinematics.NO_TARGET : bone.targetReplay;
        UIContext context = this.getMenuContext();

        if (panel != null && context != null)
        {
            UIAnchorKeyframeFactory.displayActors(context, panel.getController().getEntities(), current, (actor) ->
                this.editBone((ikBone) -> ikBone.targetReplay = actor)
            );
        }
    }

    private void displayTargetAttachments()
    {
        if (this.currentBone == null)
        {
            return;
        }

        UIFilmPanel panel = this.getFilmPanel();
        InverseKinematicsBone bone = this.getBone(this.currentBone);

        if (panel == null || bone == null || bone.targetReplay < 0)
        {
            return;
        }

        UIAnchorKeyframeFactory.displayAttachments(panel, bone.targetReplay, bone.targetAttachment, (attachment) ->
            this.editBone((ikBone) -> ikBone.targetAttachment = attachment)
        );
    }

    private void displayAngleActors()
    {
        if (this.currentBone == null)
        {
            return;
        }

        UIFilmPanel panel = this.getFilmPanel();
        InverseKinematicsBone bone = this.getBone(this.currentBone);
        int current = bone == null ? InverseKinematics.NO_TARGET : bone.angleTargetReplay;
        UIContext context = this.getMenuContext();

        if (panel != null && context != null)
        {
            UIAnchorKeyframeFactory.displayActors(context, panel.getController().getEntities(), current, (actor) ->
                this.editBone((ikBone) -> ikBone.angleTargetReplay = actor)
            );
        }
    }

    private void displayAngleAttachments()
    {
        if (this.currentBone == null)
        {
            return;
        }

        UIFilmPanel panel = this.getFilmPanel();
        InverseKinematicsBone bone = this.getBone(this.currentBone);

        if (panel == null || bone == null || bone.angleTargetReplay < 0)
        {
            return;
        }

        UIAnchorKeyframeFactory.displayAttachments(panel, bone.angleTargetReplay, bone.angleTargetAttachment, (attachment) ->
            this.editBone((ikBone) -> ikBone.angleTargetAttachment = attachment)
        );
    }

    private static class InverseKinematicsBoneList extends UIStringList
    {
        private final UIInverseKinematicsEditor editor;

        public InverseKinematicsBoneList(UIInverseKinematicsEditor editor, Consumer<List<String>> callback)
        {
            super(callback);

            this.editor = editor;
        }

        @Override
        protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
        {
            InverseKinematicsBone bone = this.editor.getBone(element);
            boolean locked = bone != null && bone.enabled;
            int iconX = x + 2;
            int iconY = y + (this.scroll.scrollItemSize - 16) / 2;
            int iconColor = locked ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.35F);

            GlStateManager._enableBlend();
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
