package dev.modmind.neo_eco_cpu_ex.mixin;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftingCPURecord;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accessor used only to recover the CPU behind an AE2 menu record for display conversion. */
@Mixin(value = CraftingCPURecord.class, remap = false)
public interface CraftingCPURecordAccessor {
    @Invoker("getCpu")
    ICraftingCPU neoecoae$getCpu();
}