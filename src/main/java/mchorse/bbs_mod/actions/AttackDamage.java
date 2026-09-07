package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.items.MobKillerItem;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

/**
 * Resolves melee attack damage from the attacker's held item / attributes so
 * film Attack clips match swords (incl. Sharpness / {@link MobKillerItem})
 * instead of a flat stub value.
 * <p>
 * Recorded clips should store the <b>actual</b> vanilla hit amount (cooldown,
 * crits, strength, enchants already baked in). Playback must not inflate that
 * back to full weapon damage.
 */
public final class AttackDamage
{
    /**
     * Stored on Attack clips for Mob Killer hits. Playback treats this (or a
     * Mob Killer in-hand) as {@link LivingEntity#kill()} for non-players.
     */
    public static final float MOB_KILLER_DAMAGE = 1000000F;

    private AttackDamage()
    {}

    public static boolean isMobKiller(ItemStack stack)
    {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof MobKillerItem;
    }

    /**
     * Vanilla {@link PlayerEntity} attack-strength scale applied to melee damage
     * ({@code 0.2 + cooldown² * 0.8}).
     */
    public static float attackStrengthScale(PlayerEntity player)
    {
        if (player == null)
        {
            return 1F;
        }

        float cooldown = player.getAttackCooldownProgress(0.5F);

        return 0.2F + cooldown * cooldown * 0.8F;
    }

    /**
     * Estimate damage from the current held item (attributes + enchants +
     * player attack cooldown). Prefer recording the real {@code damage()} amount
     * when a hit lands — this is a fallback for speculative clips.
     */
    public static float fromAttacker(LivingEntity attacker, Entity target)
    {
        if (attacker == null)
        {
            return 1F;
        }

        ItemStack stack = attacker.getMainHandStack();

        /* Mob Killer does not use attribute damage — postHit calls kill(). */
        if (isMobKiller(stack))
        {
            return MOB_KILLER_DAMAGE;
        }

        float base = (float) attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);

        if (stack.isEmpty() || !(attacker.getEntityWorld() instanceof ServerWorld serverWorld))
        {
            return scaleForAttacker(attacker, Math.max(0F, base));
        }

        if (target == null)
        {
            return scaleForAttacker(attacker, Math.max(0F, base));
        }

        DamageSource source = serverWorld.getDamageSources().mobAttack(attacker);

        if (attacker instanceof PlayerEntity player)
        {
            source = serverWorld.getDamageSources().playerAttack(player);
        }

        float enchanted = EnchantmentHelper.getDamage(serverWorld, stack, target, source, base);

        return scaleForAttacker(attacker, Math.max(0F, Math.max(base, enchanted)));
    }

    private static float scaleForAttacker(LivingEntity attacker, float damage)
    {
        if (attacker instanceof PlayerEntity player)
        {
            return damage * attackStrengthScale(player);
        }

        return damage;
    }

    /**
     * Clip damage is authoritative when present (recorded vanilla hit). Only
     * fall back to a live weapon estimate when the clip has no damage stored.
     */
    public static float resolve(float clipDamage, LivingEntity attacker, Entity target)
    {
        if (clipDamage > 0F)
        {
            return clipDamage;
        }

        return Math.max(0F, fromAttacker(attacker, target));
    }

    /**
     * Applies film attack damage, honoring Mob Killer's instakill behavior.
     */
    public static void applyHit(LivingEntity attacker, Entity target, float clipDamage)
    {
        if (target == null || attacker == null)
        {
            return;
        }

        ItemStack stack = attacker.getMainHandStack();

        if (isMobKiller(stack) || clipDamage >= MOB_KILLER_DAMAGE)
        {
            if (target instanceof LivingEntity living && !(living instanceof PlayerEntity) && attacker.getEntityWorld() instanceof ServerWorld serverWorld)
            {
                living.kill(serverWorld);
            }

            return;
        }

        float damage = resolve(clipDamage, attacker, target);

        if (damage <= 0F)
        {
            return;
        }

        if (attacker.getEntityWorld() instanceof ServerWorld serverWorld)
        {
            target.damage(serverWorld, serverWorld.getDamageSources().mobAttack(attacker), damage);
        }
    }
}
