package org.chatterjay.emilink;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.chatterjay.emilink.client.ModKeybindings;
import org.chatterjay.emilink.network.NetworkHandler;
import org.slf4j.Logger;

@Mod(Emilink.MODID)
public class Emilink {

    public static final String MODID = "emilink";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Emilink() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modEventBus.addListener(ModKeybindings::register));

        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NetworkHandler.register();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        Config.validate();
        LOGGER.info("EmiLink common setup completed");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("EmiLink server starting");
    }
}
