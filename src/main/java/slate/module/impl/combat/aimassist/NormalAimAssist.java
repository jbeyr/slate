package slate.module.impl.combat.aimassist;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.NotNull;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.impl.combat.AimAssist;
import slate.module.impl.world.targeting.TargetManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.module.setting.impl.SubMode;
import slate.module.setting.utils.slant.Interpolate;
import slate.module.setting.utils.slant.RayUtils;
import slate.utility.Utils;

import java.util.Optional;
import java.util.stream.Stream;

public class NormalAimAssist extends SubMode<AimAssist> {

    private float maxRangeSq;
    private float minRangeSq;

    private final SliderSetting maxRange = new SliderSetting("Max Distance", 3.2, 0d, 5d, 0.01);
    private final SliderSetting minRange = new SliderSetting("Min Distance", 0d, 0d, 3d, 0.1);
    private final SliderSetting fov = new SliderSetting("FOV", 180, 1, 360, 1);
    private final SliderSetting strength = new SliderSetting("Strength", 0.3d, 0, 0.5d, 0.01);
    private final SliderSetting yOffset = new SliderSetting("Y-Offset", 0d, -1.5, 1.5, 0.01);
    private final ButtonSetting clickAim = new ButtonSetting("Click Aim", false);
    private final SliderSetting samples = new SliderSetting("Samples", 3, 3, 20, 1);

    // for smoothing/accumulation
    private float accumulatedMouseDX = 0.0f;
    private float accumulatedMouseDY = 0.0f;

    // deltas to be applied by the Mixin this frame
    @Getter
    private int assistDX_toApplyThisFrame = 0;
    @Getter
    private int assistDY_toApplyThisFrame = 0;

    private Optional<AimResult> currentTarget;

    public NormalAimAssist(String name, @NotNull AimAssist parent) {
        super(name, parent);
        this.registerSetting(maxRange, minRange, fov, strength, yOffset, clickAim, samples);
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(minRange, maxRange);

        minRangeSq = (float) (minRange.getInput()*minRange.getInput());
        maxRangeSq = (float) (maxRange.getInput()*maxRange.getInput());
    }

    public boolean isAssistEnabledAndActive() {
        return isEnabled() && mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null &&
                (!clickAim.isToggled() || (mc.gameSettings.keyBindAttack != null && mc.gameSettings.keyBindAttack.isKeyDown()));
    }

    @Override
    public void onDisable() throws Throwable {
        super.onDisable();
        resetAimAssistState();
    }

    private void resetAimAssistState() {
        accumulatedMouseDX = 0.0f;
        accumulatedMouseDY = 0.0f;
        assistDX_toApplyThisFrame = 0;
        assistDY_toApplyThisFrame = 0;
        currentTarget = Optional.empty();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        float partialTicks = event.renderTickTime;

        if (event.phase != TickEvent.Phase.START || mc.thePlayer == null || mc.theWorld == null) {
            assistDX_toApplyThisFrame = 0;
            assistDY_toApplyThisFrame = 0;
            return;
        }

        assistDX_toApplyThisFrame = 0;
        assistDY_toApplyThisFrame = 0;

        if (!isEnabled() || mc.currentScreen != null) {
            if (!isEnabled()) resetAimAssistState();
            else {
                accumulatedMouseDX *= 0.85f;
                accumulatedMouseDY *= 0.85f;
                if (Math.abs(accumulatedMouseDX) < 0.01f) accumulatedMouseDX = 0;
                if (Math.abs(accumulatedMouseDY) < 0.01f) accumulatedMouseDY = 0;
            }
            currentTarget = Optional.empty();
            return;
        }

        boolean shouldAimThisFrame = !clickAim.isToggled() || (mc.gameSettings.keyBindAttack != null && mc.gameSettings.keyBindAttack.isKeyDown());
        float frameStrength = (float)strength.getInput();

        float rawFractionalMouseDXThisFrame = 0.0f;
        float rawFractionalMouseDYThisFrame = 0.0f;

        if (shouldAimThisFrame) {
            currentTarget = findBestPotentialTarget(partialTicks);
            if (currentTarget.isPresent()) {
                Vec3 playerEyePos = Interpolate.interpolatedPosEyes(partialTicks);
                    Vec3 targetAimPos = currentTarget.get().aimVec;

                    double dx = targetAimPos.xCoord - playerEyePos.xCoord;
                    double dy = targetAimPos.yCoord - playerEyePos.yCoord;
                    double dz = targetAimPos.zCoord - playerEyePos.zCoord;

                    double distanceHorizontal = MathHelper.sqrt_double(dx * dx + dz * dz);

                    if (distanceHorizontal > 0.01) {
                        float targetYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
                        float targetPitch = (float) -(MathHelper.atan2(dy, distanceHorizontal) * 180.0D / Math.PI);

                        float playerViewYaw = mc.thePlayer.rotationYaw;
                        float playerViewPitch = mc.thePlayer.rotationPitch;

                        float yawDifference = MathHelper.wrapAngleTo180_float(targetYaw - playerViewYaw);
                        float pitchDifference = targetPitch - playerViewPitch;

                        float dynamicStrengthFactor = 1.0f;

                        float yawToApplyDegrees = yawDifference * frameStrength * dynamicStrengthFactor;
                        float pitchToApplyDegrees = pitchDifference * frameStrength * dynamicStrengthFactor;

                        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
                        float power = sensitivity * sensitivity * sensitivity * 8.0F;
                        float degreesPerMouseDeltaUnit = power * 0.15F;

                        if (degreesPerMouseDeltaUnit > 0.0001f) {
                            rawFractionalMouseDXThisFrame = yawToApplyDegrees / degreesPerMouseDeltaUnit;
                            rawFractionalMouseDYThisFrame = -pitchToApplyDegrees / degreesPerMouseDeltaUnit;
                        }
                    }
            }
        } else {
            currentTarget = Optional.empty();
        }

        accumulatedMouseDX += rawFractionalMouseDXThisFrame;
        accumulatedMouseDY += rawFractionalMouseDYThisFrame;

        if (Math.abs(accumulatedMouseDX) >= 0.5f) {
            assistDX_toApplyThisFrame = Math.round(accumulatedMouseDX);
            accumulatedMouseDX -= assistDX_toApplyThisFrame;
        }
        if (Math.abs(accumulatedMouseDY) >= 0.5f) {
            assistDY_toApplyThisFrame = Math.round(accumulatedMouseDY);
            accumulatedMouseDY -= assistDY_toApplyThisFrame;
        }

        if (!shouldAimThisFrame && isEnabled()) {
            if (Math.abs(accumulatedMouseDX) > 0.05f) {
                assistDX_toApplyThisFrame = Math.round(accumulatedMouseDX * 0.3f);
                accumulatedMouseDX -= assistDX_toApplyThisFrame;
            } else if (Math.abs(accumulatedMouseDX) < 0.05f) accumulatedMouseDX = 0;

            if (Math.abs(accumulatedMouseDY) > 0.05f) {
                assistDY_toApplyThisFrame = Math.round(accumulatedMouseDY * 0.3f);
                accumulatedMouseDY -= assistDY_toApplyThisFrame;
            } else if (Math.abs(accumulatedMouseDY) < 0.05f) accumulatedMouseDY = 0;
        }
    }


    private Optional<AimResult> findBestPotentialTarget(float partialTicks) {
        if (mc.theWorld == null || mc.thePlayer == null) return Optional.empty();
        TargetManager tm = ModuleManager.targetManager;

        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityLivingBase)
                .map(e -> (EntityLivingBase) e)
                .filter(entity -> {
                    double dSq = Interpolate.interpolatedDistanceSqToEntity(mc.thePlayer, entity, partialTicks);
                    return dSq <= maxRangeSq && dSq >= minRangeSq && tm.isRecommendedTarget(entity);
                })
                .map(entity -> viableAimPointForEntity(entity, partialTicks))
                .flatMap(opt -> opt.map(Stream::of).orElseGet(Stream::empty))
                .filter(ar -> isWithinFOVInterpolated((float) fov.getInput(), partialTicks, ar.getAimVec()))
                .limit(5)
                .min((AimResult a1, AimResult a2) -> Double.compare(angleToEntityCrosshairInterpolated(partialTicks, a1), angleToEntityCrosshairInterpolated(partialTicks, a2)));
                // ^ pick one with the smallest angle
    }

    @Data @AllArgsConstructor
    private static class AimResult {
        private Vec3 aimVec;
        private EntityLivingBase target;
    }

    private Optional<AimResult> viableAimPointForEntity(Entity entity, float partialTicks) {
        if (!(entity instanceof EntityLivingBase)) return Optional.empty();
        EntityLivingBase target = (EntityLivingBase) entity;
        Vec3 playerEyePos = Interpolate.interpolatedPosEyes(partialTicks);
        if (playerEyePos == null) return Optional.empty();

        Optional<Vec3> res;
        Vec3 yOffsetPos = Interpolate.interpolatedTargetAimPositionWithYOffset(target, partialTicks, (float)yOffset.getInput());
        if (RayUtils.hasDirectLineOfSight(playerEyePos, yOffsetPos)) {
            res = Optional.of(yOffsetPos);
        } else { // if direct point for yOffset is occluded, use visible sampled point
            res = RayUtils.nearestVisiblePointOnHitboxFromMyEyes(target, partialTicks, (int)samples.getInput());
        }

        return res.map(vec3 -> new AimResult(vec3, target));
    }

    private boolean isWithinFOVInterpolated(float fovVal, float partialTicks, Vec3 viableAimPoint) {
        if (fovVal <= 0) return true; // <= 0 disables fov check

        Vec3 playerEyePos = Interpolate.interpolatedPosEyes(partialTicks);
        if (playerEyePos == null) return false;

        float playerViewYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * partialTicks;
        float playerViewPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * partialTicks;

        double dx = viableAimPoint.xCoord - playerEyePos.xCoord;
        double dy = viableAimPoint.yCoord - playerEyePos.yCoord;
        double dz = viableAimPoint.zCoord - playerEyePos.zCoord;

        double distanceHorizontal = MathHelper.sqrt_double(dx * dx + dz * dz);
        if (distanceHorizontal < 0.001) return true; // effectively on top of us, so within fov

        float requiredYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float requiredPitch = (float) -(MathHelper.atan2(dy, distanceHorizontal) * 180.0D / Math.PI);

        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(requiredYaw - playerViewYaw));
        float pitchDiff = Math.abs(requiredPitch - playerViewPitch);

        return (yawDiff + pitchDiff) <= fovVal / 2.0F; // use sum for diamond shape (common interpretation)
    }


    private float angleToEntityCrosshairInterpolated(float partialTicks, AimResult ar) {
        if (mc.thePlayer == null) return Float.MAX_VALUE;

        Vec3 playerEyePos = Interpolate.interpolatedPosEyes(partialTicks);
        if (playerEyePos == null) return Float.MAX_VALUE;

        float playerViewYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * partialTicks;
        float playerViewPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * partialTicks;

        Vec3 targetPointToUse = ar.getAimVec();
        double dx = targetPointToUse.xCoord - playerEyePos.xCoord;
        double dy = targetPointToUse.yCoord - playerEyePos.yCoord;
        double dz = targetPointToUse.zCoord - playerEyePos.zCoord;

        double distanceHorizontal = MathHelper.sqrt_double(dx * dx + dz * dz);
        if (distanceHorizontal < 0.001) return 0; // angle is effectively 0

        float requiredYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float requiredPitch = (float) -(MathHelper.atan2(dy, distanceHorizontal) * 180.0D / Math.PI);

        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(requiredYaw - playerViewYaw));
        float pitchDiff = Math.abs(requiredPitch - playerViewPitch);
        return yawDiff + pitchDiff;
    }
}