package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.network.packet.s2c.BDBatchCraftResponsePacket;

/** Requests several consecutive BD result-slot takes in one server task. */
public record BDBatchCraftPacket(int amount, boolean toNetwork, long requestId)
        implements CustomPacketPayload {

    public static final Type<BDBatchCraftPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "bd_batch_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BDBatchCraftPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BDBatchCraftPacket::amount,
                    ByteBufCodecs.BOOL, BDBatchCraftPacket::toNetwork,
                    ByteBufCodecs.VAR_LONG, BDBatchCraftPacket::requestId,
                    BDBatchCraftPacket::new
            );

    private void handleInServer(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (amount <= 0 || amount > 256 || !BDProxy.isLoaded()) {
            return;
        }

        var result = BDProxy.batchCraft(player, amount, toNetwork);
        PacketDistributor.sendToPlayer(player,
                new BDBatchCraftResponsePacket(requestId, result.crafted(), result.delivered()));
    }

    public static void handle(BDBatchCraftPacket packet, IPayloadContext context) {
        if (packet != null) {
            PacketHelper.handleServerBound(context, () -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
