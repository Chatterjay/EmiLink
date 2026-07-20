package org.chatterjay.emiextend.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Method;
import org.chatterjay.emiextend.network.packet.c2s.AEAutocraftAmountOverridePacket;
import org.chatterjay.emiextend.network.packet.c2s.AEAutocraftRequestPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEBatchQueryPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEDepositPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEExtractPacket;
import org.chatterjay.emiextend.network.packet.c2s.AELockedSlotsPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEQueryPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDDepositSlotPacket;
import org.chatterjay.emiextend.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emiextend.network.packet.s2c.AEBatchQueryResponsePacket;
import org.chatterjay.emiextend.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emiextend.network.packet.s2c.ClearCachePacket;
import org.chatterjay.emiextend.network.packet.s2c.ServerHasModPacket;

/**
 * Forge 1.20.1 SimpleChannel-based networking for EmiLink.
 * Replaces the NeoForge 1.21.1 PayloadRegistrar/CustomPacketPayload system.
 */
public final class EmiLinkNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(EmiAE2.MODID, "main"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    private static int nextId = 0;

    private EmiLinkNetwork() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(EmiLinkNetwork::registerMessages);
    }

    private static synchronized void registerMessages() {
        // C2S
        registerMessage(AEQueryPacket.class, AEQueryPacket::encode, AEQueryPacket::decode, AEQueryPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(AEAutocraftAmountOverridePacket.class, AEAutocraftAmountOverridePacket::encode, AEAutocraftAmountOverridePacket::decode, AEAutocraftAmountOverridePacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(AEAutocraftRequestPacket.class, AEAutocraftRequestPacket::encode, AEAutocraftRequestPacket::decode, AEAutocraftRequestPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(AEBatchQueryPacket.class, AEBatchQueryPacket::encode, AEBatchQueryPacket::decode, AEBatchQueryPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(AELockedSlotsPacket.class, AELockedSlotsPacket::encode, AELockedSlotsPacket::decode, AELockedSlotsPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(AEDepositPacket.class, AEDepositPacket::encode, AEDepositPacket::decode, AEDepositPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(AEExtractPacket.class, AEExtractPacket::encode, AEExtractPacket::decode, AEExtractPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(BDActionPacket.class, BDActionPacket::encode, BDActionPacket::decode, BDActionPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(BDDepositSlotPacket.class, BDDepositSlotPacket::encode, BDDepositSlotPacket::decode, BDDepositSlotPacket::handle, NetworkDirection.PLAY_TO_SERVER);
        registerMessage(TransferMatchingPacket.class, TransferMatchingPacket::encode, TransferMatchingPacket::decode, TransferMatchingPacket::handle, NetworkDirection.PLAY_TO_SERVER);

        // S2C
        registerMessage(AEQueryResponsePacket.class, AEQueryResponsePacket::encode, AEQueryResponsePacket::decode, AEQueryResponsePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        registerMessage(AEBatchQueryResponsePacket.class, AEBatchQueryResponsePacket::encode, AEBatchQueryResponsePacket::decode, AEBatchQueryResponsePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        registerMessage(ClearCachePacket.class, ClearCachePacket::encode, ClearCachePacket::decode, ClearCachePacket::handle, NetworkDirection.PLAY_TO_CLIENT);
        registerMessage(ServerHasModPacket.class, ServerHasModPacket::encode, ServerHasModPacket::decode, ServerHasModPacket::handle, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static <T> void registerMessage(Class<T> clazz,
                                            net.minecraftforge.network.simple.MessageEncoder<T> encoder,
                                            net.minecraftforge.network.simple.MessageDecoder<T> decoder,
                                            net.minecraftforge.network.simple.MessageConsumer<T> handler,
                                            NetworkDirection direction) {
        CHANNEL.registerMessage(nextId++, clazz, encoder, decoder, handler, direction);
    }

    /** Send a packet from client to server. */
    public static <T> void sendToServer(T packet) {
        CHANNEL.sendToServer(packet);
    }

    /** Send a packet from server to a specific player. */
    public static <T> void sendTo(T packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Send an AE2 packet to the server via reflection on appeng.core.AppEng.
     * Falls back gracefully if AE2's API is unavailable.
     */
    public static void sendAEPacketToServer(Object packet) {
        if (packet == null) return;
        Class<?> packetClass = packet.getClass();
        try {
            Class<?> appEngClass = Class.forName("appeng.core.AppEng");
            // Try direct method with the packet's class hierarchy
            for (Class<?> c = packetClass; c != null; c = c.getSuperclass()) {
                try {
                    Method m = appEngClass.getMethod("sendPacketToServer", c);
                    m.invoke(null, packet);
                    return;
                } catch (NoSuchMethodException ignored) {}
            }
            // Try Object.class
            try {
                Method m = appEngClass.getMethod("sendPacketToServer", Object.class);
                m.invoke(null, packet);
                return;
            } catch (NoSuchMethodException ignored) {}
            ModLogger.warn("AE2 sendPacketToServer method not found for packet type {}", packetClass.getName());
        } catch (Exception e) {
            ModLogger.warn("Failed to send AE packet via reflection: {}", e.getMessage());
        }
    }
}
