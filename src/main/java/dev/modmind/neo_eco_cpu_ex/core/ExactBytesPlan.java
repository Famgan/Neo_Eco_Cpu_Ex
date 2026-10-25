package dev.modmind.neo_eco_cpu_ex.core;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;

import java.util.Map;
import java.util.Objects;

/**
 * Read-only compatibility wrapper used when NBT restores an exact reservation wider than long.
 * AE2 still sees a saturated long, while this addon sees the original limb array.
 */
public final class ExactBytesPlan implements ExactBytePlan {
    private final ICraftingPlan delegate;
    private final LongArrayBigInt exactBytes;

    public ExactBytesPlan(ICraftingPlan delegate, LongArrayBigInt exactBytes) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.exactBytes = Objects.requireNonNull(exactBytes, "exactBytes");
    }

    @Override
    public LongArrayBigInt neoecoae$exactBytes() {
        return exactBytes;
    }

    @Override
    public GenericStack finalOutput() {
        return delegate.finalOutput();
    }

    @Override
    public long bytes() {
        return exactBytes.toLongSaturated();
    }

    @Override
    public boolean simulation() {
        return delegate.simulation();
    }

    @Override
    public boolean multiplePaths() {
        return delegate.multiplePaths();
    }

    @Override
    public KeyCounter usedItems() {
        return delegate.usedItems();
    }

    @Override
    public KeyCounter emittedItems() {
        return delegate.emittedItems();
    }

    @Override
    public KeyCounter missingItems() {
        return delegate.missingItems();
    }

    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return delegate.patternTimes();
    }
}