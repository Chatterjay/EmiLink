package org.chatterjay.emiextend.integration;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.util.ModLogger;

public class EAEPProxy {
    private static Boolean loaded;

    private static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded("extendedae_plus");
        }
        return loaded;
    }

    public static boolean openCraftScreen(AEItemKey aeKey) {
        if (!isLoaded()) return false;
        try {
            var clazz = Class.forName("com.extendedae_plus.network.OpenCraftFromJeiC2SPacket");
            var ctor = clazz.getConstructor(GenericStack.class);
            var packet = ctor.newInstance(new GenericStack(aeKey, 1));
            PacketDistributor.sendToServer((CustomPacketPayload) packet);
            return true;
        } catch (Exception e) {
            ModLogger.debug("EAEP openCraftScreen: {}", e.getMessage());
            return false;
        }
    }

    public static boolean pullFromNetwork(AEItemKey aeKey) {
        if (!isLoaded()) return false;
        try {
            var clazz = Class.forName("com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket");
            var ctor = clazz.getConstructor(GenericStack.class);
            var packet = ctor.newInstance(new GenericStack(aeKey, 1));
            PacketDistributor.sendToServer((CustomPacketPayload) packet);
            return true;
        } catch (Exception e) {
            ModLogger.debug("EAEP pullFromNetwork: {}", e.getMessage());
            return false;
        }
    }
}
