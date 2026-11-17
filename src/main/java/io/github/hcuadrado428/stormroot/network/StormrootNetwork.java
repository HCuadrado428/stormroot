package io.github.hcuadrado428.stormroot.network;

import net.minecraft.server.level.ServerPlayer;

//? if fabric && >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?}
//? if fabric && <1.20.5 {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
*///?}
//? if forge {
/*import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
*///?}
//? if neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
*///?}

/**
 * Red de Stormroot: registro del paquete y envío servidor → cliente en cada loader/versión.
 * Si el cliente no tiene el mod (o el servidor no lo tiene), no se envía nada y no falla nada.
 */
public final class StormrootNetwork {
    //? if forge {
    /*private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(FrontsSyncPayload.ID,
            () -> PROTOCOL, NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    *///?}

    private StormrootNetwork() {
    }

    /** Registro común (en ambos lados), desde el inicializador del mod. */
    public static void init() {
        //? if fabric && >=1.20.5 {
        PayloadTypeRegistry.playS2C().register(FrontsSyncPayload.TYPE, FrontsSyncPayload.CODEC);
        //?}
        //? if forge {
        /*CHANNEL.messageBuilder(FrontsSyncPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FrontsSyncPayload::write)
                .decoder(FrontsSyncPayload::new)
                .consumerMainThread((payload, context) -> FrontsSyncPayload.handleOnClient(payload))
                .add();
        *///?}
    }

    //? if neoforge {
    /*public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(FrontsSyncPayload.TYPE, FrontsSyncPayload.CODEC,
                (payload, context) -> FrontsSyncPayload.handleOnClient(payload));
    }
    *///?}

    public static void sendFronts(ServerPlayer player, FrontsSyncPayload payload) {
        //? if fabric && >=1.20.5 {
        if (ServerPlayNetworking.canSend(player, FrontsSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
        //?}
        //? if fabric && <1.20.5 {
        /*if (ServerPlayNetworking.canSend(player, FrontsSyncPayload.ID)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            payload.write(buf);
            ServerPlayNetworking.send(player, FrontsSyncPayload.ID, buf);
        }
        *///?}
        //? if forge {
        /*if (CHANNEL.isRemotePresent(player.connection.connection)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
        *///?}
        //? if neoforge {
        /*if (player.connection.hasChannel(FrontsSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        *///?}
    }
}
