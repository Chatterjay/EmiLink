package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.network.PacketHelper;

public record BDDepositSlotPacket(int slotIndex) implements CustomPacketPayload {
    public static final Type<BDDepositSlotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "bd_deposit_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BDDepositSlotPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    BDDepositSlotPacket::slotIndex,
                    BDDepositSlotPacket::new
            );

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null) {
            return;
        }
        BDProxy.depositSlotToNetwork(player, slotIndex);
    }

    public static void handle(final BDDepositSlotPacket packet, final IPayloadContext context) {
        PacketHelper.handleServerBound(context, () -> packet.handleInServer(context));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
