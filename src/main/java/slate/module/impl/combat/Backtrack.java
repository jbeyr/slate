package slate.module.impl.combat;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.impl.world.targeting.TargetManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.Utils;
import slate.utility.slate.PacketManager;
import slate.utility.slate.SlantRenderUtils;

import java.util.Optional;
import java.util.UUID;

public class Backtrack extends Module {

    private static final double CLEANUP_DIST_SQ = 36d;

    @Getter private boolean shouldSpoof = false;

    // Effective delay currently in use (base or second-stage)
    @Getter private float delayMs;

    // Primary/base delay from slider
    private float baseDelayMs = 1f;

    // Second-stage delay from slider (ordered via Utils.correctValue)
    @Getter private float secondStageDelayMs = 60f;

    // Second-stage activation delay (how long after our hit with no return hit)
    @Getter private float secondStageStartMs = 80f;

    @Getter private float minRangeSq;
    @Getter private float maxRangeSq;

    // Tracks if we are currently in second-stage (useful for HUD/debug)
    @Getter private boolean secondStageActive = false;

    // Track combo state (our last hit vs their last hit) per-target
    private long lastOurHitAtMs = 0L;
    private long lastTheirHitAtMs = 0L;
    private UUID trackedTargetId = null;

    private final SliderSetting delayMsSlider = new SliderSetting("Delay (ms)", 1, 1, 100, 1);

    // Second-stage UI
    private final ButtonSetting secondStageEnabled = new ButtonSetting(
            "Second Stage Delay", false,
            "When you hit a target and they haven't hit you back yet, use a higher backtrack delay"
    );
    private final SliderSetting secondStageDelayMsSlider = new SliderSetting(
            "Second Stage Delay (ms)", 50, 1, 100, 1,
            "Applied while they haven't hit you back",
            secondStageEnabled::isToggled
    );
    private final SliderSetting secondStageStartMsSlider = new SliderSetting(
            "Second Stage Start (ms)", 80, 0, 500, 5,
            "Wait this long after your hit (if they haven't hit back) before enabling second stage",
            secondStageEnabled::isToggled
    );
    private final ButtonSetting secondStageLatch = new ButtonSetting(
            "Second Stage Latch", true,
            secondStageEnabled::isToggled
    );

    @Getter private boolean secondStageLatched = false;

    private final SliderSetting minRange = new SliderSetting("Min Range", 3.5, 0, 4, 0.02);
    private final SliderSetting maxRange = new SliderSetting("Max Range", 8, 0, 8, 0.02);
    private final ButtonSetting startWhenAttacked = new ButtonSetting("Start if target hits first", false);
    private final ButtonSetting renderOriginalPosition = new ButtonSetting("Render Original Position", true);
    private final ButtonSetting smartDisable = new ButtonSetting("Smart Disable", true, "Disables backtrack if the target is moving toward you so that if they get a combo they aren't out of range as a result of the backtrack lag");
    private final SliderSetting rushThreshold = new SliderSetting("Rush Threshold", -0.7, -1, 1, 0.01, "How directly the target must rush you to disable backtrack (dot product of current and past velocity)", smartDisable::isToggled);

    public Backtrack() {
        super("Backtrack", category.combat);
        this.registerSetting(
                delayMsSlider,
                secondStageEnabled, secondStageDelayMsSlider, secondStageStartMsSlider, secondStageLatch,
                minRange, maxRange,
                startWhenAttacked,
                renderOriginalPosition,
                smartDisable, rushThreshold
        );
    }

    @SubscribeEvent
    public void onCombat(AttackEntityEvent e) {
        boolean imAttacked = e.target == mc.thePlayer;
        boolean imAttacking = e.entity == mc.thePlayer;
        if (!imAttacked && !imAttacking) return;

        TargetManager tm = ModuleManager.targetManager;
        Entity other = e.target == mc.thePlayer ? e.entity : e.target;

        if (!(other instanceof EntityPlayer)) return;
        if (!tm.isRecommendedTarget(other)) return;

        EntityPlayer otherPlayer = (EntityPlayer) other;

        if (imAttacked && startWhenAttacked.isToggled()) {
            PacketManager.setTarget(Optional.of(otherPlayer));
        }

        if (imAttacking) {
            PacketManager.setTarget(Optional.of(otherPlayer));
        }

        // Track hit ordering for second-stage logic
        long now = System.currentTimeMillis();
        if (imAttacking) {
            lastOurHitAtMs = now;
            trackedTargetId = otherPlayer.getUniqueID();
        } else if (imAttacked) {
            lastTheirHitAtMs = now;
            trackedTargetId = otherPlayer.getUniqueID();
        }
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();

        Utils.correctValue(minRange, maxRange);
        // Keep second stage delay >= base via swap; you prefer this util behavior
        Utils.correctValue(delayMsSlider, secondStageDelayMsSlider);

        // Sync fields with sliders
        baseDelayMs = (float) delayMsSlider.getInput();
        secondStageDelayMs = (float) secondStageDelayMsSlider.getInput();
        secondStageStartMs = (float) secondStageStartMsSlider.getInput();

        // Default to base; tickCheck will switch to second stage if applicable
        delayMs = baseDelayMs;

        minRangeSq = (float) Math.pow(minRange.getInput(), 2);
        maxRangeSq = (float) Math.pow(maxRange.getInput(), 2);
    }

    public void tickCheck() {
        EntityPlayer target = PacketManager.getTarget();
        long now = System.currentTimeMillis();

        // Reset tracking on target change
        if (target == null) {
            trackedTargetId = null;
            secondStageLatched = false;
        } else {
            UUID currentId = target.getUniqueID();
            if (trackedTargetId == null || !currentId.equals(trackedTargetId)) {
                trackedTargetId = currentId;
                lastOurHitAtMs = 0L;
                lastTheirHitAtMs = 0L;
                secondStageLatched = false;
            }
        }

        // Determine if we're currently "ahead" in the trade
        boolean ahead = target != null && lastOurHitAtMs > 0 && lastOurHitAtMs > lastTheirHitAtMs;

        // Latching behavior (optional)
        if (secondStageEnabled.isToggled() && target != null) {
            if (secondStageLatch.isToggled()) {
                // Once active, keep it until they hit back or target changes
                if (!secondStageLatched && ahead && (now - lastOurHitAtMs) >= (long) secondStageStartMs) {
                    secondStageLatched = true;
                }
                if (!ahead) {
                    secondStageLatched = false;
                }
                secondStageActive = secondStageLatched;
            } else {
                // Original non-latched behavior
                secondStageActive = ahead && (now - lastOurHitAtMs) >= (long) secondStageStartMs;
            }
        } else {
            secondStageActive = false;
            secondStageLatched = false;
        }

        // Apply effective delay
        delayMs = secondStageActive ? secondStageDelayMs : baseDelayMs;

        if (target == null) {
            shouldSpoof = false;
            return;
        }

        double dSq = mc.thePlayer.getDistanceSqToEntity(target);

        // remove backtrack target if unreasonably far
        if (dSq > CLEANUP_DIST_SQ) {
            PacketManager.setTarget(Optional.empty());
            shouldSpoof = false;
            return;
        }

        if (dSq < minRangeSq || dSq > maxRangeSq) {
            shouldSpoof = false;
            PacketManager.forceFlushInboundQueue();
            return;
        }

        // if target is moving to us, backtracking would make them appear further away
        // assuming they hit us, we won't be able to hit back, so don't backtrack here
        if (smartDisable.isToggled()) {
            if (getDotProductBetwMyLookVecAndTargetDirection(target) < rushThreshold.getInput()) {
                shouldSpoof = false;
                PacketManager.forceFlushInboundQueue();
                return;
            }
        }

        // passed all checks; desirable to backtrack
        shouldSpoof = true;
    }

    private static double getDotProductBetwMyLookVecAndTargetDirection(EntityPlayer target) {
        Vec3 myLookVec = mc.thePlayer.getLookVec();
        Vec3 targetVelocityVec = new Vec3(
                target.posX - target.prevPosX,
                0,
                target.posZ - target.prevPosZ
        );

        Vec3 targetDirectionVec = targetVelocityVec.normalize();

        // if approaching -1 => target is moving directly to us
        // if approaching 0 => target is strafing perpendicular to us
        // if approaching 1 => target is moving away from us
        return myLookVec.dotProduct(targetDirectionVec);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        // Only render when enabled and actively spoofing
        if (!renderOriginalPosition.isToggled() || !isEnabled() || !shouldSpoof) {
            return;
        }

        // We still need a target to get position data from.
        // This check is mostly redundant because shouldSpoof would be false without a target,
        // but it's good practice for preventing NullPointerExceptions.
        EntityPlayer target = PacketManager.getTarget();
        if (target == null) {
            return;
        }

        // Get the interpolated "real" position from the packet history.
        Vec3 interpolatedPos = PacketManager.getInterpolatedTargetPos(event.partialTicks);

        // This should rarely, if ever, be null when shouldSpoof is true, but it's a safe check.
        if (interpolatedPos == null) {
            return;
        }

        // Fully opaque color: white normally, bright cyan in second stage
        final float r = secondStageActive ? 0.00f : 1.00f;
        final float g = secondStageActive ? 1.00f : 1.00f;
        final float b = secondStageActive ? 1.00f : 1.00f;

        SlantRenderUtils.drawBboxAtWorldPos(
                interpolatedPos,
                r, g, b,
                1.0f,   // outline opacity
                1.0f,   // fill opacity multiplier
                false   // don't respect depth (opaque and visible through walls)
        );
    }
}