package slate.utility.slate;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import slate.module.impl.player.AutoGhead;

public class ActionCoordinator {
    public static boolean isActingOnPlayerBehalfAllowed() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.isEntityAlive() && mc.currentScreen == null && mc.inGameHasFocus;
    }

    public static boolean isClickAllowed() {
        Minecraft mc = Minecraft.getMinecraft();
        return isActingOnPlayerBehalfAllowed() && !mc.thePlayer.isUsingItem() && !AutoGhead.isInProgress();
    }

    public static boolean isSwordBlockAllowed() {
        Minecraft mc = Minecraft.getMinecraft();
        return isActingOnPlayerBehalfAllowed() && !AutoGhead.isInProgress();
    }

    public static boolean isHotbarSelectedSlotChangeAllowed() {
        return isActingOnPlayerBehalfAllowed();
    }

    public static boolean isSneakingAllowed() {
        return isActingOnPlayerBehalfAllowed();
    }

    public static boolean isSprintingAllowed() {
        return isActingOnPlayerBehalfAllowed();
    }

    public static boolean isGheadAllowed() {
        return isActingOnPlayerBehalfAllowed();
    }

}
