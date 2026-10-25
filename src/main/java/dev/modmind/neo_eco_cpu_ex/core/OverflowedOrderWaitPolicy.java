package dev.modmind.neo_eco_cpu_ex.core;

import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;

/**
 * Decides when an exact plan that overflowed AE2's long projection is nevertheless safe to
 * publish as a deferred parent order.
 *
 * <p>NeoECOAE normally rejects every UNRESOLVED cycle component. An overflowed plan is special:
 * its status is already {@link PlanningStatus#PLANNED_BUT_AMOUNT_UNREPRESENTABLE}, so the
 * planner cannot promote a proven external-material shortage to MISSING_ITEMS. The surrounding
 * big-order runtime, however, can keep a long-lived parent and replan from fresh stock later.
 * Only that narrow case is admitted here. Unsupported recipes, budget exhaustion, cancelled
 * solves, and any component that has not proven a concrete missing external input stay rejected.</p>
 */
public final class OverflowedOrderWaitPolicy {
    private OverflowedOrderWaitPolicy() {
    }

    public static boolean canWaitForMissingMaterials(ECOPlanningResult result) {
        if (result == null || result.plan() == null
                || result.status() != PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) {
            return false;
        }

        boolean foundBlockedShortage = false;
        for (ComponentPlanningResult component : result.components()) {
            ComponentPlanningResult.Status status = component.status();
            if (status == ComponentPlanningResult.Status.PLANNED
                    || status == ComponentPlanningResult.Status.NOT_REQUIRED) {
                continue;
            }
            if (status != ComponentPlanningResult.Status.UNRESOLVED
                    || component.type() != ComponentPlanningResult.Type.CYCLIC
                    || component.cycleDisposition() != CycleExecutionDisposition.BLOCKED
                    || component.cycleStatus() != CyclePlanningStatus.INSUFFICIENT_EXTERNAL_INPUT
                    || component.externalDemandStatus() != CycleExternalDemandStatus.MISSING
                    || !hasConcreteMissingMaterial(component)) {
                return false;
            }
            foundBlockedShortage = true;
        }
        return foundBlockedShortage;
    }

    private static boolean hasConcreteMissingMaterial(ComponentPlanningResult component) {
        if (!component.externalMissingItems().isEmpty()) {
            return true;
        }
        if (component.cycleResult() == null) {
            return false;
        }
        // A missing startup seed is a material deficit even when the external-DAG report has no
        // leaf because the tool itself cannot be produced from the current network.
        return !component.cycleResult().seedShortfall().isEmpty()
                || !component.cycleResult().positiveExternalDemand().isEmpty();
    }
}