package io.github.hcuadrado428.stormroot.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Ajustes de cliente cuando NO está Bloomberto (con Bloomberto todo va en {@code config/stormroot.toml}).
 * Archivo {@code config/stormroot-client.properties} en la carpeta del juego; se crea con los valores por defecto.
 * Java puro.
 */
public final class ClientSettingsFile {
    private static final String NAME = "stormroot-client.properties";
    private static Properties props;

    private ClientSettingsFile() {
    }

    /** Nubes de Stormroot en vez de las vanilla (por defecto sí). */
    public static boolean customClouds() {
        return Boolean.parseBoolean(load().getProperty("custom_clouds", "true").trim());
    }

    private static synchronized Properties load() {
        if (props != null) {
            return props;
        }
        props = new Properties();
        Path file = Path.of("config", NAME);
        try {
            if (Files.isRegularFile(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    props.load(r);
                }
            } else {
                props.setProperty("custom_clouds", "true");
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    props.store(w, "Stormroot (cliente). custom_clouds=false para volver a las nubes vanilla"
                            + " (por ejemplo, si tu shader pack dibuja las suyas). Reinicia el juego tras cambiarlo.");
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // Sin archivo o ilegible: valores por defecto.
        }
        return props;
    }
}
