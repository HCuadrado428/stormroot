package io.github.hcuadrado428.stormroot.sky;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Semilla del cielo de un mundo. Sale de la semilla del mundo con SHA-256 (de un solo sentido): se manda a todos los
 * clientes y no debe permitir sacar la semilla del mundo. Java puro.
 */
public final class SkySeed {
    private SkySeed() {
    }

    public static long fromWorldSeed(long worldSeed) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("stormroot-sky".getBytes(StandardCharsets.UTF_8));
            sha.update(ByteBuffer.allocate(Long.BYTES).putLong(worldSeed).array());
            return ByteBuffer.wrap(sha.digest()).getLong();
        } catch (NoSuchAlgorithmException e) {
            // Todas las JVM tienen SHA-256; por si acaso, una semilla fija.
            return CloudCover.SKY_SEED;
        }
    }
}
