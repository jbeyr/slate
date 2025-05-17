package slate.module.impl.combat.autoclicker;

import slate.module.ModuleManager;
import slate.module.impl.world.targeting.TargetManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.module.setting.impl.SubMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;
import slate.utility.ActionCoordinator;

import java.util.Optional;

public class SlantLeftAutoClicker extends SubMode<IAutoClicker> {
    private final SliderSetting minCPS;
    private final SliderSetting maxCPS;
    private final ButtonSetting triggerBot;

    public SlantLeftAutoClicker(String name, @NotNull IAutoClicker parent) {
        super(name, parent);

        minCPS = new SliderSetting("Min CPS", 12, 10, 25, 0.05);
        maxCPS = new SliderSetting("Max CPS", 14, 10, 25, 0.05);
        triggerBot = new ButtonSetting("Trigger on hover", false);
        this.registerSetting(minCPS, maxCPS, triggerBot);
    }

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static long lastClickTime = 0;
    private static long clickDelay = 0;

    public Optional<Entity> entityOnCrosshair() {
        EntityPlayer me = mc.thePlayer;
        if (me == null) return Optional.empty();

        TargetManager tm = ModuleManager.targetManager;
        MovingObjectPosition objectMouseOver = mc.objectMouseOver;

        if (objectMouseOver != null && objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            Entity entity = objectMouseOver.entityHit;
            if ((entity instanceof EntityPlayer && !tm.isRecommendedTarget(entity))
                    || (entity instanceof EntityLiving && entity.isEntityAlive())
                    || entity instanceof EntityFireball) {
                return Optional.of(entity);
            }
        }

        return Optional.empty();
    }

    public static void legitLeftClick() {
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        KeyBinding.onTick(key);
        KeyBinding.setKeyBindState(key, false);
    }

    public boolean shouldClick() {
        Optional<Entity> enInCrosshair = entityOnCrosshair();
        TargetManager tm = ModuleManager.targetManager;

        return this.isEnabled()
                && ActionCoordinator.isClickAllowed()
                && (Mouse.isButtonDown(0) || triggerBot.isToggled())
                && hasCooldownExpired()
                && (enInCrosshair.isPresent() && (!(enInCrosshair.get() instanceof EntityLivingBase) || !tm.isRecommendedTarget(enInCrosshair.get())));
    }

    public void resetClickDelay() {
        lastClickTime = System.currentTimeMillis();
        clickDelay = getRandomClickDelay();
    }

    private boolean hasCooldownExpired() {
        long currentTime = System.currentTimeMillis();
        return currentTime - lastClickTime >= clickDelay;
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

        if (shouldClick()) {
            if (ModuleManager.autoWeapon.isEnabled() && ActionCoordinator.isHotbarSelectedSlotChangeAllowed()) ModuleManager.autoWeapon.swap();
            legitLeftClick();
        }
    }
}
