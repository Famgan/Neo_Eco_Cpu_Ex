package dev.modmind.neo_eco_cpu_ex.core;

import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;

/** Duck-typed exact view added to the stock cluster and logical network cluster by Mixin. */
public interface NeoBigStorageView {
    LongArrayBigInt neoecoae$totalStorageExact();

    LongArrayBigInt neoecoae$usedStorageExact();

    LongArrayBigInt neoecoae$availableStorageExact();

    boolean neoecoae$reservationsFitExact();

    /**
     * Whether the stock endgame override is active.
     *
     * <p>NeoECOAE represents its eight-host endgame topology with a synthetic
     * {@link Long#MAX_VALUE} capacity and {@link Integer#MAX_VALUE} threads/parallelism. The exact
     * storage APIs intentionally keep that value for gameplay compatibility, but the UI should
     * present this override as infinity rather than pretending the physical cell total is 8 EB.
     * Ordinary exact values that happen to equal {@link Long#MAX_VALUE} remain finite.</p>
     */
    default boolean neoecoae$displayAsInfinite() {
        return false;
    }
}