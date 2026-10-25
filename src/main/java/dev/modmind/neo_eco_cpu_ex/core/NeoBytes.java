package dev.modmind.neo_eco_cpu_ex.core;

import appeng.api.networking.crafting.ICraftingPlan;
import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;

public final class NeoBytes {
    public static final String NBT_KEY = "neo_eco_cpu_ex$exactBytes";

    private NeoBytes() {
    }

    public static LongArrayBigInt requiredStorage(ICraftingPlan plan) {
        if (plan == null) {
            return LongArrayBigInt.ZERO;
        }
        if (plan instanceof ExactBytePlan exact) {
            LongArrayBigInt value = exact.neoecoae$exactBytes();
            return value == null ? LongArrayBigInt.ZERO : value.max(LongArrayBigInt.ZERO);
        }

        long legacy = plan.bytes();
        // A negative legacy value cannot be a real reservation. Treat it as a saturated overflow
        // instead of silently turning an overflowing job into a zero-byte job.
        if (legacy < 0L) {
            return LongArrayBigInt.LONG_MAX;
        }
        return LongArrayBigInt.of(legacy);
    }

    public static LongArrayBigInt nonNegativeDifference(LongArrayBigInt total, LongArrayBigInt used) {
        LongArrayBigInt safeUsed = used.min(total);
        return total.subtract(safeUsed).max(LongArrayBigInt.ZERO);
    }
}