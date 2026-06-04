package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.integration.BDProxy;
import org.chatterjay.emilink.util.ModLogger;

import java.util.function.Supplier;

public class TransferMatchingPacket {
    // mode: 0 = network→player (all matching), 1 = main inventory→network, 2 = hotbar→network, 3 = network→player (single stack)
    private final ItemStack clickedStack;
    private final int mode;
    private final int[] lockedSlots;

    public TransferMatchingPacket(ItemStack clickedStack, int mode, int[] lockedSlots) {
        this.clickedStack = clickedStack;
        this.mode = mode;
        this.lockedSlots = lockedSlots != null ? lockedSlots : new int[0];
    }

    public static void encode(TransferMatchingPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.clickedStack);
        buf.writeVarInt(msg.mode);
        buf.writeVarInt(msg.lockedSlots.length);
        for (int v : msg.lockedSlots) {
            buf.writeVarInt(v);
        }
    }

    public static TransferMatchingPacket decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int mode = buf.readVarInt();
        int len = buf.readVarInt();
        int[] locked = new int[len];
        for (int i = 0; i < len; i++) {
            locked[i] = buf.readVarInt();
        }
        return new TransferMatchingPacket(stack, mode, locked);
    }

    public static void handle(TransferMatchingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> handleServer(context, msg));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, TransferMatchingPacket msg) {
        ServerPlayer player = context.getSender();
        if (player == null || msg.clickedStack == null || msg.clickedStack.isEmpty()) {
            ModLogger.info("TransferMatchingPacket: skipped - player={}, stack={}",
                    player != null ? "ok" : "null",
                    msg.clickedStack == null ? "null" : (msg.clickedStack.isEmpty() ? "empty" : "ok"));
            return;
        }

        ModLogger.info("TransferMatchingPacket: mode={}, item={}, locked={}",
                msg.mode, msg.clickedStack.getHoverName().getString(), msg.lockedSlots.length);

        switch (msg.mode) {
            case 0 -> {
                boolean ok = BDProxy.extractAllFromNetwork(player, msg.clickedStack, msg.lockedSlots);
                ModLogger.info("TransferMatchingPacket: extractAllFromNetwork={} for {}", ok, msg.clickedStack.getHoverName().getString());
            }
            case 3 -> {
                boolean ok = BDProxy.extractFromNetwork(player, msg.clickedStack);
                ModLogger.info("TransferMatchingPacket: extractSingleFromNetwork={} for {}", ok, msg.clickedStack.getHoverName().getString());
            }
            default -> {
                boolean ok = BDProxy.depositToNetwork(player, msg.mode, msg.lockedSlots);
                ModLogger.info("TransferMatchingPacket: depositToNetwork={} for mode={}", ok, msg.mode);
            }
        }
    }
}
