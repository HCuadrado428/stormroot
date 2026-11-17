package io.github.hcuadrado428.stormroot.client.mixin;

import io.github.hcuadrado428.stormroot.client.ClientWeather;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

//? if >=1.21.6 {
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Niebla de Stormroot en FABRIC (Forge/NeoForge usan el evento de niebla). Solo está en la sección "client" de
 * {@code stormroot.mixins.json}, que solo declara {@code fabric.mod.json}: en Forge/NeoForge no se carga.
 *
 * <ul>
 *   <li>1.20.1 – 1.21.1: al final de {@code FogRenderer.setupFog} (estático) se acorta la distancia de la niebla
 *       que acaba de fijar vanilla en {@code RenderSystem}.</li>
 *   <li>1.21.6+: {@code FogRenderer#setupFog} calcula un {@code FogData} y lo sube a la GPU; se cambia
 *       {@code environmentalStart/End} justo antes de subirlo (mismo punto que el evento de NeoForge).</li>
 * </ul>
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    //? if >=1.21.6 {
    @Inject(method = "setupFog", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
    private void stormroot$applyFog(CallbackInfoReturnable<Vector4f> cir, @Local FogData fogData) {
        float far = ClientWeather.fogFarPlane(fogData.environmentalEnd);
        if (far < fogData.environmentalEnd) {
            fogData.environmentalStart = Math.min(fogData.environmentalStart, far * 0.1f);
            fogData.environmentalEnd = far;
            ClientWeather.markFogApplied();
        }
    }
    //?} else {
    /*@Inject(method = "setupFog", at = @At("TAIL"))
    private static void stormroot$applyFog(CallbackInfo ci) {
        float vanillaFar = RenderSystem.getShaderFogEnd();
        float far = ClientWeather.fogFarPlane(vanillaFar);
        if (far < vanillaFar) {
            RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), far * 0.1f));
            RenderSystem.setShaderFogEnd(far);
            ClientWeather.markFogApplied();
        }
    }
    *///?}
}
