package identitychain.mining;

import identitychain.blockchain.Block;
import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.merkle.MerkleTreeBuilder;
import identitychain.blockchain.transaction.Coinbase;
import identitychain.blockchain.transaction.CurrencyTransaction;
import identitychain.blockchain.transaction.CurrencyTransactionOutput;
import identitychain.blockchain.transaction.Transaction;
import identitychain.blockchain.utilities.BCConstants;
import identitychain.blockchain.utilities.BlockChainInt;
import identitychain.network.BlockChainRouter;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BlockChainMiner extends Observable implements Observer {

    private BlockChain blockChain;
    private final BlockChainManager manager;
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<PublicKey, Double> outputShare = new HashMap<>();
    private final int concurrency;
    private final MiningCoordinator coordinator = new MiningCoordinator();

    private final Lock miningLock = new ReentrantLock();
    private final Set<MiningBlock> activeMiners = new HashSet<>();

    public BlockChainMiner(BlockChainManager manager, int concurrency) {
        this.manager = manager;
        this.blockChain = manager.getBlockChain();
        this.concurrency = concurrency;

        this.manager.addObserver(this);
    }

    public boolean acceptTransaction(Transaction transaction) {
        if (!transaction.isValid()) {
            return false;
        }

        transactions.add(transaction);

        return true;
    }

    public void setOutputShare(Map<PublicKey, Double> outputShare) {
        double total = 0.0;
        for (double share : outputShare.values()) {
            total += share;
        }

        // Need a final variable.
        final double computedTotal = total;

        if (computedTotal != 1.0) {
            for (PublicKey key : outputShare.keySet()) {
                outputShare.compute(key, (k, v) -> v / computedTotal);
            }
        }

        this.outputShare.clear();
        this.outputShare.putAll(outputShare);
    }


    public void startMining() {
        coordinator.setStopped(false);

        beginMining();
    }

    private void beginMining() {
        if (coordinator.isStopped()) {
            return;
        }

        removeDuplicateTransactions();
        Thread miningThread = new Thread(() -> {
            try {
                miningLock.lock();

                final List<Transaction> transactionList = getNextTransactionList();
                final List<CurrencyTransactionOutput> coinbaseOut = generateCoinbaseOutput(transactionList);
                final BlockChainInt previousBlockHash = blockChain.getHeadHash();

                coordinator.reset();
                final CountDownLatch countDownLatch = new CountDownLatch(concurrency);

                for (int i = 0; i < concurrency; i++) {
                    Thread mine = new Thread(() -> {
                        while (!coordinator.isFinished() && !coordinator.hasOverflowed()) {
                            final List<Transaction> blockTransactionList = new ArrayList<>();
                            blockTransactionList.add(
                                    new Coinbase(blockChain.getSize(), coinbaseOut, coordinator.getExtraNonce())
                            );
                            blockTransactionList.addAll(transactionList);

                            MiningBlock block = new MiningBlock(
                                    previousBlockHash,
                                    manager.getCurrentTarget(),
                                    MerkleTreeBuilder.buildMerkleTree(blockTransactionList)
                            );

                            activeMiners.add(block);

                            block.addObserver(this);
                            block.startMining();

                            activeMiners.remove(block);
                        }

                        countDownLatch.countDown();
                    });

                    mine.start();
                }

                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {

                }
            } finally {
                miningLock.unlock();
            }
        });

        miningThread.start();
    }

    /**
     * Tell all mining blocks to stop.
     */
    public void stopMining() {
        coordinator.setStopped(true);

        for (MiningBlock block : activeMiners) {
            block.stop();
        }
    }

    /**
     * Stop all active mining, and start the next block.
     *
     * Note: If the miner is stopped from an outside source, this will just return without any mining starting.
     */
    private void restartMining() {
        coordinator.setFinished();

        for (MiningBlock block : activeMiners) {
            block.stop();
        }

        startMining();
    }

    private List<Transaction> getNextTransactionList() {
        List<Transaction> blockTransactionList = new LinkedList<>();

        final Map<PublicKey, Long> balances = new HashMap<>();

        for (Transaction transaction : transactions) {
            if (transaction instanceof CurrencyTransaction) {
                final Map<PublicKey, Long> effects = ((CurrencyTransaction) transaction).getEffects();
                for (PublicKey wallet : effects.keySet()) {
                    balances.putIfAbsent(wallet, blockChain.getBalance(wallet));
                }

                boolean flag = false;

                for (Map.Entry<PublicKey, Long> entry : effects.entrySet()) {
                    if (balances.get(entry.getKey()) + entry.getValue() < 0) {
                        flag = true;
                        break;
                    }
                }

                if (flag) {
                    continue;
                }

                for (Map.Entry<PublicKey, Long> entry : effects.entrySet()) {
                    balances.compute(entry.getKey(), (k, v) -> v + entry.getValue());
                }
            }

            blockTransactionList.add(transaction);

            if (blockTransactionList.size() == BCConstants.MAX_TRANSACTIONS_PER_BLOCK - 1) {
                break;
            }
        }

        return blockTransactionList;
    }

    private final List<CurrencyTransactionOutput> generateCoinbaseOutput(List<Transaction> transactionList) {
        long amountOut = BCConstants.MINING_REWARD;

        for (Transaction transaction : transactionList) {
            amountOut += transaction.getTransactionFee();
        }

        final Map<PublicKey, Long> amountPayed = new HashMap<>();
        long totalPayed = 0;

        for (Map.Entry<PublicKey, Double> entry : outputShare.entrySet()) {
            final long amount = Math.round(amountOut * entry.getValue());
            totalPayed += amount;
            amountPayed.put(entry.getKey(), amount);
        }

        List<PublicKey> keys = new ArrayList<>();
        keys.addAll(amountPayed.keySet());
        Collections.shuffle(keys);

        while (totalPayed < amountOut) {
            for (PublicKey key : keys) {
                amountPayed.compute(key, (k, v) -> v + 1);
                totalPayed++;
                if (totalPayed >= amountOut) {
                    break;
                }
            }
        }

        while (totalPayed > amountOut) {
            for (PublicKey key : keys) {
                amountPayed.compute(key, (k, v) -> v - 1);
                totalPayed++;
                if (totalPayed <= amountOut) {
                    break;
                }
            }
        }

        final List<CurrencyTransactionOutput> outputs = new LinkedList<>();

        for (Map.Entry<PublicKey, Long> entry : amountPayed.entrySet()) {
            outputs.add(new CurrencyTransactionOutput(entry.getKey(), entry.getValue()));
        }

        return outputs;
    }

    private void removeDuplicateTransactions() {
        final Set<Transaction> transactionSet = new HashSet<>();
        transactionSet.addAll(transactions);
        for (Block block : blockChain) {
            transactionSet.removeAll(block.getTransactions());
        }
    }

    @Override
    public synchronized void update(Observable observable, Object o) {
        if (observable instanceof BlockChainManager) {
            this.blockChain = ((BlockChainManager) observable).getBlockChain();
            restartMining();
        }
        else if (observable instanceof MiningBlock) {
            Block block = ((MiningBlock) observable).toBlock();
            if (blockChain.pushBlock(block)) {
                notifyObservers();
                restartMining();
            }
        }
    }
}
