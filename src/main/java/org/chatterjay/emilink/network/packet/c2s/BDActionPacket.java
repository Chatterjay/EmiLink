package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.integration.BDProxy;
import org.chatterjay.emilink.util.ModLogger;

import java.util.function.Supplier;

public class BDActionPacket {
    // action: 0 = extract from network to player inventory
    // action: 1 = mass craft (Space+Click on result slot)
    // action: 2 = single craft (Ctrl+Click on result slot or B key)
    private final ItemStack targetStack;
    private final int action;

    public BDActionPacket(ItemStack targetStack, int action) {
        this.targetStack = targetStack;
        this.action = action;
    }

    public static void encode(BDActionPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.targetStack);
        buf.writeVarInt(msg.action);
    }

    public static BDActionPacket decode(FriendlyByteBuf buf) {
        return new BDActionPacket(buf.readItem(), buf.readVarInt());
    }

    public static void handle(BDActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> handleServer(context, msg));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, BDActionPacket msg) {
        ServerPlayer player = context.getSender();
        if (player == null) return;

        ModLogger.info("BDActionPacket: handling action={}", msg.action);

        switch (msg.action) {
            case 0 -> {
                if (msg.targetStack == null || msg.targetStack.isEmpty()) {
                    ModLogger.info("BDActionPacket: action=0 skipped - null/empty targetStack (action={})", msg.action);
                    return;
                }
                boolean ok = BDProxy.extractFromNetwork(player, msg.targetStack);
                if (!ok) {
                    ModLogger.info("BDActionPacket: extractFromNetwork returned false for {}", msg.targetStack.getHoverName().getString());
                }
            }
            case 1 -> {
                BDProxy.massCraft(player);
            }
            case 2 -> {
                BDProxy.singleCraft(player);
            }
        }
    }
}
