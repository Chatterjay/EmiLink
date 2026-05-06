package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.client.AENetworkCache;

public record ClearCachePacket() implements CustomPacketPayload {
    public static final Type<ClearCachePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "clear_cache"));

    public static final StreamCodec<FriendlyByteBuf, ClearCachePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new ClearCachePacket()
            );

    public static void handle(final ClearCachePacket packet, final IPayloadContext context) {
        context.enqueueWork(AENetworkCache::clear);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
