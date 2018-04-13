package identitychain.client;

import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.transaction.*;
import identitychain.blockchain.utilities.BlockChainInt;
import identitychain.blockchain.utilities.Utilities;
import identitychain.mining.BlockChainMiner;
import identitychain.network.BlockChainRouter;
import identitychain.network.BlockChainServer;
import identitychain.network.NetworkNode;

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
        this.nameService = new NameService(this.blockChain);
        this.manager.addObserver(this);
        this.manager.addObserver(this.nameService);
        this.miner.addObserver(this.router);
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
            client.createWallet(getWalletFileName(args[0]));
        }

        client.startProcesses();
        try {
            client.runCommandLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getWalletFileName(String propertyFile) {
        final Properties properties = new Properties(generateDefaultProperties());

        try {
            properties.load(new FileInputStream(propertyFile));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return properties.getProperty("WALLET_FILE");
    }

    private static Client loadFromFile(String filename) throws IOException {
        final Properties defaults = generateDefaultProperties();

        File file = new File(filename);
        if (!file.exists()) {
            file.createNewFile();

            defaults.store(new FileOutputStream(file), "");
        }

        final Properties properties = new Properties(defaults);
        properties.load(new FileInputStream(filename));

        final Client client = loadFromProperties(properties);

        return client;
    }

    private static Client loadFromProperties(Properties properties) {
        File dataDir = new File(properties.getProperty("DATA_DIR"));
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File blockDir = new File(properties.getProperty("BLOCKCHAIN_DIR"));
        if (!blockDir.exists()) {
            blockDir.mkdirs();
        }

        final BlockChainManager manager = BlockChainManager.getBlockChainManager(
                new File(properties.getProperty("BLOCKCHAIN_DIR"))
        );

        final BlockChainMiner miner = new BlockChainMiner(
                manager,
                Integer.parseInt(properties.getProperty("NUM_MINING_THREADS")),
                properties.getProperty("DATA_DIR") + "/transactions.dat");

        final BlockChainRouter router = BlockChainRouter.loadFromFile(
                new File(properties.getProperty("DATA_DIR") + "/neighbours.txt"),
                manager,
                new NetworkNode(
                        properties.getProperty("PUBLIC_ADDRESS"),
                        Integer.parseInt(properties.getProperty("PORT"))
                ),
                miner
        );


        final BlockChainServer server = new BlockChainServer(
                new NetworkNode(
                        properties.getProperty("PUBLIC_ADDRESS"), Integer.parseInt(properties.getProperty("PORT"))
                ),
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

    private void createWallet(String walletFile) {
        final Scanner scan = new Scanner(System.in);
        System.out.print("Your Name: ");
        final String name = scan.nextLine();

        System.out.print("Description: ");
        final String description = scan.nextLine();

        System.out.print("Desired key size [2048 bits]: ");
        final String answer = scan.nextLine();

        int keySize = 2048;

        if (!answer.equals("")) {
            keySize = Integer.parseInt(answer);
        }

        System.out.print("Document File: ");

        String docFileName = scan.nextLine();

        BlockChainInt docHash = BlockChainInt.ZERO;

        if (!docFileName.equals("")) {
            try {
                docHash = hashFile(docFileName);
            } catch (IOException e) {
                System.out.println("Error reading file " + docFileName + ": " + e.getMessage());
                docHash = BlockChainInt.ZERO;
            }
        }

        wallet = Wallet.generateWallet(name, description, docHash, keySize);

        try (final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(walletFile))) {
            out.writeObject(wallet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private BlockChainInt hashFile(String fileName) throws IOException {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[4096];

            try (final BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileName))) {
                while (true) {
                    final int amountRead = in.read(buffer);

                    if (amountRead <= 0) {
                        break;
                    }

                    hash.update(buffer, 0, amountRead);
                }
            }

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }

    private void startProcesses() {
        miner.setOutputShare(Collections.singletonMap(wallet.getPublicKey(), 1.0));

        server.start();
        miner.startMining();

        Thread addIdentity = new Thread(() -> {
            IdentityEntry entry = blockChain.getIdentity(wallet.getPublicKey());
            if (entry == null) {
                router.broadcastTransaction(wallet.toIdentityEntry());
            }
        });

        addIdentity.start();

        router.startBroadcasts(300000);
    }

    private boolean sendTransaction(List<CurrencyTransactionOutput> outputs, long transactionFee) {
        if (transactionFee < 0) {
            System.out.println("Transaction fee must not be negative.");
            return false;
        }

        final long amountOut = outputs.stream()
                .map(CurrencyTransactionOutput::getAmount)
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
            hash.update(ByteBuffer.allocate(Long.BYTES).putLong(id).array());

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

        System.out.print("> ");
        String line;
        while ((line = in.readLine()) != null) {
            processCommand(line.split(" "), in);
            System.out.print("> ");
        }
    }

    private void processCommand(String[] args, BufferedReader in) {
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

            final PublicKey key = args.length >= 5 ? getKey(args[1], Integer.parseInt(args[2])) : getKey(args[1]);

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
        else if (args[0].equals("head")) {
            System.out.println(blockChain.getHeadHash().toString());
        }
        else if (args[0].equals("trace")) {
            for (BlockChainInt hash : blockChain.traceBlockchain()) {
                System.out.print("-> " + hash.toString() + " ");
            }
            System.out.println();
        }
        else if (args[0].equals("connect")) {
            if (args.length != 3) {
                System.out.println("Improper format, usage: connect [address] [port]");
                return;
            }

            try {
                router.connectToNeighbour(new NetworkNode(args[1], Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                System.out.println(args[2] + " is not a number, usage: connect [address] [port]");
            }
        }
        else if (args[0].equals("neighbours") || args[0].equals("neighbors")) {
            for (NetworkNode node : router.getNeighbours()) {
                System.out.println(node);
            }
        }
        else if (args[0].equals("print")) {
            System.out.println(blockChain);
        }
        else if (args[0].equals("message") || args[0].equals("msg")) {
            if (args.length == 1) {
                System.out.println("Improper format, usage: " + args[0] + " [recipient] <index>");
            }
            final PublicKey key = args.length == 3 ? getKey(args[1], Integer.parseInt(args[2])) : getKey(args[1]);

            if (key == null) {
                return;
            }

            System.out.println("Start typing message. Make a line \":x\" to indicate the end of the message.");

            String messageBody = "";

            try {
                while (true) {
                    final String line = in.readLine();

                    if (line == null) {
                        break;
                    }

                    if (line.equals(":x")) {
                        break;
                    }

                    messageBody += line + "\n";
                }

                final Message message = Message.generateMessage(wallet.getPublicKey(), key, messageBody);
                message.sign(wallet.getPrivateKey());

                if (miner.acceptTransaction(message)) {
                    router.broadcastTransaction(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
        else if (args[0].equals("get-message") || args[0].equals("get-msg")) {
            if (args.length == 1) {
                System.out.println("You have " + blockChain.getMessages(wallet.getPublicKey()).size() + " messages.");
                return;
            }
            if (args.length == 2) {
                final PublicKey key = getKey(args[1]);

                if (key == null) {
                    return;
                }

                List<Message> messages = blockChain.getMessages(key, wallet.getPublicKey());

                if (messages.size() == 1) {
                    displayMessage(messages.get(0));
                }
                else {
                    System.out.println("You have " + messages.size() + " messages from " + args[1] + ".");
                }
            }
            if (args.length == 3) {
                final PublicKey key = getKey(args[1]);

                if (key == null) {
                    return;
                }

                int index;

                try {
                    index = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    System.out.println(args[2] + " is not a number, usage: " + args[0] + " [sender] [msg-index]");
                    return;
                }

                final List<Message> messages = blockChain.getMessages(key, wallet.getPublicKey());

                if (messages.size() <= index) {
                    System.out.println("You only have " + messages.size() + " messages from " + args[1] + ".");
                }
                else {
                    displayMessage(messages.get(index));
                }
            }
            if (args.length == 4) {
                int nameIndex;
                int msgIndex;

                try {
                    nameIndex = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    System.out.println(args[2] + " is not a number, usage: "
                            + args[0] + " [sender] [sender-index] [msg-index]");
                    return;
                }

                try {
                    msgIndex = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    System.out.println(args[3] + " is not a number, usage: "
                            + args[0] + " [sender] [sender-index] [msg-index]");
                    return;
                }

                final PublicKey key = getKey(args[1], nameIndex);

                if (key == null) {
                    return;
                }

                final List<Message> messages = blockChain.getMessages(key, wallet.getPublicKey());

                if (messages.size() <= msgIndex) {
                    System.out.println("You only have " + messages.size() + " messages from " + args[1] + ".");
                }
                else {
                    displayMessage(messages.get(msgIndex));
                }
            }
        }
        else if (args[0].equals("exit")) {
            final CountDownLatch latch = new CountDownLatch(3);

            Thread stop = new Thread(() -> {
                miner.stopMining();
                miner.saveTransactions();
                latch.countDown();
            });

            Thread saveRouter = new Thread(() -> {
                router.stopBroadcasts();
                router.saveToFile();
                latch.countDown();
            });

            Thread saveBlockChain = new Thread(() -> {
                blockChain.saveToFile();
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
        return getKey(name, true);
    }

    private PublicKey getKey(String name, boolean verbose) {
        final List<PublicKey> keys = nameService.getKeys(name);

        if (keys.size() == 0) {
            if (verbose) {
                System.out.println("Could not find name " + name + ".");
            }

            return null;
        }

        if (keys.size() > 1) {
            if (verbose) {
                System.out.println("Found multiple entries for name " + name + ".");

                for (int i = 0; i < keys.size(); i++) {
                    System.out.println(name + "[" + i + "]\t" + blockChain.getIdentity(keys.get(i)).getDescription());
                }
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

    private void displayMessage(Message message) {
        System.out.println("From: " + blockChain.getIdentity(message.getSender()).getShortString()
                + "\nDate: " + message.getDate()
                + "\nTo: " + blockChain.getIdentity(message.getReceiver()).getShortString()
                + "\n\n" + message.getMessage(wallet.getPrivateKey())
                + "\n---------------------------------------------------------------");
    }
}
