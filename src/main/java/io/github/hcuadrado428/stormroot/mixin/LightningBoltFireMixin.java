package io.github.hcuadrado428.stormroot.mixin;

import io.github.hcuadrado428.stormroot.weather.DryStorms;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Los rayos de tormenta seca ({@link DryStorms}) no prenden fuego (así no queman casas); el resto de rayos, igual que en
 * vanilla. {@code LightningBolt#spawnFire(int)} es privado y existe con esa firma en las 3 versiones (comprobado).
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltFireMixin {
    @Inject(method = "spawnFire", at = @At("HEAD"), cancellable = true)
    private void stormroot$noFireFromDryStorms(int extraIgnitions, CallbackInfo ci) {
        if (DryStorms.isDryStormBolt((LightningBolt) (Object) this)) {
            ci.cancel();
        }
    }
}
