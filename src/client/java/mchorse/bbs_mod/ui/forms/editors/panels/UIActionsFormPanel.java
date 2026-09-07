package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;

public class UIActionsFormPanel extends UIFormPanel<ModelForm>
{
    public UIActionsConfigEditor editor;

    private boolean pendingAnimRefresh;
    private String lastModelId = "";
    private int lastAnimationCount = -1;

    public UIActionsFormPanel(UIForm editor)
    {
        super(editor);

        this.editor = new UIActionsConfigEditor(() ->
        {
            this.form.actions.preNotify();
        }, () ->
        {
            ((ModelFormRenderer) FormUtilsClient.getRenderer(this.form)).resetAnimator();
            this.form.postNotify();
        });
        this.editor.setUndoId("model_action_editor");

        this.options.add(this.editor);
    }

    @Override
    public void startEdit(ModelForm form)
    {
        super.startEdit(form);

        this.pendingAnimRefresh = true;
        this.lastModelId = "";
        this.lastAnimationCount = -1;
        this.refreshConfigs();
    }

    public void refreshConfigs()
    {
        if (this.form == null)
        {
            return;
        }

        String modelId = this.form.model.get();

        if (modelId == null)
        {
            modelId = "";
        }

        ModelInstance model = ModelFormRenderer.getModel(this.form);

        if (model == null && !modelId.isEmpty())
        {
            BBSModClient.getModels().getModel(modelId, true);
            this.pendingAnimRefresh = true;
        }

        int animationCount = model != null && model.animations != null
            ? model.animations.animations.size()
            : -1;

        if (!modelId.equals(this.lastModelId) || animationCount != this.lastAnimationCount || this.pendingAnimRefresh)
        {
            this.editor.setConfigs(this.form.actions.get(), this.form);
            this.lastModelId = modelId;
            this.lastAnimationCount = animationCount;
        }

        if (model != null)
        {
            this.pendingAnimRefresh = animationCount <= 0 && BBSModClient.getModels().isLoading(modelId);
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.form != null)
        {
            String modelId = this.form.model.get();
            ModelInstance model = ModelFormRenderer.getModel(this.form);
            int animationCount = model != null && model.animations != null
                ? model.animations.animations.size()
                : -1;
            boolean modelChanged = modelId != null && !modelId.equals(this.lastModelId);
            boolean animsChanged = animationCount != this.lastAnimationCount;

            if (this.pendingAnimRefresh || modelChanged || animsChanged)
            {
                this.refreshConfigs();
            }
        }

        super.render(context);
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        ActionsConfig.removeDefaultActions(this.form.actions.get().actions);
        this.pendingAnimRefresh = false;
        this.lastModelId = "";
        this.lastAnimationCount = -1;
    }
}
