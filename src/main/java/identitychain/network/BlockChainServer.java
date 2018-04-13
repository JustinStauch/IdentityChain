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
import java.net.SocketException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BlockChainServer implements Observer {

    private final NetworkNode ownAddress;
    private BlockChain blockChain;
    private final BlockChainManager manager;
    private final BlockChainRouter router;
    private final ReadWriteLock blockChainLock = new ReentrantReadWriteLock();

    private final BlockChainMiner miner;

    public BlockChainServer(NetworkNode ownAddress, BlockChainManager manager, BlockChainRouter router, BlockChainMiner miner) {
        this.ownAddress = ownAddress;
        this.manager = manager;
        this.router = router;
        this.miner = miner;
        blockChain = manager.getBlockChain();
        manager.addObserver(this);
    }

    public void start() {
        try {
            final ServerSocket serverSocket = new ServerSocket(ownAddress.getPort());

            Thread thread = new Thread(() -> {
                try {
                    while (true) {
                        try {
                            Socket socket = serverSocket.accept();

                            Thread handleThread = new Thread(() -> handleSocket(socket));
                            handleThread.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } finally {
                    try {
                        serverSocket.close();
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

                    // If the head is the same, the rest is likely the same.
                    if (blockChain.getHeadHash().equals(packet.getHeadHash())) {
                        return;
                    }

                    final double difficulty = blockChain.getTotalDifficulty();

                    if (difficulty > packet.getDifficulty()
                            || (difficulty == packet.getDifficulty() && blockChain.getSize() > packet.getSize())) {

                        router.sendBlockChainInfo(packet.getSource());
                        router.broadcastBlockChainInfo();
                    }
                    else if (packet.getDifficulty() != difficulty || packet.getSize() != blockChain.getSize()) {

                        BlockChainSynchronizer.dispatchSynchronization(
                                blockChain,
                                manager,
                                packet.getHeadHash(),
                                packet.getSource()
                        );

                    }

                } finally {
                    blockChainLock.readLock().unlock();
                }
            }
            else if (obj instanceof InfoRequestPacket) {
                blockChainLock.readLock().lock();
                try {
                    switch (((InfoRequestPacket) obj).getInfoType()) {
                        case BLOCK_CHAIN_SUMMARY:
                            out.writeObject(
                                    new BlockChainSummaryPacket(
                                            blockChain.getSize(),
                                            blockChain.getTotalDifficulty(),
                                            blockChain.getHeadHash(),
                                            ownAddress
                                    )
                            );
                            break;
                        case BLOCK_CHAIN_TRACE:
                            out.writeObject(blockChain.traceBlockchain());
                            break;
                        case NEIGHBOURS:
                            out.writeObject(router.getNeighbours());
                            break;
                    }
                } finally {
                    blockChainLock.readLock().unlock();
                }
            }
            else if (obj instanceof NeighboursUpdatePacket) {
                final NeighboursUpdatePacket neighboursUpdatePacket = (NeighboursUpdatePacket) obj;
                router.updateNeighbours(neighboursUpdatePacket.getNeighbours(), neighboursUpdatePacket.getSource());
            }
            else if (obj instanceof PingPacket) {
                out.writeObject(obj);
            }
            else if (obj instanceof Transaction) {
                final Transaction transaction = (Transaction) obj;
                if (miner.acceptTransaction(transaction)) {
                    router.propogateTransaction(transaction);
                }
            }
            else if (obj instanceof IntroductionPacket) {
                IntroductionPacket intro = (IntroductionPacket) obj;
                router.addNeighbour(new NetworkNode(intro.getIp(), intro.getPort()));
            }
        } catch(SocketException e) {
            // Ignore socket exceptions.
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
