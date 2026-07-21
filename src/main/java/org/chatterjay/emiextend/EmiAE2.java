package org.chatterjay.emiextend;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.TickEvent;
import org.chatterjay.emiextend.client.ModKeybindings;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.network.EmiLinkNetwork;
import org.chatterjay.emiextend.network.PacketRateLimiter;
import org.chatterjay.emiextend.network.packet.s2c.ClearCachePacket;
import org.chatterjay.emiextend.network.packet.s2c.ServerHasModPacket;
import org.chatterjay.emiextend.util.ModLogger;

@Mod(EmiAE2.MODID)
public class EmiAE2 {
    public static final String MODID = "emilink";

    public EmiAE2() {
        IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EmiLinkConfig.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerConfigScreenReflectively();
            registerAeClientHandlersIfLoaded();
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

        modBus.addListener(ModKeybindings::register);
        modBus.addListener(EmiLinkNetwork::onCommonSetup);

        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        MinecraftForge.EVENT_BUS.addListener(EmiAE2::onRegisterClientCommands);
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
                                    var current = EmiLinkConfig.EXTRACT_MODIFIER.get();
                                    var options = EmiLinkConfig.ExtractTrigger.values();
                                    var next = options[(current.ordinal() + 1) % options.length];
                                    EmiLinkConfig.EXTRACT_MODIFIER.set(next);
                                    EmiLinkConfig.SPEC.save();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("emilink.command.extract", next.name()),
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

    private static void registerAeClientHandlersIfLoaded() {
        if (!isModLoaded("ae2")) {
            return;
        }
        registerStaticEventHandler("org.chatterjay.emiextend.client.AEQuickCraftDelayHandler");
        registerStaticEventHandler("org.chatterjay.emiextend.client.AENetworkCache");
        registerStaticEventHandler("org.chatterjay.emiextend.client.InputEvents");
    }

    private static void registerStaticEventHandler(String className) {
        try {
            MinecraftForge.EVENT_BUS.register(Class.forName(className));
        } catch (Throwable t) {
            ModLogger.warn("Failed to register optional event handler {}: {}", className, t.toString());
        }
    }

    private static boolean isModLoaded(String modId) {
        var modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    /** Client-only: register Forge built-in config screen via reflection. */
    private static void registerConfigScreenReflectively() {
        try {
            Class<?> factoryClass = Class.forName("net.minecraftforge.client.ConfigScreenHandler$ConfigScreenFactory");
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            var factoryCtor = factoryClass.getDeclaredConstructor(java.util.function.Function.class);
            var regMethod = ModContainer.class.getMethod("registerExtensionPoint", Class.class, java.util.function.Supplier.class);

            java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> fn =
                    parent -> {
                        try {
                            Class<?> configScreenClass = Class.forName("net.minecraftforge.client.gui.ConfigScreen");
                            var ctor = configScreenClass.getConstructor(net.minecraftforge.fml.ModContainer.class, screenClass);
                            return (net.minecraft.client.gui.screens.Screen) ctor.newInstance(
                                    net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer(), parent);
                        } catch (Exception e) {
                            return null;
                        }
                    };
            Object factory = factoryCtor.newInstance(fn);
            regMethod.invoke(net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer(), factoryClass, (java.util.function.Supplier<?>) () -> factory);
        } catch (Exception e) {
            // Config screen not available (shouldn't happen on client)
        }
    }

    public static class ServerEvents {
        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                try {
                    EmiLinkNetwork.sendTo(new ServerHasModPacket(), serverPlayer);
                } catch (Exception e) {
                    // Client doesn't have EmiLink installed — that's fine
                }
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
            var command = Commands.literal("emilink")
                    .then(Commands.literal("clearcache")
                            .requires(src -> src.hasPermission(0))
                            .executes(ctx -> {
                                var player = ctx.getSource().getPlayerOrException();
                                EmiLinkNetwork.sendTo(new ClearCachePacket(), player);
                                ctx.getSource().sendSuccess(
                                        () -> Component.translatable("emilink.command.clear_cache"),
                                        false
                                );
                                return 1;
                            }))
                    .build();
            event.getDispatcher().getRoot().addChild(command);
        }
    }
}
