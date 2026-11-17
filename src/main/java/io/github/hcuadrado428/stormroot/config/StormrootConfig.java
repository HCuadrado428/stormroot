package io.github.hcuadrado428.stormroot.config;

/**
 * Ajustes del mod. Java puro.
 *
 * <p>De momento son campos estáticos con su valor por defecto; cuando haya fichero de configuración
 * (skill {@code mod-config}) se leerán de ahí sin cambiar a quien los usa.
 */
public final class StormrootConfig {
    /** Probabilidad (0–1) de que un rayo sobre arena o piedra deje un cristal de rayo. */
    public static double lightningCrystalChance = 0.25;

    /**
     * Ticks que se espera tras el rayo antes de soltar el cristal: el rayo daña a las entidades
     * cercanas durante unos ticks y el item se destruiría.
     */
    public static int lightningCrystalDelayTicks = 20;

    private StormrootConfig() {
    }
}
