package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.11 BlockEntityRenderState snapshot for {@link ModelBlockEntity}.
 */
public class ModelBlockEntityRenderState extends BlockEntityRenderState
{
    public ModelBlockEntity entity;
    public float tickDelta;
    public Form form;
    public Transform transform;
    public boolean lookAt;
    public boolean shadow;
    public float shadowRadius = 0.5F;
    public float shadowOpacity = 1F;
    public final List<EntityRenderState.ShadowPiece> shadowPieces = new ArrayList<>();
}
