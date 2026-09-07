package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

public class MolangHelper
{
    public static void registerVars(MolangParser parser)
    {
        parser.register("query.anim_time");
        parser.register("query.life_time");
        parser.register("query.ground_speed");
        parser.register("query.yaw_speed");

        /* Additional Chameleon specific variables */
        parser.register("query.head_yaw");
        parser.register("query.head_pitch");

        parser.register("query.velocity");
        parser.register("query.age");

        /* Cool joystick variables */
        parser.register("joystick.l_x");
        parser.register("joystick.l_y");
        parser.register("joystick.r_x");
        parser.register("joystick.r_y");
        parser.register("joystick.l_trigger");
        parser.register("joystick.r_trigger");
        parser.register("extra1.x");
        parser.register("extra1.y");
        parser.register("extra2.x");
        parser.register("extra2.y");
    }

    public static void setMolangVariables(MolangParser parser, IEntity target, float frame, float transition)
    {
        double dx = 0;
        double dz = 0;
        double yawSpeed = 0;
        double headYaw = 0;
        double headPitch = 0;
        double velocity = 0;
        double age = 0;
        float limbSwingAmount = 0;
        float limbSwing = 0;
        float lifeTime = frame;

        if (target != null)
        {
            float yawHead = Lerps.lerp(target.getPrevHeadYaw(), target.getHeadYaw(), transition);
            float bodyYaw = Lerps.lerp(target.getPrevBodyYaw(), target.getBodyYaw(), transition);

            dx = target.getVelocity().x;
            dz = target.getVelocity().z;
            yawSpeed = Lerps.lerp(target.getPrevBodyYaw() - target.getPrevPrevBodyYaw(), target.getBodyYaw() - target.getPrevBodyYaw(), transition);
            headYaw = yawHead - bodyYaw;
            headPitch = Lerps.lerp(target.getPrevPitch(), target.getPitch(), transition);
            velocity = Math.sqrt(dx * dx + target.getVelocity().y * target.getVelocity().y + dz * dz);
            limbSwingAmount = target.getLimbSpeed(transition);
            limbSwing = target.getLimbPos(transition);
            lifeTime = target.getAge() + transition;

            /* There is still a tiny bit of vertical velocity (gravity) when an
             * entity stands still, so set it to zero in that case */
            if (target.isOnGround() && target.getVelocity().y < 0 && (Math.abs(dx) < 0.001 || Math.abs(dz) < 0.001))
            {
                velocity = 0;
            }

            age = target.getAge() + transition;

            float[] prev = target.getPrevExtraVariables();
            float[] sticks = target.getExtraVariables();

            parser.setValue("joystick.l_x", Lerps.lerp(prev[0], sticks[0], transition));
            parser.setValue("joystick.l_y", Lerps.lerp(prev[1], sticks[1], transition));
            parser.setValue("joystick.r_x", Lerps.lerp(prev[2], sticks[2], transition));
            parser.setValue("joystick.r_y", Lerps.lerp(prev[3], sticks[3], transition));
            parser.setValue("joystick.l_trigger", Lerps.lerp(prev[4], sticks[4], transition));
            parser.setValue("joystick.r_trigger", Lerps.lerp(prev[5], sticks[5], transition));
            parser.setValue("extra1.x", Lerps.lerp(prev[6], sticks[6], transition));
            parser.setValue("extra1.y", Lerps.lerp(prev[7], sticks[7], transition));
            parser.setValue("extra2.x", Lerps.lerp(prev[8], sticks[8], transition));
            parser.setValue("extra2.y", Lerps.lerp(prev[9], sticks[9], transition));
        }
        else
        {
            parser.setValue("joystick.l_x", 0);
            parser.setValue("joystick.l_y", 0);
            parser.setValue("joystick.r_x", 0);
            parser.setValue("joystick.r_y", 0);
            parser.setValue("joystick.l_bumper", 0);
            parser.setValue("joystick.r_bumper", 0);
            parser.setValue("extra1.x", 0);
            parser.setValue("extra1.y", 0);
            parser.setValue("extra2.x", 0);
            parser.setValue("extra2.y", 0);
        }

        float groundSpeed = (float) Math.sqrt(dx * dx + dz * dz);

        parser.setValue("query.anim_time", frame / 20D);
        parser.setValue("query.life_time", lifeTime / 20D);
        parser.setValue("query.ground_speed", groundSpeed);
        parser.setValue("query.yaw_speed", MathUtils.toRad((float) yawSpeed));
        parser.setValue("query.head_yaw", headYaw);
        parser.setValue("query.head_pitch", headPitch);
        parser.setValue("query.velocity", velocity);
        parser.setValue("query.limb_swing", limbSwing);
        parser.setValue("query.limb_swing_amount", limbSwingAmount);
        parser.setValue("query.age", age);
    }
}