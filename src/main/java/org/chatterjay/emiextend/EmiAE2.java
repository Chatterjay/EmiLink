package org.chatterjay.emiextend;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.chatterjay.emiextend.client.BDShortcutHandler;
import org.chatterjay.emiextend.client.InputEvents;
import org.chatterjay.emiextend.client.ModKeybindings;
import org.chatterjay.emiextend.network.packet.c2s.AEQueryPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emiextend.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emiextend.network.packet.s2c.ClearCachePacket;
import org.chatterjay.emiextend.network.packet.s2c.ServerHasModPacket;

@Mod(EmiAE2.MODID)
public class EmiAE2 {
    public static final String MODID = "emilink";

    public EmiAE2(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, ModKeybindings::register);
        modBus.addListener(this::registerPackets);
        NeoForge.EVENT_BUS.register(InputEvents.class);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                AEQueryPacket.TYPE,
                AEQueryPacket.STREAM_CODEC,
                AEQueryPacket::handle
        );
        registrar.playToServer(
                BDActionPacket.TYPE,
                BDActionPacket.STREAM_CODEC,
                BDActionPacket::handle
        );
        registrar.playToServer(
                TransferMatchingPacket.TYPE,
                TransferMatchingPacket.STREAM_CODEC,
                TransferMatchingPacket::handle
        );
        registrar.playToClient(
                AEQueryResponsePacket.TYPE,
                AEQueryResponsePacket.STREAM_CODEC,
                AEQueryResponsePacket::handle
        );
        registrar.playToClient(
                ClearCachePacket.TYPE,
                ClearCachePacket.STREAM_CODEC,
                ClearCachePacket::handle
        );
        registrar.playToClient(
                ServerHasModPacket.TYPE,
                ServerHasModPacket.STREAM_CODEC,
                ServerHasModPacket::handle
        );
    }

    public static class ServerEvents {
        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new ServerHasModPacket());
            }
        }

        @SubscribeEvent
        public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
            var command = net.minecraft.commands.Commands.literal("emilink")
                    .then(net.minecraft.commands.Commands.literal("clearcache")
                            .requires(src -> src.hasPermission(0))
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayerOrException();
                                PacketDistributor.sendToPlayer(player, new ClearCachePacket());
                                ctx.getSource().sendSuccess(
                                        () -> net.minecraft.network.chat.Component.literal("AE network cache cleared"),
                                        false
                                );
                                return 1;
                            }))
                    .build();
            event.getDispatcher().getRoot().addChild(command);
        }
    }
}
