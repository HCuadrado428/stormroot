package io.github.hcuadrado428.stormroot.selftest;

import io.github.hcuadrado428.stormroot.weather.FrontCollisions;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.ArrayList;
import java.util.List;

/** Self-test de los choques entre frentes (reglas puras, una por caso). */
final class CollisionSelfTest {
    private CollisionSelfTest() {
    }

    private static WeatherFront front(long id, FrontType type, double x, double radius) {
        return new WeatherFront(id, type, x, 0, radius, 0, 0, 500, 1000);
    }

    private static List<WeatherFront> crash(WeatherFront... fronts) {
        List<WeatherFront> list = new ArrayList<>(List.of(fronts));
        long[] next = {100};
        FrontCollisions.resolve(list, () -> next[0]++);
        return list;
    }

    static void run() {
        List<WeatherFront> storm = crash(front(1, FrontType.HEAT_WAVE, 0, 200), front(2, FrontType.RAIN, 20, 120));
        boolean heatRain = storm.stream().anyMatch(f -> f.type() == FrontType.THUNDERSTORM)
                && storm.stream().noneMatch(f -> f.type() == FrontType.RAIN);

        WeatherFront blizzard = front(3, FrontType.BLIZZARD, 0, 120);
        crash(front(4, FrontType.HEAT_WAVE, 30, 200), blizzard);
        boolean heatSnow = blizzard.lifeFactor() < 1;

        WeatherFront big = front(5, FrontType.RAIN, 0, 150);
        List<WeatherFront> merged = crash(big, front(6, FrontType.RAIN, 30, 100));
        boolean merge = merged.size() == 1 && big.radius() > 150 && big.radius() <= FrontType.RAIN.maxRadius() * 1.3;

        WeatherFront fog = front(7, FrontType.FOG, 0, 100);
        crash(fog, front(8, FrontType.RAIN, 10, 120));
        boolean fogGone = fog.lifeFactor() < 1;

        WeatherFront sand = front(9, FrontType.SANDSTORM, 0, 120);
        crash(sand, front(10, FrontType.THUNDERSTORM, 10, 100));
        boolean sandSettles = sand.lifeFactor() < 1;

        WeatherFront aurora = front(11, FrontType.AURORA, 0, 200);
        WeatherFront rain = front(12, FrontType.RAIN, 0, 120);
        crash(aurora, rain);
        WeatherFront farA = front(13, FrontType.RAIN, 0, 100);
        WeatherFront farB = front(14, FrontType.FOG, 1000, 100);
        List<WeatherFront> far = crash(farA, farB);
        boolean untouched = aurora.lifeFactor() == 1 && rain.lifeFactor() == 1 && far.size() == 2 && farB.lifeFactor() == 1;

        SelfTest.check("fronts collide", heatRain && heatSnow && merge && fogGone && sandSettles && untouched,
                "calor+lluvia=" + heatRain + ", calor+ventisca=" + heatSnow + ", se funden=" + merge + ", niebla=" + fogGone
                        + ", arena=" + sandSettles + ", sin choque=" + untouched);
    }
}
