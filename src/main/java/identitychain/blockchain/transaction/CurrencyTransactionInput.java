package identitychain.blockchain.transaction;

import identitychain.blockchain.utilities.BlockChainInt;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.nio.ByteBuffer;
import java.security.*;

public class CurrencyTransactionInput implements TransactionInput {
    private final PublicKey sourcePublicKey;
    private final long amount;
    private final byte[] signature;

    private CurrencyTransactionInput(PublicKey sourcePublicKey, long amount, byte[] signature) {
        this.sourcePublicKey = sourcePublicKey;
        this.amount = amount;
        this.signature = signature;
    }

    public static CurrencyTransactionInput generateInput(KeyPair keyPair, long amount, BlockChainInt outputHash) {

        try {
            Signature sig = Signature.getInstance("SHA256withRSA");

            sig.initSign(keyPair.getPrivate());
            sig.update(ByteBuffer.allocate(Long.BYTES).putLong(amount));
            sig.update(outputHash.toByteArray());

            return new CurrencyTransactionInput(keyPair.getPublic(), amount, sig.sign());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        return new CurrencyTransactionInput(keyPair.getPublic(), amount, new byte[0]);
    }

    /**
     * Verify that this input is matched to the given output, and that the input is a non-negative amount.
     *
     * @param outputHash The hash of the associated output.
     * @return If the transaction is signed on this output, and is a non-negative amount.
     */
    public boolean verifySignature(BlockChainInt outputHash) {
        if (amount < 0) {
            return false;
        }

        try {
            Signature sig = Signature.getInstance("SHA256withRSA");

            sig.initVerify(sourcePublicKey);
            sig.update(ByteBuffer.allocate(Long.BYTES).putLong(amount));
            sig.update(outputHash.toByteArray());

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

    public PublicKey getSourcePublicKey() {
        return sourcePublicKey;
    }

    public long getAmount() {
        return amount;
    }

    @Override
    public BlockChainInt getHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            hash.update(sourcePublicKey.getEncoded());
            hash.update(ByteBuffer.allocate(Long.BYTES).putLong(amount));
            hash.update(signature);

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CurrencyTransactionInput)) {
            return false;
        }

        CurrencyTransactionInput input = (CurrencyTransactionInput) o;

        return new EqualsBuilder()
                .append(sourcePublicKey, input.sourcePublicKey)
                .append(amount, input.amount)
                .append(signature, input.signature)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(13, 47)
                .append(sourcePublicKey)
                .append(amount)
                .append(signature)
                .toHashCode();
    }
}
