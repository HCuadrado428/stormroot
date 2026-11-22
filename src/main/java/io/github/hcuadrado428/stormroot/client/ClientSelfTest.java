package io.github.hcuadrado428.stormroot.client;

import io.github.hcuadrado428.stormroot.Stormroot;
import io.github.hcuadrado428.stormroot.client.sky.SkyRenderer;
import io.github.hcuadrado428.stormroot.compat.Compat;
import io.github.hcuadrado428.stormroot.seep.SeepageSavedData;
import io.github.hcuadrado428.stormroot.selftest.SelfTest;
import io.github.hcuadrado428.stormroot.weather.FrontSimulation;
import io.github.hcuadrado428.stormroot.weather.FrontTicker;
import io.github.hcuadrado428.stormroot.weather.FrontType;
import io.github.hcuadrado428.stormroot.weather.WeatherFront;
import java.io.File;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;

/**
 * Self-test del CLIENTE (modo {@code client} o {@code all}), en un mundo de un jugador. Espera a que termine el del
 * servidor integrado ({@link SelfTest}) y entonces comprueba con el jugador real:
 * que llegan los frentes, que la tormenta se ve y se va, la niebla, la humedad de las cuevas y (con Bloomberto)
 * los efectos de pantalla y el archivo de config, y el cielo de Stormroot (nubes vanilla ocultas, tormenta lejana,
 * nubes de buen tiempo). Al terminar cierra el juego.
 */
public final class ClientSelfTest {
    private static final int TIMEOUT = 600;

    private static int ticks;
    private static int stage;
    private static int stageStart;
    private static boolean done;
    private static long windPushesBefore;
    private static volatile int weaverAngry = -1;
    private static long nextFrontId = 10_000;
    /** Captura pendiente: se coloca la cámara, se espera a que se dibuje y se avisa al script (que hace la captura). */
    private static String pendingShot;
    private static int shotAt;
    private static int holdUntil;

    private ClientSelfTest() {
    }

    /** Lo llama {@link ClientWeather#tick()} cada tick de cliente. */
    public static void tick() {
        if (!SelfTest.clientEnabled() || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ticks++;
        if (pendingShot != null) {
            if (ticks >= shotAt) {
                // Captura del propio juego (screenshots/); scripts/selftest-client-xvfb.sh la copia con este nombre.
                Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), message -> { });
                Stormroot.LOGGER.info("[selftest] SCREENSHOT {}", pendingShot);
                pendingShot = null;
                holdUntil = ticks + 80;
            }
            return;
        }
        if (ticks < holdUntil) {
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            SelfTest.report("client is singleplayer", false, "no hay servidor integrado");
            finish(mc);
            return;
        }
        double x = mc.player.getX();
        double z = mc.player.getZ();
        int waited = ticks - stageStart;
        if (stage >= 200) {
            gallery(mc, server, waited);
            return;
        }
        switch (stage) {
            case 0 -> {
                if (ticks > 100 && SelfTest.serverDone()) {
                    Stormroot.LOGGER.info("[selftest] cliente: empezando");
                    server.execute(() -> {
                        // Para las capturas: mediodía, y el jugador flotando a la altura de un mundo normal (y=70; el
                        // mundo plano de pruebas está en y=-60, demasiado lejos de las nubes).
                        server.overworld().setDayTime(6000);
                        net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
                        server.getCommands().performPrefixedCommand(src, "gamemode spectator @a");
                        server.getCommands().performPrefixedCommand(src, "execute as @a at @s run tp @s ~ 70 ~");
                    });
                    spawnAt(server, FrontType.THUNDERSTORM, x, z);
                    next();
                }
            }
            case 1 -> {
                if (ClientFronts.active() && !ClientFronts.snapshot().isEmpty()) {
                    SelfTest.report("client receives fronts", true, "");
                    next();
                } else if (waited > TIMEOUT) {
                    SelfTest.report("client receives fronts", false, "no llegó ningún paquete de frentes");
                    next();
                }
            }
            case 2 -> {
                boolean visible = ClientWeather.thunder() > 0.6f && mc.level.getThunderLevel(1.0f) > 0.5f
                        && mc.level.getRainLevel(1.0f) > 0.5f;
                if (visible || waited > TIMEOUT) {
                    SelfTest.report("storm is visible on the client", visible,
                            "trueno=" + ClientWeather.thunder() + ", lluvia=" + ClientWeather.rain());
                    checkBloomberto(mc);
                    stage = 20; // captura bajo la tormenta y luego sigue en el 3
                    stageStart = ticks;
                    requestShot(mc, "debajo_de_la_tormenta", -90f, -45f);
                }
            }
            case 3 -> {
                boolean gone = ClientWeather.thunder() < 0.05f && ClientWeather.rain() < 0.05f
                        && mc.level.getRainLevel(1.0f) < 0.1f;
                if (gone || waited > TIMEOUT) {
                    SelfTest.report("storm fades when the front leaves", gone,
                            "trueno=" + ClientWeather.thunder() + ", lluvia=" + ClientWeather.rain());
                    spawnAt(server, FrontType.FOG, x, z);
                    next();
                }
            }
            case 4 -> {
                boolean foggy = ClientWeather.fog() > 0.6f && ClientWeather.fogFarPlane(192f) < 100f
                        && ClientWeather.fogAppliedCount() > 0;
                if (foggy || waited > TIMEOUT) {
                    SelfTest.report("fog bank on the client", foggy, "niebla=" + ClientWeather.fog()
                            + ", veces que el enganche de niebla la aplicó=" + ClientWeather.fogAppliedCount());
                    stage = 40; // captura en la niebla y luego sigue en el 41
                    stageStart = ticks;
                    requestShot(mc, "dentro_de_la_niebla", -90f, 0f);
                }
            }
            case 41 -> {
                {
                    clear(server);
                    int cx = (int) Math.floor(x) >> 4;
                    int cz = (int) Math.floor(z) >> 4;
                    server.execute(() -> {
                        SeepageSavedData data = SeepageSavedData.get(server);
                        data.field().getOrCreate(cx, cz).set(1);
                        data.changed();
                        FrontTicker.save(server);
                    });
                    stage = 5;
                    stageStart = ticks;
                }
            }
            case 5 -> {
                boolean wet = ClientFronts.seep() > 0.9f;
                if (wet || waited > TIMEOUT) {
                    SelfTest.report("cave humidity reaches the client", wet, "humedad=" + ClientFronts.seep());
                    // Una tormenta grande a ~110 bloques de su borde: el cielo de Stormroot debe dibujarla de lejos.
                    spawnAt(server, FrontType.THUNDERSTORM, x + 260, z, 150);
                    next();
                }
            }
            case 6 -> {
                boolean hidden = mc.options.getCloudsType() == CloudStatus.OFF;
                boolean storm = SkyRenderer.stormCells() > 0 && SkyRenderer.cellsDrawn() > 0;
                if ((hidden && storm) || waited > TIMEOUT) {
                    SelfTest.report("vanilla clouds are hidden", hidden,
                            "getCloudsType=" + mc.options.getCloudsType() + " con el cielo de Stormroot activo");
                    SelfTest.report("stormroot sky draws a distant storm", storm, "celdas de tormenta="
                            + SkyRenderer.stormCells() + ", celdas dibujadas=" + SkyRenderer.cellsDrawn()
                            + ", fotogramas=" + SkyRenderer.framesDrawn());
                    SelfTest.report("cloud shadows on the ground", SkyRenderer.shadowQuads() > 0,
                            "trozos de sombra dibujados=" + SkyRenderer.shadowQuads());
                    stage = 60;
                    stageStart = ticks;
                    requestShot(mc, "tormenta_a_lo_lejos", -90f, -30f);
                }
            }
            case 60 -> {
                // Cielo despejado de frentes: solo las nubes de buen tiempo.
                mc.options.hideGui = false;
                clear(server);
                stage = 61;
                stageStart = ticks;
            }
            case 61 -> {
                // El paquete sin frentes llega en unos segundos; la rejilla se recalcula cada medio segundo.
                boolean fair = SkyRenderer.stormCells() == 0 && SkyRenderer.cellsDrawn() > 0;
                if ((fair && waited > 40) || waited > TIMEOUT) {
                    long before = SkyRenderer.cellsDrawn();
                    SelfTest.report("fair-weather clouds without fronts", fair, "celdas de tormenta="
                            + SkyRenderer.stormCells() + ", celdas dibujadas=" + before);
                    stage = 62;
                    stageStart = ticks;
                    requestShot(mc, "cielo_de_buen_tiempo", 0f, -35f);
                }
            }
            case 62 -> {
                // Mirando al suelo: las sombras de las nubes de buen tiempo.
                mc.options.hideGui = false;
                stage = 63;
                stageStart = ticks;
                requestShot(mc, "sombras_en_el_suelo", 0f, 80f);
            }
            case 63 -> {
                // Un arcoíris de frente: a 80 bloques al este, de norte a sur (centrado delante del jugador) y algo por
                // debajo de él (el jugador vuela muy alto sobre el mundo plano).
                double y = mc.player.getY() - 40;
                server.execute(() -> io.github.hcuadrado428.stormroot.weather.Rainbows.spawn(server.overworld(),
                        x + 80, y, z - io.github.hcuadrado428.stormroot.sky.Rainbow.RADIUS, Math.PI / 2));
                stage = 64;
                stageStart = ticks;
            }
            case 64 -> {
                // Esperar a que aparezca del todo (tarda 20 s en hacerse visible del todo).
                if ((SkyRenderer.rainbowFrames() > 0 && waited > 400) || waited > 900) {
                    SelfTest.report("rainbow drawn in the sky", SkyRenderer.rainbowFrames() > 0,
                            "fotogramas con arcoíris=" + SkyRenderer.rainbowFrames());
                    stage = 65;
                    stageStart = ticks;
                    requestShot(mc, "arcoiris", -90f, 20f);
                }
            }
            case 65 -> {
                // Tormenta magnética encima, con una brújula en la mano: debe girar sin parar.
                server.execute(io.github.hcuadrado428.stormroot.weather.Rainbows::clear);
                spawnAt(server, FrontType.MAGNETIC_STORM, x, z);
                mc.options.hideGui = false;
                // En espectador no se ve la mano: modo creativo y una brújula en la mano.
                server.execute(() -> {
                    net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(src, "gamemode creative @a");
                    server.getCommands().performPrefixedCommand(src, "item replace entity @a weapon.mainhand with minecraft:compass");
                });
                stage = 66;
                stageStart = ticks;
            }
            case 66 -> {
                long spins = MagneticCompass.spinChecks();
                if ((spins > 0 && waited > 100) || waited > 600) {
                    SelfTest.report("compass spins in a magnetic storm", spins > 0,
                            "giros=" + spins + ", tormenta=" + ClientFronts.intensity(FrontType.MAGNETIC_STORM, x, z));
                    stage = 67;
                    stageStart = ticks;
                    requestShot(mc, "brujula_en_tormenta_magnetica", 0f, 30f);
                    mc.options.hideGui = false;
                }
            }
            case 67 -> {
                // Viento: tormenta encima y unas flechas lanzadas; el viento tiene que empujarlas.
                server.execute(() -> server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(), "item replace entity @a weapon.mainhand with minecraft:air"));
                spawnAt(server, FrontType.THUNDERSTORM, x, z);
                stage = 68;
                stageStart = ticks;
            }
            case 68 -> {
                if (ClientFronts.intensity(FrontType.THUNDERSTORM, x, z) > 0.3 || waited > 400) {
                    windPushesBefore = io.github.hcuadrado428.stormroot.weather.Wind.pushes();
                    server.execute(() -> {
                        net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
                        for (int i = 0; i < 4; i++) {
                            server.getCommands().performPrefixedCommand(src,
                                    "execute as @a at @s run summon minecraft:arrow ~ ~2 ~ {Motion:[0.0d,0.3d,1.5d]}");
                        }
                    });
                    stage = 69;
                    stageStart = ticks;
                }
            }
            case 69 -> {
                if (waited > 80) {
                    long pushed = io.github.hcuadrado428.stormroot.weather.Wind.pushes() - windPushesBefore;
                    double wind = ClientWind.here();
                    SelfTest.report("wind pushes arrows in a storm", pushed > 0 && wind >= 0.5,
                            "empujes=" + pushed + ", viento aquí=" + wind);
                    spawnAt(server, FrontType.SANDSTORM, x, z);
                    stage = 70;
                    stageStart = ticks;
                }
            }
            case 70 -> {
                // Tormenta de arena encima de un bioma templado: muro marrón en el cielo y calima (polvo fino).
                ClientWeather.sandForcedForTest = 0.8;
                if ((SkyRenderer.dustCells() > 0 && ClientWeather.sandParticles > 0 && ClientWeather.sandFogFrames() > 0
                        && waited > 200) || waited > 700) {
                    SelfTest.report("sandstorm wall and calima on the client",
                            SkyRenderer.dustCells() > 0 && ClientWeather.sandParticles > 0,
                            "celdas de arena=" + SkyRenderer.dustCells() + ", partículas=" + ClientWeather.sandParticles);
                    SelfTest.report("sandstorm fog turns brown", ClientWeather.sandFogFrames() > 0,
                            "fotogramas con niebla marrón=" + ClientWeather.sandFogFrames());
                    stage = 170;
                    stageStart = ticks;
                    requestShot(mc, "tormenta_de_arena", 0f, -10f);
                }
            }
            case 170 -> {
                // La misma tormenta de arena vista desde fuera: a 380 bloques al este, mirando hacia ella (oeste).
                ClientWeather.sandForcedForTest = -1;
                if (waited == 5) {
                    server.execute(() -> server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack().withSuppressedOutput(), "execute as @a at @s run tp @s ~380 ~ ~"));
                }
                if (waited == 120) {
                    requestShot(mc, "tormenta_de_arena_desde_fuera", 90f, -4f);
                }
                if (waited > 200) {
                    server.execute(() -> server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack().withSuppressedOutput(), "execute as @a at @s run tp @s ~-380 ~ ~"));
                    stage = 71;
                    stageStart = ticks;
                }
            }
            case 71 -> {
                ClientWeather.sandForcedForTest = -1;
                // Un esporín delante del jugador: tiene que dibujarse con su modelo.
                mc.options.hideGui = false;
                clear(server);
                server.execute(() -> {
                    net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
                    // En el suelo y con el esporín a 3 bloques, para que la captura lo enseñe de cerca.
                    server.getCommands().performPrefixedCommand(src, "execute as @a at @s positioned over world_surface run tp @s ~ ~1 ~");
                    server.getCommands().performPrefixedCommand(src, "execute as @a at @s run summon stormroot:sporeling ~3 ~-1 ~");
                });
                mc.player.setYRot(-90f);
                mc.player.setXRot(25f);
                stage = 72;
                stageStart = ticks;
            }
            case 72 -> {
                long frames = io.github.hcuadrado428.stormroot.client.entity.SporelingModel.frames;
                if ((frames > 0 && waited > 60) || waited > 400) {
                    SelfTest.report("sporeling is drawn on the client", frames > 0, "fotogramas=" + frames);
                    stage = 73;
                    stageStart = ticks;
                    requestShot(mc, "esporin", -90f, 25f);
                }
            }
            case 73 -> {
                // Un tejedor delante; en supervivencia, "rompes" un nodo a su lado: tiene que ir a por ti.
                mc.options.hideGui = false;
                weaverAngry = -1;
                server.execute(() -> {
                    net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(src, "kill @e[type=stormroot:sporeling]");
                    // Al suelo antes de pasar a supervivencia (si no, cae desde y=70 y muere).
                    server.getCommands().performPrefixedCommand(src, "execute as @a at @s positioned over world_surface run tp @s ~ ~ ~");
                    server.getCommands().performPrefixedCommand(src, "gamemode survival @a");
                    server.getCommands().performPrefixedCommand(src, "execute as @a at @s run summon stormroot:weaver ~3 ~ ~");
                    net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    weaverAngry = io.github.hcuadrado428.stormroot.mycel.Mycelium.onNodeBroken(server.overworld(),
                            sp.blockPosition(), sp);
                });
                stage = 74;
                stageStart = ticks;
            }
            case 74 -> {
                long frames = io.github.hcuadrado428.stormroot.client.entity.WeaverModel.frames;
                if ((frames > 0 && weaverAngry >= 0 && waited > 60) || waited > 400) {
                    SelfTest.report("weaver is drawn and defends the network", frames > 0 && weaverAngry > 0,
                            "fotogramas=" + frames + ", tejedores enfadados=" + weaverAngry);
                    stage = 75;
                    stageStart = ticks;
                    requestShot(mc, "tejedor", -90f, 30f);
                }
            }
            case 75 -> {
                // El guardián de micelio, dormido, delante del jugador.
                mc.options.hideGui = false;
                server.execute(() -> {
                    net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(src, "kill @e[type=stormroot:weaver]");
                    server.getCommands().performPrefixedCommand(src, "gamemode creative @a");
                    // En pacífico los monstruos desaparecen al momento.
                    server.getCommands().performPrefixedCommand(src, "difficulty normal");
                    server.getCommands().performPrefixedCommand(src, "execute as @a at @s run summon stormroot:mycelium_guardian ~4 ~ ~");
                });
                stage = 76;
                stageStart = ticks;
            }
            case 76 -> {
                long frames = io.github.hcuadrado428.stormroot.client.entity.MyceliumGuardianModel.frames;
                if ((frames > 0 && waited > 60) || waited > 400) {
                    SelfTest.report("mycelium guardian is drawn on the client", frames > 0, "fotogramas=" + frames);
                    stage = 77;
                    stageStart = ticks;
                    requestShot(mc, "guardian_de_micelio", -90f, 0f);
                }
            }
            case 77 -> {
                mc.options.hideGui = false;
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "kill @e[type=stormroot:mycelium_guardian]"));
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "difficulty peaceful"));
                // Galería: capturas de todo lo que se ve (bloques, objetos, cada clima, sombras sobre desniveles).
                stage = 200;
                stageStart = ticks;
            }
            case 20 -> {
                clear(server);
                mc.options.hideGui = false;
                stage = 3;
                stageStart = ticks;
            }
            case 40 -> {
                mc.options.hideGui = false;
                stage = 41;
                stageStart = ticks;
            }
            default -> {
            }
        }
    }

    /** Coloca la cámara (yaw/pitch en grados), oculta la interfaz y pide una captura dentro de 40 ticks. */
    private static void requestShot(Minecraft mc, String name, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.options.hideGui = true;
        pendingShot = name;
        shotAt = ticks + 40;
    }

    private static void next() {
        stage++;
        stageStart = ticks;
    }

    // ---------------------------------------------------------------------------------------------------- galería
    // Lejos de la zona de pruebas, en el suelo del mundo plano (la hierba está en y=-61).
    private static final int GX = 600;
    private static final int GY = -60;
    private static final int GZ = 600;

    private static final String[] ITEMS_A = {"lightning_crystal", "hailstone", "aurora_dust", "rainbow_shard", "spores",
            "mycelium_thread", "mycelium_heart", "sand_goggles", "weathervane"};
    private static final String[] ITEMS_B = {"barometer", "lightning_root", "mycelium_node", "ice_crystal", "icicle",
            "magnetic_rock", "sporeling_spawn_egg", "weaver_spawn_egg", "mycelium_guardian_spawn_egg"};

    private static void cmd(MinecraftServer server, String... commands) {
        server.execute(() -> {
            net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
            for (String c : commands) {
                server.getCommands().performPrefixedCommand(src, c);
            }
        });
    }

    private static void hotbar(MinecraftServer server, String[] items) {
        for (int i = 0; i < items.length; i++) {
            cmd(server, "item replace entity @a hotbar." + i + " with stormroot:" + items[i]);
        }
    }

    /** Escenas de la galería: preparar en el tick 1, captura a los {@code wait} ticks, siguiente 50 ticks después. */
    private static void gallery(Minecraft mc, MinecraftServer server, int waited) {
        int scene = stage - 200;
        int wait = 120;
        String shot = null;
        float yaw = 0f;
        float pitch = 0f;
        boolean gui = false;
        switch (scene) {
            case 0 -> {
                // Preparar: mediodía, sin frentes, el jugador lejos de la zona de pruebas.
                if (waited == 1) {
                    clear(server);
                    cmd(server, "time set 6000", "gamemode spectator @a",
                            "tp @a " + GX + " " + (GY + 10) + " " + GZ);
                }
                wait = 100;
            }
            case 1 -> {
                // Todos los bloques de Stormroot en fila, delante del jugador (mira al sur).
                if (waited == 1) {
                    int z = GZ + 5;
                    cmd(server,
                            "fill " + (GX - 5) + " " + GY + " " + (GZ + 2) + " " + (GX + 7) + " " + (GY + 6) + " " + (GZ + 8) + " air",
                            "setblock " + (GX - 4) + " " + GY + " " + z + " stormroot:weathervane",
                            "setblock " + (GX - 3) + " " + GY + " " + z + " stormroot:barometer",
                            "setblock " + (GX - 2) + " " + GY + " " + z + " stormroot:lightning_root",
                            "setblock " + (GX - 1) + " " + GY + " " + z + " stormroot:ice_crystal",
                            "setblock " + GX + " " + GY + " " + z + " stormroot:magnetic_rock",
                            "setblock " + (GX + 1) + " " + GY + " " + z + " stormroot:sand_layer[layers=3]",
                            "setblock " + (GX + 2) + " " + GY + " " + z + " stormroot:frost_ice",
                            "setblock " + (GX + 3) + " " + GY + " " + z + " stormroot:mycelium_node",
                            "setblock " + (GX + 4) + " " + GY + " " + (z + 1) + " minecraft:stone",
                            "setblock " + (GX + 4) + " " + GY + " " + z + " stormroot:mycelium_vein[south=true]",
                            "setblock " + (GX + 5) + " " + (GY - 1) + " " + z + " minecraft:oak_leaves[persistent=true]",
                            "setblock " + (GX + 5) + " " + GY + " " + z + " stormroot:storm_fire",
                            "fill " + (GX - 4) + " " + (GY + 3) + " " + (z + 2) + " " + (GX + 5) + " " + (GY + 3) + " " + (z + 2) + " minecraft:stone",
                            "setblock " + (GX - 1) + " " + (GY + 2) + " " + (z + 2) + " stormroot:icicle[tip=true]",
                            "setblock " + (GX + 1) + " " + (GY + 2) + " " + (z + 2) + " stormroot:icicle[tip=false]",
                            "setblock " + (GX + 1) + " " + (GY + 1) + " " + (z + 2) + " stormroot:icicle[tip=true]",
                            "tp @a " + GX + ".5 " + (GY + 1.5) + " " + (GZ + 1) + " 0 20");
                }
                shot = "galeria_bloques";
                pitch = 20f;
            }
            case 2 -> {
                if (waited == 1) {
                    cmd(server, "gamemode creative @a", "clear @a");
                    hotbar(server, ITEMS_A);
                }
                shot = "galeria_objetos_a";
                pitch = -10f;
                gui = true;
                wait = 80;
            }
            case 3 -> {
                if (waited == 1) {
                    cmd(server, "clear @a");
                    hotbar(server, ITEMS_B);
                }
                shot = "galeria_objetos_b";
                pitch = -10f;
                gui = true;
                wait = 80;
            }
            case 4 -> {
                if (waited == 1) {
                    cmd(server, "clear @a", "gamemode spectator @a", "tp @a " + GX + " " + (GY + 8) + " " + GZ);
                    spawnAt(server, FrontType.BLIZZARD, GX, GZ);
                }
                shot = "galeria_ventisca";
                pitch = 5f;
                wait = 200;
            }
            case 5 -> {
                if (waited == 1) {
                    // Tormenta de granizo: id múltiplo de 3 e intensidad plena en el centro.
                    server.execute(() -> {
                        FrontSimulation sim = FrontTicker.simulation(server);
                        sim.clear();
                        sim.add(new WeatherFront(300, FrontType.THUNDERSTORM, GX, GZ, 120, 0, 0, 1000, 2000));
                        FrontTicker.applyNow(server);
                    });
                }
                shot = "galeria_granizo";
                pitch = -20f;
                wait = 200;
            }
            case 6 -> {
                if (waited == 1) {
                    cmd(server, "time set 18000");
                    spawnAt(server, FrontType.AURORA, GX, GZ, 250);
                }
                shot = "galeria_aurora";
                yaw = 180f;
                pitch = -35f;
                wait = 200;
            }
            case 7 -> {
                if (waited == 1) {
                    cmd(server, "time set 6000");
                    spawnAt(server, FrontType.MAGNETIC_STORM, GX, GZ, 200);
                }
                shot = "galeria_tormenta_magnetica";
                pitch = -5f;
                wait = 160;
            }
            case 8 -> {
                if (waited == 1) {
                    spawnAt(server, FrontType.HEAT_WAVE, GX, GZ, 250);
                }
                shot = "galeria_ola_de_calor";
                pitch = -5f;
                wait = 160;
            }
            case 9 -> {
                // Sombras sobre desniveles: una copa de árbol con huecos, escalones de piedra y losas.
                if (waited == 1) {
                    int z0 = GZ + 6;
                    cmd(server,
                            "fill " + (GX - 4) + " " + GY + " " + z0 + " " + (GX + 2) + " " + (GY + 3) + " " + (z0 + 5) + " minecraft:oak_leaves[persistent=true]",
                            "fill " + (GX - 3) + " " + (GY + 3) + " " + (z0 + 1) + " " + (GX - 1) + " " + (GY + 3) + " " + (z0 + 2) + " air",
                            "fill " + (GX + 4) + " " + GY + " " + z0 + " " + (GX + 5) + " " + GY + " " + (z0 + 5) + " minecraft:stone",
                            "fill " + (GX + 6) + " " + GY + " " + z0 + " " + (GX + 7) + " " + (GY + 1) + " " + (z0 + 5) + " minecraft:stone",
                            "fill " + (GX + 8) + " " + GY + " " + z0 + " " + (GX + 9) + " " + GY + " " + (z0 + 5) + " minecraft:stone_slab",
                            "fill " + (GX - 7) + " " + GY + " " + z0 + " " + (GX - 6) + " " + GY + " " + (z0 + 5) + " minecraft:snow[layers=3]",
                            "tp @a " + GX + " " + (GY + 14) + " " + (GZ - 4));
                    spawnAt(server, FrontType.THUNDERSTORM, GX, GZ);
                }
                shot = "galeria_sombras";
                pitch = 50f;
                wait = 160;
            }
            case 10 -> {
                // Una tormenta que se acerca, vista desde fuera (a 220 bloques al sur).
                if (waited == 1) {
                    spawnAt(server, FrontType.THUNDERSTORM, GX, GZ + 260, 110);
                    cmd(server, "tp @a " + GX + " " + (GY + 6) + " " + GZ);
                }
                shot = "galeria_tormenta_que_llega";
                pitch = -8f;
                wait = 160;
            }
            default -> {
                clear(server);
                mc.options.hideGui = false;
                finish(mc);
                return;
            }
        }
        if (shot != null && waited == wait) {
            requestShot(mc, shot, yaw, pitch);
            if (gui) {
                mc.options.hideGui = false;
            }
        }
        if (waited >= wait + 50) {
            stage++;
            stageStart = ticks;
        }
    }

    private static void spawnAt(MinecraftServer server, FrontType type, double x, double z) {
        spawnAt(server, type, x, z, 120);
    }

    private static void spawnAt(MinecraftServer server, FrontType type, double x, double z, double radius) {
        long id = nextFrontId++;
        server.execute(() -> {
            FrontSimulation sim = FrontTicker.simulation(server);
            sim.clear();
            // A media vida (intensidad plena) y quieto encima del jugador.
            sim.add(new WeatherFront(id, type, x, z, radius, 0, 0, 1000, 2000));
            FrontTicker.applyNow(server);
            FrontTicker.save(server);
        });
    }

    private static void clear(MinecraftServer server) {
        server.execute(() -> {
            FrontTicker.simulation(server).clear();
            FrontTicker.applyNow(server);
            FrontTicker.save(server);
        });
    }

    private static void checkBloomberto(Minecraft mc) {
        if (!Compat.bloomberto()) {
            Stormroot.LOGGER.info("[selftest] SKIP bloomberto (no está instalado en este target)");
            return;
        }
        boolean config = new File(mc.gameDirectory, "config/" + Stormroot.MOD_ID + ".toml").isFile();
        SelfTest.report("bloomberto config file", config, "no existe config/stormroot.toml");
        try {
            Object active = Class.forName("io.github.hcuadrado428.stormroot.compat.bloomberto.BloombertoClientCompat")
                    .getMethod("effectsActive").invoke(null);
            SelfTest.report("bloomberto storm screen effects", Boolean.TRUE.equals(active),
                    "los efectos de pantalla no se encendieron en la tormenta");
            Object screen = Class.forName("io.github.hcuadrado428.stormroot.compat.bloomberto.BloombertoClientCompat")
                    .getMethod("configScreen", net.minecraft.client.gui.screens.Screen.class).invoke(null, (Object) null);
            SelfTest.report("bloomberto config screen", screen != null, "la pantalla de config no se pudo crear");
        } catch (ReflectiveOperationException e) {
            SelfTest.report("bloomberto storm screen effects", false, e.toString());
        }
    }

    private static void finish(Minecraft mc) {
        done = true;
        Stormroot.LOGGER.info("[selftest] client done: {} passed, {} failed", SelfTest.passed(), SelfTest.failed());
        if (SelfTest.exitWhenDone()) {
            mc.stop();
        }
    }
}
