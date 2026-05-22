package org.chatterjay.emilink.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.client.handler.AENetworkCache;

import java.util.function.Supplier;

public class AEQueryResponsePacket {
    private final ItemStack stack;
    private final long count;
    private final boolean craftable;

    public AEQueryResponsePacket(ItemStack stack, long count, boolean craftable) {
        this.stack = stack;
        this.count = count;
        this.craftable = craftable;
    }

    public ItemStack getStack() { return stack; }
    public long getCount() { return count; }
    public boolean isCraftable() { return craftable; }

    public static void encode(AEQueryResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
        buf.writeVarLong(msg.count);
        buf.writeBoolean(msg.craftable);
    }

    public static AEQueryResponsePacket decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        long count = buf.readVarLong();
        boolean craftable = buf.readBoolean();
        return new AEQueryResponsePacket(stack, count, craftable);
    }

    public static void handle(AEQueryResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            AENetworkCache.receiveResponse(msg.stack, msg.count, msg.craftable));
        ctx.get().setPacketHandled(true);
    }
}
