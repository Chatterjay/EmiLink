package org.chatterjay.emiextend.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.util.ModLogger;

public final class ClientPacketHelper {
    private ClientPacketHelper() {
    }

    public static boolean sendToServer(CustomPacketPayload packet) {
        if (!BDShortcutHandler.serverHasMod) {
            ModLogger.debug("Skipping client packet because server does not have EmiLink: {}",
                    packet.type().id());
            return false;
        }
        try {
            PacketDistributor.sendToServer(packet);
            return true;
        } catch (Exception e) {
            ModLogger.warn("Failed to send client packet {}: {}", packet.type().id(), e.toString());
            return false;
        }
    }
}
