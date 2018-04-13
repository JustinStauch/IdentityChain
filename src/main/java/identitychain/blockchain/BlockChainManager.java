package identitychain.blockchain;

import identitychain.blockchain.utilities.BlockChainInt;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class BlockChainManager extends Observable implements Observer {

    private static final int TARGET_BLOCK_MINING_TIME = 30;
    private static final int ADJUSTMENT_PERIOND = 120;// Blocks, i.e. reassess every hour.

    private static final Map<String, BlockChainManager> MANAGERS = new HashMap<>();

    private BlockChain blockChain;
    private final Lock blockChainLock = new ReentrantLock();
    private double difficulty = 1.0;

    private BlockChainManager(BlockChain blockChain) {
        this.blockChain = blockChain;
        blockChain.addObserver(this);
    }

    public static BlockChainManager getBlockChainManager(File file) {
        if (!MANAGERS.containsKey(file.getAbsolutePath())) {
            MANAGERS.put(file.getAbsolutePath(), new BlockChainManager(BlockChain.getFromDirectory(file)));
        }

        return MANAGERS.get(file.getAbsolutePath());
    }

    public BlockChain getBlockChain() {
        blockChainLock.lock();
        try {
            return blockChain;
        } finally {
            blockChainLock.unlock();
        }
    }

    public BlockChainInt getCurrentTarget() {
        return BlockChainInt.fromDouble(BlockChainInt.MAX_TARGET.doubleValue() / difficulty);
    }

    public boolean replaceBlockChain(BlockChain newChain) {
        blockChainLock.lock();
        try {
            if (!newChain.isValid()) {
                return false;
            }
            if (!blockChain.isValid()) {
                makeReplacement(newChain);
                return true;
            }

            final double difficulty = blockChain.getTotalDifficulty();
            final double newDifficulty = newChain.getTotalDifficulty();

            if (difficulty > newDifficulty
                    || (difficulty == newDifficulty && blockChain.getSize() > newChain.getSize())) {
                return false;
            }

            makeReplacement(newChain);

            return true;
        } finally {
            blockChainLock.unlock();
        }
    }

    private void makeReplacement(BlockChain newChain) {
        blockChain.delete(blockChain.getFirstCommonBlock(newChain.traceBlockchain()));

        blockChain = newChain;
        blockChain.makePrimary();
        blockChain.saveToFileInBackground();
        final Thread notify = new Thread(this::notifyObservers);
        notify.start();
    }

    private void assessDifficulty() {
        int headTime = blockChain.getHead().getTimeStamp();
        difficulty = BlockChainInt.MAX_TARGET.doubleValue() / blockChain.getHead().getTarget().doubleValue();
        if (blockChain.getSize() >= 120) {
            int startTime = blockChain.getBlock(blockChain.getSize() - 121).getTimeStamp();

            difficulty *= (TARGET_BLOCK_MINING_TIME * ADJUSTMENT_PERIOND) / (headTime - startTime);
        }
        else {
            int startTime = blockChain.getBlock(0).getTimeStamp();

            difficulty *= (TARGET_BLOCK_MINING_TIME * blockChain.getSize()) / (headTime - startTime);
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof BlockChain) {
            BlockChain chain = (BlockChain) o;
            if (chain.equals(blockChain)) {
                if (blockChain.getSize() % 120 == 0) {
                    assessDifficulty();
                }
            }
        }
    }
}
