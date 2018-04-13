package identitychain.client;

import identitychain.blockchain.transaction.IdentityEntry;
import identitychain.blockchain.utilities.BlockChainInt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public class Wallet {
    private final KeyPair keys;
    private final String name;
    private final BlockChainInt identityDocumentHash;

    private Wallet(KeyPair keys, String name, BlockChainInt identityDocumentHash) {
        this.keys = keys;
        this.name = name;
        this.identityDocumentHash = identityDocumentHash;
    }

    public static Wallet generateWallet(String name, BlockChainInt identityDocumentHash, int keySize) {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize);
            return new Wallet(generator.generateKeyPair(), name, identityDocumentHash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String getName() {
        return name;
    }

    public PublicKey getPublicKey() {
        return keys.getPublic();
    }

    public KeyPair getKeys() {
        return keys;
    }

    public IdentityEntry toIdentityEntry() {
        final IdentityEntry entry = new IdentityEntry(keys.getPublic(), name, identityDocumentHash);
        entry.sign(keys.getPrivate());

        return entry;
    }
}
