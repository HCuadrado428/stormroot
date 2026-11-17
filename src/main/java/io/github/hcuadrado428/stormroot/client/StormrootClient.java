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
//? if neoforge {
/*import net.neoforged.neoforge.client.event.ViewportEvent;
*///?}

/**
 * Pegamento de CLIENTE de cada loader. Nunca se carga en un servidor dedicado:
 * Fabric lo usa como entrypoint "client"; Forge/NeoForge solo llaman a {@link #register()} si el lado es cliente.
 *
 * <p>Niebla: Forge y NeoForge usan el evento {@code ViewportEvent.RenderFog} (que desde 1.21.6 ya no se cancela);
 * Fabric usa {@code client/mixin/FogRendererMixin}.
 */
public class StormrootClient /*? if fabric {*/ implements ClientModInitializer /*?}*/ {

    //? if fabric {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientWeather.tick());
        registerReceiver();
        registerWorldRender();
        registerRenderLayers();
    }
    //?}

    // Fuego del clima con transparencia recortada (en Forge/NeoForge lo dice su modelo: "render_type").
    //? if fabric && >=1.21.11 {
    private static void registerRenderLayers() {
        for (net.minecraft.world.level.block.Block block : new net.minecraft.world.level.block.Block[] {
                io.github.hcuadrado428.stormroot.registry.ModBlocks.STORM_FIRE,
                io.github.hcuadrado428.stormroot.registry.ModBlocks.ICICLE,
                io.github.hcuadrado428.stormroot.registry.ModBlocks.ICE_CRYSTAL,
                io.github.hcuadrado428.stormroot.registry.ModBlocks.MYCELIUM_VEIN}) {
            net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(block,
                    net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT);
        }
    }
    //?} else if fabric {
    /*private static void registerRenderLayers() {
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(net.minecraft.client.renderer.RenderType.cutout(),
                io.github.hcuadrado428.stormroot.registry.ModBlocks.STORM_FIRE,
                io.github.hcuadrado428.stormroot.registry.ModBlocks.ICICLE,
                io.github.hcuadrado428.stormroot.registry.ModBlocks.ICE_CRYSTAL,
                io.github.hcuadrado428.stormroot.registry.ModBlocks.MYCELIUM_VEIN);
    }
    *///?}

    // Cielo de Stormroot (client/sky/SkyRenderer) desde el evento de render del mundo de cada loader.
    // Mismos eventos y matrices que Bloomberto (WorldRenderBridge), probados en todas las versiones.
    //? if fabric && >=1.21.11 {
    private static void registerWorldRender() {
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(context -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            io.github.hcuadrado428.stormroot.client.render.WorldRenderHook.fire(context.matrices(),
                    mc.gameRenderer.getMainCamera(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), null);
        });
    }
    //?} else if fabric && >=1.21 {
    /*private static void registerWorldRender() {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context ->
                io.github.hcuadrado428.stormroot.client.render.WorldRenderHook.fire(new com.mojang.blaze3d.vertex.PoseStack(),
                        context.camera(), context.tickCounter().getGameTimeDeltaPartialTick(false), context.positionMatrix()));
    }
    *///?} else if fabric {
    /*private static void registerWorldRender() {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context ->
                io.github.hcuadrado428.stormroot.client.render.WorldRenderHook.fire(context.matrixStack(),
                        context.camera(), context.tickDelta(), null));
    }
    *///?}

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
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderLevelStageEvent e) -> {
            if (e.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                io.github.hcuadrado428.stormroot.client.render.WorldRenderHook.fire(e.getPoseStack(), e.getCamera(),
                        e.getPartialTick(), null);
            }
        });
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
            ClientWeather.markFogApplied();
        }
    }
    *///?}

    //? if neoforge {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(StormrootClient::onClientTick);
        registerFog();
        registerWorldRender();
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
            ClientWeather.markFogApplied();
        }
    }
    *///?}

    //? if neoforge && >=1.21.6 {
    /*private static void registerFog() {
        NeoForge.EVENT_BUS.addListener(StormrootClient::onRenderFog);
    }

    // Desde 1.21.6 el evento no se cancela: cambia directamente FogData (environmentalStart/End).
    private static void onRenderFog(ViewportEvent.RenderFog event) {
        float far = ClientWeather.fogFarPlane(event.getFarPlaneDistance());
        if (far < event.getFarPlaneDistance()) {
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1f));
            event.setFarPlaneDistance(far);
            ClientWeather.markFogApplied();
        }
    }
    *///?}

    //? if neoforge && >=1.21.11 {
    /*private static void registerWorldRender() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterParticles e) -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            io.github.hcuadrado428.stormroot.client.render.WorldRenderHook.fire(
                    e.getPoseStack() != null ? e.getPoseStack() : new com.mojang.blaze3d.vertex.PoseStack(),
                    mc.gameRenderer.getMainCamera(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), null);
        });
    }
    *///?} else if neoforge {
    /*private static void registerWorldRender() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent e) -> {
            if (e.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                io.github.hcuadrado428.stormroot.client.render.WorldRenderHook.fire(new com.mojang.blaze3d.vertex.PoseStack(),
                        e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false), e.getModelViewMatrix());
            }
        });
    }
    *///?}
}
