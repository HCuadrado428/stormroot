package io.github.hcuadrado428.stormroot.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Carámbano de las cuevas heladas (como los de 26.4, que nuestras versiones no tienen). Cuelga del techo (o de otro
 * carámbano), hasta 3 bloques. Si hay luz de antorchas cerca ({@link #FALL_LIGHT} o más), se suelta y cae haciendo
 * daño. Lo pone y lo quita la filtración (cuevas heladas, {@code seep/SeepageTicker}).
 */
public class IcicleBlock extends Block {
    /** Luz de bloque (antorchas, faroles...) a partir de la cual se suelta. */
    public static final int FALL_LIGHT = 7;
    public static final int MAX_LENGTH = 3;
    /** {@code true} = la punta (el de más abajo). */
    public static final BooleanProperty TIP = BooleanProperty.create("tip");

    private static final VoxelShape BODY = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape POINT = Block.box(6, 4, 6, 10, 16, 10);

    public IcicleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIP);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TIP) ? POINT : BODY;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState up = level.getBlockState(above);
        return up.is(this) || up.isFaceSturdy(level, above, Direction.DOWN);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, 40);
        }
    }

    /** Cada 2 s mira la luz: con luz cerca, se suelta (con todo lo que cuelga de él). */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canSurvive(state, level, pos) || level.getBrightness(LightLayer.BLOCK, pos) >= FALL_LIGHT) {
            drop(level, pos);
            return;
        }
        level.scheduleTick(pos, this, 40);
    }

    /** Suelta el carámbano de {@code pos} y los que cuelgan debajo. */
    public static void drop(ServerLevel level, BlockPos pos) {
        BlockPos at = pos;
        while (level.getBlockState(at).getBlock() instanceof IcicleBlock) {
            FallingBlockEntity falling = FallingBlockEntity.fall(level, at, level.getBlockState(at));
            falling.setHurtsEntities(2.0F, 20);
            falling.dropItem = false;
            at = at.below();
        }
    }

    /** Longitud del carámbano que acaba en {@code tip} (contando hacia arriba). */
    public static int lengthAt(BlockGetter level, BlockPos tip) {
        int n = 0;
        BlockPos at = tip;
        while (level.getBlockState(at).getBlock() instanceof IcicleBlock && n <= MAX_LENGTH) {
            n++;
            at = at.above();
        }
        return n;
    }
}
