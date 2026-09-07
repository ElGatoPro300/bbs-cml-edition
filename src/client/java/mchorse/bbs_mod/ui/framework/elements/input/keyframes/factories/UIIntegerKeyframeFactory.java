package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.List;

public class UIIntegerKeyframeFactory extends UIKeyframeFactory<Integer>
{
    private UITrackpad value;
    private UIBezierHandles handles;
    private UIElement hotbarPreview;
    private UIToggle centered;
    private KeyframeChannel<Boolean> centerChannel;
    private Replay replay;
    private Film film;

    public UIIntegerKeyframeFactory(Keyframe<Integer> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);
        boolean isSelectedSlot = sheet != null && ("selected_slot".equals(sheet.id) || sheet.id.endsWith("/selected_slot"));
        String repeatAxis = getRepeatAxis(sheet);

        this.value = new UITrackpad(this::setValue);
        this.value.setValue(keyframe.getValue());
        this.handles = new UIBezierHandles(keyframe);
        this.registerValueTrackpad(this.value);

        if (repeatAxis != null)
        {
            this.value.limit(1D, 64D).integer();
            this.setupCenteredToggle(keyframe, sheet, repeatAxis);
        }

        if (isSelectedSlot)
        {
            this.replay = this.findReplay(keyframe.getParent());
            this.film = this.findFilm(keyframe.getParent());
            this.value.limit(0, 8).integer();
            this.hotbarPreview = new UIElement()
            {
                @Override
                public void render(UIContext context)
                {
                    super.render(context);

                    int selected = MathUtils.clamp(UIIntegerKeyframeFactory.this.keyframe.getValue(), 0, 8);
                    int padding = 4;
                    int gap = 2;
                    int available = this.area.w - padding * 2 - gap * 8;
                    int slotW = Math.max(6, available / 9);
                    int slotH = Math.max(24, this.area.h - padding * 2);
                    int startX = this.area.x + padding;
                    int y = this.area.y + (this.area.h - slotH) / 2;

                    for (int i = 0; i < 9; i++)
                    {
                        int x = startX + i * (slotW + gap);
                        int bg = Colors.setA(Colors.DARKER_GRAY, 0.45F);

                        if (i == selected)
                        {
                            context.batcher.box(x - 1, y - 1, x + slotW + 1, y + slotH + 1, Colors.ACTIVE | Colors.A100);
                        }

                        context.batcher.box(x, y, x + slotW, y + slotH, bg);

                        ItemStack stack = ItemStack.EMPTY;

                        if (UIIntegerKeyframeFactory.this.replay != null)
                        {
                            List<ItemStack> stacks = UIIntegerKeyframeFactory.this.replay.inventory.getStacks();
                            if (!stacks.isEmpty())
                            {
                                stack = stacks.size() > i ? stacks.get(i) : ItemStack.EMPTY;
                            }
                        }

                        if ((stack == null || stack.isEmpty()) && UIIntegerKeyframeFactory.this.film != null)
                        {
                            List<ItemStack> stacks = UIIntegerKeyframeFactory.this.film.inventory.getStacks();
                            if (!stacks.isEmpty())
                            {
                                stack = stacks.size() > i ? stacks.get(i) : ItemStack.EMPTY;
                            }
                        }

                        if ((stack == null || stack.isEmpty()))
                        {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client.player != null)
                            {
                                stack = client.player.getInventory().getStack(i);
                            }
                        }

                        if (stack != null && !stack.isEmpty())
                        {
                            MatrixStack matrices = new MatrixStack();
                            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
                            int itemX = x + Math.max(0, (slotW - 16) / 2);
                            int itemY = y + Math.max(0, (slotH - 16) / 2);

                            matrices.push();
                            consumers.setUI(true);

                            Vector3f light0 = new Vector3f(0.85F, 0.85F, -1.0F).normalize();
                            Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1.0F).normalize();
                            /* TODO 1.21.11: RenderSystem.setupGui3DDiffuseLighting removed */

                            context.batcher.getContext().drawItem(stack, itemX, itemY);
                            context.batcher.getContext().drawStackOverlay(context.batcher.getFont().getRenderer(), stack, itemX, itemY);

                            /* TODO 1.21.11: context.draw() removed */

                            /* TODO 1.21.11: DiffuseLighting.disableGuiDepthLighting removed */

                            consumers.setUI(false);
                            matrices.pop();
                        }
                    }
                }
            };

            this.hotbarPreview.h(32);
        }

        if (this.hotbarPreview != null)
        {
            this.scroll.add(this.value, this.handles.createColumn(), this.hotbarPreview);
        }
        else if (this.centered != null)
        {
            this.scroll.add(this.value, this.centered, this.handles.createColumn());
        }
        else
        {
            this.scroll.add(this.value, this.handles.createColumn());
        }
    }

    private static String getRepeatAxis(UIKeyframeSheet sheet)
    {
        if (sheet == null)
        {
            return null;
        }

        String name = sheet.id.contains("/") ? sheet.id.substring(sheet.id.lastIndexOf('/') + 1) : sheet.id;

        if ("repeat_x".equals(name) || "repeat_y".equals(name) || "repeat_z".equals(name))
        {
            return name;
        }

        return null;
    }

    private void setupCenteredToggle(Keyframe<Integer> keyframe, UIKeyframeSheet sheet, String repeatAxis)
    {
        FormProperties properties = this.findFormProperties(keyframe.getParent());
        Form form = sheet.property != null ? FormUtils.getForm(sheet.property) : null;

        if (form == null)
        {
            Replay owner = this.findReplayOwner(keyframe.getParent());

            form = owner != null ? owner.form.get() : null;
        }

        if (properties == null || form == null)
        {
            return;
        }

        String centerKey = sheet.id.substring(0, sheet.id.length() - repeatAxis.length()) + repeatAxis.replace("repeat_", "repeat_center_");
        KeyframeChannel channel = properties.getOrCreate(form, centerKey);

        if (channel == null)
        {
            return;
        }

        this.centerChannel = (KeyframeChannel<Boolean>) channel;

        boolean current;

        if (this.centerChannel.getKeyframes().isEmpty())
        {
            BaseValue property = FormUtils.getProperty(form, centerKey);

            current = property instanceof ValueBoolean booleanValue && booleanValue.get();
        }
        else
        {
            Boolean interpolated = this.centerChannel.interpolate(keyframe.getTick(), Boolean.FALSE);

            current = interpolated != null && interpolated;
        }

        this.centered = new UIToggle(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER, current, (b) ->
        {
            this.centerChannel.insert(this.keyframe.getTick(), b.getValue());
        });
    }

    private Replay findReplayOwner(BaseValue value)
    {
        BaseValue current = value;

        while (current != null)
        {
            if (current instanceof Replay r)
            {
                return r;
            }

            current = current.getParent();
        }

        return null;
    }

    private FormProperties findFormProperties(BaseValue value)
    {
        BaseValue current = value;

        while (current != null)
        {
            if (current instanceof FormProperties formProperties)
            {
                return formProperties;
            }

            current = current.getParent();
        }

        return null;
    }

    @Override
    public void update()
    {
        super.update();

        if (!this.value.isActivelyEditing() && !this.value.isDragging())
        {
            this.value.setValue(this.keyframe.getValue());
        }

        this.handles.setKeyframe(this.keyframe);
        this.handles.update();
    }

    private Replay findReplay(BaseValue value)
    {
        BaseValue current = value;

        while (current != null)
        {
            if (current instanceof ReplayKeyframes keyframes)
            {
                BaseValue parent = keyframes.getParent();

                if (parent instanceof Replay r)
                {
                    return r;
                }
            }

            current = current.getParent();
        }

        return null;
    }

    private Film findFilm(BaseValue value)
    {
        BaseValue current = value;

        while (current != null)
        {
            if (current instanceof Film filmValue)
            {
                return filmValue;
            }

            current = current.getParent();
        }

        return null;
    }
}
