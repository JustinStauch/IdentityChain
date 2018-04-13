package identitychain.client;

import identitychain.blockchain.transaction.IdentityEntry;
import identitychain.blockchain.utilities.BlockChainInt;

import java.io.Serializable;
import java.security.*;

public class Wallet implements Serializable {
    private final KeyPair keys;
    private final String name;
    private final String description;
    private final BlockChainInt identityDocumentHash;

    private Wallet(KeyPair keys, String name, String description, BlockChainInt identityDocumentHash) {
        this.keys = keys;
        this.name = name;
        this.description = description;
        this.identityDocumentHash = identityDocumentHash;
    }

    public static Wallet generateWallet(
            String name,
            String description,
            BlockChainInt identityDocumentHash,
            int keySize) {

        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize);
            return new Wallet(generator.generateKeyPair(), name, description, identityDocumentHash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public PublicKey getPublicKey() {
        return keys.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keys.getPrivate();
    }

    public KeyPair getKeys() {
        return keys;
    }

    public IdentityEntry toIdentityEntry() {
        final IdentityEntry entry = new IdentityEntry(keys.getPublic(), name, description, identityDocumentHash);
        entry.sign(keys.getPrivate());

        return entry;
    }
}
