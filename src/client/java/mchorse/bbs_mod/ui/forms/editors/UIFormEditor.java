package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.BodyPartManager;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.FluidForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.ShapeForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.ValueFormEditorGizmoToolbar;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.ICursor;
import mchorse.bbs_mod.ui.film.controller.UIGizmoSizeContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIGizmoThicknessContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIGizmoTranslateSpeedContextMenu;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.editors.forms.UIAnchorForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIBillboardForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIBlockForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIExtrudedForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIFluidForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIFramebufferForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIItemForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UILabelForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UILightForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIMobForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIParticleForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIShapeForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIStructureForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UITrailForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIVanillaParticleForm;
import mchorse.bbs_mod.ui.forms.editors.states.UIAnimationStatesOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoMatrixUtils;
import mchorse.bbs_mod.ui.utils.gizmo.TransformOrientation;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.ui.utils.presets.UIPresetContextMenu;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.presets.PresetManager;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class UIFormEditor extends UIElement implements IUIFormList, ICursor
{
    public static Map<Class, Supplier<UIForm>> panels = new HashMap<>();
    public static Function<UIFormEditor, UIPickableFormRenderer> rendererFactory = UIPickableFormRenderer::new;

    private static float treeWidth = 0F;
    private static boolean TOGGLED = true;

    /* Palette for picking a form for body parts */
    public UIFormPalette palette;

    /* Main form editor element */
    public UIElement formEditor;
    public UIPickableFormRenderer renderer;
    public UIForm editor;

    /* States editor */
    public UIElement statesEditor;
    public UIAnimationStateEditor statesKeyframes;
    public UIIcon openStates;
    public UIIcon plause;
    public UIIcon shiftDuration;

    /* Model settings editor */
    public UIFormModelEditor modelSettingsEditor;

    /* Forms sidebar */
    public UIElement forms;
    public UIForms formsList;
    public UIBodyPartEditor bodyPartEditor;

    /* Sidebar icons */
    public UIElement icons;
    public UIIcon finish;
    public UIIcon toggleSidebar;
    public UIIcon openStateEditor;
    public UIIcon openModelEditor;

    /* Gizmo mode toolbar (mirrors the film viewport's transform-mode buttons, plus a toggle
     * that routes gizmo drags into the selected body part's transform instead of the bone pose) */
    public UIElement gizmoToolbar;
    public UIIcon gizmoBodyPart;
    public UIIcon gizmoTransform;
    public UIIcon gizmoMove;
    public UIIcon gizmoScale;
    public UIIcon gizmoRotate;
    public UIIcon gizmoCombined;
    public UIIcon gizmoTop;
    public UIIcon gizmoVisualSize;
    public UIIcon gizmoThickness;
    public UIIcon gizmoTranslateSpeed;

    private final Map<String, UIIcon> gizmoButtonMap = new HashMap<>();

    private boolean gizmoTargetsBodyPart;
    private boolean gizmoTargetsTransform;

    public Form form;

    private Consumer<Form> callback;
    private UICopyPasteController copyPasteController;
    private UIFormUndoHandler undoHandler;

    private int lastTick;
    private int cursor;
    private boolean playing;

    static
    {
        register(BillboardForm.class, UIBillboardForm::new);
        register(FluidForm.class, UIFluidForm::new);
        register(ExtrudedForm.class, UIExtrudedForm::new);
        register(LabelForm.class, UILabelForm::new);
        register(ModelForm.class, UIModelForm::new);
        register(ParticleForm.class, UIParticleForm::new);
        register(BlockForm.class, UIBlockForm::new);
        register(ItemForm.class, UIItemForm::new);
        register(AnchorForm.class, UIAnchorForm::new);
        register(MobForm.class, UIMobForm::new);
        register(VanillaParticleForm.class, UIVanillaParticleForm::new);
        register(TrailForm.class, UITrailForm::new);
        register(StructureForm.class, UIStructureForm::new);
        register(ShapeForm.class, UIShapeForm::new);
        register(LightForm.class, UILightForm::new);
        register(FramebufferForm.class, UIFramebufferForm::new);
    }

    public static void register(Class clazz, Supplier<UIForm> supplier)
    {
        panels.put(clazz, supplier);
    }

    public static UIForm createPanel(Form form)
    {
        if (form == null)
        {
            return null;
        }

        Supplier<UIForm> supplier = panels.get(form.getClass());

        return supplier == null ? null : supplier.get();
    }

    public UIFormEditor(UIFormPalette palette)
    {
        this.palette = palette;

        if (BBSSettings.uiLayoutPreferences != null)
        {
            treeWidth = BBSSettings.uiLayoutPreferences.getFormTreeWidth();
        }

        this.undoHandler = new UIFormUndoHandler(this);
        this.copyPasteController = new UICopyPasteController(PresetManager.BODY_PARTS, "_FormEditorBodyPart")
            .supplier(this::copyBodyPart)
            .consumer(this::pasteBodyPart)
            .canCopy(() ->
            {
                for (UIForms.FormEntry entry : this.formsList.getCurrent())
                {
                    if (entry.part != null)
                    {
                        return true;
                    }
                }

                return false;
            })
            .canPaste(() ->
            {
                UIForms.FormEntry current = this.formsList.getCurrentFirst();

                return current != null && current.getForm() != null;
            });

        this.forms = new UIElement();
        this.forms.relative(this).x(20).w(treeWidth).minW(140).h(1F);

        this.formsList = new UIForms((l) ->
        {
            if (!l.isEmpty())
            {
                this.pickForm(l.get(0));
            }
        });
        this.formsList.setReorderCallback(this::refillState);
        this.formsList.relative(this.forms).w(1F).h(0.5F);
        this.formsList.context(this::createFormContextMenu);
        this.bodyPartEditor = new UIBodyPartEditor(this);
        this.bodyPartEditor.relative(this.forms).w(1F).y(0.5F).h(0.5F);

        this.formEditor = new UIElement();
        this.formEditor.full(this);

        this.statesEditor = new UIElement();
        this.statesEditor.full(this);
        this.statesEditor.setVisible(false);
        this.statesKeyframes = new UIAnimationStateEditor(this);
        this.statesKeyframes.relative(this.statesEditor).x(20).y(1F).w(1F, -20).h(BBSSettings.editorLayoutSettings.getStateEditorSizeV()).anchorY(1F);

        this.modelSettingsEditor = new UIFormModelEditor(this);
        this.modelSettingsEditor.full(this);
        this.modelSettingsEditor.setVisible(false);

        this.openStates = new UIIcon(Icons.MORE, (b) ->
        {
            UIAnimationStatesOverlayPanel panel = new UIAnimationStatesOverlayPanel(this.form.states, this.statesKeyframes.getState(), (state) -> this.pickState(state));

            panel.setUndoId("animation_states_overlay_panel");
            UIOverlay.addOverlay(this.getContext(), panel, 280, 0.5F).eventPropagataion(EventPropagation.PASS);
        });
        this.openStates.relative(this.statesEditor);
        this.openStates.tooltip(UIKeys.FORMS_EDITOR_STATES_OPEN, Direction.RIGHT);
        this.plause = new UIIcon(() -> this.playing ? Icons.PAUSE : Icons.PLAY, (b) -> this.plause());
        this.plause.relative(this.openStates).y(1F);
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE, Direction.RIGHT);
        this.shiftDuration = new UIIcon(Icons.SHIFT_TO, (b) ->
        {
            AnimationState state = this.statesKeyframes.getState();

            if (state != null)
            {
                state.duration.set(this.cursor);
            }
        });
        this.shiftDuration.relative(this.plause).y(1F);
        this.shiftDuration.tooltip(UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT_DURATION, Direction.RIGHT);
        this.shiftDuration.keys().register(Keys.CLIP_SHIFT, () -> this.shiftDuration.clickItself());

        this.renderer = rendererFactory.apply(this);
        this.renderer.setRenderForm(() -> this.modelSettingsEditor == null || !this.modelSettingsEditor.isVisible());
        this.renderer.updatable();
        this.renderer.full(this);

        this.finish = new UIIcon(Icons.IN, (b) -> this.palette.exit());
        this.finish.tooltip(UIKeys.FORMS_EDITOR_FINISH, Direction.RIGHT).relative(this.formEditor).xy(0, 1F).anchorY(1F);
        this.toggleSidebar = new UIIcon(() -> this.forms.isVisible() ? Icons.LEFTLOAD : Icons.RIGHTLOAD, (b) ->
        {
            this.toggleSidebar();

            TOGGLED = !TOGGLED;
        });
        this.toggleSidebar.tooltip(UIKeys.FORMS_EDITOR_TOGGLE_TREE, Direction.RIGHT);
        this.openStateEditor = new UIIcon(Icons.GALLERY, (b) -> this.toggleStateEditor());
        this.openStateEditor.tooltip(UIKeys.FORMS_EDITOR_STATES_TOGGLE, Direction.RIGHT);
        this.openModelEditor = new UIIcon(Icons.PLAYER, (b) -> this.toggleModelEditor());
        this.openModelEditor.tooltip(UIKeys.MODELS_TITLE, Direction.RIGHT);
        this.openModelEditor.setEnabled(false);
        this.icons = UI.column(this.openModelEditor, this.openStateEditor, this.toggleSidebar, this.finish);
        this.icons.relative(this).y(1F).w(20).anchorY(1F);

        UIRenderable background = new UIRenderable((context) ->
        {
            if (this.forms.isVisible())
            {
                this.forms.area.render(context.batcher, Colors.A50);
            }
        });

        UIRenderable backgroundStates = new UIRenderable((context) ->
        {
            context.batcher.box(this.area.x, this.area.y, this.area.x + 20, this.area.ey(), Colors.A100);
        });

        UIDraggable draggable = new UIDraggable((context) ->
        {
            int diff = context.mouseX - this.forms.area.x;
            float f = diff / (float) this.area.w;

            treeWidth = MathUtils.clamp(f, 0F, 0.5F);

            this.forms.w(treeWidth).resize();
        }).dragEnd(() ->
        {
            if (BBSSettings.uiLayoutPreferences != null)
            {
                BBSSettings.uiLayoutPreferences.setFormTreeWidth(treeWidth);
            }
        });

        draggable.relative(this.forms).x(1F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        /* Gizmo mode toolbar */
        this.gizmoBodyPart = new UIIcon(Icons.LIMB, (b) ->
        {
            this.gizmoTargetsBodyPart = !this.gizmoTargetsBodyPart;

            if (this.gizmoTargetsBodyPart)
            {
                this.gizmoTargetsTransform = false;
            }

            UIUtils.playClick();
        });
        this.gizmoBodyPart.tooltip(UIKeys.FILM_GIZMO_BODY_PART);
        this.gizmoBodyPart.activeBackground(Colors.A50 | Colors.BLUE);
        this.gizmoTransform = new UIIcon(Icons.GEAR, (b) ->
        {
            this.gizmoTargetsTransform = !this.gizmoTargetsTransform;

            if (this.gizmoTargetsTransform)
            {
                this.enableFormTransformGizmo();
            }
            else
            {
                this.disableFormTransformGizmo();

                if (this.editor instanceof UIModelForm modelForm)
                {
                    modelForm.showPosePanel();
                }
            }

            UIUtils.playClick();
        });
        this.gizmoTransform.tooltip(UIKeys.FILM_GIZMO_TRANSFORM);
        this.gizmoTransform.activeBackground(Colors.A50 | Colors.BLUE);
        this.gizmoMove = this.createGizmoModeButton(Icons.ALL_DIRECTIONS, Gizmo.Mode.TRANSLATE, UIKeys.FILM_GIZMO_MOVE);
        this.gizmoScale = this.createGizmoModeButton(Icons.SCALE, Gizmo.Mode.SCALE, UIKeys.FILM_GIZMO_SCALE);
        this.gizmoRotate = this.createGizmoModeButton(Icons.ARC, Gizmo.Mode.ROTATE, UIKeys.FILM_GIZMO_ROTATE);
        this.gizmoCombined = this.createGizmoModeButton(Icons.SHAPES, Gizmo.Mode.COMBINED, UIKeys.FILM_GIZMO_COMBINED);
        this.gizmoTop = this.createGizmoModeButton(Icons.SPHERE, Gizmo.Mode.ROTATE, UIKeys.FILM_GIZMO_TOP);

        this.gizmoVisualSize = new UIIcon(Icons.MAXIMIZE, (b) ->
        {
            if (this.getContext() != null)
            {
                this.getContext().replaceContextMenu(new UIGizmoSizeContextMenu());
            }
        });
        this.gizmoVisualSize.tooltip(UIKeys.FILM_GIZMO_SIZE);

        this.gizmoThickness = new UIIcon(Icons.LINE, (b) ->
        {
            if (this.getContext() != null)
            {
                this.getContext().replaceContextMenu(new UIGizmoThicknessContextMenu());
            }
        });
        this.gizmoThickness.tooltip(UIKeys.FILM_GIZMO_THICKNESS);

        this.gizmoTranslateSpeed = new UIIcon(Icons.FORWARD, (b) ->
        {
            if (this.getContext() != null)
            {
                this.getContext().replaceContextMenu(new UIGizmoTranslateSpeedContextMenu());
            }
        });
        this.gizmoTranslateSpeed.tooltip(UIKeys.FILM_GIZMO_TRANSLATE_SPEED);

        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.BODY_PART, this.gizmoBodyPart);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.TRANSFORM, this.gizmoTransform);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.MOVE, this.gizmoMove);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.SCALE, this.gizmoScale);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.ROTATE, this.gizmoRotate);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.COMBINED, this.gizmoCombined);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.TOP, this.gizmoTop);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.SIZE, this.gizmoVisualSize);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.THICKNESS, this.gizmoThickness);
        this.gizmoButtonMap.put(ValueFormEditorGizmoToolbar.TRANSLATE_SPEED, this.gizmoTranslateSpeed);

        UIRenderable toolbarBackground = new UIRenderable((context) ->
        {
            this.gizmoToolbar.area.render(context.batcher, Colors.A75);

            Gizmo.Mode gizmoMode = Gizmo.INSTANCE.getMode();

            this.gizmoBodyPart.active(this.gizmoTargetsBodyPart);
            this.gizmoTransform.active(this.gizmoTargetsTransform);
            this.gizmoMove.active(gizmoMode == Gizmo.Mode.TRANSLATE);
            this.gizmoScale.active(gizmoMode == Gizmo.Mode.SCALE);
            this.gizmoRotate.active(gizmoMode == Gizmo.Mode.ROTATE);
            this.gizmoCombined.active(gizmoMode == Gizmo.Mode.COMBINED);
            this.gizmoTop.active(gizmoMode == Gizmo.Mode.ROTATE);
        });

        this.gizmoToolbar = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 1 && this.area.isInside(context))
                {
                    UIFormEditor.this.openGizmoToolbarCustomizer();
                    UIUtils.playClick();

                    return true;
                }

                return super.subMouseClicked(context);
            }
        };
        this.gizmoToolbar.row(0);
        this.gizmoToolbar.relative(this).x(0.5F).y(4).wh(160, 20).anchorX(0.5F);
        this.rebuildGizmoToolbar();
        BBSSettings.editorFormGizmoToolbar.postCallback((v, f) -> this.rebuildGizmoToolbar());

        this.forms.add(background, this.formsList, this.bodyPartEditor, draggable);
        this.formEditor.add(this.forms);
        this.statesEditor.add(backgroundStates, this.openStates, this.plause, this.shiftDuration, this.statesKeyframes);
        this.add(this.renderer, this.formEditor, this.statesEditor, this.modelSettingsEditor, toolbarBackground, this.gizmoToolbar, this.icons);

        this.keys().register(Keys.UNDO, this::undo);
        this.keys().register(Keys.REDO, this::redo);
        this.keys().register(Keys.DELETE, () ->
        {
            if (this.hasSelectedBodyParts())
            {
                this.removeBodyPart();
            }
        });
        this.keys().register(Keys.FORMS_OPEN_STATES_EDITOR, () ->
        {
            if (!this.statesEditor.isVisible())
            {
                this.toggleStateEditor();
            }

            if (!UIOverlay.has(this.getContext()))
            {
                this.openStates.clickItself();
            }
        });
        this.plause.keys().register(Keys.PLAUSE, () ->
        {
            this.plause();
            UIUtils.playClick();
        });

        this.setUndoId("form_editor");
    }

    public boolean clickViewport(UIContext context, StencilFormFramebuffer stencil)
    {
        if (this.statesEditor.isVisible() && this.statesKeyframes.clickViewport(context, stencil))
        {
            return true;
        }

        UIPropTransform editableTransform = this.getGizmoDragTransform();

        this.renderer.setPoseBoneGizmoDrag(this.isPoseBoneGizmo(editableTransform));

        if (context.mouseButton == 0 && this.renderer.getGizmoController().tryStartHandleDrag(context, editableTransform))
        {
            return true;
        }

        if (stencil.hasPicked() && context.mouseButton == 0)
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null)
            {
                this.pickFormFromRenderer(pair);

                return true;
            }
        }

        return false;
    }

    /** Completes a pending trackball press: click-through selects the bone/form under the
     *  sphere; otherwise just stops any active gizmo drag. */
    public void finishGizmoPendingClick()
    {
        Pair<Form, String> formPick = this.renderer.getGizmoController().consumePendingTrackballClick();

        if (formPick != null)
        {
            this.pickFormFromRenderer(formPick);
        }

        this.renderer.getGizmoController().stop();
        this.statesKeyframes.finishGizmoPendingClick();
    }

    /** Which transform the gizmo should drag: the selected body part's transform when the
     *  toolbar's body-part toggle is on (and a body part is selected), the form's own general
     *  transform when the toolbar's transform toggle is on, the form/bone pose transform
     *  otherwise. */
    private UIPropTransform getGizmoDragTransform()
    {
        if (this.gizmoTargetsBodyPart && this.bodyPartEditor != null && this.bodyPartEditor.getPart() != null)
        {
            return this.bodyPartEditor.transform;
        }

        if (this.gizmoTargetsTransform && this.editor != null)
        {
            return this.editor.getEditableTransform();
        }

        if (this.modelSettingsEditor != null && this.modelSettingsEditor.isVisible())
        {
            UIPoseEditor poseEditor = this.modelSettingsEditor.getPoseEditor();

            if (poseEditor != null)
            {
                return poseEditor.transform;
            }
        }

        if (this.editor instanceof UIModelForm modelForm)
        {
            return modelForm.getPoseGizmoTransform();
        }

        if (this.editor == null || this.editor.generalPanel == null)
        {
            return null;
        }

        return this.editor.generalPanel.transform;
    }

    /** Pose bone handles (Model Block Edit / form palette Pose), not General or body-part. */
    private boolean isPoseBoneGizmo(UIPropTransform transform)
    {
        if (transform == null || this.gizmoTargetsBodyPart || this.gizmoTargetsTransform)
        {
            return false;
        }

        if (this.modelSettingsEditor != null && this.modelSettingsEditor.isVisible())
        {
            UIPoseEditor poseEditor = this.modelSettingsEditor.getPoseEditor();

            if (poseEditor != null && transform == poseEditor.transform)
            {
                return true;
            }
        }

        return this.editor instanceof UIModelForm modelForm
            && transform == modelForm.getPoseGizmoTransform();
    }

    public boolean isGizmoTargetingFormTransform()
    {
        return this.gizmoTargetsTransform;
    }

    /** Enables the toolbar transform gizmo and wires it to the form transform (General need not be open). */
    public void enableFormTransformGizmo()
    {
        this.gizmoTargetsTransform = true;
        this.gizmoTargetsBodyPart = false;

        this.enableFormTransformGizmoFromGeneralPanel();
    }

    /** Called when the General sidebar tab is selected — avoids re-entering {@link UIForm#setPanel}. */
    public void enableFormTransformGizmoFromGeneralPanel()
    {
        this.gizmoTargetsTransform = true;
        this.gizmoTargetsBodyPart = false;

        if (this.editor != null && this.editor.generalPanel != null && this.editor.form != null)
        {
            this.editor.generalPanel.transform.setTransform(this.editor.form.transform.get());
        }

        if (this.modelSettingsEditor != null && this.modelSettingsEditor.isVisible())
        {
            this.modelSettingsEditor.enterFormTransformGizmoMode();
        }
    }

    /** Turns off the toolbar transform gizmo and leaves form-transform edit mode in the model editor. */
    public void disableFormTransformGizmo()
    {
        if (!this.gizmoTargetsTransform
            && (this.modelSettingsEditor == null || !this.modelSettingsEditor.isFormTransformGizmoMode()))
        {
            return;
        }

        this.gizmoTargetsTransform = false;

        if (this.modelSettingsEditor != null)
        {
            this.modelSettingsEditor.exitFormTransformGizmoMode();
        }
    }

    /** Finds the world matrix of the selected body part's attach point (its bone's matrix,
     *  the part's own transform, and its own form root all composed together), i.e. exactly
     *  the point the part rotates/scales around - so the gizmo lands where the part is actually
     *  attached (e.g. on another model's head) instead of wherever the pose bone gizmo happens
     *  to be. Returns null if it can't be resolved, so the caller can fall back. */
    private Matrix4f getBodyPartOrigin(float transition)
    {
        BodyPart part = this.bodyPartEditor == null ? null : this.bodyPartEditor.getPart();
        BodyPartManager manager = part == null ? null : part.getManager();
        Form owner = manager == null ? null : manager.getOwner();

        if (owner == null || this.editor == null)
        {
            return null;
        }

        int index = owner.parts.getAllTyped().indexOf(part);

        if (index < 0)
        {
            return null;
        }

        String path = StringUtils.combinePaths(FormUtils.getPath(owner), String.valueOf(index));

        return normalizeOriginBasis(this.editor.getOrigin(transition, path, this.bodyPartEditor.transform.getOrientation()));
    }

    /** Strips scale/skew/mirroring out of a gizmo origin matrix, leaving only position and a
     *  right-handed unit-length rotation basis. Body part attach matrices carry the model
     *  chain's scale (and .bobj armatures can carry mirrored axes); feeding those raw into the
     *  gizmo distorts its rings into ellipses and skews the drag math - the rotation sweep arc
     *  runs ahead of the mouse and clicking a ring can kick the value by a large arbitrary
     *  amount. */
    private static Matrix4f normalizeOriginBasis(Matrix4f matrix)
    {
        return GizmoMatrixUtils.normalizeBasis(matrix);
    }

    /* Build a single gizmo transform-mode button that selects its mode and highlights while
       that mode is active (same behavior as the film viewport's buttons). */
    public void rebuildGizmoToolbar()
    {
        this.gizmoToolbar.removeAll();

        for (String id : BBSSettings.editorFormGizmoToolbar.getVisibleOrder())
        {
            UIIcon button = this.gizmoButtonMap.get(id);

            if (button == null)
            {
                continue;
            }

            button.setVisible(true);
            this.gizmoToolbar.add(button);
        }

        int count = this.gizmoToolbar.getChildren().size();

        this.gizmoToolbar.w(Math.max(20, count * 20));
        this.gizmoToolbar.resize();
    }

    private void openGizmoToolbarCustomizer()
    {
        if (this.getContext() == null)
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIFormEditorGizmoToolbarOverlayPanel(this::rebuildGizmoToolbar), 320, 180);
    }

    private UIIcon createGizmoModeButton(Icon icon, Gizmo.Mode mode, IKey tooltip)
    {
        UIIcon button = new UIIcon(icon, (b) ->
        {
            Gizmo.INSTANCE.setMode(mode);
            UIUtils.playClick();
        });

        button.tooltip(tooltip);
        button.activeBackground(Colors.A50 | Colors.BLUE);

        return button;
    }

    /** Leave the nested model editor and return to the form editor, like Esc does first. */
    private void closeModelEditorIfOpen()
    {
        if (this.modelSettingsEditor != null && this.modelSettingsEditor.isVisible())
        {
            this.toggleModelEditor();
        }
    }

    public void pickFormFromRenderer(Pair<Form, String> pair)
    {
        if (Window.isCtrlPressed() && !pair.b.isEmpty()) this.bodyPartEditor.pickBone(pair);
        else if (Window.isAltPressed()) UIReplaysEditorUtils.offerAdjacent(this.getContext(), pair.a, pair.b, (bone) -> this.pickFormBone(pair.a, bone));
        else if (Window.isShiftPressed()) UIReplaysEditorUtils.offerHierarchy(this.getContext(), pair.a, pair.b, (bone) -> this.pickFormBone(pair.a, bone));
        else this.pickFormBone(pair.a, pair.b);
    }

    private void pickFormBone(Form form, String bone)
    {
        if (form == null)
        {
            return;
        }

        Form currentForm = this.formsList.getCurrentFirst() != null ? this.formsList.getCurrentFirst().getForm() : null;

        if (form != currentForm)
        {
            this.formsList.setCurrentForm(form);
            this.pickForm(this.formsList.getCurrentFirst());
        }

        if (!bone.isEmpty() && this.editor != null)
        {
            this.editor.pickBone(bone);
        }
    }

    public void refillState()
    {
        if (this.statesKeyframes.getState() != null)
        {
            this.pickState(this.statesKeyframes.getState());
        }
    }

    private void pickState(AnimationState state)
    {
        this.statesKeyframes.setState(state);
    }

    private void plause()
    {
        this.playing = !this.playing;
    }

    private void toggleStateEditor()
    {
        this.closeModelEditorIfOpen();

        this.formEditor.toggleVisible();
        this.statesEditor.toggleVisible();
    }

    private void toggleModelEditor()
    {
        ModelForm modelForm = this.getEditedModelForm();

        if (modelForm == null)
        {
            return;
        }

        String modelId = modelForm.model.get();

        if (modelId == null || modelId.isEmpty())
        {
            return;
        }

        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard)
        {
            if (dashboard.documentTabsBar != null)
            {
                dashboard.documentTabsBar.addOrActivate(ContentType.MODELS, modelId);
            }
        }
    }

    private ModelForm getEditedModelForm()
    {
        if (this.editor != null && this.editor.form instanceof ModelForm modelForm)
        {
            return modelForm;
        }

        return null;
    }

    private void updateModelEditorButton()
    {
        if (this.openModelEditor == null)
        {
            return;
        }

        ModelForm modelForm = this.getEditedModelForm();
        boolean hasModel = modelForm != null && modelForm.model.get() != null && !modelForm.model.get().isEmpty();

        this.openModelEditor.setEnabled(hasModel);
    }

    private void toggleSidebar()
    {
        this.closeModelEditorIfOpen();
        this.forms.toggleVisible();
    }

    private void createFormContextMenu(ContextMenuManager menu)
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        if (current != null)
        {
            menu.custom(new UIPresetContextMenu(this.copyPasteController)
                .labels(this.getBodyPartCopyLabel(), UIKeys.FORMS_EDITOR_CONTEXT_PASTE));

            if (current.getForm() != null)
            {
                menu.action(Icons.ADD, UIKeys.FORMS_EDITOR_CONTEXT_ADD, () -> this.addBodyPart(new BodyPart("")));
            }

            if (current.part != null)
            {
                List<BodyPart> all = current.part.getManager().getAllTyped();

                if (all.size() > 1)
                {
                    int index = -1;

                    for (int i = 0; i < all.size(); i++)
                    {
                        if (all.get(i) == current.part)
                        {
                            index = i;

                            break;
                        }
                    }

                    if (index > 0) menu.action(Icons.ARROW_UP, UIKeys.FORMS_EDITOR_CONTEXT_MOVE_UP, () -> this.moveBodyPart(current, -1));
                    if (index < all.size() - 1) menu.action(Icons.ARROW_DOWN, UIKeys.FORMS_EDITOR_CONTEXT_MOVE_DOWN, () -> this.moveBodyPart(current, 1));
                }
            }

            if (this.hasSelectedBodyParts())
            {
                menu.action(Icons.REMOVE, this.getBodyPartRemoveLabel(), this::removeBodyPart);
            }
        }
    }

    private boolean hasSelectedBodyParts()
    {
        for (UIForms.FormEntry entry : this.formsList.getCurrent())
        {
            if (entry.part != null)
            {
                return true;
            }
        }

        return false;
    }

    private void moveBodyPart(UIForms.FormEntry current, int direction)
    {
        BodyPartManager manager = current.part.getManager();
        List<BodyPart> all = manager.getAllTyped();
        int index = all.indexOf(current.part);
        int newIndex = MathUtils.clamp(index + direction, 0, all.size() - 1);

        if (newIndex != index)
        {
            manager.moveBodyPart(current.part, newIndex);
            this.formsList.setForm(this.form);

            UIForms.FormEntry selection = null;

            for (UIForms.FormEntry entry : this.formsList.getList())
            {
                if (entry.part == current.part)
                {
                    selection = entry;

                    break;
                }
            }

            if (selection != null)
            {
                this.formsList.setCurrentScroll(selection);
                this.pickForm(selection);
            }
        }

        this.refillState();
    }

    private void addBodyPart(BodyPart part)
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        current.getForm().parts.addBodyPart(part);
        this.refreshFormList();
    }

    private MapType copyBodyPart()
    {
        List<UIForms.FormEntry> selected = this.formsList.getCurrent();

        if (selected.size() > 1)
        {
            ListType parts = new ListType();

            for (UIForms.FormEntry entry : selected)
            {
                if (entry.part != null)
                {
                    parts.add(entry.part.toData());
                }
            }

            if (parts.size() == 0)
            {
                return null;
            }

            MapType wrapper = new MapType();

            wrapper.put("body_parts", parts);

            return wrapper;
        }

        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        if (current == null || current.part == null)
        {
            return null;
        }

        return current.part.toData().asMap();
    }

    private void pasteBodyPart(MapType data, int mouseX, int mouseY)
    {
        if (data.has("body_parts"))
        {
            ListType parts = data.getList("body_parts");

            for (BaseType partData : parts)
            {
                BodyPart part = new BodyPart("");

                part.fromData(partData);
                this.addBodyPart(part);
            }
        }
        else
        {
            BodyPart part = new BodyPart("");

            part.fromData(data);
            this.addBodyPart(part);
        }

        this.refillState();
    }

    private IKey getBodyPartRemoveLabel()
    {
        int count = 0;

        for (UIForms.FormEntry entry : this.formsList.getCurrent())
        {
            if (entry.part != null)
            {
                count++;
            }
        }

        return count > 1 ? UIKeys.FORMS_EDITOR_CONTEXT_REMOVE_ALL : UIKeys.FORMS_EDITOR_CONTEXT_REMOVE;
    }

    private IKey getBodyPartCopyLabel()
    {
        int count = 0;

        for (UIForms.FormEntry entry : this.formsList.getCurrent())
        {
            if (entry.part != null)
            {
                count++;
            }
        }

        return count > 1 ? UIKeys.FORMS_EDITOR_CONTEXT_COPY_ALL : UIKeys.FORMS_EDITOR_CONTEXT_COPY;
    }

    private void removeBodyPart()
    {
        List<UIForms.FormEntry> selected = this.formsList.getCurrent();
        List<BodyPart> parts = new ArrayList<>();

        for (UIForms.FormEntry entry : selected)
        {
            if (entry.part != null)
            {
                parts.add(entry.part);
            }
        }

        if (parts.isEmpty() || this.form == null)
        {
            return;
        }

        int index = this.formsList.getIndex();

        this.undoHandler.handlePreValues(this.form.parts, 0);

        for (BodyPart part : parts)
        {
            part.getManager().removeBodyPart(part);
        }

        this.refreshFormList();

        if (!this.formsList.getList().isEmpty())
        {
            this.formsList.setIndex(Math.max(0, Math.min(index, this.formsList.getList().size() - 1)));
            UIForms.FormEntry first = this.formsList.getCurrentFirst();

            if (first != null)
            {
                this.pickForm(first);
            }
        }

        this.refillState();
    }

    private void pickForm(UIForms.FormEntry entry)
    {
        if (entry == null)
        {
            return;
        }
        this.bodyPartEditor.setVisible(entry.part != null);

        if (entry.part != null)
        {
            this.bodyPartEditor.setPart(entry.part, entry.form);
        }

        this.switchEditor(entry.getForm());
    }

    public void openFormList(Form current, Consumer<Form> callback)
    {
        UIFormEditorList list = new UIFormEditorList(this);

        list.setSelected(current);
        this.callback = callback;

        list.full(this);
        list.resize();
        this.add(list);
    }

    public boolean isEditing()
    {
        return this.form != null;
    }

    public boolean edit(Form form)
    {
        this.form = null;

        if (form == null)
        {
            return false;
        }

        form = FormUtils.copy(form);

        this.bodyPartEditor.setVisible(false);

        if (this.switchEditor(form))
        {
            this.undoHandler.reset();

            if (this.statesEditor.isVisible())
            {
                this.toggleStateEditor();
            }

            if (this.modelSettingsEditor.isVisible())
            {
                this.toggleModelEditor();
            }

            this.form = form;
            this.form.setId("form");
            this.form.preCallback(this.undoHandler::handlePreValues);

            AnimationState main = form.states.getMain();

            if (main == null)
            {
                main = CollectionUtils.getSafe(form.states.getAllTyped(), 0);
            }

            this.pickState(main);

            if (TOGGLED != this.forms.isVisible())
            {
                this.toggleSidebar();
            }

            this.palette.accept(form);
            this.renderer.reset();
            this.renderer.form = form;
            this.refreshFormList();
            this.formsList.setIndex(0);

            this.form.clearStatePlayers();

            return true;
        }

        return false;
    }

    public void undo()
    {
        if (this.form != null && this.undoHandler.applyUndo(this.form)) UIUtils.playClick();
    }

    public void redo()
    {
        if (this.form != null && this.undoHandler.applyRedo(this.form)) UIUtils.playClick();
    }

    public void refreshFormList()
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        this.formsList.setForm(this.form);
        this.formsList.setCurrentScroll(current);
    }

    public boolean switchEditor(Form form)
    {
        UIForm editor = createPanel(form);

        if (editor == null)
        {
            return false;
        }

        editor.setUndoId("form_panel");

        if (this.editor != null)
        {
            this.editor.removeFromParent();
        }

        this.editor = editor;

        this.formEditor.prepend(this.editor);

        this.editor.setEditor(this);
        this.editor.startEdit(form);
        this.editor.full(this.formEditor).resize();
        this.updateModelEditorButton();
        this.refillState();
        this.syncFormTransformGizmoForEditor();

        return true;
    }

    /**
     * Forms without a pose editor (extruded, label, billboard, …) only expose a form-level
     * transform. Enable that gizmo target as soon as their panel opens — otherwise the gizmo
     * stays inert until the user visits the General tab once (which calls
     * {@link #enableFormTransformGizmoFromGeneralPanel()}).
     */
    private void syncFormTransformGizmoForEditor()
    {
        if (this.editor instanceof UIModelForm)
        {
            /* Model forms default to pose bones; leave transform-gizmo mode off until the
             * toolbar gear (or General tab) opts in. */
            this.gizmoTargetsTransform = false;

            return;
        }

        if (this.editor != null)
        {
            this.enableFormTransformGizmoFromGeneralPanel();
        }
    }

    public Form finish()
    {
        Form form = this.form;

        this.form.setId("");
        this.form.resetCallbacks();
        this.form.states.cleanUp();
        this.exit();

        this.editor.finishEdit();
        this.editor.removeFromParent();
        this.editor = null;
        this.form = null;

        return form;
    }

    @Override
    public void exit()
    {
        if (this.modelSettingsEditor != null && this.modelSettingsEditor.isVisible())
        {
            this.toggleModelEditor();
        }

        this.callback = null;

        List<UIFormList> children = this.getChildren(UIFormList.class);

        if (!children.isEmpty())
        {
            children.get(0).removeFromParent();
        }
    }

    @Override
    public void toggleEditor()
    {}

    @Override
    public void accept(Form form)
    {
        if (this.callback != null)
        {
            this.callback.accept(form);
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putInt("body_part", this.formsList.getIndex());
    }

    @Override
    public void applyAllUndoData(MapType data)
    {
        if (this.editor != null && this.form != null)
        {
            this.switchEditor(this.form);
        }

        super.applyAllUndoData(data);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.refreshFormList();

        if (data.has("body_part"))
        {
            int bodyPartIndex = data.getInt("body_part");
            List<UIForms.FormEntry> list = this.formsList.getList();

            if (bodyPartIndex >= 0 && bodyPartIndex < list.size())
            {
                UIForms.FormEntry bodyPart = list.get(bodyPartIndex);

                this.formsList.setCurrentScroll(bodyPart);
                this.pickForm(bodyPart);
            }
            else if (!list.isEmpty())
            {
                this.formsList.setIndex(Math.max(0, Math.min(bodyPartIndex, list.size() - 1)));
                this.pickForm(this.formsList.getCurrentFirst());
            }
        }

        this.refillState();
    }

    public void preFormRender(UIContext context, Form form)
    {
        int tick = (int) context.getTick();

        if (this.statesEditor.isVisible())
        {
            AnimationState state = this.statesKeyframes.getState();

            if (state != null)
            {
                if (this.playing)
                {
                    if (tick != this.lastTick)
                    {
                        this.cursor += 1;
                    }

                    if (this.cursor >= state.duration.get())
                    {
                        this.playing = false;
                        this.cursor = 0;
                    }
                }

                state.properties.applyProperties(form, this.cursor + (this.playing ? context.getTransition() : 0));
            }
        }

        this.lastTick = tick;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.undoHandler != null)
        {
            this.undoHandler.submitUndo();
        }

        this.tickDetachedGizmoDrag(context);

        super.render(context);
    }

    /**
     * {@link UIPropTransform} advances gizmo drags from its own {@code render()}. When the
     * General panel that owns that widget is not mounted, drive the drag here so form
     * transform values still update without auto-opening that panel.
     */
    private void tickDetachedGizmoDrag(UIContext context)
    {
        UIPropTransform transform = this.getGizmoDragTransform();

        if (transform == null || !transform.isGizmoEditing() || transform.getRoot() != null)
        {
            return;
        }

        transform.tickGizmoDrag(context);
    }

    public Matrix4f getOrigin(float transition)
    {
        Matrix4f result = null;

        if (this.gizmoTargetsBodyPart && this.bodyPartEditor != null && this.bodyPartEditor.getPart() != null)
        {
            result = this.getBodyPartOrigin(transition);
        }
        else if (this.gizmoTargetsTransform && this.editor != null && this.editor.form != null)
        {
            /* "#origin" makes UIForm.getOrigin() return the form's own pivot (entry.origin()),
             * i.e. the point its own transform rotates/scales around, ignoring any pose bone -
             * exactly the model's bottom/pivot the transform panel's numbers apply to. */
            TransformOrientation orientation = this.editor.generalPanel != null ? this.editor.generalPanel.transform.getOrientation() : TransformOrientation.PARENT;
            Matrix4f matrix = this.editor.getOrigin(transition, FormUtils.getPath(this.editor.form) + "#origin", orientation);

            if (matrix != null && matrix != Matrices.EMPTY_4F)
            {
                Transform formTransform = this.editor.form.transform.get();
                result = GizmoMatrixUtils.withLocalRotation(matrix, formTransform, orientation);
            }
            else
            {
                result = matrix;
            }
        }
        else if (this.modelSettingsEditor != null && this.modelSettingsEditor.isVisible())
        {
            UIPoseEditor poseEditor = this.modelSettingsEditor.getPoseEditor();

            if (this.editor instanceof UIModelForm modelForm)
            {
                result = modelForm.getOriginForPoseEditor(transition, poseEditor);
            }
        }
        else if (this.statesEditor.isVisible())
        {
            result = this.statesKeyframes.getOrigin(transition);
        }
        else if (this.editor != null)
        {
            result = this.editor.getOrigin(transition);
        }

        return GizmoMatrixUtils.normalizeBasis(result);
    }

    @Override
    public int getCursor()
    {
        return this.cursor;
    }

    @Override
    public void setCursor(int tick)
    {
        this.cursor = tick;
    }
}
