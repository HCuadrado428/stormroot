package io.github.hcuadrado428.stormroot.registry;

import io.github.hcuadrado428.stormroot.ModIds;
import io.github.hcuadrado428.stormroot.block.BarometerBlock;
import io.github.hcuadrado428.stormroot.block.StormFireBlock;
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
    public static final String LIGHTNING_ROOT_ID = "lightning_root";
    public static final String STORM_FIRE_ID = "storm_fire";

    public static Block WEATHERVANE;
    public static Block BAROMETER;
    public static Item WEATHERVANE_ITEM;
    public static Item BAROMETER_ITEM;
    public static Block LIGHTNING_ROOT;
    public static Item LIGHTNING_ROOT_ITEM;
    /** Fuego del clima: sin objeto (no se puede coger). */
    public static Block STORM_FIRE;

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

    /** Columna de cristal que deja un rayo al crecer hacia abajo. Da luz. */
    public static Block createLightningRoot() {
        return create(LIGHTNING_ROOT_ID, Block::new,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5f)
                        .sound(SoundType.AMETHYST).lightLevel(state -> 7));
    }

    /** Fuego del clima: como el fuego vanilla (sin colisión, se rompe al tocarlo, da luz) pero solo en hojas. */
    public static Block createStormFire() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).replaceable()
                .instabreak().lightLevel(state -> 12).sound(SoundType.WOOL)
                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
        //? if >=1.21.11 {
        properties = properties.noCollision();
        //?} else {
        /*properties = properties.noCollission();
        *///?}
        return create(STORM_FIRE_ID, StormFireBlock::new, properties);
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
