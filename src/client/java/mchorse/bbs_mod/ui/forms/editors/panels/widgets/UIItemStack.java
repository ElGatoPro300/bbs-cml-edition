package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.ItemStackContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

public class UIItemStack extends UIElement
{
    private static final int OPTIONS_BUTTON_WIDTH = 20;
    private static final int OPTIONS_BUTTON_GAP = 2;

    private Consumer<ItemStack> callback;
    private ItemStack stack;
    private UIIcon optionsButton;
    private boolean opened;
    private boolean optionsOnLeft;

    public UIItemStack(Consumer<ItemStack> callback)
    {
        this.stack = ItemStack.EMPTY;
        this.callback = callback;
        this.optionsButton = new UIIcon(Icons.CHEST, (b) ->
        {
            if (this.getContext() != null)
            {
                this.getContext().replaceContextMenu(this::fillContextMenu);
            }
        });
        this.optionsButton.tooltip(UIKeys.ITEM_STACK_CONTEXT_OPTIONS);

        this.context(this::fillContextMenu);

        this.add(this.optionsButton);
        this.h(20);
    }

    private void fillContextMenu(ContextMenuManager menu)
    {
        menu.action(Icons.SPHERE, UIKeys.ITEM_STACK_CONTEXT_INVENTORY, this::openInventoryPanel);
        menu.action(Icons.SEARCH, UIKeys.ITEM_STACK_CONTEXT_ALL_ITEMS, this::openCreativeItemSelectorPanel);

        menu.action(Icons.POSE, UIKeys.ITEM_STACK_CONTEXT_HOTBAR, () ->
        {
            this.getContext().replaceContextMenu((newMenu) ->
            {
                PlayerInventory inventory = MinecraftClient.getInstance().player.getInventory();

                for (int i = 0; i < 9; i++)
                {
                    ItemStack s = inventory.getStack(i);

                    newMenu.action(new ItemStackContextAction(s, IKey.constant(s.getName().getString()), () ->
                    {
                        if (this.callback != null)
                        {
                            this.callback.accept(s);
                        }

                        this.setStack(s);
                    }));
                }
            });
        });

        menu.action(Icons.PASTE, UIKeys.ITEM_STACK_CONTEXT_PASTE, () ->
        {
            ItemStack stack = MinecraftClient.getInstance().player.getMainHandStack().copy();

            if (this.callback != null)
            {
                this.callback.accept(stack);
            }

            this.setStack(stack);
        });

        menu.action(Icons.CLOSE, UIKeys.ITEM_STACK_CONTEXT_RESET, () ->
        {
            if (this.callback != null)
            {
                this.callback.accept(ItemStack.EMPTY);
            }

            this.setStack(ItemStack.EMPTY);
        });
    }

    public void setStack(ItemStack stack)
    {
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public UIItemStack optionsOnLeft(boolean optionsOnLeft)
    {
        this.optionsOnLeft = optionsOnLeft;

        return this;
    }

    public UIItemStack optionsIcon(Icon icon)
    {
        this.optionsButton.both(icon);

        return this;
    }

    public void openInventoryPanel()
    {
        this.opened = true;

        UIPlayerInventoryPanel panel = new UIPlayerInventoryPanel((i) ->
        {
            if (this.callback != null)
            {
                this.callback.accept(i);
            }

            this.setStack(i);
        });

        panel.onClose((a) -> this.opened = false);
        UIOverlay.addOverlay(this.getContext(), panel, UIPlayerInventoryPanel.PANEL_WIDTH, UIPlayerInventoryPanel.PANEL_HEIGHT);
        UIUtils.playClick();
    }

    public void openCreativeItemSelectorPanel()
    {
        this.opened = true;

        UICreativeItemSelectorPanel panel = new UICreativeItemSelectorPanel((i) ->
        {
            if (this.callback != null)
            {
                this.callback.accept(i);
            }

            this.setStack(i);
        });

        panel.onClose((a) -> this.opened = false);
        UIOverlay.addOverlay(this.getContext(), panel, UICreativeItemSelectorPanel.PANEL_WIDTH, UICreativeItemSelectorPanel.PANEL_HEIGHT);
        UIUtils.playClick();
    }

    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            this.opened = true;

            UIItemStackOverlayPanel panel = new UIItemStackOverlayPanel((i) ->
            {
                if (this.callback != null)
                {
                    this.callback.accept(i);
                }

                this.setStack(i);
            }, this.stack);

            panel.onClose((a) -> this.opened = false);

            UIOverlay.addOverlay(this.getContext(), panel, 0.9F, 0.5F);
            UIUtils.playClick();

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void resize()
    {
        super.resize();

        int optionsX = this.optionsOnLeft ? this.area.x : this.area.ex() - OPTIONS_BUTTON_WIDTH;

        this.optionsButton.area.set(optionsX, this.area.y, OPTIONS_BUTTON_WIDTH, this.area.h);
    }

    public void render(UIContext context)
    {
        int border = this.opened ? Colors.A100 | BBSSettings.primaryColor.get() : Colors.WHITE;
        int stackAreaX = this.optionsOnLeft ? this.area.x + OPTIONS_BUTTON_WIDTH + OPTIONS_BUTTON_GAP : this.area.x;
        int stackAreaEx = this.optionsOnLeft ? this.area.ex() : this.area.ex() - OPTIONS_BUTTON_WIDTH - OPTIONS_BUTTON_GAP;
        int stackCenterX = (stackAreaX + stackAreaEx) / 2;

        context.batcher.box((float)stackAreaX, (float)this.area.y, (float)stackAreaEx, (float)this.area.ey(), border);
        context.batcher.box((float)(stackAreaX + 1), (float)(this.area.y + 1), (float)(stackAreaEx - 1), (float)(this.area.ey() - 1), -3750202);

        if (this.stack != null && !this.stack.isEmpty())
        {
            MatrixStack matrices = context.batcher.getContext().getMatrices();
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

            matrices.push();
            RenderSystem.disableDepthTest();
            consumers.setUI(true);

            Vector3f light0 = new Vector3f(0.85F, 0.85F, -1.0F).normalize();
            Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1.0F).normalize();
            RenderSystem.setupGui3DDiffuseLighting(light0, light1);

            context.batcher.getContext().drawItem(this.stack, stackCenterX - 8, this.area.my() - 8);
            context.batcher.getContext().drawStackOverlay(context.batcher.getFont().getRenderer(), this.stack, stackCenterX - 8, this.area.my() - 8);

            context.batcher.getContext().draw();

            DiffuseLighting.disableGuiDepthLighting();

            consumers.setUI(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            matrices.pop();
        }

        super.render(context);
    }
}
