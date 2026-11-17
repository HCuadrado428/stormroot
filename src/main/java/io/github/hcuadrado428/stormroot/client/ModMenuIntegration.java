package io.github.hcuadrado428.stormroot.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.compat.Compat;
import net.minecraft.client.gui.screens.Screen;

/**
 * Botón "Config" de Stormroot en Mod Menu (solo Fabric; entrypoint "modmenu" de fabric.mod.json).
 * La pantalla es la de Bloomberto: sin Bloomberto no hay config que abrir y Mod Menu no muestra el botón.
 * Se compila solo en Fabric (build.gradle.kts la excluye en Forge/NeoForge) y Mod Menu es opcional.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!Compat.bloomberto()) {
            return parent -> null;
        }
        return ModMenuIntegration::open;
    }

    /** Abre la pantalla por reflexión: así esta clase no carga Bloomberto si no está instalado. */
    static Screen open(Screen parent) {
        try {
            return (Screen) Class.forName("io.github.hcuadrado428.stormroot.compat.bloomberto.BloombertoClientCompat")
                    .getMethod("configScreen", Screen.class).invoke(null, parent);
        } catch (ReflectiveOperationException | LinkageError e) {
            Stormroot.LOGGER.warn("[Stormroot] No se pudo abrir la config de Bloomberto", e);
            return null;
        }
    }
}
