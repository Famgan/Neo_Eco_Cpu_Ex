package dev.modmind.neo_eco_cpu_ex.math;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable signed arbitrary-precision integer backed by unsigned {@code long} limbs.
 *
 * <p>Limbs are little-endian and contain the magnitude only. The sign is stored separately,
 * so {@link Long#MIN_VALUE} and every unsigned limb value are representable without losing a bit.</p>
 */
public final class LongArrayBigInt implements Comparable<LongArrayBigInt> {
    public static final LongArrayBigInt ZERO = new LongArrayBigInt(0, new long[0]);
    public static final LongArrayBigInt ONE = new LongArrayBigInt(1, new long[]{1L});
    public static final LongArrayBigInt LONG_MAX = new LongArrayBigInt(1, new long[]{Long.MAX_VALUE});

    private static final int MAX_SMALL_MULTIPLIER = Integer.MAX_VALUE;

    private final int sign;
    private final long[] magnitude;

    private LongArrayBigInt(int sign, long[] magnitude) {
        if (sign < -1 || sign > 1) {
            throw new IllegalArgumentException("sign must be -1, 0 or 1");
        }
        if (sign == 0) {
            this.sign = 0;
            this.magnitude = new long[0];
            return;
        }
        long[] normalized = trim(magnitude);
        if (normalized.length == 0) {
            this.sign = 0;
            this.magnitude = normalized;
        } else {
            this.sign = sign;
            this.magnitude = normalized;
        }
    }

    public static LongArrayBigInt of(long value) {
        if (value == 0L) {
            return ZERO;
        }
        if (value > 0L) {
            return new LongArrayBigInt(1, new long[]{value});
        }
        // 0L - Long.MIN_VALUE wraps to 0x8000_0000_0000_0000, which is exactly 2^63.
        return new LongArrayBigInt(-1, new long[]{0L - value});
    }

    /** Encodes the sign in slot zero and the little-endian magnitude after it. */
    public long[] toLongArray() {
        if (sign == 0) {
            return new long[]{0L};
        }
        long[] encoded = new long[magnitude.length + 1];
        encoded[0] = sign;
        System.arraycopy(magnitude, 0, encoded, 1, magnitude.length);
        return encoded;
    }

    /** Inverse of {@link #toLongArray()}. Malformed input is rejected. */
    public static LongArrayBigInt fromLongArray(long[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0) {
            throw new IllegalArgumentException("Missing sign limb");
        }
        if (encoded[0] < -1L || encoded[0] > 1L) {
            throw new IllegalArgumentException("Invalid sign limb: " + encoded[0]);
        }
        int sign = (int) encoded[0];
        if (sign == 0 && encoded.length != 1) {
            throw new IllegalArgumentException("Zero must not carry magnitude limbs");
        }
        return new LongArrayBigInt(sign, Arrays.copyOfRange(encoded, 1, encoded.length));
    }

    public boolean isZero() {
        return sign == 0;
    }

    public boolean isNegative() {
        return sign < 0;
    }

    public boolean isPositive() {
        return sign > 0;
    }

    public int signum() {
        return sign;
    }

    public LongArrayBigInt negate() {
        return sign == 0 ? ZERO : new LongArrayBigInt(-sign, magnitude);
    }

    public LongArrayBigInt abs() {
        return sign < 0 ? new LongArrayBigInt(1, magnitude) : this;
    }

    public LongArrayBigInt add(LongArrayBigInt other) {
        Objects.requireNonNull(other, "other");
        if (sign == 0) {
            return other;
        }
        if (other.sign == 0) {
            return this;
        }
        if (sign == other.sign) {
            return new LongArrayBigInt(sign, addMagnitudes(magnitude, other.magnitude));
        }
        int comparison = compareMagnitudes(magnitude, other.magnitude);
        if (comparison == 0) {
            return ZERO;
        }
        if (comparison > 0) {
            return new LongArrayBigInt(sign, subtractMagnitudes(magnitude, other.magnitude));
        }
        return new LongArrayBigInt(other.sign, subtractMagnitudes(other.magnitude, magnitude));
    }

    public LongArrayBigInt add(long value) {
        return add(of(value));
    }

    public LongArrayBigInt subtract(LongArrayBigInt other) {
        Objects.requireNonNull(other, "other");
        return add(other.negate());
    }

    public LongArrayBigInt subtract(long value) {
        return subtract(of(value));
    }

    /**
     * Multiplies by a non-negative {@code int}. This is used only for the 1/2/8 network multiplier;
     * throwing for a wider factor keeps the carry bound trivially provable.
     */
    public LongArrayBigInt multiplyNonNegative(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must be non-negative");
        }
        if (factor == 0 || sign == 0) {
            return ZERO;
        }
        if (factor == 1) {
            return this;
        }
        if (factor > MAX_SMALL_MULTIPLIER) {
            throw new IllegalArgumentException("factor is too large");
        }

        long[] result = new long[magnitude.length + 1];
        long carry = 0L;
        for (int i = 0; i < magnitude.length; i++) {
            long limb = magnitude[i];
            long low = limb * factor;
            long high = Math.unsignedMultiplyHigh(limb, factor);
            long sum = low + carry;
            long overflow = Long.compareUnsigned(sum, low) < 0 ? 1L : 0L;
            result[i] = sum;
            carry = high + overflow;
        }
        result[magnitude.length] = carry;
        return new LongArrayBigInt(sign, result);
    }

    public LongArrayBigInt min(LongArrayBigInt other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public LongArrayBigInt max(LongArrayBigInt other) {
        return compareTo(other) >= 0 ? this : other;
    }

    /** Clamps to {@link Long#MIN_VALUE}..{@link Long#MAX_VALUE} for legacy long-only call sites. */
    public long toLongSaturated() {
        if (sign == 0) {
            return 0L;
        }
        if (sign > 0) {
            if (magnitude.length > 1 || Long.compareUnsigned(magnitude[0], Long.MAX_VALUE) > 0) {
                return Long.MAX_VALUE;
            }
            return magnitude[0];
        }
        if (magnitude.length > 1 || Long.compareUnsigned(magnitude[0], 0x8000_0000_0000_0000L) > 0) {
            return Long.MIN_VALUE;
        }
        return 0L - magnitude[0];
    }

    public long longValueExact() {
        long result = toLongSaturated();
        if (!equals(of(result))) {
            throw new ArithmeticException("LongArrayBigInt does not fit in a long");
        }
        return result;
    }

    public boolean fitsLong() {
        try {
            longValueExact();
            return true;
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("sign", (byte) sign);
        tag.putLongArray("magnitude", magnitude);
        return tag;
    }

    public void writeTo(CompoundTag parent, String key) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(key, "key");
        parent.put(key, toTag());
    }

    /** Returns null when the key is absent or the payload is malformed. */
    public static @Nullable LongArrayBigInt readFrom(@Nullable CompoundTag parent, String key) {
        if (parent == null || !parent.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = parent.getCompound(key);
        int sign = tag.getByte("sign");
        if (sign < -1 || sign > 1) {
            return null;
        }
        if (sign == 0) {
            return ZERO;
        }
        long[] magnitude = tag.getLongArray("magnitude");
        if (magnitude.length == 0) {
            return null;
        }
        return new LongArrayBigInt(sign, magnitude);
    }

    @Override
    public int compareTo(LongArrayBigInt other) {
        Objects.requireNonNull(other, "other");
        if (sign != other.sign) {
            return Integer.compare(sign, other.sign);
        }
        if (sign == 0) {
            return 0;
        }
        int magnitudeComparison = compareMagnitudes(magnitude, other.magnitude);
        return sign > 0 ? magnitudeComparison : -magnitudeComparison;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LongArrayBigInt value
            && sign == value.sign
            && Arrays.equals(magnitude, value.magnitude);
    }

    @Override
    public int hashCode() {
        return 31 * sign + Arrays.hashCode(magnitude);
    }

    /** Debug representation. Persistence uses the sign + long[] NBT layout above. */
    @Override
    public String toString() {
        if (sign == 0) {
            return "0";
        }
        StringBuilder result = new StringBuilder(sign < 0 ? "-0x" : "0x");
        int top = magnitude.length - 1;
        result.append(Long.toUnsignedString(magnitude[top], 16));
        for (int i = top - 1; i >= 0; i--) {
            result.append(String.format(Locale.ROOT, "%016x", magnitude[i]));
        }
        return result.toString();
    }

    private static long[] trim(long[] value) {
        int length = value.length;
        while (length > 0 && value[length - 1] == 0L) {
            length--;
        }
        return length == value.length ? value.clone() : Arrays.copyOf(value, length);
    }

    private static int compareMagnitudes(long[] left, long[] right) {
        if (left.length != right.length) {
            return Integer.compare(left.length, right.length);
        }
        for (int i = left.length - 1; i >= 0; i--) {
            int comparison = Long.compareUnsigned(left[i], right[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static long[] addMagnitudes(long[] left, long[] right) {
        int length = Math.max(left.length, right.length);
        long[] result = new long[length + 1];
        long carry = 0L;
        for (int i = 0; i < length; i++) {
            long a = i < left.length ? left[i] : 0L;
            long b = i < right.length ? right[i] : 0L;
            long sum = a + b;
            long carryFromSum = Long.compareUnsigned(sum, a) < 0 ? 1L : 0L;
            long withCarry = sum + carry;
            long carryFromPrevious = carry != 0L && Long.compareUnsigned(withCarry, sum) < 0 ? 1L : 0L;
            result[i] = withCarry;
            carry = carryFromSum | carryFromPrevious;
        }
        result[length] = carry;
        return trim(result);
    }

    /** Requires {@code left >= right}. */
    private static long[] subtractMagnitudes(long[] left, long[] right) {
        long[] result = new long[left.length];
        long borrow = 0L;
        for (int i = 0; i < left.length; i++) {
            long a = left[i];
            long b = i < right.length ? right[i] : 0L;
            long difference = a - b;
            long borrowFromLimb = Long.compareUnsigned(a, b) < 0 ? 1L : 0L;
            long withBorrow = difference - borrow;
            long borrowFromPrevious = borrow != 0L && Long.compareUnsigned(difference, borrow) < 0 ? 1L : 0L;
            result[i] = withBorrow;
            borrow = borrowFromLimb | borrowFromPrevious;
        }
        if (borrow != 0L) {
            throw new IllegalStateException("Magnitude subtraction underflow");
        }
        return trim(result);
    }
}