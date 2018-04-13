package identitychain.network.packets;

import identitychain.network.NetworkNode;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class NeighboursUpdatePacket implements Serializable {

    private final Set<NetworkNode> neighbours = new HashSet<>();
    private final NetworkNode source;

    public NeighboursUpdatePacket(Set<NetworkNode> neighbours, NetworkNode source) {
        this.neighbours.addAll(neighbours);
        this.source = source;
    }

    public Set<NetworkNode> getNeighbours() {
        return neighbours;
    }

    public NetworkNode getSource() {
        return source;
    }
}
