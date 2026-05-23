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
import org.chatterjay.emilink.network.packet.c2s.AEQueryPacket;
import org.chatterjay.emilink.network.packet.c2s.OpenCraftAmountC2SPacket;
import org.chatterjay.emilink.network.packet.c2s.PullFromNetworkC2SPacket;
import org.chatterjay.emilink.network.packet.s2c.*;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Emilink.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
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
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    @Mod.EventBusSubscriber(modid = Emilink.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ServerEvents {

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ServerHasModPacket());
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
                                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new ClearCachePacket());
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );
        }
    }
}
