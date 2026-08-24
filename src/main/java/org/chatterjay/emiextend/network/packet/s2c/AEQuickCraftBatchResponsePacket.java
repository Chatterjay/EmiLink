package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.client.InputEvents;

/** Server acknowledgement for an exact AE BOM craft batch. */
public record AEQuickCraftBatchResponsePacket(long requestId, int crafted, int delivered, boolean resultSlotEmpty)
        implements CustomPacketPayload {

    public static final Type<AEQuickCraftBatchResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_quick_craft_batch_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEQuickCraftBatchResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, AEQuickCraftBatchResponsePacket::requestId,
                    ByteBufCodecs.VAR_INT, AEQuickCraftBatchResponsePacket::crafted,
                    ByteBufCodecs.VAR_INT, AEQuickCraftBatchResponsePacket::delivered,
                    ByteBufCodecs.BOOL, AEQuickCraftBatchResponsePacket::resultSlotEmpty,
                    AEQuickCraftBatchResponsePacket::new
            );

    public static void handle(AEQuickCraftBatchResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> InputEvents.onAeQuickCraftBatchResponse(
                packet.requestId(), packet.crafted(), packet.delivered(), packet.resultSlotEmpty()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
