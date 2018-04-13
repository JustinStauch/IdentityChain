package identitychain.client;

import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.transaction.IdentityEntry;

import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

public class NameService implements Observer {
    private BlockChain blockChain;

    private final Map<String, List<PublicKey>> nameMap = new HashMap<>();

    public NameService(BlockChain blockChain) {
        this.blockChain = blockChain;
    }

    /**
     * Get all the public keys associated with a certain name.
     * @param name
     * @return
     */
    public List<PublicKey> getKeys(String name) {
        if (!nameMap.containsKey(name)) {
            loadKeys(name);
        }
        if (nameMap.get(name).isEmpty()) {
            loadKeys(name);
        }

        return nameMap.get(name);
    }

    public void loadKeys(String name) {
        nameMap.put(
                name,
                blockChain.getAllIdentitiesWithName(name).stream()
                        .map(IdentityEntry::getPublicKey)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof BlockChainManager) {
            blockChain = ((BlockChainManager) o).getBlockChain();
        }
    }
}
