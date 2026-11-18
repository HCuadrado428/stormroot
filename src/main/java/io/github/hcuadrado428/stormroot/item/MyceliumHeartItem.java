package io.github.hcuadrado428.stormroot.item;

import io.github.hcuadrado428.stormroot.mycel.Mycelium;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Corazón de micelio: lo suelta el guardián. Úsalo sobre un nodo de micelio y su red pasa a ser tuya: solo crece por
 * roca y tierra naturales (no trepa por tus construcciones) y sus tejedores te ayudan contra los monstruos.
 */
public class MyceliumHeartItem extends Item {
    public MyceliumHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!Mycelium.isNode(context.getLevel().getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel server) {
            if (!Mycelium.tame(server, context.getClickedPos())) {
                return InteractionResult.FAIL;
            }
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
