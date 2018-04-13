package identitychain.blockchain.merkle;

import identitychain.blockchain.transaction.Transaction;
import identitychain.blockchain.utilities.BlockChainInt;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class MerkleNode implements MerkleTree {
    private final MerkleTree left;
    private final MerkleTree right;
    private final BlockChainInt hash;

    public MerkleNode(MerkleTree left, MerkleTree right) {
        this.left = left;
        this.right = right;
        hash = computeHash();
    }


    @Override
    public BlockChainInt getHash() {
        return hash;
    }

    @Override
    public List<Transaction> getTransactions() {
        final List<Transaction> transactions = left.getTransactions();
        transactions.addAll(right.getTransactions());

        return transactions;
    }

    private BlockChainInt computeHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            hash.update(left.getHash().toByteArray());
            hash.update(right.getHash().toByteArray());

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }
}
