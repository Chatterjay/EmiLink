package org.chatterjay.emiextend;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.chatterjay.emiextend.client.ModKeybindings;
import org.chatterjay.emiextend.config.EmiLinkConfig;

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

    /** Client-only: register NeoForge built-in config screen via reflection. */
    private static void registerConfigScreenReflectively(net.neoforged.fml.ModContainer container) {
        try {
            Class<?> factoryClass = Class.forName("net.neoforged.neoforge.client.gui.IConfigScreenFactory");
            Class<?> configScreenClass = Class.forName("net.neoforged.neoforge.client.gui.ConfigurationScreen");
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            var ctor = configScreenClass.getConstructor(net.neoforged.fml.ModContainer.class, screenClass);
            var regMethod = net.neoforged.fml.ModContainer.class.getMethod(
                    "registerExtensionPoint",
                    Class.class,
                    java.util.function.Supplier.class
            );

            Object factory = java.lang.reflect.Proxy.newProxyInstance(
                    factoryClass.getClassLoader(),
                    new Class<?>[]{factoryClass},
                    (_proxy, method, args) -> {
                        if ("createScreen".equals(method.getName()) && args != null && args.length == 2) {
                            return ctor.newInstance(container, args[1]);
                        }
                        return null;
                    }
            );
            regMethod.invoke(container, factoryClass, (java.util.function.Supplier<?>) () -> factory);
        } catch (Exception e) {
            // Config screen is optional across NeoForge point releases.
        }
    }
}
