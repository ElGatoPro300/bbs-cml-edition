package mchorse.bbs_mod.ui.utils.pose;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.Collection;

public class UIActionsConfigEditor extends UIElement
{
    public UIStringList actions;
    public UISearchList<String> actionsSearch;
    public UISearchList<String> animations;
    public UIToggle loop;
    public UITrackpad speed;
    public UITrackpad fade;
    public UITrackpad tick;

    private ActionsConfig configs;
    private ActionConfig config;
    private Runnable preCallback;
    private Runnable postCallback;
    private ModelForm form;
    private boolean waitingForAnimations;
    private String waitingModelId = "";
    private String selectedAction = "idle";

    public UIActionsConfigEditor(Runnable preCallback, Runnable postCallback)
    {
        this.preCallback = preCallback;
        this.postCallback = postCallback;

        this.actions = new UIStringList((l) -> this.pickAction(l.get(0), false))
        {
            @Override
            public void render(UIContext context)
            {
                super.render(context);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
            }
        };
        this.actions.scroll.cancelScrolling();
        this.actions.background(0xFF141418);
        this.actionsSearch = new UISearchList<>(this.actions);
        this.actionsSearch.label(UIKeys.GENERAL_SEARCH);
        this.actionsSearch.h(112 + 20);

        this.animations = new UISearchList<>(new UIStringList((l) ->
        {
            this.callback(this.preCallback);
            this.config.name = this.animations.list.getIndex() == 0 ? "" : l.get(0);
            this.callback(this.postCallback);
        })
        {
            @Override
            public void render(UIContext context)
            {
                super.render(context);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
            }
        });
        this.animations.list.cancelScrollEdge();
        this.animations.label(UIKeys.GENERAL_SEARCH).list.background(0xFF141418);
        this.animations.h(112 + 20);
        this.loop = new UIToggle(UIKeys.FORMS_EDITORS_ACTIONS_LOOPS, (b) ->
        {
            this.callback(this.preCallback);
            this.config.loop = b.getValue();
            this.callback(this.postCallback);
        });
        this.speed = new UITrackpad((v) ->
        {
            this.callback(this.preCallback);
            this.config.speed = v.floatValue();
            this.callback(this.postCallback);
        });
        this.fade = new UITrackpad((v) ->
        {
            this.callback(this.preCallback);
            this.config.fade = v.floatValue();
            this.callback(this.postCallback);
        });
        this.fade.limit(0);
        this.tick = new UITrackpad((v) ->
        {
            this.callback(this.preCallback);
            this.config.tick = v.intValue();
            this.callback(this.postCallback);
        });
        this.tick.limit(0).integer();

        this.column().vertical().stretch();
        this.add(UI.label(UIKeys.FORMS_EDITORS_MODEL_ACTIONS), this.actionsSearch);
        this.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_ANIMATIONS).marginTop(6), this.animations, this.loop);
        this.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_SPEED).marginTop(6), this.speed);
        this.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_FADE).marginTop(6), this.fade);
        this.add(UI.label(UIKeys.FORMS_EDITORS_ACTIONS_TICK).marginTop(6), this.tick);
    }

    private void callback(Runnable runnable)
    {
        if (runnable != null)
        {
            runnable.run();
        }
    }

    public void setConfigs(ActionsConfig configs, ModelForm form)
    {
        this.configs = configs;
        this.form = form;
        this.waitingModelId = form != null && form.model.get() != null ? form.model.get() : "";
        this.selectedAction = "idle";

        ModelManager models = BBSModClient.getModels();

        if (!this.waitingModelId.isEmpty())
        {
            /* Prioritize visible morphs so Acciones does not sit on an empty list
             * while a heavy emoticons/alex|steve mesh loads on low-end PCs. */
            models.getModel(this.waitingModelId, true);

            if (this.waitingModelId.startsWith("emoticons/"))
            {
                models.ensureEmoticonDefaultAnimations();
            }
        }

        this.applyFromForm(true);
    }

    private void applyFromForm(boolean resetActionSelection)
    {
        if (this.form == null || this.configs == null)
        {
            return;
        }

        ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(this.form);
        ModelInstance model = renderer.getModel();

        if (model != null)
        {
            BBSModClient.getModels().ensureEmoticonAnimations(model);
        }

        renderer.ensureAnimator(0F);

        IAnimator animator = renderer.getAnimator();
        Collection<String> animations = model != null && model.animations != null
            ? model.animations.animations.keySet()
            : null;
        Collection<String> actions = animator != null ? animator.getActions() : null;
        boolean hasAnimations = animations != null && !animations.isEmpty();
        boolean loading = !this.waitingModelId.isEmpty()
            && BBSModClient.getModels().isLoading(this.waitingModelId);

        this.waitingForAnimations = !hasAnimations
            && !this.waitingModelId.isEmpty()
            && (loading || model == null || this.waitingModelId.startsWith("emoticons/"));

        this.setConfigs(this.configs, animations, actions, resetActionSelection);
    }

    public void setConfigs(ActionsConfig configs, Collection<String> animations, Collection<String> actions)
    {
        this.setConfigs(configs, animations, actions, true);
    }

    private void setConfigs(ActionsConfig configs, Collection<String> animations, Collection<String> actions, boolean resetActionSelection)
    {
        this.configs = configs;

        String keepAction = resetActionSelection ? "idle" : this.selectedAction;

        this.animations.list.clear();
        this.actions.clear();

        if (animations != null)
        {
            this.animations.list.add(animations);
            this.animations.list.sort();
            this.animations.list.getList().add(0, UIKeys.GENERAL_NONE.get());
            this.animations.list.update();
        }

        if (actions != null)
        {
            this.actions.add(actions);
            this.actions.sort();

            if (keepAction == null || keepAction.isEmpty() || !this.actions.getList().contains(keepAction))
            {
                keepAction = "idle";
            }

            this.pickAction(keepAction, true);
        }
    }

    private void pickAction(String key, boolean select)
    {
        ActionsConfig config = this.configs;

        this.selectedAction = key;
        this.config = config.actions.get(key);

        if (this.config == null)
        {
            this.config = new ActionConfig(key);

            config.actions.put(key, this.config);
        }

        this.animations.list.setCurrentScroll(this.config.name);

        if (this.animations.list.getIndex() == -1)
        {
            this.animations.list.setIndex(0);
        }

        this.loop.setValue(this.config.loop);
        this.speed.setValue(this.config.speed);
        this.fade.setValue(this.config.fade);
        this.tick.setValue(this.config.tick);

        if (select)
        {
            this.actions.setCurrentScroll(key);
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.waitingForAnimations && this.form != null)
        {
            ModelFormRenderer renderer = (ModelFormRenderer) FormUtilsClient.getRenderer(this.form);
            ModelInstance model = renderer.getModel();
            boolean loading = !this.waitingModelId.isEmpty()
                && BBSModClient.getModels().isLoading(this.waitingModelId);
            boolean hasAnimations = model != null
                && model.animations != null
                && !model.animations.animations.isEmpty();

            if (hasAnimations)
            {
                this.applyFromForm(false);
            }
            else if (!loading && model != null && this.waitingModelId.startsWith("emoticons/"))
            {
                /* Model finished without clips — retry shared library merge once. */
                BBSModClient.getModels().ensureEmoticonAnimations(model);

                if (model.animations != null && !model.animations.animations.isEmpty())
                {
                    this.applyFromForm(false);
                }
                else
                {
                    this.waitingForAnimations = false;
                }
            }
            else if (!loading && model == null)
            {
                this.waitingForAnimations = false;
            }
        }

        super.render(context);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putString("action", this.actions.getCurrentFirst());
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.pickAction(data.getString("action"), true);
    }
}
