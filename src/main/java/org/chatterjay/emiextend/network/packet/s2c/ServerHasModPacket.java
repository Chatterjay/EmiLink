package org.chatterjay.emiextend.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.client.BDShortcutHandler;

import java.util.function.Supplier;

public record ServerHasModPacket() {

    public static void encode(ServerHasModPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static ServerHasModPacket decode(FriendlyByteBuf buf) {
        return new ServerHasModPacket();
    }

    public static void handle(ServerHasModPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> BDShortcutHandler.serverHasMod = true);
        ctx.get().setPacketHandled(true);
    }
}
