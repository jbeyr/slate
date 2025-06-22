package slate.module.impl.combat;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
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
import slate.utility.slate.DelayedPacket;
import slate.utility.slate.PacketManager;
import slate.utility.slate.SlantRenderUtils;

import java.util.Optional;

import static slate.utility.slate.PacketManager.inboundPacketsQueue;

public class Backtrack extends Module {

    private static final int SENSITIVITY = 100; // hard to configure well and if we make this more lax (lower than 100) it'll probably ban on prediction anticheats anyway
    @Getter private boolean shouldSpoof = false;
    @Getter private float delayMs;
    @Getter private float minRangeSq;
    @Getter private float maxRangeSq;

    private final SliderSetting delayMsSlider = new SliderSetting("Delay (ms)", 1, 1, 20, 1);
    private final SliderSetting minRange = new SliderSetting("Min Range", 3.5, 0, 4, 0.02);
    private final SliderSetting maxRange = new SliderSetting("Max Range", 8, 0, 8, 0.02);

    /**
     * Intended to extend the window we can attack back and trade if the target has better ping and hits us first.
     */
    private final ButtonSetting startWhenAttacked = new ButtonSetting("Start if target hits first", false);

    @SubscribeEvent
    public void onCombat(AttackEntityEvent e) {
        boolean imAttacked = e.target == mc.thePlayer;
        boolean imAttacking = e.entity == mc.thePlayer;
        if(!imAttacked && !imAttacking) return; // doesn't involve user

        TargetManager tm = ModuleManager.targetManager;
        Entity other = e.target == mc.thePlayer ? e.entity : e.target;

        if(!(other instanceof EntityPlayer)) return;
        if(!tm.isRecommendedTarget(other)) return;

        if (imAttacked && startWhenAttacked.isToggled()) {
            PacketManager.setTarget(Optional.of((EntityPlayer) other));
        }

        if (imAttacking) {
            PacketManager.setTarget(Optional.of((EntityPlayer) other));
        }

        //
        // if (tm.isRecommendedTarget(e.target) && (e.target instanceof EntityPlayer)) {
        //     PacketManager.setTarget(Optional.of((EntityPlayer) e.target));
        // } else if (stopWhenAttacked.isToggled() && e.target == mc.thePlayer && e.entity == PacketManager.getTarget() && tm.isRecommendedTarget((EntityLivingBase) e.entity)) {
        //     PacketManager.setTarget(Optional.empty());
        // }
    }

    @SubscribeEvent
    public void r1(RenderWorldLastEvent e) {
        if (Utils.nullCheckPasses()) {
            Backtrack bt = ModuleManager.backtrack;

            EntityLivingBase target = PacketManager.getTarget();

            Vec3 cttp = PacketManager.getCurrTickTargetPos();
            Vec3 pttp = PacketManager.getPrevTickTargetPos();
            if (bt.isEnabled() && target != null && cttp != null && pttp != null) {
                double dSq = mc.thePlayer.getDistanceSqToEntity(target);
                if(!(bt.getMinRangeSq() <= dSq && dSq <= bt.getMaxRangeSq())) return;
                // long currentTime = System.currentTimeMillis();

                // long cttt = PacketManager.getCurrTickTargetTime();
                // long timeSinceLastUpdate = currentTime - cttt;
                // long updateInterval = cttt - PacketManager.getPrevTickTargetTime();
                // float targetPartialTicks = (float) timeSinceLastUpdate / updateInterval;

                SlantRenderUtils.draw3dEntityESP(target, e.partialTicks, 0, 0, .5f, 0.7f);
                // LagUtils.drawTrueBacktrackHitbox(prevTickTargetPos, currTickTargetPos, targetPartialTicks, e.partialTicks, .3f, .7f, .3f, 1f);
            }
        }
    }


    public Backtrack() {
        super("Backtrack", category.combat);
        this.registerSetting(delayMsSlider, minRange, maxRange, startWhenAttacked);
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
            PacketManager.forceFlushInboundQueue();
            return;
        }

        // stop if too far
        if (dSq > maxRangeSq) {
            shouldSpoof = false;
            PacketManager.forceFlushInboundQueue();
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