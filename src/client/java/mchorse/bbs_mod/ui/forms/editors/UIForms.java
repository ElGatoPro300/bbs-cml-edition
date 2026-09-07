package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.BodyPartManager;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.utils.StringUtils;

import net.minecraft.client.render.DiffuseLighting;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class UIForms extends UIList<UIForms.FormEntry>
{
    private Form rootForm;
    private Runnable reorderCallback;

    public UIForms(Consumer<List<FormEntry>> callback)
    {
        super(callback);

        this.multi();
        this.sorting();
        this.scroll.cancelScrolling();
    }

    public UIForms setReorderCallback(Runnable reorderCallback)
    {
        this.reorderCallback = reorderCallback;

        return this;
    }

    public void setCurrentForm(Form form)
    {
        FormEntry toSelect = null;

        for (FormEntry entry : this.list)
        {
            if (entry.getForm() == form)
            {
                toSelect = entry;

                break;
            }
        }

        if (toSelect != null)
        {
            this.setCurrentScroll(toSelect);
        }
    }

    public void setForm(Form form)
    {
        this.rootForm = form;
        this.clear();

        this.add(new FormEntry(form, null, 0));

        for (BodyPart part : form.parts.getAllTyped())
        {
            this.setupRecursively(form, part, 1);
        }
    }

    private void setupRecursively(Form parent, BodyPart part, int depth)
    {
        this.add(new FormEntry(parent, part, depth));

        if (part.getForm() == null)
        {
            return;
        }

        for (BodyPart childPart : part.getForm().parts.getAllTyped())
        {
            this.setupRecursively(part.getForm(), childPart, depth + 1);
        }
    }

    @Override
    protected void handleSwap(int from, int to)
    {
        if (from < 0 || to < 0 || from >= this.list.size() || to >= this.list.size())
        {
            return;
        }

        if (this.current.size() > 1 && this.current.contains(from))
        {
            this.handleMultiSwap(from, to);

            return;
        }

        FormEntry fromEntry = this.list.get(from);
        FormEntry toEntry = this.list.get(to);

        if (fromEntry.part == null || toEntry.part == null || fromEntry.depth != toEntry.depth)
        {
            return;
        }

        BodyPartManager manager = fromEntry.part.getManager();

        if (manager != toEntry.part.getManager())
        {
            return;
        }

        List<BodyPart> siblings = manager.getAllTyped();
        int indexFrom = siblings.indexOf(fromEntry.part);
        int indexTo = siblings.indexOf(toEntry.part);

        if (indexFrom < 0 || indexTo < 0 || indexFrom == indexTo)
        {
            return;
        }

        BodyPart moved = fromEntry.part;

        manager.moveBodyPart(moved, indexTo);

        if (this.rootForm != null)
        {
            this.setForm(this.rootForm);
        }

        for (int i = 0; i < this.list.size(); i++)
        {
            if (this.list.get(i).part == moved)
            {
                this.setIndex(i);

                break;
            }
        }

        if (this.reorderCallback != null)
        {
            this.reorderCallback.run();
        }
    }

    private void handleMultiSwap(int from, int to)
    {
        FormEntry toEntry = this.list.get(to);

        if (toEntry.part == null)
        {
            return;
        }

        BodyPartManager manager = toEntry.part.getManager();
        List<Integer> indices = new ArrayList<>(this.current);
        Collections.sort(indices);

        List<BodyPart> moving = new ArrayList<>();

        for (int idx : indices)
        {
            if (idx < 0 || idx >= this.list.size())
            {
                continue;
            }

            FormEntry entry = this.list.get(idx);

            if (entry.part != null && entry.part.getManager() == manager)
            {
                moving.add(entry.part);
            }
        }

        if (moving.isEmpty())
        {
            return;
        }

        int targetIndex = manager.getAllTyped().indexOf(toEntry.part);

        if (targetIndex < 0)
        {
            return;
        }

        for (BodyPart part : moving)
        {
            int currentIndex = manager.getAllTyped().indexOf(part);

            if (currentIndex < 0)
            {
                continue;
            }

            if (currentIndex < targetIndex)
            {
                targetIndex--;
            }
        }

        for (BodyPart part : moving)
        {
            manager.moveBodyPart(part, targetIndex);
            targetIndex++;
        }

        if (this.rootForm != null)
        {
            this.setForm(this.rootForm);
        }

        for (int i = 0; i < this.list.size(); i++)
        {
            if (moving.contains(this.list.get(i).part))
            {
                this.current.clear();

                for (int j = 0; j < this.list.size(); j++)
                {
                    if (moving.contains(this.list.get(j).part))
                    {
                        this.addIndex(j);
                    }
                }

                break;
            }
        }

        if (this.reorderCallback != null)
        {
            this.reorderCallback.run();
        }
    }

    private boolean canDragSelection()
    {
        if (this.current.isEmpty())
        {
            return false;
        }

        for (int idx : this.current)
        {
            if (idx < 0 || idx >= this.list.size() || this.list.get(idx).part == null)
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int index = this.scroll.getIndex(context.mouseX, context.mouseY);
            boolean filtering = this.isFiltering();

            if (filtering)
            {
                index = this.exists(this.filtered, index) ? this.filtered.get(index).b : -1;
            }

            if (this.exists(index))
            {
                if (this.multi && Window.isShiftPressed() && this.isSelected())
                {
                    int first = this.current.get(0);
                    int min = Math.min(first, index);
                    int max = Math.max(first, index);

                    this.current.clear();

                    for (int i = min; i <= max; i++)
                    {
                        FormEntry entry = this.list.get(i);

                        if (entry.part != null)
                        {
                            this.addIndex(i);
                        }
                    }

                    if (this.current.isEmpty())
                    {
                        this.setIndex(index);
                    }
                }
                else if (this.multi && Window.isCtrlPressed())
                {
                    this.toggleIndex(index);
                }
                else
                {
                    this.setIndex(index);
                }

                if (!filtering && this.sorting && this.canDragSelection())
                {
                    this.dragging = index;
                    this.dragTime = System.currentTimeMillis();
                }

                List<FormEntry> current = this.getCurrent();

                if (this.callback != null)
                {
                    this.callback.accept(current);

                    return true;
                }
            }
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected void renderElementPart(UIContext context, FormEntry element, int i, int x, int y, boolean hover, boolean selected)
    {
        super.renderElementPart(context, element, i, x, y, hover, selected);

        Form form = element.getForm();

        if (form != null)
        {
            x += this.area.w - 40;

            context.batcher.clip(x, y, 40, 20, context);

            y -= 10;

            Vector3f a = new Vector3f(0.85F, 0.85F, -1F).normalize();
            Vector3f b = new Vector3f(-0.85F, 0.85F, 1F).normalize();
            RenderSystem.setupLevelDiffuseLighting(a, b);
            FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);
            DiffuseLighting.disableGuiDepthLighting();

            context.batcher.unclip(context);
        }
    }

    @Override
    protected String elementToString(UIContext context, int i, FormEntry element)
    {
        return StringUtils.repeat("  ", element.depth * 2) + element.toString();
    }

    public static class FormEntry
    {
        public Form form;
        public BodyPart part;
        public int depth;

        public FormEntry(Form form, BodyPart part, int depth)
        {
            this.form = form;
            this.part = part;
            this.depth = depth;
        }

        public Form getForm()
        {
            return this.part == null ? this.form : this.part.getForm();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (super.equals(obj))
            {
                return true;
            }

            if (obj instanceof FormEntry)
            {
                FormEntry entry = (FormEntry) obj;

                return Objects.equals(this.form, entry.form)
                    && Objects.equals(this.part, entry.part)
                    && this.depth == entry.depth;
            }

            return false;
        }

        @Override
        public String toString()
        {
            if (this.part == null)
            {
                return this.form.getFormIdOrName();
            }
            else if (this.part.getForm() == null)
            {
                return "-";
            }

            return this.part.getForm().getFormIdOrName();
        }
    }
}
