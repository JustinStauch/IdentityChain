package identitychain.blockchain.merkle;

import identitychain.blockchain.transaction.Transaction;
import identitychain.blockchain.utilities.BlockChainInt;

import java.util.LinkedList;
import java.util.List;

public class MerkleStub implements MerkleTree {
    private final BlockChainInt hash;

    public MerkleStub(BlockChainInt hash) {
        this.hash = hash;
    }

    public MerkleStub(MerkleTree tree) {
        this.hash = tree.getHash();
    }

    @Override
    public BlockChainInt getHash() {
        return hash;
    }

    @Override
    public List<Transaction> getTransactions() {
        return new LinkedList<>();
    }
}
