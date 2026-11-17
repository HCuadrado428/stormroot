package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Clima por zonas en el cliente (solo vanilla de cliente; la llaman los hooks de cada loader).
 * <ul>
 *   <li>Lluvia y truenos visibles según los frentes donde está el jugador, con transición suave.</li>
 *   <li>Niebla dentro de los frentes de niebla ({@link #fogFarPlane}).</li>
 *   <li>Partículas en el borde de los frentes cercanos.</li>
 * </ul>
 * Si el servidor no tiene Stormroot o tiene {@code /stormroot weather off}, no toca nada.
 */
public final class ClientWeather {
    /** Cuánto cambia la lluvia/trueno/niebla por tick (0.01 → unos 5 s de transición completa). */
    private static final float STEP = 0.01f;
    /** Distancia de niebla en el centro de un frente de niebla. */
    private static final float FOG_MIN_DISTANCE = 12f;
    /** Distancia máxima al borde para las partículas (más allá, Minecraft no las dibuja). Cantidad: config. */
    private static final double EDGE_MAX_DISTANCE = 32;

    /** Veces que un enganche de niebla (evento o mixin) ha aplicado la niebla de Stormroot. Para el self-test. */
    private static volatile long fogApplied;

    private static float rain;
    private static float thunder;
    private static float fog;

    private ClientWeather() {
    }

    public static void tick() {
        io.github.hcuadrado428.stormroot.client.sky.ClientSkySource.ensureRegistered();
        ClientSelfTest.tick();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            ClientFronts.reset();
            rain = thunder = fog = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        if (!ClientFronts.active() || level.dimension() != Level.OVERWORLD) {
            fog = approach(fog, 0);
            return;
        }

        ClientFronts.advance(1.0 / 20.0);
        double x = player.getX();
        double z = player.getZ();
        double storm = ClientFronts.intensity(FrontType.THUNDERSTORM, x, z);
        double blizzard = ClientFronts.intensity(FrontType.BLIZZARD, x, z);
        double monsoon = ClientFronts.intensity(FrontType.MONSOON, x, z);
        double wet = Math.max(Math.max(Math.max(ClientFronts.intensity(FrontType.RAIN, x, z), storm), blizzard), monsoon);
        double mist = ClientFronts.intensity(FrontType.FOG, x, z);
        // La niebla se deshace sobre los biomas sin lluvia (desierto, badlands, sabana).
        if (!level.getBiome(player.blockPosition()).value().hasPrecipitation()) {
            mist = 0;
        }

        // La mayor parte del frente debe verse "a tope": intensidad 0.5 ya es lluvia completa.
        rain = approach(rain, (float) Math.min(1.0, wet * 2));
        thunder = approach(thunder, (float) Math.min(1.0, storm * 2));
        // Ventisca: casi tanta niebla como un banco de niebla (la nieve no deja ver).
        // Monzón: la lluvia torrencial también quita visibilidad (algo menos que la ventisca).
        fog = approach(fog, (float) Math.min(1.0, Math.max(Math.max(mist, blizzard * 0.8), monsoon * 0.45) * 2));

        level.setRainLevel(rain);
        level.setThunderLevel(thunder);

        spawnEdgeParticles(level, player);
        spawnRollingFog(level, player);
        spawnHail(level, player);
        spawnCaveParticles(level, player);
    }

    /** Bajo tierra: goteo del techo si el subsuelo está empapado, polvo si hay sequía. */
    private static void spawnCaveParticles(ClientLevel level, LocalPlayer player) {
        float seep = ClientFronts.seep();
        if (Math.abs(seep) < 0.3f || level.canSeeSky(player.blockPosition())) {
            return;
        }
        RandomSource random = level.getRandom();
        if (random.nextFloat() > Math.abs(seep)) {
            return;
        }
        BlockPos base = player.blockPosition();
        if (seep > 0) {
            for (int attempt = 0; attempt < 4; attempt++) {
                BlockPos air = base.offset(random.nextInt(17) - 8, random.nextInt(6), random.nextInt(17) - 8);
                BlockPos ceiling = air.above();
                if (level.getBlockState(air).isAir() && !level.getBlockState(ceiling).isAir()
                        && level.getBlockState(ceiling).getFluidState().isEmpty()) {
                    level.addParticle(ParticleTypes.DRIPPING_WATER, air.getX() + random.nextDouble(),
                            ceiling.getY() - 0.05, air.getZ() + random.nextDouble(), 0, 0, 0);
                    return;
                }
            }
        } else {
            double x = player.getX() + (random.nextDouble() - 0.5) * 12;
            double y = player.getY() + random.nextDouble() * 3;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 12;
            level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0, 0, 0);
        }
    }

    /** Lluvia visible ahora mismo (0–1), ya suavizada. */
    public static float rain() {
        return rain;
    }

    /** Tormenta visible ahora mismo (0–1), ya suavizada. */
    public static float thunder() {
        return thunder;
    }

    /** Niebla de Stormroot ahora mismo (0–1), ya suavizada. */
    public static float fog() {
        return fog;
    }

    /**
     * Distancia de niebla a usar, dado la de vanilla. Devuelve la misma si no hay niebla de Stormroot.
     * La usan los eventos de niebla de Forge/NeoForge (y un futuro mixin en Fabric).
     */
    public static float fogFarPlane(float vanillaFar) {
        if (fog <= 0.01f || vanillaFar <= FOG_MIN_DISTANCE) {
            return vanillaFar;
        }
        // Curva que cierra antes: con la mitad de niebla ya se ve poco más allá de la mitad de la distancia.
        float t = (float) Math.sqrt(fog);
        return vanillaFar + (FOG_MIN_DISTANCE - vanillaFar) * t;
    }

    /** Lo llaman los enganches de niebla de cada loader cuando aplican {@link #fogFarPlane}. */
    public static void markFogApplied() {
        fogApplied++;
    }

    public static long fogAppliedCount() {
        return fogApplied;
    }

    private static void spawnEdgeParticles(ClientLevel level, LocalPlayer player) {
        RandomSource random = level.getRandom();
        int budget = StormrootConfig.edgeParticlesPerTick.getAsInt();
        for (WeatherFront front : ClientFronts.snapshot()) {
            if (budget <= 0) {
                return;
            }
            double dx = player.getX() - front.x();
            double dz = player.getZ() - front.z();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double toEdge = Math.abs(dist - front.radius());
            if (dist < 1 || toEdge > EDGE_MAX_DISTANCE || front.lifeFactor() < 0.2) {
                continue;
            }
            // Punto del borde más cercano al jugador, y un arco de ±12 bloques alrededor.
            double nx = dx / dist;
            double nz = dz / dist;
            for (int i = 0; i < 2 && budget > 0; i++, budget--) {
                double along = (random.nextDouble() - 0.5) * 24;
                double px = front.x() + nx * front.radius() - nz * along;
                double pz = front.z() + nz * front.radius() + nx * along;
                spawnFor(level, front.type(), random, px, player.getY(), pz);
            }
        }
    }

    /** {@code true} mientras cae granizo sobre el jugador (para el self-test). */
    private static boolean hailHere;

    public static boolean hailHere() {
        return hailHere;
    }

    /** Granizo: piedrecitas blancas que caen rápido alrededor del jugador, en el núcleo de una tormenta de granizo. */
    private static void spawnHail(ClientLevel level, LocalPlayer player) {
        io.github.hcuadrado428.stormroot.client.sky.ClientSkySource sky = io.github.hcuadrado428.stormroot.client.sky.ClientSkySource.get();
        hailHere = sky.active() && io.github.hcuadrado428.stormroot.sky.SkyWeather.rainCloudAt(sky, player.getX(), player.getZ())
                == io.github.hcuadrado428.stormroot.sky.CloudCover.Kind.HAIL
                && level.getBiome(player.blockPosition()).value().hasPrecipitation()
                && level.getBiome(player.blockPosition()).value().getBaseTemperature() >= 0.15f;
        if (!hailHere) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < 8; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 24;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 24;
            double y = player.getY() + 6 + random.nextDouble() * 8;
            level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0, -0.8, 0);
        }
    }

    /**
     * Niebla que rueda: dentro de un banco de niebla, jirones bajos pegados al suelo que avanzan con el viento.
     * Más cuanto más espesa es la niebla.
     */
    private static void spawnRollingFog(ClientLevel level, LocalPlayer player) {
        if (fog < 0.15f) {
            return;
        }
        RandomSource random = level.getRandom();
        double wind = ClientFronts.windAngle();
        double speed = 0.04 + 0.03 * fog;
        double vx = Math.cos(wind) * speed;
        double vz = Math.sin(wind) * speed;
        int count = 1 + (int) (fog * 4);
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 40;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 40;
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(x), (int) Math.floor(z));
            if (Math.abs(ground - player.getY()) > 12) {
                continue;
            }
            level.addParticle(ParticleTypes.CLOUD, x, ground + 0.2 + random.nextDouble() * 1.2, z, vx, 0.002, vz);
        }
    }

    private static void spawnFor(ClientLevel level, FrontType type, RandomSource random, double x, double playerY, double z) {
        ParticleOptions particle;
        double y;
        double vy = 0;
        switch (type) {
            case THUNDERSTORM:
                particle = random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FALLING_WATER;
                y = playerY + 6 + random.nextDouble() * 10;
                break;
            case HEAT_WAVE:
                // Aire caliente: polvillo blanco que flota.
                particle = ParticleTypes.WHITE_ASH;
                y = playerY + random.nextDouble() * 6;
                vy = 0.01;
                break;
            case BLIZZARD:
                particle = ParticleTypes.SNOWFLAKE;
                y = playerY + 2 + random.nextDouble() * 10;
                vy = -0.05;
                break;
            case FOG:
                particle = ParticleTypes.CLOUD;
                y = playerY + random.nextDouble() * 4;
                vy = 0.005;
                break;
            case RAIN:
            default:
                particle = ParticleTypes.FALLING_WATER;
                y = playerY + 6 + random.nextDouble() * 10;
                break;
        }
        if (type == FrontType.FOG && !level.getBiome(net.minecraft.core.BlockPos.containing(x, playerY, z)).value().hasPrecipitation()) {
            return;
        }
        level.addParticle(particle, x, y, z, 0, vy, 0);
    }

    private static float approach(float current, float target) {
        if (current < target) {
            return Math.min(target, current + STEP);
        }
        return Math.max(target, current - STEP);
    }
}
