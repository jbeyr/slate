package slate.module.impl.combat;

import slate.event.ClickEvent;
import slate.event.PreMotionEvent;
import slate.mixins.impl.client.PlayerControllerMPAccessor;
import slate.module.impl.combat.autoclicker.*;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.ModeValue;
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
}
