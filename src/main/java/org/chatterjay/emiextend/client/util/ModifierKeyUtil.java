package org.chatterjay.emiextend.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Shared helper for arbitrary modifier keys. Accepts generic SHIFT / CONTROL / CTRL / CTL / ALT / OFF
 * (either side) and side-specific LEFT_SHIFT / RIGHT_SHIFT / etc., plus any InputConstants name
 * (key.keyboard.c, key.mouse.left, SPACE, F5, etc.).
 * Generic names match either side; side-specific names match only that physical key.
 */
public final class ModifierKeyUtil {
    private ModifierKeyUtil() {}

    public static boolean isHeld(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        if (s.isEmpty()) return false;
        String upper = s.toUpperCase(java.util.Locale.ROOT);
        // Generic modifiers: either side
        switch (upper) {
            case "SHIFT": return Screen.hasShiftDown();
            case "CONTROL":
            case "CTRL":
            case "CTL": return Screen.hasControlDown();
            case "ALT": return Screen.hasAltDown();
            case "OFF":
            case "": return false;
            // Side-specific aliases — only that physical key
            case "LEFT_SHIFT":
            case "LSHIFT":
            case "LEFT SHIFT":
            case "LEFT-SHIFT": return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT);
            case "RIGHT_SHIFT":
            case "RSHIFT":
            case "RIGHT SHIFT":
            case "RIGHT-SHIFT": return isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
            case "LEFT_CONTROL":
            case "LEFT_CTRL":
            case "LCONTROL":
            case "LCTRL":
            case "LEFT CONTROL":
            case "LEFT CTRL":
            case "LEFT-CONTROL":
            case "LEFT-CTRL": return isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL);
            case "RIGHT_CONTROL":
            case "RIGHT_CTRL":
            case "RCONTROL":
            case "RCTRL":
            case "RIGHT CONTROL":
            case "RIGHT CTRL":
            case "RIGHT-CONTROL":
            case "RIGHT-CTRL": return isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
            case "LEFT_ALT":
            case "LALT":
            case "LEFT ALT":
            case "LEFT-ALT": return isKeyDown(GLFW.GLFW_KEY_LEFT_ALT);
            case "RIGHT_ALT":
            case "RALT":
            case "RIGHT ALT":
            case "RIGHT-ALT": return isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
            case "LEFT_SUPER":
            case "LEFT_WIN":
            case "LEFT_META":
            case "LSUPER":
            case "LWIN":
            case "LEFT SUPER":
            case "LEFT WIN": return isKeyDown(GLFW.GLFW_KEY_LEFT_SUPER);
            case "RIGHT_SUPER":
            case "RIGHT_WIN":
            case "RIGHT_META":
            case "RSUPER":
            case "RWIN":
            case "RIGHT SUPER":
            case "RIGHT WIN": return isKeyDown(GLFW.GLFW_KEY_RIGHT_SUPER);
            default: break;
        }
        InputConstants.Key key = resolveKey(s);
        if (key == null) return false;
        return isKeyDown(key);
    }

    private static boolean isKeyDown(int glfwKey) {
        try {
            long win = Minecraft.getInstance().getWindow().getWindow();
            return InputConstants.isKeyDown(win, glfwKey);
        } catch (Throwable ignored) { return false; }
    }

    @javax.annotation.Nullable
    public static InputConstants.Key resolveKey(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return null;
        // 1. Direct InputConstants name (e.g. "key.keyboard.left.shift")
        try { return InputConstants.getKey(s); } catch (Exception ignored) {}
        String upper = s.toUpperCase(java.util.Locale.ROOT);
        // 2. Alias -> canonical InputConstants name for side-specific modifiers
        String canonical = toCanonicalInputName(upper);
        if (canonical != null) {
            try { return InputConstants.getKey(canonical); } catch (Exception ignored) {}
        }
        // 3. Simple key names: "C", "SPACE", "F5", "a", etc.
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        String lowerDots = lower.replace(' ', '.').replace('_', '.').replace('-', '.');
        for (String cand : new String[]{"key.keyboard." + lower, "key.keyboard." + lowerDots, lower, lowerDots}) {
            try { return InputConstants.getKey(cand); } catch (Exception ignored) {}
        }
        if (s.length() == 1) {
            try { return InputConstants.getKey("key.keyboard." + lower); } catch (Exception ignored) {}
        }
        // 4. Mouse names already handled by direct try; also try key.mouse.*
        if (upper.startsWith("MOUSE") || upper.startsWith("BUTTON")) {
            try { return InputConstants.getKey("key.mouse." + lowerDots); } catch (Exception ignored) {}
        }
        return null;
    }

    @javax.annotation.Nullable
    private static String toCanonicalInputName(String upper) {
        return switch (upper) {
            case "LEFT_SHIFT", "LSHIFT", "LEFT SHIFT", "LEFT-SHIFT" -> "key.keyboard.left.shift";
            case "RIGHT_SHIFT", "RSHIFT", "RIGHT SHIFT", "RIGHT-SHIFT" -> "key.keyboard.right.shift";
            case "LEFT_CONTROL", "LEFT_CTRL", "LCONTROL", "LCTRL", "LEFT CONTROL", "LEFT CTRL", "LEFT-CONTROL", "LEFT-CTRL" -> "key.keyboard.left.control";
            case "RIGHT_CONTROL", "RIGHT_CTRL", "RCONTROL", "RCTRL", "RIGHT CONTROL", "RIGHT CTRL", "RIGHT-CONTROL", "RIGHT-CTRL" -> "key.keyboard.right.control";
            case "LEFT_ALT", "LALT", "LEFT ALT", "LEFT-ALT" -> "key.keyboard.left.alt";
            case "RIGHT_ALT", "RALT", "RIGHT ALT", "RIGHT-ALT" -> "key.keyboard.right.alt";
            case "LEFT_SUPER", "LEFT_WIN", "LEFT_META", "LSUPER", "LWIN", "LEFT SUPER", "LEFT WIN", "LEFT-SUPER", "LEFT-WIN" -> "key.keyboard.left.win";
            case "RIGHT_SUPER", "RIGHT_WIN", "RIGHT_META", "RSUPER", "RWIN", "RIGHT SUPER", "RIGHT WIN", "RIGHT-SUPER", "RIGHT-WIN" -> "key.keyboard.right.win";
            // Generic single-word aliases that should map to nothing here (handled in isHeld directly)
            default -> null;
        };
    }

    public static boolean isKeyDown(InputConstants.Key key) {
        try {
            long win = Minecraft.getInstance().getWindow().getWindow();
            int code = key.getValue();
            InputConstants.Type type = key.getType();
            if (type == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(win, code);
            if (type == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, code) == GLFW.GLFW_PRESS;
            if (type == InputConstants.Type.SCANCODE) return GLFW.glfwGetKey(win, code) == GLFW.GLFW_PRESS || InputConstants.isKeyDown(win, code);
        } catch (Throwable ignored) {}
        return false;
    }

    @javax.annotation.Nullable
    public static InputConstants.Key resolveDisplayKey(String raw) {
        if (raw == null || raw.isBlank() || "OFF".equalsIgnoreCase(raw)) return null;
        String upper = raw.trim().toUpperCase(java.util.Locale.ROOT);
        // Generic modifiers have no single physical key — caller should use translatable
        if ("SHIFT".equals(upper) || "CONTROL".equals(upper) || "CTRL".equals(upper) || "CTL".equals(upper) || "ALT".equals(upper)) return null;
        // Side-specific textual aliases: resolve via canonical name so display uses InputConstants translation
        String canonical = toCanonicalInputName(upper);
        if (canonical != null) {
            try { return InputConstants.getKey(canonical); } catch (Exception ignored) {}
        }
        try { return InputConstants.getKey(raw.trim()); } catch (Exception e) { return null; }
    }

    public static Component describe(String raw, @javax.annotation.Nullable InputConstants.Key parsed) {
        if (raw == null || raw.isBlank()) return Component.literal("OFF");
        String upper = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if ("OFF".equals(upper)) return Component.literal("OFF");
        if ("SHIFT".equals(upper)) return Component.translatable("key.keyboard.left.shift");
        if ("CONTROL".equals(upper) || "CTRL".equals(upper) || "CTL".equals(upper)) return Component.translatable("key.keyboard.left.control");
        if ("ALT".equals(upper)) return Component.translatable("key.keyboard.left.alt");
        // Side-specific aliases: prefer InputConstants display name
        String canonical = toCanonicalInputName(upper);
        if (canonical != null) {
            try {
                InputConstants.Key k = InputConstants.getKey(canonical);
                return k.getDisplayName();
            } catch (Exception ignored) {
                // Fallback to parsing alias with dots
                return Component.literal(raw);
            }
        }
        if (parsed != null) return parsed.getDisplayName();
        // Fallback: try to resolve raw as any key
        InputConstants.Key k = resolveKey(raw);
        if (k != null) return k.getDisplayName();
        return Component.literal(raw);
    }

    public static Component describe(String raw) {
        return describe(raw, resolveDisplayKey(raw));
    }
}
