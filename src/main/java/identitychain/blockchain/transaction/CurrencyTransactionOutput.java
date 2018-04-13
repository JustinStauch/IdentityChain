package identitychain.blockchain.transaction;

import identitychain.blockchain.utilities.BlockChainInt;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public class CurrencyTransactionOutput implements TransactionOutput {
    private final PublicKey destPublicKey;
    private final long amount;

    public CurrencyTransactionOutput(PublicKey destPublicKey, long amount) {
        this.destPublicKey = destPublicKey;
        this.amount = amount;
    }

    public PublicKey getDestPublicKey() {
        return destPublicKey;
    }

    public long getAmount() {
        return amount;
    }

    @Override
    public BlockChainInt getHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            hash.update(destPublicKey.getEncoded());
            hash.update(ByteBuffer.allocate(Long.BYTES).putLong(amount));

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CurrencyTransactionOutput)) {
            return false;
        }

        final CurrencyTransactionOutput output = (CurrencyTransactionOutput) o;

        return new EqualsBuilder()
                .append(destPublicKey, output.destPublicKey)
                .append(amount, output.amount)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 13)
                .append(destPublicKey)
                .append(amount)
                .toHashCode();
    }
}
