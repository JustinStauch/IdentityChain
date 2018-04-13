package identitychain.blockchain.transaction;

import identitychain.blockchain.merkle.MerkleTree;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public abstract class Transaction implements MerkleTree {

    private static Random random = new Random();

    private final long id;

    public Transaction() {
        this(random.nextLong());
    }

    public Transaction(long id) {
        this.id = id;
    }

    long getID() {
        return id;
    }

    public abstract boolean isValid();

    public long getTransactionFee() {
        return 0;
    }

    @Override
    public List<Transaction> getTransactions() {
        final List<Transaction> transactions = new LinkedList<>();
        transactions.add(this);

        return transactions;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Transaction)) {
            return false;
        }

        return id == ((Transaction) o).id;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(101, 53)
                .append(id)
                .toHashCode();
    }
}
