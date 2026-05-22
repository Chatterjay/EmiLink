package org.chatterjay.emilink;

import net.minecraftforge.fml.ModList;

public class ModCompat {
    public static final boolean AE2_LOADED = ModList.get().isLoaded("ae2");
    public static final boolean EXTENDEDAE_PLUS_LOADED = ModList.get().isLoaded("extendedae_plus");
    public static final boolean EMI_LOADED = ModList.get().isLoaded("emi");

    private ModCompat() {
    }
}
