package identitychain.blockchain.transaction;

import identitychain.blockchain.utilities.BlockChainInt;

import java.io.Serializable;

public interface TransactionInput extends Serializable {

    BlockChainInt getHash();
}
