package slate.module.impl.other;

import lombok.Getter;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3EPacketTeams;
import slate.module.Module;
import slate.module.setting.impl.SliderSetting;
import slate.utility.slate.DelayedPacket;
import slate.utility.slate.PacketManager;

public class PingSpoofer extends Module {

    @Getter private float delayMs;

    private final SliderSetting delayMsSlider = new SliderSetting("Delay (ms)", 50, 1, 300, 1, "Increase your ping by this amount");


    public PingSpoofer() {
        super("Ping Spoofer", category.other);
        this.registerSetting(delayMsSlider);
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();
        delayMs = (float) delayMsSlider.getInput();
    }

    public void tickCheck() {
        for (DelayedPacket packet : PacketManager.inboundPacketsQueue) {
            if (System.currentTimeMillis() > packet.getTime() + delayMsSlider.getInput()) {
                try {
                    Packet p = packet.getPacket();
                    if (p instanceof S3EPacketTeams) {
                        if (((S3EPacketTeams) p).getPlayers() != null) {
                            p.processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                        }
                    } else if (p instanceof S0CPacketSpawnPlayer) {
                        if (((S0CPacketSpawnPlayer) p).getPlayer() != null) {
                            p.processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                        }

                    } else if (p instanceof S3BPacketScoreboardObjective) {
                        if (((S3BPacketScoreboardObjective) p).func_149337_d() != null) {
                            p.processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                        }

                    } else {

                        p.processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                    }
                } catch (Exception exception) {
                    //   System.out.println("Error! - "+packet +", "+packets.size());
                    exception.printStackTrace();
                }
                PacketManager.inboundPacketsQueue.remove(packet);
            }
        }
    }
}