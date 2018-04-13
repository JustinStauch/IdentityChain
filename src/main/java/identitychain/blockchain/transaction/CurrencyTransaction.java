package identitychain.blockchain.transaction;

import identitychain.blockchain.utilities.BlockChainInt;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.*;

public class CurrencyTransaction extends Transaction{
    private final List<CurrencyTransactionInput> inputs = new ArrayList<>();
    private final List<CurrencyTransactionOutput> outputs = new ArrayList<>();

    public CurrencyTransaction(long id, List<CurrencyTransactionInput> inputs, List<CurrencyTransactionOutput> outputs) {
        super(id);
        this.inputs.addAll(inputs);
        this.outputs.addAll(outputs);
    }

    @Override
    public BlockChainInt getHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            hash.update(getInputHash().toByteArray());
            hash.update(getOutputHash().toByteArray());

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }

    /**
     * Check that the signatures all match to ensure that it was not tampered with.
     *
     * This also checks that all amounts are positive.
     *
     * @return If the signatures are all valid.
     */
    @Override
    public boolean isValid() {
        final BlockChainInt outputHash = getOutputHash();

        return inputs.stream().map(input -> input.verifySignature(outputHash)).reduce(true, (a, b) -> a && b)
                && outputs.stream().map(output -> output.getAmount() >= 0).reduce(true, (a, b) -> a && b)
                && getTransactionFee() >= 0;
    }

    /**
     * Check how this transaction affects the balances of each wallet.
     *
     * @return A map that matches a wallet's public key to its change in value.
     */
    public Map<PublicKey, Long> getEffects() {
        Map<PublicKey, Long> effects = new HashMap<>();
        for (CurrencyTransactionInput input : inputs) {
            effects.merge(input.getSourcePublicKey(), -input.getAmount(), (x, y) -> x + y);
        }

        for (CurrencyTransactionOutput output : outputs) {
            effects.merge(output.getDestPublicKey(), output.getAmount(), (x, y) -> x + y);
        }

        return effects;
    }

    @Override
    public long getTransactionFee() {
        return getTotalIn() - getTotalOut();
    }

    public long getTotalIn() {
        return inputs.stream().map(CurrencyTransactionInput::getAmount).reduce(0L, (x, y) -> x + y);
    }

    public long getTotalOut() {
        return outputs.stream().map(CurrencyTransactionOutput::getAmount).reduce(0L, (x, y) -> x + y);
    }

    protected BlockChainInt getInputHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            for (int i = 0; i < inputs.size(); i++) {
                hash.update(inputs.get(i).getHash().toByteArray());
            }

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }

    protected BlockChainInt getOutputHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            // Add the id to the hash to prevent duplicate transactions.
            hash.update(ByteBuffer.allocate(Long.BYTES).putLong(getID()));

            for (int i = 0; i < outputs.size(); i++) {
                hash.update(outputs.get(i).getHash().toByteArray());
            }

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CurrencyTransaction)) {
            return false;
        }

        final CurrencyTransaction transaction = (CurrencyTransaction) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(transaction))
                .append(inputs, transaction.inputs)
                .append(outputs, transaction.outputs)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(197, 199)
                .appendSuper(super.hashCode())
                .append(inputs)
                .append(outputs)
                .toHashCode();
    }
}
