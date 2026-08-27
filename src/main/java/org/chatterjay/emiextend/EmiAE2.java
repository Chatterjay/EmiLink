package org.chatterjay.emiextend;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
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
import org.chatterjay.emiextend.network.packet.c2s.AEAutocraftAmountOverridePacket;
import org.chatterjay.emiextend.network.packet.c2s.AEAutocraftRequestPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEQuickCraftBatchPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEQueryPacket;
import org.chatterjay.emiextend.network.packet.c2s.AELockedSlotsPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDBatchCraftPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDDepositSlotPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEDepositPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEExtractPacket;
import org.chatterjay.emiextend.network.packet.c2s.TransferMatchingPacket;
import org.chatterjay.emiextend.network.packet.s2c.AEBatchQueryResponsePacket;
import org.chatterjay.emiextend.network.packet.s2c.AEQueryResponsePacket;
import org.chatterjay.emiextend.network.packet.s2c.ClearCachePacket;
import org.chatterjay.emiextend.network.packet.s2c.ServerHasModPacket;
import org.chatterjay.emiextend.network.packet.s2c.AEQuickCraftBatchResponsePacket;
import org.chatterjay.emiextend.network.packet.s2c.BDBatchCraftResponsePacket;
import org.chatterjay.emiextend.util.ModLogger;

@Mod(EmiAE2.MODID)
public class EmiAE2 {
    public static final String MODID = "emilink";

    public EmiAE2(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, EmiLinkConfig.SPEC);
        logIntegrationState();
        if (FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            registerConfigScreenReflectively(container);
            registerAeClientHandlersIfLoaded();
        }
        modBus.addListener((ModConfigEvent.Loading e) -> {
            if (MODID.equals(e.getConfig().getModId())) {
                EmiLinkConfig.validate();
                logIntegrationState();
            }
        });
        modBus.addListener((ModConfigEvent.Reloading e) -> {
            if (MODID.equals(e.getConfig().getModId())) {
                EmiLinkConfig.onReload();
                logIntegrationState();
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
                                            () -> Component.translatable("emilink.command.debug", stateKey(!current)),
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
                                            () -> Component.translatable("emilink.command.wrap_book", stateKey(!current)),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("shiftclick")
                                .executes(ctx -> {
                                    String current = EmiLinkConfig.getExtractModifierKeyName();
                                    String next;
                                    String up = current.toUpperCase(java.util.Locale.ROOT);
                                    if ("SHIFT".equals(up)) next = "CONTROL";
                                    else if ("CONTROL".equals(up) || "CTRL".equals(up) || "CTL".equals(up)) next = "ALT";
                                    else if ("ALT".equals(up)) next = "OFF";
                                    else next = "SHIFT";
                                    EmiLinkConfig.EXTRACT_MODIFIER.set(next);
                                    EmiLinkConfig.SPEC.save();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("emilink.command.extract", next),
                                            false
                                    );
                                    return 1;
                                })
                        )
        );
    }

    private static Component stateKey(boolean enabled) {
        return Component.translatable(enabled ? "emilink.command.state.on" : "emilink.command.state.off");
    }

    private void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();
        if (isModLoaded("ae2")) {
            ModLogger.debug("Registering optional AE2 network payloads");
            registerAePackets(registrar);
        } else {
            ModLogger.debug("Skipping AE2 network payloads because AE2 is not loaded");
        }
        registrar.playToServer(
                BDActionPacket.TYPE,
                BDActionPacket.STREAM_CODEC,
                BDActionPacket::handle
        );
        registrar.playToServer(
                BDBatchCraftPacket.TYPE,
                BDBatchCraftPacket.STREAM_CODEC,
                BDBatchCraftPacket::handle
        );
        registrar.playToServer(
                BDDepositSlotPacket.TYPE,
                BDDepositSlotPacket.STREAM_CODEC,
                BDDepositSlotPacket::handle
        );
        registrar.playToServer(
                TransferMatchingPacket.TYPE,
                TransferMatchingPacket.STREAM_CODEC,
                TransferMatchingPacket::handle
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
        registrar.playToClient(
                BDBatchCraftResponsePacket.TYPE,
                BDBatchCraftResponsePacket.STREAM_CODEC,
                BDBatchCraftResponsePacket::handle
        );
    }

    private void registerAePackets(final PayloadRegistrar registrar) {
        registrar.playToServer(
                AEQueryPacket.TYPE,
                AEQueryPacket.STREAM_CODEC,
                AEQueryPacket::handle
        );
        registrar.playToServer(
                AEAutocraftAmountOverridePacket.TYPE,
                AEAutocraftAmountOverridePacket.STREAM_CODEC,
                AEAutocraftAmountOverridePacket::handle
        );
        registrar.playToServer(
                AEAutocraftRequestPacket.TYPE,
                AEAutocraftRequestPacket.STREAM_CODEC,
                AEAutocraftRequestPacket::handle
        );
        registrar.playToServer(
                AEBatchQueryPacket.TYPE,
                AEBatchQueryPacket.STREAM_CODEC,
                AEBatchQueryPacket::handle
        );
        registrar.playToServer(
                AEQuickCraftBatchPacket.TYPE,
                AEQuickCraftBatchPacket.STREAM_CODEC,
                AEQuickCraftBatchPacket::handle
        );
        registrar.playToServer(
                AELockedSlotsPacket.TYPE,
                AELockedSlotsPacket.STREAM_CODEC,
                AELockedSlotsPacket::handle
        );
        registrar.playToServer(
                AEDepositPacket.TYPE,
                AEDepositPacket.STREAM_CODEC,
                AEDepositPacket::handle
        );
        registrar.playToServer(
                AEExtractPacket.TYPE,
                AEExtractPacket.STREAM_CODEC,
                AEExtractPacket::handle
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
                AEQuickCraftBatchResponsePacket.TYPE,
                AEQuickCraftBatchResponsePacket.STREAM_CODEC,
                AEQuickCraftBatchResponsePacket::handle
        );
    }

    private static void registerAeClientHandlersIfLoaded() {
        if (!isModLoaded("ae2")) {
            ModLogger.debug("Skipping AE2 client handlers because AE2 is not loaded");
            return;
        }
        ModLogger.debug("Registering AE2 client handlers");
        registerStaticEventHandler("org.chatterjay.emiextend.client.AEQuickCraftDelayHandler");
        registerStaticEventHandler("org.chatterjay.emiextend.client.AENetworkCache");
        registerStaticEventHandler("org.chatterjay.emiextend.client.InputEvents");
    }

    private static void registerStaticEventHandler(String className) {
        try {
            NeoForge.EVENT_BUS.register(Class.forName(className));
        } catch (Throwable t) {
            ModLogger.warn("Failed to register optional event handler {}: {}", className, t.toString());
        }
    }

    private static boolean isModLoaded(String modId) {
        var modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    private static void logIntegrationState() {
        ModLogger.debug("Optional integrations: side={} ae2={} bd={} extendedae_plus={} ars_nouveau={} curios={} inventoryessentials={}",
                FMLEnvironment.dist,
                isModLoaded("ae2"),
                isModLoaded("beyonddimensions"),
                isModLoaded("extendedae_plus"),
                isModLoaded("ars_nouveau"),
                isModLoaded("curios"),
                isModLoaded("inventoryessentials"));
    }

    /** Client-only: register NeoForge built-in config screen via reflection. */
    private static void registerConfigScreenReflectively(net.neoforged.fml.ModContainer container) {
        try {
            Class<?> factoryClass = Class.forName("net.neoforged.neoforge.client.gui.IConfigScreenFactory");
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            Class<?> customScreenClass = Class.forName("org.chatterjay.emiextend.client.EmiLinkConfigScreen");
            var createMethod = customScreenClass.getMethod("create", net.neoforged.fml.ModContainer.class, screenClass);
            var regMethod = net.neoforged.fml.ModContainer.class.getMethod("registerExtensionPoint", Class.class, java.util.function.Supplier.class);

            Object factory = java.lang.reflect.Proxy.newProxyInstance(
                    factoryClass.getClassLoader(),
                    new Class<?>[]{factoryClass},
                    (_proxy, method, args) -> {
                        if ("createScreen".equals(method.getName()) && args != null && args.length == 2) {
                            return createMethod.invoke(null, container, args[1]);
                        }
                        return null;
                    }
            );
            regMethod.invoke(container, factoryClass, (java.util.function.Supplier<?>) () -> factory);
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
                                try {
                                    PacketDistributor.sendToPlayer(player, new ClearCachePacket());
                                } catch (Exception e) {
                                    ModLogger.debug("Skipping clear-cache packet for player without EmiLink client: {}",
                                            player.getGameProfile().getName());
                                }
                                ctx.getSource().sendSuccess(
                                        () -> net.minecraft.network.chat.Component.translatable("emilink.command.clear_cache"),
                                        false
                                );
                                return 1;
                            }))
                    .build();
            event.getDispatcher().getRoot().addChild(command);
        }
    }
}
