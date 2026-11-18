package io.github.hcuadrado428.stormroot.weather;

import io.github.hcuadrado428.stormroot.config.StormrootConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * El viento en el mundo (las reglas puras están en {@link WindRules}). Se calcula igual en el servidor y en el cliente:
 * cada lado registra de dónde saca el viento ({@link Source}); el servidor, de la simulación; el cliente, del paquete.
 * Lo llama {@code mixin/ProjectileWindMixin} en cada tick de cada proyectil.
 */
public final class Wind {
    /** Lo que hace falta saber del viento en un lado (servidor o cliente). */
    public interface Source {
        boolean active();

        double angle();

        List<WeatherFront> fronts();

        boolean projectiles();

        boolean playerShots();
    }

    private static volatile Source server = new Source() {
        @Override
        public boolean active() {
            return FrontTicker.simulationIfRunning() != null && FrontTicker.controlsWeatherNow();
        }

        @Override
        public double angle() {
            FrontSimulation sim = FrontTicker.simulationIfRunning();
            return sim == null ? 0 : sim.windAngle();
        }

        @Override
        public List<WeatherFront> fronts() {
            FrontSimulation sim = FrontTicker.simulationIfRunning();
            return sim == null ? List.of() : sim.fronts();
        }

        @Override
        public boolean projectiles() {
            return StormrootConfig.windProjectilesActive();
        }

        @Override
        public boolean playerShots() {
            return StormrootConfig.windPlayerShotsActive();
        }
    };
    private static volatile Source client;
    /** Proyectiles empujados (para el self-test). */
    private static long pushes;

    private Wind() {
    }

    public static void setClientSource(Source source) {
        client = source;
    }

    public static Source sourceFor(Level level) {
        return level.isClientSide() ? client : server;
    }

    public static long pushes() {
        return pushes;
    }

    /** Fuerza del viento en esa posición, con el resguardo (techo, árboles, altura, terreno abierto). */
    public static double strengthAt(Level level, Source source, Vec3 pos) {
        if (source == null || !source.active() || level.dimension() != Level.OVERWORLD) {
            return 0;
        }
        BlockPos block = BlockPos.containing(pos);
        ClimateZone zone = BiomeClimate.zoneAt(level, pos.x, pos.z);
        boolean open = zone == ClimateZone.OCEAN || zone == ClimateZone.MOUNTAIN;
        double exposure = WindRules.exposure(level.canSeeSky(block), level.getBrightness(LightLayer.SKY, block), open,
                pos.y - level.getSeaLevel());
        if (exposure <= 0) {
            return 0;
        }
        return WindRules.strength(source.fronts(), pos.x, pos.z) * exposure;
    }

    public static WindRules.Projectile kindOf(Entity projectile) {
        EntityType<?> type = projectile.getType();
        if (type == EntityType.TRIDENT) {
            return WindRules.Projectile.TRIDENT;
        }
        if (type == EntityType.ARROW || type == EntityType.SPECTRAL_ARROW) {
            return WindRules.Projectile.ARROW;
        }
        if (type == EntityType.SNOWBALL || type == EntityType.EGG) {
            return WindRules.Projectile.LIGHT;
        }
        if (type == EntityType.ENDER_PEARL) {
            return WindRules.Projectile.ENDER_PEARL;
        }
        if (type == EntityType.EXPERIENCE_BOTTLE) {
            return WindRules.Projectile.THROWN;
        }
        return WindRules.Projectile.NONE;
    }

    /** Un tick de viento sobre un proyectil en vuelo. */
    public static void push(Projectile projectile) {
        Level level = projectile.level();
        Source source = sourceFor(level);
        if (source == null || !source.active() || !source.projectiles()) {
            return;
        }
        WindRules.Projectile kind = kindOf(projectile);
        if (kind == WindRules.Projectile.NONE) {
            return;
        }
        if (!source.playerShots() && projectile.getOwner() instanceof Player) {
            return;
        }
        Vec3 motion = projectile.getDeltaMovement();
        double accelBase = WindRules.projectileAccel(kind, projectile.tickCount, motion.length(), 1.0);
        if (accelBase <= 0) {
            return;
        }
        double wind = strengthAt(level, source, projectile.position());
        double accel = WindRules.projectileAccel(kind, projectile.tickCount, motion.length(), wind);
        if (accel <= 0) {
            return;
        }
        double angle = source.angle();
        projectile.setDeltaMovement(motion.add(WindRules.dirX(angle) * accel, 0, WindRules.dirZ(angle) * accel));
        pushes++;
        // Con viento fuerte, un rastro corto para que se vea que la flecha se va.
        if (level.isClientSide() && wind >= 0.5 && projectile.tickCount % 2 == 0) {
            level.addParticle(ParticleTypes.CLOUD, projectile.getX(), projectile.getY(), projectile.getZ(), 0, 0, 0);
        }
    }
}
