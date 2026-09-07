package mchorse.bbs_mod.bridge;

import net.minecraft.entity.Entity;

public interface IEntityRenderState {
    void bbs$setEntity(Entity entity);
    Entity bbs$getEntity();
}
