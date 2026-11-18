package io.github.hcuadrado428.stormroot.selftest;

import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.weather.AuroraCalm;
import io.github.hcuadrado428.stormroot.weather.ClimateZone;
import io.github.hcuadrado428.stormroot.weather.Frost;
import io.github.hcuadrado428.stormroot.weather.FrostRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Self-test de la helada y de la aurora que calma la noche. Usa la cima de la zona de pruebas: una charca de un bloque
 * en (+10, +5), una de dos bloques en (+10, +7) y trigo en (+1, +6), lejos de la luz de las raíces de rayo. La luz la pone la cámara de la red de micelio.
 */
final class FrostSelfTest {
    private final int bx;
    private final int bz;
    private final int top;

    FrostSelfTest(int bx, int bz, int top) {
        this.bx = bx;
        this.bz = bz;
        this.top = top;
    }

    private BlockPos shallow() {
        return new BlockPos(bx + 10, top, bz + 5);
    }

    private BlockPos deep() {
        return new BlockPos(bx + 10, top, bz + 7);
    }

    private BlockPos crop() {
        return new BlockPos(bx + 1, top + 1, bz + 6);
    }

    /** Fase 0: charcas y trigo (la luz del cielo necesita unos ticks). */
    void build(ServerLevel level) {
        level.setBlock(shallow(), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(deep(), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(deep().below(), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(crop().below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(crop(), Blocks.WHEAT.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    void run(ServerLevel level) {
        SelfTest.check("frost nights follow the rules", checkRules(), "reglas de la helada mal");
        try {
            checkWater(level);
            checkCrops(level);
        } finally {
            Frost.forceForTest(null);
        }
        boolean calm = AuroraCalm.blocks(true, 1.0, 0.5) && !AuroraCalm.blocks(true, 1.0, 0.9)
                && !AuroraCalm.blocks(false, 1.0, 0.0) && !AuroraCalm.blocks(true, 0.1, 0.0);
        SelfTest.check("aurora keeps hostiles away", calm, "regla de la aurora mal");
    }

    private static boolean checkRules() {
        boolean hours = FrostRules.frostHours(15000) && FrostRules.frostHours(2000) && !FrostRules.frostHours(6000)
                && !FrostRules.frostHours(10000);
        boolean sameNight = FrostRules.nightIndex(23000) == FrostRules.nightIndex(26000)
                && FrostRules.nightIndex(26000) != FrostRules.nightIndex(38000);
        int cold = 0;
        int temperate = 0;
        int other = 0;
        for (long night = 0; night < 2000; night++) {
            cold += FrostRules.frostNight(1234, night, ClimateZone.COLD) ? 1 : 0;
            temperate += FrostRules.frostNight(1234, night, ClimateZone.TEMPERATE) ? 1 : 0;
            other += FrostRules.frostNight(1234, night, ClimateZone.HUMID) || FrostRules.frostNight(1234, night, ClimateZone.DRY) ? 1 : 0;
        }
        boolean odds = Math.abs(cold / 2000.0 - FrostRules.COLD_CHANCE) < 0.05
                && Math.abs(temperate / 2000.0 - FrostRules.TEMPERATE_CHANCE) < 0.05 && other == 0;
        boolean cloudy = !FrostRules.freezing(1234, 15000, ClimateZone.COLD, 0.5);
        return hours && sameNight && odds && cloudy;
    }

    /** El agua de un bloque de hondo se congela y vuelve a ser agua al dejar de helar; la honda no. */
    private void checkWater(ServerLevel level) {
        Frost.forceForTest(true);
        boolean froze = Frost.freeze(level, shallow()) && level.getBlockState(shallow()).is(ModBlocks.FROST_ICE);
        boolean deepStays = !Frost.freeze(level, deep());
        Frost.forceForTest(false);
        BlockState ice = level.getBlockState(shallow());
        ice.randomTick(level, shallow(), RandomSource.create(3));
        boolean melted = level.getBlockState(shallow()).is(Blocks.WATER);
        SelfTest.check("frost freezes shallow water and melts it back", froze && deepStays && melted,
                "se heló=" + froze + ", la honda no=" + deepStays + ", se derritió=" + melted);
    }

    /** Con helada el trigo no crece (salvo con luz cerca); sin ella, sí. */
    private void checkCrops(ServerLevel level) {
        RandomSource random = RandomSource.create(5);
        int startAge = level.getBlockState(crop()).getValue(CropBlock.AGE);
        // Otras pruebas (monzón) dan ticks de más a los cultivos de la zona: se empieza con trigo recién plantado.
        level.setBlock(crop(), Blocks.WHEAT.defaultBlockState(), Block.UPDATE_CLIENTS);
        Frost.forceForTest(true);
        boolean stops = Frost.stopsGrowth(level, crop());
        for (int i = 0; i < 300; i++) {
            BlockState state = level.getBlockState(crop());
            state.randomTick(level, crop(), random);
        }
        int frozenAge = level.getBlockState(crop()).getValue(CropBlock.AGE);
        boolean lightHelps = !Frost.stopsGrowth(level, new BlockPos(bx + 8, -56, bz + 8));
        Frost.forceForTest(false);
        for (int i = 0; i < 300; i++) {
            BlockState state = level.getBlockState(crop());
            state.randomTick(level, crop(), random);
        }
        int thawedAge = level.getBlockState(crop()).getValue(CropBlock.AGE);
        SelfTest.check("frost stops crops unless there is light", frozenAge == 0 && thawedAge > frozenAge && lightHelps,
                "edad al empezar=" + startAge + ", para=" + stops + ", luz=" + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, crop()) + ", bloque=" + level.getBlockState(crop()).getBlock().getClass().getName()
                        + ", edad con helada=" + frozenAge + ", sin helada=" + thawedAge + ", con luz crece=" + lightHelps);
    }
}
