package slate.utility.slate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;

@Getter @AllArgsConstructor
public class DelayedPacket<T extends INetHandler> {
    private final Packet<T> packet;
    private final long time;
}
