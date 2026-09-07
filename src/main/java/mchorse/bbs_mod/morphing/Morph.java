package mchorse.bbs_mod.morphing;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.utils.RayTracing;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Morph
{
    public static final List<IEntityCaptureHandler> HANDLERS = new ArrayList<>();

    private Form form;
    public final MCEntity entity;

    /* Cached hitbox snapshot so live form edits refresh the player AABB (ActorEntity does this each tick). */
    private boolean lastHitboxEnabled;
    private float lastHitboxWidth = Float.NaN;
    private float lastHitboxHeight = Float.NaN;
    private float lastHitboxSneakMultiplier = Float.NaN;
    private float lastHitboxEyeHeight = Float.NaN;
    private boolean lastSneaking;

    public static Form getMobForm(PlayerEntity player)
    {
        HitResult hitResult = RayTracing.rayTraceEntity(player, player.getWorld(), player.getEyePos(), player.getRotationVector(), 64);

        if (hitResult.getType() == HitResult.Type.ENTITY)
        {
            Entity target = ((EntityHitResult) hitResult).getEntity();

            return captureFormFromEntity(player, target);
        }

        return null;
    }

    public static Form captureFormFromEntity(PlayerEntity player, Entity target)
    {
        if (target == null || target == player)
        {
            return null;
        }

        for (IEntityCaptureHandler handler : HANDLERS)
        {
            Form form = handler.capture(player, target);

            if (form != null)
            {
                return form;
            }
        }

        Optional<RegistryKey<EntityType<?>>> key = Registries.ENTITY_TYPE.getKey(target.getType());

        if (key.isPresent())
        {
            MobForm form = new MobForm();
            NbtCompound compound = target.writeNbt(new NbtCompound());

            for (String s : Arrays.asList(
                "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround",
                "Invulnerable", "PortalCooldown", "UUID",
                "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount",
                "FallFlying", "Brain", "Attributes", "ActiveEffects", "Passengers",
                "SleepingX", "SleepingY", "SleepingZ"
            ))
            {
                compound.remove(s);
            }

            form.mobID.set(key.get().getValue().toString());
            form.mobNBT.set(compound.toString());

            return form;
        }

        return null;
    }

    public static Morph getMorph(Entity entity)
    {
        if (entity instanceof IMorphProvider provider)
        {
            return provider.getMorph();
        }

        return null;
    }

    public Morph(Entity entity)
    {
        this.entity = new MCEntity(entity);
    }

    public Form getForm()
    {
        return this.form;
    }

    public void setForm(Form form)
    {
        if (form == null && this.form != null && this.entity.getMcEntity() instanceof PlayerEntity player)
        {
            this.form.onDemorph(player);
        }

        this.form = form;

        if (this.form != null && this.entity.getMcEntity() instanceof PlayerEntity player)
        {
            this.form.onMorph(player);
            this.form.playMain();
        }

        this.resetHitboxCache();
        this.entity.getMcEntity().calculateDimensions();
        this.syncHitboxCache();
    }

    public void update()
    {
        this.entity.update();

        if (this.form != null)
        {
            this.form.update(this.entity);
            this.updateHitboxDimensions();
        }
        else
        {
            this.resetHitboxCache();
        }
    }

    private void updateHitboxDimensions()
    {
        if (this.form == null)
        {
            return;
        }

        Entity entity = this.entity.getMcEntity();
        boolean enabled = this.form.hitbox.get();
        boolean sneaking = entity.isSneaking();
        float width = this.form.hitboxWidth.get();
        float height = this.form.hitboxHeight.get();
        float sneakMultiplier = this.form.hitboxSneakMultiplier.get();
        float eyeHeight = this.form.hitboxEyeHeight.get();

        if (enabled != this.lastHitboxEnabled
            || sneaking != this.lastSneaking
            || width != this.lastHitboxWidth
            || height != this.lastHitboxHeight
            || sneakMultiplier != this.lastHitboxSneakMultiplier
            || eyeHeight != this.lastHitboxEyeHeight)
        {
            this.lastHitboxEnabled = enabled;
            this.lastSneaking = sneaking;
            this.lastHitboxWidth = width;
            this.lastHitboxHeight = height;
            this.lastHitboxSneakMultiplier = sneakMultiplier;
            this.lastHitboxEyeHeight = eyeHeight;

            entity.calculateDimensions();
        }
    }

    private void syncHitboxCache()
    {
        if (this.form == null)
        {
            this.resetHitboxCache();

            return;
        }

        Entity entity = this.entity.getMcEntity();

        this.lastHitboxEnabled = this.form.hitbox.get();
        this.lastSneaking = entity.isSneaking();
        this.lastHitboxWidth = this.form.hitboxWidth.get();
        this.lastHitboxHeight = this.form.hitboxHeight.get();
        this.lastHitboxSneakMultiplier = this.form.hitboxSneakMultiplier.get();
        this.lastHitboxEyeHeight = this.form.hitboxEyeHeight.get();
    }

    private void resetHitboxCache()
    {
        this.lastHitboxEnabled = false;
        this.lastHitboxWidth = Float.NaN;
        this.lastHitboxHeight = Float.NaN;
        this.lastHitboxSneakMultiplier = Float.NaN;
        this.lastHitboxEyeHeight = Float.NaN;
        this.lastSneaking = false;
    }

    public NbtElement toNbt()
    {
        NbtCompound compound = new NbtCompound();

        if (this.form != null)
        {
            compound.put("Form", DataStorageUtils.toNbt(FormUtils.toData(this.form)));
        }

        return compound;
    }

    public void fromNbt(NbtCompound compound)
    {
        if (compound.contains("Form"))
        {
            MapType map = (MapType) DataStorageUtils.fromNbt(compound.getCompound("Form"));

            this.form = FormUtils.fromData(map);
        }
    }
}