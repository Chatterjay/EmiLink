package org.chatterjay.emiextend;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.chatterjay.emiextend.client.InputEvents;
import org.chatterjay.emiextend.client.ModKeybindings;

@Mod(EmiAE2.MODID)
public class EmiAE2 {
    public static final String MODID = "emilink";

    public EmiAE2(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, ModKeybindings::register);
        NeoForge.EVENT_BUS.register(InputEvents.class);
    }
}
