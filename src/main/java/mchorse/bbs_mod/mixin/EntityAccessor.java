package mchorse.bbs_mod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor
{
    @Invoker("setFlag")
    void invokeSetFlag(int mask, boolean value);

    @Mixin(LivingEntity.class)
    public interface LivingEntityAccessor
    {
        @Invoker("setLivingFlag")
        void invokeSetLivingFlag(int mask, boolean value);
    }
}
