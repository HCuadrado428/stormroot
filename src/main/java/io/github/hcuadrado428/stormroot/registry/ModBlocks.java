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
    public static final String ICICLE_ID = "icicle";
    public static final String ICE_CRYSTAL_ID = "ice_crystal";
    public static final String MAGNETIC_ROCK_ID = "magnetic_rock";
    public static final String MYCELIUM_VEIN_ID = "mycelium_vein";
    public static final String MYCELIUM_NODE_ID = "mycelium_node";

    public static Block WEATHERVANE;
    public static Block BAROMETER;
    public static Item WEATHERVANE_ITEM;
    public static Item BAROMETER_ITEM;
    public static Block LIGHTNING_ROOT;
    public static Item LIGHTNING_ROOT_ITEM;
    /** Fuego del clima: sin objeto (no se puede coger). */
    public static Block STORM_FIRE;
    /** Carámbano y cristal de hielo de las cuevas heladas (con objeto, para el modo creativo). */
    public static Block ICICLE;
    public static Item ICICLE_ITEM;
    public static Block ICE_CRYSTAL;
    public static Item ICE_CRYSTAL_ITEM;
    public static Block MAGNETIC_ROCK;
    public static Item MAGNETIC_ROCK_ITEM;
    /** Red de micelio: hilos (sin objeto, como las venas de sculk sin toque de seda) y nodos. */
    public static Block MYCELIUM_VEIN;
    public static Block MYCELIUM_NODE;
    public static Item MYCELIUM_NODE_ITEM;

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

    public static Block createIcicle() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.5F)
                .sound(SoundType.GLASS).noOcclusion()
                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
        return create(ICICLE_ID, io.github.hcuadrado428.stormroot.block.IcicleBlock::new, properties);
    }

    public static Block createIceCrystal() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.3F)
                .sound(SoundType.AMETHYST_CLUSTER).noOcclusion().lightLevel(state -> 4)
                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
        return create(ICE_CRYSTAL_ID, io.github.hcuadrado428.stormroot.block.IceCrystalBlock::new, properties);
    }

    public static Block createMagneticRock() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F)
                .sound(SoundType.STONE).lightLevel(state -> 3);
        return create(MAGNETIC_ROCK_ID, io.github.hcuadrado428.stormroot.block.MagneticRockBlock::new, properties);
    }

    public static Block createMyceliumVein() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).replaceable()
                .strength(0.2F).sound(SoundType.SCULK_VEIN).randomTicks()
                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
        //? if >=1.21.11 {
        properties = properties.noCollision();
        //?} else {
        /*properties = properties.noCollission();
        *///?}
        return create(MYCELIUM_VEIN_ID, io.github.hcuadrado428.stormroot.block.MyceliumVeinBlock::new, properties);
    }

    public static Block createMyceliumNode() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.8F)
                .sound(SoundType.FUNGUS).lightLevel(state -> 6).randomTicks();
        return create(MYCELIUM_NODE_ID, io.github.hcuadrado428.stormroot.block.MyceliumNodeBlock::new, properties);
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
