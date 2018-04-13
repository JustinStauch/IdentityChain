package identitychain.mining;

import identitychain.blockchain.Block;
import identitychain.blockchain.merkle.MerkleTree;
import identitychain.blockchain.utilities.BlockChainInt;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Observable;

public class MiningBlock extends Observable {
    private BlockChainInt hash;
    private final BlockChainInt previousBlockHash;
    private final BlockChainInt target;
    private long nonce;
    private final int time;
    private final MerkleTree merkleRoot;

    private boolean stopped;

    public MiningBlock(BlockChainInt previousBlockHash, BlockChainInt target, MerkleTree merkleRoot) {

        this.previousBlockHash = previousBlockHash;
        this.target = target;
        this.time = (int) (System.currentTimeMillis() / 1000);
        this.merkleRoot = merkleRoot;
    }

    public void startMining() {

        stopped = false;

        for (nonce = Long.MIN_VALUE; nonce < Long.MAX_VALUE && !stopped; nonce++) {
            hash = computeHash();
            if (hash.compareTo(target) < 0) {
                break;
            }
        }

        if (hash.compareTo(target) >= 0) {
            nonce = Long.MAX_VALUE;
            hash = computeHash();
        }

        if (hash.compareTo(target) < 0) {
            notifyObservers();
        }
    }

    public Block toBlock() {
        return new Block(hash, previousBlockHash, target, nonce, time, merkleRoot);
    }

    public void stop() {
        stopped = true;
    }

    private final BlockChainInt computeHash() {
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
