package org.chatterjay.emiextend.network.packet.c2s;

import io.netty.buffer.ByteBuf;
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

public record TransferMatchingPacket(ItemStack clickedStack, int mode, int[] lockedSlots) implements CustomPacketPayload {
    // mode: 0 = network→player (matching items), 1 = main inventory→network, 2 = hotbar→network

    public static final Type<TransferMatchingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "transfer_matching"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransferMatchingPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    TransferMatchingPacket::clickedStack,
                    ByteBufCodecs.BYTE.map(b -> (int) b, i -> (byte) (int) i),
                    TransferMatchingPacket::mode,
                    new StreamCodec<RegistryFriendlyByteBuf, int[]>() {
                        @Override
                        public int[] decode(RegistryFriendlyByteBuf buf) {
                            int len = buf.readVarInt();
                            int[] arr = new int[len];
                            for (int i = 0; i < len; i++) arr[i] = buf.readVarInt();
                            return arr;
                        }

                        @Override
                        public void encode(RegistryFriendlyByteBuf buf, int[] arr) {
                            buf.writeVarInt(arr.length);
                            for (int v : arr) buf.writeVarInt(v);
                        }
                    },
                    TransferMatchingPacket::lockedSlots,
                    TransferMatchingPacket::new
            );

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null || clickedStack == null || clickedStack.isEmpty()) return;

        if (mode == 0) {
            BDProxy.extractAllFromNetwork(player, clickedStack, lockedSlots);
        } else {
            BDProxy.depositToNetwork(player, mode, lockedSlots);
        }
    }

    public static void handle(final TransferMatchingPacket packet, final IPayloadContext context) {
        if (packet != null && context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
