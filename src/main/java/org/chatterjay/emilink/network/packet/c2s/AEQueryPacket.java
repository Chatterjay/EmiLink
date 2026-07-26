package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.network.AE2GridQueryUtil;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.network.PacketRateLimiter;
import org.chatterjay.emilink.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emilink.util.ModLogger;

import java.util.function.Supplier;

public class AEQueryPacket {
    private final ItemStack stack;

    public AEQueryPacket(ItemStack stack) {
        this.stack = stack;
    }

    public static void encode(AEQueryPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static AEQueryPacket decode(FriendlyByteBuf buf) {
        return new AEQueryPacket(buf.readItem());
    }

    public static void handle(AEQueryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        if (!PacketRateLimiter.allowDebugPacket()) {
            ModLogger.debug("AEQuery rate limited (dropped)");
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> msg.handleInServer(context));
        context.setPacketHandled(true);
    }

    private void handleInServer(NetworkEvent.Context context) {
        Player player = context.getSender();
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
            NetworkHandler.sendToPlayer(sp, new AEQueryResponsePacket(stack, count, craftable));
        }
    }
}
