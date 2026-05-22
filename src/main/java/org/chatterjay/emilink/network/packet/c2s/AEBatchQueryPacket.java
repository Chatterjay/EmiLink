package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.network.PacketRateLimiter;
import org.chatterjay.emilink.network.packet.s2c.AEBatchQueryResponsePacket;
import org.chatterjay.emilink.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class AEBatchQueryPacket {
    private final List<ItemStack> stacks;

    public AEBatchQueryPacket(List<ItemStack> stacks) {
        this.stacks = stacks;
    }

    public static void encode(AEBatchQueryPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.stacks.size());
        for (var stack : msg.stacks) {
            buf.writeItem(stack);
        }
    }

    public static AEBatchQueryPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        var stacks = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(buf.readItem());
        }
        return new AEBatchQueryPacket(stacks);
    }

    public static void handle(AEBatchQueryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        if (!PacketRateLimiter.allowDebugPacket()) {
            ModLogger.debug("AEBatchQuery rate limited (dropped)");
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> msg.handleInServer(context));
        context.setPacketHandled(true);
    }

    private void handleInServer(NetworkEvent.Context context) {
        Player player = context.getSender();
        if (player == null || stacks == null || stacks.isEmpty()) return;
        if (!AE2Proxy.isLoaded()) {
            sendResponse(player, List.of());
            return;
        }

        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(menu)) {
                sendResponse(player, List.of());
                return;
            }

            Object grid = AEQueryPacket.resolveGrid(aeBaseMenuClass, menu);
            if (grid == null) {
                sendResponse(player, List.of());
                return;
            }

            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            var ofMethod = aeItemKeyClass.getMethod("of", ItemStack.class);

            var entries = new ArrayList<AEBatchQueryResponsePacket.Entry>();
            for (var stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                Object aeKey = ofMethod.invoke(null, stack);
                if (aeKey == null) continue;

                long count = AEQueryPacket.queryItemCount(grid, aeKey);
                boolean craftable = AEQueryPacket.queryCraftability(grid, aeKey);
                entries.add(new AEBatchQueryResponsePacket.Entry(stack, count, craftable));
            }

            sendResponse(player, entries);
        } catch (Exception e) {
            sendResponse(player, List.of());
        }
    }

    private void sendResponse(Player player, List<AEBatchQueryResponsePacket.Entry> entries) {
        if (player instanceof ServerPlayer sp) {
            NetworkHandler.sendToPlayer(sp, new AEBatchQueryResponsePacket(entries));
        }
    }
}
