package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.util.AeAutocraftAmountOverride;

import java.util.function.Supplier;

public record AEAutocraftAmountOverridePacket(int amount) {

    public static void encode(AEAutocraftAmountOverridePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.amount);
    }

    public static AEAutocraftAmountOverridePacket decode(FriendlyByteBuf buf) {
        return new AEAutocraftAmountOverridePacket(buf.readVarInt());
    }

    void handleInServer(ServerPlayer serverPlayer) {
        AeAutocraftAmountOverride.set(serverPlayer, amount);
    }

    public static void handle(AEAutocraftAmountOverridePacket msg, Supplier<NetworkEvent.Context> ctx) {
        PacketHelper.handleServerBound(ctx, () -> {
            if (ctx.get().getSender() instanceof ServerPlayer sp) {
                msg.handleInServer(sp);
            }
        });
    }
}
