package identitychain.network.packets;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;
import java.util.Random;

public class PingPacket implements Serializable {
    private final int nonce;

    public PingPacket() {
        final Random random = new Random();
        nonce = random.nextInt();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PingPacket)) {
            return false;
        }

        final PingPacket packet = (PingPacket) o;

        return nonce == packet.nonce;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(29, 97)
                .append(nonce)
                .toHashCode();
    }
}
