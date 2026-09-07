package mchorse.bbs_mod.ui.framework.elements.input;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.colors.Colors;

import org.joml.Vector3d;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Transformation editor GUI
 * 
 * Must be exactly 190 by 70 (with extra 12 on top for labels)
 */
public abstract class UITransform extends UIElement
{
    public UITrackpad tx;
    public UITrackpad ty;
    public UITrackpad tz;
    public UITrackpad sx;
    public UITrackpad sy;
    public UITrackpad sz;
    public UITrackpad rx;
    public UITrackpad ry;
    public UITrackpad rz;
    public UITrackpad r2x;
    public UITrackpad r2y;
    public UITrackpad r2z;
    public UITrackpad px;
    public UITrackpad py;
    public UITrackpad pz;

    protected UIIcon iconT;
    protected UIIcon iconS;
    protected UIIcon iconR;
    protected UIIcon iconR2;
    protected UIIcon iconP;

    protected UIElement scaleRow;

    private boolean uniformDrag;
    private boolean uniformScale;

    public UITransform()
    {
        super();

        IKey raw = IKey.constant("%s (%s)");

        this.tx = new UITrackpad((value) -> this.internalSetT(value, Axis.X)).block().onlyNumbers();
        this.tx.tooltip(raw.format(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_X));
        this.tx.textbox.setColor(Colors.RED);
        this.ty = new UITrackpad((value) -> this.internalSetT(value, Axis.Y)).block().onlyNumbers();
        this.ty.tooltip(raw.format(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_Y));
        this.ty.textbox.setColor(Colors.GREEN);
        this.tz = new UITrackpad((value) -> this.internalSetT(value, Axis.Z)).block().onlyNumbers();
        this.tz.tooltip(raw.format(UIKeys.TRANSFORMS_TRANSLATE, UIKeys.GENERAL_Z));
        this.tz.textbox.setColor(Colors.BLUE);

        this.sx = new UITrackpad((value) ->
        {
            this.internalSetS(value, Axis.X);
            this.syncScale(value);
        }).disableCanceling();
        this.sx.onlyNumbers().tooltip(raw.format(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_X));
        this.sx.textbox.setColor(Colors.RED);
        this.sy = new UITrackpad((value) ->
        {
            this.internalSetS(value, Axis.Y);
            this.syncScale(value);
        }).disableCanceling();
        this.sy.onlyNumbers().tooltip(raw.format(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_Y));
        this.sy.textbox.setColor(Colors.GREEN);
        this.sz = new UITrackpad((value) ->
        {
            this.internalSetS(value, Axis.Z);
            this.syncScale(value);
        }).disableCanceling();
        this.sz.onlyNumbers().tooltip(raw.format(UIKeys.TRANSFORMS_SCALE, UIKeys.GENERAL_Z));
        this.sz.textbox.setColor(Colors.BLUE);

        this.rx = new UITrackpad((value) -> this.internalSetR(value, Axis.X)).degrees().onlyNumbers();
        this.rx.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_X));
        this.rx.textbox.setColor(Colors.RED);
        this.ry = new UITrackpad((value) -> this.internalSetR(value, Axis.Y)).degrees().onlyNumbers();
        this.ry.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_Y));
        this.ry.textbox.setColor(Colors.GREEN);
        this.rz = new UITrackpad((value) -> this.internalSetR(value, Axis.Z)).degrees().onlyNumbers();
        this.rz.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE, UIKeys.GENERAL_Z));
        this.rz.textbox.setColor(Colors.BLUE);

        this.r2x = new UITrackpad((value) -> this.internalSetR2(value, Axis.X)).degrees().onlyNumbers();
        this.r2x.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE2, UIKeys.GENERAL_X));
        this.r2x.textbox.setColor(Colors.RED);
        this.r2y = new UITrackpad((value) -> this.internalSetR2(value, Axis.Y)).degrees().onlyNumbers();
        this.r2y.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE2, UIKeys.GENERAL_Y));
        this.r2y.textbox.setColor(Colors.GREEN);
        this.r2z = new UITrackpad((value) -> this.internalSetR2(value, Axis.Z)).degrees().onlyNumbers();
        this.r2z.tooltip(raw.format(UIKeys.TRANSFORMS_ROTATE2, UIKeys.GENERAL_Z));
        this.r2z.textbox.setColor(Colors.BLUE);

        this.w(1F).column().stretch().vertical();

        this.iconT = new UIIcon(Icons.ALL_DIRECTIONS, null);
        this.iconS = new UIIcon(Icons.SCALE, (b) -> this.toggleUniformScale());
        this.iconS.tooltip(UIKeys.TRANSFORMS_UNIFORM_SCALE);
        this.iconR = new UIIcon(Icons.REFRESH, null);
        this.iconR2 = new UIIcon(Icons.REFRESH, null);
        this.iconP = new UIIcon(Icons.SPHERE, null);

        this.iconT.disabledColor = this.iconS.disabledColor = this.iconR.disabledColor = this.iconR2.disabledColor = this.iconP.disabledColor = Colors.WHITE;
        this.iconT.hoverColor = this.iconS.hoverColor = this.iconR.hoverColor = this.iconR2.hoverColor = this.iconP.hoverColor = Colors.WHITE;

        this.iconT.setEnabled(false);
        this.iconR.setEnabled(false);
        this.iconR2.setEnabled(false);
        this.iconP.setEnabled(false);

        this.add(UI.row(this.iconT, this.tx, this.ty, this.tz));
        this.add(this.scaleRow = UI.row(this.iconS, this.sx, this.sy, this.sz));
        this.add(UI.row(this.iconR, this.rx, this.ry, this.rz));
        this.add(UI.row(this.iconR2, this.r2x, this.r2y, this.r2z));
        
        IKey rawPivot = IKey.constant("%s (%s)");
        this.px = new UITrackpad((value) -> this.internalSetP(value, Axis.X)).block().onlyNumbers();
        this.px.tooltip(rawPivot.format(UIKeys.TRANSFORMS_PIVOT_TITLE, UIKeys.GENERAL_X));
        this.px.textbox.setColor(Colors.RED);
        this.py = new UITrackpad((value) -> this.internalSetP(value, Axis.Y)).block().onlyNumbers();
        this.py.tooltip(rawPivot.format(UIKeys.TRANSFORMS_PIVOT_TITLE, UIKeys.GENERAL_Y));
        this.py.textbox.setColor(Colors.GREEN);
        this.pz = new UITrackpad((value) -> this.internalSetP(value, Axis.Z)).block().onlyNumbers();
        this.pz.tooltip(rawPivot.format(UIKeys.TRANSFORMS_PIVOT_TITLE, UIKeys.GENERAL_Z));
        this.pz.textbox.setColor(Colors.BLUE);

        this.add(UI.row(this.iconP, this.px, this.py, this.pz));

        if (BBSSettings.disablePivotTransform.get())
        {
            this.iconP.removeFromParent();
            this.px.removeFromParent();
            this.py.removeFromParent();
            this.pz.removeFromParent();
        }

        this.context((menu) ->
        {
            menu.custom(new UITransformContextMenu(this, this.getClipboardTransforms()));
        });

        this.wh(190, 90);

        this.keys().register(Keys.COPY, this::copyTransformations).inside().label(UIKeys.TRANSFORMS_CONTEXT_COPY);
        this.keys().register(Keys.CUT, () ->
        {
            this.copyTransformations();
            this.reset();
            UIContext context = this.getContext();
            if (context != null) context.notifyInfo(UIKeys.GENERAL_CUT);
            UIUtils.playClick();
        }).inside().label(UIKeys.GENERAL_CUT);
        this.keys().register(Keys.PASTE, () ->
        {
            ListType transforms = Window.getClipboardList();

            if (transforms != null && transforms.size() < 12)
            {
                transforms = null;
            }

            if (transforms != null)
            {
                this.pasteAll(transforms);
            }
        }).inside().label(UIKeys.TRANSFORMS_CONTEXT_PASTE);
    }

    protected void toggleUniformScale()
    {
        this.uniformScale = !this.uniformScale;

        this.scaleRow.removeAll();

        if (this.uniformScale)
        {
            this.scaleRow.add(this.iconS, this.sx);
        }
        else
        {
            this.scaleRow.add(this.iconS, this.sx, this.sy, this.sz);
        }

        UIElement parentContainer = this.getParentContainer();

        if (parentContainer != null)
        {
            parentContainer.resize();
        }
    }

    protected boolean isUniformScale()
    {
        return this.uniformDrag || Window.isKeyPressed(GLFW.GLFW_KEY_SPACE);
    }

    private void syncScale(double value)
    {
        if (this.isUniformScale())
        {
            this.fillS(value, value, value);
            this.setS(null, value, value, value);
        }
    }

    public void fillSetT(double x, double y, double z)
    {
        this.fillT(x, y, z);
        this.setT(null, x, y, z);
    }

    public void fillSetS(double x, double y, double z)
    {
        this.fillS(x, y, z);
        this.setS(null, x, y, z);
    }

    public void fillSetR(double x, double y, double z)
    {
        this.fillR(x, y, z);
        this.setR(null, x, y, z);
    }

    public void fillSetR2(double x, double y, double z)
    {
        this.fillR2(x, y, z);
        this.setR2(null, x, y, z);
    }

    public void fillSetP(double x, double y, double z)
    {
        this.fillP(x, y, z);
        this.setP(null, x, y, z);
    }

    public void fillT(double x, double y, double z)
    {
        this.tx.setValue(x);
        this.ty.setValue(y);
        this.tz.setValue(z);
    }

    public void fillS(double x, double y, double z)
    {
        this.sx.setValue(x);
        this.sy.setValue(y);
        this.sz.setValue(z);
    }

    public void fillR(double x, double y, double z)
    {
        this.rx.setValue(x);
        this.ry.setValue(y);
        this.rz.setValue(z);
    }

    public void fillR2(double x, double y, double z)
    {
        this.r2x.setValue(x);
        this.r2y.setValue(y);
        this.r2z.setValue(z);
    }

    public void fillP(double x, double y, double z)
    {
        this.px.setValue(x);
        this.py.setValue(y);
        this.pz.setValue(z);
    }
    
    protected void internalSetT(double x, Axis axis)
    {
        try
        {
            this.setT(axis,
                axis == Axis.X ? x : this.tx.value,
                axis == Axis.Y ? x : this.ty.value,
                axis == Axis.Z ? x : this.tz.value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void internalSetS(double x, Axis axis)
    {
        try
        {
            if (this.uniformScale && axis == Axis.X)
            {
                this.setS(axis, x, x, x);
                this.sy.setValue(x);
                this.sz.setValue(x);

                return;
            }

            this.setS(axis,
                axis == Axis.X ? x : this.sx.value,
                axis == Axis.Y ? x : this.sy.value,
                axis == Axis.Z ? x : this.sz.value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void internalSetR(double x, Axis axis)
    {
        try
        {
            this.setR(axis,
                axis == Axis.X ? x : this.rx.value,
                axis == Axis.Y ? x : this.ry.value,
                axis == Axis.Z ? x : this.rz.value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void internalSetR2(double x, Axis axis)
    {
        try
        {
            this.setR2(axis,
                axis == Axis.X ? x : this.r2x.value,
                axis == Axis.Y ? x : this.r2y.value,
                axis == Axis.Z ? x : this.r2z.value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void internalSetP(double x, Axis axis)
    {
        try
        {
            this.setP(axis,
                axis == Axis.X ? x : this.px.value,
                axis == Axis.Y ? x : this.py.value,
                axis == Axis.Z ? x : this.pz.value
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    protected void addGeneralTabActions(ContextMenuManager menu, ListType transforms)
    {}

    private ListType getClipboardTransforms()
    {
        ListType transforms = Window.getClipboardList();

        if (transforms != null && transforms.size() < 12)
        {
            transforms = null;
        }

        return transforms;
    }

    private void fillGeneralTabActions(ContextMenuManager menu, ListType transforms)
    {
        this.addGeneralTabActions(menu, transforms);
        menu.action(Icons.COPY, UIKeys.TRANSFORMS_CONTEXT_COPY, this::copyTransformations);

        if (transforms != null)
        {
            menu.action(Icons.PASTE, UIKeys.TRANSFORMS_CONTEXT_PASTE, () -> this.pasteAll(transforms));
        }

        menu.action(Icons.CLOSE, UIKeys.TRANSFORMS_CONTEXT_RESET, this::reset);
    }

    private void fillPastesTabActions(ContextMenuManager menu, ListType transforms)
    {
        if (transforms == null)
        {
            return;
        }

        menu.action(Icons.PASTE, UIKeys.TRANSFORMS_CONTEXT_PASTE, () -> this.pasteAll(transforms));
        menu.action(Icons.ALL_DIRECTIONS, UIKeys.TRANSFORMS_CONTEXT_PASTE_TRANSLATION, () -> this.pasteTranslation(this.getVector(transforms, 0)));
        menu.action(Icons.MAXIMIZE, UIKeys.TRANSFORMS_CONTEXT_PASTE_SCALE, () -> this.pasteScale(this.getVector(transforms, 3)));
        menu.action(Icons.REFRESH, UIKeys.TRANSFORMS_CONTEXT_PASTE_ROTATION, () -> this.pasteRotation(this.getVector(transforms, 6)));
        menu.action(Icons.REFRESH, UIKeys.TRANSFORMS_CONTEXT_PASTE_ROTATION2, () -> this.pasteRotation2(this.getVector(transforms, 9)));
    }

    private void fillResetsTabActions(ContextMenuManager menu)
    {
        menu.action(Icons.CLOSE, UIKeys.TRANSFORMS_CONTEXT_RESET, this::reset);
        menu.action(Icons.ALL_DIRECTIONS, UIKeys.TRANSFORMS_CONTEXT_RESET_TRANSLATION, this::resetTranslation);
        menu.action(Icons.MAXIMIZE, UIKeys.TRANSFORMS_CONTEXT_RESET_SCALE, this::resetScale);
        menu.action(Icons.REFRESH, UIKeys.TRANSFORMS_CONTEXT_RESET_ROTATION, this::resetRotation);
        menu.action(Icons.REFRESH, UIKeys.TRANSFORMS_CONTEXT_RESET_ROTATION2, this::resetRotation2);
    }

    private void fillInvertsTabActions(ContextMenuManager menu)
    {
        menu.action(Icons.REFRESH, UIKeys.TRANSFORMS_CONTEXT_INVERT, this::invert);
        menu.action(Icons.ALL_DIRECTIONS, UIKeys.TRANSFORMS_CONTEXT_INVERT_TRANSLATION, this::invertTranslation);
        menu.action(Icons.MAXIMIZE, UIKeys.TRANSFORMS_CONTEXT_INVERT_SCALE, this::invertScale);
        menu.action(Icons.REFRESH, UIKeys.TRANSFORMS_CONTEXT_INVERT_ROTATION, this::invertRotation);
        menu.action(Icons.REFRESH, UIKeys.TRANSFORMS_CONTEXT_INVERT_ROTATION2, this::invertRotation2);
    }

    private List<ContextAction> buildGeneralTabActions(ListType transforms)
    {
        ContextMenuManager menu = new ContextMenuManager();

        this.fillGeneralTabActions(menu, transforms);

        return new ArrayList<>(menu.actions);
    }

    private List<ContextAction> buildPastesTabActions(ListType transforms)
    {
        ContextMenuManager menu = new ContextMenuManager();

        this.fillPastesTabActions(menu, transforms);

        return new ArrayList<>(menu.actions);
    }

    private List<ContextAction> buildResetsTabActions()
    {
        ContextMenuManager menu = new ContextMenuManager();

        this.fillResetsTabActions(menu);

        return new ArrayList<>(menu.actions);
    }

    private List<ContextAction> buildInvertsTabActions()
    {
        ContextMenuManager menu = new ContextMenuManager();

        this.fillInvertsTabActions(menu);

        return new ArrayList<>(menu.actions);
    }

    public abstract void setT(Axis axis, double x, double y, double z);

    public abstract void setS(Axis axis, double x, double y, double z);

    public abstract void setR(Axis axis, double x, double y, double z);

    public abstract void setR2(Axis axis, double x, double y, double z);

    public abstract void setP(Axis axis, double x, double y, double z);

    private void copyTransformations()
    {
        ListType list = new ListType();

        list.addDouble(this.tx.value);
        list.addDouble(this.ty.value);
        list.addDouble(this.tz.value);
        list.addDouble(this.sx.value);
        list.addDouble(this.sy.value);
        list.addDouble(this.sz.value);
        list.addDouble(this.rx.value);
        list.addDouble(this.ry.value);
        list.addDouble(this.rz.value);
        list.addDouble(this.r2x.value);
        list.addDouble(this.r2y.value);
        list.addDouble(this.r2z.value);
        list.addDouble(this.px.value);
        list.addDouble(this.py.value);
        list.addDouble(this.pz.value);

        Window.setClipboard(list);
    }

    public void pasteAll(ListType list)
    {
        this.pasteTranslation(this.getVector(list, 0));
        this.pasteScale(this.getVector(list, 3));
        this.pasteRotation(this.getVector(list, 6));
        this.pasteRotation2(this.getVector(list, 9));
        this.pastePivot(this.getVector(list, 12));
    }

    public void pasteTranslation(Vector3d translation)
    {
        this.fillSetT(translation.x, translation.y, translation.z);
    }

    public void pasteScale(Vector3d scale)
    {
        this.fillSetS(scale.x, scale.y, scale.z);
    }

    public void pasteRotation(Vector3d rotation)
    {
        this.fillSetR(rotation.x, rotation.y, rotation.z);
    }

    public void pasteRotation2(Vector3d rotation)
    {
        this.fillSetR2(rotation.x, rotation.y, rotation.z);
    }

    public void pastePivot(Vector3d pivot)
    {
        this.fillSetP(pivot.x, pivot.y, pivot.z);
    }

    private Vector3d getVector(ListType list, int offset)
    {
        Vector3d result = new Vector3d();

        if (list.get(offset).isNumeric() && list.get(offset + 1).isNumeric() && list.get(offset + 2).isNumeric())
        {
            result.x = list.get(offset).asNumeric().doubleValue();
            result.y = list.get(offset + 1).asNumeric().doubleValue();
            result.z = list.get(offset + 2).asNumeric().doubleValue();
        }

        if (offset == 0)
        {
            result.x *= Window.isShiftPressed() ? -1 : 1;
        }

        if (offset >= 6)
        {
            result.y *= Window.isShiftPressed() ? -1 : 1;
            result.z *= Window.isShiftPressed() ? -1 : 1;
        }

        return result;
    }

    protected void reset()
    {
        this.fillSetT(0, 0, 0);
        this.fillSetS(1, 1, 1);
        this.fillSetR(0, 0, 0);
        this.fillSetR2(0, 0, 0);

        if (!BBSSettings.disablePivotTransform.get())
        {
            this.fillSetP(0, 0, 0);
        }
    }

    protected void resetTranslation()
    {
        this.fillSetT(0, 0, 0);
    }

    protected void resetScale()
    {
        this.fillSetS(1, 1, 1);
    }

    protected void resetRotation()
    {
        this.fillSetR(0, 0, 0);
        this.fillSetR2(0, 0, 0);
    }

    protected void resetRotation2()
    {
        this.fillSetR2(0, 0, 0);
    }

    protected void invert()
    {
        this.fillSetT(-this.tx.value, -this.ty.value, -this.tz.value);
        this.fillSetS(-this.sx.value, -this.sy.value, -this.sz.value);
        this.fillSetR(-this.rx.value, -this.ry.value, -this.rz.value);
        this.fillSetR2(-this.r2x.value, -this.r2y.value, -this.r2z.value);
    }

    protected void invertTranslation()
    {
        this.fillSetT(-this.tx.value, -this.ty.value, -this.tz.value);
    }

    protected void invertScale()
    {
        this.fillSetS(-this.sx.value, -this.sy.value, -this.sz.value);
    }

    protected void invertRotation()
    {
        this.fillSetR(-this.rx.value, -this.ry.value, -this.rz.value);
    }

    protected void invertRotation2()
    {
        this.fillSetR2(-this.r2x.value, -this.r2y.value, -this.r2z.value);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.sx.area.isInside(context) || this.sy.area.isInside(context) || this.sz.area.isInside(context))
        {
            if (context.mouseButton == 1 && (this.sx.isDragging() || this.sy.isDragging() || this.sz.isDragging()))
            {
                this.uniformDrag = true;

                return true;
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (context.mouseButton == 1)
        {
            this.uniformDrag = false;
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.sx.isDragging() || this.sy.isDragging() || this.sz.isDragging())
        {
            if (context.isHeld(GLFW.GLFW_KEY_SPACE))
            {
                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    private enum TransformContextTab
    {
        GENERAL,
        PASTES,
        RESETS,
        INVERTS
    }

    private static class UITransformContextMenu extends UISimpleContextMenu
    {
        private final UIElement tabs;
        private final UIElement separator;
        private final UIButton general;
        private final UIButton pastes;
        private final UIButton resets;
        private final UIButton inverts;
        private final List<ContextAction> generalActions;
        private final List<ContextAction> pastesActions;
        private final List<ContextAction> resetsActions;
        private final List<ContextAction> invertsActions;
        private TransformContextTab tab = TransformContextTab.GENERAL;

        public UITransformContextMenu(UITransform transform, ListType transforms)
        {
            this.generalActions = transform.buildGeneralTabActions(transforms);
            this.pastesActions = transform.buildPastesTabActions(transforms);
            this.resetsActions = transform.buildResetsTabActions();
            this.invertsActions = transform.buildInvertsTabActions();
            this.general = new UITabButton(IKey.EMPTY, UIKeys.MODELS_GENERAL, Icons.SETTINGS, (b) -> this.setTab(TransformContextTab.GENERAL));
            this.pastes = new UITabButton(IKey.EMPTY, UIKeys.TRANSFORMS_CONTEXT_PASTES_OPTIONS, Icons.PASTE, (b) -> this.setTab(TransformContextTab.PASTES));
            this.resets = new UITabButton(IKey.EMPTY, UIKeys.TRANSFORMS_CONTEXT_RESETS_OPTIONS, Icons.CLOSE, (b) -> this.setTab(TransformContextTab.RESETS));
            this.inverts = new UITabButton(IKey.EMPTY, UIKeys.TRANSFORMS_CONTEXT_INVERTS_OPTIONS, Icons.CONVERT, (b) -> this.setTab(TransformContextTab.INVERTS));
            ((UITabButton) this.inverts).noSeparator();
            this.tabs = UI.row(0, this.general, this.pastes, this.resets, this.inverts);
            this.separator = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x44ffffff);
                }
            };

            this.tabs.relative(this).w(1F).h(20).row(0).resize();
            this.separator.relative(this).xy(0, 20).w(1F).h(1);
            this.actions.relative(this).xy(0, 21).w(1F).h(1F, -21);
            this.add(this.tabs, this.separator);
            this.pastes.setEnabled(!this.pastesActions.isEmpty());
            this.setTab(TransformContextTab.GENERAL);
        }

        private void setTab(TransformContextTab tab)
        {
            this.tab = tab;
            ((UITabButton) this.general).setActive(tab == TransformContextTab.GENERAL);
            ((UITabButton) this.pastes).setActive(tab == TransformContextTab.PASTES);
            ((UITabButton) this.resets).setActive(tab == TransformContextTab.RESETS);
            ((UITabButton) this.inverts).setActive(tab == TransformContextTab.INVERTS);

            if (tab == TransformContextTab.GENERAL)
            {
                this.actions.setList(new ArrayList<>(this.generalActions));
            }
            else if (tab == TransformContextTab.PASTES)
            {
                this.actions.setList(new ArrayList<>(this.pastesActions));
            }
            else if (tab == TransformContextTab.RESETS)
            {
                this.actions.setList(new ArrayList<>(this.resetsActions));
            }
            else
            {
                this.actions.setList(new ArrayList<>(this.invertsActions));
            }

            UIContext context = this.getContext();

            if (context != null)
            {
                this.w(this.calculateWidth(context));
                this.h(this.calculateHeight());
                this.bounds(context.menu.overlay, 5);
                this.resize();
            }
        }

        @Override
        public void setMouse(UIContext context)
        {
            int w = this.calculateWidth(context);
            int h = this.calculateHeight();

            this.xy(context.mouseX(), context.mouseY()).w(w).h(h).bounds(context.menu.overlay, 5);
            this.resize();
        }

        private int calculateWidth(UIContext context)
        {
            int w = 120;

            for (ContextAction action : this.generalActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));
            for (ContextAction action : this.pastesActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));
            for (ContextAction action : this.resetsActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));
            for (ContextAction action : this.invertsActions) w = Math.max(w, action.getWidth(context.batcher.getFont()));

            return w % 4 == 0 ? w : w + (4 - w % 4);
        }

        private List<ContextAction> getActions(TransformContextTab tab)
        {
            if (tab == TransformContextTab.GENERAL)
            {
                return this.generalActions;
            }
            else if (tab == TransformContextTab.PASTES)
            {
                return this.pastesActions;
            }
            else if (tab == TransformContextTab.RESETS)
            {
                return this.resetsActions;
            }

            return this.invertsActions;
        }

        private int calculateHeight()
        {
            int actions = 1;

            if (this.tab == TransformContextTab.GENERAL)
            {
                actions = this.generalActions.size();
            }
            else if (this.tab == TransformContextTab.PASTES)
            {
                actions = this.pastesActions.size();
            }
            else if (this.tab == TransformContextTab.RESETS)
            {
                actions = this.resetsActions.size();
            }
            else if (this.tab == TransformContextTab.INVERTS)
            {
                actions = this.invertsActions.size();
            }

            actions = Math.max(actions, 1);

            return 21 + actions * this.actions.scroll.scrollItemSize;
        }

        private static class UITabButton extends UIButton
        {
            private final Icon icon;
            private final IKey tooltip;
            private boolean active;
            private boolean noSeparator;

            public UITabButton(IKey label, IKey tooltip, Icon icon, Consumer<UIButton> callback)
            {
                super(label, callback);
                this.tooltip = tooltip;
                this.icon = icon;
                this.tooltip(this.tooltip);
            }

            public void noSeparator()
            {
                this.noSeparator = true;
            }

            public void setActive(boolean active)
            {
                this.active = active;
            }

            @Override
            protected void renderSkin(UIContext context)
            {
                boolean enabled = this.isEnabled();
                int primary = BBSSettings.primaryColor.get();
                int color = this.active ? primary : 0;
                int iconColor = this.active ? Colors.WHITE : 0xddffffff;

                if (!enabled)
                {
                    iconColor = 0x80404040;
                }
                else if (this.hover)
                {
                    color = this.active ? Colors.mulRGB(primary, 0.9F) : Colors.A25;
                    iconColor = Colors.WHITE;
                }

                if (color != 0)
                {
                    this.area.render(context.batcher, this.active ? (color | Colors.A100) : color);
                }

                if (!this.noSeparator)
                {
                    context.batcher.box(this.area.ex() - 1, this.area.y + 2, this.area.ex(), this.area.ey() - 2, 0x22ffffff);
                }

                context.batcher.icon(this.icon, iconColor, this.area.mx(), this.area.my(), 0.5F, 0.5F);
            }
        }
    }
}
