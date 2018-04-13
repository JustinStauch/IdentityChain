package identitychain.client;

import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.transaction.*;
import identitychain.blockchain.utilities.BlockChainInt;
import identitychain.mining.BlockChainMiner;
import identitychain.network.BlockChainRouter;
import identitychain.network.BlockChainServer;
import identitychain.network.NetworkNode;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Client implements Observer {
    private final Random random = new Random();

    private final BlockChainManager manager;
    private final BlockChainServer server;
    private final BlockChainRouter router;
    private final BlockChainMiner miner;
    private final NameService nameService;

    private BlockChain blockChain;

    private Wallet wallet;

    public Client(BlockChainManager manager, BlockChainServer server, BlockChainRouter router, BlockChainMiner miner) {
        this.manager = manager;
        this.server = server;
        this.router = router;
        this.miner = miner;
        this.blockChain = manager.getBlockChain();
        nameService = new NameService(blockChain);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Config file name missing. Usage: command [filename]");
            System.exit(1);
        }

        Client client = null;

        try {
            client = loadFromFile(args[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (client.wallet == null) {
            client.createWallet();
        }

        client.startProcesses();
        try {
            client.runCommandLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Client loadFromFile(String filename) throws IOException {
        final Properties properties = new Properties(generateDefaultProperties());
        properties.load(new FileInputStream(filename));

        final Client client = loadFromProperties(properties);

        Thread saveProperties = new Thread(() -> {
            File file = new File(filename);

            try {
                if (!file.exists()) {
                    file.createNewFile();
                }

                properties.store(new FileOutputStream(file), "");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        saveProperties.start();

        return client;
    }

    private static Client loadFromProperties(Properties properties) {
        final BlockChainManager manager = BlockChainManager.getBlockChainManager(
                new File(properties.getProperty("BLOCKCHAIN_DIR") + "/manager.dat")
        );

        final BlockChainRouter router = BlockChainRouter.loadFromFile(
                new File(properties.getProperty("DATA_DIR") + "/router.dat"),
                manager,
                new NetworkNode(
                        properties.getProperty("PUBLIC_ADDRESS"),
                        Integer.parseInt(properties.getProperty("PORT"))
                )
        );

        final BlockChainMiner miner = new BlockChainMiner(manager, Integer.parseInt("NUM_MINING_THREADS"));
        final BlockChainServer server = new BlockChainServer(
                Integer.parseInt(properties.getProperty("PORT")),
                        manager,
                        router,
                        miner
        );

        final Client client = new Client(manager, server, router, miner);

        File walletFile = new File(properties.getProperty("WALLET_FILE"));
        if (walletFile.exists()) {
            try (final ObjectInputStream in = new ObjectInputStream(new FileInputStream(walletFile))) {
                client.wallet = (Wallet) in.readObject();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        else {
            client.wallet = null;
        }

        return client;
    }

    private static Properties generateDefaultProperties() {
        final Properties properties = new Properties();
        properties.setProperty("PUBLIC_ADDRESS", "localhost");
        properties.setProperty("PORT", "4114");
        properties.setProperty("NUM_MINING_THREADS", "10");
        properties.setProperty("BLOCKCHAIN_DIR", "blockchain/");
        properties.setProperty("DATA_DIR", "data/");
        properties.setProperty("WALLET_FILE", "wallet.dat");

        return properties;
    }

    private void createWallet() {
        final Scanner scan = new Scanner(System.in);
        System.out.print("Your Name: ");
        final String name = scan.nextLine();

        // TODO: Read in identity file;

        System.out.print("Desired key size [2048 bits]: ");
        final String answer = scan.nextLine();

        int keySize = 2048;

        if (!answer.equals("")) {
            keySize = Integer.parseInt(answer);
        }

        wallet = Wallet.generateWallet(name, BlockChainInt.ZERO, keySize);
    }

    private void startProcesses() {
        miner.setOutputShare(Collections.singletonMap(wallet.getPublicKey(), 1.0));

        server.start();
        miner.startMining();


        Thread neighbours = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                router.pingNeighbours();
            }
        });
        neighbours.start();

        Thread addIdentity = new Thread(() -> {
            IdentityEntry entry = blockChain.getIdentity(wallet.getPublicKey());
            if (entry == null) {
                router.broadcastTransaction(wallet.toIdentityEntry());
            }
        });

        addIdentity.start();
    }

    private boolean sendTransaction(List<CurrencyTransactionOutput> outputs, long transactionFee) {
        if (transactionFee < 0) {
            System.out.println("Transaction fee must not be negative.");
            return false;
        }

        final long amountOut = outputs.stream()
                .map(output -> output.getAmount())
                .reduce(transactionFee, (a, b) -> a + b);

        if (blockChain.getBalance(wallet.getPublicKey()) < amountOut) {
            System.out.println("Insufficient funds.");
            return false;
        }

        final long id = random.nextLong();

        BlockChainInt outputHash = null;

        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            // Add the id to the hash to prevent duplicate transactions.
            hash.update(ByteBuffer.allocate(Long.BYTES).putLong(id));

            for (int i = 0; i < outputs.size(); i++) {
                hash.update(outputs.get(i).getHash().toByteArray());
            }

            outputHash = BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }



        final CurrencyTransactionInput input = CurrencyTransactionInput.generateInput(
                wallet.getKeys(),
                amountOut,
                outputHash
        );

        CurrencyTransaction transaction = new CurrencyTransaction(id, Collections.singletonList(input), outputs);

        if (!transaction.isValid()) {
            System.out.println("Transaction not valid.");
            return false;
        }

        router.broadcastTransaction(transaction);

        return true;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof BlockChainManager) {
            blockChain = ((BlockChainManager) o).getBlockChain();
        }
    }

    private void runCommandLine() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        String line;
        while ((line = in.readLine()) != null) {
            processCommand(line.split(" "));
        }
    }

    private void processCommand(String[] args) {
        if (args[0].equals("balance")) {
            if (args.length == 1) {
                System.out.println(wallet.getName() + "'s balance: "
                        + blockChain.getBalance(wallet.getPublicKey()) + " IDC");
            }
            else {
                final PublicKey key = args.length >= 3 ? getKey(args[1], Integer.parseInt(args[2])) : getKey(args[1]);

                if (key != null) {
                    System.out.println(args[1] + "'s balance: " + blockChain.getBalance(key) + " IDC");
                }
            }
        }
        else if (args[0].equals("send")) {
            if (args.length < 4) {
                System.out.println("Improper format, usage: send [recipient] <index> [amount] [fee]");
                return;
            }

            final PublicKey key = args.length >= 3 ? getKey(args[1], Integer.parseInt(args[2])) : getKey(args[1]);

            if (key != null) {
                long amount = Long.parseLong(args[args.length == 4 ? 2 : 3]);
                long fee = Long.parseLong(args[args.length == 4 ? 3 : 4]);

                if (sendTransaction(Collections.singletonList(new CurrencyTransactionOutput(key, amount)), fee)) {
                    System.out.println("Transaction submitted.");
                }
                else {
                    System.out.println("Transaction failed.");
                }
            }
        }
        else if (args[0].equals("exit")) {
            final CountDownLatch latch = new CountDownLatch(3);

            Thread stop = new Thread(() -> {
                miner.stopMining();
                latch.countDown();
            });

            Thread saveRouter = new Thread(() -> {
                router.saveToFile();
                latch.countDown();
            });

            Thread saveBlockChain = new Thread(() -> {
                blockChain.saveToFileInBackground();
                latch.countDown();
            });

            System.out.println("Exiting...");

            stop.start();
            saveRouter.start();
            saveBlockChain.start();

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("Done.");

            System.exit(0);
        }
        else {
            System.out.println("Unknown command " + args[0] + ".");
        }
    }

    private PublicKey getKey(String name) {
        final List<PublicKey> keys = nameService.getKeys(name);

        if (keys.size() == 0) {
            System.out.println("Could not find name " + name + ".");
            return null;
        }

        if (keys.size() > 1) {
            System.out.println("Found multiple entries for name " + name + ".");

            for (int i = 0; i < keys.size(); i++) {
                System.out.println(name + "[" + i + "]\t" + computeFingerPrint(keys.get(i)));
            }

            return null;
        }

        return keys.get(0);
    }

    private PublicKey getKey(String name, int index) {
        final List<PublicKey> keys = nameService.getKeys(name);

        if (keys.size() == 0) {
            System.out.println("Could not find name " + name + ".");
            return null;
        }

        if (keys.size() <= index) {
            System.out.println("Name " + name + " only has " + keys.size() + " entries.");
            return null;
        }

        return keys.get(index);
    }

    private String computeFingerPrint(PublicKey key) {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-1");
            hash.update(key.getEncoded());
            return DatatypeConverter.printHexBinary(hash.digest()).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return "";
    }
}
