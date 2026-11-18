package io.github.hcuadrado428.stormroot.selftest;

import io.github.hcuadrado428.stormroot.mycel.Mycelium;
import io.github.hcuadrado428.stormroot.mycel.MyceliumNetworks;
import io.github.hcuadrado428.stormroot.mycel.MyceliumRules;
import io.github.hcuadrado428.stormroot.registry.ModBlocks;
import io.github.hcuadrado428.stormroot.registry.ModItems;
import io.github.hcuadrado428.stormroot.seep.SeepageField;
import io.github.hcuadrado428.stormroot.seep.SeepageSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Self-test de la red de micelio y de la bruma de esporas. Usa un túnel propio bajo la cueva de pruebas
 * (y -56..-55, z +3, x +2..+13) y una cámara con luz (x/z +8..+9). El mundo plano es de llanura, así que para
 * hacerla crecer se activa {@link Mycelium#setAnywhereForTest}.
 */
final class MyceliumSelfTest {
    private static final int FLOOR = -57;
    private static final int CEILING = -54;

    private final int bx;
    private final int bz;

    MyceliumSelfTest(int bx, int bz) {
        this.bx = bx;
        this.bz = bz;
    }

    private BlockPos tunnel(int x, int y) {
        return new BlockPos(bx + x, y, bz + 3);
    }

    /** Fase 0: túnel y cámara con una piedra luminosa (la luz necesita unos ticks para propagarse). */
    void build(ServerLevel level) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(bx + 2, FLOOR + 1, bz + 2, bx + 13, CEILING - 1, bz + 4)) {
            level.setBlock(pos, air, Block.UPDATE_CLIENTS);
        }
        for (BlockPos pos : BlockPos.betweenClosed(bx + 8, FLOOR + 1, bz + 8, bx + 9, CEILING - 1, bz + 9)) {
            level.setBlock(pos, air, Block.UPDATE_CLIENTS);
        }
        level.setBlock(new BlockPos(bx + 9, CEILING, bz + 9), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Fase 1: todas las comprobaciones. */
    void run(ServerLevel level) {
        SelfTest.check("mycelium networks stop at 30 chunks", checkNetworks(), "una red pasa de "
                + MyceliumNetworks.MAX_CHUNKS + " chunks o roba los de otra");
        checkBiomes(level);
        checkGrowth(level);
        checkLight(level);
        checkBrokenNode(level);
        checkSporeling(level);
        checkWeaver(level);
        checkMistRules();
        checkLoot(level);
        checkCodec(level);
        checkGuardian(level);
    }

    /**
     * Guardián: duerme, despierta contra un objetivo, nube de esporas y raíces; al vencerlo la red se calma (no crece) y
     * el corazón la domestica (solo crece por roca y tierra naturales). Se prueba en la cima de la zona (cabe de pie).
     */
    private void checkGuardian(ServerLevel level) {
        BlockPos at = new BlockPos(bx + 3, -20, bz + 4);
        io.github.hcuadrado428.stormroot.entity.MyceliumGuardian guardian =
                Mycelium.spawn(io.github.hcuadrado428.stormroot.entity.ModEntities.guardian(), level, at);
        boolean spawned = guardian != null;
        boolean asleep = spawned && !guardian.isAwake();
        float health = spawned ? guardian.getMaxHealth() : 0;
        boolean fights = false;
        if (spawned) {
            net.minecraft.world.entity.Entity victim = Mycelium.spawnSporeling(level, at.east(3));
            if (victim instanceof net.minecraft.world.entity.LivingEntity living) {
                guardian.wake(living);
                net.minecraft.world.entity.AreaEffectCloud cloud = guardian.sporeCloud(level);
                io.github.hcuadrado428.stormroot.entity.MyceliumGuardian.roots(level, living);
                //? if >=1.21.11 {
                boolean rooted = living.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
                //?} else {
                /*boolean rooted = living.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                *///?}
                fights = guardian.isAwake() && guardian.getTarget() == living && cloud.isAlive() && rooted;
                cloud.discard();
                victim.discard();
            }
            guardian.discard();
        }
        SelfTest.check("mycelium guardian sleeps and fights", spawned && asleep && health == 60.0F && fights,
                "aparece=" + spawned + ", dormido=" + asleep + ", vida=" + health + ", pelea=" + fights);

        // La red del túnel: al vencer al guardián se calma; con el corazón, domesticada.
        BlockPos node = tunnel(11, FLOOR);
        if (!Mycelium.isNode(level.getBlockState(node))) {
            level.setBlock(node, ModBlocks.MYCELIUM_NODE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        MyceliumNetworks networks = Mycelium.networks(level);
        long chunk = SeepageField.key(bx >> 4, bz >> 4);
        if (networks.networkAt(chunk) == 0) {
            networks.create(chunk);
        }
        // El self-test se repite sobre el mismo mundo (tras reiniciar): la red empieza salvaje cada vez.
        networks.setState(networks.networkAt(chunk), MyceliumNetworks.State.WILD);
        boolean calmed = Mycelium.onGuardianDefeated(level, node) && networks.stateAt(chunk) == MyceliumNetworks.State.CALM;
        boolean stopped;
        Mycelium.setAnywhereForTest(true);
        try {
            stopped = !Mycelium.growInto(level, node, tunnel(12, FLOOR + 1), false);
            boolean tamed = Mycelium.tame(level, node) && networks.stateAt(chunk) == MyceliumNetworks.State.TAMED;
            // Domesticada: no se agarra a tablones (construcción), sí a la piedra.
            BlockPos gap = tunnel(13, FLOOR + 1);
            level.setBlock(gap.below(), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            net.minecraft.world.level.block.state.properties.BooleanProperty down =
                    net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(net.minecraft.core.Direction.DOWN);
            BlockState onWood = Mycelium.veinStateAt(level, gap, true);
            boolean noPlanks = onWood == null || !onWood.getValue(down);
            level.setBlock(gap.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockState onRock = Mycelium.veinStateAt(level, gap, true);
            boolean onStone = onRock != null && onRock.getValue(down);
            SelfTest.check("defeating the guardian calms the network and the heart tames it",
                    calmed && stopped && tamed && noPlanks && onStone,
                    "calmada=" + calmed + ", no crece=" + stopped + ", domesticada=" + tamed + ", no trepa por tablones=" + noPlanks
                            + ", sí por piedra=" + onStone);
        } finally {
            Mycelium.setAnywhereForTest(false);
        }
        boolean eggs = ModItems.SPORELING_SPAWN_EGG instanceof net.minecraft.world.item.SpawnEggItem
                && ModItems.WEAVER_SPAWN_EGG instanceof net.minecraft.world.item.SpawnEggItem
                && ModItems.GUARDIAN_SPAWN_EGG instanceof net.minecraft.world.item.SpawnEggItem
                && ModItems.MYCELIUM_HEART != null;
        SelfTest.check("spawn eggs for the network's inhabitants", eggs, "faltan huevos o el corazón");
    }

    private static boolean checkNetworks() {
        MyceliumNetworks networks = new MyceliumNetworks();
        long home = SeepageField.key(1000, 1000);
        int id = networks.create(home);
        int grown = 0;
        for (int i = 1; i <= 40; i++) {
            if (networks.tryGrow(home, SeepageField.key(1000 + i, 1000))) {
                grown++;
            }
        }
        long other = SeepageField.key(0, 0);
        int otherId = networks.create(other);
        boolean touch = networks.tryGrow(other, home) && networks.networkAt(home) == id;
        return grown == MyceliumNetworks.MAX_CHUNKS - 1 && networks.size(id) == MyceliumNetworks.MAX_CHUNKS
                && touch && networks.size(otherId) == 1;
    }

    /** En la llanura del mundo plano no nace ni crece: la red no sale de pantanos, manglares y champiñones. */
    private void checkBiomes(ServerLevel level) {
        Mycelium.setAnywhereForTest(false);
        BlockPos air = tunnel(6, FLOOR + 1);
        boolean habitable = Mycelium.habitable(level, air);
        boolean born = Mycelium.tryBirth(level, air);
        boolean grew = Mycelium.growInto(level, tunnel(5, FLOOR + 1), air, false);
        SelfTest.check("mycelium stays under its biomes", !habitable && !born && !grew,
                "habitable=" + habitable + ", nace=" + born + ", crece=" + grew);
    }

    /** Desde un nodo, la red crece por el suelo, las paredes y el techo del túnel. */
    private void checkGrowth(ServerLevel level) {
        Mycelium.setAnywhereForTest(true);
        try {
            BlockPos node = tunnel(3, FLOOR);
            level.setBlock(node, ModBlocks.MYCELIUM_NODE.defaultBlockState(), Block.UPDATE_CLIENTS);
            RandomSource random = RandomSource.create(42);
            List<BlockPos> grown = new ArrayList<>();
            grown.add(node);
            for (int i = 0; i < 300; i++) {
                BlockPos from = grown.get(random.nextInt(grown.size()));
                int before = count(level);
                if (Mycelium.spread(level, from, random) && count(level) > before) {
                    grown.clear();
                    grown.addAll(parts(level));
                }
            }
            int veins = count(level);
            boolean walls = false;
            for (BlockPos pos : parts(level)) {
                BlockState state = level.getBlockState(pos);
                walls |= Mycelium.isVein(state) && (state.getValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(
                        net.minecraft.core.Direction.UP)) || pos.getZ() != bz + 3);
            }
            SelfTest.check("mycelium grows from a node", veins >= 12 && walls,
                    veins + " hilos, en paredes o techo=" + walls);
        } finally {
            Mycelium.setAnywhereForTest(false);
        }
    }

    /** Con luz de antorchas (aquí, piedra luminosa) el hilo se retira y no crece hacia ella. */
    private void checkLight(ServerLevel level) {
        Mycelium.setAnywhereForTest(true);
        try {
            BlockPos lit = new BlockPos(bx + 8, FLOOR + 1, bz + 8);
            BlockState vein = Mycelium.veinStateAt(level, lit);
            level.setBlock(lit, vein, Block.UPDATE_CLIENTS);
            int light = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, lit);
            Mycelium.veinTick(level, lit, RandomSource.create(1));
            boolean retreated = level.getBlockState(lit).isAir();
            boolean grewIntoLight = Mycelium.growInto(level, lit.east(), lit, false);
            SelfTest.check("mycelium is scared of light", MyceliumRules.scaredBy(light) && retreated && !grewIntoLight,
                    "luz=" + light + ", se retiró=" + retreated + ", creció hacia la luz=" + grewIntoLight);
        } finally {
            Mycelium.setAnywhereForTest(false);
        }
    }

    /** Dos trozos con su nodo cada uno: rompes un nodo y solo muere su trozo. */
    private void checkBrokenNode(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(bx + 2, FLOOR + 1, bz + 2, bx + 13, CEILING - 1, bz + 4)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        // Suelo limpio (el crecimiento de antes pudo dejar nodos en él).
        for (BlockPos pos : BlockPos.betweenClosed(bx + 1, FLOOR, bz + 1, bx + 14, FLOOR, bz + 5)) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(tunnel(2, FLOOR), ModBlocks.MYCELIUM_NODE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(tunnel(11, FLOOR), ModBlocks.MYCELIUM_NODE.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int x : new int[] {3, 4, 5, 8, 9, 10}) {
            level.setBlock(tunnel(x, FLOOR + 1), Mycelium.veinStateAt(level, tunnel(x, FLOOR + 1)), Block.UPDATE_CLIENTS);
        }
        int aliveBefore = Mycelium.killIfOrphan(level, tunnel(4, FLOOR + 1));
        level.setBlock(tunnel(2, FLOOR), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        int killed = Mycelium.killIfOrphan(level, tunnel(4, FLOOR + 1));
        int otherKilled = Mycelium.killIfOrphan(level, tunnel(9, FLOOR + 1));
        boolean otherAlive = Mycelium.isVein(level.getBlockState(tunnel(8, FLOOR + 1)))
                && Mycelium.isVein(level.getBlockState(tunnel(10, FLOOR + 1)));
        boolean deadGone = level.getBlockState(tunnel(3, FLOOR + 1)).isAir() && level.getBlockState(tunnel(5, FLOOR + 1)).isAir();
        SelfTest.check("breaking a node kills its part of the network",
                aliveBefore == 0 && killed == 3 && otherKilled == 0 && otherAlive && deadGone,
                "antes=" + aliveBefore + ", muertos=" + killed + ", del otro nodo=" + otherKilled + ", el otro vive=" + otherAlive);
    }

    /** Esporín: aparece sobre un hilo a oscuras, alimenta la red y le asusta la luz. */
    private void checkSporeling(ServerLevel level) {
        BlockPos at = tunnel(9, FLOOR + 1);
        if (!Mycelium.isVein(level.getBlockState(at))) {
            level.setBlock(at, Mycelium.veinStateAt(level, at), Block.UPDATE_CLIENTS);
        }
        net.minecraft.world.entity.Entity entity = Mycelium.spawnSporeling(level, at);
        boolean spawned = entity instanceof io.github.hcuadrado428.stormroot.entity.Sporeling;
        boolean fed = false;
        float health = 0;
        if (spawned) {
            io.github.hcuadrado428.stormroot.entity.Sporeling sporeling = (io.github.hcuadrado428.stormroot.entity.Sporeling) entity;
            health = sporeling.getMaxHealth();
            Mycelium.setAnywhereForTest(true);
            try {
                for (int i = 0; i < 30 && !fed; i++) {
                    fed = sporeling.feedNetwork(level);
                }
            } finally {
                Mycelium.setAnywhereForTest(false);
            }
            entity.discard();
        }
        boolean scared = io.github.hcuadrado428.stormroot.entity.Sporeling.scaredOfLight(level, new BlockPos(bx + 8, FLOOR + 1, bz + 8))
                && !io.github.hcuadrado428.stormroot.entity.Sporeling.scaredOfLight(level, at);
        SelfTest.check("sporelings live on the network", spawned && health == 6.0F && fed && scared,
                "aparece=" + spawned + ", vida=" + health + ", alimenta la red=" + fed + ", huye de la luz=" + scared);
    }

    /** Tejedor: aparece, repara la red y sus golpes enredan (lentitud). */
    private void checkWeaver(ServerLevel level) {
        BlockPos at = tunnel(10, FLOOR + 1);
        if (!Mycelium.isVein(level.getBlockState(at))) {
            level.setBlock(at, Mycelium.veinStateAt(level, at), Block.UPDATE_CLIENTS);
        }
        io.github.hcuadrado428.stormroot.entity.Weaver weaver =
                Mycelium.spawn(io.github.hcuadrado428.stormroot.entity.ModEntities.WEAVER, level, at);
        boolean spawned = weaver != null;
        float health = spawned ? weaver.getMaxHealth() : 0;
        boolean repaired = false;
        boolean entangled = false;
        if (spawned) {
            Mycelium.setAnywhereForTest(true);
            try {
                for (int i = 0; i < 30 && !repaired; i++) {
                    repaired = weaver.repairNear(level);
                }
            } finally {
                Mycelium.setAnywhereForTest(false);
            }
            net.minecraft.world.entity.Entity victim = Mycelium.spawnSporeling(level, tunnel(8, FLOOR + 1));
            if (victim instanceof net.minecraft.world.entity.LivingEntity living) {
                io.github.hcuadrado428.stormroot.entity.Weaver.entangle(living);
                //? if >=1.21.11 {
                entangled = living.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
                //?} else {
                /*entangled = living.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                *///?}
                victim.discard();
            }
            weaver.discard();
        }
        SelfTest.check("weavers repair the network and entangle", spawned && health == 16.0F && repaired && entangled,
                "aparece=" + spawned + ", vida=" + health + ", repara=" + repaired + ", enreda=" + entangled);
    }

    /** Reglas: humedad y niebla aceleran, la sequía para; respirar la bruma 30 s marea. */
    private static void checkMistRules() {
        boolean growth = MyceliumRules.growthChance(-0.6, true) == 0
                && MyceliumRules.growthChance(0.5, true) > MyceliumRules.growthChance(0.5, false)
                && MyceliumRules.growthChance(0.5, false) > MyceliumRules.growthChance(0.0, false);
        int exposure = 0;
        int firstSick = -1;
        for (int s = 1; s <= 60 && firstSick < 0; s++) {
            exposure = MyceliumRules.expose(exposure, true);
            if (MyceliumRules.nauseous(exposure)) {
                firstSick = s;
            }
        }
        int recovered = exposure;
        for (int s = 0; s < 20; s++) {
            recovered = MyceliumRules.expose(recovered, false);
        }
        SelfTest.check("spore mist makes you sick after a while", growth && firstSick == MyceliumRules.NAUSEA_AFTER && recovered == 0,
                "crecimiento ok=" + growth + ", mareo a los " + firstSick + " s, exposición tras salir=" + recovered);
    }

    /** Sin toque de seda el nodo da esporas (y a veces una seta), no el nodo. */
    private void checkLoot(ServerLevel level) {
        BlockPos pos = tunnel(6, FLOOR);
        BlockState node = ModBlocks.MYCELIUM_NODE.defaultBlockState();
        int spores = 0;
        boolean nodeDropped = false;
        for (int i = 0; i < 4; i++) {
            for (ItemStack stack : Block.getDrops(node, level, pos, null)) {
                if (stack.is(ModItems.SPORES)) {
                    spores += stack.getCount();
                }
                nodeDropped |= stack.is(ModBlocks.MYCELIUM_NODE_ITEM);
            }
        }
        SelfTest.check("mycelium nodes drop spores", spores >= 4 && spores <= 12 && !nodeDropped,
                "esporas en 4 nodos=" + spores + ", soltó el nodo=" + nodeDropped);
    }

    private void checkCodec(ServerLevel level) {
        SeepageSavedData data = SeepageSavedData.get(level.getServer());
        long chunk = SeepageField.key(bx >> 4, bz >> 4);
        int id = data.mycelium().networkAt(chunk);
        Tag tag = SeepageSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data).result().orElseThrow();
        SeepageSavedData back = SeepageSavedData.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        SelfTest.check("mycelium networks are saved", id != 0 && back.mycelium().networkAt(chunk) == id,
                "red del chunk de pruebas=" + id + ", tras guardar=" + back.mycelium().networkAt(chunk));
    }

    private List<BlockPos> parts(ServerLevel level) {
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(bx + 1, FLOOR, bz + 1, bx + 14, CEILING, bz + 5)) {
            BlockState state = level.getBlockState(pos);
            if (Mycelium.isVein(state) || Mycelium.isNode(state)) {
                list.add(pos.immutable());
            }
        }
        return list;
    }

    private int count(ServerLevel level) {
        int n = 0;
        for (BlockPos pos : parts(level)) {
            if (Mycelium.isVein(level.getBlockState(pos))) {
                n++;
            }
        }
        return n;
    }
}
