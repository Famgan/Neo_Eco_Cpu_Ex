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
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Replaces NEComputationCluster's long byte pool with exact arithmetic.
 *
 * <p>The original long field is retained only as a saturated compatibility mirror. Every stock read
 * and write site is covered: capacity collection, active-job reservation, admission comparison,
 * remaining-storage recalculation, formation reset, and both pooled getters. The exact values are
 * recomputed from the drives and active plan identities, so this class owns no persistent state that
 * could become stale or leak when a job is cancelled.</p>
 */
@Mixin(value = NEComputationCluster.class, remap = false)
public abstract class NEComputationClusterMixin implements NeoBigStorageView, NeoExMixinMarker {
    @Shadow @Final private List<ECOComputationDriveBlockEntity> upperDrives;
    @Shadow @Final private List<ECOComputationDriveBlockEntity> lowerDrives;
    @Shadow @Final private Map<ICraftingPlan, ECOCraftingCPU> activeCpus;
    @Shadow @Final private Map<ICraftingPlan, Boolean> bigOrderPlans;
    @Shadow private NEComputationNetworkCluster networkCluster;
    @Shadow private long availableStorage;

    @Shadow private void killCpu(ICraftingPlan plan, boolean update, boolean recalculate) {
        throw new AssertionError("shadow");
    }

    @Unique private LongArrayBigInt neoecoae$availableStorage = LongArrayBigInt.ZERO;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neoecoae$initializeExactStorage(CallbackInfo ci) {
        this.neoecoae$availableStorage = LongArrayBigInt.ZERO;
        this.availableStorage = 0L;
    }

    @Override
    @Unique
    public LongArrayBigInt neoecoae$totalStorageExact() {
        return neoecoae$collectOwnTotal();
    }

    @Override
    @Unique
    public LongArrayBigInt neoecoae$usedStorageExact() {
        return neoecoae$collectOwnUsed();
    }

    @Override
    @Unique
    public LongArrayBigInt neoecoae$availableStorageExact() {
        NeoBigStorageView network = neoecoae$networkView();
        if (network != null) {
            return network.neoecoae$availableStorageExact();
        }
        // Derive the value instead of trusting a cache: finishing or cancelling a job removes it from
        // activeCpus, so its reservation disappears from this calculation immediately.
        return NeoBytes.nonNegativeDifference(neoecoae$collectOwnTotal(), neoecoae$collectOwnUsed());
    }

    @Override
    @Unique
    public boolean neoecoae$reservationsFitExact() {
        NeoBigStorageView network = neoecoae$networkView();
        return network != null
            ? network.neoecoae$reservationsFitExact()
            : neoecoae$collectOwnUsed().compareTo(neoecoae$collectOwnTotal()) <= 0;
    }

    @Override
    @Unique
    public boolean neoecoae$displayAsInfinite() {
        NeoBigStorageView network = neoecoae$networkView();
        return network != null && network.neoecoae$displayAsInfinite();
    }

    @Inject(method = "getTotalStorage", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getTotalStorage(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(neoecoae$collectOwnTotal().toLongSaturated());
    }

    @Inject(method = "getOwnUsedStorage", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getOwnUsedStorage(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(neoecoae$collectOwnUsed().toLongSaturated());
    }

    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getAvailableStorage(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(neoecoae$availableStorageExact().toLongSaturated());
    }

    @Inject(method = "getPooledTotalStorage", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getPooledTotalStorage(CallbackInfoReturnable<Long> cir) {
        NeoBigStorageView network = neoecoae$networkView();
        LongArrayBigInt total = network != null ? network.neoecoae$totalStorageExact() : neoecoae$collectOwnTotal();
        cir.setReturnValue(total.toLongSaturated());
    }

    @Inject(method = "getActiveJobBytes", at = @At("HEAD"), cancellable = true)
    private void neoecoae$getActiveJobBytes(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(neoecoae$collectOwnUsed().toLongSaturated());
    }

    /**
     * Compare exactly before stock code can truncate either side. The stock long comparison below
     * remains a second-line guard, with availableStorage temporarily saturated for it.
     */
    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true)
    private void neoecoae$guardSubmit(
        IGrid grid,
        ICraftingPlan job,
        IActionSource src,
        ICraftingRequester requestingMachine,
        CallbackInfoReturnable<ICraftingSubmitResult> cir
    ) {
        NEComputationCluster self = (NEComputationCluster) (Object) this;
        if (!self.isActive()) {
            return;
        }
        LongArrayBigInt available = neoecoae$availableStorageExact();
        LongArrayBigInt required = NeoBytes.requiredStorage(job);
        if (required.compareTo(available) > 0) {
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
            return;
        }
        this.availableStorage = available.toLongSaturated();
    }

    /**
     * Full exact replacement for the original long subtraction/saturation path. The same
     * all-or-nothing cancellation policy is preserved.
     */
    @Inject(method = "recalculateRemainingStorage", at = @At("HEAD"), cancellable = true)
    private void neoecoae$recalculateRemainingStorage(CallbackInfo ci) {
        ci.cancel();
        neoecoae$recalculateExact();
    }

    @Inject(method = "updateFormed", at = @At("TAIL"))
    private void neoecoae$resetWhenUnformed(boolean formed, CallbackInfo ci) {
        if (!formed) {
            this.neoecoae$availableStorage = LongArrayBigInt.ZERO;
            this.availableStorage = 0L;
        }
    }

    @Unique
    private void neoecoae$recalculateExact() {
        LongArrayBigInt total = neoecoae$collectOwnTotal();
        LongArrayBigInt used = neoecoae$collectOwnUsed();
        this.neoecoae$availableStorage = NeoBytes.nonNegativeDifference(total, used);
        this.availableStorage = this.neoecoae$availableStorage.toLongSaturated();

        if (neoecoae$reservationsFitExact() || this.activeCpus.isEmpty()) {
            return;
        }

        List<ICraftingPlan> plansToKill = new ArrayList<>(this.activeCpus.keySet());
        for (ICraftingPlan plan : plansToKill) {
            this.killCpu(plan, false, false);
        }

        total = neoecoae$collectOwnTotal();
        used = neoecoae$collectOwnUsed();
        this.neoecoae$availableStorage = NeoBytes.nonNegativeDifference(total, used);
        this.availableStorage = this.neoecoae$availableStorage.toLongSaturated();
    }

    @Unique
    private LongArrayBigInt neoecoae$collectOwnTotal() {
        LongArrayBigInt total = LongArrayBigInt.ZERO;
        total = neoecoae$addDriveStorage(total, this.upperDrives);
        return neoecoae$addDriveStorage(total, this.lowerDrives);
    }

    @Unique
    private static LongArrayBigInt neoecoae$addDriveStorage(
        LongArrayBigInt initial,
        List<ECOComputationDriveBlockEntity> drives
    ) {
        LongArrayBigInt total = initial;
        for (ECOComputationDriveBlockEntity drive : drives) {
            ItemStack stack = drive.getCellStack();
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ECOComputationCellItem cell)) {
                continue;
            }
            total = total.add(LongArrayBigInt.of(cell.getTier().getCPUTotalBytes()));
        }
        return total;
    }

    @Unique
    private LongArrayBigInt neoecoae$collectOwnUsed() {
        LongArrayBigInt used = LongArrayBigInt.ZERO;
        for (ICraftingPlan plan : new ArrayList<>(this.activeCpus.keySet())) {
            if (this.bigOrderPlans.containsKey(plan)) {
                continue;
            }
            used = used.add(NeoBytes.requiredStorage(plan));
        }
        return used;
    }

    @Unique
    private NeoBigStorageView neoecoae$networkView() {
        return this.networkCluster == null ? null : (NeoBigStorageView) (Object) this.networkCluster;
    }
}