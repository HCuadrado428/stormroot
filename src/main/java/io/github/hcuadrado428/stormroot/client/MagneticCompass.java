package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.weather.FrontType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** ¿Está el jugador dentro de una tormenta magnética? Entonces las brújulas giran ({@code MagneticCompassMixin}). */
public final class MagneticCompass {
    /** La misma intensidad a partir de la cual la tormenta magnetiza el hierro. */
    public static final double SPIN_AT = 0.3;
    private static long spinChecks;

    private MagneticCompass() {
    }

    public static boolean spinning() {
        Player player = Minecraft.getInstance().player;
        if (player == null || !ClientFronts.active()) {
            return false;
        }
        boolean spin = ClientFronts.intensity(FrontType.MAGNETIC_STORM, player.getX(), player.getZ()) >= SPIN_AT;
        if (spin) {
            spinChecks++;
        }
        return spin;
    }

    /** Veces que una brújula ha girado por la tormenta (para el self-test). */
    public static long spinChecks() {
        return spinChecks;
    }
}
