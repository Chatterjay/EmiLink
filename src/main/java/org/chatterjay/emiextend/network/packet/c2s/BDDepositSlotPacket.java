package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.network.PacketHelper;

import java.util.function.Supplier;

public record BDDepositSlotPacket(int slotIndex) {

    public static void encode(BDDepositSlotPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slotIndex);
    }

    public static BDDepositSlotPacket decode(FriendlyByteBuf buf) {
        return new BDDepositSlotPacket(buf.readVarInt());
    }

    private void handleInServer(Player player) {
        BDProxy.depositSlotToNetwork(player, slotIndex);
    }

    public static void handle(BDDepositSlotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        PacketHelper.handleServerBound(ctx, () -> {
            Player player = ctx.get().getSender();
            if (player != null) {
                msg.handleInServer(player);
            }
        });
    }
}
