package io.github.hcuadrado428.stormroot.entity;

import io.github.hcuadrado428.stormroot.ModIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Tipos de entidad de Stormroot (el registro lo hace cada loader en {@code Stormroot.java}). */
public final class ModEntities {
    public static final String SPORELING_ID = "sporeling";

    /** Esporín: habitante pequeño y pasivo de la red de micelio. */
    public static EntityType<Sporeling> SPORELING;

    private ModEntities() {
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
