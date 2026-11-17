package io.github.hcuadrado428.stormroot.registry;

import io.github.hcuadrado428.stormroot.ModIds;
import io.github.hcuadrado428.stormroot.block.BarometerBlock;
import io.github.hcuadrado428.stormroot.block.WeathervaneBlock;
import java.util.function.Function;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

//? if >=1.21.2 {
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
//?}

/** Bloques del mod. La creación es común; el registro lo hace cada loader en Stormroot. */
public final class ModBlocks {
    public static final String WEATHERVANE_ID = "weathervane";
    public static final String BAROMETER_ID = "barometer";

    public static Block WEATHERVANE;
    public static Block BAROMETER;
    public static Item WEATHERVANE_ITEM;
    public static Item BAROMETER_ITEM;

    private ModBlocks() {
    }

    public static Block createWeathervane() {
        return create(WEATHERVANE_ID, WeathervaneBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.5f, 6.0f)
                        .sound(SoundType.COPPER).noOcclusion());
    }

    public static Block createBarometer() {
        return create(BAROMETER_ID, BarometerBlock::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f)
                        .sound(SoundType.WOOD).noOcclusion());
    }

    public static Item createBlockItem(String name, Block block) {
        Item.Properties properties = new Item.Properties();
        //? if >=1.21.2 {
        properties = properties.setId(ResourceKey.create(Registries.ITEM, ModIds.id(name)));
        //?}
        return new BlockItem(block, properties);
    }

    private static Block create(String name, Function<BlockBehaviour.Properties, Block> factory,
                                BlockBehaviour.Properties properties) {
        //? if >=1.21.2 {
        // Desde 1.21.2 cada bloque necesita conocer su clave antes de construirse.
        properties = properties.setId(ResourceKey.create(Registries.BLOCK, ModIds.id(name)));
        //?}
        return factory.apply(properties);
    }
}
