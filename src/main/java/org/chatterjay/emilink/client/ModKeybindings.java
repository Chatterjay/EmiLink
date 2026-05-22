package org.chatterjay.emilink.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ModKeybindings {
    private ModKeybindings() {}

    public static final KeyMapping FILL_SEARCH_KEY = new KeyMapping(
            "key.emilink.fill_search",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.categories.emilink"
    );

    public static final KeyMapping QUICK_PATTERN_KEY = new KeyMapping(
            "key.emilink.quick_pattern",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.emilink"
    );

    public static final KeyMapping QUICK_FILL_SLOT_KEY = new KeyMapping(
            "key.emilink.quick_fill_slot",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.emilink"
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(FILL_SEARCH_KEY);
        event.register(QUICK_PATTERN_KEY);
        event.register(QUICK_FILL_SLOT_KEY);
    }
}
