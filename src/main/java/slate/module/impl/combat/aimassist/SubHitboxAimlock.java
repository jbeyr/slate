package slate.module.impl.combat.aimassist;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import slate.mixins.impl.client.PlayerControllerMPAccessor;
import slate.module.ModuleManager;
import slate.module.impl.combat.AimAssist;
import slate.module.impl.world.targeting.TargetManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.ModeSetting;
import slate.module.setting.impl.SliderSetting;
import slate.module.setting.impl.SubMode;
import slate.utility.Utils;
import slate.utility.slate.Interpolate;
import slate.utility.slate.MouseManager;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

public class SubHitboxAimlock extends SubMode<AimAssist> {

    private float maxRangeSq;
    private float minRangeSq;
    private static final float DEADZONE_ANGLE = 0.3f;

    private final SliderSetting maxRange = new SliderSetting("Max Distance", 3.2, 0d, 5d, 0.01);
    private final SliderSetting minRange = new SliderSetting("Min Distance", 0d, 0d, 3d, 0.1);
    private final SliderSetting fov = new SliderSetting("FOV", 180, 1, 360, 1);
    private final SliderSetting strength = new SliderSetting("Strength", 0.35d, 0, 0.8d, 0.01);
    private final SliderSetting boxScale = new SliderSetting("Box Scale", 0.5d, 0.1d, 1.0d, 0.01);
    private final SliderSetting anchorScale = new SliderSetting("Anchor Scale", 0.6d, 0.2d, 0.99d, 0.01);
    private final SliderSetting hysteresis = new SliderSetting("Hysteresis", 0.15d, 0d, 0.6d, 0.01);
    private final SliderSetting angularHyst = new SliderSetting("Angular Hyst", 1.2d, 0d, 6d, 0.05);
    private final SliderSetting yOffset = new SliderSetting("Y-Offset", 0d, -1.5, 1.5, 0.01);
    private final ButtonSetting clickAim = new ButtonSetting("Click Aim", false);
    private final ButtonSetting aimWhileMining = new ButtonSetting("While mining", false);
    private final SliderSetting hurtTimeThreshold = new SliderSetting("Hurt Time Threshold", 0, 0, 10, 1);

    private final SortMode[] sortModes = {
            new SortMode("Lowest Health", Comparator.comparingDouble(c -> c.getTarget().getHealth())),
            new SortMode("Closest to FOV", Comparator.comparingDouble(this::angleToEntityCrosshairInterpolated)),
            new SortMode("Nearest Distance", Comparator.comparingDouble(c -> c.getTarget().getDistanceSqToEntity(mc.thePlayer)))
    };

    @Data @AllArgsConstructor
    private static class SortMode {
        private final String name;
        private final Comparator<Candidate> comparator;
        @Override public String toString() { return name; }
    }

    private final ModeSetting sortMethod = new ModeSetting("Sort", Arrays.stream(sortModes).map(sm -> sm.name).toArray(String[]::new), 0);

    private float accumulatedMouseDX = 0.0f;
    private float accumulatedMouseDY = 0.0f;
    private int assistDX_toApplyThisFrame = 0;
    private int assistDY_toApplyThisFrame = 0;

    private Optional<LockState> lock = Optional.empty();

    public SubHitboxAimlock(String name, @NotNull AimAssist parent) {
        super(name, parent);
        this.registerSetting(maxRange, minRange, fov, strength, boxScale, anchorScale, hysteresis, angularHyst, yOffset, clickAim, aimWhileMining, hurtTimeThreshold, sortMethod);
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(minRange, maxRange);
        minRangeSq = (float) (minRange.getInput() * minRange.getInput());
        maxRangeSq = (float) (maxRange.getInput() * maxRange.getInput());
    }

    public boolean isAssistEnabledAndActive() {
        return isEnabled() && mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null && (!clickAim.isToggled() || (mc.gameSettings.keyBindAttack != null && mc.gameSettings.keyBindAttack.isKeyDown()));
    }

    @Override
    public void onDisable() throws Throwable {
        super.onDisable();
        resetState();
    }

    private void resetState() {
        accumulatedMouseDX = 0.0f;
        accumulatedMouseDY = 0.0f;
        assistDX_toApplyThisFrame = 0;
        assistDY_toApplyThisFrame = 0;
        lock = Optional.empty();
    }

    /**
     * @param e render world last
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        float partial = e.partialTicks;
        if (mc.thePlayer == null || mc.theWorld == null) {
            assistDX_toApplyThisFrame = 0;
            assistDY_toApplyThisFrame = 0;
            return;
        }

        assistDX_toApplyThisFrame = 0;
        assistDY_toApplyThisFrame = 0;

        if (!isEnabled() || mc.currentScreen != null) {
            if (!isEnabled()) resetState();
            else {
                accumulatedMouseDX *= 0.85f;
                accumulatedMouseDY *= 0.85f;
                if (Math.abs(accumulatedMouseDX) < 0.01f) accumulatedMouseDX = 0;
                if (Math.abs(accumulatedMouseDY) < 0.01f) accumulatedMouseDY = 0;
            }
            lock = Optional.empty();
            return;
        }

        boolean shouldAim = (!clickAim.isToggled() || (mc.gameSettings.keyBindAttack != null && mc.gameSettings.keyBindAttack.isKeyDown())) && (aimWhileMining.isToggled() || !((PlayerControllerMPAccessor) mc.playerController).isHittingBlock());
        float s = (float) strength.getInput();

        float addDX = 0f;
        float addDY = 0f;

        if (shouldAim) {
            if (lock.isPresent() && !isTargetStillValid(lock.get().getTarget(), partial)) lock = Optional.empty();
            if (!lock.isPresent()) {
                Optional<Candidate> best = findBest(partial);
                if (best.isPresent()) lock = Optional.of(new LockState(best.get().getTarget(), false));
            }

            if (lock.isPresent()) {
                EntityLivingBase tgt = lock.get().getTarget();
                float inner = (float) (double) boxScale.getInput();
                float outer = Math.min(1f, inner + (float) (double) hysteresis.getInput());
                float anchor = Math.max(0.05f, inner * (float) (double) anchorScale.getInput());

                AxisAlignedBB boxInner = computeSubBoxInterpolated(tgt, inner, (float) yOffset.getInput(), partial);
                AxisAlignedBB boxOuter = computeSubBoxInterpolated(tgt, outer, (float) yOffset.getInput(), partial);
                AxisAlignedBB boxAnchor = computeSubBoxInterpolated(tgt, anchor, (float) yOffset.getInput(), partial);

                Vec3 eye = Interpolate.interpolatedPosEyes(partial);
                if (eye != null) {
                    Vec3 look = mc.thePlayer.getLook(partial);
                    float viewYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * partial;
                    float viewPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * partial;

                    boolean inAnchor = rayIntersectsAABB(eye, look, boxAnchor, 6.0) || angularInside(boxAnchor, eye, viewYaw, viewPitch, 0f);
                    boolean inInner = rayIntersectsAABB(eye, look, boxInner, 6.0) || angularInside(boxInner, eye, viewYaw, viewPitch, 0f);
                    boolean inOuter = rayIntersectsAABB(eye, look, boxOuter, 6.0) || angularInside(boxOuter, eye, viewYaw, viewPitch, 0f);

                    boolean lockedNow = lock.get().isLocked();
                    if (!lockedNow && inAnchor) lock = Optional.of(new LockState(tgt, true));
                    else if (lockedNow && !inOuter && !angularInside(boxOuter, eye, viewYaw, viewPitch, (float) angularHyst.getInput())) lock = Optional.of(new LockState(tgt, false));

                    boolean lockedAfter = lock.get().isLocked();

                    if (lockedAfter) {
                        accumulatedMouseDX = 0;
                        accumulatedMouseDY = 0;
                    } else {
                        Vec3 aim = aabbCenter(inInner ? boxAnchor : boxAnchor);
                        double dx = aim.xCoord - eye.xCoord;
                        double dy = aim.yCoord - eye.yCoord;
                        double dz = aim.zCoord - eye.zCoord;
                        double distHoriz = MathHelper.sqrt_double(dx * dx + dz * dz);
                        if (distHoriz > 0.01) {
                            float targetYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
                            float targetPitch = (float) -(MathHelper.atan2(dy, distHoriz) * 180.0D / Math.PI);
                            float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
                            float pitchDiff = targetPitch - mc.thePlayer.rotationPitch;

                            if (Math.abs(yawDiff) < DEADZONE_ANGLE && Math.abs(pitchDiff) < DEADZONE_ANGLE) {
                                addDX = 0;
                                addDY = 0;
                                accumulatedMouseDX = 0;
                                accumulatedMouseDY = 0;
                            } else {
                                float yawApply = yawDiff * s;
                                float pitchApply = pitchDiff * s;
                                float sens = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
                                float factor = sens * sens * sens * 8.0F * 0.15F;
                                if (factor > 0.0001f) {
                                    addDX = yawApply / factor;
                                    addDY = -pitchApply / factor;
                                }
                            }
                        }
                    }
                }
            }
        } else lock = Optional.empty();

        accumulatedMouseDX += addDX;
        accumulatedMouseDY += addDY;

        if (Math.abs(accumulatedMouseDX) >= 0.5f) {
            assistDX_toApplyThisFrame = Math.round(accumulatedMouseDX);
            accumulatedMouseDX -= assistDX_toApplyThisFrame;
        }
        if (Math.abs(accumulatedMouseDY) >= 0.5f) {
            assistDY_toApplyThisFrame = Math.round(accumulatedMouseDY);
            accumulatedMouseDY -= assistDY_toApplyThisFrame;
        }

        if (assistDX_toApplyThisFrame != 0 || assistDY_toApplyThisFrame != 0) MouseManager.offer(assistDX_toApplyThisFrame, assistDY_toApplyThisFrame, MouseManager.Priority.NORMAL);
    }

    @Data @AllArgsConstructor
    private static class LockState {
        private EntityLivingBase target;
        private boolean locked;
    }

    @Data @AllArgsConstructor
    private static class Candidate {
        private EntityLivingBase target;
        private Vec3 aimPoint;
        private float partial;
    }

    /**
     * @param partial render partial
     * @return best candidate
     */
    private Optional<Candidate> findBest(float partial) {
        TargetManager tm = ModuleManager.targetManager;
        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityLivingBase)
                .map(e -> (EntityLivingBase) e)
                .filter(entity -> entity.hurtTime <= hurtTimeThreshold.getInput())
                .filter(entity -> {
                    double dSq = Interpolate.interpolatedDistanceSqToEntity(mc.thePlayer, entity, partial);
                    return minRangeSq <= dSq && dSq <= maxRangeSq && tm.isRecommendedTarget(entity);
                })
                .map(entity -> {
                    float inner = (float) (double) boxScale.getInput();
                    float anchor = Math.max(0.05f, inner * (float) (double) anchorScale.getInput());
                    AxisAlignedBB box = computeSubBoxInterpolated(entity, anchor, (float) yOffset.getInput(), partial);
                    return new Candidate(entity, aabbCenter(box), partial);
                })
                .filter(c -> isWithinFOVInterpolated((float) fov.getInput(), c.partial, c.aimPoint))
                .limit(5)
                .min(sortModes[(int) sortMethod.getInput()].comparator);
    }

    /**
     * @param target entity
     * @param scale sub-box scale
     * @param yOff vertical offset
     * @param partial render partial
     * @return interpolated sub-box
     */
    private AxisAlignedBB computeSubBoxInterpolated(EntityLivingBase target, float scale, float yOff, float partial) {
        double ix = target.prevPosX + (target.posX - target.prevPosX) * partial;
        double iy = target.prevPosY + (target.posY - target.prevPosY) * partial;
        double iz = target.prevPosZ + (target.posZ - target.prevPosZ) * partial;

        double cx = ix;
        double cy = iy + yOff + target.height * 0.5;
        double cz = iz;

        double hx = target.width * 0.5 * scale;
        double hy = target.height * 0.5 * scale;
        double hz = target.width * 0.5 * scale;

        return new AxisAlignedBB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    private Vec3 aabbCenter(AxisAlignedBB bb) {
        return new Vec3((bb.minX + bb.maxX) * 0.5, (bb.minY + bb.maxY) * 0.5, (bb.minZ + bb.maxZ) * 0.5);
    }

    /**
     * @param e target
     * @param partial render partial
     * @return validity
     */
    private boolean isTargetStillValid(EntityLivingBase e, float partial) {
        if (e == null || e.isDead || e.getHealth() <= 0) return false;
        if (!mc.theWorld.loadedEntityList.contains(e)) return false;
        double dSq = Interpolate.interpolatedDistanceSqToEntity(mc.thePlayer, e, partial);
        if (dSq < minRangeSq || dSq > maxRangeSq) return false;
        float inner = (float) (double) boxScale.getInput();
        float anchor = Math.max(0.05f, inner * (float) (double) anchorScale.getInput());
        AxisAlignedBB box = computeSubBoxInterpolated(e, anchor, (float) yOffset.getInput(), partial);
        return isWithinFOVInterpolated((float) fov.getInput(), partial, aabbCenter(box));
    }

    /**
     * @param fovVal fov
     * @param partial render partial
     * @param aimPoint world point
     * @return within
     */
    private boolean isWithinFOVInterpolated(float fovVal, float partial, Vec3 aimPoint) {
        if (fovVal <= 0) return true;
        Vec3 eye = Interpolate.interpolatedPosEyes(partial);
        if (eye == null) return false;
        float viewYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * partial;
        float viewPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * partial;

        double dx = aimPoint.xCoord - eye.xCoord;
        double dy = aimPoint.yCoord - eye.yCoord;
        double dz = aimPoint.zCoord - eye.zCoord;
        double distHoriz = MathHelper.sqrt_double(dx * dx + dz * dz);
        if (distHoriz < 0.001) return true;

        float reqYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float reqPitch = (float) -(MathHelper.atan2(dy, distHoriz) * 180.0D / Math.PI);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(reqYaw - viewYaw));
        float pitchDiff = Math.abs(reqPitch - viewPitch);
        return (yawDiff + pitchDiff) <= fovVal / 2.0F;
    }

    /**
     * @param c candidate
     * @return angle
     */
    private float angleToEntityCrosshairInterpolated(Candidate c) {
        if (mc.thePlayer == null) return Float.MAX_VALUE;
        Vec3 eye = Interpolate.interpolatedPosEyes(c.partial);
        if (eye == null) return Float.MAX_VALUE;

        float viewYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * c.partial;
        float viewPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * c.partial;

        Vec3 p = c.getAimPoint();
        double dx = p.xCoord - eye.xCoord;
        double dy = p.yCoord - eye.yCoord;
        double dz = p.zCoord - eye.zCoord;

        double distHoriz = MathHelper.sqrt_double(dx * dx + dz * dz);
        if (distHoriz < 0.001) return 0;

        float reqYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float reqPitch = (float) -(MathHelper.atan2(dy, distHoriz) * 180.0D / Math.PI);
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(reqYaw - viewYaw));
        float pitchDiff = Math.abs(reqPitch - viewPitch);
        return yawDiff + pitchDiff;
    }

    /**
     * @param origin eye
     * @param dir look
     * @param box box
     * @param maxT max distance
     * @return hit
     */
    private boolean rayIntersectsAABB(Vec3 origin, Vec3 dir, AxisAlignedBB box, double maxT) {
        double tmin = 0.0;
        double tmax = maxT;

        if (Math.abs(dir.xCoord) < 1e-9) {
            if (origin.xCoord < box.minX || origin.xCoord > box.maxX) return false;
        } else {
            double inv = 1.0 / dir.xCoord;
            double t1 = (box.minX - origin.xCoord) * inv;
            double t2 = (box.maxX - origin.xCoord) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmax < tmin) return false;
        }

        if (Math.abs(dir.yCoord) < 1e-9) {
            if (origin.yCoord < box.minY || origin.yCoord > box.maxY) return false;
        } else {
            double inv = 1.0 / dir.yCoord;
            double t1 = (box.minY - origin.yCoord) * inv;
            double t2 = (box.maxY - origin.yCoord) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmax < tmin) return false;
        }

        if (Math.abs(dir.zCoord) < 1e-9) {
            if (origin.zCoord < box.minZ || origin.zCoord > box.maxZ) return false;
        } else {
            double inv = 1.0 / dir.zCoord;
            double t1 = (box.minZ - origin.zCoord) * inv;
            double t2 = (box.maxZ - origin.zCoord) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tmin) tmin = t1;
            if (t2 < tmax) tmax = t2;
            if (tmax < tmin) return false;
        }

        return tmax >= Math.max(0.0, tmin);
    }

    /**
     * @param box sub-box
     * @param eye eye
     * @param yaw view yaw
     * @param pitch view pitch
     * @param pad degrees
     * @return within angular bounds
     */
    private boolean angularInside(AxisAlignedBB box, Vec3 eye, float yaw, float pitch, float pad) {
        Vec3 c = aabbCenter(box);
        double dx = c.xCoord - eye.xCoord;
        double dy = c.yCoord - eye.yCoord;
        double dz = c.zCoord - eye.zCoord;
        double distHoriz = MathHelper.sqrt_double(dx * dx + dz * dz);
        double dist3 = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist3 < 1e-4) return true;

        float reqYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float reqPitch = (float) -(MathHelper.atan2(dy, distHoriz) * 180.0D / Math.PI);

        float yawErr = Math.abs(MathHelper.wrapAngleTo180_float(reqYaw - yaw));
        float pitchErr = Math.abs(reqPitch - pitch);

        double hx = (box.maxX - box.minX) * 0.5;
        double hy = (box.maxY - box.minY) * 0.5;

        float yawThresh = (float) Math.toDegrees(Math.atan(hx / Math.max(1e-4, distHoriz))) + pad;
        float pitchThresh = (float) Math.toDegrees(Math.atan(hy / Math.max(1e-4, dist3))) + pad;

        return yawErr <= yawThresh && pitchErr <= pitchThresh;
    }
}