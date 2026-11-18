package io.github.hcuadrado428.stormroot.item;

import net.minecraft.world.item.Item;

/**
 * Gafas de arena: se llevan en la cabeza. Dentro de una tormenta de arena evitan la ceguera y dejan ver el doble de lejos
 * ({@code weather/Sandstorms}, {@code client/ClientWeather}). Desde 1.21.2 se equipan con el componente {@code equippable}
 * (lo pone {@code ModItems}); antes, con la interfaz {@code Equipable}, como la calabaza tallada.
 */
//? if >=1.21.2 {
public class SandGogglesItem extends Item {
    public SandGogglesItem(Properties properties) {
        super(properties);
    }
}
//?} else {
/*public class SandGogglesItem extends Item implements net.minecraft.world.item.Equipable {
    public SandGogglesItem(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.entity.EquipmentSlot getEquipmentSlot() {
        return net.minecraft.world.entity.EquipmentSlot.HEAD;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }
}
*///?}
