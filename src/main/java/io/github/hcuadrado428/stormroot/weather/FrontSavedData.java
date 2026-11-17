package io.github.hcuadrado428.stormroot.weather;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.hcuadrado428.stormroot.Stormroot;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

//? if >=1.21.5 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
*///?}

/**
 * Guarda en el mundo (fichero {@code data/stormroot_fronts.dat} del Overworld) el estado de
 * {@link FrontSimulation} y si Stormroot controla el clima vanilla.
 *
 * <p>La simulación sigue siendo Java puro: aquí solo se traduce su {@link FrontSimulation.State}
 * a un {@link Codec}. El mismo Codec sirve en todas las versiones; lo que cambia es cómo se registra:
 * <ul>
 *   <li>1.20.1: {@code computeIfAbsent(load, constructor, nombre)} + {@code save(CompoundTag)}</li>
 *   <li>1.20.5 – 1.21.4: {@code SavedData.Factory} + {@code save(CompoundTag, HolderLookup.Provider)}</li>
 *   <li>1.21.5+: {@code SavedDataType} con el Codec (vanilla serializa solo)</li>
 * </ul>
 * (1.20.2 – 1.20.4 no son targets del proyecto; su Factory recibía {@code Function<CompoundTag, T>}.)
 *
 * <p>Ojo: en vanilla {@code DataFixTypes} NO puede ser null (se llama a {@code update} sin comprobarlo),
 * así que se usa uno cuyas reglas no tocan nuestros campos.
 */
public final class FrontSavedData extends SavedData {
    public static final String ID = Stormroot.MOD_ID + "_fronts";

    private static final Codec<FrontType> TYPE_CODEC = Codec.STRING.comapFlatMap(id -> {
        FrontType type = FrontType.byId(id);
        return type != null
                ? DataResult.success(type)
                : DataResult.error(() -> "Tipo de frente desconocido: " + id);
    }, FrontType::id);

    private static final Codec<WeatherFront> FRONT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(WeatherFront::id),
            TYPE_CODEC.fieldOf("type").forGetter(WeatherFront::type),
            Codec.DOUBLE.fieldOf("x").forGetter(WeatherFront::x),
            Codec.DOUBLE.fieldOf("z").forGetter(WeatherFront::z),
            Codec.DOUBLE.fieldOf("radius").forGetter(WeatherFront::radius),
            Codec.DOUBLE.fieldOf("vx").forGetter(WeatherFront::vx),
            Codec.DOUBLE.fieldOf("vz").forGetter(WeatherFront::vz),
            Codec.DOUBLE.fieldOf("age").forGetter(WeatherFront::ageSeconds),
            Codec.DOUBLE.fieldOf("max_age").forGetter(WeatherFront::maxAgeSeconds)
    ).apply(i, WeatherFront::new));

    public static final Codec<FrontSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("wind_angle").forGetter(FrontSavedData::windAngle),
            Codec.LONG.fieldOf("next_id").forGetter(FrontSavedData::nextId),
            FRONT_CODEC.listOf().optionalFieldOf("fronts", List.of()).forGetter(FrontSavedData::fronts),
            Codec.BOOL.optionalFieldOf("controls_weather", true).forGetter(FrontSavedData::controlsWeather)
    ).apply(i, FrontSavedData::new));

    //? if >=1.21.5 {
    public static final SavedDataType<FrontSavedData> TYPE = new SavedDataType<>(
            ID, FrontSavedData::new, CODEC, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
    //?} else if >=1.20.5 {
    /*public static final SavedData.Factory<FrontSavedData> FACTORY = new SavedData.Factory<>(
            FrontSavedData::new, (tag, registries) -> load(tag), DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
    *///?}

    /** Simulación viva enlazada por {@link FrontTicker}; null hasta que se enlaza. */
    private FrontSimulation simulation;
    /** Estado leído del disco que aún no se ha aplicado a una simulación. */
    private FrontSimulation.State loaded;
    private boolean controlsWeather = true;

    public FrontSavedData() {
    }

    private FrontSavedData(double windAngle, long nextId, List<WeatherFront> fronts, boolean controlsWeather) {
        this.loaded = new FrontSimulation.State(windAngle, nextId, fronts);
        this.controlsWeather = controlsWeather;
    }

    /** Datos de Stormroot del nivel (normalmente el Overworld); los crea si no existen. */
    public static FrontSavedData get(ServerLevel level) {
        //? if >=1.21.5 {
        return level.getDataStorage().computeIfAbsent(TYPE);
        //?} else if >=1.20.5 {
        /*return level.getDataStorage().computeIfAbsent(FACTORY, ID);
        *///?} else {
        /*return level.getDataStorage().computeIfAbsent(FrontSavedData::load, FrontSavedData::new, ID);
        *///?}
    }

    /** Enlaza la simulación viva y le aplica el estado guardado, si lo había. */
    public void attach(FrontSimulation sim) {
        if (loaded != null) {
            sim.restoreState(loaded);
            loaded = null;
        }
        simulation = sim;
    }

    public FrontSimulation simulation() {
        return simulation;
    }

    public boolean controlsWeather() {
        return controlsWeather;
    }

    public void setControlsWeather(boolean enabled) {
        controlsWeather = enabled;
        setDirty();
    }

    private FrontSimulation.State state() {
        if (simulation != null) {
            return simulation.exportState();
        }
        return loaded != null ? loaded : new FrontSimulation.State(0, 1, List.of());
    }

    private double windAngle() {
        return state().windAngle();
    }

    private long nextId() {
        return state().nextId();
    }

    private List<WeatherFront> fronts() {
        return state().fronts();
    }

    //? if <1.21.5 {
    /*static FrontSavedData load(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(error -> Stormroot.LOGGER.error("[Stormroot] No se pudieron leer los frentes: {}", error))
                .orElseGet(FrontSavedData::new);
    }

    private CompoundTag write(CompoundTag tag) {
        CODEC.encodeStart(NbtOps.INSTANCE, this)
                .resultOrPartial(error -> Stormroot.LOGGER.error("[Stormroot] No se pudieron guardar los frentes: {}", error))
                .ifPresent(encoded -> {
                    if (encoded instanceof CompoundTag compound) {
                        tag.merge(compound);
                    }
                });
        return tag;
    }
    *///?}

    //? if >=1.21.5 {
    // 1.21.5+: no hay save(); vanilla guarda con CODEC (ver TYPE).
    //?} else if >=1.20.5 {
    /*@Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        return write(tag);
    }
    *///?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
        return write(tag);
    }
    *///?}
}
