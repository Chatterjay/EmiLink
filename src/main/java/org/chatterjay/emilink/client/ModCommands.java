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
        );
    }
}
