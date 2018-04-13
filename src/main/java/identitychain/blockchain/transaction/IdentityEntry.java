package identitychain.blockchain.transaction;

import identitychain.blockchain.utilities.BlockChainInt;

import java.security.*;

public class IdentityEntry extends Transaction {
    private final PublicKey publicKey;
    private final String name;
    private final BlockChainInt documentHash;

    private byte[] signature;

    public IdentityEntry(PublicKey publicKey, String name, BlockChainInt documentHash) {
        this.publicKey = publicKey;
        this.name = name;
        this.documentHash = documentHash;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getName() {
        return name;
    }

    public BlockChainInt getDocumentHash() {
        return documentHash;
    }

    public void sign(PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");

            sig.initSign(privateKey);
            sig.update(publicKey.getEncoded());
            sig.update(name.getBytes());
            sig.update(documentHash.toByteArray());

            signature = sig.sign();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }
    }

    private boolean verify() {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");

            sig.initVerify(publicKey);
            sig.update(publicKey.getEncoded());
            sig.update(name.getBytes());
            sig.update(documentHash.toByteArray());

            return sig.verify(signature);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean isValid() {
        return verify();
    }

    @Override
    public BlockChainInt getHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            hash.update(publicKey.getEncoded());
            hash.update(name.getBytes());
            hash.update(documentHash.toByteArray());
            hash.update(signature);

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }

    /**
     * Every public key can only be used once, so this is the only thing checked.
     *
     * @param o
     * @return True if o is an IndentityEntry, and o and this have the same public key.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IdentityEntry)) {
            return false;
        }

        return publicKey.equals(((IdentityEntry) o).getPublicKey());
    }
}
