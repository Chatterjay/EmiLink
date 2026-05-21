package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.client.AENetworkCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Response to AEBatchQueryPacket — returns count & craftability for each
 * queried item in the same order the batch was sent.
 */
public record AEBatchQueryResponsePacket(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<AEBatchQueryResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_batch_query_response"));

    public record Entry(ItemStack stack, long count, boolean craftable) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, AEBatchQueryResponsePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(RegistryFriendlyByteBuf buf, AEBatchQueryResponsePacket packet) {
                    buf.writeVarInt(packet.entries().size());
                    for (var entry : packet.entries()) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.stack());
                        buf.writeVarLong(entry.count());
                        buf.writeBoolean(entry.craftable());
                    }
                }

                @Override
                public AEBatchQueryResponsePacket decode(RegistryFriendlyByteBuf buf) {
                    int size = buf.readVarInt();
                    var entries = new ArrayList<Entry>(size);
                    for (int i = 0; i < size; i++) {
                        var stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                        long count = buf.readVarLong();
                        boolean craftable = buf.readBoolean();
                        entries.add(new Entry(stack, count, craftable));
                    }
                    return new AEBatchQueryResponsePacket(entries);
                }
            };

    public static void handle(final AEBatchQueryResponsePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.entries() == null) return;
            for (var entry : packet.entries()) {
                if (entry.stack() != null && !entry.stack().isEmpty()) {
                    AENetworkCache.receiveResponse(entry.stack(), entry.count(), entry.craftable());
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
