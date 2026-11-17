package io.github.hcuadrado428.stormroot.seep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Guarda el {@link SeepageField} del Overworld en {@code data/stormroot_seepage.dat}.
 * Mismo enganche por versión que {@code FrontSavedData} (ver allí).
 */
public class SeepageSavedData extends SavedData {
    public static final String NAME = "stormroot_seepage";

    private static final Codec<SeepageCell.Change> CHANGE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("pos").forGetter(SeepageCell.Change::pos),
            Codec.STRING.xmap(SeepageCell.Kind::byId, SeepageCell.Kind::id).fieldOf("kind").forGetter(SeepageCell.Change::kind),
            Codec.STRING.optionalFieldOf("original", "").forGetter(SeepageCell.Change::original)
    ).apply(i, SeepageCell.Change::new));

    private record CellEntry(long key, double surface, double deep, double frost, List<SeepageCell.Change> changes) {
    }

    private static final Codec<CellEntry> CELL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("chunk").forGetter(CellEntry::key),
            Codec.DOUBLE.fieldOf("surface").forGetter(CellEntry::surface),
            Codec.DOUBLE.fieldOf("deep").forGetter(CellEntry::deep),
            Codec.DOUBLE.optionalFieldOf("frost", 0.0).forGetter(CellEntry::frost),
            CHANGE_CODEC.listOf().fieldOf("changes").forGetter(CellEntry::changes)
    ).apply(i, CellEntry::new));

    public static final Codec<SeepageSavedData> CODEC = CELL_CODEC.listOf().xmap(
            SeepageSavedData::fromEntries, SeepageSavedData::toEntries).fieldOf("cells").codec();

    //? if >=1.21.5 {
    public static final SavedDataType<SeepageSavedData> TYPE =
            new SavedDataType<>(NAME, SeepageSavedData::new, CODEC, null);
    //?} else if >=1.20.5 {
    /*public static final SavedData.Factory<SeepageSavedData> FACTORY =
            new SavedData.Factory<>(SeepageSavedData::new, (tag, registries) -> load(tag), null);
    *///?}

    private final SeepageField field = new SeepageField();

    public SeepageSavedData() {
    }

    public static SeepageSavedData get(MinecraftServer server) {
        //? if >=1.21.5 {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        //?} else if >=1.20.5 {
        /*return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        *///?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(SeepageSavedData::load, SeepageSavedData::new, NAME);
        *///?}
    }

    public SeepageField field() {
        return field;
    }

    /** Marcar como modificado después de cambiar {@link #field()}. */
    public void changed() {
        setDirty();
    }

    private static SeepageSavedData fromEntries(List<CellEntry> entries) {
        SeepageSavedData data = new SeepageSavedData();
        for (CellEntry e : entries) {
            data.field.put(e.key(), new SeepageCell(e.surface(), e.deep(), e.frost(), e.changes()));
        }
        return data;
    }

    private static List<CellEntry> toEntries(SeepageSavedData data) {
        data.field.prune();
        List<CellEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, SeepageCell> e : data.field.cells().entrySet()) {
            SeepageCell c = e.getValue();
            entries.add(new CellEntry(e.getKey(), c.surface(), c.deep(), c.frost(), new ArrayList<>(c.changes())));
        }
        return entries;
    }

    //? if <1.21.5 {
    /*private static SeepageSavedData load(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag.get("data")).result().orElseGet(SeepageSavedData::new);
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
