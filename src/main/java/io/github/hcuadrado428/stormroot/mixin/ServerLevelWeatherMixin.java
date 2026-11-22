package io.github.hcuadrado428.stormroot.mixin;

import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /weather} y dormir mueven los frentes de Stormroot. {@code setWeatherParameters} es lo que usa el comando
 * {@code /weather} (y lo que usa Stormroot, con duraciones cortas); {@code resetWeatherCycle} es lo que hace el juego
 * cuando los jugadores duermen con lluvia. Mismos nombres y firmas en 1.20.1, 1.21.1 y 1.21.11.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {
    @Inject(method = "setWeatherParameters", at = @At("HEAD"), cancellable = true)
    private void stormroot$weatherCommand(int clearTime, int weatherTime, boolean raining, boolean thundering, CallbackInfo ci) {
        if (FrontTicker.onWeatherCommand((ServerLevel) (Object) this, clearTime, weatherTime, raining, thundering)) {
            ci.cancel();
        }
    }

    @Inject(method = "resetWeatherCycle", at = @At("HEAD"))
    private void stormroot$sleptThroughWeather(CallbackInfo ci) {
        FrontTicker.onSleptThroughWeather((ServerLevel) (Object) this);
    }
}
