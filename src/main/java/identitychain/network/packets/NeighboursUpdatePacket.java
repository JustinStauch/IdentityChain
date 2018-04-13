package identitychain.network.packets;

import identitychain.network.NetworkNode;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class NeighboursUpdatePacket implements Serializable {

    private final Set<NetworkNode> neighbours = new HashSet<>();

    public NeighboursUpdatePacket(Set<NetworkNode> neighbours) {
        this.neighbours.addAll(neighbours);
    }

    public Set<NetworkNode> getNeighbours() {
        return neighbours;
    }
}
