package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIVideoFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIVideoForm extends UIForm<VideoForm>
{
    private UIVideoFormPanel videoFormPanel;

    public UIVideoForm()
    {
        super();

        this.videoFormPanel = new UIVideoFormPanel(this);
        this.defaultPanel = this.videoFormPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_VIDEO_TITLE, Icons.IMAGE);
        this.registerDefaultPanels();
    }
}
