package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.tooltips.LabelTooltip;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class UICreativeItemSelectorPanel extends UIOverlayPanel
{
    public static final int PANEL_WIDTH = 700;
    public static final int PANEL_HEIGHT = 470;

    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 2;
    private static final int SLOT_SPACING = SLOT_SIZE + SLOT_GAP;
    private static final int ITEM_RENDER_OFFSET = (SLOT_SIZE - 16) / 2;

    private static final List<ItemStack> ALL_ITEMS = createAllItems();

    private final Consumer<ItemStack> callback;
    private final ClientPlayerEntity player;

    private final UITextbox search;
    private final UIButton allButton;
    private final UIButton inventoryButton;
    private final UIItemGrid grid;
    private final UIHotbarStrip hotbar;

    private ViewMode mode = ViewMode.ALL;

    public UICreativeItemSelectorPanel(Consumer<ItemStack> callback)
    {
        super(UIKeys.INVENTORY_TITLE);

        this.callback = callback;
        this.player = MinecraftClient.getInstance().player;
        this.search = new UITextbox(200, (s) -> this.refreshItems()).placeholder(UIKeys.GENERAL_SEARCH);
        this.allButton = new UIButton(UIKeys.CREATIVE_ALL, (b) -> this.setMode(ViewMode.ALL));
        this.inventoryButton = new UIButton(UIKeys.CREATIVE_INVENTORY, (b) -> this.setMode(ViewMode.INVENTORY));
        this.grid = new UIItemGrid(this::pick);
        this.hotbar = new UIHotbarStrip(this::pick);

        this.content.w(PANEL_WIDTH).h(PANEL_HEIGHT);
        this.search.relative(this.content).xy(8, 8).w(1F, -224).h(20);
        this.allButton.relative(this.content).x(1F, -212).y(8).w(100).h(20);
        this.inventoryButton.relative(this.content).x(1F, -108).y(8).w(100).h(20);
        this.grid.relative(this.content).xy(8, 34).w(1F, -16).h(1F, -88);
        int hotbarWidth = SLOT_SPACING * 9 - SLOT_GAP;
        this.hotbar.relative(this.content).x(0.5F, -hotbarWidth / 2).y(1F, -48).w(hotbarWidth).h(SLOT_SIZE);

        this.content.add(this.search, this.allButton, this.inventoryButton, this.grid, this.hotbar);
        this.refreshItems();
        this.updateModeButtons();
    }

    private static List<ItemStack> createAllItems()
    {
        List<ItemStack> stacks = new ArrayList<>();

        for (Item item : Registries.ITEM)
        {
            ItemStack stack = new ItemStack(item);

            if (!stack.isEmpty())
            {
                stacks.add(stack);
            }
        }

        stacks.sort((a, b) -> Registries.ITEM.getId(a.getItem()).toString().compareToIgnoreCase(Registries.ITEM.getId(b.getItem()).toString()));

        return stacks;
    }

    private void setMode(ViewMode mode)
    {
        if (this.mode == mode)
        {
            return;
        }

        this.mode = mode;
        this.updateModeButtons();
        this.refreshItems();
    }

    private void updateModeButtons()
    {
        this.allButton.color(this.mode == ViewMode.ALL ? Colors.GREEN : Colors.GRAY);
        this.inventoryButton.color(this.mode == ViewMode.INVENTORY ? Colors.GREEN : Colors.GRAY);
    }

    private void refreshItems()
    {
        String filter = this.search.getText().toLowerCase(Locale.ROOT).trim();
        List<ItemStack> source = this.mode == ViewMode.ALL ? ALL_ITEMS : this.getInventoryItems();
        List<ItemStack> filtered = new ArrayList<>();

        for (ItemStack stack : source)
        {
            if (stack == null || stack.isEmpty())
            {
                continue;
            }

            if (filter.isEmpty() || this.matchesFilter(stack, filter))
            {
                filtered.add(stack);
            }
        }

        this.grid.setItems(filtered);
    }

    private boolean matchesFilter(ItemStack stack, String filter)
    {
        String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);

        if (id.contains(filter))
        {
            return true;
        }

        return stack.getName().getString().toLowerCase(Locale.ROOT).contains(filter);
    }

    private List<ItemStack> getInventoryItems()
    {
        List<ItemStack> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();

        if (this.player == null)
        {
            return result;
        }

        this.addEquipment(result, visited, this.player.getEquippedStack(EquipmentSlot.HEAD));
        this.addEquipment(result, visited, this.player.getEquippedStack(EquipmentSlot.CHEST));
        this.addEquipment(result, visited, this.player.getEquippedStack(EquipmentSlot.LEGS));
        this.addEquipment(result, visited, this.player.getEquippedStack(EquipmentSlot.FEET));
        this.addEquipment(result, visited, this.player.getEquippedStack(EquipmentSlot.OFFHAND));

        PlayerInventory inventory = this.player.getInventory();

        if (inventory != null)
        {
            for (int i = 0; i < inventory.size(); i++)
            {
                this.addEquipment(result, visited, inventory.getStack(i));
            }
        }

        return result;
    }

    private void addEquipment(List<ItemStack> result, Set<String> visited, ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        String encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, normalized).result().map(NbtElement::asString).orElse("{}");
        String key = Registries.ITEM.getId(stack.getItem()) + "|" + encoded;

        if (visited.add(key))
        {
            result.add(stack.copy());
        }
    }

    private void pick(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        if (this.callback != null)
        {
            this.callback.accept(stack.copy());
        }

        this.close();
        UIUtils.playClick();
    }

    private enum ViewMode
    {
        ALL,
        INVENTORY
    }

    private static class UIItemGrid extends UIElement
    {
        private final Consumer<ItemStack> callback;
        private final Scroll scroll;
        private final List<ItemStack> items = new ArrayList<>();
        private final LabelTooltip hoverTooltip = new LabelTooltip(IKey.constant(""), Direction.TOP);
        private int columns = 1;
        private int contentX;
        private int innerWidth;

        public UIItemGrid(Consumer<ItemStack> callback)
        {
            this.callback = callback;
            this.scroll = new Scroll(this.area, SLOT_SPACING);
            this.scroll.scrollSpeed = 24;
        }

        public void setItems(List<ItemStack> items)
        {
            this.items.clear();
            this.items.addAll(items);
            this.updateScroll();
        }

        @Override
        public void resize()
        {
            super.resize();
            this.updateScroll();
        }

        private void updateScroll()
        {
            this.innerWidth = Math.max(1, this.area.w - this.scroll.getScrollbarWidth() - 2);
            this.columns = Math.max(1, (this.innerWidth + SLOT_GAP) / SLOT_SPACING);
            int contentWidth = this.columns * SLOT_SIZE + Math.max(0, this.columns - 1) * SLOT_GAP;
            this.contentX = this.area.x + 1 + Math.max(0, (this.innerWidth - contentWidth) / 2);

            int rows = (int) Math.ceil(this.items.size() / (double) this.columns);
            this.scroll.setSize(Math.max(rows, 1));
            this.scroll.clamp();
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                int index = this.indexAt(context.mouseX, context.mouseY);

                if (index >= 0 && index < this.items.size())
                {
                    if (this.callback != null)
                    {
                        this.callback.accept(this.items.get(index));
                    }

                    return true;
                }
            }

            if (this.scroll.mouseClicked(context))
            {
                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public boolean subMouseScrolled(UIContext context)
        {
            return this.scroll.mouseScroll(context);
        }

        @Override
        public boolean subMouseReleased(UIContext context)
        {
            this.scroll.mouseReleased(context);

            return super.subMouseReleased(context);
        }

        private int indexAt(int mouseX, int mouseY)
        {
            int localX = mouseX - this.contentX;
            int localY = mouseY - this.area.y - 2 + (int) this.scroll.getScroll();

            if (localX < 0 || localY < 0)
            {
                return -1;
            }

            int col = localX / SLOT_SPACING;
            int row = localY / SLOT_SPACING;

            if (col < 0 || col >= this.columns)
            {
                return -1;
            }

            int inCellX = localX % SLOT_SPACING;
            int inCellY = localY % SLOT_SPACING;

            if (inCellX >= SLOT_SIZE || inCellY >= SLOT_SIZE)
            {
                return -1;
            }

            return row * this.columns + col;
        }

        @Override
        public void render(UIContext context)
        {
            this.scroll.drag(context);
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x66000000);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF666666);
            context.batcher.clip(this.area, context);
            ItemStack hoveredStack = ItemStack.EMPTY;

            int startRow = Math.max(0, (int) this.scroll.getScroll() / SLOT_SPACING);
            int visibleRows = this.area.h / SLOT_SPACING + 2;
            int endRow = startRow + visibleRows;

            for (int row = startRow; row <= endRow; row++)
            {
                for (int col = 0; col < this.columns; col++)
                {
                    int index = row * this.columns + col;

                    if (index >= this.items.size())
                    {
                        break;
                    }

                    int x = this.contentX + col * SLOT_SPACING;
                    int y = this.area.y + 2 + row * SLOT_SPACING - (int) this.scroll.getScroll();
                    int x2 = x + SLOT_SIZE;
                    int y2 = y + SLOT_SIZE;

                    if (y2 < this.area.y || y > this.area.ey())
                    {
                        continue;
                    }

                    boolean hover = context.mouseX >= x && context.mouseX < x2 && context.mouseY >= y && context.mouseY < y2;
                    int bg = hover ? 0x88555555 : 0x66333333;

                    context.batcher.box(x, y, x2, y2, bg);
                    context.batcher.outline(x, y, x2, y2, 0xFF666666);

                    ItemStack stack = this.items.get(index);

                    Vector3f light0 = new Vector3f(0.85F, 0.85F, -1.0F).normalize();
                    Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1.0F).normalize();
                    RenderSystem.setupGui3DDiffuseLighting(light0, light1);

                    context.batcher.getContext().drawItem(stack, x + ITEM_RENDER_OFFSET, y + ITEM_RENDER_OFFSET);
                    context.batcher.getContext().drawStackOverlay(context.batcher.getFont().getRenderer(), stack, x + ITEM_RENDER_OFFSET, y + ITEM_RENDER_OFFSET);

                    context.batcher.getContext().draw();

                    DiffuseLighting.disableGuiDepthLighting();

                    if (hover)
                    {
                        hoveredStack = stack;
                        context.batcher.box(x, y, x2, y2, 0x33FFFFFF);
                    }
                }
            }

            this.scroll.renderScrollbar(context.batcher);
            context.batcher.unclip(context);

            if (!hoveredStack.isEmpty())
            {
                this.hoverTooltip.label = IKey.constant(hoveredStack.getName().getString());
                this.tooltip(this.hoverTooltip);
            }
            else
            {
                this.removeTooltip();
            }

            super.render(context);
        }
    }

    private class UIHotbarStrip extends UIElement
    {
        private final Consumer<ItemStack> callback;

        public UIHotbarStrip(Consumer<ItemStack> callback)
        {
            this.callback = callback;
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                int index = (context.mouseX - this.area.x) / SLOT_SPACING;

                if (index >= 0 && index < 9)
                {
                    ItemStack stack = this.getStack(index);

                    if (!stack.isEmpty() && this.callback != null)
                    {
                        this.callback.accept(stack);

                        return true;
                    }
                }
            }

            return super.subMouseClicked(context);
        }

        private ItemStack getStack(int index)
        {
            if (UICreativeItemSelectorPanel.this.player == null)
            {
                return ItemStack.EMPTY;
            }

            PlayerInventory inventory = UICreativeItemSelectorPanel.this.player.getInventory();

            if (inventory == null || index < 0 || index >= 9)
            {
                return ItemStack.EMPTY;
            }

            return inventory.getStack(index);
        }

        @Override
        public void render(UIContext context)
        {
            for (int i = 0; i < 9; i++)
            {
                int x = this.area.x + i * SLOT_SPACING;
                int y = this.area.y;
                int x2 = x + SLOT_SIZE;
                int y2 = y + SLOT_SIZE;
                ItemStack stack = this.getStack(i);
                boolean hover = context.mouseX >= x && context.mouseX < x2 && context.mouseY >= y && context.mouseY < y2;

                context.batcher.box(x, y, x2, y2, stack.isEmpty() ? 0x66000000 : 0x66333333);
                context.batcher.outline(x, y, x2, y2, 0xFF666666);

                if (!stack.isEmpty())
                {
                    Vector3f light0 = new Vector3f(0.85F, 0.85F, -1.0F).normalize();
                    Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1.0F).normalize();
                    RenderSystem.setupGui3DDiffuseLighting(light0, light1);

                    context.batcher.getContext().drawItem(stack, x + ITEM_RENDER_OFFSET, y + ITEM_RENDER_OFFSET);
                    context.batcher.getContext().drawStackOverlay(context.batcher.getFont().getRenderer(), stack, x + ITEM_RENDER_OFFSET, y + ITEM_RENDER_OFFSET);

                    context.batcher.getContext().draw();

                    DiffuseLighting.disableGuiDepthLighting();
                }

                if (hover)
                {
                    context.batcher.box(x, y, x2, y2, 0x33FFFFFF);
                }
            }

            super.render(context);
        }
    }
}
