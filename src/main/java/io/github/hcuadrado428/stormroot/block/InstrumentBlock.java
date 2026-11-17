package io.github.hcuadrado428.stormroot.block;

import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

//? if >=1.21.2 {
import net.minecraft.world.level.block.state.properties.EnumProperty;
//?} else {
/*import net.minecraft.world.level.block.state.properties.DirectionProperty;
*///?}

//? if <1.20.5 {
/*import net.minecraft.world.InteractionHand;
*///?}

/**
 * Base de los instrumentos (veleta, barómetro): mira hacia un lado, da señal de redstone (POWER 0–15)
 * y cada {@link #UPDATE_TICKS} ticks recalcula esa señal a partir de los frentes del Overworld.
 * Al hacer clic derecho muestra su lectura en la barra de acción.
 */
public abstract class InstrumentBlock extends Block {
    //? if >=1.21.2 {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    //?} else {
    /*public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    *///?}
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    /** Cada cuánto se actualiza la señal (40 ticks = 2 s). */
    public static final int UPDATE_TICKS = 40;

    protected InstrumentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWER, 0));
    }

    /** Señal de redstone (0–15) para este bloque ahora mismo. Solo se llama en el Overworld. */
    protected abstract int computePower(BlockState state, ServerLevel level, BlockPos pos, FrontSimulation simulation);

    /** Lectura que se muestra al jugador. Solo se llama en el Overworld. */
    protected abstract Component reading(BlockState state, ServerLevel level, BlockPos pos, FrontSimulation simulation);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
            serverLevel.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int power = 0;
        if (level.dimension() == Level.OVERWORLD) {
            FrontSimulation simulation = FrontTicker.simulation(level.getServer());
            power = Math.max(0, Math.min(15, computePower(state, level, pos, simulation)));
            // Tormenta magnética: lecturas locas.
            if (io.github.hcuadrado428.stormroot.seep.SeepageTicker.intensityAt(simulation,
                    io.github.hcuadrado428.stormroot.weather.FrontType.MAGNETIC_STORM, pos.getX() + 0.5, pos.getZ() + 0.5) > 0.2) {
                power = random.nextInt(16);
            }
        }
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, UPDATE_TICKS);
    }

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        showReading(state, level, pos, player);
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        showReading(state, level, pos, player);
        return InteractionResult.SUCCESS;
    }
    *///?}

    private void showReading(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Component message = serverLevel.dimension() == Level.OVERWORLD
                ? reading(state, serverLevel, pos, FrontTicker.simulation(serverLevel.getServer()))
                : Component.translatable("message.stormroot.instrument.no_weather");
        player.displayClientMessage(message, true);
    }
}
