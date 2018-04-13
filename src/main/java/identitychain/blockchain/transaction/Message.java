package identitychain.blockchain.transaction;

import identitychain.blockchain.utilities.BlockChainInt;
import identitychain.blockchain.utilities.Utilities;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.*;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Message extends Transaction {
    private final PublicKey sender;
    private final PublicKey receiver;
    private final byte[] encryptedAESKey;
    private final byte[] body;
    private final int time;

    private byte[] signature;

    private Message(PublicKey sender, PublicKey receiver, byte[] encryptedAESKey, byte[] body, int time) {
        this.sender = sender;
        this.receiver = receiver;
        this.encryptedAESKey = encryptedAESKey;
        this.body = body;
        this.time = time;
    }

    public PublicKey getSender() {
        return sender;
    }

    public PublicKey getReceiver() {
        return receiver;
    }

    public String getDate() {
        Date date = new Date(time * 1000L);
        final Format format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(date);
    }

    public static Message generateMessage(PublicKey sender, PublicKey receiver, String body) {
        final int time = (int) (System.currentTimeMillis() / 1000L);

        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);

            final SecretKey aesKey = generator.generateKey();

            final Cipher bodyCipher = Cipher.getInstance("AES");
            bodyCipher.init(Cipher.ENCRYPT_MODE, aesKey);
            final byte[] encryptedBody = bodyCipher.doFinal(body.getBytes("UTF-8"));

            final Cipher keyCipher = Cipher.getInstance("RSA");
            keyCipher.init(Cipher.ENCRYPT_MODE, receiver);
            final byte[] encryptedKey = keyCipher.doFinal(aesKey.getEncoded());

            return new Message(sender, receiver, encryptedKey, encryptedBody, time);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void sign(PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");

            sig.initSign(privateKey);
            sig.update(ByteBuffer.allocate(Long.BYTES).putLong(getID()).array());
            sig.update(sender.getEncoded());
            sig.update(receiver.getEncoded());
            sig.update(encryptedAESKey);
            sig.update(body);
            sig.update(ByteBuffer.allocate(Integer.BYTES).putInt(time).array());

            signature = sig.sign();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isValid() {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");

            sig.initVerify(sender);
            sig.update(ByteBuffer.allocate(Long.BYTES).putLong(getID()).array());
            sig.update(sender.getEncoded());
            sig.update(receiver.getEncoded());
            sig.update(encryptedAESKey);
            sig.update(body);
            sig.update(ByteBuffer.allocate(Integer.BYTES).putInt(time).array());

            return sig.verify(signature);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        return false;
    }

    public String getMessage(PrivateKey privateKey) {
        try {
            final Cipher keyCipher = Cipher.getInstance("RSA");
            keyCipher.init(Cipher.DECRYPT_MODE, privateKey);

            final SecretKey aesKey = new SecretKeySpec(keyCipher.doFinal(encryptedAESKey), "AES");

            final Cipher bodyCipher = Cipher.getInstance("AES");
            bodyCipher.init(Cipher.DECRYPT_MODE, aesKey);

            return new String(bodyCipher.doFinal(body));

        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        return "";
    }

    @Override
    public BlockChainInt getHash() {
        try {
            final MessageDigest hash = MessageDigest.getInstance("SHA-256");


            hash.update(ByteBuffer.allocate(Long.BYTES).putLong(getID()).array());
            hash.update(sender.getEncoded());
            hash.update(receiver.getEncoded());
            hash.update(encryptedAESKey);
            hash.update(body);
            hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(time).array());
            hash.update(signature);

            return BlockChainInt.fromByteArray(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        return BlockChainInt.ZERO;
    }

    @Override
    public String toString() {
        return "Message\nID: " + getID()
                + "\nHash: " + getHash()
                + "\n\nFrom: " + Utilities.computeFingerPrint(sender)
                + "\nDate: " + getDate()
                + "\nTo: " + Utilities.computeFingerPrint(receiver);
    }
}
