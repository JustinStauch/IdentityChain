package identitychain.blockchain.merkle;

import identitychain.blockchain.utilities.BlockChainInt;

public final class MerkleTreeStubber {

    private MerkleTreeStubber() {

    }

    public static MerkleTree stub(MerkleTree tree, BlockChainInt node) {
        if (tree.getHash().equals(node)) {
            return new MerkleStub(node);
        }
        if (tree instanceof MerkleNode) {
            final MerkleNode cur = (MerkleNode) tree;

            final MerkleTree newLeft = stub(cur.getLeft(), node);
            final MerkleTree newRight = stub(cur.getRight(), node);

            if (newLeft instanceof MerkleStub && newRight instanceof MerkleStub) {
                return new MerkleStub(tree.getHash());
            }

            return new MerkleNode(newLeft, newRight);
        }

        return tree;
    }
}
