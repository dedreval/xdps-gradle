package com.wiley.tes.util;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 8/9/2019
 */
public class BitValue {
    public static final int BITS_0_1 = 3;

    private static final int MAX_SIZE = 32;

    private static final int[] MASKS = new int[MAX_SIZE];

    static {
        for (int i = 0; i < MAX_SIZE; i++) {
            MASKS[i] = 1 << i;
        }
    }

    private int value;

    public BitValue() {
        this(0);
    }

    public BitValue(int value) {
        setRawValue(value);
    }

    public final int getRawValue() {
        return value;
    }

    public final boolean getBit(byte bitNum) {
        return (value & MASKS[bitNum]) > 0;
    }

    public final int setRawValue(int value) {
        this.value = value;
        return value;
    }

    public final int setBit(byte bitNum) {
        return setRawValue(setBit(bitNum, getRawValue()));
    }

    public final int resetBit(byte bitNum) {
        return setRawValue(resetBit(bitNum, getRawValue()));
    }

    public final int setBit(byte bitNum, boolean bitValue) {
        return bitValue ? setBit(bitNum) : resetBit(bitNum);
    }

    public char[] getBits(int count) {
        char[] bits = new char[count > MAX_SIZE ? MAX_SIZE : count];
        for (char i = 0; i < bits.length; i++) {
            bits[i] = getBitValue(i, value);
        }
        return bits;
    }

    public static boolean getBit(int bitNum, int value) {
        return getBitValue(bitNum, value) > 0;
    }

    public static int setBit(int bitNum, int v) {
        return v | MASKS[bitNum];
    }

    public static int resetBit(int bitNum, int v) {
        return v & ~MASKS[bitNum];
    }

    private static char getBitValue(int bitNum, int value) {
        return (char) (value & MASKS[bitNum]);
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder(MAX_SIZE);

        boolean append = false;

        for (int i = MAX_SIZE - 1; i >= 0; i--) {

            char bit = getBitValue(i, value);
            if (!append && bit > 0) {
                append = true;
            }

            if (append) {
                sb.append(bit > 0 ? 1 : 0);
            }
        }

        return sb.toString();
    }
}
