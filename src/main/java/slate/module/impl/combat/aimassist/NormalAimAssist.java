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
import slate.mixins.impl.client.PlayerControllerMPAccessor;
import slate.module.ModuleManager;
import slate.module.impl.combat.AimAssist;
import slate.module.impl.world.targeting.TargetManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.ModeSetting;
import slate.module.setting.impl.SliderSetting;
import slate.module.setting.impl.SubMode;
import slate.utility.slate.Interpolate;
import slate.utility.slate.RayUtils;
import slate.utility.Utils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class NormalAimAssist extends SubMode<AimAssist> {

    private float maxRangeSq;
    private float minRangeSq;
    private static final float DEADZONE_ANGLE = 0.3f;

    private final SliderSetting maxRange = new SliderSetting("Max Distance", 3.2, 0d, 5d, 0.01);
    private final SliderSetting minRange = new SliderSetting("Min Distance", 0d, 0d, 3d, 0.1);
    private final SliderSetting fov = new SliderSetting("FOV", 180, 1, 360, 1);
    private final SliderSetting strength = new SliderSetting("Strength", 0.3d, 0, 0.5d, 0.01);
    private final SliderSetting yOffset = new SliderSetting("Y-Offset", 0d, -1.5, 1.5, 0.01);
    private final SliderSetting samples = new SliderSetting("Samples", 3, 3, 20, 1);
    private final ButtonSetting clickAim = new ButtonSetting("Click Aim", false);
    private final ButtonSetting aimWhileMining = new ButtonSetting("While mining", false);
    private final SliderSetting hurtTimeThreshold = new SliderSetting("Hurt Time Threshold", 0, 0, 10, 1);

    private final SortMode[] sortModes = {
        new SortMode("Lowest Health", Comparator.comparingDouble(ar -> ar.getTarget().getHealth())),
        new SortMode("Closest to FOV", Comparator.comparingDouble(this::angleToEntityCrosshairInterpolated)),
        new SortMode("Nearest Distance", Comparator.comparingDouble(ar -> ar.getTarget().getDistanceSqToEntity(mc.thePlayer)))
    };

    @Data @AllArgsConstructor
    private static class SortMode {
        private final String name;
        private final Comparator<AimResult> comparator;

        @Override public String toString() {
            return name;
        }
    }

    private final ModeSetting sortMethod = new ModeSetting("Sort", Arrays.stream(sortModes).map(sm -> sm.name).toArray(String[]::new), 0);
    private final ButtonSetting switchTargets = new ButtonSetting("Switch fighters", false);
    private final SliderSetting switchFov     = new SliderSetting("Switch FOV", 60, 1, 180, 1, switchTargets::isToggled);

    // for smoothing/accumulation
    private float accumulatedMouseDX = 0.0f;
    private float accumulatedMouseDY = 0.0f;

    // deltas to be applied by the Mixin this frame
    @Getter
    private int assistDX_toApplyThisFrame = 0;
    @Getter
    private int assistDY_toApplyThisFrame = 0;

    private Optional<AimResult> currentTarget = Optional.empty();
    private boolean pendingSwitch = false;
    private EntityLivingBase switchExclude = null;

    public NormalAimAssist(String name, @NotNull AimAssist parent) {
        super(name, parent);
        this.registerSetting(maxRange, minRange, fov, strength, yOffset, samples, clickAim, aimWhileMining, hurtTimeThreshold, sortMethod, switchTargets, switchFov);
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(minRange, maxRange);
        Utils.correctValue(switchFov, fov);

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

        boolean shouldAimThisFrame = (!clickAim.isToggled() || (mc.gameSettings.keyBindAttack != null && mc.gameSettings.keyBindAttack.isKeyDown()))
                && (aimWhileMining.isToggled() || !((PlayerControllerMPAccessor) mc.playerController).isHittingBlock());
        float frameStrength = (float)strength.getInput();

        float rawFractionalMouseDXThisFrame = 0.0f;
        float rawFractionalMouseDYThisFrame = 0.0f;

        if (shouldAimThisFrame) {

            /* 0) ───── throw away invalid target ───── */
            if (currentTarget.isPresent() &&
                    !isTargetStillValid(currentTarget.get(), partialTicks)) {
                currentTarget = Optional.empty();
            }

            /* 1) ───── perform swap requested by the autoclicker ───── */
            if (pendingSwitch) {

                Optional<AimResult> newTarget = findBestPotentialTarget(
                        partialTicks,
                        (float) switchFov.getInput(),   // narrow cone
                        switchExclude                   // exclude the entity we just hit
                );

                // Only switch when there is a DIFFERENT target to switch to.
                if (newTarget.isPresent()) {
                    currentTarget = newTarget;          // swap
                }
                // else: stay locked on the old one
                pendingSwitch = false;                  // request handled
            }


            /* 2) ───── normal acquisition fallback ───── */
            if (!currentTarget.isPresent()) {
                currentTarget = findBestPotentialTarget(          // ← CHANGED
                        partialTicks,
                        (float) fov.getInput(),                   // use normal-FOV
                        null);                                   // no exclusion
            }

            /* refresh the stored aim-vector each tick */
            if (currentTarget.isPresent()) {
                EntityLivingBase tgt = currentTarget.get().getTarget();

                // update coordinates; lost LoS / range / etc.; let acquisition pick another
                currentTarget = viableAimPointForEntity(tgt, partialTicks);
            }

            if (currentTarget.isPresent()) {

                Vec3 playerEyePos  = Interpolate.interpolatedPosEyes(partialTicks);
                Vec3 targetAimPos  = currentTarget.get().aimVec;

                double dx = targetAimPos.xCoord - playerEyePos.xCoord;
                double dy = targetAimPos.yCoord - playerEyePos.yCoord;
                double dz = targetAimPos.zCoord - playerEyePos.zCoord;

                double distHoriz = MathHelper.sqrt_double(dx * dx + dz * dz);
                if (distHoriz > 0.01) {

                    float targetYaw   = (float)(MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
                    float targetPitch = (float)-(MathHelper.atan2(dy, distHoriz) * 180.0D / Math.PI);

                    float playerYaw   = mc.thePlayer.rotationYaw;
                    float playerPitch = mc.thePlayer.rotationPitch;

                    float yawDiff   = MathHelper.wrapAngleTo180_float(targetYaw - playerYaw);
                    float pitchDiff = targetPitch - playerPitch;

                    /* Dead-zone to avoid tiny oscillations */
                    if (Math.abs(yawDiff)  < DEADZONE_ANGLE &&
                            Math.abs(pitchDiff) < DEADZONE_ANGLE) {

                        rawFractionalMouseDXThisFrame = 0;
                        rawFractionalMouseDYThisFrame = 0;
                        accumulatedMouseDX = 0;
                        accumulatedMouseDY = 0;

                    } else {

                        float yawApply   = yawDiff   * frameStrength;
                        float pitchApply = pitchDiff * frameStrength;

                        float sens = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
                        float factor = sens * sens * sens * 8.0F * 0.15F;     // deg per delta-unit

                        if (factor > 0.0001f) {
                            rawFractionalMouseDXThisFrame =  yawApply   / factor;
                            rawFractionalMouseDYThisFrame = -pitchApply / factor;
                        }
                    }
                }
            }

        } else {
            currentTarget = Optional.empty();
        }
        // ───────────────────────────────────────────────────────────────────────────────

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

    @SubscribeEvent
    public void onAutoClickerAttack(slate.event.custom.AutoclickerAttackEvent e) {
        if (!switchTargets.isToggled()) return;               // feature off?
        if (!(e.getAttacked() instanceof EntityLivingBase)) return;
        pendingSwitch = true;                                 // request swap
        switchExclude = (EntityLivingBase) e.getAttacked();   // don’t retarget same entity
    }


    private Optional<AimResult> findBestPotentialTarget(float partialTicks,
                                                        float fovCone,
                                                        EntityLivingBase exclude) {

        TargetManager tm = ModuleManager.targetManager;

        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityLivingBase)
                .map(e -> (EntityLivingBase) e)
                .filter(entity -> entity != exclude)
                .filter(entity -> entity.hurtTime <= hurtTimeThreshold.getInput())
                .filter(entity -> {
                    double dSq = Interpolate.interpolatedDistanceSqToEntity(mc.thePlayer, entity, partialTicks);
                    return minRangeSq <= dSq && dSq <= maxRangeSq && tm.isRecommendedTarget(entity);
                })
                .map(entity -> viableAimPointForEntity(entity, partialTicks))
                .flatMap(opt -> opt.map(Stream::of).orElseGet(Stream::empty))
                //  NEW – fov parameter now passed in from caller (normal or switch-FOV)
                .filter(ar -> isWithinFOVInterpolated(fovCone, partialTicks, ar.getAimVec()))
                .limit(5)
                .min(sortModes[(int)sortMethod.getInput()].comparator);
    }

    private Optional<AimResult> findBestPotentialTarget(float partialTicks) {
        return findBestPotentialTarget(partialTicks, (float) fov.getInput(), null);
    }

    @Data @AllArgsConstructor
    private static class AimResult {
        private Vec3 aimVec;
        private EntityLivingBase target;
        private float partialTicks;
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

        return res.map(vec3 -> new AimResult(vec3, target, partialTicks));
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


    private float angleToEntityCrosshairInterpolated(AimResult ar) {
        if (mc.thePlayer == null) return Float.MAX_VALUE;

        Vec3 playerEyePos = Interpolate.interpolatedPosEyes(ar.partialTicks);
        if (playerEyePos == null) return Float.MAX_VALUE;

        float playerViewYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * ar.partialTicks;
        float playerViewPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * ar.partialTicks;

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

    private boolean isTargetStillValid(AimResult ar, float partialTicks) {
        EntityLivingBase e = ar.getTarget();
        if (e == null || e.isDead || e.getHealth() <= 0) return false;

        // out of world list via despawn or teleport
        if (!mc.theWorld.loadedEntityList.contains(e)) return false;

        // range check
        double dSq = Interpolate.interpolatedDistanceSqToEntity(mc.thePlayer, e, partialTicks);
        if (dSq < minRangeSq || dSq > maxRangeSq) return false;

        // keep inside normal FOV so we don’t stare at someone behind
        return isWithinFOVInterpolated((float) fov.getInput(), partialTicks, ar.getAimVec());
    }
}