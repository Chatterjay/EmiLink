package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.network.PacketHelper;

import java.util.function.Supplier;

public record BDActionPacket(ItemStack targetStack, int action) {
    // action: 0 = extract from network to player inventory
    // action: 1 = mass craft (Space+Click on result slot)
    // action: 2 = single craft (Ctrl+Click on result slot or B key)
    // action: 3 = BoM quick craft: make BD return crafting-grid leftovers to network
    // action: 4 = BoM quick craft: restore BD crafting-grid return direction
    // action: 5 = deposit matching player-inventory stacks to BD network
    // action: 6 = clean BD crafting-grid leftovers to BD network
    // action: 7 = single craft result directly into BD network

    public static void encode(BDActionPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.targetStack);
        buf.writeVarInt(msg.action);
    }

    public static BDActionPacket decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int action = buf.readVarInt();
        return new BDActionPacket(stack, action);
    }

    private void handleInServer(Player player) {
        switch (action) {
            case 0 -> {
                if (targetStack == null || targetStack.isEmpty()) return;
                BDProxy.extractFromNetwork(player, targetStack);
            }
            case 1 -> BDProxy.massCraft(player);
            case 2 -> BDProxy.singleCraft(player);
            case 3 -> BDProxy.beginQuickCraftReturnToInventory(player);
            case 4 -> BDProxy.restoreQuickCraftReturnDirection(player);
            case 5 -> {
                if (targetStack == null || targetStack.isEmpty()) return;
                BDProxy.depositMatchingToNetwork(player, targetStack);
            }
            case 6 -> BDProxy.cleanCraftSlotsToNetwork(player);
            case 7 -> BDProxy.singleCraftToNetwork(player);
        }
    }

    public static void handle(BDActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        PacketHelper.handleServerBound(ctx, () -> {
            Player player = ctx.get().getSender();
            if (player != null) {
                msg.handleInServer(player);
            }
        });
    }
}
