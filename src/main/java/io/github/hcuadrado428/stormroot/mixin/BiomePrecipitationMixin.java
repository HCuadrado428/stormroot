package io.github.hcuadrado428.stormroot.mixin;

import io.github.hcuadrado428.stormroot.sky.SkyWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Solo llueve (o nieva) bajo los nubarrones de Stormroot ({@link SkyWeather}). Todo lo que hace la lluvia pasa por
 * {@code Biome#getPrecipitationAt} en las 3 versiones (comprobado en las fuentes): {@code Level#isRainingAt} (fuegos,
 * calderos, rayos vanilla), la nieve y el hielo del servidor, y las gotas y partículas del cliente. Fuera de un
 * nubarrón se devuelve {@code NONE}; dentro, lo que diga vanilla (así los desiertos siguen sin lluvia: tormenta seca).
 */
@Mixin(Biome.class)
public abstract class BiomePrecipitationMixin {
    //? if >=1.21.11 {
    @Inject(method = "getPrecipitationAt", at = @At("RETURN"), cancellable = true)
    private void stormroot$onlyUnderClouds(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        stormroot$filter(pos, cir);
    }
    //?} else {
    /*@Inject(method = "getPrecipitationAt", at = @At("RETURN"), cancellable = true)
    private void stormroot$onlyUnderClouds(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        stormroot$filter(pos, cir);
    }
    *///?}

    private static void stormroot$filter(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (cir.getReturnValue() != Biome.Precipitation.NONE && SkyWeather.noRainCloudAt(pos.getX() + 0.5, pos.getZ() + 0.5)) {
            cir.setReturnValue(Biome.Precipitation.NONE);
        }
    }
}
