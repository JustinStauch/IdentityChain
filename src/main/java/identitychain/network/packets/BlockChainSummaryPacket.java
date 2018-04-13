package identitychain.network.packets;

import identitychain.blockchain.utilities.BlockChainInt;
import identitychain.network.NetworkNode;

import java.io.Serializable;

public class BlockChainSummaryPacket implements Serializable {

    private final long size;
    private final double difficulty;
    private final BlockChainInt headHash;
    private final NetworkNode source;

    public BlockChainSummaryPacket(long size, double difficulty, BlockChainInt headHash, NetworkNode source) {
        this.size = size;
        this.difficulty = difficulty;
        this.headHash = headHash;
        this.source = source;
    }

    public long getSize() {
        return size;
    }

    public double getDifficulty() {
        return difficulty;
    }

    public BlockChainInt getHeadHash() {
        return headHash;
    }

    public NetworkNode getSource() {
        return source;
    }
}
