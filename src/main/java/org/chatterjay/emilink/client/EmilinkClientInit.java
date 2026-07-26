package org.chatterjay.emilink.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

public final class EmilinkClientInit {
    private EmilinkClientInit() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModKeybindings::register);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) ->
                        EmilinkConfigScreen.create(parent))
        );
    }
}
