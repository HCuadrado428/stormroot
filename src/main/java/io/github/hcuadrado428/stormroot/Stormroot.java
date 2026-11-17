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
        StormrootNetwork.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            StormrootClient.register();
        }
        Compat.initBloomberto(ModList.get().isLoaded("bloomberto"), FMLEnvironment.dist == Dist.CLIENT);
        LOGGER.info("Stormroot cargado en Forge");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.WEATHERVANE_ID),
                () -> ModBlocks.WEATHERVANE = ModBlocks.createWeathervane());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.BAROMETER_ID),
                () -> ModBlocks.BAROMETER = ModBlocks.createBarometer());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                () -> ModBlocks.LIGHTNING_ROOT = ModBlocks.createLightningRoot());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.STORM_FIRE_ID),
                () -> ModBlocks.STORM_FIRE = ModBlocks.createStormFire());
        event.register(Registries.ITEM, ModIds.id("lightning_crystal"),
                () -> ModItems.LIGHTNING_CRYSTAL = ModItems.create("lightning_crystal"));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.WEATHERVANE_ID),
                () -> ModBlocks.WEATHERVANE_ITEM = ModBlocks.createBlockItem(ModBlocks.WEATHERVANE_ID, ModBlocks.WEATHERVANE));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.BAROMETER_ID),
                () -> ModBlocks.BAROMETER_ITEM = ModBlocks.createBlockItem(ModBlocks.BAROMETER_ID, ModBlocks.BAROMETER));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                () -> ModBlocks.LIGHTNING_ROOT_ITEM = ModBlocks.createBlockItem(ModBlocks.LIGHTNING_ROOT_ID, ModBlocks.LIGHTNING_ROOT));
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.LIGHTNING_CRYSTAL);
            event.accept(ModBlocks.LIGHTNING_ROOT_ITEM);
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
        if (LightningCrystals.onEntityAdded(event.getEntity(), event.getLevel())) {
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
        if (dist.isClient()) {
            StormrootClient.register();
        }
        Compat.initBloomberto(ModList.get().isLoaded("bloomberto"), dist.isClient());
        LOGGER.info("Stormroot cargado en NeoForge");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.WEATHERVANE_ID),
                () -> ModBlocks.WEATHERVANE = ModBlocks.createWeathervane());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.BAROMETER_ID),
                () -> ModBlocks.BAROMETER = ModBlocks.createBarometer());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                () -> ModBlocks.LIGHTNING_ROOT = ModBlocks.createLightningRoot());
        event.register(Registries.BLOCK, ModIds.id(ModBlocks.STORM_FIRE_ID),
                () -> ModBlocks.STORM_FIRE = ModBlocks.createStormFire());
        event.register(Registries.ITEM, ModIds.id("lightning_crystal"),
                () -> ModItems.LIGHTNING_CRYSTAL = ModItems.create("lightning_crystal"));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.WEATHERVANE_ID),
                () -> ModBlocks.WEATHERVANE_ITEM = ModBlocks.createBlockItem(ModBlocks.WEATHERVANE_ID, ModBlocks.WEATHERVANE));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.BAROMETER_ID),
                () -> ModBlocks.BAROMETER_ITEM = ModBlocks.createBlockItem(ModBlocks.BAROMETER_ID, ModBlocks.BAROMETER));
        event.register(Registries.ITEM, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                () -> ModBlocks.LIGHTNING_ROOT_ITEM = ModBlocks.createBlockItem(ModBlocks.LIGHTNING_ROOT_ID, ModBlocks.LIGHTNING_ROOT));
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.LIGHTNING_CRYSTAL);
            event.accept(ModBlocks.LIGHTNING_ROOT_ITEM);
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
        if (LightningCrystals.onEntityAdded(event.getEntity(), event.getLevel())) {
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
        ModItems.LIGHTNING_CRYSTAL = Registry.register(BuiltInRegistries.ITEM,
                ModIds.id("lightning_crystal"), ModItems.create("lightning_crystal"));
        ModBlocks.WEATHERVANE_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.WEATHERVANE_ID),
                ModBlocks.createBlockItem(ModBlocks.WEATHERVANE_ID, ModBlocks.WEATHERVANE));
        ModBlocks.BAROMETER_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.BAROMETER_ID),
                ModBlocks.createBlockItem(ModBlocks.BAROMETER_ID, ModBlocks.BAROMETER));
        ModBlocks.LIGHTNING_ROOT_ITEM = Registry.register(BuiltInRegistries.ITEM, ModIds.id(ModBlocks.LIGHTNING_ROOT_ID),
                ModBlocks.createBlockItem(ModBlocks.LIGHTNING_ROOT_ID, ModBlocks.LIGHTNING_ROOT));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> {
                    entries.accept(ModItems.LIGHTNING_CRYSTAL);
                    entries.accept(ModBlocks.LIGHTNING_ROOT_ITEM);
                });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(ModBlocks.WEATHERVANE_ITEM);
            entries.accept(ModBlocks.BAROMETER_ITEM);
        });
        ServerTickEvents.END_SERVER_TICK.register(FrontTicker::onServerTick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (LightningCrystals.onEntityAdded(entity, level)) {
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
