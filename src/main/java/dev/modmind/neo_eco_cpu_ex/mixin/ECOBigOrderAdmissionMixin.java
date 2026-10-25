package dev.modmind.neo_eco_cpu_ex.mixin;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import dev.modmind.neo_eco_cpu_ex.core.OverflowedOrderWaitPolicy;

import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets an overflowed exact plan enter NeoECOAE's existing deferred-parent path when the only
 * reason it is not executable yet is a proven external material shortage.
 *
 * <p>This deliberately does not make the plan executable. It changes the admission result from
 * INCOMPLETE_PLAN to a persistent WAITING_MATERIALS parent, whose planner retries from a fresh
 * inventory snapshot. The carrier submitted through AE2 is a normal non-simulation plan, so AE2's
 * own {@code plan.simulation()} guard is not bypassed.</p>
 */
@Mixin(value = ECOBigOrderAdmission.class, remap = false)
public abstract class ECOBigOrderAdmissionMixin {
    @Inject(method = "waitsForMaterials", at = @At("HEAD"), cancellable = true)
    private static void neoecoae$waitForOverflowedShortage(
            ECOPlanningResult result,
            boolean forced,
            CallbackInfoReturnable<Boolean> cir) {
        if (!forced || !OverflowedOrderWaitPolicy.canWaitForMissingMaterials(result)) {
            return;
        }
        LoggerFactory.getLogger("neo_eco_cpu_ex").info(
                "[big-order] Deferring overflowed plan instead of INCOMPLETE_PLAN: status={}, components={}",
                result.status(),
                result.components().stream()
                        .filter(component -> component.status()
                                != cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.PLANNED
                                && component.status()
                                != cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.NOT_REQUIRED)
                        .map(component -> component.componentId() + ":" + component.status()
                                + "/" + component.cycleStatus()
                                + "/" + component.externalDemandStatus())
                        .toList());
        cir.setReturnValue(true);
    }
}