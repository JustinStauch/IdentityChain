package identitychain.network.packets;

import java.io.Serializable;

public class InfoRequestPacket implements Serializable {

    public enum InfoType {
        BLOCK_CHAIN_SUMMARY, BLOCK_CHAIN_TRACE, NEIGHBOURS
    }

    private final InfoType infoType;

    public InfoRequestPacket(InfoType infoType) {
        this.infoType = infoType;
    }

    public InfoType getInfoType() {
        return infoType;
    }
}
