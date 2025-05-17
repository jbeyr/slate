package slate.module.impl.render;

import slate.event.ReceivePacketEvent;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.DescriptionSetting;
import slate.module.setting.impl.SliderSetting;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class NoHurtCam extends Module {
    private final ButtonSetting noHurtAnimation;
    public SliderSetting multiplier;

    public NoHurtCam() {
        super("NoHurtCam", category.render);
        this.registerSetting(new DescriptionSetting("Default is 14x multiplier."));
        this.registerSetting(multiplier = new SliderSetting("Multiplier", 14, -40, 40, 1));
        this.registerSetting(noHurtAnimation = new ButtonSetting("No hurt animation", false));
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent event) {
        if (noHurtAnimation.isToggled() && event.getPacket() instanceof S0BPacketAnimation) {
            S0BPacketAnimation packet = (S0BPacketAnimation) event.getPacket();

            if (packet.getEntityID() == mc.thePlayer.getEntityId() && packet.getAnimationType() == 1)
                event.setCanceled(true);
        }
    }
}
