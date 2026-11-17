package io.github.hcuadrado428.stormroot.compat.bloomberto;

import com.dougg865.bloomberto.Bloomberto;
import com.dougg865.bloomberto.api.client.WorldRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.client.ClientFronts;
import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * Nubes de los frentes al estilo de las nubes vanilla: bloques planos de {@link #CELL}×altura×{@link #CELL} siguiendo
 * el patrón de {@code minecraft:textures/environment/clouds.png}, pegados al frente (avanzan con él en tiempo real),
 * densos en el centro y deshilachados en el borde. Tormenta: capa gruesa gris azulada justo bajo las nubes vanilla;
 * lluvia: capa gris. La niebla no se dibuja: ella misma es el efecto. Con la distancia se funden con el horizonte
 * (como el resto del paisaje), porque el tipo de render no lleva niebla propia.
 *
 * <p>Usa el contexto de render de Bloomberto ({@code WorldRenderContext}) pero dibuja TODAS las caras de un frente en un
 * solo lote (Bloomberto hace un envío por cara, demasiado para miles de caras). Solo dibuja caras visibles: la de abajo o
 * la de arriba según dónde esté la cámara, y los lados solo en el borde de la masa de nubes.
 */
public final class StormClouds {
    /** Tamaño de una celda de nube, como en vanilla. */
    public static final int CELL = 12;
    /** Altura de las nubes vanilla (las nuestras van justo debajo). */
    private static final double VANILLA_CLOUD_Y = 192;
    /** Se dibujan los frentes cuyo borde está a menos de esto de la cámara. */
    private static final double MAX_DISTANCE = 450;

    private static boolean[][] pattern;
    private static boolean patternLoaded;
    private static long drawn;

    private StormClouds() {
    }

    public static long drawnCount() {
        return drawn;
    }

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD || !ClientFronts.active()
                || !StormrootConfig.distantFronts.getAsBoolean()) {
            return;
        }
        Vec3 cam = ctx.cameraPos();
        float daylight = daylight(level);
        for (WeatherFront front : ClientFronts.snapshot()) {
            double dx = cam.x - front.x();
            double dz = cam.z - front.z();
            double edge = Math.sqrt(dx * dx + dz * dz) - front.radius();
            if (front.type() == FrontType.FOG || edge > MAX_DISTANCE || front.lifeFactor() < 0.05) {
                continue;
            }
            drawFront(ctx, level, cam, front, daylight);
            drawn++;
        }
    }

    private static void drawFront(WorldRenderContext ctx, ClientLevel level, Vec3 cam, WeatherFront front, float daylight) {
        FrontType type = front.type();
        double height;
        double bottom;
        double fill;
        int r;
        int g;
        int b;
        float alpha;
        switch (type) {
            case THUNDERSTORM -> {
                height = 6; bottom = VANILLA_CLOUD_Y - 9; fill = 0.22; r = 104; g = 110; b = 128; alpha = 0.97f;
            }
            default -> {
                height = 4; bottom = VANILLA_CLOUD_Y - 5; fill = 0.35; r = 176; g = 181; b = 192; alpha = 0.92f;
            }
        }
        double life = front.lifeFactor();
        int cells = (int) Math.ceil(front.radius() / CELL);
        int size = cells * 2;
        boolean[][] on = new boolean[size + 2][size + 2];
        // Origen de la rejilla pegado al frente: la masa de nubes se mueve con él, sin saltos.
        double originX = front.x() - cells * CELL;
        double originZ = front.z() - cells * CELL;
        int seedX = (int) (front.id() * 7919 % 256);
        int seedZ = (int) (front.id() * 104729 % 256);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double cx = originX + (i + 0.5) * CELL - front.x();
                double cz = originZ + (j + 0.5) * CELL - front.z();
                double d = Math.sqrt(cx * cx + cz * cz) / front.radius();
                if (d >= 1) {
                    continue;
                }
                double intensity = (1 - d) * life;
                boolean cloud = cloudAt(i + seedX, j + seedZ);
                // Centro macizo, borde solo donde el patrón vanilla tiene nube (deshilachado).
                on[i + 1][j + 1] = intensity > 0.04 && (cloud || (intensity > fill && d < 0.85));
            }
        }

        float shadeDay = Math.max(0.18f, daylight);
        int a = (int) (alpha * 255 * Math.min(1.0, life * 1.5));
        // Color del horizonte hacia el que se funden las nubes lejanas (cielo de mediodía, oscurecido de noche).
        int hr = (int) (182 * shadeDay);
        int hg = (int) (198 * shadeDay);
        int hb = (int) (222 * shadeDay);

        double y0 = bottom - cam.y;
        double y1 = bottom + height - cam.y;
        boolean below = cam.y < bottom;
        boolean above = cam.y > bottom + height;

        //? if >=1.21.11 {
        RenderType renderType = RenderTypes.debugQuads();
        //?} else {
        /*RenderType renderType = RenderType.debugQuads();
        *///?}
        VertexConsumer vc = ctx.buffers().getBuffer(renderType);
        PoseStack.Pose pose = ctx.poseStack().last();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (!on[i + 1][j + 1]) {
                    continue;
                }
                float x0 = (float) (originX + i * CELL - cam.x);
                float x1 = x0 + CELL;
                float z0 = (float) (originZ + j * CELL - cam.z);
                float z1 = z0 + CELL;
                float fy0 = (float) y0;
                float fy1 = (float) y1;
                // Neblina de la distancia: de 120 a 700 bloques se van fundiendo con el horizonte.
                double hx = x0 + CELL / 2.0;
                double hz = z0 + CELL / 2.0;
                float haze = (float) Math.max(0, Math.min(0.75, (Math.sqrt(hx * hx + hz * hz) - 120) / 580));
                int bottomColor = hazed(a, r, g, b, shadeDay * 0.78f, hr, hg, hb, haze);
                int topColor = hazed(a, r, g, b, shadeDay, hr, hg, hb, haze);
                int sideX = hazed(a, r, g, b, shadeDay * 0.9f, hr, hg, hb, haze);
                int sideZ = hazed(a, r, g, b, shadeDay * 0.84f, hr, hg, hb, haze);
                if (below) {
                    quad(vc, pose, x0, fy0, z0, x1, fy0, z0, x1, fy0, z1, x0, fy0, z1, bottomColor);
                }
                if (above) {
                    quad(vc, pose, x0, fy1, z0, x0, fy1, z1, x1, fy1, z1, x1, fy1, z0, topColor);
                }
                if (!on[i][j + 1]) {
                    quad(vc, pose, x0, fy0, z0, x0, fy0, z1, x0, fy1, z1, x0, fy1, z0, sideX);
                }
                if (!on[i + 2][j + 1]) {
                    quad(vc, pose, x1, fy0, z0, x1, fy1, z0, x1, fy1, z1, x1, fy0, z1, sideX);
                }
                if (!on[i + 1][j]) {
                    quad(vc, pose, x0, fy0, z0, x0, fy1, z0, x1, fy1, z0, x1, fy0, z0, sideZ);
                }
                if (!on[i + 1][j + 2]) {
                    quad(vc, pose, x0, fy0, z1, x1, fy0, z1, x1, fy1, z1, x0, fy1, z1, sideZ);
                }
            }
        }
        ctx.buffers().endBatch(renderType);
    }

    /** Un quad (el tipo de render no recorta caras traseras, así que se ve por los dos lados). */
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

    private static int hazed(int a, int r, int g, int b, float shade, int hr, int hg, int hb, float haze) {
        int rr = (int) (r * shade * (1 - haze) + hr * haze);
        int gg = (int) (g * shade * (1 - haze) + hg * haze);
        int bb = (int) (b * shade * (1 - haze) + hb * haze);
        return argb(a, rr, gg, bb, 1f);
    }

    private static int argb(int a, int r, int g, int b, float shade) {
        int rr = Math.min(255, (int) (r * shade));
        int gg = Math.min(255, (int) (g * shade));
        int bb = Math.min(255, (int) (b * shade));
        return (Math.max(0, Math.min(255, a)) << 24) | (rr << 16) | (gg << 8) | bb;
    }

    /** Luz del día aproximada (como el cielo vanilla): 1 a mediodía, ~0,15 de noche. */
    private static float daylight(ClientLevel level) {
        double t = (level.getDayTime() % 24000L) / 24000.0 - 0.25;
        double v = Math.cos(t * Math.PI * 2) * 2 + 0.5;
        return (float) Math.max(0.15, Math.min(1.0, v));
    }

    /** ¿Hay nube en esa celda del patrón vanilla? (si no se pudo leer clouds.png, un patrón propio parecido). */
    private static boolean cloudAt(int x, int z) {
        if (!patternLoaded) {
            patternLoaded = true;
            pattern = loadVanillaPattern();
        }
        if (pattern != null) {
            int w = pattern.length;
            int h = pattern[0].length;
            return pattern[Math.floorMod(x, w)][Math.floorMod(z, h)];
        }
        long hsh = (x * 73856093L) ^ (z * 19349663L);
        return Math.floorMod(hsh, 100) < 45;
    }

    private static boolean[][] loadVanillaPattern() {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                    .getResource(Bloomberto.id("minecraft", "textures/environment/clouds.png"));
            if (resource.isEmpty()) {
                return null;
            }
            BufferedImage image;
            try (InputStream in = resource.get().open()) {
                image = ImageIO.read(in);
            }
            boolean[][] p = new boolean[image.getWidth()][image.getHeight()];
            for (int x = 0; x < image.getWidth(); x++) {
                for (int z = 0; z < image.getHeight(); z++) {
                    p[x][z] = (image.getRGB(x, z) >>> 24) > 16;
                }
            }
            return p;
        } catch (Exception e) {
            Stormroot.LOGGER.warn("[Stormroot] No se pudo leer clouds.png; uso un patrón propio", e);
            return null;
        }
    }
}
