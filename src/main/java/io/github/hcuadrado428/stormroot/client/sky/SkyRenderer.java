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
import net.minecraft.world.level.levelgen.Heightmap;
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
 *   <li>aviso de tormenta: delante de un frente salen más nubes y se vuelven grises; los nubarrones son más gruesos y
 *       oscuros cuanto más fuerte es el frente;</li>
 *   <li>una capa alta de velos finos;</li>
 *   <li>sombra de las nubes en el suelo, cerca de la cámara;</li>
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

    /** Capa alta (velos finos, solo decorativa). */
    private static final double HIGH_Y = 262;
    /** Sombras: trozos de 2×2 bloques, hasta 6 celdas (72 bloques) alrededor de la cámara. */
    private static final int SHADOW_STEP = 2;
    private static final int SHADOW_CELLS = 6;

    private static final byte[][] GRID = new byte[SIZE + 2][SIZE + 2];
    /** Por celda: intensidad del frente (nubarrones) o aviso de tormenta (buen tiempo), 0–1. */
    private static final float[][] SHADE = new float[SIZE + 2][SIZE + 2];
    private static final boolean[][] HIGH = new boolean[SIZE + 2][SIZE + 2];
    private static long shadowQuads;
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

    /** Para el self-test: trozos de sombra dibujados en total. */
    public static long shadowQuads() {
        return shadowQuads;
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
        long seed = ClientFronts.skySeed();
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
                float x0 = (float) ((camCellX - RADIUS + i) * C + driftX - cam.x);
                float z0 = (float) ((camCellZ - RADIUS + j) * C + driftZ - cam.z);
                double cx = x0 + C / 2.0;
                double cz = z0 + C / 2.0;
                float haze = (float) Math.max(0, Math.min(0.8, (Math.sqrt(cx * cx + cz * cz) - 120) / 300));
                if (HIGH[i + 1][j + 1]) {
                    // Capa alta: velos finos, solo una cara, muy transparentes.
                    float yh = (float) (HIGH_Y - cam.y);
                    quad(vc, pose, x0, yh, z0, x0 + C, yh, z0, x0 + C, yh, z0 + C, x0, yh, z0 + C,
                            shade(90, 250, 250, 255, day, hr, hg, hb, haze));
                }
                byte k = GRID[i + 1][j + 1];
                if (k == 0) {
                    continue;
                }
                CloudCover.Kind kind = CloudCover.Kind.values()[k];
                float amount = SHADE[i + 1][j + 1];
                double bottom;
                double height;
                int r;
                int g;
                int b;
                int alpha;
                switch (kind) {
                    case STORM -> {
                        // Más gruesa y oscura cuanto más fuerte es la tormenta en ese punto.
                        bottom = FAIR_Y - 14;
                        height = 5 + 9 * amount;
                        r = (int) (118 - 30 * amount);
                        g = (int) (124 - 30 * amount);
                        b = (int) (140 - 26 * amount);
                        alpha = 248;
                    }
                    case RAIN -> {
                        bottom = FAIR_Y - 6;
                        height = 4 + 3 * amount;
                        r = (int) (180 - 20 * amount);
                        g = (int) (184 - 20 * amount);
                        b = (int) (196 - 18 * amount);
                        alpha = 236;
                    }
                    default -> {
                        // Aviso de tormenta: delante de un frente las nubes de buen tiempo se vuelven grises.
                        bottom = FAIR_Y;
                        height = 4 + 2 * amount;
                        r = (int) (255 - 90 * amount);
                        g = (int) (255 - 86 * amount);
                        b = (int) (255 - 72 * amount);
                        alpha = 204 + (int) (36 * amount);
                    }
                }
                float x1 = x0 + C;
                float z1 = z0 + C;
                float y0 = (float) (bottom - cam.y);
                float y1 = (float) (bottom + height - cam.y);
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
                // Lados solo en los bordes; entre celdas de distinto tipo (o grosor) también, para que no queden huecos.
                if (GRID[i][j + 1] != k || SHADE[i][j + 1] < amount) {
                    quad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, sideX);
                }
                if (GRID[i + 2][j + 1] != k || SHADE[i + 2][j + 1] < amount) {
                    quad(vc, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, sideX);
                }
                if (GRID[i + 1][j] != k || SHADE[i + 1][j] < amount) {
                    quad(vc, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, sideZ);
                }
                if (GRID[i + 1][j + 2] != k || SHADE[i + 1][j + 2] < amount) {
                    quad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, sideZ);
                }
                drawn++;
            }
        }
        if (StormrootConfig.cloudShadows.getAsBoolean()) {
            drawShadows(vc, pose, level, cam, camCellX, camCellZ, driftX, driftZ, day);
        }
        buffers.endBatch(renderType);
        cellsDrawn += drawn;
        frames++;
    }

    /**
     * Sombra de las nubes: manchas oscuras sobre el terreno (en trozos de {@link #SHADOW_STEP} bloques a la altura del
     * bloque más alto de cada trozo), justo debajo de cada nube cercana. Se desvanece con la distancia.
     */
    private static void drawShadows(VertexConsumer vc, PoseStack.Pose pose, ClientLevel level, Vec3 cam,
                                    long camCellX, long camCellZ, double driftX, double driftZ, float day) {
        int sub = C / SHADOW_STEP;
        for (int i = RADIUS - SHADOW_CELLS; i <= RADIUS + SHADOW_CELLS; i++) {
            for (int j = RADIUS - SHADOW_CELLS; j <= RADIUS + SHADOW_CELLS; j++) {
                byte k = GRID[i + 1][j + 1];
                if (k == 0) {
                    continue;
                }
                CloudCover.Kind kind = CloudCover.Kind.values()[k];
                double darkness = switch (kind) {
                    case STORM -> 0.42;
                    case RAIN -> 0.32;
                    default -> 0.2 + 0.12 * SHADE[i + 1][j + 1];
                };
                double wx0 = (camCellX - RADIUS + i) * C + driftX;
                double wz0 = (camCellZ - RADIUS + j) * C + driftZ;
                for (int a = 0; a < sub; a++) {
                    for (int c = 0; c < sub; c++) {
                        double wx = wx0 + a * SHADOW_STEP;
                        double wz = wz0 + c * SHADOW_STEP;
                        double dx = wx + SHADOW_STEP / 2.0 - cam.x;
                        double dz = wz + SHADOW_STEP / 2.0 - cam.z;
                        double fade = 1 - Math.sqrt(dx * dx + dz * dz) / (SHADOW_CELLS * C);
                        if (fade <= 0) {
                            continue;
                        }
                        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(wx + SHADOW_STEP / 2.0),
                                (int) Math.floor(wz + SHADOW_STEP / 2.0));
                        float y = (float) (top + 0.03 - cam.y);
                        if (y > 0) {
                            // Más alta que la cámara: no se ve desde abajo y taparía la vista.
                            continue;
                        }
                        int alpha = (int) (255 * darkness * day * Math.min(1, fade * 2));
                        if (alpha <= 2) {
                            continue;
                        }
                        float x0 = (float) (wx - cam.x);
                        float z0 = (float) (wz - cam.z);
                        float x1 = x0 + SHADOW_STEP;
                        float z1 = z0 + SHADOW_STEP;
                        quad(vc, pose, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, (alpha << 24) | 0x0A0C14);
                        shadowQuads++;
                    }
                }
            }
        }
    }

    /** Qué nube hay en cada celda alrededor de la cámara (se calcula en el centro de la celda, en coordenadas del mundo). */
    private static void rebuild(long seed, double wind, double seconds, long camCellX, long camCellZ, List<WeatherFront> fronts) {
        double driftX = CloudCover.driftX(wind, seconds);
        double driftZ = CloudCover.driftZ(wind, seconds);
        int storms = 0;
        for (int i = 0; i < SIZE + 2; i++) {
            for (int j = 0; j < SIZE + 2; j++) {
                GRID[i][j] = 0;
                SHADE[i][j] = 0;
                HIGH[i][j] = false;
                if (i == 0 || j == 0 || i == SIZE + 1 || j == SIZE + 1) {
                    continue;
                }
                // Círculo, no cuadrado: las esquinas de la rejilla no se dibujan.
                int di = i - 1 - RADIUS;
                int dj = j - 1 - RADIUS;
                if (di * di + dj * dj > RADIUS * RADIUS) {
                    continue;
                }
                double u = (camCellX - RADIUS + i - 1 + 0.5) * C;
                double v = (camCellZ - RADIUS + j - 1 + 0.5) * C;
                double x = u + driftX;
                double z = v + driftZ;
                CloudCover.Kind kind = CloudCover.frontCloud(seed, x, z, u, v, seconds, fronts);
                if (kind != CloudCover.Kind.NONE) {
                    SHADE[i][j] = (float) CloudCover.frontIntensity(x, z, fronts);
                } else {
                    double approach = CloudCover.approach(x, z, fronts);
                    if (CloudCover.fairCloud(seed, u, v, seconds, approach)) {
                        kind = CloudCover.Kind.FAIR;
                        SHADE[i][j] = (float) approach;
                    }
                }
                GRID[i][j] = (byte) kind.ordinal();
                if (kind == CloudCover.Kind.STORM) {
                    storms++;
                }
                HIGH[i][j] = CloudCover.highCloud(seed, u, v, seconds);
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
