package identitychain.blockchain;

import identitychain.blockchain.transaction.IdentityEntry;
import identitychain.blockchain.transaction.Transaction;
import identitychain.blockchain.utilities.BlockChainInt;
import identitychain.mining.MiningBlock;

import java.io.*;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class BlockChain extends Observable implements Serializable, Iterable<Block> {
    private final BlockCache cache;

    private final File directory;

    private BlockChainInt head = BlockChainInt.ZERO;
    private long size = 0;
    private final long uid;

    private static long curID = 0;
    private static final Lock curIDLock = new ReentrantLock();

    private BlockChain(File directory) {
        this(directory, 0);
    }

    private BlockChain(File directory, long uid) {
        this(directory, uid, new BlockCache(directory, uid));
    }

    private BlockChain(File directory, long uid, BlockCache cache) {
        this.directory = directory;
        this.uid = uid;
        this.cache = cache;

        setCurID(uid + 1);
    }

    private static void setCurID(long uid) {
        curIDLock.lock();
        try {
            curID = curID > uid ? curID : uid;
        } finally {
            curIDLock.unlock();
        }
    }

    private static long getCurID() {
        curIDLock.lock();
        try {
            curID++;
            return curID;
        } finally {
            curIDLock.unlock();
        }
    }

    public static BlockChain getFromDirectory(File directory) {
        final File file = new File(directory.getAbsolutePath() + "/IDCBlockChain.dat");

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            return (BlockChain) in.readObject();
        } catch (FileNotFoundException e) {

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return new BlockChain(directory);
    }

    public long getSize() {
        return size;
    }

    public BlockChainInt getHeadHash() {
        return head;
    }

    public Block getHead() {
        return getBlock(head);
    }

    public Block getBlock(BlockChainInt hash) {
        return cache.getBlock(hash);
    }

    public Block getBlock(long seqNum) {
        return cache.getBlock(seqNum);
    }

    public boolean isValid() {
        final Map<PublicKey, Long> balances = new HashMap<>();

        if (!cache.getBlock(0).getPreviousBlockHash().equals(BlockChainInt.ZERO)) {
            return false;
        }

        int lastTime = Integer.MAX_VALUE;

        for (Block block : this) {
            if (!block.isValid()) {
                return false;
            }

            if (block.getTimeStamp() > lastTime) {
                return false;
            }

            lastTime = block.getTimeStamp();

            for (Map.Entry<PublicKey, Long> entry : block.getEffects().entrySet()) {
                balances.merge(entry.getKey(), entry.getValue(), (x, y) -> x + y);
            }
        }

        for (long value : balances.values()) {
            if (value < 0) {
                return false;
            }
        }

        return true;
    }

    public boolean pushBlock(Block block) {
        if (!block.isValid()) {
            return false;
        }

        if (head != null) {
            if (!block.getPreviousBlockHash().equals(head)) {
                return false;
            }
        }

        if (!block.verifyCoinbase()) {
            return false;
        }

        if (!verifyEffects(block)) {
            return false;
        }

        cache.storeBlock(block, size);
        head = block.getHash();
        size++;

        Thread notify = new Thread(() -> {
            notifyObservers();
        });
        notify.start();

        saveToFileInBackground();

        return true;
    }

    public boolean propose(MiningBlock block) {
        return verifyEffects(block.toBlock());
    }

    public void delete(BlockChainInt stopHash) {
        for (Block block : this) {
            if (block.getHash().equals(stopHash)) {
                break;
            }

            cache.deleteBlock(block.getHash());
        }

        getFile().delete();
    }

    /**
     * Create a new Blockchain starting from the specified block.
     *
     * @param forkHash The block to make the head of the new Blockchain.
     * @return A BlockChain with the block that hashes to forkHash as the head.
     */
    public BlockChain forkBlockChain(BlockChainInt forkHash) {
        final BlockCache newCache = new BlockCache(cache, uid);

        for (Block block : this) {
            if (block.getHash().equals(forkHash)) {
                break;
            }

            newCache.untrackBlock(block.getHash());
        }

        final BlockChain newChain = new BlockChain(directory, getCurID(), newCache);
        newChain.saveToFileInBackground();

        return newChain;
    }

    public void makePrimary() {
        cache.makePrimary();
    }

    public LinkedList<BlockChainInt> traceBlockchain() {
        final LinkedList<BlockChainInt> trace = new LinkedList<>();

        for (Block block = cache.getBlock(head);
             block != null;
             block = cache.getBlock(block.getPreviousBlockHash(), false)) {

            trace.add(block.getHash());
        }

        return trace;
    }

    public BlockChainInt getFirstCommonBlock(List<BlockChainInt> trace) {
        for (BlockChainInt blockHash : trace) {
            if (cache.getBlock(blockHash) != null) {
                return blockHash;
            }
        }

        return BlockChainInt.ZERO;
    }

    public double getTotalDifficulty() {
        double difficulty = 0.0;

        for (Block block = cache.getBlock(head);
             block != null;
             block = cache.getBlock(block.getPreviousBlockHash(), false)) {

            difficulty += BlockChainInt.MAX_TARGET.doubleValue() / block.getTarget().doubleValue();
        }

        return difficulty;
    }

    public long getBalance(PublicKey wallet) {
        long balance = 0;

        for (Block block : this) {
            balance += block.getEffects().get(wallet);
        }

        return balance;
    }

    public Set<IdentityEntry> getAllIdentitiesWithName(String name) {
        final Set<IdentityEntry> identities = new HashSet<>();

        for (Block block : this) {
            for (Transaction transaction : block.getTransactions()) {
                if (transaction instanceof IdentityEntry) {
                    final String transactionName = ((IdentityEntry) transaction).getName();
                    if (transactionName.equals(name)) {
                        identities.add((IdentityEntry) transaction);
                    }
                }
            }
        }

        return identities;
    }

    public IdentityEntry getIdentity(PublicKey key) {
        for (Block block : this) {
            for (Transaction transaction : block.getTransactions()) {
                if (transaction instanceof IdentityEntry) {
                    final PublicKey transactionKey = ((IdentityEntry) transaction).getPublicKey();
                    if (transactionKey.equals(key)) {
                        return (IdentityEntry) transaction;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public Iterator<Block> iterator() {
        return new Iterator<Block>() {
            Block current = cache.getBlock(head, false);

            @Override
            public boolean hasNext() {
                return cache.getBlock(current.getPreviousBlockHash(), false) != null;
            }

            @Override
            public Block next() {
                current = cache.getBlock(current.getPreviousBlockHash(), false);

                return current;
            }
        };
    }

    private boolean verifyEffects(Block block) {
        final Map<PublicKey, Long> effects = block.getNegativeEffects();

        final Set<BlockChainInt> transactionHashes = block.getTransactions().stream()
                .map(Transaction::getHash)
                .collect(Collectors.toSet());

        if (effects.isEmpty()) {
            return true;
        }

        for (Block cur : this) {

            // Check for a duplicate transaction.
            if (cur.getTransactions().stream()
                    .map(transaction -> transactionHashes.contains(transaction.getHash()))
                    .reduce(false, (a, b) -> a || b)) {

                return false;
            }

            final Map<PublicKey, Long> blockEffects = cur.getEffects();

            for (PublicKey key : effects.keySet()) {
                effects.compute(key, (k, v) -> v + blockEffects.get(key));

                if (effects.get(key) >= 0) {
                    effects.remove(key);
                }
            }

            if (effects.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public void saveToFile() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(getFile()))) {
            out.writeObject(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveToFileInBackground() {
        Thread save = new Thread(() -> saveToFile());

        save.start();
    }

    private void saveToFileUID() {
        final File file = new File(directory.getAbsolutePath() + "/IDCBlockChain_" + uid + ".dat");

        Thread save = new Thread(() -> {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
                out.writeObject(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        save.start();
    }

    private File getFile() {
        if (getFromDirectory(directory).uid >= uid) {
            return new File(directory.getAbsolutePath() + "/IDCBlockChain.dat");
        }

        return new File(directory.getAbsolutePath() + "/IDCBlockChain_" + uid + ".dat");
    }
}
