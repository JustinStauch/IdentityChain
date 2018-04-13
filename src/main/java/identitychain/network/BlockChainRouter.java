package identitychain.network;

import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.transaction.Transaction;
import identitychain.mining.BlockChainMiner;
import identitychain.network.packets.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.spec.ECField;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Thread.sleep;

public class BlockChainRouter implements Observer {
    private final Set<NetworkNode> neighbours = new HashSet<>();
    private final File dataFile;
    private BlockChain blockChain;
    private final ReadWriteLock blockChainLock = new ReentrantReadWriteLock();
    private final BlockChainManager manager;
    private final NetworkNode ownAddress;
    private final NetworkNode loopback;
    private final BlockChainMiner miner;

    private final ReadWriteLock neighboursLock = new ReentrantReadWriteLock();

    private Thread broadcast;
    private Lock broadcastLock = new ReentrantLock();

    private BlockChainRouter(File dataFile, BlockChainManager manager, NetworkNode ownAddress, BlockChainMiner miner) {
        this.dataFile = dataFile;
        this.manager = manager;
        this.ownAddress = ownAddress;
        this.loopback = new NetworkNode("localhost", ownAddress.getPort());
        blockChain = manager.getBlockChain();
        this.miner = miner;
        manager.addObserver(this);

        // Have everything get broadcast back.
        neighbours.add(loopback);
    }

    public static BlockChainRouter loadFromFile(
            File dataFile,
            BlockChainManager manager,
            NetworkNode ownAddress,
            BlockChainMiner miner) {


        final BlockChainRouter router = new BlockChainRouter(dataFile, manager, ownAddress, miner);

        try (final BufferedReader in = new BufferedReader(new FileReader(dataFile))) {
            String line;

            while ((line = in.readLine()) != null) {
                final String[] parts = line.split(":");

                router.neighbours.add(new NetworkNode(parts[0], Integer.parseInt(parts[1])));
            }
        } catch (FileNotFoundException e) {

        } catch (IOException e) {
            e.printStackTrace();
        }

        return router;
    }

    public void startBroadcasts(int interval) {
        broadcastLock.lock();
        try {
            if (broadcast != null) {
                broadcast.interrupt();
            }

            broadcast = new Thread(() -> {
                while (true) {
                    broadcastLock.lock();
                    try {
                        broadcastInfo();
                    } finally {
                        broadcastLock.unlock();
                    }

                    try {
                        sleep(interval);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });

            broadcast.start();
        } finally {
            broadcastLock.unlock();
        }
    }

    public void stopBroadcasts() {
        broadcastLock.lock();
        try {
            broadcast.interrupt();
            broadcast = null;
        } finally {
            broadcastLock.unlock();
        }
    }

    public void broadcastInfo() {
        final Thread updateBlockChain = new Thread(this::broadcastBlockChainInfo);
        final Thread updateNeighbours = new Thread(this::broadcastNeighbours);
        final Thread updateTransactions = new Thread(this::broadcastTransactions);
        final Thread neighboursRequest = new Thread(this::broadcastRequestForNeighbours);

        updateBlockChain.start();
        updateNeighbours.start();
        updateTransactions.start();
        neighboursRequest.start();
    }

    public void addNeighbour(NetworkNode node) {
        neighboursLock.writeLock().lock();
        try {
            if (!neighbours.add(node)) {
                return;
            }
        } finally {
            neighboursLock.writeLock().unlock();
        }

        saveToFileInBackground();

        final Thread updateBlockChain = new Thread(() -> sendBlockChainInfo(node));
        final Thread updateNeighbours = new Thread(() -> broadcastNeighbours());
        final Thread sendTransactions = new Thread(() -> sendTransactions(node));
        final Thread neighboursRequest = new Thread(() -> broadcastRequestForNeighbours());

        updateBlockChain.start();
        updateNeighbours.start();
        sendTransactions.start();
        neighboursRequest.start();
    }

    public void connectToNeighbour(NetworkNode node) {
        neighboursLock.readLock().lock();
        try {
            if (neighbours.contains(node)) {
                return;
            }
        } finally {
            neighboursLock.readLock().unlock();
        }

        final Thread connect = new Thread(() -> {
            try (final Socket socket = node.createSocket()) {
                final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(new IntroductionPacket(ownAddress.getIp(), ownAddress.getPort()));
                addNeighbour(node);
            } catch (IOException e) {
                // Do not add the neighbour if this fails.
            }
        });

        connect.start();
    }

    private void broadcastNeighbours() {
        final Set<NetworkNode> broadcastNeighbours = new HashSet<>();

        neighboursLock.readLock().lock();
        try {
            broadcastNeighbours.addAll(neighbours);
            broadcastNeighbours.remove(loopback);
            broadcastNeighbours.add(ownAddress);
        } finally {
            neighboursLock.readLock().unlock();
        }

        for (NetworkNode node : broadcastNeighbours) {
            if (node.equals(ownAddress)) {
                continue;
            }

            final Thread broadcast = new Thread(() -> {
                try {
                    final Socket socket = node.createSocket();
                    final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(new NeighboursUpdatePacket(broadcastNeighbours, ownAddress));
                } catch(SocketException e) {
                    // Do not care about connection issues.
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            broadcast.start();
        }
    }

    public void updateNeighbours(Set<NetworkNode> neighbours, NetworkNode source) {
        boolean flag = false;

        neighboursLock.readLock().lock();
        try {
            for (NetworkNode node : this.neighbours) {
                if (!neighbours.contains(node) && !node.equals(loopback) && !node.equals(source)) {
                    flag = true;
                }
            }

            for (NetworkNode node : neighbours) {
                if (!this.neighbours.contains(node)) {
                    connectToNeighbour(node);
                    flag = true;
                }
            }
        } finally {
            neighboursLock.readLock().unlock();
        }

        if (flag) {
            broadcastNeighbours();
        }
    }

    public void broadcastRequestForNeighbours() {
        final Set<NetworkNode> broadcastNeighbours = new HashSet<>();

        neighboursLock.readLock().lock();
        try {
            broadcastNeighbours.addAll(neighbours);
            broadcastNeighbours.remove(loopback);
        } finally {
            neighboursLock.readLock().unlock();
        }

        for (NetworkNode node : broadcastNeighbours) {
            Thread request = new Thread(() -> {
                try (final Socket socket = node.createSocket()) {
                    final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.writeObject(new InfoRequestPacket(InfoRequestPacket.InfoType.NEIGHBOURS));

                    final ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                    updateNeighbours((Set<NetworkNode>) in.readObject(), node);
                } catch (SocketException e) {
                    // Ignore SocketExceptions.
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            });

            request.start();
        }
    }

    public void broadcastBlockChainInfo() {
        final BlockChainSummaryPacket packet = generateBlockChainSummaryPacket();

        neighboursLock.readLock().lock();
        try {
            for (NetworkNode node : neighbours) {
                if (node.equals(loopback)) {
                    continue;
                }

                final Thread update = new Thread(() -> sendBlockChainInfo(node, packet));
                update.start();
            }
        } finally {
            neighboursLock.readLock().unlock();
        }
    }

    public void sendBlockChainInfo(NetworkNode node) {
        sendBlockChainInfo(node, generateBlockChainSummaryPacket());
    }

    private BlockChainSummaryPacket generateBlockChainSummaryPacket() {
        blockChainLock.readLock().lock();
        try {
            return new BlockChainSummaryPacket(
                    blockChain.getSize(),
                    blockChain.getTotalDifficulty(),
                    blockChain.getHeadHash(),
                    ownAddress
            );
        } finally {
            blockChainLock.readLock().unlock();
        }

    }

    private void sendBlockChainInfo(NetworkNode node, BlockChainSummaryPacket summaryPacket) {
        if (node.equals(summaryPacket.getSource())) {
            return;
        }

        sendData(node, summaryPacket);
    }

    public void propogateTransaction(Transaction transaction) {
        neighboursLock.readLock().lock();
        try {
            for (NetworkNode node : neighbours) {
                if (node.equals(loopback)) {
                    continue;
                }

                sendTransaction(node, transaction);
            }
        } finally {
            neighboursLock.readLock().unlock();
        }
    }

    private void broadcastTransactions() {
        neighboursLock.readLock().lock();
        try {
            for (NetworkNode node : neighbours) {
                if (!node.equals(loopback)) {
                    sendTransactions(node);
                }
            }
        } finally {
            neighboursLock.readLock().unlock();
        }
    }

    public void broadcastTransaction(Transaction transaction) {
        if (miner.acceptTransaction(transaction)) {
            propogateTransaction(transaction);
        }
    }

    private void sendTransactions(NetworkNode node) {
        for (Transaction transaction : miner.getTransactions()) {
            sendTransaction(node, transaction);
        }
    }

    private void sendTransaction(NetworkNode node, Transaction transaction) {
        final Thread send = new Thread(() -> sendData(node, transaction));

        send.start();
    }

    public Set<NetworkNode> getNeighbours() {
        final Set<NetworkNode> nodes = new HashSet<>();

        // Don't need to lock here. Don't need to worry about race condition.
        nodes.addAll(neighbours);
        nodes.remove(ownAddress);
        return nodes;
    }

    public synchronized void saveToFile() {
        neighboursLock.readLock().lock();
        try (final BufferedWriter out = new BufferedWriter(new FileWriter(dataFile))) {
            for (NetworkNode neighbour : neighbours) {
                out.write(neighbour.toString());
                out.newLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            neighboursLock.readLock().unlock();
        }
    }

    private void saveToFileInBackground() {
        Thread save = new Thread(this::saveToFile);

        save.start();
    }

    private void sendData(NetworkNode node, Object obj) {
        try (final Socket socket = node.createSocket()) {
            final ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(obj);
        } catch(SocketException e) {
            // Socket issues do not matter.
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(Observable observable, Object o) {
        if (observable instanceof BlockChainManager) {

            blockChainLock.writeLock().lock();
            try {
                blockChain = manager.getBlockChain();
            } finally {
                blockChainLock.writeLock().unlock();
            }

            for (Transaction transaction : manager.getRecoveredTransactions()) {
                broadcastTransaction(transaction);
            }

            manager.clearRecoveredTransactions();

            broadcastBlockChainInfo();
        }
        else if (observable instanceof BlockChainMiner) {
            broadcastBlockChainInfo();
        }
    }
}
