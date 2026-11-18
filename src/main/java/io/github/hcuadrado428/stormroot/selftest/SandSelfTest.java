package io.github.hcuadrado428.stormroot.selftest;

import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.sky.CloudCover;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.Sandstorms;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import io.github.hcuadrado428.stormroot.weather.WindRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;

/** Self-test de la tormenta de arena: capas que se van, ceguera, muro en el cielo y calima. Cima de la zona, x +15. */
final class SandSelfTest {
    private final BlockPos sand;
    private final BlockPos planks;
    private final int bx;
    private final int bz;
    private final int top;

    SandSelfTest(int bx, int bz, int top) {
        this.sand = new BlockPos(bx + 15, top, bz + 5);
        this.planks = new BlockPos(bx + 15, top, bz + 7);
        this.bx = bx;
        this.bz = bz;
        this.top = top;
    }

    /** Fase 0: un bloque de arena y uno de tablones en la cima (la luz del cielo tarda unos ticks). */
    void build(ServerLevel level) {
        level.setBlock(sand, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(planks, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
        // Un trozo de "desierto" en la cima para el fósil (x +9..+13, z +13..+15).
        for (BlockPos pos : BlockPos.betweenClosed(bx + 9, top, bz + 13, bx + 13, top, bz + 15)) {
            level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    void run(ServerLevel level) {
        try {
            checkLayers(level);
        } finally {
            Sandstorms.forceForTest(null);
        }
        int bones = Sandstorms.uncoverFossil(level, new BlockPos(bx + 11, top, bz + 14), true, RandomSource.create(2));
        boolean spine = level.getBlockState(new BlockPos(bx + 11, top, bz + 14)).is(Blocks.BONE_BLOCK);
        int onStone = Sandstorms.uncoverFossil(level, new BlockPos(bx + 4, top, bz + 2), true, RandomSource.create(2));
        SelfTest.check("sandstorms uncover fossils in the sand", bones >= 5 && spine && onStone == 0,
                "huesos=" + bones + ", columna=" + spine + ", sobre piedra=" + onStone);
        boolean blind = Sandstorms.blinds(0.8, true, false, 0.05) && !Sandstorms.blinds(0.8, true, true, 0.05)
                && !Sandstorms.blinds(0.8, false, false, 0.05) && !Sandstorms.blinds(0.3, true, false, 0.05);
        SelfTest.check("sand blinds only without goggles", blind, "regla de la ceguera mal");

        WeatherFront storm = new WeatherFront(1, FrontType.SANDSTORM, 0, 0, 100, 0, 0, 500, 1000);
        WeatherFront rain = new WeatherFront(2, FrontType.RAIN, 0, 0, 100, 0, 0, 500, 1000);
        CloudCover.Kind wall = CloudCover.frontCloud(1, 0, 0, 0, 0, 0, List.of(storm));
        CloudCover.Kind both = CloudCover.frontCloud(1, 0, 0, 0, 0, 0, List.of(storm, rain));
        double near = CloudCover.calima(200, 0, List.of(storm));
        double far = CloudCover.calima(300, 0, List.of(storm));
        boolean sky = wall == CloudCover.Kind.DUST && both == CloudCover.Kind.RAIN && near > 0 && far == 0
                && WindRules.gust(FrontType.SANDSTORM) == 1.0 && !FrontType.SANDSTORM.precipitates();
        SelfTest.check("sandstorm wall and calima", sky, "muro=" + wall + ", con lluvia=" + both + ", calima a 2 radios=" + near
                + ", a 3=" + far);
    }

    private void checkLayers(ServerLevel level) {
        Sandstorms.forceForTest(true);
        BlockPos above = sand.above();
        int placed = 0;
        for (int i = 0; i < 4; i++) {
            placed += Sandstorms.addLayer(level, above) ? 1 : 0;
        }
        int layers = level.getBlockState(above).is(ModBlocks.SAND_LAYER) ? level.getBlockState(above).getValue(SnowLayerBlock.LAYERS) : 0;
        boolean notOnPlanks = !Sandstorms.addLayer(level, planks.above());
        Sandstorms.forceForTest(false);
        RandomSource random = RandomSource.create(9);
        for (int i = 0; i < 3; i++) {
            level.getBlockState(above).randomTick(level, above, random);
        }
        boolean gone = level.getBlockState(above).isAir();
        SelfTest.check("sandstorm leaves sand layers that blow away", placed == 3 && layers == 3 && notOnPlanks && gone,
                "capas puestas=" + placed + ", capas=" + layers + ", no en tablones=" + notOnPlanks + ", se fueron=" + gone);
    }
}
