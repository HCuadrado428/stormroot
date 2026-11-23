package io.github.hcuadrado428.stormroot.registry;

import io.github.hcuadrado428.stormroot.ModIds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;

/**
 * Sonidos del mod (bucles de ambiente del clima; los reproduce {@code client/WeatherSounds}). Creación común;
 * el registro lo hace cada loader en {@code Stormroot.java}, como con los items. Los archivos vienen de grabaciones
 * CC0 reprocesadas (ver {@code docs/creditos-sonidos.md} y {@code art/sounds/make_sounds.py}).
 */
public final class ModSounds {
    /** Un id por bucle; el archivo es {@code sounds/weather/<nombre>.ogg} y la clave {@code weather.<nombre>}. */
    public static final String[] IDS = {
            "weather.wind", "weather.wind_strong", "weather.blizzard",
            "weather.sandstorm", "weather.hail", "weather.aurora",
    };

    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();

    private ModSounds() {
    }

    /** El evento de un id de {@link #IDS} (se crea al pedirlo: en Forge/NeoForge el registro es perezoso). */
    public static SoundEvent get(String id) {
        return EVENTS.computeIfAbsent(id, i -> SoundEvent.createVariableRangeEvent(ModIds.id(i)));
    }
}
