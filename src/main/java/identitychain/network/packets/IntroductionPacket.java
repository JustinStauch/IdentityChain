package identitychain.network.packets;

import java.io.Serializable;

public class IntroductionPacket implements Serializable {
    private final String ip;
    private final int port;

    public IntroductionPacket(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
