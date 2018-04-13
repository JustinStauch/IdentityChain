package identitychain.network;

import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.transaction.Transaction;
import identitychain.mining.BlockChainMiner;
import identitychain.network.packets.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BlockChainServer implements Observer {

    private BlockChain blockChain;
    private final BlockChainManager manager;
    private final BlockChainRouter router;
    private final ReadWriteLock blockChainLock = new ReentrantReadWriteLock();
    private final int port;

    private final BlockChainMiner miner;

    public BlockChainServer(int port, BlockChainManager manager, BlockChainRouter router, BlockChainMiner miner) {
        this.port = port;
        this.manager = manager;
        this.router = router;
        this.miner = miner;
        blockChain = manager.getBlockChain();
        manager.addObserver(this);
    }

    public void start() {
        try {
            final ServerSocket serverSocket = new ServerSocket(port);

            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();

                        Thread handleThread = new Thread(() -> handleSocket(socket));
                        handleThread.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            thread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleSocket(Socket socket) {

        try {
            final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            final Object obj = in.readObject();

            if (obj instanceof BlockRequestPacket) {
                blockChainLock.readLock().lock();
                try {
                    out.writeObject(((BlockRequestPacket) obj).handle(blockChain));
                } finally {
                    blockChainLock.readLock().unlock();
                }
            }
            else if (obj instanceof BlockChainSummaryPacket) {
                blockChainLock.readLock().lock();
                try {
                    final BlockChainSummaryPacket packet = (BlockChainSummaryPacket) obj;
                    final double difficulty = blockChain.getTotalDifficulty();
                    if (difficulty > packet.getDifficulty()
                            || (difficulty == packet.getDifficulty() && blockChain.getSize() > packet.getSize())) {

                        out.writeObject(new BlockChainSummaryPacket(blockChain.getSize(), difficulty));
                    }
                    else if (packet.getDifficulty() == difficulty && packet.getSize() == blockChain.getSize()) {
                        socket.close();
                    }
                    else {
                        new BlockChainSynchronizer(blockChain, manager).synchronize(socket);
                    }
                } finally {
                    blockChainLock.readLock().unlock();
                }
            }
            else if (obj instanceof PingPacket) {
                out.writeObject(obj);
            }
            else if (obj instanceof Transaction) {
                final Transaction transaction = (Transaction) obj;
                router.broadcastTransaction(transaction);
                miner.acceptTransaction(transaction);
            }
            else if (obj instanceof IntroductionPacket) {
                IntroductionPacket intro = (IntroductionPacket) obj;
                router.addNeighbour(new NetworkNode(intro.getIp(), intro.getPort()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(Observable observable, Object o) {
        if (observable instanceof BlockChainManager) {
            blockChainLock.writeLock().lock();
            try {
                blockChain = ((BlockChainManager) observable).getBlockChain();
            } finally {
                blockChainLock.writeLock().unlock();
            }
        }
    }
}
