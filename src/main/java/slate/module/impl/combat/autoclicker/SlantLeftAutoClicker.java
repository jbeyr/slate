package slate.module.impl.combat.autoclicker;

import slate.module.ModuleManager;
import slate.module.impl.world.targeting.TargetManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.module.setting.impl.SubMode;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;
import slate.utility.slate.ActionCoordinator;
import slate.utility.Utils;

import java.util.Optional;

public class SlantLeftAutoClicker extends SubMode<IAutoClicker> {
    private final SliderSetting minCPS = new SliderSetting("Min CPS", 12, 10, 25, 0.05);
    private final SliderSetting maxCPS = new SliderSetting("Max CPS", 14, 10, 25, 0.05);
    private final ButtonSetting triggerBot = new ButtonSetting("Trigger on hover", false);
    private final ButtonSetting hitFireballsOnHold = new ButtonSetting("Hit fireballs on hold", true);
    private final SliderSetting hurtTimeThreshold = new SliderSetting("Hurt Time Threshold", 0, 0, 10, 1);


    private long lastClickTime = 0;
    private long clickDelay = 0;

    public SlantLeftAutoClicker(String name, @NotNull IAutoClicker parent) {
        super(name, parent);
        this.registerSetting(minCPS, maxCPS, triggerBot, hitFireballsOnHold, hurtTimeThreshold);
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(minCPS, maxCPS);
    }

    public Optional<Entity> entityOnCrosshair() {
        if(!Utils.nullCheckPasses()) return Optional.empty();

        TargetManager tm = ModuleManager.targetManager;
        MovingObjectPosition objectMouseOver = mc.objectMouseOver;

        if (objectMouseOver != null && objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity entity = objectMouseOver.entityHit;
            if (tm.isRecommendedTarget(entity) || (entity instanceof EntityFireball && hitFireballsOnHold.isToggled() && Mouse.isButtonDown(0))) {
                return Optional.of(entity);
            }
        }

        return Optional.empty();
    }

    public void legitLeftClick(Entity target) {
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        KeyBinding.onTick(key);
        if(target instanceof EntityLivingBase) ModuleManager.autoClicker.sendAutoclickerAttackEvent((EntityLivingBase) target);
        KeyBinding.setKeyBindState(key, false);
    }

    public Optional<Entity> shouldClick() {
        Optional<Entity> enInCrosshair = entityOnCrosshair();
        TargetManager tm = ModuleManager.targetManager;

        boolean b = isEnabled()
                && ActionCoordinator.isClickAllowed()
                && (Mouse.isButtonDown(0) || triggerBot.isToggled()) // is mouse pressed or trigger bot on
                && hasCooldownExpired()
                && (
                    enInCrosshair.isPresent()
                    && (
                            !(enInCrosshair.get() instanceof EntityLivingBase /* so we can hit fireballs */)
                            || ((EntityLivingBase) enInCrosshair.get()).hurtTime <= hurtTimeThreshold.getInput() && tm.isRecommendedTarget(enInCrosshair.get())
                    )
                );

        return b ? enInCrosshair : Optional.empty();
    }

    public void resetClickDelay() {
        lastClickTime = System.currentTimeMillis();
        clickDelay = getRandomClickDelay();
    }

    private boolean hasCooldownExpired() {
        return System.currentTimeMillis() - lastClickTime >= clickDelay;
    }

    private long getRandomClickDelay() {
        long minDelay = (long)(1000 / maxCPS.getInput());
        long maxDelay = (long)(1000 / minCPS.getInput());
        return minDelay + (long)(Math.random() * (maxDelay - minDelay + 1));
    }

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        if (event.button == 0 && event.buttonstate) {  // 0 is the left mouse button; buttonstate is true if pressed
            resetClickDelay();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Optional<Entity> target = shouldClick();
        if (target.isPresent()) {
            if (ModuleManager.autoWeapon.isEnabled() && ActionCoordinator.isHotbarSelectedSlotChangeAllowed()) ModuleManager.autoWeapon.swapToWeapon();
            legitLeftClick(target.get());
        }
    }
}
