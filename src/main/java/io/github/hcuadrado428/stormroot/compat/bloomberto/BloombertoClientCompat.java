package io.github.hcuadrado428.stormroot.compat.bloomberto;

import com.dougg865.bloomberto.Bloomberto;
import com.dougg865.bloomberto.api.client.ClientEvents;
import com.dougg865.bloomberto.api.client.ConfigScreen;
import com.dougg865.bloomberto.api.client.HudOverlays;
import com.dougg865.bloomberto.api.render.CameraShake;
import com.dougg865.bloomberto.api.render.post.BuiltInEffects;
import com.dougg865.bloomberto.api.render.post.PostEffect;
import com.dougg865.bloomberto.api.render.post.PostEffects;
import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.client.ClientWeather;
import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;

/**
 * Extras de cliente con Bloomberto:
 * <ul>
 *   <li>{@code PostEffects}: desaturación y viñeta propias (stormroot:storm_desaturate / storm_vignette)
 *   según la tormenta/niebla donde está el jugador.</li>
 *   <li>{@code CameraShake}: temblor cuando cae un rayo cerca.</li>
 *   <li>{@code HudOverlays}: indicador de lluvia/tormenta/niebla (desactivado por defecto).</li>
 * </ul>
 */
public final class BloombertoClientCompat {
    private static final float SHAKE_RADIUS = 64f;

    private static final Set<Integer> SEEN_BOLTS = new HashSet<>();
    private static PostEffect desaturate;
    private static PostEffect vignette;

    private BloombertoClientCompat() {
    }

    static void init() {
        desaturate = PostEffects.register(Bloomberto.id(Stormroot.MOD_ID, "storm_desaturate"), BuiltInEffects.Desaturate::new);
        vignette = PostEffects.register(Bloomberto.id(Stormroot.MOD_ID, "storm_vignette"), BuiltInEffects.Vignette::new);
        ClientEvents.CLIENT_TICK.register(BloombertoClientCompat::tick);
        ClientEvents.DISCONNECT.register(BloombertoClientCompat::reset);
        HudOverlays.register(Bloomberto.id(Stormroot.MOD_ID, "weather_hud"), (graphics, partialTick) -> {
            if (!StormrootConfig.weatherHud.getAsBoolean()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) {
                return;
            }
            Component text = Component.translatable("hud.stormroot.weather",
                    percent(ClientWeather.rain()), percent(ClientWeather.thunder()), percent(ClientWeather.fog()));
            graphics.drawString(mc.font, text, 4, 4, 0xFFFFFFFF); // ARGB: desde 1.21.6 sin alfa no se ve
        });
    }

    /** Pantalla de config de Stormroot (la abre Mod Menu en Fabric; en Forge/NeoForge sale sola en la lista de mods). */
    public static Screen configScreen(Screen parent) {
        return BloombertoCompat.config() == null ? null : ConfigScreen.create(parent, BloombertoCompat.config());
    }

    /** Para el self-test: {@code true} si algún efecto de pantalla de Stormroot está encendido. */
    public static boolean effectsActive() {
        return (desaturate != null && desaturate.isEnabled()) || (vignette != null && vignette.isEnabled());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return;
        }
        updateScreenEffects();
        shakeOnNearbyLightning(level, player);
    }

    private static void updateScreenEffects() {
        boolean enabled = StormrootConfig.stormScreenEffects.getAsBoolean();
        float thunder = ClientWeather.thunder();
        float fog = ClientWeather.fog();
        drive(desaturate, enabled ? Math.max(thunder * 0.35f, fog * 0.45f) : 0f);
        drive(vignette, enabled ? Math.max(thunder * 0.5f, fog * 0.3f) : 0f);
    }

    /** ClientWeather ya suaviza los valores: aquí solo se copian al efecto. */
    private static void drive(PostEffect effect, float intensity) {
        if (intensity > 0.01f) {
            effect.setIntensity(intensity).enable();
        } else if (effect.isEnabled()) {
            effect.setIntensity(0f).disable();
        }
    }

    private static void shakeOnNearbyLightning(ClientLevel level, LocalPlayer player) {
        Set<Integer> present = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LightningBolt)) {
                continue;
            }
            present.add(entity.getId());
            if (!SEEN_BOLTS.contains(entity.getId()) && StormrootConfig.lightningCameraShake.getAsBoolean()) {
                float distance = (float) Math.sqrt(entity.distanceToSqr(player));
                if (distance < SHAKE_RADIUS) {
                    float strength = 1f - distance / SHAKE_RADIUS;
                    CameraShake.shared().shake(strength * 0.8f, 15 + (int) (strength * 25));
                }
            }
        }
        SEEN_BOLTS.clear();
        SEEN_BOLTS.addAll(present);
    }

    private static void reset() {
        SEEN_BOLTS.clear();
        drive(desaturate, 0f);
        drive(vignette, 0f);
    }

    private static String percent(float value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }
}
