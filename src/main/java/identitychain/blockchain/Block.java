package identitychain.blockchain;

import identitychain.blockchain.merkle.MerkleTree;
import identitychain.blockchain.transaction.Coinbase;
import identitychain.blockchain.transaction.CurrencyTransaction;
import identitychain.blockchain.transaction.Transaction;
import identitychain.blockchain.utilities.BCConstants;
import identitychain.blockchain.utilities.BlockChainInt;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Block implements Serializable {
    private final BlockChainInt hash;
    private final BlockChainInt previousBlockHash;
    private final BlockChainInt target;
    private final long nonce;
    private final int time;
    private final MerkleTree merkleRoot;

    public Block(BlockChainInt hash,
                 BlockChainInt previousBlockHash,
                 BlockChainInt target,
                 long nonce,
                 int time,
                 MerkleTree merkleRoot) {

        this.hash = hash;
        this.previousBlockHash = previousBlockHash;
        this.target = target;
        this.nonce = nonce;
        this.time = time;
        this.merkleRoot = merkleRoot;
    }

    /**
     * Check if the block is properly solved.
     *
     * This does not check if the underlying transactions are valid.
     *
     * A block if valid iff the hash of the header equals the stated hash, the hash is less than the target.
     *
     * @return True if this is a proper block.
     */
    public boolean isValid() {

        // Check that the hash is larger than the target.
        if (hash.compareTo(target) >= 0) {
            return false;
        }

        return hash.equals(computeHash());
    }

    /**
     * Checks that there is a coinbase transaction, and it includes all transaction fees.
     *
     * @return True if the coinbase transaction is proper.
     */
    public boolean verifyCoinbase() {
        final List<Transaction> transactions = getTransactions();

        if(!(transactions.get(0) instanceof Coinbase)) {
            return false;
        }

        final Coinbase coinbase = (Coinbase) transactions.get(0);

        return transactions.stream()
                .skip(1)
                .filter(transaction -> transaction instanceof CurrencyTransaction)
                .map(transaction -> ((CurrencyTransaction) transaction).getTransactionFee())
                .reduce(BCConstants.MINING_REWARD, (x, y) -> x + y)
                == coinbase.getTotalOut();
    }

    public Map<PublicKey, Long> getEffects() {
        return getTransactions().stream()
                .filter(transaction -> transaction instanceof CurrencyTransaction)
                .flatMap(transaction -> ((CurrencyTransaction) transaction).getEffects().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x + y));
    }

    public Map<PublicKey, Long> getNegativeEffects() {
        return getEffects().entrySet().stream()
                .filter(entry -> entry.getValue() < 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x + y));
    }

    public BlockChainInt getHash() {
        return hash;
    }

    public BlockChainInt getPreviousBlockHash() {
        return previousBlockHash;
    }

    public BlockChainInt getTarget() {
        return target;
    }

    public int getTimeStamp() {
        return time;
    }

    public List<Transaction> getTransactions() {
        return merkleRoot.getTransactions();
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public boolean equals(Object o){
        if (!(o instanceof Block)) {
            return false;
        }

        final Block block = (Block) o;

        return block.isValid() && block.hash.equals(hash);
    }

    private BlockChainInt computeHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            hash.update(previousBlockHash.toByteArray());
            hash.update(target.toByteArray());
            hash.update(ByteBuffer.allocate(Long.BYTES).putLong(nonce));
            hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(time));
            hash.update(merkleRoot.getHash().toByteArray());

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }
}
