package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.client.AENetworkCache;

import java.util.function.Supplier;

public record AEQueryResponsePacket(ItemStack stack, long count, boolean craftable) {

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
        ctx.get().enqueueWork(() -> AENetworkCache.receiveResponse(msg.stack(), msg.count(), msg.craftable()));
        ctx.get().setPacketHandled(true);
    }
}
