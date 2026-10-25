package dev.modmind.neo_eco_cpu_ex.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingCPURecord;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.modmind.neo_eco_cpu_ex.core.NeoCpuStorageView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Converts the storage copied into {@code CraftConfirmMenu.cpuBytesAvail}.
 *
 * <p>The original record still uses its real saturated size for sorting and planning; only the
 * menu field consumed by the confirmation screen receives the display sentinel.</p>
 */
@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuStorageMixin {
    @WrapOperation(
        method = "onCPUSelectionChanged",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/menu/me/crafting/CraftingCPURecord;getSize()J"
        )
    )
    private static long neoecoae$displayConfirmStorage(
        CraftingCPURecord record,
        Operation<Long> original
    ) {
        ICraftingCPU cpu = ((CraftingCPURecordAccessor) (Object) record).neoecoae$getCpu();
        if (cpu instanceof ECOCraftingCPU ecoCpu) {
            return ((NeoCpuStorageView) (Object) ecoCpu).neoecoae$availableStorageDisplay();
        }
        return original.call(record);
    }
}