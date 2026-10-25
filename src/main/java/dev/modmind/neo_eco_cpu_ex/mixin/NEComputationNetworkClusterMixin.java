package dev.modmind.neo_eco_cpu_ex.mixin;

import dev.modmind.neo_eco_cpu_ex.core.NeoBigStorageView;
import dev.modmind.neo_eco_cpu_ex.core.NeoExMixinMarker;
import dev.modmind.neo_eco_cpu_ex.core.NeoBytes;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Makes the pooled 2-8 host byte view exact as well, including multiplier saturation. */
@Mixin(value = NEComputationNetworkCluster.class, remap = false)
public abstract class NEComputationNetworkClusterMixin implements NeoBigStorageView, NeoExMixinMarker {
    @Shadow @Final private List<NEComputationCluster> members;

    @Shadow public abstract boolean isEndgameEligible();

    @Override
    @Unique
    public LongArrayBigInt neoecoae$totalStorageExact() {
        if (isEndgameEligible()) {
            return LongArrayBigInt.LONG_MAX;
        }
        LongArrayBigInt total = LongArrayBigInt.ZERO;
        for (NEComputationCluster member : this.members) {
            NeoBigStorageView view = (NeoBigStorageView) (Object) member;
            int multiplier = this.members.size() > 1 ? member.getNetworkMultiplier() : 1;
            total = total.add(view.neoecoae$totalStorageExact().multiplyNonNegative(multiplier));
        }
        return total;
    }

    @Override
    @Unique
    public LongArrayBigInt neoecoae$usedStorageExact() {
        LongArrayBigInt used = LongArrayBigInt.ZERO;
        for (NEComputationCluster member : this.members) {
            NeoBigStorageView view = (NeoBigStorageView) (Object) member;
            used = used.add(view.neoecoae$usedStorageExact());
        }
        return used;
    }

    @Override
    @Unique
    public LongArrayBigInt neoecoae$availableStorageExact() {
        LongArrayBigInt total = neoecoae$totalStorageExact();
        return NeoBytes.nonNegativeDifference(total, neoecoae$usedStorageExact());
    }

    @Override
    @Unique
    public boolean neoecoae$reservationsFitExact() {
        return neoecoae$usedStorageExact().compareTo(neoecoae$totalStorageExact()) <= 0;
    }

    /**
     * The stock endgame override is conceptually infinite even though its physical long mirror is
     * capped at {@link Long#MAX_VALUE}. Expose that fact only to display-boundary conversions.
     */
    @Override
    @Unique
    public boolean neoecoae$displayAsInfinite() {
        return isEndgameEligible();
    }

    @Inject(method = "getTotalStorageCapacity", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getTotalStorageCapacity(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(neoecoae$totalStorageExact().toLongSaturated());
    }

    @Inject(method = "getPooledAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getPooledAvailableStorage(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(neoecoae$availableStorageExact().toLongSaturated());
    }

    @Inject(method = "getReservedStorage", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getReservedStorage(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(neoecoae$usedStorageExact().toLongSaturated());
    }

    @Inject(method = "reservationsFit", at = @At("HEAD"), cancellable = true)
    private void neoecoae$reservationsFit(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(neoecoae$reservationsFitExact());
    }

    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void neoecoae$guardSubmit(
        IGrid grid,
        ICraftingPlan job,
        IActionSource src,
        ICraftingRequester requestingMachine,
        CallbackInfoReturnable<ICraftingSubmitResult> cir
    ) {
        boolean anyActive = false;
        for (NEComputationCluster member : this.members) {
            if (member.isActive()) {
                anyActive = true;
                break;
            }
        }
        if (!anyActive) {
            return;
        }
        if (NeoBytes.requiredStorage(job).compareTo(neoecoae$availableStorageExact()) > 0) {
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
        }
    }
}