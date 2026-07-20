package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.util.ServerIPNState;

import java.util.function.Supplier;

public record AELockedSlotsPacket(int[] lockedSlots, int clickedSlotIndex) {

    public static void encode(AELockedSlotsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.lockedSlots.length);
        for (int v : msg.lockedSlots) buf.writeVarInt(v);
        buf.writeVarInt(msg.clickedSlotIndex);
    }

    public static AELockedSlotsPacket decode(FriendlyByteBuf buf) {
        int len = buf.readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = buf.readVarInt();
        int clickedSlot = buf.readVarInt();
        return new AELockedSlotsPacket(arr, clickedSlot);
    }

    private void handleInServer(Player player) {
        if (player == null) return;
        ServerIPNState.setLockedSlots(player.getUUID(), lockedSlots);
    }

    public static void handle(AELockedSlotsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        PacketHelper.handleServerBound(ctx, () -> msg.handleInServer(ctx.get().getSender()));
    }
}
