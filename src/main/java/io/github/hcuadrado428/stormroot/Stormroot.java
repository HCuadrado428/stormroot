package io.github.hcuadrado428.stormroot;

import io.github.hcuadrado428.stormroot.client.StormrootClient;
import io.github.hcuadrado428.stormroot.command.StormrootCommands;
import io.github.hcuadrado428.stormroot.compat.Compat;
import io.github.hcuadrado428.stormroot.network.StormrootNetwork;
import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.registry.ModItems;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import io.github.hcuadrado428.stormroot.weather.LightningCrystals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*? if fabric {*/
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
/*?}*/

/*? if forge {*/
/*import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;
*//*?}*/

/*? if neoforge {*/
/*import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
*//*?}*/

/**
 * Punto de entrada del mod para los tres loaders.
 * El código común vive en otras clases (ModIds, ModItems, paquetes weather y command);
 * aquí solo va el "pegamento" de cada loader.
 */
/*? if neoforge {*/
/*@Mod(Stormroot.MOD_ID)
*//*?}*/
/*? if forge {*/
/*@Mod(Stormroot.MOD_ID)
*//*?}*/
public class Stormroot /*? if fabric {*/ implements ModInitializer /*?}*/ {
    public static final String MOD_ID = "stormroot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /*? if forge {*/
    /*public Stormroot() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(Stormroot::onRegister);
        modBus.addListener(Stormroot::onCreativeTabs);
        MinecraftForge.EVENT_BUS.addListener(Stormroot::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(Stormroot::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(Stormroot::onEntityJoin);
        modBus.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(io.github.hcuadrado428.stormroot.entity.ModEntities.sporeling(), io.github.hcuadrado428.stormroot.entity.Sporeling.createAttributes().build());
            e.put(io.github.hcuadrado428.stormroot.entity.ModEntities.weaver(), io.github.hcuadrado428.stormroot.entity.Weaver.createAttributes().build());
            e.put(io.github.hcuadrado428.stormroot.entity.ModEntities.guardian(), io.github.hcuadrado428.stormroot.entity.MyceliumGuardian.createAttributes().build());
        });
        StormrootNetwork.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            StormrootClient.register();
        }
        Compat.initBloomberto(ModList.get().isLoaded("bloomberto"), FMLEnvironment.dist == Dist.CLIENT);
        LOGGER.info("Stormroot cargado en Forge");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.SPORELING_ID), io.github.hcuadrado428.stormroot.entity.ModEntities::sporeling);
        event.register(Registries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.WEAVER_ID), io.github.hcuadrado428.stormroot.entity.ModEntities::weaver);
        event.register(Registries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.GUARDIAN_ID), io.github.hcuadrado428.stormroot.entity.ModEntities::guardian);
        for (String soundId : io.github.hcuadrado428.stormroot.registry.ModSounds.IDS) {
            event.register(Registries.SOUND_EVENT, ModIds.id(soundId), () -> io.github.hcuadrado428.stormroot.registry.ModSounds.get(soundId));
        }
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.WEATHERVANE_ID),
                () -> ModBlocks.WEATHERVANE = ModBlocks.createWeathervane());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.BAROMETER_ID),
                () -> ModBlocks.BAROMETER = ModBlocks.createBarometer());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                () -> ModBlocks.LIGHTNING_ROOT = ModBlocks.createLightningRoot());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.STORM_FIRE_ID),
                () -> ModBlocks.STORM_FIRE = ModBlocks.createStormFire());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.ICICLE_ID),
                () -> ModBlocks.ICICLE = ModBlocks.createIcicle());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.ICE_CRYSTAL_ID),
                () -> ModBlocks.ICE_CRYSTAL = ModBlocks.createIceCrystal());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.MAGNETIC_ROCK_ID),
                () -> ModBlocks.MAGNETIC_ROCK = ModBlocks.createMagneticRock());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.MYCELIUM_VEIN_ID),
                () -> ModBlocks.MYCELIUM_VEIN = ModBlocks.createMyceliumVein());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.MYCELIUM_NODE_ID),
                () -> ModBlocks.MYCELIUM_NODE = ModBlocks.createMyceliumNode());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.FROST_ICE_ID),
                () -> ModBlocks.FROST_ICE = ModBlocks.createFrostIce());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.SAND_LAYER_ID),
                () -> ModBlocks.SAND_LAYER = ModBlocks.createSandLayer());
        event.register(Registries.ITEM, ModIds.id("lightning_crystal"),
                () -> ModItems.LIGHTNING_CRYSTAL = ModItems.create("lightning_crystal"));
        event.register(Registries.ITEM, ModIds.id("hailstone"),
                () -> ModItems.HAILSTONE = ModItems.create("hailstone"));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.WEATHERVANE_ID),
                () -> ModBlocks.WEATHERVANE_ITEM = ModBlocks.createBlockItem(ModBlocks.WEATHERVANE_ID, ModBlocks.WEATHERVANE));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.BAROMETER_ID),
                () -> ModBlocks.BAROMETER_ITEM = ModBlocks.createBlockItem(ModBlocks.BAROMETER_ID, ModBlocks.BAROMETER));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                () -> ModBlocks.LIGHTNING_ROOT_ITEM = ModBlocks.createBlockItem(ModBlocks.LIGHTNING_ROOT_ID, ModBlocks.LIGHTNING_ROOT));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.ICICLE_ID),
                () -> ModBlocks.ICICLE_ITEM = ModBlocks.createBlockItem(ModBlocks.ICICLE_ID, ModBlocks.ICICLE));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.ICE_CRYSTAL_ID),
                () -> ModBlocks.ICE_CRYSTAL_ITEM = ModBlocks.createBlockItem(ModBlocks.ICE_CRYSTAL_ID, ModBlocks.ICE_CRYSTAL));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.MAGNETIC_ROCK_ID),
                () -> ModBlocks.MAGNETIC_ROCK_ITEM = ModBlocks.createBlockItem(ModBlocks.MAGNETIC_ROCK_ID, ModBlocks.MAGNETIC_ROCK));
        event.register(Registries.ITEM, ModIds.id("aurora_dust"), () -> ModItems.AURORA_DUST = ModItems.create("aurora_dust"));
        event.register(Registries.ITEM, ModIds.id("rainbow_shard"), () -> ModItems.RAINBOW_SHARD = ModItems.create("rainbow_shard"));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.MYCELIUM_NODE_ID),
                () -> ModBlocks.MYCELIUM_NODE_ITEM = ModBlocks.createBlockItem(ModBlocks.MYCELIUM_NODE_ID, ModBlocks.MYCELIUM_NODE));
        event.register(Registries.ITEM, ModIds.id("spores"), () -> ModItems.SPORES = ModItems.create("spores"));
        event.register(Registries.ITEM, ModIds.id("mycelium_thread"), () -> ModItems.MYCELIUM_THREAD = ModItems.create("mycelium_thread"));
        event.register(Registries.ITEM, ModIds.id("mycelium_heart"), () -> ModItems.MYCELIUM_HEART = ModItems.createHeart("mycelium_heart"));
        event.register(Registries.ITEM, ModIds.id("sporeling_spawn_egg"), () -> ModItems.SPORELING_SPAWN_EGG = ModItems.createSpawnEgg(
                "sporeling_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.sporeling(), 0xCFDCA6, 0x866A9A));
        event.register(Registries.ITEM, ModIds.id("weaver_spawn_egg"), () -> ModItems.WEAVER_SPAWN_EGG = ModItems.createSpawnEgg(
                "weaver_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.weaver(), 0xD9D2C4, 0x9CAE7C));
        event.register(Registries.ITEM, ModIds.id("mycelium_guardian_spawn_egg"), () -> ModItems.GUARDIAN_SPAWN_EGG = ModItems.createSpawnEgg(
                "mycelium_guardian_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.guardian(), 0x4A3B47, 0xB9D98A));
        event.register(Registries.ITEM, ModIds.id("sand_goggles"), () -> ModItems.SAND_GOGGLES = ModItems.createGoggles("sand_goggles"));
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.LIGHTNING_CRYSTAL);
            event.accept(ModItems.HAILSTONE);
            event.accept(ModBlocks.LIGHTNING_ROOT_ITEM);
            event.accept(ModBlocks.ICICLE_ITEM);
            event.accept(ModBlocks.ICE_CRYSTAL_ITEM);
            event.accept(ModBlocks.MAGNETIC_ROCK_ITEM);
            event.accept(ModItems.AURORA_DUST);
            event.accept(ModItems.RAINBOW_SHARD);
            event.accept(ModBlocks.MYCELIUM_NODE_ITEM);
            event.accept(ModItems.SPORES);
            event.accept(ModItems.MYCELIUM_THREAD);
            event.accept(ModItems.MYCELIUM_HEART);
            event.accept(ModItems.SPORELING_SPAWN_EGG);
            event.accept(ModItems.WEAVER_SPAWN_EGG);
            event.accept(ModItems.GUARDIAN_SPAWN_EGG);
            event.accept(ModItems.SAND_GOGGLES);
        }
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.WEATHERVANE_ITEM);
            event.accept(ModBlocks.BAROMETER_ITEM);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FrontTicker.onServerTick(event.getServer());
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        StormrootCommands.register(event.getDispatcher());
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (LightningCrystals.onEntityAdded(event.getEntity(), event.getLevel())
                || io.github.hcuadrado428.stormroot.weather.AuroraCalm.onEntityAdded(event.getEntity(), event.getLevel())) {
            event.setCanceled(true);
        }
    }
    *//*?}*/

    /*? if neoforge {*/
    /*public Stormroot(IEventBus modBus, Dist dist) {
        modBus.addListener(Stormroot::onRegister);
        modBus.addListener(StormrootNetwork::onRegisterPayloads);
        modBus.addListener(Stormroot::onCreativeTabs);
        NeoForge.EVENT_BUS.addListener(Stormroot::onServerTick);
        NeoForge.EVENT_BUS.addListener(Stormroot::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(Stormroot::onEntityJoin);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(io.github.hcuadrado428.stormroot.entity.ModEntities.sporeling(), io.github.hcuadrado428.stormroot.entity.Sporeling.createAttributes().build());
            e.put(io.github.hcuadrado428.stormroot.entity.ModEntities.weaver(), io.github.hcuadrado428.stormroot.entity.Weaver.createAttributes().build());
            e.put(io.github.hcuadrado428.stormroot.entity.ModEntities.guardian(), io.github.hcuadrado428.stormroot.entity.MyceliumGuardian.createAttributes().build());
        });
        if (dist.isClient()) {
            StormrootClient.register(modBus);
        }
        Compat.initBloomberto(ModList.get().isLoaded("bloomberto"), dist.isClient());
        LOGGER.info("Stormroot cargado en NeoForge");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.SPORELING_ID), io.github.hcuadrado428.stormroot.entity.ModEntities::sporeling);
        event.register(Registries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.WEAVER_ID), io.github.hcuadrado428.stormroot.entity.ModEntities::weaver);
        event.register(Registries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.GUARDIAN_ID), io.github.hcuadrado428.stormroot.entity.ModEntities::guardian);
        for (String soundId : io.github.hcuadrado428.stormroot.registry.ModSounds.IDS) {
            event.register(Registries.SOUND_EVENT, ModIds.id(soundId), () -> io.github.hcuadrado428.stormroot.registry.ModSounds.get(soundId));
        }
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.WEATHERVANE_ID),
                () -> ModBlocks.WEATHERVANE = ModBlocks.createWeathervane());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.BAROMETER_ID),
                () -> ModBlocks.BAROMETER = ModBlocks.createBarometer());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                () -> ModBlocks.LIGHTNING_ROOT = ModBlocks.createLightningRoot());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.STORM_FIRE_ID),
                () -> ModBlocks.STORM_FIRE = ModBlocks.createStormFire());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.ICICLE_ID),
                () -> ModBlocks.ICICLE = ModBlocks.createIcicle());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.ICE_CRYSTAL_ID),
                () -> ModBlocks.ICE_CRYSTAL = ModBlocks.createIceCrystal());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.MAGNETIC_ROCK_ID),
                () -> ModBlocks.MAGNETIC_ROCK = ModBlocks.createMagneticRock());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.MYCELIUM_VEIN_ID),
                () -> ModBlocks.MYCELIUM_VEIN = ModBlocks.createMyceliumVein());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.MYCELIUM_NODE_ID),
                () -> ModBlocks.MYCELIUM_NODE = ModBlocks.createMyceliumNode());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.FROST_ICE_ID),
                () -> ModBlocks.FROST_ICE = ModBlocks.createFrostIce());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.SAND_LAYER_ID),
                () -> ModBlocks.SAND_LAYER = ModBlocks.createSandLayer());
        event.register(Registries.ITEM, ModIds.id("lightning_crystal"),
                () -> ModItems.LIGHTNING_CRYSTAL = ModItems.create("lightning_crystal"));
        event.register(Registries.ITEM, ModIds.id("hailstone"),
                () -> ModItems.HAILSTONE = ModItems.create("hailstone"));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.WEATHERVANE_ID),
                () -> ModBlocks.WEATHERVANE_ITEM = ModBlocks.createBlockItem(ModBlocks.WEATHERVANE_ID, ModBlocks.WEATHERVANE));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.BAROMETER_ID),
                () -> ModBlocks.BAROMETER_ITEM = ModBlocks.createBlockItem(ModBlocks.BAROMETER_ID, ModBlocks.BAROMETER));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                () -> ModBlocks.LIGHTNING_ROOT_ITEM = ModBlocks.createBlockItem(ModBlocks.LIGHTNING_ROOT_ID, ModBlocks.LIGHTNING_ROOT));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.ICICLE_ID),
                () -> ModBlocks.ICICLE_ITEM = ModBlocks.createBlockItem(ModBlocks.ICICLE_ID, ModBlocks.ICICLE));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.ICE_CRYSTAL_ID),
                () -> ModBlocks.ICE_CRYSTAL_ITEM = ModBlocks.createBlockItem(ModBlocks.ICE_CRYSTAL_ID, ModBlocks.ICE_CRYSTAL));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.MAGNETIC_ROCK_ID),
                () -> ModBlocks.MAGNETIC_ROCK_ITEM = ModBlocks.createBlockItem(ModBlocks.MAGNETIC_ROCK_ID, ModBlocks.MAGNETIC_ROCK));
        event.register(Registries.ITEM, ModIds.id("aurora_dust"), () -> ModItems.AURORA_DUST = ModItems.create("aurora_dust"));
        event.register(Registries.ITEM, ModIds.id("rainbow_shard"), () -> ModItems.RAINBOW_SHARD = ModItems.create("rainbow_shard"));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.MYCELIUM_NODE_ID),
                () -> ModBlocks.MYCELIUM_NODE_ITEM = ModBlocks.createBlockItem(ModBlocks.MYCELIUM_NODE_ID, ModBlocks.MYCELIUM_NODE));
        event.register(Registries.ITEM, ModIds.id("spores"), () -> ModItems.SPORES = ModItems.create("spores"));
        event.register(Registries.ITEM, ModIds.id("mycelium_thread"), () -> ModItems.MYCELIUM_THREAD = ModItems.create("mycelium_thread"));
        event.register(Registries.ITEM, ModIds.id("mycelium_heart"), () -> ModItems.MYCELIUM_HEART = ModItems.createHeart("mycelium_heart"));
        event.register(Registries.ITEM, ModIds.id("sporeling_spawn_egg"), () -> ModItems.SPORELING_SPAWN_EGG = ModItems.createSpawnEgg(
                "sporeling_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.sporeling(), 0xCFDCA6, 0x866A9A));
        event.register(Registries.ITEM, ModIds.id("weaver_spawn_egg"), () -> ModItems.WEAVER_SPAWN_EGG = ModItems.createSpawnEgg(
                "weaver_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.weaver(), 0xD9D2C4, 0x9CAE7C));
        event.register(Registries.ITEM, ModIds.id("mycelium_guardian_spawn_egg"), () -> ModItems.GUARDIAN_SPAWN_EGG = ModItems.createSpawnEgg(
                "mycelium_guardian_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.guardian(), 0x4A3B47, 0xB9D98A));
        event.register(Registries.ITEM, ModIds.id("sand_goggles"), () -> ModItems.SAND_GOGGLES = ModItems.createGoggles("sand_goggles"));
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.LIGHTNING_CRYSTAL);
            event.accept(ModItems.HAILSTONE);
            event.accept(ModBlocks.LIGHTNING_ROOT_ITEM);
            event.accept(ModBlocks.ICICLE_ITEM);
            event.accept(ModBlocks.ICE_CRYSTAL_ITEM);
            event.accept(ModBlocks.MAGNETIC_ROCK_ITEM);
            event.accept(ModItems.AURORA_DUST);
            event.accept(ModItems.RAINBOW_SHARD);
            event.accept(ModBlocks.MYCELIUM_NODE_ITEM);
            event.accept(ModItems.SPORES);
            event.accept(ModItems.MYCELIUM_THREAD);
            event.accept(ModItems.MYCELIUM_HEART);
            event.accept(ModItems.SPORELING_SPAWN_EGG);
            event.accept(ModItems.WEAVER_SPAWN_EGG);
            event.accept(ModItems.GUARDIAN_SPAWN_EGG);
            event.accept(ModItems.SAND_GOGGLES);
        }
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.WEATHERVANE_ITEM);
            event.accept(ModBlocks.BAROMETER_ITEM);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        FrontTicker.onServerTick(event.getServer());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        StormrootCommands.register(event.getDispatcher());
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (LightningCrystals.onEntityAdded(event.getEntity(), event.getLevel())
                || io.github.hcuadrado428.stormroot.weather.AuroraCalm.onEntityAdded(event.getEntity(), event.getLevel())) {
            event.setCanceled(true);
        }
    }
    *//*?}*/

    /*? if fabric {*/
    @Override
    public void onInitialize() {
        ModBlocks.WEATHERVANE = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.WEATHERVANE_ID), ModBlocks.createWeathervane());
        ModBlocks.BAROMETER = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.BAROMETER_ID), ModBlocks.createBarometer());
        ModBlocks.LIGHTNING_ROOT = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.LIGHTNING_ROOT_ID), ModBlocks.createLightningRoot());
        ModBlocks.STORM_FIRE = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.STORM_FIRE_ID), ModBlocks.createStormFire());
        ModBlocks.ICICLE = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.ICICLE_ID), ModBlocks.createIcicle());
        ModBlocks.ICE_CRYSTAL = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.ICE_CRYSTAL_ID), ModBlocks.createIceCrystal());
        ModBlocks.MAGNETIC_ROCK = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.MAGNETIC_ROCK_ID), ModBlocks.createMagneticRock());
        ModBlocks.MYCELIUM_VEIN = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.MYCELIUM_VEIN_ID), ModBlocks.createMyceliumVein());
        ModBlocks.MYCELIUM_NODE = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.MYCELIUM_NODE_ID), ModBlocks.createMyceliumNode());
        ModBlocks.FROST_ICE = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.FROST_ICE_ID), ModBlocks.createFrostIce());
        ModBlocks.SAND_LAYER = Registry.register(BuiltInRegistries.BLOCK,
                ModIds.id(ModBlocks.SAND_LAYER_ID), ModBlocks.createSandLayer());
        for (String soundId : io.github.hcuadrado428.stormroot.registry.ModSounds.IDS) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, ModIds.id(soundId), io.github.hcuadrado428.stormroot.registry.ModSounds.get(soundId));
        }
        ModItems.LIGHTNING_CRYSTAL = Registry.register(BuiltInRegistries.ITEM,
                ModIds.id("lightning_crystal"), ModItems.create("lightning_crystal"));
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.SPORELING_ID), io.github.hcuadrado428.stormroot.entity.ModEntities.sporeling());
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.WEAVER_ID), io.github.hcuadrado428.stormroot.entity.ModEntities.weaver());
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ModIds.id(io.github.hcuadrado428.stormroot.entity.ModEntities.GUARDIAN_ID), io.github.hcuadrado428.stormroot.entity.ModEntities.guardian());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                io.github.hcuadrado428.stormroot.entity.ModEntities.sporeling(), io.github.hcuadrado428.stormroot.entity.Sporeling.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                io.github.hcuadrado428.stormroot.entity.ModEntities.weaver(), io.github.hcuadrado428.stormroot.entity.Weaver.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                io.github.hcuadrado428.stormroot.entity.ModEntities.guardian(), io.github.hcuadrado428.stormroot.entity.MyceliumGuardian.createAttributes());
        ModItems.HAILSTONE = Registry.register(BuiltInRegistries.ITEM, ModIds.id("hailstone"), ModItems.create("hailstone"));
        ModBlocks.WEATHERVANE_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.WEATHERVANE_ID),
                ModBlocks.createBlockItem(ModBlocks.WEATHERVANE_ID, ModBlocks.WEATHERVANE));
        ModBlocks.BAROMETER_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.BAROMETER_ID),
                ModBlocks.createBlockItem(ModBlocks.BAROMETER_ID, ModBlocks.BAROMETER));
        ModBlocks.LIGHTNING_ROOT_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                ModBlocks.createBlockItem(ModBlocks.LIGHTNING_ROOT_ID, ModBlocks.LIGHTNING_ROOT));
        ModBlocks.ICICLE_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.ICICLE_ID),
                ModBlocks.createBlockItem(ModBlocks.ICICLE_ID, ModBlocks.ICICLE));
        ModBlocks.ICE_CRYSTAL_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.ICE_CRYSTAL_ID),
                ModBlocks.createBlockItem(ModBlocks.ICE_CRYSTAL_ID, ModBlocks.ICE_CRYSTAL));
        ModBlocks.MAGNETIC_ROCK_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.MAGNETIC_ROCK_ID),
                ModBlocks.createBlockItem(ModBlocks.MAGNETIC_ROCK_ID, ModBlocks.MAGNETIC_ROCK));
        ModItems.AURORA_DUST = Registry.register(BuiltInRegistries.ITEM, ModIds.id("aurora_dust"), ModItems.create("aurora_dust"));
        ModItems.RAINBOW_SHARD = Registry.register(BuiltInRegistries.ITEM, ModIds.id("rainbow_shard"), ModItems.create("rainbow_shard"));
        ModBlocks.MYCELIUM_NODE_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.MYCELIUM_NODE_ID),
                ModBlocks.createBlockItem(ModBlocks.MYCELIUM_NODE_ID, ModBlocks.MYCELIUM_NODE));
        ModItems.SPORES = Registry.register(BuiltInRegistries.ITEM, ModIds.id("spores"), ModItems.create("spores"));
        ModItems.MYCELIUM_THREAD = Registry.register(BuiltInRegistries.ITEM, ModIds.id("mycelium_thread"), ModItems.create("mycelium_thread"));
        ModItems.MYCELIUM_HEART = Registry.register(BuiltInRegistries.ITEM, ModIds.id("mycelium_heart"), ModItems.createHeart("mycelium_heart"));
        ModItems.SPORELING_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, ModIds.id("sporeling_spawn_egg"), ModItems.createSpawnEgg(
                "sporeling_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.sporeling(), 0xCFDCA6, 0x866A9A));
        ModItems.WEAVER_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, ModIds.id("weaver_spawn_egg"), ModItems.createSpawnEgg(
                "weaver_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.weaver(), 0xD9D2C4, 0x9CAE7C));
        ModItems.GUARDIAN_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, ModIds.id("mycelium_guardian_spawn_egg"), ModItems.createSpawnEgg(
                "mycelium_guardian_spawn_egg", io.github.hcuadrado428.stormroot.entity.ModEntities.guardian(), 0x4A3B47, 0xB9D98A));
        ModItems.SAND_GOGGLES = Registry.register(BuiltInRegistries.ITEM, ModIds.id("sand_goggles"), ModItems.createGoggles("sand_goggles"));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> {
                    entries.accept(ModItems.LIGHTNING_CRYSTAL);
                    entries.accept(ModItems.HAILSTONE);
                    entries.accept(ModBlocks.LIGHTNING_ROOT_ITEM);
                    entries.accept(ModBlocks.ICICLE_ITEM);
                    entries.accept(ModBlocks.ICE_CRYSTAL_ITEM);
                    entries.accept(ModBlocks.MAGNETIC_ROCK_ITEM);
                    entries.accept(ModItems.AURORA_DUST);
                    entries.accept(ModItems.RAINBOW_SHARD);
                    entries.accept(ModBlocks.MYCELIUM_NODE_ITEM);
                    entries.accept(ModItems.SPORES);
                    entries.accept(ModItems.MYCELIUM_THREAD);
                    entries.accept(ModItems.MYCELIUM_HEART);
                    entries.accept(ModItems.SPORELING_SPAWN_EGG);
                    entries.accept(ModItems.WEAVER_SPAWN_EGG);
                    entries.accept(ModItems.GUARDIAN_SPAWN_EGG);
                    entries.accept(ModItems.SAND_GOGGLES);
                });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(ModBlocks.WEATHERVANE_ITEM);
            entries.accept(ModBlocks.BAROMETER_ITEM);
        });
        ServerTickEvents.END_SERVER_TICK.register(FrontTicker::onServerTick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (LightningCrystals.onEntityAdded(entity, level)
                    || io.github.hcuadrado428.stormroot.weather.AuroraCalm.onEntityAdded(entity, level)) {
                entity.discard();
            }
        });
        StormrootNetwork.init();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> StormrootCommands.register(dispatcher));
        FabricLoader loader = FabricLoader.getInstance();
        Compat.initBloomberto(loader.isModLoaded("bloomberto"), loader.getEnvironmentType() == EnvType.CLIENT);
        LOGGER.info("Stormroot cargado en Fabric");
    }
    /*?}*/
}
