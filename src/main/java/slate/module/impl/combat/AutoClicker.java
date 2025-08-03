package slate.module.impl.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.MinecraftForge;
import slate.event.ClickEvent;
import slate.event.PreMotionEvent;
import slate.event.custom.AutoclickerAttackEvent;
import slate.mixins.impl.client.PlayerControllerMPAccessor;
import slate.module.ModuleManager;
import slate.module.impl.combat.autoclicker.*;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.ModeValue;
import slate.module.setting.impl.SliderSetting;
import slate.module.setting.impl.SubMode;
import slate.utility.CoolDown;
import slate.utility.Utils;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class AutoClicker extends IAutoClicker {
    private final ModeValue mode;
    private final ButtonSetting breakBlocks;
    private final ButtonSetting inventoryFill;


    private final CoolDown coolDown = new CoolDown(100);

    private boolean inventoryClick = false;

    public AutoClicker() {
        super("AutoClicker", category.combat);
        this.registerSetting(mode = new ModeValue("Mode", this)
                .add(new SlantLeftAutoClicker("Slant", this))
                .add(new RavenXdAutoClicker("Raven XD", this, true, false))
                .build()
        );
        this.registerSetting(breakBlocks = new ButtonSetting("Break blocks", true));
        this.registerSetting(inventoryFill = new ButtonSetting("Inventory fill", false));
    }

    @Override
    public void onEnable() {
        mode.enable();
    }

    @Override
    public void onDisable() {
        mode.disable();
    }

    @SubscribeEvent
    public void onClick(ClickEvent event) {
        coolDown.start();
    }

    @Override
    public boolean click() {
        if (mc.currentScreen == null/* && HitSelect.canAttack()*/) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (breakBlocks.isToggled()) {
                    return false;
                } else {
                    ((PlayerControllerMPAccessor) mc.playerController).setCurBlockDamageMP(0);
                }
            }

            Utils.sendClick(0, true);
            return true;
        } else if (inventoryFill.isToggled() && mc.currentScreen instanceof GuiContainer) {
            inventoryClick = true;
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent event) {
        if (mc.currentScreen instanceof GuiContainer) {
            if (inventoryClick) {
                Utils.inventoryClick(mc.currentScreen);
            }
        }
        inventoryClick = false;
    }

    public void sendAutoclickerAttackEvent(EntityLivingBase target) {
        MinecraftForge.EVENT_BUS.post(new AutoclickerAttackEvent(target));
    }

    /**
     * Allows other modules to query the time remaining until the next scheduled click.
     * @return The remaining time in milliseconds, or -1 if not available.
     */
    public static long getRemainingClickDelay() {
        if (ModuleManager.autoClicker != null && ModuleManager.autoClicker.isEnabled()) {
            SubMode<?> currentMode = ModuleManager.autoClicker.mode.getSubModeValues().get((int)ModuleManager.autoClicker.mode.getInput());
            if (currentMode instanceof SlantLeftAutoClicker) {
                return ((SlantLeftAutoClicker) currentMode).getRemainingDelay();
            }
        }
        return -1; // return -1 to indicate timing is not available
    }
}
