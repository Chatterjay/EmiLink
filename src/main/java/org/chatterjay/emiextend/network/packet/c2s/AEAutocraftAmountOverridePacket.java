package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.util.AeAutocraftAmountOverride;

public record AEAutocraftAmountOverridePacket(int amount) implements CustomPacketPayload {
    public static final Type<AEAutocraftAmountOverridePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_autocraft_amount_override"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEAutocraftAmountOverridePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AEAutocraftAmountOverridePacket::amount,
                    AEAutocraftAmountOverridePacket::new
            );

    void handleInServer(final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            AeAutocraftAmountOverride.set(serverPlayer, amount);
        }
    }

    public static void handle(final AEAutocraftAmountOverridePacket packet, final IPayloadContext context) {
        PacketHelper.handleServerBound(context, () -> packet.handleInServer(context));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
