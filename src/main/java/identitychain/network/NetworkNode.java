package identitychain.network;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class NetworkNode {

    private final String ip;
    private final int port;

    public NetworkNode(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public Socket createSocket() throws IOException {
        return new Socket(ip, port);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NetworkNode)) {
            return false;
        }

        final NetworkNode node = (NetworkNode) o;

        return new EqualsBuilder()
                .append(ip, node.ip)
                .append(port, node.port)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 73)
                .append(ip)
                .append(port)
                .toHashCode();
    }
}
