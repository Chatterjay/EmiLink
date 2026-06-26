package org.chatterjay.emilink.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.client.handler.AENetworkCache;
import org.chatterjay.emilink.util.ModLogger;

@Mod.EventBusSubscriber(modid = Emilink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModCommands {
    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("emilink")
                        .then(Commands.literal("debug")
                                .executes(ctx -> {
                                    boolean current = Config.DEBUG_MODE.get();
                                    Config.DEBUG_MODE.set(!current);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink debug mode: " + (!current ? "ON" : "OFF")),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("wb")
                                .executes(ctx -> {
                                    boolean current = Config.ENABLE_WRAP_BOOK.get();
                                    Config.ENABLE_WRAP_BOOK.set(!current);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink wrap book mode: " + (!current ? "ON" : "OFF")),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("clearcache")
                                .executes(ctx -> {
                                    AENetworkCache.clearAll();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink network cache cleared"),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("shiftclick")
                                .executes(ctx -> {
                                    String current = Config.EXTRACT_MODIFIER.get();
                                    String next;
                                    switch (current.toUpperCase(java.util.Locale.ROOT)) {
                                        case "SHIFT" -> next = "CONTROL";
                                        case "CONTROL" -> next = "ALT";
                                        case "ALT" -> next = "OFF";
                                        default -> next = "SHIFT";
                                    }
                                    Config.EXTRACT_MODIFIER.set(next);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink extract modifier: " + next),
                                            false
                                    );
                                    ModLogger.info("Extract modifier set to {}", next);
                                    return 1;
                                })
                        )
        );
    }
}
