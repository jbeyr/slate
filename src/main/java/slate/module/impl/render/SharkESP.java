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

public class SharkESP extends Module {

    private static final float MAX_OPACITY = 0.9F;
    private static final float MIN_OPACITY = 0.1F;
    private static final float CRITICAL_HEALTH = 0.15f;

    private float activationRadiusSqr;

    private final SliderSetting lowHealthThreshold = new SliderSetting("HP %", 0.7f, 0f, 1f, 0.02);
    private final SliderSetting activationRadius = new SliderSetting("Range", 20, 1, 50, 1);

    public SharkESP() {
        super("Shark ESP", category.render);
        this.registerSetting(lowHealthThreshold);
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();

        activationRadiusSqr = (float) Math.pow(activationRadius.getInput(), 2);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderWorldLastEvent event) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer me = mc.thePlayer;
        float partialTicks = event.partialTicks;

        List<EntityPlayer> nearbyPlayers = new ArrayList<EntityPlayer>();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == me) continue;

            double distanceSqr = me.getDistanceSqToEntity(player);
            if (distanceSqr > activationRadiusSqr) continue;
            nearbyPlayers.add(player);
        }

        if (nearbyPlayers.isEmpty()) return;

        for (EntityPlayer player : nearbyPlayers) {
            if (!player.isEntityAlive()) continue;

            float healthRatio = player.getHealth() / player.getMaxHealth();
            if (healthRatio > (float)lowHealthThreshold.getInput()) continue;

            float[] color = calculateColor(healthRatio);
            float opacity = calculateOpacity(healthRatio);

            SlantRenderUtils.draw3dEntityESP(player, partialTicks, color[0], color[1], color[2], opacity);
        }
    }

    private float[] calculateColor(float healthRatio) {
        float red = 1.0f;
        float green, blue;

        if (healthRatio <= CRITICAL_HEALTH) {
            green = 0.0f;
            blue = 0.0f;
        } else { // interpolate between yellow and red for health above critical threshold
            float normalizedHealth = (healthRatio - CRITICAL_HEALTH) / ((float)lowHealthThreshold.getInput() - CRITICAL_HEALTH);
            green = normalizedHealth * normalizedHealth; // quadratic fn - steeper color dropoff
            blue = 0.0f;
        }

        return new float[]{red, green, blue};
    }

    private float calculateOpacity(float healthRatio) {
        float normalizedHealth;
        if (healthRatio <= CRITICAL_HEALTH) {
            normalizedHealth = 0;
        } else {
            normalizedHealth = (healthRatio - CRITICAL_HEALTH) / ((float)lowHealthThreshold.getInput() - CRITICAL_HEALTH);
        }
        float opacityFactor = 1 - (float) Math.pow(normalizedHealth, 2); // quadratic fn - steeper color dropoff
        return MIN_OPACITY + (MAX_OPACITY - MIN_OPACITY) * opacityFactor;
    }
}