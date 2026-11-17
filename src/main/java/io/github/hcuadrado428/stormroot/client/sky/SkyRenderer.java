package io.github.hcuadrado428.stormroot.client.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.hcuadrado428.stormroot.client.ClientFronts;
import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.sky.CloudCover;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * Dibuja el cielo de Stormroot (sustituye a las nubes vanilla; ver {@code client/mixin/clouds/OptionsCloudsMixin}).
 * Qué nube hay en cada celda lo decide {@link CloudCover} (Java puro, determinista). Aquí solo se dibuja:
 * <ul>
 *   <li>bloques planos al estilo vanilla con caras sombreadas (arriba claras, lados medios, abajo oscuras);</li>
 *   <li>buen tiempo: blancas a la altura vanilla; lluvia: grises algo más bajas; tormenta: gris azulado, gruesas y bajas;</li>
 *   <li>solo caras visibles (abajo o arriba según la cámara; lados solo en los bordes) y todo en un solo lote;</li>
 *   <li>con la distancia se funden con el horizonte (el tipo de render no lleva niebla propia).</li>
 * </ul>
 * La rejilla se recalcula cada medio segundo o al cambiar de celda (no por fotograma); entre medias solo se desplaza
 * con el viento.
 */
public final class SkyRenderer {
    private static final int C = CloudCover.CELL;
    /** Celdas alrededor de la cámara (radio). 32 × 12 = 384 bloques. */
    private static final int RADIUS = 32;
    private static final int SIZE = RADIUS * 2 + 1;
    private static final double FAIR_Y = 192;

    private static final byte[][] GRID = new byte[SIZE + 2][SIZE + 2];
    private static long gridTick = Long.MIN_VALUE;
    private static long gridCellX;
    private static long gridCellZ;
    private static long cellsDrawn;
    private static long frames;
    private static int stormCells;

    private SkyRenderer() {
    }

    /** {@code true} si Stormroot pone el cielo ahora: hay que ocultar las nubes vanilla. */
    public static boolean replacesVanillaClouds() {
        if (!StormrootConfig.customClouds.getAsBoolean() || !ClientFronts.active()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.level.dimension() == Level.OVERWORLD;
    }

    /** Para el self-test: fotogramas dibujados y celdas de nube dibujadas en total. */
    public static long framesDrawn() {
        return frames;
    }

    public static long cellsDrawn() {
        return cellsDrawn;
    }

    /** Para el self-test: celdas de nubarrón de tormenta en la última rejilla calculada. */
    public static int stormCells() {
        return stormCells;
    }

    public static void render(PoseStack poseStack, Camera camera, float partialTick, MultiBufferSource.BufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !replacesVanillaClouds()) {
            return;
        }
        // Respeta el ajuste de nubes del jugador (el valor real, no el que devuelve getCloudsType con nuestro mixin).
        CloudStatus status = mc.options.cloudStatus().get();
        if (status == CloudStatus.OFF) {
            return;
        }
        boolean fancy = status == CloudStatus.FANCY;
        //? if >=1.21.11 {
        Vec3 cam = camera.position();
        //?} else {
        /*Vec3 cam = camera.getPosition();
        *///?}
        long seed = CloudCover.SKY_SEED;
        double wind = ClientFronts.windAngle();
        long tick = level.getGameTime();
        double seconds = (tick + partialTick) / 20.0;
        double driftX = CloudCover.driftX(wind, seconds);
        double driftZ = CloudCover.driftZ(wind, seconds);

        // Rejilla en coordenadas del cielo (que deriva con el viento): celda (i, j) = [i*C, (i+1)*C) en u.
        long camCellX = (long) Math.floor((cam.x - driftX) / C);
        long camCellZ = (long) Math.floor((cam.z - driftZ) / C);
        if (tick - gridTick >= 10 || tick < gridTick || camCellX != gridCellX || camCellZ != gridCellZ) {
            rebuild(seed, wind, tick / 20.0, camCellX, camCellZ, ClientFronts.snapshot());
            gridTick = tick;
            gridCellX = camCellX;
            gridCellZ = camCellZ;
        }

        float day = daylight(level);
        int hr = (int) (182 * day);
        int hg = (int) (198 * day);
        int hb = (int) (222 * day);

        //? if >=1.21.11 {
        RenderType renderType = RenderTypes.debugQuads();
        //?} else {
        /*RenderType renderType = RenderType.debugQuads();
        *///?}
        VertexConsumer vc = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        long drawn = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                byte k = GRID[i + 1][j + 1];
                if (k == 0) {
                    continue;
                }
                CloudCover.Kind kind = CloudCover.Kind.values()[k];
                double bottom;
                double height;
                int r;
                int g;
                int b;
                int alpha;
                switch (kind) {
                    case STORM -> {
                        bottom = FAIR_Y - 14; height = 8; r = 100; g = 106; b = 124; alpha = 248;
                    }
                    case RAIN -> {
                        bottom = FAIR_Y - 6; height = 5; r = 172; g = 177; b = 189; alpha = 236;
                    }
                    default -> {
                        bottom = FAIR_Y; height = 4; r = 255; g = 255; b = 255; alpha = 204;
                    }
                }
                float x0 = (float) ((camCellX - RADIUS + i) * C + driftX - cam.x);
                float z0 = (float) ((camCellZ - RADIUS + j) * C + driftZ - cam.z);
                float x1 = x0 + C;
                float z1 = z0 + C;
                float y0 = (float) (bottom - cam.y);
                float y1 = (float) (bottom + height - cam.y);
                double cx = x0 + C / 2.0;
                double cz = z0 + C / 2.0;
                float haze = (float) Math.max(0, Math.min(0.8, (Math.sqrt(cx * cx + cz * cz) - 120) / 300));
                if (!fancy) {
                    // Nubes rápidas: una sola capa plana, como vanilla en "Rápidas".
                    int flat = shade(alpha, r, g, b, day * 0.9f, hr, hg, hb, haze);
                    quad(vc, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, flat);
                    drawn++;
                    continue;
                }
                if (y0 > 0) {
                    quad(vc, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                            shade(alpha, r, g, b, day * 0.72f, hr, hg, hb, haze));
                }
                if (y1 < 0) {
                    quad(vc, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                            shade(alpha, r, g, b, day, hr, hg, hb, haze));
                }
                int sideX = shade(alpha, r, g, b, day * 0.9f, hr, hg, hb, haze);
                int sideZ = shade(alpha, r, g, b, day * 0.82f, hr, hg, hb, haze);
                if (GRID[i][j + 1] != k) {
                    quad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, sideX);
                }
                if (GRID[i + 2][j + 1] != k) {
                    quad(vc, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, sideX);
                }
                if (GRID[i + 1][j] != k) {
                    quad(vc, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, sideZ);
                }
                if (GRID[i + 1][j + 2] != k) {
                    quad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, sideZ);
                }
                drawn++;
            }
        }
        buffers.endBatch(renderType);
        cellsDrawn += drawn;
        frames++;
    }

    /** Qué nube hay en cada celda alrededor de la cámara (se calcula en el centro de la celda, en coordenadas del mundo). */
    private static void rebuild(long seed, double wind, double seconds, long camCellX, long camCellZ, List<WeatherFront> fronts) {
        double driftX = CloudCover.driftX(wind, seconds);
        double driftZ = CloudCover.driftZ(wind, seconds);
        int storms = 0;
        for (int i = 0; i < SIZE + 2; i++) {
            for (int j = 0; j < SIZE + 2; j++) {
                if (i == 0 || j == 0 || i == SIZE + 1 || j == SIZE + 1) {
                    GRID[i][j] = 0;
                    continue;
                }
                double u = (camCellX - RADIUS + i - 1 + 0.5) * C;
                double v = (camCellZ - RADIUS + j - 1 + 0.5) * C;
                double x = u + driftX;
                double z = v + driftZ;
                // Círculo, no cuadrado: las esquinas de la rejilla no se dibujan.
                int di = i - 1 - RADIUS;
                int dj = j - 1 - RADIUS;
                if (di * di + dj * dj > RADIUS * RADIUS) {
                    GRID[i][j] = 0;
                    continue;
                }
                CloudCover.Kind kind = CloudCover.at(seed, x, z, wind, seconds, fronts);
                GRID[i][j] = (byte) kind.ordinal();
                if (kind == CloudCover.Kind.STORM) {
                    storms++;
                }
            }
        }
        stormCells = storms;
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int argb) {
        vertex(vc, pose, ax, ay, az, argb);
        vertex(vc, pose, bx, by, bz, argb);
        vertex(vc, pose, cx, cy, cz, argb);
        vertex(vc, pose, dx, dy, dz, argb);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int argb) {
        //? if >=1.21 {
        vc.addVertex(pose, x, y, z).setColor(argb);
        //?} else {
        /*vc.vertex(pose.pose(), x, y, z).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
        *///?}
    }

    /** Color sombreado y fundido con el horizonte según la distancia. */
    private static int shade(int a, int r, int g, int b, float light, int hr, int hg, int hb, float haze) {
        int rr = (int) (r * light * (1 - haze) + hr * haze);
        int gg = (int) (g * light * (1 - haze) + hg * haze);
        int bb = (int) (b * light * (1 - haze) + hb * haze);
        return (Math.max(0, Math.min(255, a)) << 24) | (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
    }

    /** Luz del día aproximada (como el cielo vanilla): 1 a mediodía, ~0,15 de noche. */
    private static float daylight(ClientLevel level) {
        double t = (level.getDayTime() % 24000L) / 24000.0 - 0.25;
        double v = Math.cos(t * Math.PI * 2) * 2 + 0.5;
        return (float) Math.max(0.15, Math.min(1.0, v));
    }
}
