package io.github.hcuadrado428.stormroot.mycel;

/**
 * Reglas de la red de micelio y de la bruma de esporas (Java puro, sin Minecraft).
 * La humedad es la del subsuelo de la filtración ({@code SeepageCell.deep()}, de -1 a 1).
 */
public final class MyceliumRules {
    /** Con el subsuelo así de seco la red deja de crecer (no se muere: es permanente). */
    public static final double DRY_AT = -0.3;
    /** Hace falta al menos esta humedad para que nazca una red nueva. */
    public static final double BIRTH_DAMP = 0.15;
    /** Con luz de bloque (antorchas...) desde este nivel los hilos se retiran y no crecen. */
    public static final int SCARED_LIGHT = 7;
    /** Un nodo nuevo no puede estar a menos de esto de otro. */
    public static final int NODE_SPACING = 7;
    /** Probabilidad de que un crecimiento haga un nodo nuevo en vez de un hilo. */
    public static final double NODE_CHANCE = 0.04;
    /** Bloques que se miran como mucho al comprobar si un hilo sigue unido a algún nodo. */
    public static final int CONNECTION_SEARCH = 2048;

    /** Segundos respirando bruma de esporas antes de marearse. */
    public static final int NAUSEA_AFTER = 30;
    /** Lo que baja la exposición tras marearse (no vuelve a marear en seguida). */
    public static final int NAUSEA_RELIEF = 20;
    /** Segundos de náusea. */
    public static final int NAUSEA_SECONDS = 7;
    /** Por encima del nivel del mar + esto, la bruma no llega (protección: subir a terreno alto). */
    public static final int MIST_CEILING = 16;

    private MyceliumRules() {
    }

    /** Probabilidad de que un hilo o nodo crezca en un tick aleatorio. Con niebla suelta esporas y crece el doble. */
    public static double growthChance(double deep, boolean foggy) {
        if (deep < DRY_AT) {
            return 0;
        }
        double chance = Math.min(1.0, 0.25 + 0.6 * Math.max(0, deep));
        return Math.min(1.0, foggy ? chance * 2 : chance);
    }

    public static boolean canBeBorn(double deep) {
        return deep >= BIRTH_DAMP;
    }

    public static boolean scaredBy(int blockLight) {
        return blockLight >= SCARED_LIGHT;
    }

    /** Exposición a la bruma tras un segundo más (o menos, si ya no la respira). */
    public static int expose(int exposure, boolean inMist) {
        return inMist ? exposure + 1 : Math.max(0, exposure - 2);
    }

    public static boolean nauseous(int exposure) {
        return exposure >= NAUSEA_AFTER;
    }
}
