package io.github.hcuadrado428.stormroot.compat.bloomberto;

import com.dougg865.bloomberto.Bloomberto;
import com.dougg865.bloomberto.api.client.ClientEvents;
import com.dougg865.bloomberto.api.client.ConfigScreen;
import com.dougg865.bloomberto.api.client.HudOverlays;
import com.dougg865.bloomberto.api.color.Color;
import com.dougg865.bloomberto.api.render.CameraShake;
import com.dougg865.bloomberto.api.render.WorldDraw;
import com.dougg865.bloomberto.api.render.post.BuiltInEffects;
import com.dougg865.bloomberto.api.render.post.PostEffect;
import com.dougg865.bloomberto.api.render.post.PostEffects;
import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.client.ClientFronts;
import io.github.hcuadrado428.stormroot.client.ClientWeather;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Extras de cliente con Bloomberto:
 * <ul>
 *   <li>{@code PostEffects}: desaturación y viñeta propias (stormroot:storm_desaturate / storm_vignette)
 *   según la tormenta/niebla donde está el jugador.</li>
 *   <li>{@code CameraShake}: temblor cuando cae un rayo cerca.</li>
 *   <li>{@code HudOverlays}: indicador de lluvia/tormenta/niebla (desactivado por defecto).</li>
 *   <li>{@code WorldDraw}: nubes y cortinas de lluvia en el borde de los frentes lejanos, para verlos llegar.</li>
 * </ul>
 */
public final class BloombertoClientCompat {
    private static final float SHAKE_RADIUS = 64f;
    /** Frentes lejanos: se dibujan entre {@link #DISTANT_MIN} y {@link #DISTANT_MAX} bloques de su borde. */
    private static final double DISTANT_MIN = 24;
    private static final double DISTANT_MAX = 400;
    private static final int PUFFS = 9;
    private static long distantDrawn;

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
        ClientEvents.WORLD_RENDER.register(BloombertoClientCompat::drawDistantFronts);
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

    /** Para el self-test: veces que se ha dibujado algún frente lejano. */
    public static long distantFrontsDrawn() {
        return distantDrawn;
    }

    /**
     * Dibuja un arco de nubes (y debajo cortinas de lluvia) en el tramo del borde de cada frente que mira a la cámara.
     * Los frentes en los que ya estás, o casi, no se dibujan: ahí está el clima de verdad y las partículas.
     */
    private static void drawDistantFronts(com.dougg865.bloomberto.api.client.WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD || !ClientFronts.active()
                || !StormrootConfig.distantFronts.getAsBoolean()) {
            return;
        }
        Vec3 cam = ctx.cameraPos();
        WorldDraw draw = null;
        for (WeatherFront front : ClientFronts.snapshot()) {
            double dx = cam.x - front.x();
            double dz = cam.z - front.z();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double edge = dist - front.radius();
            double life = front.lifeFactor();
            if (dist < 1 || edge < DISTANT_MIN || edge > DISTANT_MAX || life < 0.1) {
                continue;
            }
            if (draw == null) {
                draw = WorldDraw.of(ctx);
            }
            double nx = dx / dist;
            double nz = dz / dist;
            float fade = (float) (life * Math.max(0.35, 1 - edge / DISTANT_MAX));
            boolean fog = front.type() == FrontType.FOG;
            boolean storm = front.type() == FrontType.THUNDERSTORM;
            int r = storm ? 60 : fog ? 228 : 125;
            int g = storm ? 64 : fog ? 232 : 134;
            int b = storm ? 80 : fog ? 238 : 150;
            double baseY = fog ? cam.y + 4 : cam.y + (storm ? 30 : 36);
            float size = storm ? 34f : fog ? 26f : 28f;
            double spacing = Math.min(front.radius() * 0.35, 18);
            for (int i = -PUFFS / 2; i <= PUFFS / 2; i++) {
                double along = i * spacing;
                double px = front.x() + nx * front.radius() - nz * along;
                double pz = front.z() + nz * front.radius() + nx * along;
                double y = baseY + ((i * 7) % 5) - 2;
                int alpha = (int) (215 * fade * (1 - Math.abs(i) / (PUFFS * 0.75)));
                if (alpha <= 4) {
                    continue;
                }
                draw.billboard(Bloomberto.id(Stormroot.MOD_ID, "textures/misc/front_cloud.png"),
                        new Vec3(px, y, pz), size, Color.ofRgba(r, g, b, alpha));
                if (!fog) {
                    for (int k = 1; k <= 2; k++) {
                        draw.billboard(Bloomberto.id(Stormroot.MOD_ID, "textures/misc/front_rain.png"),
                                new Vec3(px, y - k * size * 0.6, pz), size * 0.8f,
                                Color.ofRgba(r + 30, g + 30, b + 40, (int) (alpha * 0.55)));
                    }
                }
            }
            distantDrawn++;
        }
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
