package dev.modmind.neo_eco_cpu_ex.core;

import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;
import net.minecraft.locale.Language;

/**
 * Reserves a display-only sentinel for exact byte counts that do not fit in a signed long.
 *
 * <p>{@link Long#MAX_VALUE} is a real capacity (the vanilla eight-cluster maximum is exactly
 * 8 EB), so it must keep flowing into the stock formatter. Only values strictly greater than that
 * are converted to {@link #OVERFLOW_MAGIC}; this lets the UI distinguish the two cases without
 * changing the saturated values used by crafting logic.</p>
 */
public final class InfiniteDisplay {
    public static final String LANG_KEY = "gui.neo_eco_cpu_ex.infinite";
    public static final String MEANINGLESS_LANG_KEY = "gui.neo_eco_cpu_ex.meaningless";

    /**
     * Display-only marker. Real byte counts are non-negative, {@link Long#MIN_VALUE} cannot be
     * produced by any valid storage value, and the controller snapshot is patched to preserve it.
     */
    public static final long OVERFLOW_MAGIC = Long.MIN_VALUE;

    private static final String FALLBACK = "\u221E";
    private static final String MEANINGLESS_FALLBACK = "N/A";

    private InfiniteDisplay() {
    }

    /**
     * Converts an exact storage value at a UI boundary.
     *
     * <p>Values up to and including {@link Long#MAX_VALUE} retain their normal non-negative form,
     * while a genuinely wider value receives {@link #OVERFLOW_MAGIC}. Negative exact values are
     * clamped to zero so they can never be mistaken for the sentinel.</p>
     */
    public static long toDisplayLong(LongArrayBigInt exact) {
        LongArrayBigInt safe = exact.max(LongArrayBigInt.ZERO);
        return safe.compareTo(LongArrayBigInt.LONG_MAX) > 0
            ? OVERFLOW_MAGIC
            : safe.toLongSaturated();
    }

    /**
     * Converts a display-facing exact value, optionally forcing the infinity/sentinel path for a
     * stock endgame override whose internal long mirror is exactly {@link Long#MAX_VALUE}.
     */
    public static long toDisplayLong(LongArrayBigInt exact, boolean endgameOverride) {
        return endgameOverride ? OVERFLOW_MAGIC : toDisplayLong(exact);
    }

    /** Returns whether a display-facing long carries the reserved overflow marker. */
    public static boolean isSaturated(long value) {
        return value == OVERFLOW_MAGIC;
    }

    public static String text() {
        String localized = Language.getInstance().getOrDefault(LANG_KEY);
        return localized.equals(LANG_KEY) ? FALLBACK : localized;
    }

    /**
     * Text used when an overflowing pool makes a component such as "used" or "usage" meaningless.
     * The English resource supplies "N/A", while the Chinese resource uses "无意义".
     */
    public static String meaninglessText() {
        String localized = Language.getInstance().getOrDefault(MEANINGLESS_LANG_KEY);
        return localized.equals(MEANINGLESS_LANG_KEY) ? MEANINGLESS_FALLBACK : localized;
    }
}