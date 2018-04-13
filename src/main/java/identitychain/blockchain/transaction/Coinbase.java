package identitychain.blockchain.transaction;

import identitychain.blockchain.utilities.BlockChainInt;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

public class Coinbase extends CurrencyTransaction {
    private final int extraNonce;

    public Coinbase(long id, List<CurrencyTransactionOutput> outputs, int extraNonce) {
        super(id, Collections.emptyList(), outputs);

        this.extraNonce = extraNonce;
    }

    @Override
    protected BlockChainInt getInputHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");

            hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(extraNonce));

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }
}
