package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.client.AENetworkCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Response to AEBatchQueryPacket — returns count & craftability for each
 * queried item in the same order the batch was sent.
 */
public record AEBatchQueryResponsePacket(List<Entry> entries) {

    public record Entry(ItemStack stack, long count, boolean craftable) {}

    public static void encode(AEBatchQueryResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (var entry : msg.entries) {
            buf.writeItem(entry.stack);
            buf.writeVarLong(entry.count);
            buf.writeBoolean(entry.craftable);
        }
    }

    public static AEBatchQueryResponsePacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = buf.readItem();
            long count = buf.readVarLong();
            boolean craftable = buf.readBoolean();
            entries.add(new Entry(stack, count, craftable));
        }
        return new AEBatchQueryResponsePacket(entries);
    }

    public static void handle(AEBatchQueryResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.entries == null) return;
            for (var entry : msg.entries) {
                if (entry.stack != null && !entry.stack.isEmpty()) {
                    AENetworkCache.receiveResponse(entry.stack, entry.count, entry.craftable);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
