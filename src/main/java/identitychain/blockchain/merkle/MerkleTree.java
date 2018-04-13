package identitychain.blockchain.merkle;

import identitychain.blockchain.transaction.Transaction;
import identitychain.blockchain.utilities.BlockChainInt;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

public interface MerkleTree extends Serializable {

    BlockChainInt getHash();
    List<Transaction> getTransactions();
}
