package slate.utility.slate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.Packet;

/**
 * A container holding a packet and the timestamp of its interception.
 */
@Getter
@RequiredArgsConstructor
public class DelayedPacket {
    private final Packet<?> packet;
    private final long timestamp;
}