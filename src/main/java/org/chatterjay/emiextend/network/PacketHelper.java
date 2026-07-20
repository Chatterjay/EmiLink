package org.chatterjay.emiextend.network;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Utility for reducing boilerplate in Forge 1.20.1 SimpleChannel message handlers.
 */
public final class PacketHelper {

    private PacketHelper() {}

    /**
     * Standard server-bound handler: verify flow, enqueue on main thread.
     */
    public static void handleServerBound(Supplier<NetworkEvent.Context> contextSupplier, Runnable handler) {
        NetworkEvent.Context ctx = contextSupplier.get();
        if (ctx.getDirection().getReceptionSide().isServer()) {
            ctx.enqueueWork(handler);
        }
        ctx.setPacketHandled(true);
    }

    /**
     * Standard client-bound handler.
     */
    public static void handleClientBound(Supplier<NetworkEvent.Context> contextSupplier, Runnable handler) {
        NetworkEvent.Context ctx = contextSupplier.get();
        if (ctx.getDirection().getReceptionSide().isClient()) {
            ctx.enqueueWork(handler);
        }
        ctx.setPacketHandled(true);
    }
}
