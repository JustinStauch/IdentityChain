package identitychain.network;

import identitychain.blockchain.Block;
import identitychain.blockchain.BlockChain;
import identitychain.blockchain.BlockChainManager;
import identitychain.blockchain.utilities.BlockChainInt;
import identitychain.network.packets.BlockRequestPacket;
import identitychain.network.packets.InfoRequestPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Queue;
import java.util.Stack;

public class BlockChainSynchronizer {
    private static final int BUFFER_SIZE = 10;

    private final BlockChain blockChain;
    private final BlockChainManager manager;

    public BlockChainSynchronizer(BlockChain blockChain, BlockChainManager manager) {
        this.blockChain = blockChain;
        this.manager = manager;
    }

    public void synchronize(Socket socket) {
        BlockChain newChain = null;
        BlockChainInt firstCommonBlock = null;
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(new InfoRequestPacket(InfoRequestPacket.InfoType.BLOCK_CHAIN_TRACE));

            final List<BlockChainInt> trace = (List<BlockChainInt>) in.readObject();

            firstCommonBlock = blockChain.getFirstCommonBlock(trace);

            newChain = blockChain.forkBlockChain(firstCommonBlock);

            while (true) {
                out.writeObject(new BlockRequestPacket(newChain.getHeadHash(), BUFFER_SIZE));
                final Queue<Block> blocks = (Queue<Block>) in.readObject();
                if (blocks.isEmpty()) {
                    break;
                }

                final Stack<Block> blockStack = reverseQueue(blocks);
                boolean flag = false;

                while (!blockStack.isEmpty()) {
                    if (!newChain.pushBlock(blockStack.pop())) {
                        flag = true;
                        break;
                    }
                }

                if (flag) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (newChain != null) {
            if (!manager.replaceBlockChain(newChain)) {
                newChain.delete(firstCommonBlock);
                blockChain.saveToFileInBackground();
            }
        } else {
            blockChain.saveToFileInBackground();
        }
    }

    private Stack<Block> reverseQueue(Queue<Block> blocks) {
        final Stack<Block> blockStack = new Stack<>();

        while (!blocks.isEmpty()) {
            blockStack.push(blocks.poll());
        }

        return blockStack;
    }
}
