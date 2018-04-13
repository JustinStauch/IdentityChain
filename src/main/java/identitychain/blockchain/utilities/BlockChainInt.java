package identitychain.blockchain.utilities;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Stores a 256 bit unsigned integer, keeping all leading zeros.
 */
public class BlockChainInt extends Number implements Serializable, Comparable<BlockChainInt> {
    public static final BlockChainInt ZERO = new BlockChainInt(new byte[0]);
    public static final BlockChainInt ONE = new BlockChainInt(new byte[]{1});
    public static final BlockChainInt MAX_TARGET = getMaxTarget();
    public static final BlockChainInt MAX = getMaxValue();

    public static final int BYTES = 64;

    private final byte[] value = new byte[BYTES];
    private transient BigInteger bigInt = new BigInteger(1, value);

    private BlockChainInt(byte[] value) {
        for (int i = 1; i <= value.length && i <= this.value.length; i++) {
            this.value[this.value.length - i] = value[value.length - i];
        }

        bigInt = new BigInteger(1, value);
    }

    public static BlockChainInt fromByteArray(byte[] value) {
        return new BlockChainInt(value);
    }

    public static BlockChainInt fromBigInteger(BigInteger value) {
        return new BlockChainInt(value.toByteArray());
    }

    public static BlockChainInt fromString(String value, int base) {
        return fromBigInteger(new BigInteger(value, base));
    }

    public static BlockChainInt fromDouble(double value) {
        return fromBigInteger(BigDecimal.valueOf(value).toBigInteger());
    }

    private static BlockChainInt getMaxValue() {
        final byte[] max = new byte[64];

        for (int i = 0; i < max.length; i++) {
            max[i] = (byte) 0xFF;
        }

        return new BlockChainInt(max);
    }

    private static BlockChainInt getMaxTarget() {
        byte[] value = new byte[64];
        value[1] = (byte) 0xFF;

        return new BlockChainInt(value);
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(value, value.length);
    }

    public BigInteger toBigInteger() {
        return bigInt;
    }


    @Override
    public String toString() {
        return toString(64);
    }

    public String toString(int base) {
        return bigInt.toString(base);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BlockChainInt) {
            final BlockChainInt bcInt = (BlockChainInt) o;

            for (int i = 0; i < value.length; i++) {
                if (value[i] != bcInt.value[i]) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return bigInt.hashCode();
    }

    @Override
    public int compareTo(BlockChainInt blockChainInt) {
        return bigInt.compareTo(blockChainInt.bigInt);
    }

    @Override
    public int intValue() {
        return bigInt.intValue();
    }

    @Override
    public long longValue() {
        return bigInt.longValue();
    }

    @Override
    public float floatValue() {
        return bigInt.floatValue();
    }

    @Override
    public double doubleValue() {
        return bigInt.doubleValue();
    }
}
