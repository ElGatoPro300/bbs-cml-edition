package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.toolbar.TimelineInteractionHints;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import org.joml.Vector3d;

/**

 * Save, loop and scene-distance status controls shown on the top-right document

 * tab bar while editing a film. Replaces the old slide-in toast notifications.

 */

public class UIFilmStatusIcons extends UIElement

{

    public static final int WIDTH = 60;



    private static final int ICON_SIZE = 20;

    private static final long SAVE_FLASH_MS = 3000L;

    private static final int SAVE_FLASH_COLOR = 0xCC1A5C1A;

    private static final int WARNING_PULSE_COLOR = 0xCC8C1A1A;



    private final UIFilmPanel panel;

    private final UIIcon warningIcon;

    private final UIIcon loopIcon;

    private final UIIcon saveIcon;



    private long saveFlashStart = -1L;



    public UIFilmStatusIcons(UIFilmPanel panel)

    {

        this.panel = panel;

        this.h(ICON_SIZE);



        this.warningIcon = new UIIcon(Icons.WARNING, (b) -> this.panel.teleportToCamera());

        this.warningIcon.tooltip(UIKeys.FILM_TELEPORT_DESCRIPTION);

        this.warningIcon.iconColor(Colors.WHITE);



        this.loopIcon = new UIIcon(Icons.REVERSE, (b) -> this.toggleLoop());

        this.loopIcon.tooltip(UIKeys.CAMERA_EDITOR_KEYS_MODES_LOOPING);



        this.saveIcon = new UIIcon(Icons.SAVED, (b) -> this.saveFromIcon());

        this.saveIcon.tooltip(UIKeys.GENERAL_SAVE);



        this.add(this.warningIcon);

        this.add(this.loopIcon);

        this.add(this.saveIcon);

    }



    public void layoutInTabBar(int x, int y, int h)

    {

        int w = ICON_SIZE * 3;



        this.area.set(x, y, w, h);

        this.warningIcon.area.set(x, y, ICON_SIZE, h);

        this.loopIcon.area.set(x + ICON_SIZE, y, ICON_SIZE, h);

        this.saveIcon.area.set(x + ICON_SIZE * 2, y, ICON_SIZE, h);

    }



    public void flashAutosave()

    {

        this.saveFlashStart = System.currentTimeMillis();

    }



    private void saveFromIcon()

    {

        if (this.panel.getData() == null)

        {

            return;

        }



        this.panel.manualSave();

    }



    private void toggleLoop()

    {

        if (this.panel.getData() == null)

        {

            return;

        }



        BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get());

    }



    private boolean isFarFromScene()

    {

        if (this.panel.getData() == null || this.panel.isShowingHomePage())

        {

            return false;

        }



        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        Camera camera = this.panel.getCamera();



        if (player == null || camera == null)

        {

            return false;

        }



        Vec3d pos = player.getPos();

        Vector3d cameraPos = camera.position;

        double distance = cameraPos.distance(pos.x, pos.y, pos.z);

        int viewDistance = MinecraftClient.getInstance().options.getViewDistance().getValue();



        return distance > viewDistance * 12D;

    }



    @Override

    public void render(UIContext context)

    {

        boolean far = this.isFarFromScene();



        this.warningIcon.setVisible(far);

        this.warningIcon.setEnabled(far);

        this.loopIcon.active(BBSSettings.editorLoop.get());

        this.loopIcon.activeBackground(BBSSettings.editorLoop.get() ? Colors.setA(BBSSettings.primaryColor.get(), 0.55F) : 0);

        if (far)
        {
            float pulse = TimelineInteractionHints.getPulseAlpha(0.4F, 0.95F);
            int red = Colors.setA(WARNING_PULSE_COLOR, pulse);

            context.batcher.box(this.warningIcon.area.x, this.warningIcon.area.y, this.warningIcon.area.ex(), this.warningIcon.area.ey(), red);
        }

        if (this.saveFlashStart >= 0L)

        {

            long elapsed = System.currentTimeMillis() - this.saveFlashStart;



            if (elapsed >= SAVE_FLASH_MS)

            {

                this.saveFlashStart = -1L;

            }

            else

            {

                float fade = 1F - elapsed / (float) SAVE_FLASH_MS;

                int green = Colors.setA(SAVE_FLASH_COLOR, fade);



                context.batcher.box(this.saveIcon.area.x, this.saveIcon.area.y, this.saveIcon.area.ex(), this.saveIcon.area.ey(), green);

            }

        }



        super.render(context);

    }

}


