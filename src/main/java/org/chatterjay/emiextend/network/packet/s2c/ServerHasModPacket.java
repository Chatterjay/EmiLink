package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.client.BDShortcutHandler;

public record ServerHasModPacket() implements CustomPacketPayload {
    public static final Type<ServerHasModPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "server_has_mod"));

    public static final StreamCodec<FriendlyByteBuf, ServerHasModPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new ServerHasModPacket()
            );

    public static void handle(final ServerHasModPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> BDShortcutHandler.serverHasMod = true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
