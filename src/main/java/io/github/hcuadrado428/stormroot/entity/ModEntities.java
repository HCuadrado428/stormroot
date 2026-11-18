package io.github.hcuadrado428.stormroot.entity;

import io.github.hcuadrado428.stormroot.ModIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Tipos de entidad de Stormroot (el registro lo hace cada loader en {@code Stormroot.java}). */
public final class ModEntities {
    public static final String SPORELING_ID = "sporeling";
    public static final String WEAVER_ID = "weaver";
    public static final String GUARDIAN_ID = "mycelium_guardian";

    /** Esporín: habitante pequeño y pasivo de la red de micelio. */
    public static EntityType<Sporeling> SPORELING;
    /** Tejedor: habitante neutral que repara la red y te enreda si la atacas. */
    public static EntityType<Weaver> WEAVER;
    /** Guardián de micelio: el defensor de las redes grandes. */
    public static EntityType<MyceliumGuardian> GUARDIAN;

    private ModEntities() {
    }

    // Los tipos se crean la primera vez que se piden: en Forge/NeoForge los objetos (huevos) se registran antes que
    // las entidades, y los huevos necesitan el tipo ya creado. El registro de entidades usa el mismo objeto.
    public static EntityType<Sporeling> sporeling() {
        if (SPORELING == null) {
            SPORELING = createSporeling();
        }
        return SPORELING;
    }

    public static EntityType<Weaver> weaver() {
        if (WEAVER == null) {
            WEAVER = createWeaver();
        }
        return WEAVER;
    }

    public static EntityType<MyceliumGuardian> guardian() {
        if (GUARDIAN == null) {
            GUARDIAN = createGuardian();
        }
        return GUARDIAN;
    }

    public static EntityType<MyceliumGuardian> createGuardian() {
        EntityType.Builder<MyceliumGuardian> builder = EntityType.Builder.of(MyceliumGuardian::new, MobCategory.MONSTER)
                .sized(1.4F, 2.4F).clientTrackingRange(10);
        //? if >=1.21.2 {
        return builder.build(ResourceKey.create(Registries.ENTITY_TYPE, ModIds.id(GUARDIAN_ID)));
        //?} else {
        /*return builder.build(GUARDIAN_ID);
        *///?}
    }

    public static EntityType<Weaver> createWeaver() {
        EntityType.Builder<Weaver> builder = EntityType.Builder.of(Weaver::new, MobCategory.CREATURE).sized(0.9F, 0.6F)
                .clientTrackingRange(8);
        //? if >=1.21.2 {
        return builder.build(ResourceKey.create(Registries.ENTITY_TYPE, ModIds.id(WEAVER_ID)));
        //?} else {
        /*return builder.build(WEAVER_ID);
        *///?}
    }

    public static EntityType<Sporeling> createSporeling() {
        EntityType.Builder<Sporeling> builder = EntityType.Builder.of(Sporeling::new, MobCategory.AMBIENT).sized(0.5F, 0.5F)
                .clientTrackingRange(8);
        //? if >=1.21.2 {
        return builder.build(ResourceKey.create(Registries.ENTITY_TYPE, ModIds.id(SPORELING_ID)));
        //?} else {
        /*return builder.build(SPORELING_ID);
        *///?}
    }
}
