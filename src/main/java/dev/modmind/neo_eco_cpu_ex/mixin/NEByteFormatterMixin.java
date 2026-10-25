package dev.modmind.neo_eco_cpu_ex.mixin;

import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import dev.modmind.neo_eco_cpu_ex.core.InfiniteDisplay;
import dev.modmind.neo_eco_cpu_ex.core.NeoExMixinMarker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces only the reserved overflow marker with an explicit infinity label.
 *
 * <p>{@link Long#MAX_VALUE} is a valid exact capacity and deliberately falls through to the stock
 * formatter, which renders the vanilla maximum as {@code 8 EB}. A real value above that limit is
 * converted to {@link InfiniteDisplay#OVERFLOW_MAGIC} at its UI boundary.</p>
 */
@Mixin(value = NEByteFormatter.class, remap = false)
public abstract class NEByteFormatterMixin implements NeoExMixinMarker {
    @Inject(method = "format", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$formatInfinity(long bytes, CallbackInfoReturnable<String> cir) {
        if (InfiniteDisplay.isSaturated(bytes)) {
            cir.setReturnValue(InfiniteDisplay.text());
        }
    }

    @Inject(method = "formatCpuStorage", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$formatCpuStorageInfinity(long storage, CallbackInfoReturnable<String> cir) {
        if (InfiniteDisplay.isSaturated(storage)) {
            cir.setReturnValue(InfiniteDisplay.text());
        }
    }
}