package io.github.hcuadrado428.stormroot;

import io.github.hcuadrado428.stormroot.command.StormrootCommands;
import io.github.hcuadrado428.stormroot.registry.ModItems;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import io.github.hcuadrado428.stormroot.weather.LightningCrystals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*? if fabric {*/
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
/*?}*/

/*? if forge {*/
/*import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
*//*?}*/

/*? if neoforge {*/
/*import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
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
        LOGGER.info("Stormroot cargado en Forge");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ITEM, ModIds.id("lightning_crystal"),
                () -> ModItems.LIGHTNING_CRYSTAL = ModItems.createLightningCrystal());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.LIGHTNING_CRYSTAL);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FrontTicker.onServerTick(event.getServer());
            LightningCrystals.onServerTick(event.getServer());
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk()) {
            LightningCrystals.onEntityJoin(event.getEntity(), event.getLevel());
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        StormrootCommands.register(event.getDispatcher());
    }
    *//*?}*/

    /*? if neoforge {*/
    /*public Stormroot(IEventBus modBus) {
        modBus.addListener(Stormroot::onRegister);
        modBus.addListener(Stormroot::onCreativeTabs);
        NeoForge.EVENT_BUS.addListener(Stormroot::onServerTick);
        NeoForge.EVENT_BUS.addListener(Stormroot::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(Stormroot::onEntityJoin);
        LOGGER.info("Stormroot cargado en NeoForge");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ITEM, ModIds.id("lightning_crystal"),
                () -> ModItems.LIGHTNING_CRYSTAL = ModItems.createLightningCrystal());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.LIGHTNING_CRYSTAL);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        FrontTicker.onServerTick(event.getServer());
        LightningCrystals.onServerTick(event.getServer());
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk()) {
            LightningCrystals.onEntityJoin(event.getEntity(), event.getLevel());
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        StormrootCommands.register(event.getDispatcher());
    }
    *//*?}*/

    /*? if fabric {*/
    @Override
    public void onInitialize() {
        ModItems.LIGHTNING_CRYSTAL = Registry.register(BuiltInRegistries.ITEM,
                ModIds.id("lightning_crystal"), ModItems.createLightningCrystal());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> entries.accept(ModItems.LIGHTNING_CRYSTAL));
        ServerTickEvents.END_SERVER_TICK.register(FrontTicker::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(LightningCrystals::onServerTick);
        ServerEntityEvents.ENTITY_LOAD.register(LightningCrystals::onEntityJoin);
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> StormrootCommands.register(dispatcher));
        LOGGER.info("Stormroot cargado en Fabric");
    }
    /*?}*/
}
