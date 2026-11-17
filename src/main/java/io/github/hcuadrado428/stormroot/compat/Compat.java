package io.github.hcuadrado428.stormroot.compat;

import io.github.hcuadrado428.stormroot.Stormroot;

/**
 * Integraciones opcionales con otros mods. Se cargan por reflexión: si el mod no está instalado,
 * o si este jar se compiló sin la integración, no se carga ninguna clase suya y no pasa nada.
 */
public final class Compat {
    private static boolean bloomberto;

    private Compat() {
    }

    /**
     * Llamar mientras el mod carga (constructor / onInitialize), con {@code loaded} = si el loader dice que
     * Bloomberto está instalado, y {@code client} = si estamos en un cliente físico.
     */
    public static void initBloomberto(boolean loaded, boolean client) {
        if (!loaded) {
            return;
        }
        try {
            Class<?> type = Class.forName("io.github.hcuadrado428.stormroot.compat.bloomberto.BloombertoCompat");
            bloomberto = (Boolean) type.getMethod("init", boolean.class).invoke(null, client);
            if (bloomberto) {
                Stormroot.LOGGER.info("[Stormroot] Integración con Bloomberto activada");
            }
        } catch (ClassNotFoundException e) {
            Stormroot.LOGGER.info("[Stormroot] Bloomberto está instalado, pero este jar no incluye la integración");
        } catch (Throwable t) {
            Stormroot.LOGGER.warn("[Stormroot] No se pudo activar la integración con Bloomberto", t);
        }
    }

    public static boolean bloomberto() {
        return bloomberto;
    }
}
