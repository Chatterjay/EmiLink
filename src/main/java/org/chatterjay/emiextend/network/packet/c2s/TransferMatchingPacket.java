package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.network.PacketHelper;

import java.util.function.Supplier;

public record TransferMatchingPacket(ItemStack clickedStack, int mode, int[] lockedSlots) {
    // mode: 0 = network→player (matching items), 1 = main inventory→network, 2 = hotbar→network

    public static void encode(TransferMatchingPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.clickedStack);
        buf.writeByte(msg.mode);
        buf.writeVarInt(msg.lockedSlots.length);
        for (int v : msg.lockedSlots) buf.writeVarInt(v);
    }

    public static TransferMatchingPacket decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int mode = buf.readByte();
        int len = buf.readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = buf.readVarInt();
        return new TransferMatchingPacket(stack, mode, arr);
    }

    private void handleInServer(Player player) {
        if (player == null || clickedStack == null || clickedStack.isEmpty()) return;

        if (mode == 0) {
            BDProxy.extractAllFromNetwork(player, clickedStack, lockedSlots);
        } else {
            BDProxy.depositToNetwork(player, mode, lockedSlots);
        }
    }

    public static void handle(TransferMatchingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        PacketHelper.handleServerBound(ctx, () -> msg.handleInServer(ctx.get().getSender()));
    }
}
