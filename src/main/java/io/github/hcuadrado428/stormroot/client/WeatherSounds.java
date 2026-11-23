package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.registry.ModSounds;
import io.github.hcuadrado428.stormroot.sky.CloudCover;
import io.github.hcuadrado428.stormroot.sky.SkyWeather;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Ambiente sonoro del clima: un bucle por tipo (viento, viento fuerte, ventisca, arena, granizo, aurora) cuyo volumen
 * sube y baja con la intensidad del frente encima del jugador. Suenan pegados al oído (sin posición), bajo techo se
 * amortiguan, y cuando su volumen llega a cero el bucle se para solo. Lo llama {@link ClientWeather} cada tick.
 */
public final class WeatherSounds {
    /** Lo que tarda un bucle en fundirse (por tick): unos 4 s de entrada y salida. */
    private static final float FADE = 0.012f;
    private static final Map<String, Loop> LOOPS = new HashMap<>();

    private WeatherSounds() {
    }

    static void tick(Minecraft mc, ClientLevel level, LocalPlayer player) {
        double x = player.getX();
        double z = player.getZ();
        boolean dry = !level.getBiome(player.blockPosition()).value().hasPrecipitation();
        double rain = Math.max(ClientFronts.intensity(FrontType.RAIN, x, z),
                ClientFronts.intensity(FrontType.MONSOON, x, z) * 1.2);
        double storm = ClientFronts.intensity(FrontType.THUNDERSTORM, x, z);
        double blizzard = ClientFronts.intensity(FrontType.BLIZZARD, x, z);
        double sand = dry ? ClientFronts.intensity(FrontType.SANDSTORM, x, z) : 0;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean night = time > 13000 && time < 23000;
        double aurora = night ? ClientFronts.intensity(FrontType.AURORA, x, z) : 0;
        CloudCover.Kind kind = SkyWeather.cloudKindAt(x, z);
        boolean hail = kind == CloudCover.Kind.HAIL;

        // Bajo techo todo suena lejano; el granizo repica igual (golpea el tejado).
        float roof = level.canSeeSky(player.blockPosition()) ? 1.0f : 0.35f;
        set("weather.wind", roof * 0.40f * grown(rain));
        set("weather.wind_strong", roof * 0.55f * grown(storm));
        set("weather.blizzard", roof * 0.60f * grown(blizzard));
        set("weather.sandstorm", roof * 0.60f * grown(sand));
        set("weather.hail", (hail ? 0.55f : 0) * grown(storm));
        set("weather.aurora", roof * 0.35f * grown(aurora));

        LOOPS.values().removeIf(Loop::isStopped);
        for (Loop loop : LOOPS.values()) {
            if (!mc.getSoundManager().isActive(loop) && loop.target > 0) {
                mc.getSoundManager().play(loop);
            }
        }
    }

    /** La mayor parte del frente suena "a tope" (intensidad 0,5 ya es 1), como la lluvia visual. */
    private static float grown(double intensity) {
        return (float) Math.min(1.0, intensity * 2);
    }

    private static void set(String id, float target) {
        Loop loop = LOOPS.get(id);
        if (loop == null) {
            if (target <= 0) {
                return;
            }
            loop = new Loop(id);
            LOOPS.put(id, loop);
        }
        loop.target = target;
    }

    /** Bucles sonando ahora mismo. */
    public static int playing() {
        return (int) LOOPS.values().stream().filter(Loop::audible).count();
    }

    /** Bucles que QUEREMOS que suenen (para el self-test: en la CI no hay tarjeta de sonido y el motor no los tica). */
    public static int wanted() {
        return (int) LOOPS.values().stream().filter(l -> l.target > 0.01f).count();
    }

    static void reset() {
        for (Loop loop : LOOPS.values()) {
            loop.target = 0;
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        float target;

        Loop(String id) {
            super(ModSounds.get(id), SoundSource.WEATHER, RandomSource.create());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0.001f;
        }

        boolean audible() {
            return volume > 0.01f;
        }

        @Override
        public void tick() {
            if (volume < target) {
                volume = Math.min(target, volume + FADE);
            } else {
                volume = Math.max(target, volume - FADE);
            }
            if (target <= 0 && volume <= 0.002f) {
                stop();
            }
        }
    }
}
