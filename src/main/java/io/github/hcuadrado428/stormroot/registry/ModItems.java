package io.github.hcuadrado428.stormroot.registry;

import io.github.hcuadrado428.stormroot.ModIds;
import net.minecraft.world.item.Item;

//? if >=1.21.2 {
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
//?}

/** Items del mod. La creación es común; el registro lo hace cada loader en Stormroot. */
public final class ModItems {
    /** Cristal que se forma donde cae un rayo (MVP: de momento solo existe como item). */
    public static Item LIGHTNING_CRYSTAL;
    /** Granizo: lo deja una granizada en el suelo; 4 hacen un bloque de hielo (refrigerante). */
    public static Item HAILSTONE;
    /** Polvo de aurora: cae del cielo durante una aurora, de noche (ingrediente de final de juego, más adelante). */
    public static Item AURORA_DUST;
    /** Fragmento de arcoíris: el tesoro del final del arcoíris (ingrediente, más adelante). */
    public static Item RAINBOW_SHARD;
    /** Esporas: las sueltan los nodos de la red de micelio (ingrediente, más adelante). */
    public static Item SPORES;

    private ModItems() {
    }

    public static Item create(String name) {
        Item.Properties properties = new Item.Properties();
        //? if >=1.21.2 {
        // Desde 1.21.2 cada item necesita conocer su clave antes de construirse.
        properties = properties.setId(ResourceKey.create(Registries.ITEM, ModIds.id(name)));
        //?}
        return new Item(properties);
    }
}
