package org.chatterjay.emiextend.network;

import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Utility for reducing boilerplate in CustomPacketPayload handle() methods.
 */
public final class PacketHelper {

    private PacketHelper() {}

    /**
     * Standard server-bound handler: verify flow, enqueue on main thread.
     */
    public static void handleServerBound(IPayloadContext context, Runnable handler) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(handler);
        }
    }

    /**
     * Standard client-bound handler.
     */
    public static void handleClientBound(IPayloadContext context, Runnable handler) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(handler);
        }
    }
}
