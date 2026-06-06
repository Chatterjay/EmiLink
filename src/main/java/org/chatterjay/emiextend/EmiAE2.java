package org.chatterjay.emiextend;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.chatterjay.emiextend.client.ModKeybindings;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.network.PacketRateLimiter;
import org.chatterjay.emiextend.network.packet.c2s.AEBatchQueryPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEQueryPacket;
import org.chatterjay.emiextend.network.packet.c2s.AELockedSlotsPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emiextend.network.packet.s2c.AEBatchQueryResponsePacket;
import org.chatterjay.emiextend.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emiextend.network.packet.s2c.ClearCachePacket;
import org.chatterjay.emiextend.network.packet.s2c.ServerHasModPacket;

@Mod(EmiAE2.MODID)
public class EmiAE2 {
    public static final String MODID = "emilink";

    public EmiAE2(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, EmiLinkConfig.SPEC);
        if (FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            registerConfigScreenReflectively(container);
        }
        modBus.addListener((ModConfigEvent.Loading e) -> {
            if (MODID.equals(e.getConfig().getModId())) {
                EmiLinkConfig.validate();
            }
        });
        modBus.addListener((ModConfigEvent.Reloading e) -> {
            if (MODID.equals(e.getConfig().getModId())) {
                EmiLinkConfig.onReload();
            }
        });

        modBus.addListener(RegisterKeyMappingsEvent.class, ModKeybindings::register);
        modBus.addListener(this::registerPackets);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        NeoForge.EVENT_BUS.addListener(EmiAE2::onRegisterClientCommands);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("emilink")
                        .then(Commands.literal("debug")
                                .executes(ctx -> {
                                    boolean current = EmiLinkConfig.DEBUG_MODE.get();
                                    EmiLinkConfig.DEBUG_MODE.set(!current);
                                    EmiLinkConfig.SPEC.save();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink debug mode: " + (!current ? "ON" : "OFF")),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("wb")
                                .executes(ctx -> {
                                    boolean current = EmiLinkConfig.ENABLE_WRAP_BOOK.get();
                                    EmiLinkConfig.ENABLE_WRAP_BOOK.set(!current);
                                    EmiLinkConfig.SPEC.save();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink wrap book mode: " + (!current ? "ON" : "OFF")),
                                            false
                                    );
                                    return 1;
                                })
                        )
        );
    }

    private void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(
                AEQueryPacket.TYPE,
                AEQueryPacket.STREAM_CODEC,
                AEQueryPacket::handle
        );
        registrar.playToServer(
                AEBatchQueryPacket.TYPE,
                AEBatchQueryPacket.STREAM_CODEC,
                AEBatchQueryPacket::handle
        );
        registrar.playToServer(
                AELockedSlotsPacket.TYPE,
                AELockedSlotsPacket.STREAM_CODEC,
                AELockedSlotsPacket::handle
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
                AEBatchQueryResponsePacket.TYPE,
                AEBatchQueryResponsePacket.STREAM_CODEC,
                AEBatchQueryResponsePacket::handle
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

    /** Client-only: register NeoForge built-in config screen via reflection. */
    private static void registerConfigScreenReflectively(net.neoforged.fml.ModContainer container) {
        try {
            Class<?> factoryClass = Class.forName("net.neoforged.neoforge.client.gui.IConfigScreenFactory");
            Class<?> configScreenClass = Class.forName("net.neoforged.neoforge.client.gui.ConfigurationScreen");
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            var regMethod = net.neoforged.fml.ModContainer.class.getMethod("registerExtensionPoint", Class.class, java.util.function.Supplier.class);
            regMethod.invoke(container, factoryClass, (java.util.function.Supplier<?>) () -> {
                try {
                    var ctor = configScreenClass.getConstructor(net.neoforged.fml.ModContainer.class, screenClass);
                    return ctor.newInstance(container, null);
                } catch (Exception e) {
                    return null;
                }
            });
        } catch (Exception e) {
            // Config screen not available (shouldn't happen on client)
        }
    }

    public static class ServerEvents {
        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                try {
                    PacketDistributor.sendToPlayer(serverPlayer, new ServerHasModPacket());
                } catch (Exception e) {
                    // Client doesn't have EmiLink installed — that's fine
                }
            }
        }

        @SubscribeEvent
        public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            PacketRateLimiter.onTick();
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
