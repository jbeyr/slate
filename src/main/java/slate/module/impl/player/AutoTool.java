package slate.module.impl.player;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import slate.module.Module;
import slate.utility.Utils;

public class AutoTool extends Module {

    public AutoTool() {
        super("Auto Tool", category.world);
    }

    public void swapToToolFor(Block b) {
        EntityPlayer me = mc.thePlayer;
        int slot = Utils.getTool(b);
        if (slot == -1) return;
        if (me.inventory.currentItem != slot) {
            me.inventory.currentItem = slot;
        }
    }
}