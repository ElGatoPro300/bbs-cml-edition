package mchorse.bbs_mod.forms.entities;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.AABB;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class StubEntity implements IEntity
{
    private World world;
    private int age;

    private Form form;
    private boolean sneaking;
    private boolean sprinting;
    private boolean swimming;
    private boolean flying;
    private boolean fallFlying;
    private boolean crawling;
    private boolean climbing;
    private boolean blocking;
    private boolean sleeping;
    private boolean riptide;
    private boolean onGround = true;
    private float fallDistance;
    private int hurtTimer;
    private int deathTime;
    private boolean usingItem;
    private int itemUseTimeLeft;
    private int fireTicks;
    private boolean particlesEnabled = true;
    private Hand activeHand = Hand.MAIN_HAND;
    private float fallFlyingTicks;
    private float prevFallFlyingTicks;

    private double prevX;
    private double prevY;
    private double prevZ;
    private double x;
    private double y;
    private double z;

    private float prevYaw;
    private float prevHeadYaw;
    private float prevPitch;
    private float prevBodyYaw;
    private float prevPrevBodyYaw;

    private float yaw;
    private float headYaw;
    private float pitch;
    private float bodyYaw;

    /** Matches {@link LivingEntity} hand-swing duration. */
    private static final int HAND_SWING_DURATION = 6;
    private boolean handSwinging;
    private int handSwingTicks;
    private float handSwingProgress;
    private float prevHandSwingProgress;

    private Vec3d velocity = Vec3d.ZERO;

    private float[] extraVariables = new float[10];
    private float[] prevExtraVariables = new float[10];
    private boolean externalPrevPosition;
    private boolean externalPrevRotation;

    private LimbAnimator limbAnimator = new LimbAnimator();
    private final Map<EquipmentSlot, ItemStack> items = new HashMap<>();
    private IEntity mountTarget;
    private IEntity riderTarget;
    private boolean sitting;

    public StubEntity(World world)
    {
        this.world = world;

        for (EquipmentSlot value : EquipmentSlot.values())
        {
            this.items.put(value, ItemStack.EMPTY);
        }
    }

    public StubEntity()
    {
        for (EquipmentSlot value : EquipmentSlot.values())
        {
            this.items.put(value, ItemStack.EMPTY);
        }
    }

    @Override
    public void setWorld(World world)
    {
        this.world = world;
    }

    @Override
    public World getWorld()
    {
        return this.world;
    }

    @Override
    public Form getForm()
    {
        return this.form;
    }

    @Override
    public void setForm(Form form)
    {
        this.form = form;
    }

    @Override
    public ItemStack getEquipmentStack(EquipmentSlot slot)
    {
        return this.items.getOrDefault(slot, ItemStack.EMPTY);
    }

    @Override
    public void setEquipmentStack(EquipmentSlot slot, ItemStack stack)
    {
        if (stack == null)
        {
            stack = ItemStack.EMPTY;
        }

        this.items.put(slot, stack);
    }

    @Override
    public int getSelectedSlot()
    {
        return 0;
    }

    @Override
    public boolean isSneaking()
    {
        return this.sneaking;
    }

    @Override
    public void setSneaking(boolean sneaking)
    {
        this.sneaking = sneaking;
    }

    @Override
    public boolean isSprinting()
    {
        return this.sprinting;
    }

    @Override
    public void setSprinting(boolean sprinting)
    {
        this.sprinting = sprinting;
    }

    @Override
    public boolean isOnGround()
    {
        return this.onGround;
    }

    @Override
    public void setOnGround(boolean ground)
    {
        this.onGround = ground;
    }

    @Override
    public void swingArm()
    {
        /* Match LivingEntity.swingHand: restart is allowed from ~half the swing
         * (~3 ticks with duration 6), same window actors get from vanilla.
         * Do not wipe progress / prev — zeroing snapped torso and arms when a
         * second swipe clip fired while the previous swing was still blending. */
        if (!this.handSwinging || this.handSwingTicks >= HAND_SWING_DURATION / 2 || this.handSwingTicks < 0)
        {
            this.handSwinging = true;
            /* Starts at -1 so the first tickHandSwing lands on 0. */
            this.handSwingTicks = -1;
        }
    }

    public boolean isHandSwinging()
    {
        return this.handSwinging;
    }

    /**
     * Film editor stubs stop {@link #update()} while paused, so {@code prevHandSwingProgress}
     * can stay high after a swipe ends. {@link #getHandSwingProgress(float)} then wraps toward
     * 1 forever (actors keep ticking {@code LivingEntity}), which blocks procedural/Gecko idle.
     * Call while the playhead is parked — does not advance an in-progress swipe.
     */
    public void settleFinishedHandSwing()
    {
        if (!this.handSwinging && this.handSwingProgress == 0F)
        {
            this.prevHandSwingProgress = 0F;
        }
    }

    @Override
    public float getHandSwingProgress(float tickDelta)
    {
        /* Just started, update() not run yet: expose the first step so paused /
         * transition-0 scrubbing is not stuck at progress 0. */
        if (this.handSwinging && this.handSwingTicks < 0 && this.handSwingProgress == 0F)
        {
            float start = tickDelta > 0F ? tickDelta : 1F;

            return start / HAND_SWING_DURATION;
        }

        if (!this.handSwinging && this.handSwingProgress == 0F && this.prevHandSwingProgress == 0F)
        {
            return 0F;
        }

        float delta = this.handSwingProgress - this.prevHandSwingProgress;

        if (delta < 0F)
        {
            delta += 1F;
        }

        return this.prevHandSwingProgress + delta * Math.max(0F, tickDelta);
    }

    private void tickHandSwing()
    {
        this.prevHandSwingProgress = this.handSwingProgress;

        if (this.handSwinging)
        {
            this.handSwingTicks += 1;

            if (this.handSwingTicks >= HAND_SWING_DURATION)
            {
                this.handSwingTicks = 0;
                this.handSwinging = false;
            }
        }
        else
        {
            this.handSwingTicks = 0;
        }

        this.handSwingProgress = this.handSwingTicks < 0
            ? 0F
            : (float) this.handSwingTicks / (float) HAND_SWING_DURATION;
    }

    @Override
    public int getAge()
    {
        return this.age;
    }

    @Override
    public void setAge(int ticks)
    {
        this.age = ticks;
    }

    @Override
    public float getFallDistance()
    {
        return this.fallDistance;
    }

    @Override
    public void setFallDistance(float fallDistance)
    {
        this.fallDistance = fallDistance;
    }

    @Override
    public int getHurtTimer()
    {
        return this.hurtTimer;
    }

    @Override
    public void setHurtTimer(int hurtTimer)
    {
        this.hurtTimer = hurtTimer;
    }

    @Override
    public int getDeathTime()
    {
        return this.deathTime;
    }

    @Override
    public void setDeathTime(int deathTime)
    {
        this.deathTime = deathTime;
    }

    @Override
    public boolean isUsingItem()
    {
        return this.usingItem;
    }

    @Override
    public void setUsingItem(boolean usingItem)
    {
        this.usingItem = usingItem;
    }

    @Override
    public int getItemUseTimeLeft()
    {
        return this.itemUseTimeLeft;
    }

    @Override
    public void setItemUseTimeLeft(int itemUseTimeLeft)
    {
        this.itemUseTimeLeft = itemUseTimeLeft;
    }

    @Override
    public int getFireTicks()
    {
        return this.fireTicks;
    }

    @Override
    public void setFireTicks(int fireTicks)
    {
        this.fireTicks = fireTicks;
    }

    @Override
    public boolean isParticlesEnabled()
    {
        return this.particlesEnabled;
    }

    @Override
    public void setParticlesEnabled(boolean particlesEnabled)
    {
        this.particlesEnabled = particlesEnabled;
    }

    @Override
    public Hand getActiveHand()
    {
        return this.activeHand;
    }

    @Override
    public void setActiveHand(Hand hand)
    {
        this.activeHand = hand == null ? Hand.MAIN_HAND : hand;
    }

    @Override
    public double getX()
    {
        return this.x;
    }

    @Override
    public double getPrevX()
    {
        return this.prevX;
    }

    @Override
    public void setPrevX(double x)
    {
        this.prevX = x;
        this.externalPrevPosition = true;
    }

    @Override
    public double getY()
    {
        return this.y;
    }

    @Override
    public double getPrevY()
    {
        return this.prevY;
    }

    @Override
    public void setPrevY(double y)
    {
        this.prevY = y;
        this.externalPrevPosition = true;
    }

    @Override
    public double getZ()
    {
        return this.z;
    }

    @Override
    public double getPrevZ()
    {
        return this.prevZ;
    }

    @Override
    public void setPrevZ(double z)
    {
        this.prevZ = z;
        this.externalPrevPosition = true;
    }

    @Override
    public void setPosition(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public double getEyeHeight()
    {
        return 1.8F * 0.9F;
    }

    @Override
    public Vec3d getVelocity()
    {
        return this.velocity;
    }

    @Override
    public void setVelocity(float x, float y, float z)
    {
        this.velocity = new Vec3d(x, y, z);
    }

    @Override
    public float getYaw()
    {
        return this.yaw;
    }

    @Override
    public float getPrevYaw()
    {
        return this.prevYaw;
    }

    @Override
    public void setYaw(float yaw)
    {
        this.yaw = yaw;
    }

    @Override
    public void setPrevYaw(float prevYaw)
    {
        this.prevYaw = prevYaw;
        this.externalPrevRotation = true;
    }

    @Override
    public float getHeadYaw()
    {
        return this.headYaw;
    }

    @Override
    public float getPrevHeadYaw()
    {
        return this.prevHeadYaw;
    }

    @Override
    public void setHeadYaw(float headYaw)
    {
        this.headYaw = headYaw;
    }

    @Override
    public void setPrevHeadYaw(float prevHeadYaw)
    {
        this.prevHeadYaw = prevHeadYaw;
        this.externalPrevRotation = true;
    }

    @Override
    public float getPitch()
    {
        return this.pitch;
    }

    @Override
    public float getPrevPitch()
    {
        return this.prevPitch;
    }

    @Override
    public void setPitch(float pitch)
    {
        this.pitch = pitch;
    }

    @Override
    public void setPrevPitch(float prevPitch)
    {
        this.prevPitch = prevPitch;
        this.externalPrevRotation = true;
    }

    @Override
    public float getBodyYaw()
    {
        return this.bodyYaw;
    }

    @Override
    public float getPrevBodyYaw()
    {
        return this.prevBodyYaw;
    }

    @Override
    public float getPrevPrevBodyYaw()
    {
        return this.prevPrevBodyYaw;
    }

    @Override
    public void setBodyYaw(float bodyYaw)
    {
        this.bodyYaw = bodyYaw;
    }

    @Override
    public void setPrevBodyYaw(float prevBodyYaw)
    {
        this.prevBodyYaw = prevBodyYaw;
        this.externalPrevRotation = true;
    }

    @Override
    public void setPrevPrevBodyYaw(float prevPrevBodyYaw)
    {
        this.prevPrevBodyYaw = prevPrevBodyYaw;
        this.externalPrevRotation = true;
    }

    @Override
    public float[] getExtraVariables()
    {
        return this.extraVariables;
    }

    @Override
    public float[] getPrevExtraVariables()
    {
        return this.prevExtraVariables;
    }

    @Override
    public AABB getPickingHitbox()
    {
        Form form = this.getForm();
        float w = 0.6F;
        float h = 1.8F;

        if (form != null && form.hitbox.get())
        {
            w = form.hitboxWidth.get();
            h = form.hitboxHeight.get();
        }

        return new AABB(
            this.getX() - w / 2, this.getY(), this.getZ() - w / 2,
            w, h, w
        );
    }

    @Override
    public void update()
    {
        float delta = (float) MathHelper.magnitude(this.x - this.prevX, 0D, this.z - this.prevZ);
        float speed = Math.min(delta * 4F, 1F);

        this.limbAnimator.updateLimbs(speed, 0.4F, 1F);

        this.tickHandSwing();
        this.age += 1;

        this.prevFallFlyingTicks = this.fallFlyingTicks;

        if (this.fallFlying)
        {
            this.fallFlyingTicks = Math.min(10F, this.fallFlyingTicks + 1F);
        }
        else
        {
            this.fallFlyingTicks = Math.max(0F, this.fallFlyingTicks - 1F);
        }

        if (!this.externalPrevPosition)
        {
            this.prevX = this.x;
            this.prevY = this.y;
            this.prevZ = this.z;
        }

        if (!this.externalPrevRotation && this.mountTarget == null)
        {
            this.prevPrevBodyYaw = this.prevBodyYaw;
            this.prevYaw = this.yaw;
            this.prevHeadYaw = this.headYaw;
            this.prevPitch = this.pitch;
            this.prevBodyYaw = this.bodyYaw;
        }

        this.externalPrevPosition = false;
        this.externalPrevRotation = false;

        for (int i = 0; i < this.extraVariables.length; i++)
        {
            this.prevExtraVariables[i] = this.extraVariables[i];
        }
    }

    @Override
    public LimbAnimator getLimbAnimator()
    {
        return this.limbAnimator;
    }

    @Override
    public float getLimbPos(float tickDelta)
    {
        return this.limbAnimator.getPos(tickDelta);
    }

    @Override
    public float getLimbSpeed(float tickDelta)
    {
        return this.limbAnimator.getSpeed(tickDelta);
    }

    @Override
    public float getLeaningPitch(float tickDelta)
    {
        return 0;
    }

    @Override
    public boolean isTouchingWater()
    {
        return false;
    }

    @Override
    public EntityPose getEntityPose()
    {
        if (this.mountTarget != null || this.sitting)
        {
            return EntityPose.SITTING;
        }

        if (this.sneaking)
        {
            return EntityPose.CROUCHING;
        }

        return EntityPose.STANDING;
    }

    @Override
    public IEntity getMountTarget()
    {
        return this.mountTarget;
    }

    @Override
    public void setMountTarget(IEntity mountTarget)
    {
        this.mountTarget = mountTarget;
    }

    @Override
    public IEntity getRiderTarget()
    {
        return this.riderTarget;
    }

    @Override
    public void setRiderTarget(IEntity riderTarget)
    {
        this.riderTarget = riderTarget;
    }

    @Override
    public boolean isSitting()
    {
        return this.sitting;
    }

    @Override
    public void setSitting(boolean sitting)
    {
        this.sitting = sitting;
    }

    @Override
    public int getRoll()
    {
        return (int) this.fallFlyingTicks;
    }

    @Override
    public boolean isSwimming()
    {
        return this.swimming;
    }

    @Override
    public void setSwimming(boolean swimming)
    {
        this.swimming = swimming;
    }

    @Override
    public boolean isFlying()
    {
        return this.flying;
    }

    @Override
    public void setFlying(boolean flying)
    {
        this.flying = flying;
    }

    @Override
    public boolean isFallFlying()
    {
        return this.fallFlying;
    }

    @Override
    public void setFallFlying(boolean fallFlying)
    {
        this.fallFlying = fallFlying;
    }

    @Override
    public float getFallFlyingProgress(float transition)
    {
        float ticks = MathHelper.lerp(transition, this.prevFallFlyingTicks, this.fallFlyingTicks);
        float progress = MathHelper.clamp(ticks / 10F, 0F, 1F);

        return progress * progress;
    }

    @Override
    public Vec3d getRotationVec(float transition)
    {
        return Vec3d.ZERO;
    }

    @Override
    public Vec3d lerpVelocity(float transition)
    {
        return Vec3d.ZERO;
    }

    @Override
    public boolean isUsingRiptide()
    {
        return this.riptide;
    }

    @Override
    public void setRiptide(boolean riptide)
    {
        this.riptide = riptide;
    }

    @Override
    public boolean isCrawling()
    {
        return this.crawling;
    }

    @Override
    public void setCrawling(boolean crawling)
    {
        this.crawling = crawling;
    }

    @Override
    public boolean isClimbing()
    {
        return this.climbing;
    }

    @Override
    public void setClimbing(boolean climbing)
    {
        this.climbing = climbing;
    }

    @Override
    public boolean isBlocking()
    {
        return this.blocking;
    }

    @Override
    public void setBlocking(boolean blocking)
    {
        this.blocking = blocking;
    }

    @Override
    public boolean isSleeping()
    {
        return this.sleeping;
    }

    @Override
    public void setSleeping(boolean sleeping)
    {
        this.sleeping = sleeping;
    }
}
