package io.github.hcuadrado428.stormroot.selftest;

import io.github.hcuadrado428.stormroot.config.WorldSettings;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import io.github.hcuadrado428.stormroot.weather.WindRules;
import java.util.List;

/** Self-test del viento (reglas puras: fuerza, resguardo y desvío de una flecha a 40 bloques). */
final class WindSelfTest {
    private WindSelfTest() {
    }

    static void run() {
        WeatherFront storm = new WeatherFront(1, FrontType.THUNDERSTORM, 0, 0, 100, 0, 0, 500, 1000);
        List<WeatherFront> fronts = List.of(storm);
        double calm = WindRules.strength(List.of(), 0, 0);
        double inside = WindRules.strength(fronts, 50, 0);
        double ahead = WindRules.strength(fronts, 125, 0);
        double far = WindRules.strength(fronts, 400, 0);
        boolean strength = calm == WindRules.BASE && inside == 1.0 && ahead > WindRules.BASE && ahead < 1 && far == WindRules.BASE;
        SelfTest.check("wind is stronger near storms", strength,
                "calma=" + calm + ", dentro=" + inside + ", delante=" + ahead + ", lejos=" + far);

        boolean shelter = WindRules.exposure(false, 0, false, 0) == 0 && WindRules.exposure(false, 14, false, 0) == 0.5
                && WindRules.exposure(true, 15, false, 0) == 1 && WindRules.exposure(true, 15, true, 80) > 1;
        SelfTest.check("roofs and trees shelter from the wind", shelter, "resguardo mal");

        double calmShot = deviation(WindRules.Projectile.ARROW, WindRules.BASE);
        double stormShot = deviation(WindRules.Projectile.ARROW, 1.0);
        double pearl = deviation(WindRules.Projectile.ENDER_PEARL, 1.0);
        double close = deviationAt(WindRules.Projectile.ARROW, 1.0, 15);
        boolean shots = close == 0 && calmShot > 0.1 && calmShot < 0.45 && stormShot > 0.6 && stormShot < 1.6 && pearl < 0.15;
        SelfTest.check("wind bends long shots only", shots, "a 15 bloques=" + close + ", a 40 con calma=" + calmShot
                + ", con tormenta=" + stormShot + ", perla=" + pearl);
        SelfTest.check("wind settings default to soft", WorldSettings.windProjectiles && WorldSettings.windPlayerShots,
                "proyectiles=" + WorldSettings.windProjectiles + ", jugadores=" + WorldSettings.windPlayerShots);
    }

    private static double deviation(WindRules.Projectile kind, double wind) {
        return deviationAt(kind, wind, 40);
    }

    /** Desvío lateral (bloques) de un tiro con el arco tenso al recorrer {@code distance} bloques con viento de lado. */
    private static double deviationAt(WindRules.Projectile kind, double wind, double distance) {
        double x = 0;
        double vx = 3.0;
        double z = 0;
        double vz = 0;
        for (int tick = 0; tick < 200 && x < distance; tick++) {
            vz += WindRules.projectileAccel(kind, tick, Math.hypot(vx, vz), wind);
            x += vx;
            z += vz;
            vx *= 0.99;
            vz *= 0.99;
        }
        return Math.abs(z);
    }
}
