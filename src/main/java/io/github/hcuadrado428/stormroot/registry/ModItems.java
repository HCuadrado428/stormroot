package io.github.hcuadrado428.stormroot.registry;

import io.github.hcuadrado428.stormroot.ModIds;
import net.minecraft.world.item.Item;

//? if >=1.21.2 {
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
//?}

/** Items del mod. La creación es común; el registro lo hace cada loader en Stormroot. */
public final class ModItems {
    /** Cristal que se forma donde cae un rayo sobre arena o piedra (ver weather/LightningCrystals). */
    public static Item LIGHTNING_CRYSTAL;

    private ModItems() {
    }

    /** Resistente al fuego: nace donde cae un rayo, que suele dejar fuego encima. */
    public static Item createLightningCrystal() {
        return new Item(properties("lightning_crystal").fireResistant());
    }

    public static Item create(String name) {
        return new Item(properties(name));
    }

    private static Item.Properties properties(String name) {
        Item.Properties properties = new Item.Properties();
        //? if >=1.21.2 {
        // Desde 1.21.2 cada item necesita conocer su clave antes de construirse.
        properties = properties.setId(ResourceKey.create(Registries.ITEM, ModIds.id(name)));
        //?}
        return properties;
    }
}
