package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIActionsFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoMatrixUtils;
import mchorse.bbs_mod.ui.utils.gizmo.TransformOrientation;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.joml.Matrices;

import org.joml.Matrix4f;

public class UIModelForm extends UIForm<ModelForm>
{
    public UIModelFormPanel modelPanel;

    public UIModelForm()
    {
        this.modelPanel = new UIModelFormPanel(this);
        this.defaultPanel = this.modelPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_MODEL_POSE, Icons.POSE);
        this.registerPanel(new UIActionsFormPanel(this), UIKeys.FORMS_EDITORS_ACTIONS_TITLE, Icons.MORE);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.modelPanel)
            {
                this.setPanel(this.modelPanel);
            }

            this.modelPanel.pick.clickItself();
        });
    }

    @Override
    public UIPropTransform getEditableTransform()
    {
        return super.getEditableTransform();
    }

    @Override
    public void setPanel(UIFormPanel<ModelForm> panel)
    {
        super.setPanel(panel);

        if (panel == this.modelPanel && this.editor != null)
        {
            this.editor.disableFormTransformGizmo();
        }

        if (panel instanceof UIActionsFormPanel actionsPanel)
        {
            actionsPanel.refreshConfigs();
        }
    }

    public UIPropTransform getPoseGizmoTransform()
    {
        return this.modelPanel.poseEditor.transform;
    }

    public void showPosePanel()
    {
        this.setPanel(this.modelPanel);
    }

    @Override
    public Matrix4f getOrigin(float transition)
    {
        String path = FormUtils.getPath(this.form);
        UIPoseEditor poseEditor = this.modelPanel.poseEditor;

        return this.getOrigin(transition, StringUtils.combinePaths(path, poseEditor.groups.list.getCurrentFirst()), poseEditor.transform.getOrientation());
    }

    /**
     * Pose gizmo basis must match how {@code PoseTransform.translate} moves the bone.
     * BOBJ (and cubic) store translate in the bone bind / group frame — never in raw world
     * axes — so stripping {@link MatrixCacheEntry#origin()} down to a translation-only matrix
     * made BOBJ limbs slide on the wrong axis when dragging a handle.
     */
    @Override
    public Matrix4f getOrigin(float transition, String path, boolean local)
    {
        return this.getOrigin(transition, path, local ? TransformOrientation.LOCAL : TransformOrientation.PARENT);
    }

    public Matrix4f getOrigin(float transition, String path, TransformOrientation orientation)
    {
        if (path == null)
        {
            return Matrices.EMPTY_4F;
        }

        MatrixCache map = FormUtilsClient.getRenderer(FormUtils.getRoot(this.form)).collectMatrices(this.editor.renderer.getTargetEntity(), transition);
        boolean forceOrigin = path.endsWith("#origin");

        if (forceOrigin)
        {
            path = path.substring(0, path.length() - 7);
        }

        MatrixCacheEntry entry = map.get(path);

        if (entry == null)
        {
            return Matrices.EMPTY_4F;
        }

        if (forceOrigin)
        {
            Matrix4f originMatrix = entry.origin();

            return originMatrix == null ? Matrices.EMPTY_4F : new Matrix4f(originMatrix);
        }

        boolean bobj = ModelFormRenderer.isBobjModel(this.form);
        Matrix4f matrix = GizmoMatrixUtils.resolveFilmPoseBoneMatrix(entry, orientation, bobj);

        return matrix == null ? Matrices.EMPTY_4F : matrix;
    }

    public Matrix4f getOriginForPoseEditor(float transition, UIPoseEditor poseEditor)
    {
        if (poseEditor == null)
        {
            return this.getOrigin(transition);
        }

        String path = FormUtils.getPath(this.form);

        return this.getOrigin(transition, StringUtils.combinePaths(path, poseEditor.groups.list.getCurrentFirst()), poseEditor.transform.getOrientation());
    }
}
