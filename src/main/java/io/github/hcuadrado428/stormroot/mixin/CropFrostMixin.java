package io.github.hcuadrado428.stormroot.mixin;

import io.github.hcuadrado428.stormroot.weather.Frost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Helada: los cultivos no crecen mientras hiela, salvo con luz de antorcha cerca. {@code CropBlock#randomTick} tiene la
 * misma firma en las 3 versiones (público en 1.20.1, protegido después; comprobado).
 */
@Mixin(CropBlock.class)
public abstract class CropFrostMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void stormroot$frost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (Frost.stopsGrowth(level, pos)) {
            ci.cancel();
        }
    }
}
