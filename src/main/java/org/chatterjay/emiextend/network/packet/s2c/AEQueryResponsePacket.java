package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.client.AENetworkCache;

public record AEQueryResponsePacket(ItemStack stack, long count, boolean craftable) implements CustomPacketPayload {
    public static final Type<AEQueryResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_query_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEQueryResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    AEQueryResponsePacket::stack,
                    ByteBufCodecs.VAR_LONG,
                    AEQueryResponsePacket::count,
                    ByteBufCodecs.BOOL,
                    AEQueryResponsePacket::craftable,
                    AEQueryResponsePacket::new
            );

    public static void handle(final AEQueryResponsePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> AENetworkCache.receiveResponse(packet.stack(), packet.count(), packet.craftable()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
