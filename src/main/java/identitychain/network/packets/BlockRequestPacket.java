package identitychain.network.packets;

import identitychain.blockchain.Block;
import identitychain.blockchain.BlockChain;
import identitychain.blockchain.utilities.BlockChainInt;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Queue;

public class BlockRequestPacket implements Serializable {

    // This will be the previousBlockHash field of the oldest block sent.
    private final BlockChainInt blockBeforeStart;
    private final long numBlocks;

    public BlockRequestPacket(BlockChainInt blockBeforeStart, long numBlocks) {
        this.blockBeforeStart = blockBeforeStart;
        this.numBlocks = numBlocks;
    }

    public Queue<Block> handle(BlockChain blockChain) {
        Queue<Block> blocks = new LinkedList<>();

        for (Block block : blockChain) {
            if (block.getHash().equals(blockBeforeStart)) {
                break;
            }

            blocks.add(block);
        }

        while (blocks.size() > numBlocks) {
            blocks.poll();
        }

        return blocks;
    }
}
