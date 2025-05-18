package slate.module.impl.player;

import net.minecraft.entity.player.EntityPlayer;
import slate.module.Module;
import slate.module.setting.impl.DescriptionSetting;
import slate.utility.Utils;
import slate.utility.slate.ActionCoordinator;

public class AutoWeapon extends Module {

    public AutoWeapon() {
        super("Auto Weapon", category.world);
        this.registerSetting(new DescriptionSetting("Configure your weapons in the Settings tab."));
    }

    public void swapToWeapon() {
        EntityPlayer me = mc.thePlayer;
        int slot = Utils.getWeapon();
        if (slot == -1) return;
        if (me.inventory.currentItem != slot) {
            me.inventory.currentItem = slot;
        }
    }
}
