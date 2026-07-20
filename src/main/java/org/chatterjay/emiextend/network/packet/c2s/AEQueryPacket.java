package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.network.AE2GridQueryUtil;
import org.chatterjay.emiextend.network.EmiLinkNetwork;
import org.chatterjay.emiextend.network.PacketRateLimiter;
import org.chatterjay.emiextend.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.function.Supplier;

public record AEQueryPacket(ItemStack stack) {

    public static void encode(AEQueryPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static AEQueryPacket decode(FriendlyByteBuf buf) {
        return new AEQueryPacket(buf.readItem());
    }

    private void handleInServer(Player player) {
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
            // ignore
        }

        sendResponse(player, count, craftable);
    }

    private void sendResponse(Player player, long count, boolean craftable) {
        if (player instanceof ServerPlayer sp) {
            EmiLinkNetwork.sendTo(new AEQueryResponsePacket(stack, count, craftable), sp);
        }
    }

    public static void handle(AEQueryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            if (!PacketRateLimiter.allowDebugPacket()) {
                ModLogger.debug("AEQuery rate limited (dropped)");
                context.setPacketHandled(true);
                return;
            }
            context.enqueueWork(() -> msg.handleInServer(context.getSender()));
        }
        context.setPacketHandled(true);
    }
}
