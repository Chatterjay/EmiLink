package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.util.ModLogger;

public record BDActionPacket(ItemStack targetStack, int action) implements CustomPacketPayload {
    // action: 0 = extract from network to player inventory
    // action: 1 = mass craft (Space+Click on result slot)

    public static final Type<BDActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "bd_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BDActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BDActionPacket::targetStack,
                    ByteBufCodecs.VAR_INT,
                    BDActionPacket::action,
                    BDActionPacket::new
            );

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null) return;

        switch (action) {
            case 0 -> {
                if (targetStack == null || targetStack.isEmpty()) return;
                ModLogger.debug("BDActionPacket: extract from network: {}", targetStack.getHoverName().getString());
                BDProxy.extractFromNetwork(player, targetStack);
            }
            case 1 -> {
                ModLogger.debug("BDActionPacket: mass craft");
                BDProxy.massCraft(player);
            }
        }
    }

    public static void handle(final BDActionPacket packet, final IPayloadContext context) {
        if (packet != null && context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
