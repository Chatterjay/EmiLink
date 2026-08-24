package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.client.InputEvents;

/** Client acknowledgement for a BD BOM batch. */
public record BDBatchCraftResponsePacket(long requestId, int crafted, int delivered)
        implements CustomPacketPayload {

    public static final Type<BDBatchCraftResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "bd_batch_craft_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BDBatchCraftResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, BDBatchCraftResponsePacket::requestId,
                    ByteBufCodecs.VAR_INT, BDBatchCraftResponsePacket::crafted,
                    ByteBufCodecs.VAR_INT, BDBatchCraftResponsePacket::delivered,
                    BDBatchCraftResponsePacket::new
            );

    public static void handle(BDBatchCraftResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> InputEvents.onBdBatchCraftResponse(
                packet.requestId(), packet.crafted(), packet.delivered()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
