package dev.modmind.neo_eco_cpu_ex.core;

import appeng.api.networking.crafting.ICraftingPlan;
import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;

/**
 * Optional marker for an {@link ICraftingPlan} whose exact byte reservation does not fit in
 * the long returned by {@link ICraftingPlan#bytes()}.
 */
public interface ExactBytePlan extends ICraftingPlan {
    LongArrayBigInt neoecoae$exactBytes();
}