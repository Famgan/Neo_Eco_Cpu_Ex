package dev.modmind.neo_eco_cpu_ex.mixin;

import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import dev.modmind.neo_eco_cpu_ex.core.InfiniteDisplay;
import dev.modmind.neo_eco_cpu_ex.core.NeoCpuStorageView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Converts CPU storage only when the server-authored CPU row is sampled for the UI.
 *
 * <p>The public {@code ECOCraftingCPU#getAvailableStorage()} contract remains saturated because
 * crafting admission uses it. This redirect writes the display-only sentinel into the menu DTO;
 * the formatter mixin then turns only that sentinel into {@code 无限} / {@code Infinity}.</p>
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.gui.computation.ComputationCpuEntry", remap = false)
public abstract class ComputationCpuEntryMixin {
    @Redirect(
        method = "sample",
        at = @At(
            value = "INVOKE",
            target = "Lcn/dancingsnow/neoecoae/crafting/execution/ECOCraftingCPU;getAvailableStorage()J"
        )
    )
    private static long neoecoae$displayAvailableStorage(ECOCraftingCPU cpu) {
        return ((NeoCpuStorageView) (Object) cpu).neoecoae$availableStorageDisplay();
    }

    @Shadow
    public abstract long storage();

    @Inject(method = "fullStorageText", at = @At("HEAD"), cancellable = true)
    private void neoecoae$fullStorageText(CallbackInfoReturnable<String> cir) {
        if (InfiniteDisplay.isSaturated(storage())) {
            cir.setReturnValue(InfiniteDisplay.text());
        }
    }
}