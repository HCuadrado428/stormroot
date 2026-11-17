package io.github.hcuadrado428.stormroot.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

//? if >=1.20.5 {
import com.mojang.serialization.MapCodec;
//?}
//? if <1.21.11 {
/*import net.minecraft.world.level.GameRules;
*///?}

/**
 * Fuego del clima (tormenta seca, sequía, ola de calor). Pensado para NO quemar casas:
 * <ul>
 *   <li>solo vive junto a hojas de árbol y solo se extiende a hojas (nunca a madera, lana ni bloques de construcción);</li>
 *   <li>las hojas puestas por un jugador ({@code persistent=true}) no arden;</li>
 *   <li>como mucho {@link #MAX_SPREAD} saltos desde donde empezó;</li>
 *   <li>se apaga solo al llegar a {@link #MAX_AGE} (~30 s) o si llueve encima. Las hojas quemadas no vuelven.</li>
 * </ul>
 * Respeta la regla de juego del fuego (doFireTick; desde 1.21.11, el fuego solo cerca de jugadores).
 */
public class StormFireBlock extends BaseFireBlock {
    public static final int MAX_AGE = 15;
    public static final int MAX_SPREAD = 4;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
    public static final IntegerProperty SPREAD = IntegerProperty.create("spread", 0, MAX_SPREAD);

    //? if >=1.20.5 {
    public static final MapCodec<StormFireBlock> CODEC = simpleCodec(StormFireBlock::new);

    @Override
    public MapCodec<StormFireBlock> codec() {
        return CODEC;
    }
    //?}

    public StormFireBlock(Properties properties) {
        super(properties, 1.0F);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(SPREAD, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, SPREAD);
    }

    /** Hojas que pueden arder: las de árbol natural, no las que puso un jugador. */
    public static boolean isBurnableLeaf(BlockState state) {
        return state.is(BlockTags.LEAVES)
                && !(state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT));
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return isBurnableLeaf(state);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isBurnableLeaf(level.getBlockState(pos.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, delay(level.getRandom()));
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= MAX_AGE || level.isRainingAt(pos) || !canSurvive(state, level, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        int spread = state.getValue(SPREAD);
        if (spread < MAX_SPREAD && fireAllowed(level, pos)) {
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (random.nextInt(3) == 0 && isBurnableLeaf(level.getBlockState(next))) {
                    // La hoja arde: pasa a ser fuego (y al apagarse, aire).
                    level.setBlock(next, defaultBlockState().setValue(SPREAD, spread + 1), Block.UPDATE_ALL);
                }
            }
        }
        level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, delay(random));
    }

    /** Regla de juego del fuego en ese sitio (doFireTick; desde 1.21.11, solo cerca de jugadores). */
    public static boolean fireAllowed(ServerLevel level, BlockPos pos) {
        //? if >=1.21.11 {
        return level.canSpreadFireAround(pos);
        //?} else {
        /*return level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
        *///?}
    }

    private static int delay(RandomSource random) {
        return 30 + random.nextInt(10);
    }

    /**
     * Prende el fuego del clima en una hoja de árbol cerca de {@code pos} (radio 2), si hay alguna.
     *
     * @return {@code true} si prendió
     */
    public static boolean igniteNear(ServerLevel level, BlockPos pos, Block fire) {
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 1, 2))) {
            if (isBurnableLeaf(level.getBlockState(candidate))) {
                level.setBlock(candidate.immutable(), fire.defaultBlockState(), Block.UPDATE_ALL);
                return true;
            }
        }
        return false;
    }
}
