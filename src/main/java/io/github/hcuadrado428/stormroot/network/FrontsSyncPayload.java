package io.github.hcuadrado428.stormroot.network;

import io.github.hcuadrado428.stormroot.ModIds;
import io.github.hcuadrado428.stormroot.client.ClientFronts;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Servidor → cliente: frentes cercanos al jugador, viento y si Stormroot controla el clima.
 * El formato se escribe a mano con {@link FriendlyByteBuf}, igual en todas las versiones;
 * en 1.20.5+ además es un {@code CustomPacketPayload}.
 */
public final class FrontsSyncPayload /*? if >=1.20.5 {*/ implements CustomPacketPayload /*?}*/ {
    public static final Identifier ID = ModIds.id("fronts_sync");
    /** Máximo de frentes por paquete (más que suficiente; protege de paquetes enormes). */
    public static final int MAX_FRONTS = 32;

    //? if >=1.20.5 {
    public static final CustomPacketPayload.Type<FrontsSyncPayload> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, FrontsSyncPayload> CODEC =
            CustomPacketPayload.codec(FrontsSyncPayload::write, FrontsSyncPayload::new);
    //?}

    private final boolean controlsWeather;
    private final double windAngle;
    /** Humedad del subsuelo en el chunk del jugador (-1 sequía … 1 empapado). */
    private final float seep;
    /** Semilla del cielo de este mundo (sale de la semilla del mundo por una función de un solo sentido). */
    private final long skySeed;
    private final List<WeatherFront> fronts;
    /** Arcoíris cerca del jugador. */
    private final List<io.github.hcuadrado428.stormroot.sky.Rainbow> rainbows;
    private static final int MAX_RAINBOWS = 8;
    /** Bits de los ajustes del viento: 1 = desvía proyectiles, 2 = también los de los jugadores. */
    private final int windFlags;

    public FrontsSyncPayload(boolean controlsWeather, double windAngle, float seep, long skySeed, List<WeatherFront> fronts) {
        this(controlsWeather, windAngle, seep, skySeed, fronts, List.of());
    }

    public FrontsSyncPayload(boolean controlsWeather, double windAngle, float seep, long skySeed, List<WeatherFront> fronts,
                             List<io.github.hcuadrado428.stormroot.sky.Rainbow> rainbows) {
        this.windFlags = (io.github.hcuadrado428.stormroot.config.StormrootConfig.windProjectilesActive() ? 1 : 0)
                | (io.github.hcuadrado428.stormroot.config.StormrootConfig.windPlayerShotsActive() ? 2 : 0);
        this.rainbows = new ArrayList<>(rainbows.subList(0, Math.min(rainbows.size(), MAX_RAINBOWS)));
        this.controlsWeather = controlsWeather;
        this.windAngle = windAngle;
        this.seep = seep;
        this.skySeed = skySeed;
        // Copias: en un solo jugador el paquete no se serializa y no hay que compartir objetos con el servidor.
        this.fronts = new ArrayList<>();
        for (WeatherFront front : fronts) {
            if (this.fronts.size() >= MAX_FRONTS) {
                break;
            }
            this.fronts.add(front.copy());
        }
    }

    public FrontsSyncPayload(FriendlyByteBuf buf) {
        this.controlsWeather = buf.readBoolean();
        this.windAngle = buf.readDouble();
        this.seep = buf.readFloat();
        this.skySeed = buf.readLong();
        int count = Math.min(buf.readVarInt(), MAX_FRONTS);
        this.fronts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long id = buf.readLong();
            FrontType type = FrontType.byId(buf.readUtf(32));
            WeatherFront front = new WeatherFront(id, type != null ? type : FrontType.RAIN,
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
            this.fronts.add(front);
        }
        int rainbowCount = Math.min(buf.readVarInt(), MAX_RAINBOWS);
        this.rainbows = new ArrayList<>(rainbowCount);
        for (int i = 0; i < rainbowCount; i++) {
            this.rainbows.add(new io.github.hcuadrado428.stormroot.sky.Rainbow(buf.readLong(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readLong(), buf.readLong()));
        }
        this.windFlags = buf.readByte();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(controlsWeather);
        buf.writeDouble(windAngle);
        buf.writeFloat(seep);
        buf.writeLong(skySeed);
        buf.writeVarInt(fronts.size());
        for (WeatherFront front : fronts) {
            buf.writeLong(front.id());
            buf.writeUtf(front.type().id(), 32);
            buf.writeDouble(front.x());
            buf.writeDouble(front.z());
            buf.writeDouble(front.radius());
            buf.writeDouble(front.vx());
            buf.writeDouble(front.vz());
            buf.writeDouble(front.ageSeconds());
            buf.writeDouble(front.maxAgeSeconds());
        }
        buf.writeVarInt(rainbows.size());
        for (io.github.hcuadrado428.stormroot.sky.Rainbow rainbow : rainbows) {
            buf.writeLong(rainbow.id());
            buf.writeDouble(rainbow.x());
            buf.writeDouble(rainbow.y());
            buf.writeDouble(rainbow.z());
            buf.writeDouble(rainbow.angle());
            buf.writeLong(rainbow.startTick());
            buf.writeLong(rainbow.endTick());
        }
        buf.writeByte(windFlags);
    }

    public List<io.github.hcuadrado428.stormroot.sky.Rainbow> rainbows() {
        return rainbows;
    }

    public long skySeed() {
        return skySeed;
    }

    /** Se ejecuta en el hilo principal del cliente. Solo toca {@link ClientFronts} (Java puro). */
    public static void handleOnClient(FrontsSyncPayload payload) {
        ClientFronts.accept(payload.controlsWeather, payload.windAngle, payload.seep, payload.skySeed, payload.fronts);
        ClientFronts.acceptRainbows(payload.rainbows);
        ClientFronts.acceptWind((payload.windFlags & 1) != 0, (payload.windFlags & 2) != 0);
    }

    //? if >=1.20.5 {
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}
}
