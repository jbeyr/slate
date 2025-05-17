package slate.utility.slate;

import org.jetbrains.annotations.NotNull;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Optional;

public class RayUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static Optional<Vec3> nearestVisiblePointOnHitboxFromMyEyes(Entity target, float partialTicks, int samples) {
        if (mc.thePlayer == null || target == null) {
            return Optional.empty();
        }

        Vec3 intPosMe = Interpolate.interpolatedPos(mc.thePlayer, partialTicks);
        Vec3 intEyePosMe = intPosMe.addVector(0, mc.thePlayer.getEyeHeight(), 0);
        Vec3 intPosEn = Interpolate.interpolatedPos(target, partialTicks);
        AxisAlignedBB entityBox = target.getEntityBoundingBox();

        AxisAlignedBB interpolatedBox = entityBox
                .offset(-target.posX, -target.posY, -target.posZ)
                .offset(intPosEn.xCoord, intPosEn.yCoord, intPosEn.zCoord);

        double nx = clamp(intEyePosMe.xCoord, interpolatedBox.minX, interpolatedBox.maxX);
        double ny = clamp(intEyePosMe.yCoord, interpolatedBox.minY, interpolatedBox.maxY);
        double nz = clamp(intEyePosMe.zCoord, interpolatedBox.minZ, interpolatedBox.maxZ);
        Vec3 optimalPoint = new Vec3(nx, ny, nz);

        if (hasDirectLineOfSight(intEyePosMe, optimalPoint)) {
            return Optional.of(optimalPoint);
        }

        // since we don't have direct line of sight to the optimal point, we choose a sampled point
        return rayTraceNearestVisiblePoint(target, partialTicks, samples);
    }

    /**
     * Returns the closest *visible* point (ray-trace not blocked) on the
     * target's hit-box, or Optional.empty() if every sampled point is obstructed.
     *
     * @param target entity to test
     * @return Optional containing the nearest visible Vec3, or empty if none
     */
    public static @NotNull Optional<Vec3> rayTraceNearestVisiblePoint(@NotNull Entity target,
                                                                      float partialTicks,
                                                                      int samples) {
        if (mc.theWorld == null) return Optional.empty();

        Vec3 eyePos = Interpolate.interpolatedPos(mc.thePlayer, partialTicks)
                .addVector(0, mc.thePlayer.eyeHeight, 0);

        Vec3 interpTarget = Interpolate.interpolatedPos(target, partialTicks);

        double dx = interpTarget.xCoord - target.posX;
        double dy = interpTarget.yCoord - target.posY;
        double dz = interpTarget.zCoord - target.posZ;
        AxisAlignedBB box = target.getEntityBoundingBox().offset(dx, dy, dz);

        Vec3 bestPoint = null;
        double bestDist = Double.MAX_VALUE;

        for (int xi = 0; xi <= samples; xi++) {
            double x = box.minX + (box.maxX - box.minX) * xi / samples;
            for (int yi = 0; yi <= samples; yi++) {
                double y = box.minY + (box.maxY - box.minY) * yi / samples;
                for (int zi = 0; zi <= samples; zi++) {
                    double z = box.minZ + (box.maxZ - box.minZ) * zi / samples;

                    Vec3 point = new Vec3(x, y, z);
                    MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyePos, point, false, false, false);
                    boolean visible = mop == null || mop.typeOfHit == MovingObjectPosition.MovingObjectType.MISS;

                    if (visible) {
                        double dist = eyePos.distanceTo(point);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestPoint = point;
                        }
                    }
                }
            }
        }

        return bestPoint == null ? Optional.empty() : Optional.of(bestPoint);
    }

    /**
     * @return false if there's a block (partial or full) obstructing the two vectors; true otherwise
     */
    public static boolean hasDirectLineOfSight(Vec3 from, Vec3 to) {
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(from, to, false, false, false);
        return mop == null || mop.typeOfHit == MovingObjectPosition.MovingObjectType.MISS;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}