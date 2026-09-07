package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class UIRenderingContext
{
    public Batcher2D batcher;

    private List<Runnable> runnables = new ArrayList<>();
    private final List<ModelPreviewRenderer> formPreviews = new ArrayList<>();
    private int nextFormPreview;

    public UIRenderingContext(DrawContext context)
    {
        this.batcher = new Batcher2D(context);
    }

    /**
     * Swap in the live per-frame vanilla {@link DrawContext}. Must be called once per frame (from
     * {@code UIScreen.render}) before any drawing, so the batcher draws into the {@code GuiRenderState}
     * vanilla actually composites (two-phase GUI, 1.21.6+).
     */
    public void setContext(DrawContext context)
    {
        this.nextFormPreview = 0;
        this.batcher.setContext(context);
    }

    public ModelPreviewRenderer acquireFormPreview()
    {
        /* Each queued GUI image must keep its own texture until vanilla composites
         * the frame. Reusing one target would make every cell show the last model. */
        if (this.nextFormPreview == this.formPreviews.size())
        {
            this.formPreviews.add(new ModelPreviewRenderer());
        }

        return this.formPreviews.get(this.nextFormPreview++);
    }

    public void closeFormPreviews()
    {
        for (ModelPreviewRenderer preview : this.formPreviews)
        {
            preview.close();
        }

        this.formPreviews.clear();
        this.nextFormPreview = 0;
    }

    /* Rendering context implementations */

    public TextureManager getTextures()
    {
        return BBSModClient.getTextures();
    }

    public void postRunnable(Runnable runnable)
    {
        this.runnables.add(runnable);
    }

    public void executeRunnables()
    {
        for (Runnable runnable : this.runnables)
        {
            runnable.run();
        }

        this.runnables.clear();
    }
}
