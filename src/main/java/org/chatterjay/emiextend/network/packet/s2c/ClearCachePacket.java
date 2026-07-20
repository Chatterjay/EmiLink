package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.client.AENetworkCache;

import java.util.function.Supplier;

public record ClearCachePacket() {

    public static void encode(ClearCachePacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static ClearCachePacket decode(FriendlyByteBuf buf) {
        return new ClearCachePacket();
    }

    public static void handle(ClearCachePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(AENetworkCache::clear);
        ctx.get().setPacketHandled(true);
    }
}
