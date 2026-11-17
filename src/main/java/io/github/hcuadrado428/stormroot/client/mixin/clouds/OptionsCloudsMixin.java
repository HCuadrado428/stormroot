package io.github.hcuadrado428.stormroot.client.mixin.clouds;

import io.github.hcuadrado428.stormroot.client.sky.SkyRenderer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Oculta las nubes vanilla mientras Stormroot dibuja el cielo ({@link SkyRenderer}). Vanilla solo dibuja las nubes si
 * {@code Options#getCloudsType()} no es {@code OFF} (en las 3 versiones; NeoForge 1.21.11 también pasa por ahí antes de
 * su {@code CustomCloudsRenderer}). El ajuste del jugador no se toca: Stormroot lee {@code cloudStatus().get()} para
 * saber si las quiere rápidas, detalladas o ninguna.
 *
 * <p>Está en {@code stormroot-sky.mixins.json}, que cargan los 3 loaders (Fabric: {@code fabric.mod.json};
 * NeoForge: {@code neoforge.mods.toml}; Forge: manifiesto del jar, ver {@code build.gradle.kts}).
 */
@Mixin(Options.class)
public abstract class OptionsCloudsMixin {
    @Inject(method = "getCloudsType", at = @At("HEAD"), cancellable = true)
    private void stormroot$hideVanillaClouds(CallbackInfoReturnable<CloudStatus> cir) {
        if (SkyRenderer.replacesVanillaClouds()) {
            cir.setReturnValue(CloudStatus.OFF);
        }
    }
}
