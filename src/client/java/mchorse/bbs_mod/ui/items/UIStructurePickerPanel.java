package mchorse.bbs_mod.ui.items;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.StructurePickerClient;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.items.StructurePickerMode;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

public class UIStructurePickerPanel extends UIOverlayPanel

{

    private static UIStructurePickerPanel opened;



    private final List<UIButton> shapeButtons = new ArrayList<>();

    private final UIButton modelBlockButton;

    private final UIButton importFilmButton;

    private final UIButton removeSelectionButton;

    private final UIToggle subtractToggle;

    private final UIButton breakSelectionButton;

    private final UIToggle clickOnAirToggle;



    public static void open()

    {

        if (UIStructurePickerPanel.opened != null)

        {

            return;

        }



        UIStructurePickerPanel panel = new UIStructurePickerPanel();



        UIStructurePickerPanel.opened = panel;



        MinecraftClient client = MinecraftClient.getInstance();

        Screen returnScreen = client.currentScreen;



        panel.onClose((event) ->

        {

            UIStructurePickerPanel.opened = null;



            if (returnScreen != null)

            {

                client.setScreen(returnScreen);

            }

            else

            {

                client.setScreen(null);

            }

        });



        UIScreen.open(new UIBaseMenu()

        {

            @Override

            public boolean needsWorldRender()

            {

                return true;

            }



            @Override

            public boolean canHideHUD()

            {

                return false;

            }



            @Override

            public boolean canPause()

            {

                return false;

            }



            @Override

            protected void closeMenu()

            {

                panel.close();

            }



            @Override

            public void onOpen(UIBaseMenu oldMenu)

            {

                super.onOpen(oldMenu);



                UIOverlay.addOverlay(this.context, panel, 300, 240);

            }

        });

    }



    public static boolean isOpened()

    {

        if (UIStructurePickerPanel.opened != null)

        {

            return true;

        }



        Screen currentScreen = MinecraftClient.getInstance().currentScreen;



        if (!(currentScreen instanceof UIScreen uiScreen))

        {

            return false;

        }



        List<UIStructurePickerPanel> panels = uiScreen.getMenu().getRoot().getChildren(UIStructurePickerPanel.class);



        return !panels.isEmpty();

    }



    public UIStructurePickerPanel()

    {

        super(UIKeys.STRUCTURE_PICKER_CONFIRM_TITLE);



        StructurePickerMode[] modes = StructurePickerMode.values();



        for (int i = 0; i < modes.length; i++)

        {

            StructurePickerMode mode = modes[i];

            UIButton button = new UIButton(UIKeys.STRUCTURE_PICKER_MODE_LABELS[mode.index], (b) -> this.selectMode(mode));



            button.relative(this.content).x(8).y(8 + i * 22).w(110).h(20);

            this.shapeButtons.add(button);

            this.content.add(button);

        }



        this.modelBlockButton = new UIButton(UIKeys.STRUCTURE_PICKER_MAKE_MODEL_BLOCK, (b) -> this.importSelection(true));

        this.importFilmButton = new UIButton(UIKeys.STRUCTURE_PICKER_IMPORT_FILM, (b) -> this.importSelection(false));

        this.removeSelectionButton = new UIButton(UIKeys.STRUCTURE_PICKER_REMOVE_SELECTION, (b) -> this.removeSelection());

        this.subtractToggle = new UIToggle(UIKeys.STRUCTURE_PICKER_SUBTRACT_SELECTION, StructurePickerClient.isSubtractMode(), (b) ->
        {
            StructurePickerClient.setSubtractMode(b.getValue());
        });

        this.breakSelectionButton = new UIButton(UIKeys.STRUCTURE_PICKER_BREAK_SELECTION, (b) -> this.breakSelection());

        this.clickOnAirToggle = new UIToggle(UIKeys.STRUCTURE_PICKER_CLICK_ON_AIR, StructurePickerClient.isClickOnAir(), (b) ->
        {
            StructurePickerClient.setClickOnAir(b.getValue());
        });



        this.modelBlockButton.relative(this.content).x(126).y(8).w(1F, -134).h(20);

        this.importFilmButton.relative(this.content).x(126).y(32).w(1F, -134).h(20);

        this.subtractToggle.relative(this.content).x(126).y(56).w(1F, -134).h(14);

        this.removeSelectionButton.relative(this.content).x(126).y(74).w(1F, -134).h(20);

        this.breakSelectionButton.relative(this.content).x(126).y(98).w(1F, -134).h(20);

        this.clickOnAirToggle.relative(this.content).x(126).y(122).w(1F, -134).h(14);

        this.breakSelectionButton.color(Colors.RED);



        this.content.add(this.modelBlockButton, this.importFilmButton, this.subtractToggle, this.removeSelectionButton, this.breakSelectionButton, this.clickOnAirToggle);

        this.updateShapeButtons();

        this.updateImportButtons();

    }



    private void selectMode(StructurePickerMode mode)

    {

        StructurePickerClient.setMode(mode);

        this.updateShapeButtons();

    }



    private void updateShapeButtons()

    {

        StructurePickerMode current = StructurePickerClient.getMode();

        StructurePickerMode[] modes = StructurePickerMode.values();



        for (int i = 0; i < this.shapeButtons.size(); i++)

        {

            this.shapeButtons.get(i).color(modes[i] == current ? Colors.ACTIVE : Colors.GRAY);

        }

    }



    private void updateImportButtons()

    {

        UIFilmPanel filmPanel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

        boolean canImport = filmPanel != null && filmPanel.getData() != null;



        this.importFilmButton.setEnabled(canImport);

    }



    private void importSelection(boolean toModelBlock)

    {

        if (!toModelBlock)

        {

            UIFilmPanel filmPanel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);



            if (filmPanel == null || filmPanel.getData() == null)

            {

                return;

            }

        }



        StructurePickerClient.importSelection(toModelBlock);
    }



    private void removeSelection()

    {

        if (Window.isShiftPressed())

        {

            StructurePickerClient.clearSelection();

            this.close();



            return;

        }



        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(

            UIKeys.STRUCTURE_PICKER_REMOVE_SELECTION,

            UIKeys.STRUCTURE_PICKER_REMOVE_CONFIRM,

            (confirmed) ->

            {

                if (confirmed)

                {

                    StructurePickerClient.clearSelection();

                    this.close();

                }

            }

        );



        UIOverlay.addOverlay(this.getContext(), panel);

    }



    private void breakSelection()

    {

        if (Window.isShiftPressed())

        {

            StructurePickerClient.breakSelection();

            this.close();



            return;

        }



        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(

            UIKeys.STRUCTURE_PICKER_BREAK_SELECTION,

            UIKeys.STRUCTURE_PICKER_BREAK_CONFIRM,

            (confirmed) ->

            {

                if (confirmed)

                {

                    StructurePickerClient.breakSelection();

                    this.close();

                }

            }

        );



        UIOverlay.addOverlay(this.getContext(), panel);

    }

}


