package io.github.hcuadrado428.stormroot.client.mixin.sky;

import io.github.hcuadrado428.stormroot.client.MagneticCompass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tormenta magnética: las brújulas (normales, de magnetita y de recuperación) giran sin parar, como en el Nether.
 * Vanilla las hace girar cuando su objetivo no vale ({@code isValidCompassTargetPos}): aquí decimos que no vale.
 * Hasta 1.21.1 está en {@code CompassItemPropertyFunction}; en 1.21.11, en {@code CompassAngleState} (estático).
 */
//? if >=1.21.11 {
@Mixin(net.minecraft.client.renderer.item.properties.numeric.CompassAngleState.class)
public abstract class MagneticCompassMixin {
    @Inject(method = "isValidCompassTargetPos", at = @At("RETURN"), cancellable = true)
    private static void stormroot$spin(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && MagneticCompass.spinning()) {
            cir.setReturnValue(false);
        }
    }
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.item.CompassItemPropertyFunction.class)
public abstract class MagneticCompassMixin {
    @Inject(method = "isValidCompassTargetPos", at = @At("RETURN"), cancellable = true)
    private void stormroot$spin(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && MagneticCompass.spinning()) {
            cir.setReturnValue(false);
        }
    }
}
*///?}
