package org.chatterjay.emilink.network;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.network.packet.c2s.AEBatchQueryPacket;
import org.chatterjay.emilink.network.packet.c2s.AEDepositPacket;
import org.chatterjay.emilink.network.packet.c2s.AELockedSlotsPacket;
import org.chatterjay.emilink.network.packet.c2s.AEQueryPacket;
import org.chatterjay.emilink.network.packet.c2s.BDActionPacket;
import org.chatterjay.emilink.network.packet.c2s.OpenCraftAmountC2SPacket;
import org.chatterjay.emilink.network.packet.c2s.PullFromNetworkC2SPacket;
import org.chatterjay.emilink.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emilink.network.packet.s2c.*;
import org.chatterjay.emilink.util.ModLogger;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Emilink.MODID, "main"),
            () -> PROTOCOL_VERSION,
            v -> true, // Client: accept any server (including servers without EmiLink)
            v -> true  // Server: accept any client (including clients without EmiLink)
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(packetId++, ServerHasModPacket.class,
                ServerHasModPacket::encode, ServerHasModPacket::decode, ServerHasModPacket::handle);
        CHANNEL.registerMessage(packetId++, ClearCachePacket.class,
                ClearCachePacket::encode, ClearCachePacket::decode, ClearCachePacket::handle);
        CHANNEL.registerMessage(packetId++, AEQueryResponsePacket.class,
                AEQueryResponsePacket::encode, AEQueryResponsePacket::decode, AEQueryResponsePacket::handle);
        CHANNEL.registerMessage(packetId++, AEBatchQueryResponsePacket.class,
                AEBatchQueryResponsePacket::encode, AEBatchQueryResponsePacket::decode, AEBatchQueryResponsePacket::handle);
        CHANNEL.registerMessage(packetId++, AEQueryPacket.class,
                AEQueryPacket::encode, AEQueryPacket::decode, AEQueryPacket::handle);
        CHANNEL.registerMessage(packetId++, AEBatchQueryPacket.class,
                AEBatchQueryPacket::encode, AEBatchQueryPacket::decode, AEBatchQueryPacket::handle);
        CHANNEL.registerMessage(packetId++, OpenCraftAmountC2SPacket.class,
                OpenCraftAmountC2SPacket::encode, OpenCraftAmountC2SPacket::decode, OpenCraftAmountC2SPacket::handle);
        CHANNEL.registerMessage(packetId++, PullFromNetworkC2SPacket.class,
                PullFromNetworkC2SPacket::encode, PullFromNetworkC2SPacket::decode, PullFromNetworkC2SPacket::handle);
        CHANNEL.registerMessage(packetId++, BDActionPacket.class,
                BDActionPacket::encode, BDActionPacket::decode, BDActionPacket::handle);
        CHANNEL.registerMessage(packetId++, TransferMatchingPacket.class,
                TransferMatchingPacket::encode, TransferMatchingPacket::decode, TransferMatchingPacket::handle);
        CHANNEL.registerMessage(packetId++, AELockedSlotsPacket.class,
                AELockedSlotsPacket::encode, AELockedSlotsPacket::decode, AELockedSlotsPacket::handle);
        CHANNEL.registerMessage(packetId++, AEDepositPacket.class,
                AEDepositPacket::encode, AEDepositPacket::decode, AEDepositPacket::handle);
    }

    public static boolean sendToPlayer(ServerPlayer player, Object packet) {
        try {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            return true;
        } catch (Throwable t) {
            ModLogger.debug("Skipping packet {} for player without EmiLink client: {}",
                    packet == null ? "null" : packet.getClass().getSimpleName(), t.toString());
            return false;
        }
    }

    public static boolean sendToServer(Object packet) {
        if (!ServerHasModPacket.serverHasMod) {
            ModLogger.debug("Skipping client packet {} because server has no EmiLink capability",
                    packet == null ? "null" : packet.getClass().getSimpleName());
            return false;
        }
        try {
            CHANNEL.sendToServer(packet);
            return true;
        } catch (Throwable t) {
            ModLogger.warn("Failed to send client packet {}: {}",
                    packet == null ? "null" : packet.getClass().getSimpleName(), t.toString());
            return false;
        }
    }

    @Mod.EventBusSubscriber(modid = Emilink.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ServerEvents {

        @SubscribeEvent
        public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
            org.chatterjay.emilink.util.ModLogger.info("EmiLink server starting");
        }

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                sendToPlayer(sp, new ServerHasModPacket());
            }
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                PacketRateLimiter.onTick();
            }
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("emilink")
                    .then(Commands.literal("clearcache")
                            .requires(source -> source.hasPermission(2))
                            .executes(ctx -> {
                                if (ctx.getSource().getEntity() instanceof ServerPlayer sp) {
                                    sendToPlayer(sp, new ClearCachePacket());
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );
        }
    }
}
