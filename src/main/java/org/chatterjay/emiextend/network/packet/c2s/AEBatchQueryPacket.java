package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.network.AE2GridQueryUtil;
import org.chatterjay.emiextend.network.packet.s2c.AEBatchQueryResponsePacket;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch AE network query — client sends multiple items at once, server responds
 * with count & craftability for each. Delegates query logic to AE2GridQueryUtil.
 */
public record AEBatchQueryPacket(List<ItemStack> stacks) implements CustomPacketPayload {
    public static final Type<AEBatchQueryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_batch_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEBatchQueryPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(RegistryFriendlyByteBuf buf, AEBatchQueryPacket packet) {
                    buf.writeVarInt(packet.stacks().size());
                    for (var stack : packet.stacks()) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                    }
                }

                @Override
                public AEBatchQueryPacket decode(RegistryFriendlyByteBuf buf) {
                    int size = buf.readVarInt();
                    var stacks = new ArrayList<ItemStack>(size);
                    for (int i = 0; i < size; i++) {
                        stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    }
                    return new AEBatchQueryPacket(stacks);
                }
            };

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null || stacks == null || stacks.isEmpty()) return;
        if (!AE2Proxy.isLoaded()) return;

        var results = new ArrayList<AEBatchQueryResponsePacket.Entry>();

        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(menu)) return;

            Object grid = AE2GridQueryUtil.resolveGrid(aeBaseMenuClass, menu);
            if (grid == null) return;

            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");

            for (var stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;

                long count = 0;
                boolean craftable = false;

                try {
                    Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, stack);
                    if (aeKey != null) {
                        count = AE2GridQueryUtil.queryItemCount(grid, aeKey);
                        craftable = AE2GridQueryUtil.queryCraftability(grid, aeKey);
                    }
                } catch (Exception e) {
                    // skip item
                }

                results.add(new AEBatchQueryResponsePacket.Entry(stack, count, craftable));
            }
        } catch (Exception e) {
            ModLogger.debug("AEBatchQuery: error resolving grid: {}", e.getMessage());
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new AEBatchQueryResponsePacket(results));
        }
    }

    public static void handle(final AEBatchQueryPacket packet, final IPayloadContext context) {
        if (packet != null && context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
