package io.github.hcuadrado428.stormroot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.hcuadrado428.stormroot.seep.SeepageCell;
import io.github.hcuadrado428.stormroot.seep.SeepageSavedData;
import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Comandos de depuración (solo vanilla + Brigadier). Cada loader llama a {@link #register}.
 *
 * <pre>
 * /stormroot front list              lista los frentes activos
 * /stormroot front spawn rain|...    crea un frente centrado en ti
 * /stormroot front clear             borra todos los frentes
 * /stormroot here                    qué frente hay donde estás y el viento
 * /stormroot weather on|off          Stormroot controla (o no) el clima vanilla
 * /stormroot seep                    humedad del suelo/subsuelo de tu chunk
 * /stormroot seep set <-1..1>        fija la humedad de tu chunk (para probar charcos y sequía)
 * </pre>
 */
public final class StormrootCommands {
    private StormrootCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
        for (FrontType type : FrontType.values()) {
            spawn.then(Commands.literal(type.id()).executes(ctx -> spawn(ctx.getSource(), type)));
        }

        dispatcher.register(Commands.literal("stormroot")
                //? if >=1.21.11 {
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                //?} else {
                /*.requires(source -> source.hasPermission(2))
                *///?}
                .then(Commands.literal("front")
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(spawn)
                        .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource()))))
                .then(Commands.literal("here").executes(ctx -> here(ctx.getSource())))
                .then(Commands.literal("seep")
                        .executes(ctx -> seep(ctx.getSource()))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(-1, 1))
                                        .executes(ctx -> setSeep(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "value"))))))
                .then(Commands.literal("weather")
                        .then(Commands.literal("on").executes(ctx -> setWeatherControl(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setWeatherControl(ctx.getSource(), false)))));
    }

    private static int list(CommandSourceStack source) {
        FrontSimulation sim = FrontTicker.simulation(source.getServer());
        if (sim.fronts().isEmpty()) {
            reply(source, "No hay frentes activos.");
            return 0;
        }
        reply(source, sim.fronts().size() + " frente(s) activo(s):");
        for (WeatherFront front : sim.fronts()) {
            reply(source, " - " + front);
        }
        return sim.fronts().size();
    }

    private static int spawn(CommandSourceStack source, FrontType type) {
        Vec3 pos = source.getPosition();
        WeatherFront front = FrontTicker.simulation(source.getServer()).create(type, pos.x, pos.z);
        FrontTicker.simulation(source.getServer()).add(front);
        FrontTicker.applyNow(source.getServer());
        FrontTicker.save(source.getServer());
        reply(source, "Creado " + front);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        FrontSimulation sim = FrontTicker.simulation(source.getServer());
        int count = sim.fronts().size();
        sim.clear();
        FrontTicker.applyNow(source.getServer());
        FrontTicker.save(source.getServer());
        reply(source, "Borrados " + count + " frente(s).");
        return count;
    }

    private static int here(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        FrontSimulation sim = FrontTicker.simulation(source.getServer());
        double windDegrees = Math.toDegrees(sim.windAngle()) % 360;
        if (windDegrees < 0) {
            windDegrees += 360;
        }
        WeatherFront front = sim.strongestAt(pos.x, pos.z);
        if (front == null) {
            reply(source, String.format(Locale.ROOT, "Cielo despejado. Viento hacia %.0f°.", windDegrees));
            return 0;
        }
        reply(source, String.format(Locale.ROOT, "Dentro de %s (intensidad %.0f %%). Viento hacia %.0f°.",
                front, front.intensityAt(pos.x, pos.z) * 100, windDegrees));
        return 1;
    }

    private static int setWeatherControl(CommandSourceStack source, boolean enabled) {
        FrontTicker.simulation(source.getServer()); // carga lo guardado antes de cambiar el flag
        FrontTicker.setControlsWeather(enabled);
        FrontTicker.save(source.getServer());
        if (enabled) {
            FrontTicker.applyNow(source.getServer());
        }
        reply(source, enabled
                ? "Stormroot controla el clima del Overworld."
                : "Stormroot ya no toca el clima vanilla (los frentes siguen simulándose).");
        return 1;
    }

    private static int seep(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        int cx = (int) Math.floor(pos.x) >> 4;
        int cz = (int) Math.floor(pos.z) >> 4;
        SeepageCell cell = SeepageSavedData.get(source.getServer()).field().get(cx, cz);
        if (cell == null) {
            reply(source, String.format(Locale.ROOT, "Chunk %d,%d: sin registro (neutro).", cx, cz));
            return 0;
        }
        reply(source, String.format(Locale.ROOT, "Chunk %d,%d: suelo %.2f, subsuelo %.2f, %d bloque(s) cambiados.",
                cx, cz, cell.surface(), cell.deep(), cell.changes().size()));
        return 1;
    }

    private static int setSeep(CommandSourceStack source, double value) {
        Vec3 pos = source.getPosition();
        int cx = (int) Math.floor(pos.x) >> 4;
        int cz = (int) Math.floor(pos.z) >> 4;
        SeepageSavedData data = SeepageSavedData.get(source.getServer());
        data.field().getOrCreate(cx, cz).set(value);
        data.changed();
        reply(source, String.format(Locale.ROOT, "Chunk %d,%d: suelo y subsuelo = %.2f.", cx, cz, value));
        return 1;
    }

    private static void reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
