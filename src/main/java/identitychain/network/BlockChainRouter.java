package identitychain.network;

import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.transaction.Transaction;
import identitychain.mining.BlockChainMiner;
import identitychain.network.packets.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BlockChainRouter implements Observer {
    private final Set<NetworkNode> neighbours = new HashSet<>();
    private final File dataFile;
    private BlockChain blockChain;
    private final ReadWriteLock blockChainLock = new ReentrantReadWriteLock();
    private final BlockChainManager manager;
    private final NetworkNode ownAddress;

    private BlockChainRouter(File dataFile, BlockChainManager manager, NetworkNode ownAddress) {
        this.dataFile = dataFile;
        this.manager = manager;
        this.ownAddress = ownAddress;
        blockChain = manager.getBlockChain();
        manager.addObserver(this);

        // Have everything get broadcast back.
        neighbours.add(new NetworkNode("localhost", ownAddress.getPort()));
    }

    public static BlockChainRouter loadFromFile(File dataFile, BlockChainManager manager, NetworkNode ownAddress) {
        final BlockChainRouter router = new BlockChainRouter(dataFile, manager, ownAddress);
        try (final ObjectInputStream in = new ObjectInputStream(new FileInputStream(dataFile))) {
            final Set<NetworkNode> nodes = (Set<NetworkNode>) in.readObject();

            router.neighbours.addAll(nodes);
        } catch (FileNotFoundException e) {

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return router;
    }

    public void addNeighbour(NetworkNode node) {
        neighbours.add(node);
        saveToFileInBackground();

        final Thread update = new Thread(() -> {
            sendBlockChainInfo(node, generateBlockChainSummaryPacket());
        });

        update.start();
    }

    public void connectToNeighbour(NetworkNode node) {
        final Thread connect = new Thread(() -> {
            try (final Socket socket = node.createSocket()) {
                final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(new IntroductionPacket(ownAddress.getIp(), ownAddress.getPort()));
                neighbours.add(node);
            } catch (IOException e) {
                // Do not add the neighbour if this fails.
            }
        });

        connect.start();
    }


    public void broadcastBlockChainInfo() {
        final BlockChainSummaryPacket packet = generateBlockChainSummaryPacket();

        for (NetworkNode node : neighbours) {
            final Thread update = new Thread(() -> sendBlockChainInfo(node, packet));
            update.start();
        }
    }

    private BlockChainSummaryPacket generateBlockChainSummaryPacket() {
        blockChainLock.readLock().lock();
        try {
            return new BlockChainSummaryPacket(blockChain.getSize(), blockChain.getTotalDifficulty());
        } finally {
            blockChainLock.readLock().unlock();
        }
    }

    private void sendBlockChainInfo(NetworkNode node, BlockChainSummaryPacket packet) {
        try {
            final Socket socket = node.createSocket();

            final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(packet);

            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            final Object o = in.readObject();

            if (o instanceof BlockChainSummaryPacket) {
                BlockChainSummaryPacket summaryPacket = (BlockChainSummaryPacket) o;
                blockChainLock.readLock().lock();
                try {
                    final double difficulty = blockChain.getTotalDifficulty();

                    if (difficulty < summaryPacket.getDifficulty()
                            || (difficulty == summaryPacket.getDifficulty()
                            && blockChain.getSize() < summaryPacket.getSize())) {

                        final BlockChain cur = blockChain;
                        Thread sync = new Thread(
                                () -> new BlockChainSynchronizer(cur, manager).synchronize(socket)
                        );
                        sync.start();
                    }
                } finally {
                    blockChainLock.readLock().unlock();
                }
            }

            final BlockChain sendBlockChain = blockChain;
            out.writeObject(((BlockRequestPacket) o).handle(sendBlockChain));

            while (true) {
                out.writeObject(((BlockRequestPacket) in.readObject()).handle(sendBlockChain));
            }
        } catch(SocketException e) {
            // Socket was closed at the other end, no changes need to be made.
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void broadcastTransaction(Transaction transaction) {
        for (NetworkNode node : neighbours) {
            final Thread broadcast = new Thread(() -> {
                try (final Socket socket = node.createSocket()) {
                    final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(transaction);
                } catch(SocketException e) {
                    // Do not care about connection issues.
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            broadcast.start();
        }
    }

    public void broadcastNeighbours() {
        pingNeighbours();
        for (NetworkNode node : neighbours) {
            final Thread broadcast = new Thread(() -> {
                try (final Socket socket = node.createSocket()) {
                    final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(new NeighboursUpdatePacket(neighbours));
                } catch(SocketException e) {
                    // Do not care about connection issues.
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            broadcast.start();
        }
    }

    public void updateNeighbours(Set<NetworkNode> neighbours) {
        boolean flag = false;

        for (NetworkNode node : this.neighbours) {
            if (!neighbours.contains(node)) {
                flag = true;
            }
        }

        for (NetworkNode node : neighbours) {
            if (!this.neighbours.contains(neighbours)) {
                connectToNeighbour(node);
                flag = true;
            }
        }

        if (flag) {
            broadcastNeighbours();
        }
    }

    /**
     * Try to send a packet to each neighbour, and remove all neighbours that have an error.
     *
     * @return True if some neighbours did not respond.
     */
    public boolean pingNeighbours() {
        final Set<NetworkNode> remove = new HashSet<>();
        for (NetworkNode node : neighbours) {
            try (final Socket socket = node.createSocket()) {
                final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                final PingPacket packet =new PingPacket();
                out.writeObject(new PingPacket());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                Object obj = in.readObject();
                if (!packet.equals(obj)) {
                    remove.add(node);
                }
            } catch (SocketException e) {
                remove.add(node);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        neighbours.removeAll(remove);

        if (!remove.isEmpty()) {
            saveToFileInBackground();
        }

        return !remove.isEmpty();
    }

    public void saveToFile() {
        try (final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dataFile))) {
            out.writeObject(neighbours);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveToFileInBackground() {
        Thread save = new Thread(() -> saveToFile());

        save.start();
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

            broadcastBlockChainInfo();
        }
        else if (observable instanceof BlockChainMiner) {
            broadcastBlockChainInfo();
        }
    }
}
