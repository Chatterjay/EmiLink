package org.chatterjay.emilink.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.util.ModLogger;

import java.util.function.Supplier;

public class ServerHasModPacket {
    public static boolean serverHasMod = false;

    public ServerHasModPacket() {}

    public static void encode(ServerHasModPacket msg, FriendlyByteBuf buf) {}

    public static ServerHasModPacket decode(FriendlyByteBuf buf) {
        return new ServerHasModPacket();
    }

    public static void handle(ServerHasModPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            serverHasMod = true;
            ModLogger.debug("Server has EmiLink mod");
        });
        ctx.get().setPacketHandled(true);
    }
}
