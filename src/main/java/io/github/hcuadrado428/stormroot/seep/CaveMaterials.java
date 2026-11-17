package io.github.hcuadrado428.stormroot.seep;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bloques naturales de cueva que la filtración puede cambiar, con un id corto para guardar "qué había antes".
 * Si un bloque no está aquí, la filtración no lo toca (así nunca cambia bloques de jugadores ni minerales).
 */
public final class CaveMaterials {
    private static final Map<String, Block> BY_ID = new LinkedHashMap<>();

    static {
        BY_ID.put("stone", Blocks.STONE);
        BY_ID.put("deepslate", Blocks.DEEPSLATE);
        BY_ID.put("tuff", Blocks.TUFF);
        BY_ID.put("granite", Blocks.GRANITE);
        BY_ID.put("diorite", Blocks.DIORITE);
        BY_ID.put("andesite", Blocks.ANDESITE);
        BY_ID.put("calcite", Blocks.CALCITE);
        BY_ID.put("dirt", Blocks.DIRT);
        BY_ID.put("coarse_dirt", Blocks.COARSE_DIRT);
        BY_ID.put("rooted_dirt", Blocks.ROOTED_DIRT);
        BY_ID.put("mud", Blocks.MUD);
        BY_ID.put("gravel", Blocks.GRAVEL);
    }

    /** Roca: puede tener charcos y musgo. */
    public static final Set<String> ROCK = Set.of("stone", "deepslate", "tuff", "granite", "diorite", "andesite", "calcite");
    /** Tierra: se vuelve barro con humedad. */
    public static final Set<String> SOIL = Set.of("dirt", "coarse_dirt", "rooted_dirt");
    /** Se agrieta con la sequía. */
    public static final Set<String> CRACKABLE = Set.of("dirt", "rooted_dirt", "mud");
    /** Puede tener charcos: roca, grava y barro. La tierra primero se vuelve barro (y luego puede encharcarse). */
    public static final Set<String> PUDDLE_FLOOR = Set.of("stone", "deepslate", "tuff", "granite", "diorite", "andesite",
            "calcite", "mud", "gravel");

    private CaveMaterials() {
    }

    /** Id del material, o {@code null} si la filtración no puede tocar ese bloque. */
    public static String idOf(BlockState state) {
        for (Map.Entry<String, Block> entry : BY_ID.entrySet()) {
            if (state.is(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Estado a restaurar para un id guardado; aire si el id está vacío o no se conoce. */
    public static BlockState restore(String id) {
        Block block = id == null ? null : BY_ID.get(id);
        return block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
    }
}
