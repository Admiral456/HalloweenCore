package cz.halloween.mod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class CursedTalismanItem extends Item {
    public CursedTalismanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (!HalloweenWorldPolicy.isAllowed(level)) {
                serverPlayer.displayClientMessage(Component.literal(
                        "HalloweenCore: tento svět není povolen pro Halloween obsah."), true);
                return InteractionResultHolder.fail(stack);
            }
            HalloweenGameplay.activateTalisman(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
