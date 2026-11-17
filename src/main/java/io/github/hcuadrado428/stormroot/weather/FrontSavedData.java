package io.github.hcuadrado428.stormroot.weather;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.hcuadrado428.stormroot.config.WorldSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

//? if >=1.21.5 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?} else if >=1.20.5 {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
*///?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
*///?}

/**
 * Guarda el estado de los frentes del Overworld en {@code data/stormroot_fronts.dat} del mundo.
 *
 * <p>El formato es el mismo en todas las versiones: un {@link Codec}. Lo que cambia es cómo se engancha
 * a Minecraft:
 * <ul>
 *   <li>1.20.1: {@code computeIfAbsent(load, new, nombre)} + {@code save(CompoundTag)}</li>
 *   <li>1.20.5 – 1.21.4: {@code SavedData.Factory} + {@code save(CompoundTag, HolderLookup.Provider)}</li>
 *   <li>1.21.5+: {@code SavedDataType} con el Codec (sin {@code save})</li>
 * </ul>
 * (Los targets del proyecto son 1.20.1, 1.21.1 y 1.21.11; 1.20.2–1.20.4 usarían Factory sin Provider.)
 */
public class FrontSavedData extends SavedData {
    public static final String NAME = "stormroot_fronts";

    private static final Codec<FrontType> FRONT_TYPE_CODEC = Codec.STRING.xmap(
            id -> {
                FrontType type = FrontType.byId(id);
                return type != null ? type : FrontType.RAIN;
            },
            FrontType::id);

    private static final Codec<WeatherFront> FRONT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(WeatherFront::id),
            FRONT_TYPE_CODEC.fieldOf("type").forGetter(WeatherFront::type),
            Codec.DOUBLE.fieldOf("x").forGetter(WeatherFront::x),
            Codec.DOUBLE.fieldOf("z").forGetter(WeatherFront::z),
            Codec.DOUBLE.fieldOf("radius").forGetter(WeatherFront::radius),
            Codec.DOUBLE.fieldOf("vx").forGetter(WeatherFront::vx),
            Codec.DOUBLE.fieldOf("vz").forGetter(WeatherFront::vz),
            Codec.DOUBLE.fieldOf("age").forGetter(WeatherFront::ageSeconds),
            Codec.DOUBLE.fieldOf("max_age").forGetter(WeatherFront::maxAgeSeconds)
    ).apply(i, WeatherFront::new));

    public static final Codec<FrontSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            FRONT_CODEC.listOf().fieldOf("fronts").forGetter(d -> d.fronts),
            Codec.DOUBLE.fieldOf("wind_angle").forGetter(d -> d.windAngle),
            Codec.LONG.fieldOf("next_id").forGetter(d -> d.nextId),
            Codec.BOOL.fieldOf("controls_weather").forGetter(d -> d.controlsWeather),
            Codec.BOOL.fieldOf("initialized").forGetter(d -> d.initialized),
            Codec.BOOL.optionalFieldOf("seepage", true).forGetter(d -> d.seepage),
            Codec.BOOL.optionalFieldOf("cave_changes", true).forGetter(d -> d.caveChanges),
            Codec.BOOL.optionalFieldOf("lightning_roots", true).forGetter(d -> d.lightningRoots)
    ).apply(i, FrontSavedData::new));

    //? if >=1.21.5 {
    public static final SavedDataType<FrontSavedData> TYPE =
            new SavedDataType<>(NAME, FrontSavedData::new, CODEC, null);
    //?} else if >=1.20.5 {
    /*public static final SavedData.Factory<FrontSavedData> FACTORY =
            new SavedData.Factory<>(FrontSavedData::new, (tag, registries) -> load(tag), null);
    *///?}

    private final List<WeatherFront> fronts;
    private double windAngle;
    private long nextId;
    private boolean controlsWeather;
    /** {@code false} hasta que se guarda por primera vez: así sabemos si hay que usar el viento inicial aleatorio. */
    private boolean initialized;
    /** Ajustes del mundo ({@link WorldSettings}). */
    private boolean seepage;
    private boolean caveChanges;
    private boolean lightningRoots;

    public FrontSavedData() {
        this(new ArrayList<>(), 0.0, 1L, true, false, true, true, true);
    }

    private FrontSavedData(List<WeatherFront> fronts, double windAngle, long nextId, boolean controlsWeather,
                           boolean initialized, boolean seepage, boolean caveChanges, boolean lightningRoots) {
        this.fronts = new ArrayList<>(fronts);
        this.windAngle = windAngle;
        this.nextId = nextId;
        this.controlsWeather = controlsWeather;
        this.initialized = initialized;
        this.seepage = seepage;
        this.caveChanges = caveChanges;
        this.lightningRoots = lightningRoots;
    }

    /** Datos guardados del Overworld (los crea vacíos si el mundo aún no tiene). */
    public static FrontSavedData get(MinecraftServer server) {
        //? if >=1.21.5 {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        //?} else if >=1.20.5 {
        /*return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        *///?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(FrontSavedData::load, FrontSavedData::new, NAME);
        *///?}
    }

    /** Copia el estado actual de la simulación y marca los datos para guardarlos. */
    public void capture(FrontSimulation simulation, boolean controlsWeather) {
        this.fronts.clear();
        this.fronts.addAll(simulation.fronts());
        this.windAngle = simulation.windAngle();
        this.nextId = simulation.nextId();
        this.controlsWeather = controlsWeather;
        this.initialized = true;
        this.seepage = WorldSettings.seepage;
        this.caveChanges = WorldSettings.caveChanges;
        this.lightningRoots = WorldSettings.lightningRoots;
        setDirty();
    }

    /** Pasa el estado guardado a la simulación (si había algo guardado). */
    public boolean restoreInto(FrontSimulation simulation) {
        if (!initialized) {
            return false;
        }
        simulation.restore(windAngle, nextId, fronts);
        WorldSettings.seepage = seepage;
        WorldSettings.caveChanges = caveChanges;
        WorldSettings.lightningRoots = lightningRoots;
        return true;
    }

    public boolean controlsWeather() {
        return controlsWeather;
    }

    public boolean isInitialized() {
        return initialized;
    }

    //? if <1.21.5 {
    /*private static FrontSavedData load(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag.get("data")).result().orElseGet(FrontSavedData::new);
    }

    private CompoundTag write(CompoundTag tag) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).result().ifPresent(encoded -> tag.put("data", encoded));
        return tag;
    }
    *///?}

    //? if >=1.20.5 <1.21.5 {
    /*@Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return write(tag);
    }
    *///?}

    //? if <1.20.5 {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
        return write(tag);
    }
    *///?}
}
