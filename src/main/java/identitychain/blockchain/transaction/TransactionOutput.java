package identitychain.blockchain.transaction;

import identitychain.blockchain.utilities.BlockChainInt;

import java.io.Serializable;

public interface TransactionOutput extends Serializable {

    BlockChainInt getHash();
}
