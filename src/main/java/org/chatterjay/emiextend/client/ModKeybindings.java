package org.chatterjay.emiextend.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
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

    public static final KeyMapping QUICK_BOOKMARK_KEY = new KeyMapping(
            "key.emilink.quick_bookmark",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.emilink"
    );

    public static void register(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(FILL_SEARCH_KEY);
        event.register(QUICK_BOOKMARK_KEY);
    }
}
