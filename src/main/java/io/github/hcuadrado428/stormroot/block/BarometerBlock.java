package io.github.hcuadrado428.stormroot.block;

import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Barómetro: predice el próximo frente que pasará por aquí en los próximos {@link #HORIZON_SECONDS} segundos.
 * La señal sube cuanto más fuerte y más cerca esté (15 = frente fuerte encima). Útil para cerrar
 * contraventanas, encender alarmas o proteger cultivos antes de que llegue la tormenta.
 */
public class BarometerBlock extends InstrumentBlock {
    public static final double HORIZON_SECONDS = 180;
    private static final double STEP_SECONDS = 5;
    private static final double THRESHOLD = 0.15;
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 14, 13);

    public BarometerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected int computePower(BlockState state, ServerLevel level, BlockPos pos, FrontSimulation simulation) {
        FrontSimulation.Forecast forecast = forecast(pos, simulation);
        if (forecast == null) {
            return 0;
        }
        double closeness = 1.0 - forecast.etaSeconds() / HORIZON_SECONDS;
        return Math.max(1, (int) Math.round(15 * forecast.peakIntensity() * closeness));
    }

    @Override
    protected Component reading(BlockState state, ServerLevel level, BlockPos pos, FrontSimulation simulation) {
        FrontSimulation.Forecast forecast = forecast(pos, simulation);
        if (forecast == null) {
            return Component.translatable("message.stormroot.barometer.clear");
        }
        Component front = Component.translatable("front.stormroot." + forecast.type().id());
        int percent = (int) Math.round(forecast.peakIntensity() * 100);
        if (forecast.etaSeconds() <= 0) {
            return Component.translatable("message.stormroot.barometer.here", front, percent);
        }
        return Component.translatable("message.stormroot.barometer.incoming", front,
                Math.round(forecast.etaSeconds()), percent);
    }

    private static FrontSimulation.Forecast forecast(BlockPos pos, FrontSimulation simulation) {
        return simulation.forecast(pos.getX() + 0.5, pos.getZ() + 0.5, HORIZON_SECONDS, STEP_SECONDS, THRESHOLD);
    }
}
