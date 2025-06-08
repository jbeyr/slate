package slate.utility._unused.anticheats.utils.world;

import slate.Main;
import net.minecraft.util.Vec3;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.jetbrains.annotations.NotNull;

public class PlayerRotation {
    public static float getYaw(@NotNull BlockPos pos) {
        return getYaw(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    public static float getYaw(@NotNull AbstractClientPlayer from, @NotNull Vec3 pos) {
        return from.rotationYaw +
                MathHelper.wrapAngleTo180_float(
                        (float) Math.toDegrees(Math.atan2(pos.zCoord - from.posZ, pos.xCoord - from.posX)) - 90f - from.rotationYaw
                );
    }

    public static float getYaw(@NotNull Vec3 pos) {
        return getYaw(Main.mc.thePlayer, pos);
    }

    public static float getPitch(@NotNull BlockPos pos) {
        return getPitch(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    public static float getPitch(@NotNull AbstractClientPlayer from, @NotNull Vec3 pos) {
        double diffX = pos.xCoord - from.posX;
        double diffY = pos.yCoord - (from.posY + from.getEyeHeight());
        double diffZ = pos.zCoord - from.posZ;

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        return from.rotationPitch + MathHelper.wrapAngleTo180_float((float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - from.rotationPitch);
    }

    public static float getPitch(@NotNull Vec3 pos) {
        return getPitch(Main.mc.thePlayer, pos);
    }
}
