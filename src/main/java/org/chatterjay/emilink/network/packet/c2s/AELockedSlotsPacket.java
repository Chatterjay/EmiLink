package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.util.ServerIPNState;

import java.util.function.Supplier;

/**
 * Client→Server: Sends IPN-locked slot indices before an AE Space+click.
 * The server stores these so IPNLockHandler can protect them during MOVE_REGION.
 */
public class AELockedSlotsPacket {
    private final int[] lockedSlots;

    public AELockedSlotsPacket(int[] lockedSlots) {
        this.lockedSlots = lockedSlots;
    }

    public static void encode(AELockedSlotsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.lockedSlots.length);
        for (int v : msg.lockedSlots) buf.writeVarInt(v);
    }

    public static AELockedSlotsPacket decode(FriendlyByteBuf buf) {
        int len = buf.readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = buf.readVarInt();
        return new AELockedSlotsPacket(arr);
    }

    public static void handle(AELockedSlotsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerIPNState.setLockedSlots(player.getUUID(), msg.lockedSlots);
            }
        });
        context.setPacketHandled(true);
    }
}
