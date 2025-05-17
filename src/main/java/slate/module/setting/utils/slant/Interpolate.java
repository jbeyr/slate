package slate.module.setting.utils.slant;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

import static slate.Main.mc;

public class Interpolate {

    public static int scaleZeroOneRangeTo255(float f) {
        return (int)(f * 255);
    }

    public static Vec3 interpolatedPos(Entity en, double partialTicks) {
        double ix = en.lastTickPosX + (en.posX - en.lastTickPosX) * partialTicks;
        double iy = en.lastTickPosY + (en.posY - en.lastTickPosY) * partialTicks;
        double iz = en.lastTickPosZ + (en.posZ - en.lastTickPosZ) * partialTicks;
        return new Vec3(ix, iy, iz);
    }

    public static Vec3 interpolatedPos(Vec3 now, Vec3 prev, double partialTicks) {
        double ix = prev.xCoord + (now.xCoord - prev.xCoord) * partialTicks;
        double iy = prev.yCoord + (now.yCoord - prev.yCoord) * partialTicks;
        double iz = prev.zCoord + (now.zCoord - prev.zCoord) * partialTicks;
        return new Vec3(ix, iy, iz);
    }

    public static Vec3 interpolatedDifferenceFromEntities(Entity src, Entity dst, double partialTicks) {
        Vec3 sv = interpolatedPos(src, partialTicks);
        Vec3 ov = interpolatedPos(dst, partialTicks);
        return ov.subtract(sv);
    }

    public static Vec3 interpolatedDifferenceFromMeAndVector(Vec3 dst, double partialTicks) {
        EntityPlayer me = mc.thePlayer;
        return interpolatedPos(me.getPositionVector(), dst, partialTicks);
    }

    public static Vec3 interpolatedDifferenceFromMeAndEntity(Entity dst, double partialTicks) {
        EntityPlayer me = mc.thePlayer;
        return interpolatedDifferenceFromEntities(me, dst, partialTicks);
    }



    // region
    public static Vec3 interpolatedPosEyes(float partialTicks) {
        Vec3 ip = interpolatedPos(mc.thePlayer, partialTicks);
        return ip.add(new Vec3(0, mc.thePlayer.eyeHeight, 0));
    }

    public static Vec3 interpolatedTargetAimPositionWithYOffset(EntityLivingBase target, float partialTicks, float yOffset) {
        Vec3 ip = interpolatedPos(target, partialTicks);
        return new Vec3(ip.xCoord, ip.yCoord + yOffset + (target.getEyeHeight() * 0.9), ip.zCoord);
    }

    public static double interpolatedDistanceSqToEntity(Entity player, Entity target, float partialTicks) {
        Vec3 playerPos = interpolatedPos(player, partialTicks);
        Vec3 targetPos = interpolatedPos(target, partialTicks);
        return playerPos.squareDistanceTo(targetPos);
    }
    // endregion
}
