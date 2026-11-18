package io.github.hcuadrado428.stormroot.weather;

/**
 * Reglas de la helada (Java puro). No es un frente: en las noches despejadas de los biomas fríos y templados hiela
 * hasta media mañana. Qué noches hiela sale de la semilla del cielo, así que el servidor y el cliente coinciden.
 * Las horas son las del día de Minecraft ({@code dayTime % 24000}: 0 = 6:00, 6000 = mediodía, 18000 = medianoche).
 */
public final class FrostRules {
    /** Empieza a helar a las 20:00. */
    public static final long FROST_FROM = 14000;
    /** Y se acaba a las 10:00 del día siguiente (media mañana). */
    public static final long FROST_UNTIL = 4000;
    /** Probabilidad de que una noche hiele en un bioma frío. */
    public static final double COLD_CHANCE = 0.7;
    /** Y en uno templado (o una montaña). */
    public static final double TEMPERATE_CHANCE = 0.3;
    /** Con luz de bloque desde este nivel (una antorcha a 7 bloques) los cultivos siguen creciendo. */
    public static final int WARM_LIGHT = 7;

    private FrostRules() {
    }

    /** ¿Es hora de helada? (de noche y la mañana siguiente). */
    public static boolean frostHours(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= FROST_FROM || t < FROST_UNTIL;
    }

    /** Número de la noche: la noche y la mañana que la sigue tienen el mismo. */
    public static long nightIndex(long dayTime) {
        return Math.floorDiv(dayTime + (24000L - FROST_FROM), 24000L);
    }

    /** Probabilidad de helada de una noche según la zona del bioma (0 si nunca hiela ahí). */
    public static double chance(ClimateZone zone) {
        return switch (zone) {
            case COLD -> COLD_CHANCE;
            case TEMPERATE, MOUNTAIN -> TEMPERATE_CHANCE;
            default -> 0;
        };
    }

    /** ¿Hiela esa noche en esa zona? Determinista: depende solo de la semilla del cielo y de la noche. */
    public static boolean frostNight(long skySeed, long night, ClimateZone zone) {
        double chance = chance(zone);
        if (chance <= 0) {
            return false;
        }
        long h = skySeed * 0x9E3779B97F4A7C15L + night * 0xC2B2AE3D27D4EB4FL + zone.ordinal() * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        double roll = (h >>> 11) * 0x1.0p-53;
        return roll < chance;
    }

    /**
     * ¿Hiela aquí y ahora? Hace falta hora de helada, que esa noche hiele en la zona y que el cielo esté despejado
     * (ningún frente encima: {@code frontIntensity} es la del frente más fuerte en ese punto).
     */
    public static boolean freezing(long skySeed, long dayTime, ClimateZone zone, double frontIntensity) {
        return frostHours(dayTime) && frontIntensity < 0.05 && frostNight(skySeed, nightIndex(dayTime), zone);
    }

    /** Los cultivos no crecen con helada, salvo con luz cerca. */
    public static boolean stopsGrowth(boolean freezing, int blockLight) {
        return freezing && blockLight < WARM_LIGHT;
    }
}
