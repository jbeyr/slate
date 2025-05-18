package slate.module.impl.combat;

import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import slate.module.Module;
import slate.module.setting.impl.SliderSetting;
import slate.utility.Utils;
import slate.utility.slate.PacketManager;

import java.util.Optional;

public class Backtrack extends Module {

    private static final int SENSITIVITY = 100; // hard to configure well and if we make this more lax it'll probably ban on prediction anticheats anyway
    @Getter private boolean shouldSpoof = false;
    @Getter private float delayMs;

    private float minRangeSq;
    private float maxRangeSq;

    private final SliderSetting delayMsSlider = new SliderSetting("Delay (ms)", 1, 1, 20, 1);
    private final SliderSetting minRange = new SliderSetting("Min Range", 3.5, 0, 4, 0.02);
    private final SliderSetting maxRange = new SliderSetting("Max Range", 8, 0, 8, 0.02);


    public Backtrack() {
        super("Backtrack", category.combat);
        this.registerSetting(delayMsSlider, minRange, maxRange);
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();

        Utils.correctValue(minRange, maxRange);
        delayMs = (float) delayMsSlider.getInput();
        minRangeSq = (float) Math.pow(minRange.getInput(), 2);
        maxRangeSq = (float) Math.pow(maxRange.getInput(), 2);
    }

    public void tickCheck() {
        EntityPlayer target = PacketManager.getTarget();
        Optional<AxisAlignedBB> targetBox = PacketManager.getTargetBox();

        if (target == null) {
            shouldSpoof = false;
            return;
        }

        // compute distance once
        double dSq = mc.thePlayer.getDistanceSqToEntity(target);

        // stop if too close
        if (dSq < minRangeSq) {
            shouldSpoof = false;
            PacketManager.processWholePacketQueue();
            return;
        }

        // stop if too far
        if (dSq > maxRangeSq) {
            shouldSpoof = false;
            PacketManager.processWholePacketQueue();
            return;
        }

        // do the backtracking
        if (targetBox.isPresent()) {

            double boxedDist = distanceToAxis(targetBox.get());
            double realDist = distanceToAxis(target.getEntityBoundingBox());
            double sensitivityAdj = (double) (SENSITIVITY - 100) / 100D;

            if (boxedDist + sensitivityAdj < realDist) {
                PacketManager.processWholePacketQueue(); // send everything
            } else {
                shouldSpoof = true; // hold packets → spoof
            }
        } else {
            shouldSpoof = false; // no cached box
        }
    }


    public static double distanceToAxis(AxisAlignedBB aabb) {
        float f = (float) (mc.thePlayer.posX - (aabb.minX + aabb.maxX) / 2.0);
        float f1 = (float) (mc.thePlayer.posY - (aabb.minY + aabb.maxY) / 2.0);
        float f2 = (float) (mc.thePlayer.posZ - (aabb.minZ + aabb.maxZ) / 2.0);
        return MathHelper.sqrt_float(f * f + f1 * f1 + f2 * f2);
    }
}