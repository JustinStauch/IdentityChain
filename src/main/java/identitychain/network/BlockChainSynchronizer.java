package identitychain.network;

import identitychain.blockchain.Block;
import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.utilities.BlockChainInt;
import identitychain.mining.BlockChainMiner;
import identitychain.network.packets.BlockRequestPacket;
import identitychain.network.packets.InfoRequestPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BlockChainSynchronizer {
    private static final int BUFFER_SIZE = 10;

    private static Set<BlockChainInt> syncAttempts = new HashSet<>();
    private static Lock syncAttemptsLock = new ReentrantLock();

    private final BlockChain blockChain;
    private final BlockChainManager manager;
    private final NetworkNode node;

    private BlockChainSynchronizer(BlockChain blockChain, BlockChainManager manager, NetworkNode node) {
        this.blockChain = blockChain;
        this.manager = manager;
        this.node = node;
    }

    public static void dispatchSynchronization(
            BlockChain blockChain,
            BlockChainManager manager,
            BlockChainInt headHash,
            NetworkNode node) {

        syncAttemptsLock.lock();
        try {
            if (syncAttempts.contains(headHash)) {
                return;
            }

            final Thread sync = new Thread(() -> {
                new BlockChainSynchronizer(blockChain, manager, node).synchronize();
                syncAttemptsLock.lock();
                try {
                    syncAttempts.remove(headHash);
                } finally {
                    syncAttemptsLock.unlock();
                }
            });

            sync.start();

            syncAttempts.add(headHash);

        } finally {
            syncAttemptsLock.unlock();
        }
    }

    public void synchronize() {

        final List<BlockChainInt> trace = getTrace();

        final BlockChainInt firstCommonBlock = blockChain.getFirstCommonBlock(trace);

        final BlockChain newChain = blockChain.forkBlockChain(firstCommonBlock);

        while (true) {

            final Stack<Block> blocks = getBlocks(newChain.getHeadHash());

            if (blocks.isEmpty()) {
                break;
            }

            boolean flag = false;

            while (!blocks.isEmpty()) {
                if (!newChain.pushBlock(blocks.pop())) {
                    flag = true;
                    break;
                }
            }

            if (flag) {
                break;
            }
        }

        if (newChain != null) {
            if (!manager.replaceBlockChain(newChain)) {
                newChain.delete(firstCommonBlock);
                blockChain.saveToFileInBackground();
            }
        } else {
            blockChain.saveToFileInBackground();
        }
    }

    private List<BlockChainInt> getTrace() {
        try (final Socket socket = node.createSocket()) {
            final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(new InfoRequestPacket(InfoRequestPacket.InfoType.BLOCK_CHAIN_TRACE));

            final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            return (List<BlockChainInt>) in.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return Collections.emptyList();
    }

    private Stack<Block> getBlocks(BlockChainInt hash) {
        try (final Socket socket = node.createSocket()) {
            final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(new BlockRequestPacket(hash, BUFFER_SIZE));

            final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            return reverseQueue((Queue<Block>) in.readObject());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return new Stack<>();
    }

    private Stack<Block> reverseQueue(Queue<Block> blocks) {
        final Stack<Block> blockStack = new Stack<>();

        while (!blocks.isEmpty()) {
            blockStack.push(blocks.poll());
        }

        return blockStack;
    }
}
