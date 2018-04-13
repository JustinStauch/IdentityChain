package identitychain.network.packets;

import identitychain.blockchain.Block;
import identitychain.blockchain.BlockChain;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class BlockChainSummaryPacket implements Serializable {

    private final long size;
    private final double difficulty;

    public BlockChainSummaryPacket(long size, double difficulty) {
        this.size = size;
        this.difficulty = difficulty;
    }

    public long getSize() {
        return size;
    }

    public double getDifficulty() {
        return difficulty;
    }
}
