package io.github.hcuadrado428.stormroot.mixin;

import io.github.hcuadrado428.stormroot.weather.Wind;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Viento sobre los proyectiles: {@code Projectile#tick} existe en las 3 versiones y todas las flechas y cosas lanzadas
 * lo llaman (con {@code super.tick()}) en cada tick de vuelo (comprobado). Corre en el servidor y en el cliente.
 */
@Mixin(Projectile.class)
public abstract class ProjectileWindMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void stormroot$wind(CallbackInfo ci) {
        Wind.push((Projectile) (Object) this);
    }
}
