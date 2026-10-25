package dev.modmind.neo_eco_cpu_ex.mixin;

import dev.modmind.neo_eco_cpu_ex.core.ExactBytePlan;
import dev.modmind.neo_eco_cpu_ex.core.InfiniteDisplay;
import dev.modmind.neo_eco_cpu_ex.core.ExactBytesPlan;
import dev.modmind.neo_eco_cpu_ex.core.NeoBigStorageView;
import dev.modmind.neo_eco_cpu_ex.core.NeoBytes;
import dev.modmind.neo_eco_cpu_ex.core.NeoCpuStorageView;
import dev.modmind.neo_eco_cpu_ex.core.NeoExMixinMarker;

import appeng.api.networking.crafting.ICraftingPlan;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Covers ECOCraftingCPU's long conversion and NBT boundary.
 *
 * <p>NEComputationCluster itself is transient. The actual reservation that must survive a world
 * save/unload is the plan carried by a threading core, so the exact limb array is written beside
 * the stock {@code bytes} long and reconstructed as a wrapper on load.</p>
 */
@Mixin(value = ECOCraftingCPU.class, remap = false)
public abstract class ECOCraftingCPUMixin implements NeoCpuStorageView, NeoExMixinMarker {
    @Shadow private ICraftingPlan plan;

    @Shadow public abstract NEComputationCluster getCluster();

    @Override
    @Unique
    public LongArrayBigInt neoecoae$availableStorageExact() {
        NEComputationCluster cluster = getCluster();
        if (cluster == null) {
            return LongArrayBigInt.ZERO;
        }
        LongArrayBigInt free = ((NeoBigStorageView) (Object) cluster).neoecoae$availableStorageExact();
        if (this.plan != null && cluster.isPlanRegistered(this.plan)) {
            free = free.add(NeoBytes.requiredStorage(this.plan));
        }
        return free;
    }

    /**
     * Overrides the interface's ordinary exact-to-display conversion so the stock endgame override
     * can be represented as infinity without changing the saturated gameplay getter below.
     */
    @Override
    @Unique
    public long neoecoae$availableStorageDisplay() {
        NEComputationCluster cluster = getCluster();
        boolean displayAsInfinite = cluster != null
            && ((NeoBigStorageView) (Object) cluster).neoecoae$displayAsInfinite();
        return InfiniteDisplay.toDisplayLong(neoecoae$availableStorageExact(), displayAsInfinite);
    }

    /**
     * The stock method is used by crafting admission as well as UI sampling. Keep it saturated and
     * expose the magic-value conversion only through {@link NeoCpuStorageView}.
     */
    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getAvailableStorage(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(neoecoae$availableStorageExact().toLongSaturated());
    }

    /** Adds the exact payload without changing the stock key, so old readers keep working. */
    @Inject(method = "writeCraftingPlanToNBT", at = @At("TAIL"))
    private void neoecoae$writeExactPlanBytes(
        ICraftingPlan plan,
        CompoundTag tag,
        HolderLookup.Provider registries,
        CallbackInfo ci
    ) {
        NeoBytes.requiredStorage(plan).writeTo(tag, NeoBytes.NBT_KEY);
    }

    /**
     * The stock reader cannot return a non-long value, so restore its plan first and then replace
     * only that field with a delegating wrapper when the saved exact value is wider than long.
     */
    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void neoecoae$readExactPlanBytes(
        CompoundTag data,
        HolderLookup.Provider registries,
        CallbackInfo ci
    ) {
        if (this.plan == null || this.plan instanceof ExactBytePlan) {
            return;
        }
        CompoundTag planTag = data.getCompound("plan");
        LongArrayBigInt exact = LongArrayBigInt.readFrom(planTag, NeoBytes.NBT_KEY);
        if (exact == null) {
            return;
        }
        LongArrayBigInt restoredLong = LongArrayBigInt.of(this.plan.bytes());
        if (exact.compareTo(restoredLong) > 0) {
            this.plan = new ExactBytesPlan(this.plan, exact.max(LongArrayBigInt.ZERO));
        }
    }
}