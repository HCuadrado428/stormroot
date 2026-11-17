package io.github.hcuadrado428.stormroot.client.sky;

import io.github.hcuadrado428.stormroot.client.ClientFronts;
import io.github.hcuadrado428.stormroot.sky.SkyWeather;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

/** Fuente del cielo del cliente para {@link SkyWeather}: gotas y partículas de lluvia solo bajo los nubarrones. */
public final class ClientSkySource implements SkyWeather.Source {
    private static final ClientSkySource INSTANCE = new ClientSkySource();
    private static boolean registered;

    private ClientSkySource() {
    }

    public static ClientSkySource get() {
        return INSTANCE;
    }

    /** Lo llama {@code ClientWeather.tick()}; registra la fuente una vez. */
    public static void ensureRegistered() {
        if (!registered) {
            registered = true;
            SkyWeather.setClient(Thread.currentThread(), INSTANCE);
        }
    }

    @Override
    public boolean active() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimension() == Level.OVERWORLD && ClientFronts.active();
    }

    @Override
    public long seed() {
        return ClientFronts.skySeed();
    }

    @Override
    public double windAngle() {
        return ClientFronts.windAngle();
    }

    @Override
    public double seconds() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() / 20.0;
    }

    @Override
    public List<WeatherFront> fronts() {
        return ClientFronts.live();
    }
}
