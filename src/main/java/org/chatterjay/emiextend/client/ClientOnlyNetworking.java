package org.chatterjay.emiextend.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * Client-only builds cannot provide EmiLink's custom server handlers.
 */
public final class ClientOnlyNetworking {
    private ClientOnlyNetworking() {}

    public static void sendEmiLinkPacket(CustomPacketPayload packet) {
        ModLogger.debug("Client-only build skipped EmiLink server packet {}", packet.type().id());
    }
}
