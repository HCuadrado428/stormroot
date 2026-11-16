package io.github.hcuadrado428.stormroot;

import io.github.hcuadrado428.stormroot.registry.ModItems;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*? if fabric {*/
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
/*?}*/

/*? if forge {*/
/*import net.minecraft.core.registries.Registries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
*//*?}*/

/*? if neoforge {*/
/*import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
*//*?}*/

/**
 * Punto de entrada del mod para los tres loaders.
 * El código común vive en otras clases (ModIds, ModItems, paquete weather); aquí solo va el "pegamento" de cada loader.
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
        MinecraftForge.EVENT_BUS.addListener(Stormroot::onServerTick);
        LOGGER.info("Stormroot cargado en Forge");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ITEM, ModIds.id("lightning_crystal"),
                () -> ModItems.LIGHTNING_CRYSTAL = ModItems.create("lightning_crystal"));
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FrontTicker.onServerTick(event.getServer());
        }
    }
    *//*?}*/

    /*? if neoforge {*/
    /*public Stormroot(IEventBus modBus) {
        modBus.addListener(Stormroot::onRegister);
        NeoForge.EVENT_BUS.addListener(Stormroot::onServerTick);
        LOGGER.info("Stormroot cargado en NeoForge");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.ITEM, ModIds.id("lightning_crystal"),
                () -> ModItems.LIGHTNING_CRYSTAL = ModItems.create("lightning_crystal"));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        FrontTicker.onServerTick(event.getServer());
    }
    *//*?}*/

    /*? if fabric {*/
    @Override
    public void onInitialize() {
        ModItems.LIGHTNING_CRYSTAL = Registry.register(BuiltInRegistries.ITEM,
                ModIds.id("lightning_crystal"), ModItems.create("lightning_crystal"));
        ServerTickEvents.END_SERVER_TICK.register(FrontTicker::onServerTick);
        LOGGER.info("Stormroot cargado en Fabric");
    }
    /*?}*/
}
