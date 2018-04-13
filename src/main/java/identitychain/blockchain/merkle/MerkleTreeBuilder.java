package identitychain.blockchain.merkle;

import identitychain.blockchain.transaction.Transaction;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public final class MerkleTreeBuilder {

    public static MerkleTree buildMerkleTree(List<Transaction> transactions) {
        Queue<MerkleTree> lastLayer = new LinkedList<>();
        lastLayer.addAll(transactions);

        while (lastLayer.size() > 1) {
            final Queue<MerkleTree> curLayer = new LinkedList<>();
            while (lastLayer.size() > 1) {
                curLayer.add(new MerkleNode(lastLayer.peek(), lastLayer.poll()));
            }

            if (lastLayer.size() > 0) {
                curLayer.add(lastLayer.poll());
            }

            lastLayer = curLayer;
        }

        return lastLayer.peek();
    }
}
