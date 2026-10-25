package dev.modmind.neo_eco_cpu_ex;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import dev.modmind.neo_eco_cpu_ex.core.InfiniteDisplay;
import dev.modmind.neo_eco_cpu_ex.core.NeoExMixinMarker;
import dev.modmind.neo_eco_cpu_ex.math.LongArrayBigInt;

import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mixin-only compatibility addon; all behavior is installed through myecocpu.mixins.json. */
@Mod(NeoCpuEx.MOD_ID)
public final class NeoCpuEx {
    public static final String MOD_ID = "neo_eco_cpu_ex";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NeoCpuEx() {
        boolean cluster = NeoExMixinMarker.class.isAssignableFrom(NEComputationCluster.class);
        boolean network = NeoExMixinMarker.class.isAssignableFrom(NEComputationNetworkCluster.class);
        boolean cpu = NeoExMixinMarker.class.isAssignableFrom(ECOCraftingCPU.class);
        boolean system = NeoExMixinMarker.class.isAssignableFrom(ECOComputationSystemBlockEntity.class);
        boolean formatter = NeoExMixinMarker.class.isAssignableFrom(NEByteFormatter.class);
        LongArrayBigInt aboveLongMax = LongArrayBigInt.LONG_MAX.add(LongArrayBigInt.ONE);
        boolean exactMath = aboveLongMax.compareTo(LongArrayBigInt.LONG_MAX) > 0
            && InfiniteDisplay.toDisplayLong(LongArrayBigInt.LONG_MAX) == Long.MAX_VALUE
            && !InfiniteDisplay.isSaturated(Long.MAX_VALUE)
            && InfiniteDisplay.isSaturated(InfiniteDisplay.toDisplayLong(aboveLongMax));

        LOGGER.info(
            "[neo_eco_cpu_ex] Mixin status: cluster={}, network={}, cpu={}, system={}, formatter={}, exactMath={}",
            cluster,
            network,
            cpu,
            system,
            formatter,
            exactMath
        );
        if (!(cluster && network && cpu && system && formatter && exactMath)) {
            LOGGER.error("[neo_eco_cpu_ex] Exact computation-byte pool is INACTIVE; check myecocpu.mixins.json.");
        }
    }
}