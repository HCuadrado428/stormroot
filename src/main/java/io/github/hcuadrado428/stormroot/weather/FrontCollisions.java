package io.github.hcuadrado428.stormroot.weather;

import java.util.ArrayList;
import java.util.List;

/**
 * Choques entre frentes (Java puro). Dos frentes chocan cuando los dos están maduros (en el centro de su vida) y sus
 * centros están más cerca que la mitad de la suma de sus radios. Cada choque se resuelve una sola vez:
 * <ul>
 *   <li>Ola de calor + lluvia → la lluvia se vuelve tormenta eléctrica (aire caliente que sube).</li>
 *   <li>Ola de calor + ventisca → la ventisca se deshace (y la nieve se derrite: deshielo masivo).</li>
 *   <li>Ventisca + lluvia → la ventisca se traga la lluvia y dura más.</li>
 *   <li>Niebla + lluvia, tormenta, monzón, ventisca, calor o arena → la niebla se disipa.</li>
 *   <li>Lluvia, monzón o tormenta + tormenta de arena → la lluvia asienta la arena: la de arena se deshace.</li>
 *   <li>Dos frentes del mismo tipo → se funden en uno más grande (hasta 1,3 veces el radio máximo del tipo).</li>
 * </ul>
 * Los raros (aurora, tormenta magnética) no chocan con nada.
 */
public final class FrontCollisions {
    /** Fuerza de choque: centros a menos de este factor × (r1 + r2). */
    public static final double CONTACT = 0.5;
    public static final double MERGE_GROWTH = 1.3;

    /** Qué pasó en un choque (para el registro y el self-test). */
    public record Event(FrontType a, FrontType b, String result) {
    }

    private FrontCollisions() {
    }

    /** Resuelve los choques de la lista (la cambia). {@code ids} da ids nuevos para los frentes que cambian de tipo. */
    public static List<Event> resolve(List<WeatherFront> fronts, java.util.function.LongSupplier ids) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < fronts.size(); i++) {
            for (int j = i + 1; j < fronts.size(); j++) {
                WeatherFront a = fronts.get(i);
                WeatherFront b = fronts.get(j);
                if (!inContact(a, b)) {
                    continue;
                }
                Event event = collide(fronts, i, j, ids);
                if (event != null) {
                    events.add(event);
                }
            }
        }
        fronts.removeIf(WeatherFront::isExpired);
        return events;
    }

    public static boolean inContact(WeatherFront a, WeatherFront b) {
        return a.lifeFactor() >= 1.0 && b.lifeFactor() >= 1.0 && !rare(a.type()) && !rare(b.type())
                && a.distanceTo(b) < (a.radius() + b.radius()) * CONTACT;
    }

    private static boolean rare(FrontType type) {
        return type == FrontType.AURORA || type == FrontType.MAGNETIC_STORM;
    }

    private static boolean wet(FrontType type) {
        return type == FrontType.RAIN || type == FrontType.THUNDERSTORM || type == FrontType.MONSOON;
    }

    private static Event collide(List<WeatherFront> fronts, int i, int j, java.util.function.LongSupplier ids) {
        WeatherFront a = fronts.get(i);
        WeatherFront b = fronts.get(j);
        FrontType ta = a.type();
        FrontType tb = b.type();
        // Mismo tipo: se funden (el grande se queda con el otro).
        if (ta == tb) {
            WeatherFront big = a.radius() >= b.radius() ? a : b;
            WeatherFront small = big == a ? b : a;
            big.growTo(Math.hypot(big.radius(), small.radius()), ta.maxRadius() * MERGE_GROWTH);
            small.expireNow();
            return new Event(ta, tb, "se funden");
        }
        // Ordenar para que las reglas se miren una vez.
        if (pairIs(ta, tb, FrontType.HEAT_WAVE, FrontType.RAIN)) {
            int rainIndex = ta == FrontType.RAIN ? i : j;
            fronts.set(rainIndex, fronts.get(rainIndex).asType(ids.getAsLong(), FrontType.THUNDERSTORM));
            return new Event(ta, tb, "la lluvia se vuelve tormenta");
        }
        if (pairIs(ta, tb, FrontType.HEAT_WAVE, FrontType.BLIZZARD)) {
            (ta == FrontType.BLIZZARD ? a : b).fadeOut();
            return new Event(ta, tb, "la ventisca se deshace");
        }
        if (pairIs(ta, tb, FrontType.BLIZZARD, FrontType.RAIN)) {
            WeatherFront blizzard = ta == FrontType.BLIZZARD ? a : b;
            WeatherFront rain = blizzard == a ? b : a;
            blizzard.extend(rain.maxAgeSeconds() - rain.ageSeconds(), FrontType.BLIZZARD.maxDurationSeconds() * 1.5);
            rain.expireNow();
            return new Event(ta, tb, "la ventisca se traga la lluvia");
        }
        if (ta == FrontType.FOG || tb == FrontType.FOG) {
            (ta == FrontType.FOG ? a : b).fadeOut();
            return new Event(ta, tb, "la niebla se disipa");
        }
        if ((ta == FrontType.SANDSTORM && wet(tb)) || (tb == FrontType.SANDSTORM && wet(ta))) {
            (ta == FrontType.SANDSTORM ? a : b).fadeOut();
            return new Event(ta, tb, "la lluvia asienta la arena");
        }
        return null;
    }

    private static boolean pairIs(FrontType a, FrontType b, FrontType x, FrontType y) {
        return (a == x && b == y) || (a == y && b == x);
    }
}
