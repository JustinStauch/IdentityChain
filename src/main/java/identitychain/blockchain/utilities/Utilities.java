package identitychain.blockchain.utilities;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public final class Utilities {

    private Utilities() {

    }

    public static String computeFingerPrint(PublicKey key) {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-1");
            hash.update(key.getEncoded());
            return DatatypeConverter.printHexBinary(hash.digest()).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return "";
    }
}
