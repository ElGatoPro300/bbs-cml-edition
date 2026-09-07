package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIInverseKinematicsKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UILookAtKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.gizmo.TransformOrientation;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIKeyframeEditor extends UIElement
{
    public static final int[] COLORS = {Colors.RED, Colors.GREEN, Colors.BLUE, Colors.CYAN, Colors.MAGENTA, Colors.YELLOW, Colors.LIGHTEST_GRAY & 0xffffff, Colors.DEEP_PINK};
    private static final int SIDE_PANEL_WIDTH = 140;
    private static final int SIDE_PANEL_WIDTH_MIN = 90;
    private static final int SIDE_PANEL_WIDTH_MAX = 420;
    private static final int BOTTOM_PANEL_HEIGHT = 140;
    private static final int BOTTOM_PANEL_HEIGHT_MIN = 90;
    private static final int BOTTOM_PANEL_HEIGHT_MAX = 420;
    private static final int GLOBAL_TRACKERS_TOP_GAP = 36;

    public UIKeyframes view;
    public UIKeyframeFactory editor;

    private UIElement target;
    private boolean stackedLayout;
    private boolean overlayPanel;
    private Runnable pickListener;
    private int sidePanelWidth = SIDE_PANEL_WIDTH;
    private int bottomPanelHeight = BOTTOM_PANEL_HEIGHT;
    private UIDraggable sidePanelResizer;
    private UIRenderable overlayBackground;

    public UIKeyframeEditor(Function<Consumer<Keyframe>, UIKeyframes> factory)
    {
        this.view = factory.apply(this::pickKeyframe);
        this.view.changed(() ->
        {
            if (this.editor != null)
            {
                this.editor.update();
            }
        });
        this.view.getDopeSheet().setTopMargin(GLOBAL_TRACKERS_TOP_GAP);

        this.add(this.view.full(this).w(1F, -SIDE_PANEL_WIDTH));

        this.sidePanelResizer = new UIDraggable((context) ->
        {
            if (this.stackedLayout)
            {
                int height = this.area.ey() - context.mouseY;

                this.bottomPanelHeight = Math.max(BOTTOM_PANEL_HEIGHT_MIN, Math.min(BOTTOM_PANEL_HEIGHT_MAX, height));
            }
            else
            {
                int width = this.area.ex() - context.mouseX;

                this.sidePanelWidth = Math.max(SIDE_PANEL_WIDTH_MIN, Math.min(SIDE_PANEL_WIDTH_MAX, width));
            }

            this.applyLayout();
            this.resize();
        })
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 0 && Window.isCtrlPressed())
                {
                    if (UIKeyframeEditor.this.stackedLayout)
                    {
                        UIKeyframeEditor.this.bottomPanelHeight = BOTTOM_PANEL_HEIGHT;
                    }
                    else
                    {
                        UIKeyframeEditor.this.sidePanelWidth = SIDE_PANEL_WIDTH;
                    }
                    UIKeyframeEditor.this.applyLayout();
                    UIKeyframeEditor.this.resize();

                    return true;
                }

                return super.subMouseClicked(context);
            }
        }.rendering((context) ->
        {
            float alpha = (this.sidePanelResizer.isDragging() || this.sidePanelResizer.area.isInside(context)) ? 0.75F : 0.5F;
            int color = Colors.setA(BBSSettings.accentRgb(), alpha);

            context.batcher.box(this.sidePanelResizer.area.x, this.sidePanelResizer.area.y, this.sidePanelResizer.area.ex(), this.sidePanelResizer.area.ey(), color);
        });
        this.overlayBackground = new UIRenderable(this::renderOverlayPanelBackground);

        this.add(this.overlayBackground, this.sidePanelResizer);
        this.updateSidePanelResizerState();
    }

    public UIKeyframeEditor target(UIElement target)
    {
        if (this.target == target)
        {
            /* Side-panel mode keeps target null. Undo can detach the factory panel
             * via removeFromParent while target stays null — re-parent it here so
             * applyLayout alone does not leave an empty overlay. */
            if (this.editor != null && this.target == null && this.editor.getParent() != this)
            {
                this.add(this.editor);
            }

            this.view.resetFlex().full(this).w(1F);
            /* Same host — still re-apply factory layout/resize. Needed when the
             * properties tab was already active and only a light host sync runs. */
            this.applyLayout();
            this.resize();

            return this;
        }

        if (this.editor != null)
        {
            this.editor.removeFromParent();
        }

        this.target = target;
        this.view.resetFlex().full(this).w(1F);

        if (this.editor != null)
        {
            if (this.target != null)
            {
                this.target.add(this.editor);
            }
            else
            {
                this.add(this.editor);
            }
        }

        this.applyLayout();
        this.resize();

        return this;
    }

    public UIKeyframeEditor pickListener(Runnable pickListener)
    {
        this.pickListener = pickListener;

        return this;
    }

    public UIKeyframeEditor overlayPanel(boolean overlayPanel)
    {
        this.unfocusEditorInputs();
        this.overlayPanel = overlayPanel;
        this.applyLayout();
        this.resize();

        return this;
    }

    @Override
    public void removeFromParent()
    {
        super.removeFromParent();

        if (this.editor != null)
        {
            this.editor.removeFromParent();
        }
    }

    @Override
    public void setVisible(boolean visible)
    {
        super.setVisible(visible);

        if (this.editor != null)
        {
            this.editor.setVisible(visible);
        }
    }

    private void pickKeyframe(Keyframe keyframe)
    {
        UIKeyframeFactory.saveScroll(this.editor);

        if (this.editor != null)
        {
            this.editor.removeFromParent();
            this.editor = null;
        }

        if (keyframe != null)
        {
            this.editor = UIKeyframeFactory.createPanel(keyframe, this.view);
            this.editor.setVisible(this.isVisible());

            if (this.target != null)
            {
                this.target.add(this.editor);
            }
            else
            {
                this.add(this.editor);
            }
        }

        this.applyLayout();
        this.resize();

        /* Only when a keyframe is picked — clearing selection (setChannels / deselect)
         * must not force the Properties tab away from Camera/Action Properties. */
        if (keyframe != null && this.pickListener != null)
        {
            this.pickListener.run();
        }
    }

    /**
     * True when a focused text/trackpad input belongs to the open keyframe
     * property panel. Used to avoid mid-typing refreshes that reset fields.
     */
    public boolean isEditorInputFocused()
    {
        if (this.editor == null)
        {
            return false;
        }

        UIContext context = this.getContext();

        if (context == null || !(context.activeElement instanceof UIElement focused))
        {
            return false;
        }

        UIElement current = focused;

        while (current != null)
        {
            if (current == this.editor)
            {
                return true;
            }

            current = current.getParent();
        }

        return false;
    }

    private void applyLayout()
    {
        if (this.target != null)
        {
            this.view.resetFlex().full(this).w(1F);

            if (this.editor != null)
            {
                this.editor.full(this.target);
                this.target.resize();
            }

            return;
        }

        if (this.stackedLayout)
        {
            this.view.resetFlex().relative(this).xy(0, 0).w(1F).h(1F);

            if (this.editor != null)
            {
                this.editor.relative(this).x(0).y(1F, -this.bottomPanelHeight).w(1F).h(this.bottomPanelHeight);

                if (!this.overlayPanel)
                {
                    this.view.h(1F, -this.bottomPanelHeight);
                }
            }
        }
        else
        {
            this.view.resetFlex().relative(this).xy(0, 0).w(1F).h(1F);

            if (this.editor != null)
            {
                this.editor.relative(this).x(1F, -this.sidePanelWidth).y(0).w(this.sidePanelWidth).h(1F);

                if (!this.overlayPanel)
                {
                    this.view.w(1F, -this.sidePanelWidth);
                }
            }
        }

        this.updateSidePanelResizerState();
    }

    @Override
    public void resize()
    {
        super.resize();
        this.updateSidePanelResizerState();
    }

    @Override
    public void render(UIContext context)
    {
        this.updateSidePanelResizerState();

        if (this.view.isVisible())
        {
            this.view.render(context);

            if (this.overlayPanel)
            {
                context.batcher.flushDraw();
            }
        }

        List<IUIElement> snapshot = new ArrayList<>(this.getChildren());

        for (IUIElement element : snapshot)
        {
            if (element != this.view && element.isVisible() && element.canBeRendered(context.getViewport()))
            {
                element.render(context);
            }
        }
    }

    private void renderOverlayPanelBackground(UIContext context)
    {
        if (!this.overlayPanel || this.editor == null)
        {
            return;
        }

        context.batcher.box(this.editor.area.x, this.editor.area.y, this.editor.area.ex(), this.editor.area.ey(), 0xFF141418);
        context.batcher.gradientHBox(this.editor.area.x - 40, this.editor.area.y, this.editor.area.ex() - 40, this.editor.area.ey(), 0, Colors.A25);
        context.batcher.box(this.editor.area.ex() - 40, this.editor.area.y, this.editor.area.ex(), this.editor.area.ey(), Colors.A25);
    }

    private void updateSidePanelResizerState()
    {
        if (this.sidePanelResizer == null)
        {
            return;
        }

        boolean locked = BBSSettings.editorLayoutSettings != null && BBSSettings.editorLayoutSettings.isLayoutLocked();
        boolean visible = this.target == null && this.editor != null && !locked;

        this.sidePanelResizer.setVisible(visible);
        this.sidePanelResizer.setEnabled(visible);

        if (!visible)
        {
            return;
        }

        if (this.stackedLayout)
        {
            this.bottomPanelHeight = Math.max(BOTTOM_PANEL_HEIGHT_MIN, Math.min(BOTTOM_PANEL_HEIGHT_MAX, this.bottomPanelHeight));

            int x = this.area.mx() - 20;
            int y = this.area.ey() - this.bottomPanelHeight - 3;

            this.sidePanelResizer.relative(this).x(x - this.area.x).y(y - this.area.y).w(40).h(6);
        }
        else
        {
            this.sidePanelWidth = Math.max(SIDE_PANEL_WIDTH_MIN, Math.min(SIDE_PANEL_WIDTH_MAX, this.sidePanelWidth));

            int x = this.area.ex() - this.sidePanelWidth - 3;
            int y = this.area.my() - 20;

            this.sidePanelResizer.relative(this).x(x - this.area.x).y(y - this.area.y).w(6).h(40);
        }

        this.sidePanelResizer.resize();
    }

    public void toggleLayout()
    {
        this.setStackedLayout(!this.stackedLayout);
    }

    public boolean isStackedLayout()
    {
        return this.stackedLayout;
    }

    public void setStackedLayout(boolean stackedLayout)
    {
        this.unfocusEditorInputs();
        this.stackedLayout = stackedLayout;
        this.applyLayout();
        this.resize();
    }

    /**
     * Layout / embed toggles can leave a trackpad textbox marked focused while
     * {@link UIContext#activeElement} is cleared or the widget tree moves.
     * Clearing focus avoids frozen numeric fields until the keyframe is re-picked.
     */
    private void unfocusEditorInputs()
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            context.unfocus();
        }
    }

    public void setChannel(KeyframeChannel channel, int color)
    {
        this.view.removeAllSheets();
        this.view.addSheet(new UIKeyframeSheet(color, false, channel, null));

        this.pickKeyframe(null);
    }

    public void setClip(KeyframeClip clip)
    {
        this.setChannels(clip.channels);
    }

    public void setChannels(KeyframeChannel[] channels)
    {
        this.view.removeAllSheets();

        for (int i = 0; i < channels.length; i++)
        {
            this.view.addSheet(new UIKeyframeSheet(COLORS[i % COLORS.length], false, channels[i], null));
        }

        this.pickKeyframe(null);
    }

    public UIKeyframeSheet getSheet(Keyframe keyframe)
    {
        if (keyframe == null)
        {
            return null;
        }

        for (UIKeyframeSheet sheet : this.view.getGraph().getSheets())
        {
            if (sheet.channel == keyframe.getParent())
            {
                return sheet;
            }
        }

        return null;
    }

    public Pair<String, TransformOrientation> getBone()
    {
        UIKeyframeFactory editor = this.editor;
        String bone = null;
        TransformOrientation orientation = TransformOrientation.PARENT;

        if (editor instanceof UIPoseKeyframeFactory || editor instanceof UITransformKeyframeFactory || editor instanceof UIAnchorKeyframeFactory)
        {
            UIKeyframeSheet sheet = this.getSheet(editor.getKeyframe());

            if (sheet != null)
            {
                String id = StringUtils.fileName(sheet.id);
                int colon = id.indexOf(':');
                String propertyId = colon != -1 ? id.substring(0, colon) : id;
                String boneName = colon != -1 ? id.substring(colon + 1) : null;

                boolean isPose = propertyId.equals("pose") || propertyId.startsWith("pose_overlay");

                if (isPose)
                {
                    String targetBone = boneName;

                    if (targetBone == null)
                    {
                        if (editor instanceof UIPoseKeyframeFactory pose)
                        {
                            targetBone = pose.poseEditor.getCurrentBone();

                            if (targetBone == null || targetBone.isEmpty())
                            {
                                targetBone = pose.poseEditor.groups.list.getCurrentFirst();
                            }
                        }
                    }

                    /* If the ID includes a property path (e.g., formPath/pose or formPath/pose_overlayX),
                     * retain the form prefix to correctly position the bone in the renderer.*/
                    if (sheet.id.contains("/pose") || sheet.id.contains("/pose_overlay"))
                    {
                        int lastSlash = sheet.id.lastIndexOf('/');
                        String prefix = sheet.id.substring(0, lastSlash);

                        bone = targetBone == null || targetBone.isEmpty() ? prefix : prefix + "/" + targetBone;
                    }
                    else
                    {
                        bone = targetBone;
                    }

                    if (editor instanceof UIPoseKeyframeFactory pose)
                    {
                        orientation = pose.poseEditor.transform.getOrientation();
                    }
                    else if (editor instanceof UITransformKeyframeFactory transform)
                    {
                        orientation = transform.transform.getOrientation();
                    }
                }
                else if (propertyId.equals("transform") || propertyId.startsWith("transform_overlay"))
                {
                    int lastSlash = sheet.id.lastIndexOf('/');

                    bone = lastSlash >= 0 ? sheet.id.substring(0, lastSlash) : "";

                    if (editor instanceof UITransformKeyframeFactory transform)
                    {
                        orientation = transform.transform.getOrientation();
                    }
                }
                else if (propertyId.equals("illusion_transform") || propertyId.startsWith("illusion_transform_overlay"))
                {
                    int lastSlash = sheet.id.lastIndexOf('/');

                    bone = lastSlash >= 0 ? sheet.id.substring(0, lastSlash) : "";

                    if (editor instanceof UITransformKeyframeFactory transform)
                    {
                        orientation = transform.transform.getOrientation();
                    }
                }
                else if (propertyId.equals("anchor") && editor instanceof UIAnchorKeyframeFactory anchorFactory)
                {
                    int lastSlash = sheet.id.lastIndexOf('/');

                    bone = lastSlash >= 0 ? sheet.id.substring(0, lastSlash) : "";

                    orientation = anchorFactory.transform.getOrientation();
                }
            }
        }
        else if (editor instanceof UILookAtKeyframeFactory lookAtFactory)
        {
            bone = lookAtFactory.lookAtEditor.getCurrentBone();
        }
        else if (editor instanceof UIInverseKinematicsKeyframeFactory ikFactory)
        {
            bone = ikFactory.ikEditor.getCurrentBone();
        }

        if (bone != null)
        {
            return new Pair<>(bone, orientation);
        }

        return null;
    }

    public boolean isFormAnchorTrack()
    {
        if (!(this.editor instanceof UIAnchorKeyframeFactory))
        {
            return false;
        }

        UIKeyframeSheet sheet = this.getSheet(this.editor.getKeyframe());

        return sheet != null && sheet.property != null && "anchor".equals(sheet.id);
    }

    public boolean getAnchorLocal()
    {
        if (this.editor instanceof UIAnchorKeyframeFactory factory)
        {
            return factory.transform.isLocal();
        }

        return false;
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        KeyframeState state = new KeyframeState();

        state.extra = data.getMap("extra");

        for (BaseType type : data.getList("selection"))
        {
            state.selected.add(DataStorageUtils.intListFromData(type));
        }

        this.view.applyState(state);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        KeyframeState keyframeState = this.view.cacheState();
        ListType selection = new ListType();

        for (List<Integer> integers : keyframeState.selected)
        {
            selection.add(DataStorageUtils.intListToData(integers));
        }

        data.put("extra", keyframeState.extra);
        data.put("selection", selection);
    }
}
