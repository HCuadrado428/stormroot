package io.github.hcuadrado428.stormroot.block;

import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Veleta: la flecha apunta hacia donde mira el bloque. Da señal máxima (15) cuando el viento sopla justo
 * hacia ahí, y menos cuanto más se desvía (0 si sopla hacia el otro lado). Con 4 veletas se sabe el rumbo.
 */
public class WeathervaneBlock extends InstrumentBlock {
    private static final VoxelShape SHAPE = box(5, 0, 5, 11, 16, 11);
    private static final String[] COMPASS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    public WeathervaneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected int computePower(BlockState state, ServerLevel level, BlockPos pos, FrontSimulation simulation) {
        Direction facing = state.getValue(FACING);
        double facingAngle = Math.atan2(facing.getStepZ(), facing.getStepX());
        double alignment = Math.cos(simulation.windAngle() - facingAngle);
        return (int) Math.round(15 * Math.max(0, alignment));
    }

    @Override
    protected Component reading(BlockState state, ServerLevel level, BlockPos pos, FrontSimulation simulation) {
        double bearing = bearingDegrees(simulation.windAngle());
        String point = COMPASS[(int) Math.round(bearing / 45.0) % 8];
        return Component.translatable("message.stormroot.weathervane",
                Component.translatable("compass.stormroot." + point), Math.round(bearing));
    }

    /**
     * Convierte el ángulo del viento (radianes, 0 = hacia +X/este, crece hacia +Z/sur) a rumbo de brújula
     * (grados, 0 = norte, 90 = este).
     */
    public static double bearingDegrees(double windAngle) {
        double bearing = (Math.toDegrees(windAngle) + 90) % 360;
        return bearing < 0 ? bearing + 360 : bearing;
    }
}
