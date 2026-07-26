package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.util.ModLogger;

import java.util.function.Supplier;

public class TransferFromContainerPacket {
    private final ItemStack template;
    private final boolean allMatching;

    public TransferFromContainerPacket(ItemStack template, boolean allMatching) {
        this.template = template;
        this.allMatching = allMatching;
    }

    public static void encode(TransferFromContainerPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.template);
        buf.writeBoolean(msg.allMatching);
    }

    public static TransferFromContainerPacket decode(FriendlyByteBuf buf) {
        return new TransferFromContainerPacket(buf.readItem(), buf.readBoolean());
    }

    public static void handle(TransferFromContainerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> handleServer(context, msg));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, TransferFromContainerPacket msg) {
        ServerPlayer player = context.getSender();
        if (player == null || msg.template == null || msg.template.isEmpty()) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) return;

        int moved = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot == null || !slot.hasItem()) continue;
            if (slot.container instanceof Inventory) continue;

            ItemStack stack = slot.getItem();
            if (!ItemStack.isSameItemSameTags(stack, msg.template)) continue;

            ItemStack before = stack.copy();
            menu.quickMoveStack(player, i);
            ItemStack after = slot.getItem();
            if (after.isEmpty() || after.getCount() != before.getCount() || !ItemStack.isSameItemSameTags(after, before)) {
                moved++;
            }
            if (!msg.allMatching) break;
        }

        if (moved > 0) {
            menu.broadcastChanges();
        }
        ModLogger.debug("TransferFromContainer: moved={} item={} all={}",
                moved, msg.template.getHoverName().getString(), msg.allMatching);
    }
}
