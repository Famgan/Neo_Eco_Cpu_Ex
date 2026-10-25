package dev.modmind.neo_eco_cpu_ex.core;

import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;

/** Exact, display-safe CPU storage added to ECOCraftingCPU by mixin. */
public interface NeoCpuStorageView {
    LongArrayBigInt neoecoae$availableStorageExact();

    /** Used only when serialising UI state; crafting code must keep using the saturated getter. */
    default long neoecoae$availableStorageDisplay() {
        return InfiniteDisplay.toDisplayLong(neoecoae$availableStorageExact());
    }
}