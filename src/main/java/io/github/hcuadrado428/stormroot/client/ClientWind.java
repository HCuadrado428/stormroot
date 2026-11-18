package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import io.github.hcuadrado428.stormroot.weather.Wind;
import io.github.hcuadrado428.stormroot.weather.WindRules;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * El viento en el cliente: le dice a {@link Wind} de dónde sacar el viento (el paquete del servidor), empuja un poco las
 * elytras y los barcos que lleva el jugador (su movimiento lo calcula el cliente) y suelta ráfagas visibles.
 */
public final class ClientWind {
    private static boolean registered;
    /** Última fuerza del viento donde está el jugador (0–1,5), para el HUD y el self-test. */
    private static double here;

    private ClientWind() {
    }

    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;
        Wind.setClientSource(new Wind.Source() {
            @Override
            public boolean active() {
                return ClientFronts.active();
            }

            @Override
            public double angle() {
                return ClientFronts.windAngle();
            }

            @Override
            public List<WeatherFront> fronts() {
                return ClientFronts.snapshot();
            }

            @Override
            public boolean projectiles() {
                return ClientFronts.windProjectiles();
            }

            @Override
            public boolean playerShots() {
                return ClientFronts.windPlayerShots();
            }
        });
    }

    public static double here() {
        return here;
    }

    public static void tick(ClientLevel level, LocalPlayer player) {
        ensureRegistered();
        Wind.Source source = Wind.sourceFor(level);
        here = Wind.strengthAt(level, source, player.position());
        if (here <= 0) {
            return;
        }
        double angle = ClientFronts.windAngle();
        double dx = WindRules.dirX(angle);
        double dz = WindRules.dirZ(angle);
        double accel = WindRules.travelAccel(here);
        Entity vehicle = player.getVehicle();
        if (player.isFallFlying()) {
            player.setDeltaMovement(player.getDeltaMovement().add(dx * accel, 0, dz * accel));
        } else if (isBoat(vehicle) && vehicle.getControllingPassenger() == player) {
            vehicle.setDeltaMovement(vehicle.getDeltaMovement().add(dx * accel * 0.5, 0, dz * accel * 0.5));
        }
        spawnGusts(level, player, here, dx, dz);
    }

    private static boolean isBoat(Entity vehicle) {
        //? if >=1.21.11 {
        return vehicle instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat;
        //?} else {
        /*return vehicle instanceof net.minecraft.world.entity.vehicle.Boat;
        *///?}
    }

    /** Con viento fuerte, ráfagas que cruzan delante del jugador en la dirección del viento. */
    private static void spawnGusts(ClientLevel level, LocalPlayer player, double wind, double dx, double dz) {
        if (wind < 0.5) {
            return;
        }
        RandomSource random = level.getRandom();
        if (random.nextDouble() > (wind - 0.4)) {
            return;
        }
        Vec3 at = player.position().add((random.nextDouble() - 0.5) * 16, random.nextDouble() * 3, (random.nextDouble() - 0.5) * 16);
        double speed = 0.15 + 0.25 * wind;
        level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, dx * speed, 0, dz * speed);
    }
}
