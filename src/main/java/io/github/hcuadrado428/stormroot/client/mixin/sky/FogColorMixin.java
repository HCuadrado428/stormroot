package io.github.hcuadrado428.stormroot.client.mixin.sky;

import io.github.hcuadrado428.stormroot.client.ClientWeather;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Tormenta de arena: la niebla se vuelve marrón ({@link ClientWeather#sandFogTint}). Vale para los 3 loaders.
 * <ul>
 *   <li>1.20.1 – 1.21.1: al final de {@code FogRenderer.setupColor} (estático) se mezclan los campos
 *       {@code fogRed/Green/Blue}, que luego usa {@code levelFogColor}; también se repite {@code clearColor}.</li>
 *   <li>1.21.11: {@code FogRenderer#computeFogColor} (paquete {@code fog}) devuelve un {@code Vector4f}: se cambia.</li>
 * </ul>
 */
//? if >=1.21.6 {
@Mixin(net.minecraft.client.renderer.fog.FogRenderer.class)
public abstract class FogColorMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void stormroot$sandColor(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<org.joml.Vector4f> cir) {
        float t = ClientWeather.sandFogTint();
        if (t > 0) {
            org.joml.Vector4f color = cir.getReturnValue();
            color.set(ClientWeather.tint(color.x, ClientWeather.SAND_RED, t), ClientWeather.tint(color.y, ClientWeather.SAND_GREEN, t),
                    ClientWeather.tint(color.z, ClientWeather.SAND_BLUE, t), color.w);
            ClientWeather.markSandFog();
        }
    }
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public abstract class FogColorMixin {
    @org.spongepowered.asm.mixin.Shadow
    private static float fogRed;
    @org.spongepowered.asm.mixin.Shadow
    private static float fogGreen;
    @org.spongepowered.asm.mixin.Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void stormroot$sandColor(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        float t = ClientWeather.sandFogTint();
        if (t > 0) {
            fogRed = ClientWeather.tint(fogRed, ClientWeather.SAND_RED, t);
            fogGreen = ClientWeather.tint(fogGreen, ClientWeather.SAND_GREEN, t);
            fogBlue = ClientWeather.tint(fogBlue, ClientWeather.SAND_BLUE, t);
            com.mojang.blaze3d.systems.RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
            ClientWeather.markSandFog();
        }
    }
}
*///?}
