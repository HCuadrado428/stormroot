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
 * Dibuja el cielo de Stormroot (sustituye a las nubes vanilla; ver {@code client/mixin/sky/OptionsCloudsMixin}).
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
    /** Base del muro de una tormenta de arena (casi el nivel del mar: la arena sube desde el suelo). */
    private static final double DUST_Y = 60;

    /** Capa alta (velos finos, solo decorativa). */
    private static final double HIGH_Y = 262;
    /** Sombras: una por bloque, hasta {@code SHADOW_RANGE} bloques alrededor de la cámara. */
    private static final int SHADOW_RANGE = 64;
    private static final int SHADOW_SIZE = SHADOW_RANGE * 2 + 1;
    /** Altura de la cara de arriba de cada columna (cacheada unos fotogramas), relativa a nada: y del mundo. */
    private static final float[] SHADOW_TOP = new float[SHADOW_SIZE * SHADOW_SIZE];
    private static long shadowOriginX = Long.MIN_VALUE;
    private static long shadowOriginZ;
    private static int shadowAge;

    private static final byte[][] GRID = new byte[SIZE + 2][SIZE + 2];
    /** Por celda: intensidad del frente (nubarrones) o aviso de tormenta (buen tiempo), 0–1. */
    private static final float[][] SHADE = new float[SIZE + 2][SIZE + 2];
    /** Calima (0–1) de cada celda: tiñe de naranja las nubes de buen tiempo cerca de una tormenta de arena. */
    private static final float[][] CALIMA = new float[SIZE + 2][SIZE + 2];
    private static final boolean[][] HIGH = new boolean[SIZE + 2][SIZE + 2];
    private static long shadowQuads;
    private static long gridTick = Long.MIN_VALUE;
    private static long gridCellX;
    private static long gridCellZ;
    private static long cellsDrawn;
    private static long frames;
    private static int stormCells;
    private static int dustCells;

    /** Celdas con muro de arena en la última rejilla (para el self-test). */
    public static int dustCells() {
        return dustCells;
    }

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
                        height = heightOf(kind, amount);
                        r = (int) (118 - 30 * amount);
                        g = (int) (124 - 30 * amount);
                        b = (int) (140 - 26 * amount);
                        alpha = 248;
                    }
                    case HAIL -> {
                        // Granizo: el núcleo de la tormenta, con un tono verdoso.
                        bottom = FAIR_Y - 16;
                        height = heightOf(kind, amount);
                        r = (int) (98 - 24 * amount);
                        g = (int) (124 - 20 * amount);
                        b = (int) (118 - 22 * amount);
                        alpha = 250;
                    }
                    case SNOW -> {
                        // Ventisca: nubarrón blanco grisáceo, bajo y espeso.
                        bottom = FAIR_Y - 10;
                        height = heightOf(kind, amount);
                        r = (int) (214 - 24 * amount);
                        g = (int) (218 - 22 * amount);
                        b = (int) (228 - 18 * amount);
                        alpha = 244;
                    }
                    case DUST -> {
                        // Tormenta de arena: un muro marrón desde casi el suelo, visible desde muy lejos.
                        bottom = DUST_Y;
                        height = heightOf(kind, amount);
                        r = (int) (204 - 30 * amount);
                        g = (int) (168 - 34 * amount);
                        b = (int) (116 - 30 * amount);
                        alpha = 150 + (int) (50 * amount);
                    }
                    case RAIN -> {
                        bottom = FAIR_Y - 6;
                        height = heightOf(kind, amount);
                        r = (int) (180 - 20 * amount);
                        g = (int) (184 - 20 * amount);
                        b = (int) (196 - 18 * amount);
                        alpha = 236;
                    }
                    default -> {
                        // Aviso de tormenta: delante de un frente las nubes de buen tiempo se vuelven grises.
                        bottom = FAIR_Y;
                        height = heightOf(kind, amount);
                        r = (int) (255 - 90 * amount);
                        g = (int) (255 - 86 * amount);
                        b = (int) (255 - 72 * amount);
                        alpha = 204 + (int) (36 * amount);
                        // Calima: cerca de una tormenta de arena, las nubes de buen tiempo se vuelven anaranjadas.
                        float calima = CALIMA[i + 1][j + 1];
                        g -= (int) (60 * calima);
                        b -= (int) (110 * calima);
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
                    // El techo del polvo, más transparente que los lados: se ve como una bruma, no como una tapa.
                    int topAlpha = kind == CloudCover.Kind.DUST ? alpha / 6 : alpha;
                    quad(vc, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                            shade(topAlpha, r, g, b, day, hr, hg, hb, haze));
                }
                if (kind == CloudCover.Kind.DUST) {
                    // Muro de arena: todas las paredes con el mismo tono (la escalera de celdas no hace rayas) y un
                    // degradado de abajo (espeso) a arriba (casi transparente), para que se funda con el cielo.
                    int foot = shade(alpha, r, g, b, day * 0.88f, hr, hg, hb, haze);
                    int crown = shade(alpha / 6, r, g, b, day * 0.95f, hr, hg, hb, haze);
                    if (edge(i, j + 1, k, height)) {
                        wall(vc, pose, x0, z0, x0, z1, y0, y1, foot, crown);
                    }
                    if (edge(i + 2, j + 1, k, height)) {
                        wall(vc, pose, x1, z1, x1, z0, y0, y1, foot, crown);
                    }
                    if (edge(i + 1, j, k, height)) {
                        wall(vc, pose, x1, z0, x0, z0, y0, y1, foot, crown);
                    }
                    if (edge(i + 1, j + 2, k, height)) {
                        wall(vc, pose, x0, z1, x1, z1, y0, y1, foot, crown);
                    }
                    drawn++;
                    continue;
                }
                int sideX = shade(alpha, r, g, b, day * 0.9f, hr, hg, hb, haze);
                int sideZ = shade(alpha, r, g, b, day * 0.82f, hr, hg, hb, haze);
                // Lados solo en los bordes: junto a otro tipo de nube o junto a una celda bastante más fina (escalón).
                if (edge(i, j + 1, k, height)) {
                    quad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, sideX);
                }
                if (edge(i + 2, j + 1, k, height)) {
                    quad(vc, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, sideX);
                }
                if (edge(i + 1, j, k, height)) {
                    quad(vc, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, sideZ);
                }
                if (edge(i + 1, j + 2, k, height)) {
                    quad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, sideZ);
                }
                drawn++;
            }
        }
        drawAurora(vc, pose, level, cam, seconds, day);
        drawRainbows(vc, pose, level, cam, day);
        if (StormrootConfig.cloudShadows.getAsBoolean()) {
            drawShadows(vc, pose, level, cam, camCellX, camCellZ, driftX, driftZ, day);
        }
        buffers.endBatch(renderType);
        cellsDrawn += drawn;
        frames++;
    }

    /**
     * Aurora: dos cortinas de luz verde y morada muy altas, hacia el norte, que ondulan. Solo de noche y dentro de un frente
     * de aurora (más intensa cuanto más dentro).
     */
    private static void drawAurora(VertexConsumer vc, PoseStack.Pose pose, ClientLevel level, Vec3 cam, double seconds, float day) {
        double intensity = ClientFronts.intensity(io.github.hcuadrado428.stormroot.weather.FrontType.AURORA, cam.x, cam.z);
        float night = Math.max(0, 1 - day * 1.6f);
        float strength = (float) (intensity * night);
        if (strength < 0.02f) {
            return;
        }
        int segments = 48;
        for (int curtain = 0; curtain < 2; curtain++) {
            double distance = 170 + curtain * 60;
            float bottom = (float) (250 + curtain * 20 - cam.y);
            float top = bottom + 60;
            for (int k = 0; k < segments; k++) {
                double a0 = (k - segments / 2.0) * 14;
                double a1 = a0 + 14;
                double w0 = Math.sin(a0 * 0.025 + seconds * 0.15 + curtain) * 35;
                double w1 = Math.sin(a1 * 0.025 + seconds * 0.15 + curtain) * 35;
                float x0 = (float) a0;
                float x1 = (float) a1;
                float z0 = (float) (-distance + w0);
                float z1 = (float) (-distance + w1);
                double fade = 1 - Math.abs(k - segments / 2.0) / (segments / 2.0);
                double shimmer = 0.7 + 0.3 * Math.sin(k * 0.7 + seconds * 0.9 + curtain * 2);
                int alphaBottom = (int) (150 * strength * fade * shimmer);
                int alphaTop = (int) (40 * strength * fade * shimmer);
                int green = (alphaBottom << 24) | 0x52F2A0;
                int violet = (alphaTop << 24) | 0xA67CFF;
                vertex(vc, pose, x0, bottom, z0, green);
                vertex(vc, pose, x1, bottom, z1, green);
                vertex(vc, pose, x1, top, z1, violet);
                vertex(vc, pose, x0, top, z0, violet);
            }
        }
        auroraFrames++;
    }

    private static long auroraFrames;
    private static long rainbowFrames;

    /** Para el self-test: fotogramas con algún arcoíris dibujado. */
    public static long rainbowFrames() {
        return rainbowFrames;
    }

    /** Colores del arcoíris, de fuera (rojo) a dentro (violeta). */
    private static final int[] RAINBOW = {0xE8403A, 0xF39A2E, 0xF6E04A, 0x5ED15A, 0x3FA7E8, 0x4A5ADB, 0x9A55D6};

    /**
     * Arcoíris: un arco de 7 bandas que sale del suelo en su extremo ({@link io.github.hcuadrado428.stormroot.sky.Rainbow}),
     * sube hasta {@code RADIUS} bloques y baja al otro lado. Solo de día; aparece y se desvanece poco a poco.
     */
    private static void drawRainbows(VertexConsumer vc, PoseStack.Pose pose, ClientLevel level, Vec3 cam, float day) {
        if (day < 0.5f) {
            return;
        }
        long now = level.getGameTime();
        boolean drew = false;
        for (io.github.hcuadrado428.stormroot.sky.Rainbow rainbow : ClientFronts.rainbows()) {
            float fade = (float) rainbow.fade(now);
            if (fade <= 0.01f) {
                continue;
            }
            // Como uno de verdad, el arco siempre se ve de frente: va de lado respecto a la cámara (hacia la izquierda o la
            // derecha según su ángulo), con el extremo fijo en el suelo.
            double toX = rainbow.x() - cam.x;
            double toZ = rainbow.z() - cam.z;
            double len = Math.max(1e-6, Math.sqrt(toX * toX + toZ * toZ));
            double side = Math.cos(rainbow.angle()) >= 0 ? 1 : -1;
            double dirX = -toZ / len * side;
            double dirZ = toX / len * side;
            double radius = io.github.hcuadrado428.stormroot.sky.Rainbow.RADIUS;
            double cx = rainbow.x() + dirX * radius;
            double cz = rainbow.z() + dirZ * radius;
            int segments = 40;
            for (int band = 0; band < RAINBOW.length; band++) {
                double outer = radius + 3 - band * 2.2;
                double inner = outer - 2.2;
                for (int k = 0; k < segments; k++) {
                    double t0 = Math.PI * k / segments;
                    double t1 = Math.PI * (k + 1) / segments;
                    // Los extremos se desvanecen (como uno de verdad), en vez de acabar cortados en el aire.
                    double ends = Math.min(1.0, Math.min(Math.sin((t0 + t1) / 2) * 3.0, 1.0));
                    int argb = ((int) (110 * fade * day * ends) << 24) | RAINBOW[band];
                    quad(vc, pose,
                            (float) (cx - dirX * outer * Math.cos(t0) - cam.x), (float) (rainbow.y() + outer * Math.sin(t0) - cam.y), (float) (cz - dirZ * outer * Math.cos(t0) - cam.z),
                            (float) (cx - dirX * outer * Math.cos(t1) - cam.x), (float) (rainbow.y() + outer * Math.sin(t1) - cam.y), (float) (cz - dirZ * outer * Math.cos(t1) - cam.z),
                            (float) (cx - dirX * inner * Math.cos(t1) - cam.x), (float) (rainbow.y() + inner * Math.sin(t1) - cam.y), (float) (cz - dirZ * inner * Math.cos(t1) - cam.z),
                            (float) (cx - dirX * inner * Math.cos(t0) - cam.x), (float) (rainbow.y() + inner * Math.sin(t0) - cam.y), (float) (cz - dirZ * inner * Math.cos(t0) - cam.z),
                            argb);
                }
            }
            drew = true;
        }
        if (drew) {
            rainbowFrames++;
        }
    }

    /** Para el self-test: fotogramas con aurora dibujada. */
    public static long auroraFrames() {
        return auroraFrames;
    }

    /** Grosor de una nube: las de buen tiempo, fijo; los nubarrones crecen con la intensidad del frente. */
    private static double heightOf(CloudCover.Kind kind, float amount) {
        return switch (kind) {
            case STORM, HAIL -> 5 + 9 * amount;
            case SNOW -> 6 + 6 * amount;
            case RAIN -> 4 + 3 * amount;
            case DUST -> 56; // altura fija: sin caras interiores ni escalones entre celdas
            default -> 4;
        };
    }

    /** {@code true} si hay que dibujar el lado hacia la celda vecina (gi, gj) de la rejilla. */
    private static boolean edge(int gi, int gj, byte kind, double height) {
        byte other = GRID[gi][gj];
        if (other != kind) {
            return true;
        }
        return heightOf(CloudCover.Kind.values()[other], SHADE[gi][gj]) < height - 1;
    }

    /**
     * Sombra de las nubes: un velo oscuro pegado a la cara de arriba de cada bloque (siguiendo su forma: losas, capas de
     * nieve o de arena), bajo cada nube cercana. Se desvanece con la distancia. Los bloques seguidos de una fila a la misma
     * altura y con la misma sombra se dibujan juntos.
     */
    private static void drawShadows(VertexConsumer vc, PoseStack.Pose pose, ClientLevel level, Vec3 cam,
                                    long camCellX, long camCellZ, double driftX, double driftZ, float day) {
        long ox = (long) Math.floor(cam.x) - SHADOW_RANGE;
        long oz = (long) Math.floor(cam.z) - SHADOW_RANGE;
        if (ox != shadowOriginX || oz != shadowOriginZ || ++shadowAge > 40) {
            refreshShadowHeights(level, ox, oz);
        }
        double gridX0 = (camCellX - RADIUS) * C + driftX;
        double gridZ0 = (camCellZ - RADIUS) * C + driftZ;
        for (int c = 0; c < SHADOW_SIZE; c++) {
            long wz = oz + c;
            int runStart = -1;
            float runTop = 0;
            int runAlpha = 0;
            for (int a = 0; a <= SHADOW_SIZE; a++) {
                int alpha = 0;
                float top = 0;
                if (a < SHADOW_SIZE) {
                    long wx = ox + a;
                    top = SHADOW_TOP[c * SHADOW_SIZE + a];
                    alpha = shadowAlpha(wx + 0.5 - gridX0, wz + 0.5 - gridZ0, wx + 0.5 - cam.x, wz + 0.5 - cam.z, top - cam.y, day);
                }
                if (runStart >= 0 && (alpha != runAlpha || top != runTop)) {
                    float x0 = (float) (ox + runStart - cam.x);
                    float x1 = (float) (ox + a - cam.x);
                    float z0 = (float) (wz - cam.z);
                    float y = (float) (runTop + 0.02 - cam.y);
                    quad(vc, pose, x0, y, z0, x0, y, z0 + 1, x1, y, z0 + 1, x1, y, z0, (runAlpha << 24) | 0x0A0C14);
                    shadowQuads++;
                    runStart = -1;
                }
                if (runStart < 0 && alpha > 2) {
                    runStart = a;
                    runTop = top;
                    runAlpha = alpha;
                }
            }
        }
    }

    /** Opacidad de la sombra en un punto ({@code gx, gz}: relativo al borde de la rejilla de nubes; {@code dx, dz, dy}: a la cámara). */
    private static int shadowAlpha(double gx, double gz, double dx, double dz, double dy, float day) {
        if (dy > 0) {
            return 0; // más alto que la cámara: no se ve desde abajo y taparía la vista
        }
        int i = (int) Math.floor(gx / C);
        int j = (int) Math.floor(gz / C);
        if (i < 0 || j < 0 || i >= SIZE || j >= SIZE) {
            return 0;
        }
        byte k = GRID[i + 1][j + 1];
        if (k == 0) {
            return 0;
        }
        CloudCover.Kind kind = CloudCover.Kind.values()[k];
        double darkness = switch (kind) {
            case STORM, HAIL -> 0.42;
            case RAIN, SNOW -> 0.32;
            case DUST -> 0.25 + 0.15 * SHADE[i + 1][j + 1];
            default -> 0.2 + 0.12 * SHADE[i + 1][j + 1];
        };
        double fade = 1 - Math.sqrt(dx * dx + dz * dz) / SHADOW_RANGE;
        if (fade <= 0) {
            return 0;
        }
        // En escalones de 8 para que las filas se puedan juntar.
        return ((int) (255 * darkness * day * Math.min(1, fade * 2)) >> 3) << 3;
    }

    /** Altura exacta de la cara de arriba de cada columna (bloque más alto que para el paso, con su forma). */
    private static void refreshShadowHeights(ClientLevel level, long ox, long oz) {
        shadowOriginX = ox;
        shadowOriginZ = oz;
        shadowAge = 0;
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int c = 0; c < SHADOW_SIZE; c++) {
            for (int a = 0; a < SHADOW_SIZE; a++) {
                int x = (int) (ox + a);
                int z = (int) (oz + c);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                pos.set(x, top - 1, z);
                net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
                float height = shape.isEmpty() ? 1.0f : (float) shape.max(net.minecraft.core.Direction.Axis.Y);
                SHADOW_TOP[c * SHADOW_SIZE + a] = top - 1 + height;
            }
        }
    }

    /** Qué nube hay en cada celda alrededor de la cámara (se calcula en el centro de la celda, en coordenadas del mundo). */
    private static void rebuild(long seed, double wind, double seconds, long camCellX, long camCellZ, List<WeatherFront> fronts) {
        double driftX = CloudCover.driftX(wind, seconds);
        double driftZ = CloudCover.driftZ(wind, seconds);
        int storms = 0;
        int dust = 0;
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
                CALIMA[i][j] = (float) CloudCover.calima(x, z, fronts);
                if (kind == CloudCover.Kind.DUST) {
                    SHADE[i][j] = (float) CloudCover.dustIntensity(x, z, fronts);
                } else if (kind != CloudCover.Kind.NONE) {
                    SHADE[i][j] = (float) CloudCover.frontIntensity(x, z, fronts);
                } else {
                    double bias = CloudCover.fairBias(x, z, fronts);
                    if (CloudCover.fairCloud(seed, u, v, seconds, bias)) {
                        kind = CloudCover.Kind.FAIR;
                        SHADE[i][j] = (float) Math.max(0, bias);
                    }
                }
                GRID[i][j] = (byte) kind.ordinal();
                if (kind == CloudCover.Kind.STORM || kind == CloudCover.Kind.HAIL) {
                    storms++;
                }
                if (kind == CloudCover.Kind.DUST) {
                    dust++;
                }
                HIGH[i][j] = CloudCover.highCloud(seed, u, v, seconds);
            }
        }
        stormCells = storms;
        dustCells = dust;
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int argb) {
        vertex(vc, pose, ax, ay, az, argb);
        vertex(vc, pose, bx, by, bz, argb);
        vertex(vc, pose, cx, cy, cz, argb);
        vertex(vc, pose, dx, dy, dz, argb);
    }

    /** Pared vertical de (ax, az) a (bx, bz), con un color abajo y otro arriba (degradado). */
    private static void wall(VertexConsumer vc, PoseStack.Pose pose, float ax, float az, float bx, float bz,
                             float y0, float y1, int bottom, int top) {
        vertex(vc, pose, ax, y0, az, bottom);
        vertex(vc, pose, bx, y0, bz, bottom);
        vertex(vc, pose, bx, y1, bz, top);
        vertex(vc, pose, ax, y1, az, top);
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
