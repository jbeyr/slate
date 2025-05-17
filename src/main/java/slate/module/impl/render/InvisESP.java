package slate.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import slate.module.Module;
import slate.module.setting.impl.SliderSetting;
import slate.utility.slate.SlantRenderUtils;

import java.util.ArrayList;
import java.util.List;

public class InvisESP extends Module {

    private static final float OPACITY = 0.3F;

    private float activationRadiusSqr = 50 * 50;
    private final SliderSetting activationRadius = new SliderSetting("Range", 30, 1, 50, 1);

    public InvisESP() {
        super("Invis ESP", category.render);
        this.registerSetting(activationRadius);
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();

        activationRadiusSqr = (float) Math.pow(activationRadius.getInput(), 2);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderWorldLastEvent event) {
        if(!isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer me = mc.thePlayer;
        float partialTicks = event.partialTicks;

        List<EntityPlayer> invisiblePlayers = new ArrayList<EntityPlayer>();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if(!player.isEntityAlive()) continue;
            if (player == me) continue;
            if (!player.isInvisible()) continue;

            double distanceSqr = me.getDistanceSqToEntity(player);
            if (distanceSqr > activationRadiusSqr) continue;
            invisiblePlayers.add(player);
        }

        if (invisiblePlayers.isEmpty()) return;

        for (EntityPlayer player : invisiblePlayers) {
            SlantRenderUtils.draw3dEntityESP(player, partialTicks, 1.0f, 1.0f, 1.0f, OPACITY);
        }
    }
}