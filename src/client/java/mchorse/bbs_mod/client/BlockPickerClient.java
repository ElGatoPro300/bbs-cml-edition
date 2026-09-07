package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.items.BlockPickerItem;
import mchorse.bbs_mod.items.BlockPickerMode;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.items.UIBlockPickerModeOverlayPanel;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockPickerClient
{
    private static BlockPickerMode mode = BlockPickerMode.MODEL_BLOCK;

    public static void syncFromSettings()
    {
        if (BBSSettings.blockPickerDefaultMode != null)
        {
            BlockPickerClient.mode = BlockPickerMode.fromIndex(BBSSettings.blockPickerDefaultMode.get());
        }
    }

    public static BlockPickerMode getMode()
    {
        return BlockPickerClient.mode;
    }

    public static void setMode(BlockPickerMode mode)
    {
        BlockPickerClient.mode = mode;
    }

    public static void openModeOverlay()
    {
        if (UIBlockPickerModeOverlayPanel.isOpened())
        {
            return;
        }

        UIBlockPickerModeOverlayPanel.open(new UIBlockPickerModeOverlayPanel());
    }

    public static void importBlock(BlockPos pos)
    {
        UIFilmPanel panel = BlockPickerClient.getOpenFilmPanel();

        if (panel == null)
        {
            BlockPickerClient.showMessage(UIKeys.GENERAL_ERROR, UIKeys.BLOCK_PICKER_NO_OPEN_FILM);

            return;
        }

        World world = MinecraftClient.getInstance().world;

        if (world == null)
        {
            return;
        }

        BlockState state = world.getBlockState(pos);

        if (state.isAir())
        {
            return;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (blockEntity instanceof ModelBlockEntity)
        {
            return;
        }

        if (state.isOf(BBSMod.MODEL_BLOCK))
        {
            return;
        }

        BlockForm form = BlockPickerItem.createBlockForm(world, pos, state);
        Film film = panel.getData();
        Replay replay = film.replays.addReplay();

        replay.form.set(FormUtils.copy(form));
        replay.keyframes.x.insert(0, pos.getX() + 0.5D);
        replay.keyframes.y.insert(0, (double) pos.getY());
        replay.keyframes.z.insert(0, pos.getZ() + 0.5D);

        panel.replayEditor.replays.replays.ensureVisible(replay);
    }

    public static UIFilmPanel getOpenFilmPanel()
    {
        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

        if (panel == null || panel.getData() == null)
        {
            return null;
        }

        return panel;
    }

    public static void showMessage(IKey title, IKey message)
    {
        BlockPickerClient.openInGameOverlay(new UIMessageOverlayPanel(title, message), 260, 120);
    }

    public static void openInGameOverlay(UIOverlayPanel panel, int width, int height)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen returnScreen = client.currentScreen;

        panel.onClose((event) ->
        {
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
            public void onOpen(UIBaseMenu oldMenu)
            {
                super.onOpen(oldMenu);

                UIOverlay.addOverlay(this.context, panel, width, height);
            }
        });
    }
}
