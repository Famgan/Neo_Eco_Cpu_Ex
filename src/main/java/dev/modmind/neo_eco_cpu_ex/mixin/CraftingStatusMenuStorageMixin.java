package dev.modmind.neo_eco_cpu_ex.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingStatusMenu;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import dev.modmind.neo_eco_cpu_ex.core.NeoCpuStorageView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replaces the storage long in AE2's CPU list DTO with the display-only overflow marker. */
@Mixin(value = CraftingStatusMenu.class, remap = false)
public abstract class CraftingStatusMenuStorageMixin {
    @Redirect(
        method = "createCpuList",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingCPU;getAvailableStorage()J"
        )
    )
    private static long neoecoae$displayListStorage(ICraftingCPU cpu) {
        if (cpu instanceof ECOCraftingCPU ecoCpu) {
            return ((NeoCpuStorageView) (Object) ecoCpu).neoecoae$availableStorageDisplay();
        }
        return cpu.getAvailableStorage();
    }
}