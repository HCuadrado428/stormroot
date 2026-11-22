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
        // Tormenta de arena: solo sobre biomas secos (fuera, es calima: solo polvo fino y nubes anaranjadas).
        // Con gafas de arena se ve el doble de lejos.
        boolean dryHere = !level.getBiome(player.blockPosition()).value().hasPrecipitation();
        double sand = dryHere ? ClientFronts.intensity(FrontType.SANDSTORM, x, z) : 0;
        double sandFog = sand * (wearsGoggles(player) ? 0.45 : 0.9);
        fog = approach(fog, (float) Math.min(1.0, Math.max(Math.max(Math.max(mist, blizzard * 0.8), monsoon * 0.45), sandFog) * 2));
        // Solo los bancos de niebla de verdad sueltan jirones blancos (no la arena, la ventisca ni el monzón).
        mistFog = approach(mistFog, (float) Math.min(1.0, mist * 2));
        sandHere = sand;

        level.setRainLevel(rain);
        level.setThunderLevel(thunder);

        spawnEdgeParticles(level, player);
        spawnRollingFog(level, player);
        spawnHail(level, player);
        spawnCaveParticles(level, player);
        spawnFrost(level, player);
        spawnSand(level, player, sand, dryHere ? 0
                : io.github.hcuadrado428.stormroot.sky.CloudCover.calima(x, z, ClientFronts.snapshot()));
        ClientWind.tick(level, player);
    }

    /** Intensidad de la tormenta de arena donde está el jugador (0 fuera de los biomas secos). */
    private static double sandHere;
    /** Partículas de arena soltadas (para el self-test). */
    public static long sandParticles;

    public static double sandHere() {
        return sandHere;
    }

    /** Marrón de la arena para la niebla (0–1 por canal). */
    public static final float SAND_RED = 0.66F;
    public static final float SAND_GREEN = 0.50F;
    public static final float SAND_BLUE = 0.32F;
    private static long sandFogFrames;

    /** Cuánto se tiñe la niebla de marrón (0–1): fuerte en la tormenta, menos con gafas. */
    public static float sandFogTint() {
        double sand = sandForcedForTest >= 0 ? sandForcedForTest : sandHere;
        return (float) Math.min(0.85, sand * 1.2);
    }

    /** Para el self-test (el mundo de pruebas no es desierto): fuerza la arena de la niebla; -1 = normal. */
    public static volatile double sandForcedForTest = -1;

    public static float tint(float base, float target, float t) {
        return base * (1 - t) + target * t;
    }

    public static void markSandFog() {
        sandFogFrames++;
    }

    /** Fotogramas con la niebla teñida de arena (para el self-test). */
    public static long sandFogFrames() {
        return sandFogFrames;
    }

    public static boolean wearsGoggles(LocalPlayer player) {
        return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(io.github.hcuadrado428.stormroot.registry.ModItems.SAND_GOGGLES);
    }

    /** Arena que vuela con el viento dentro de la tormenta; fuera, calima: polvo fino que cae despacio. */
    private static void spawnSand(ClientLevel level, LocalPlayer player, double sand, double calima) {
        RandomSource random = level.getRandom();
        // Polvo ocre: arena roja y tierra. (El polvo de la arena normal sale casi blanco y parece nieve.)
        net.minecraft.core.particles.BlockParticleOption redSand = new net.minecraft.core.particles.BlockParticleOption(
                ParticleTypes.FALLING_DUST, net.minecraft.world.level.block.Blocks.RED_SAND.defaultBlockState());
        net.minecraft.core.particles.BlockParticleOption dirt = new net.minecraft.core.particles.BlockParticleOption(
                ParticleTypes.FALLING_DUST, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        int count = (int) Math.round(sand * 22);
        for (int i = 0; i < count; i++) {
            double[] at = awayFromCamera(random, player, 4, 12);
            level.addParticle(random.nextInt(3) == 0 ? dirt : redSand, at[0], player.getY() + random.nextDouble() * 5 - 1, at[1], 0, 0, 0);
            sandParticles++;
        }
        if (calima > 0.05 && random.nextDouble() < calima * 0.6) {
            double[] at = awayFromCamera(random, player, 6, 16);
            level.addParticle(dirt, at[0], player.getY() + 6 + random.nextDouble() * 6, at[1], 0, 0, 0);
            sandParticles++;
        }
    }

    /** Un punto al azar alrededor del jugador, entre {@code min} y {@code max} bloques (de cerca se ven enormes). */
    private static double[] awayFromCamera(RandomSource random, LocalPlayer player, double min, double max) {
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = min + random.nextDouble() * (max - min);
        return new double[] {player.getX() + Math.cos(angle) * dist, player.getZ() + Math.sin(angle) * dist};
    }

    /** ¿Hiela donde está el jugador? (mismas reglas que el servidor: {@code weather/Frost}). */
    public static boolean frostHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientFronts.active()) {
            return false;
        }
        double x = mc.player.getX();
        double z = mc.player.getZ();
        return io.github.hcuadrado428.stormroot.weather.FrostRules.freezing(ClientFronts.skySeed(), mc.level.getDayTime(),
                io.github.hcuadrado428.stormroot.weather.BiomeClimate.zoneAt(mc.level, x, z),
                io.github.hcuadrado428.stormroot.weather.Frost.cloudsAt(ClientFronts.snapshot(), x, z));
    }

    /** Escarcha: destellos blancos que se posan sobre la hierba y las hojas, al aire libre, mientras hiela. */
    private static void spawnFrost(ClientLevel level, LocalPlayer player) {
        RandomSource random = level.getRandom();
        if (random.nextInt(4) != 0 || !level.canSeeSky(player.blockPosition()) || !frostHere()) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            BlockPos column = player.blockPosition().offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8);
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, column);
            level.addParticle(ParticleTypes.SNOWFLAKE, top.getX() + random.nextDouble(), top.getY() + 0.1,
                    top.getZ() + random.nextDouble(), 0, 0.005, 0);
        }
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
        // Trocitos de hielo que caen rápido (los copos de nieve no parecían granizo).
        net.minecraft.core.particles.BlockParticleOption ice = new net.minecraft.core.particles.BlockParticleOption(
                ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState());
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 3 + random.nextDouble() * 12;
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            double y = player.getY() + 4 + random.nextDouble() * 8;
            level.addParticle(ice, x, y, z, 0, -1.2, 0);
        }
    }

    /**
     * Niebla que rueda: dentro de un banco de niebla, jirones bajos pegados al suelo que avanzan con el viento.
     * Más cuanto más espesa es la niebla.
     */
    /** Niebla de los frentes de niebla (sin la de la arena, la ventisca o el monzón), 0–1. */
    private static float mistFog;

    private static void spawnRollingFog(ClientLevel level, LocalPlayer player) {
        if (mistFog < 0.15f) {
            return;
        }
        RandomSource random = level.getRandom();
        double wind = ClientFronts.windAngle();
        double speed = 0.04 + 0.03 * mistFog;
        double vx = Math.cos(wind) * speed;
        double vz = Math.sin(wind) * speed;
        int count = 1 + (int) (mistFog * 4);
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

    /** Partículas de bruma de esporas soltadas (para el self-test). */
    public static int sporeParticles;

    private static void spawnFor(ClientLevel level, FrontType type, RandomSource random, double x, double playerY, double z) {
        ParticleOptions particle;
        double y;
        double vy = 0;
        switch (type) {
            case THUNDERSTORM:
                particle = random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FALLING_WATER;
                y = playerY + 6 + random.nextDouble() * 10;
                break;
            case MAGNETIC_STORM:
                // Chispas en el aire.
                particle = ParticleTypes.ELECTRIC_SPARK;
                y = playerY + 1 + random.nextDouble() * 6;
                vy = 0;
                break;
            case AURORA:
                // Destellos muy altos, solo de noche (de día parecerían copos blancos).
                long time = Math.floorMod(level.getDayTime(), 24000L);
                if (time < 13000 || time > 23000) {
                    return;
                }
                particle = ParticleTypes.END_ROD;
                y = playerY + 8 + random.nextDouble() * 12;
                vy = 0;
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
                // Sobre pantanos, manglares y campos de champiñones la niebla es bruma de esporas.
                if (io.github.hcuadrado428.stormroot.mycel.HostBiomes.isHostColumn(level, (int) Math.floor(x), (int) Math.floor(z))) {
                    particle = random.nextBoolean() ? ParticleTypes.SPORE_BLOSSOM_AIR : ParticleTypes.MYCELIUM;
                    sporeParticles++;
                } else {
                    particle = ParticleTypes.CLOUD;
                }
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
