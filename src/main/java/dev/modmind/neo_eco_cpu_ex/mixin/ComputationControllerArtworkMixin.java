package dev.modmind.neo_eco_cpu_ex.mixin;

import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerUI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.modmind.neo_eco_cpu_ex.core.InfiniteDisplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Keeps the display sentinel intact through the controller snapshot and gives the stock artwork
 * overflow-aware text without disturbing the exact {@code Long.MAX_VALUE} (8 EB) case.
 *
 * <p>The stock snapshot clamps each byte supplier with {@code Math.max(0, value)}. The reserved
 * overflow marker is intentionally negative, so it would otherwise become zero before reaching the
 * renderer. Only these three display fields are restored; gameplay state never sees the marker.</p>
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.gui.computation.ComputationControllerArtwork", remap = false)
public abstract class ComputationControllerArtworkMixin {
    private static final String BYTE_CANDIDATES =
        "Lcn/dancingsnow/neoecoae/gui/computation/ComputationControllerArtwork;"
            + "byteCandidates(J)[Ljava/lang/String;";

    @Shadow @Final private ComputationControllerUI.Config config;
    @Shadow private long usedBytes;
    @Shadow private long totalBytes;
    @Shadow private long availableBytes;

    @Inject(method = "snapshot", at = @At("RETURN"))
    private void neoecoae$preserveOverflowMarker(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag == null) {
            return;
        }
        tag.putLong("usedBytes", this.config.usedBytes().getAsLong());
        tag.putLong("totalBytes", this.config.totalBytes().getAsLong());
        tag.putLong("availableBytes", this.config.availableBytes().getAsLong());
    }

    @Inject(method = "storageTooltip", at = @At("HEAD"), cancellable = true)
    private void neoecoae$storageTooltip(CallbackInfoReturnable<List<Component>> cir) {
        if (!neoecoae$hasOverflow()) {
            return;
        }

        String infinity = InfiniteDisplay.text();
        String meaningless = InfiniteDisplay.meaninglessText();
        String total = InfiniteDisplay.isSaturated(this.totalBytes)
            ? infinity
            : NEByteFormatter.format(this.totalBytes);
        String free = InfiniteDisplay.isSaturated(this.totalBytes)
            || InfiniteDisplay.isSaturated(this.availableBytes)
                ? infinity
                : HostText.expandedNumber(this.availableBytes) + " B";

        cir.setReturnValue(List.of(
            Component.translatable("gui.neoecoae.host.computation.cpu_storage").append(": ")
                .append(Component.literal(meaningless + " / " + total).withColor(HostText.VALUE)),
            Component.translatable("gui.neoecoae.host.computation.free_memory").append(": ")
                .append(Component.literal(free).withColor(HostText.VALUE)),
            Component.translatable("gui.neoecoae.computation.ui.stat.usage").append(": ")
                .append(Component.literal(meaningless).withColor(HostText.VALUE))
        ));
    }

    /**
     * Row 0 of the CPU panel is "used". Once the exact pool is wider than a signed long, the
     * displayed long is no longer a meaningful measurement, so show N/A instead of a truncated
     * number.
     */
    @WrapOperation(
        method = "drawStatPanels",
        at = @At(value = "INVOKE", target = BYTE_CANDIDATES, ordinal = 0)
    )
    private String[] neoecoae$usedByteCandidates(long bytes, Operation<String[]> original) {
        return neoecoae$hasOverflow()
            ? neoecoae$meaninglessCandidates()
            : original.call(bytes);
    }

    /** Row 1 is total capacity: only the dedicated total-capacity sentinel means infinity. */
    @WrapOperation(
        method = "drawStatPanels",
        at = @At(value = "INVOKE", target = BYTE_CANDIDATES, ordinal = 1)
    )
    private String[] neoecoae$capacityByteCandidates(long bytes, Operation<String[]> original) {
        return InfiniteDisplay.isSaturated(this.totalBytes)
            ? neoecoae$infinityCandidates()
            : original.call(bytes);
    }

    /** Row 2 is free space, which is infinite whenever either side of total - used overflowed. */
    @WrapOperation(
        method = "drawStatPanels",
        at = @At(value = "INVOKE", target = BYTE_CANDIDATES, ordinal = 2)
    )
    private String[] neoecoae$freeByteCandidates(long bytes, Operation<String[]> original) {
        return InfiniteDisplay.isSaturated(this.totalBytes)
            || InfiniteDisplay.isSaturated(this.availableBytes)
                ? neoecoae$infinityCandidates()
                : original.call(bytes);
    }

    /** Safety net for any other future caller of the stock byte formatter. */
    @Inject(method = "byteCandidates", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$infiniteByteCandidates(long bytes, CallbackInfoReturnable<String[]> cir) {
        if (InfiniteDisplay.isSaturated(bytes)) {
            cir.setReturnValue(neoecoae$infinityCandidates());
        }
    }

    @Unique
    private boolean neoecoae$hasOverflow() {
        return InfiniteDisplay.isSaturated(this.usedBytes)
            || InfiniteDisplay.isSaturated(this.totalBytes)
            || InfiniteDisplay.isSaturated(this.availableBytes);
    }

    @Unique
    private static String[] neoecoae$infinityCandidates() {
        String value = InfiniteDisplay.text();
        return new String[] {value, value, value};
    }

    @Unique
    private static String[] neoecoae$meaninglessCandidates() {
        String value = InfiniteDisplay.meaninglessText();
        return new String[] {value, value, value};
    }
}