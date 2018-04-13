package identitychain.blockchain;

import identitychain.blockchain.utilities.BlockChainInt;

import java.io.*;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class BlockCache {
    private transient final Block[] cache = new Block[5000000];

    private final File directory;
    private final Map<BlockChainInt, Long> blockSequenceNumbers = new HashMap<>();
    private final long uid;
    private boolean primary = true;

    public BlockCache(File directory, long uid) {
        this.directory = directory;
        this.uid = uid;
    }

    public BlockCache(BlockCache cache, long uid) {
        this(cache.directory, uid);
        blockSequenceNumbers.putAll(cache.blockSequenceNumbers);
        primary = false;
    }


    /**
     * Gets the block with the given hash.
     *
     * This block will be cached following a call to this method.
     *
     * @param hash The hash of the block to get.
     * @return The Block with the given hash.
     */
    public Block getBlock(BlockChainInt hash) {
        return getBlock(hash, true);
    }

    /**
     * Gets the block with the given hash.
     *
     * @param hash The hash of the block to load.
     * @param cacheBlock If the block should replace the current cached value in the event of a collision.
     * @return The Block with the given hash.
     */
    public Block getBlock(BlockChainInt hash, boolean cacheBlock) {

        final int cacheIndex = hash.intValue() % cache.length;
        if (cache[cacheIndex] != null) {
            if (!cache[cacheIndex].getHash().equals(hash)) {
                if (cacheBlock) {
                    if (!cacheBlockFromFile(hash)) {
                        return null;
                    }
                } else {
                    return loadBlockFromFile(hash);
                }
            }
        }

        return cache[cacheIndex];
    }

    public Block getBlock(long seqNum) {
        return getBlock(seqNum, true);
    }

    public Block getBlock(long seqNum, boolean cacheBlock) {
        final Block block = loadBlockFromFile(seqNum);

        if (cacheBlock && block != null) {
            cache[block.getHash().intValue() % cache.length] = block;
        }

        return block;
    }

    public void storeBlock(Block block, long seqNum) {
        if (!block.isValid()) {
            return;
        }

        final int cacheIndex = block.getHash().intValue() % cache.length;

        if (cache[cacheIndex] != null) {
            if (cache[cacheIndex].equals(block)) {
                return;
            }
        }

        blockSequenceNumbers.put(block.getHash(), seqNum);

        updateBlock(block);
    }

    public void updateBlock(Block block) {
        if (!block.isValid()) {
            return;
        }

        saveBlockToFile(block);

        cache[block.getHash().intValue() % cache.length] = block;
    }

    /**
     * Remove this block from the sequence number map, and the cache. Does not delete the file.
     *
     * @param hash The hash of the block to untrack.
     */
    public void untrackBlock(BlockChainInt hash) {
        blockSequenceNumbers.remove(hash);

        final int cacheIndex = hash.intValue() % cache.length;
        if (cache[cacheIndex] != null) {
            if (cache[cacheIndex].getHash().equals(hash)) {
                cache[cacheIndex] = null;
            }
        }
    }

    /**
     * Delete the Block with the given hash.
     *
     * @param hash The hash of the block to delete.
     */
    public void deleteBlock(BlockChainInt hash) {
        getStoredFile(hash).delete();
        untrackBlock(hash);
    }

    /**
     * Move the blocks so that they are stored as the primary Block for their sequence numbers.
     */
    public void makePrimary() {
        primary = true;

        for (long seqNum : blockSequenceNumbers.values()) {
            final File secondaryFile = getSecondaryFile(seqNum);

            if (secondaryFile.exists()) {
                secondaryFile.renameTo(getPrimaryFile(seqNum));
            }
        }
    }

    private boolean cacheBlockFromFile(BlockChainInt hash) {
        final Block found = loadBlockFromFile(hash);

        if (found == null) {
            return false;
        }

        cache[hash.intValue() % cache.length] = found;

        return true;
    }

    private Block loadBlockFromFile(BlockChainInt hash) {
        return loadBlockFromFile(blockSequenceNumbers.get(hash));
    }

    private Block loadBlockFromFile(long seqNum) {
        try (final ObjectInputStream in = new ObjectInputStream(new FileInputStream(getStoredFile(seqNum)))) {
            return (Block) in.readObject();
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void saveBlockToFile(Block block) {
        final File file = getWriteFile(block.getHash());

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (final ObjectOutputStream out
                     = new ObjectOutputStream(new FileOutputStream(file))) {

            out.writeObject(block);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the File where the block with the given hash should be saved.
     *
     * @param hash The hash of the Block to get the file for.
     * @return The File to save the Block with the given hash.
     */
    private File getWriteFile(BlockChainInt hash) {
        return getWriteFile(blockSequenceNumbers.get(hash));
    }

    /**
     * Gets the File where the Block with the given sequence number should be saved.
     *
     * If this is for a primary blockchain, this returns the result of getPrimaryFile().
     * If this is not for a primary blockchain, this returns the result of getSecondaryFile().
     *
     * @param seqNum The index of this block in the order of the blockchain.
     * @return The File to save the Block with the given sequence number.
     */
    private File getWriteFile(long seqNum) {
        return primary ? getPrimaryFile(seqNum) : getSecondaryFile(seqNum);
    }

    private File getStoredFile(BlockChainInt hash) {
        return getStoredFile(blockSequenceNumbers.get(hash));
    }

    /**
     * Gets the file that this block is stored in.
     *
     * The BlockChain this cache belongs to may have some blocks in common with the primary blockchain,
     * or it is the primary blockchain.
     *
     * It gets the secondary File and returns it if it exists, otherwise it returns the primary file.
     *
     * @param seqNum The index of this block in the order of the blockchain.
     * @return The File where the desired Block is stored.
     */
    private File getStoredFile(long seqNum) {
        final File file = getSecondaryFile(seqNum);

        if (file.exists()) {
            return file;
        }

        return getPrimaryFile(seqNum);
    }

    /**
     * Return the file that this block is stored in if this was a primary BlockCache.
     *
     * This file does not contain the BlockChain's uid in the file name.
     *
     * @param seqNum The index of this block in the order of the blockchain.
     * @return The File storing the primary block with this sequence number.
     */
    private File getPrimaryFile(long seqNum) {
        return Paths.get(directory.getAbsolutePath()).resolve("IDCBlock_" + seqNum + ".dat").toFile();
    }

    /**
     * Get the File for the block with the BlockChain uid in the file name.
     *
     * @param seqNum The index of this block in the order of the blockchain.
     * @return The File to store the block for this secondary blockchain.
     */
    private File getSecondaryFile(long seqNum) {
        return Paths.get(directory.getAbsolutePath()).resolve("IDCBlock_" + seqNum + "_" + uid + ".dat").toFile();
    }
}
