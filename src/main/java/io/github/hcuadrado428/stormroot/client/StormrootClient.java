package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.network.FrontsSyncPayload;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?}
//? if forge {
/*import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
*///?}
//? if neoforge {
/*import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
*///?}
//? if neoforge && <1.21.6 {
/*import net.neoforged.neoforge.client.event.ViewportEvent;
*///?}

/**
 * Pegamento de CLIENTE de cada loader. Nunca se carga en un servidor dedicado:
 * Fabric lo usa como entrypoint "client"; Forge/NeoForge solo llaman a {@link #register()} si el lado es cliente.
 *
 * <p>Niebla: Forge 1.20.1 y NeoForge 1.21.1 usan el evento de niebla.
 * TODO: Fabric (todas) y NeoForge 1.21.11 necesitan otro enganche (mixin o el nuevo sistema de niebla
 * de 1.21.6+); de momento ahí no hay niebla, solo partículas.
 */
public class StormrootClient /*? if fabric {*/ implements ClientModInitializer /*?}*/ {

    //? if fabric {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientWeather.tick());
        registerReceiver();
    }
    //?}

    //? if fabric && >=1.20.5 {
    private static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(FrontsSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> FrontsSyncPayload.handleOnClient(payload)));
    }
    //?}

    //? if fabric && <1.20.5 {
    /*private static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(FrontsSyncPayload.ID, (client, handler, buf, responseSender) -> {
            FrontsSyncPayload payload = new FrontsSyncPayload(buf);
            client.execute(() -> FrontsSyncPayload.handleOnClient(payload));
        });
    }
    *///?}

    //? if forge {
    /*public static void register() {
        MinecraftForge.EVENT_BUS.addListener(StormrootClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(StormrootClient::onRenderFog);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientWeather.tick();
        }
    }

    private static void onRenderFog(ViewportEvent.RenderFog event) {
        float far = ClientWeather.fogFarPlane(event.getFarPlaneDistance());
        if (far < event.getFarPlaneDistance()) {
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1f));
            event.setFarPlaneDistance(far);
            event.setCanceled(true);
        }
    }
    *///?}

    //? if neoforge {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(StormrootClient::onClientTick);
        registerFog();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientWeather.tick();
    }
    *///?}

    //? if neoforge && <1.21.6 {
    /*private static void registerFog() {
        NeoForge.EVENT_BUS.addListener(StormrootClient::onRenderFog);
    }

    private static void onRenderFog(ViewportEvent.RenderFog event) {
        float far = ClientWeather.fogFarPlane(event.getFarPlaneDistance());
        if (far < event.getFarPlaneDistance()) {
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1f));
            event.setFarPlaneDistance(far);
            event.setCanceled(true);
        }
    }
    *///?}

    //? if neoforge && >=1.21.6 {
    /*private static void registerFog() {
        // TODO: niebla en NeoForge 1.21.6+ (el render de niebla cambió).
    }
    *///?}
}
