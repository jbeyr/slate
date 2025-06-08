package slate.utility._unused.backtrack;

import lombok.Getter;
import net.minecraft.network.Packet;

@Getter
public class TimedPacket {

    private final Packet<?> packet;
    private final Cold time;
    private final long millis;

    public TimedPacket(Packet<?> packet) {
        this.packet = packet;
        this.time = new Cold();
        this.millis = System.currentTimeMillis();
    }

    public TimedPacket(final Packet<?> packet, final long millis) {
        this.packet = packet;
        this.millis = millis;
        this.time = new Cold();
    }

    public Cold getCold() {
        return getTime();
    }

}