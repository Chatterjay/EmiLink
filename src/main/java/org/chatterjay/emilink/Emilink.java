package org.chatterjay.emilink;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.util.IEProxy;
import org.slf4j.Logger;

import java.lang.reflect.Method;

@Mod(Emilink.MODID)
public class Emilink {

    public static final String MODID = "emilink";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Emilink() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NetworkHandler.register();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> registerClient(modEventBus));
    }

    private static void registerClient(IEventBus modEventBus) {
        try {
            Class<?> clientInitClass = Class.forName("org.chatterjay.emilink.client.EmilinkClientInit");
            Method register = clientInitClass.getMethod("register", IEventBus.class);
            register.invoke(null, modEventBus);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize EmiLink client hooks", e);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        Config.validate();
        IEProxy.registerIgnoredScreens();
        LOGGER.info("EmiLink common setup completed");
    }
}
