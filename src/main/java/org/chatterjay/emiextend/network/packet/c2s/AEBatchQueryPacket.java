package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.network.AE2GridQueryUtil;
import org.chatterjay.emiextend.network.EmiLinkNetwork;
import org.chatterjay.emiextend.network.packet.s2c.AEBatchQueryResponsePacket;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Batch AE network query — client sends multiple items at once, server responds
 * with count & craftability for each. Delegates query logic to AE2GridQueryUtil.
 */
public record AEBatchQueryPacket(List<ItemStack> stacks) {

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

    private void handleInServer(Player player) {
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

        if (player instanceof ServerPlayer sp) {
            EmiLinkNetwork.sendTo(new AEBatchQueryResponsePacket(results), sp);
        }
    }

    public static void handle(AEBatchQueryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> msg.handleInServer(context.getSender()));
        }
        context.setPacketHandled(true);
    }
}
