package dev.modmind.neo_eco_cpu_ex.mixin;

import dev.modmind.neo_eco_cpu_ex.core.InfiniteDisplay;
import dev.modmind.neo_eco_cpu_ex.core.NeoBigStorageView;
import dev.modmind.neo_eco_cpu_ex.core.NeoExMixinMarker;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies display-only byte values to the computation controller.
 *
 * <p>These three private getters feed {@code ComputationControllerUI.Config}; they are not used by
 * crafting admission. Internal cluster/network/CPU getters therefore stay saturated at
 * {@link Long#MAX_VALUE}, while a genuinely wider exact pool becomes {@link InfiniteDisplay#OVERFLOW_MAGIC}.
 * The stock endgame override is a special case: its exact capacity is intentionally stored as
 * {@link Long#MAX_VALUE}, but for display it represents infinity rather than a physical 8 EB pool.</p>
 */
@Mixin(value = ECOComputationSystemBlockEntity.class, remap = false)
public abstract class ECOComputationSystemBlockEntityMixin implements NeoExMixinMarker {
    @Inject(method = "getUsedComputationBytes", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getUsedComputationBytes(CallbackInfoReturnable<Long> cir) {
        ECOComputationSystemBlockEntity self = (ECOComputationSystemBlockEntity) (Object) this;
        NeoBigStorageView view = neoecoae$storageView(self.getCluster());
        if (view == null) {
            cir.setReturnValue(0L);
            return;
        }
        // The original getter is max(total - available, 0), which is min(total, used).
        LongArrayBigInt used = view.neoecoae$usedStorageExact()
            .min(view.neoecoae$totalStorageExact());
        cir.setReturnValue(InfiniteDisplay.toDisplayLong(used, view.neoecoae$displayAsInfinite()));
    }

    @Inject(method = "getAvailableBytes", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getAvailableBytes(CallbackInfoReturnable<Long> cir) {
        ECOComputationSystemBlockEntity self = (ECOComputationSystemBlockEntity) (Object) this;
        NeoBigStorageView view = neoecoae$storageView(self.getCluster());
        cir.setReturnValue(view == null
            ? 0L
            : InfiniteDisplay.toDisplayLong(view.neoecoae$availableStorageExact(), view.neoecoae$displayAsInfinite()));
    }

    @Inject(method = "getTotalBytes", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getTotalBytes(CallbackInfoReturnable<Long> cir) {
        ECOComputationSystemBlockEntity self = (ECOComputationSystemBlockEntity) (Object) this;
        NeoBigStorageView view = neoecoae$storageView(self.getCluster());
        cir.setReturnValue(view == null
            ? 0L
            : InfiniteDisplay.toDisplayLong(view.neoecoae$totalStorageExact(), view.neoecoae$displayAsInfinite()));
    }

    @Unique
    private static NeoBigStorageView neoecoae$storageView(NEComputationCluster cluster) {
        if (cluster == null) {
            return null;
        }
        NEComputationNetworkCluster network = cluster.getNetworkCluster();
        return network != null
            ? (NeoBigStorageView) (Object) network
            : (NeoBigStorageView) (Object) cluster;
    }
}