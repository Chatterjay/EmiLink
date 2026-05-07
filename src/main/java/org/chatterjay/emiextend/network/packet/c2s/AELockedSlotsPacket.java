package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.util.ServerIPNState;

public record AELockedSlotsPacket(int[] lockedSlots, int clickedSlotIndex) implements CustomPacketPayload {
    public static final Type<AELockedSlotsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_locked_slots"));

    public static final StreamCodec<FriendlyByteBuf, AELockedSlotsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AELockedSlotsPacket decode(FriendlyByteBuf buf) {
                    int len = buf.readVarInt();
                    int[] arr = new int[len];
                    for (int i = 0; i < len; i++) arr[i] = buf.readVarInt();
                    int clickedSlot = buf.readVarInt();
                    return new AELockedSlotsPacket(arr, clickedSlot);
                }

                @Override
                public void encode(FriendlyByteBuf buf, AELockedSlotsPacket packet) {
                    buf.writeVarInt(packet.lockedSlots.length);
                    for (int v : packet.lockedSlots) buf.writeVarInt(v);
                    buf.writeVarInt(packet.clickedSlotIndex);
                }
            };

    private void handleInServer(final IPayloadContext context) {
        var player = context.player();
        if (player == null) return;

        ServerIPNState.setLockedSlots(player.getUUID(), lockedSlots);
    }

    public static void handle(final AELockedSlotsPacket packet, final IPayloadContext context) {
        if (packet != null && context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
