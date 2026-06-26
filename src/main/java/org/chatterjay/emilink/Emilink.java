package org.chatterjay.emilink;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.chatterjay.emilink.client.EmilinkConfigScreen;
import org.chatterjay.emilink.client.ModKeybindings;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.util.IEProxy;
import org.slf4j.Logger;

@Mod(Emilink.MODID)
public class Emilink {

    public static final String MODID = "emilink";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Emilink() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modEventBus.addListener(ModKeybindings::register));

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NetworkHandler.register();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) ->
                            EmilinkConfigScreen.create(parent))
            );
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        Config.validate();
        IEProxy.registerIgnoredScreens();
        LOGGER.info("EmiLink common setup completed");
    }
}
