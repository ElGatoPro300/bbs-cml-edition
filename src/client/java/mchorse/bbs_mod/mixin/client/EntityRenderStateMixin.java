package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.bridge.IEntityRenderState;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements IEntityRenderState {
    @Unique
    private Entity bbs$entity;

    @Override
    public void bbs$setEntity(Entity entity) {
        this.bbs$entity = entity;
    }

    @Override
    public Entity bbs$getEntity() {
        return this.bbs$entity;
    }
}
