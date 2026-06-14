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
import org.chatterjay.emiextend.network.PacketRateLimiter;
import org.chatterjay.emiextend.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emiextend.util.ModLogger;

public record AEQueryPacket(ItemStack stack) implements CustomPacketPayload {
    public static final Type<AEQueryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEQueryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    AEQueryPacket::stack,
                    AEQueryPacket::new
            );

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null || stack == null || stack.isEmpty()) return;
        if (!AE2Proxy.isLoaded()) {
            sendResponse(player, 0, false);
            return;
        }

        long count = 0;
        boolean craftable = false;

        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(menu)) {
                sendResponse(player, count, craftable);
                return;
            }

            Object grid = AE2GridQueryUtil.resolveGrid(aeBaseMenuClass, menu);
            if (grid == null) {
                sendResponse(player, count, craftable);
                return;
            }

            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, stack);
            if (aeKey == null) {
                sendResponse(player, count, craftable);
                return;
            }

            count = AE2GridQueryUtil.queryItemCount(grid, aeKey);
            craftable = AE2GridQueryUtil.queryCraftability(grid, aeKey);

        } catch (Exception e) {
        }

        sendResponse(player, count, craftable);
    }

    private void sendResponse(Player player, long count, boolean craftable) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new AEQueryResponsePacket(stack, count, craftable));
        }
    }

    public static void handle(final AEQueryPacket packet, final IPayloadContext context) {
        if (packet != null && context.flow() == PacketFlow.SERVERBOUND) {
            if (!PacketRateLimiter.allowDebugPacket()) {
                ModLogger.debug("AEQuery rate limited (dropped)");
                return;
            }
            context.enqueueWork(() -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
