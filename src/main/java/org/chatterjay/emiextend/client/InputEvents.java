package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.util.ModLogger;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class InputEvents {
    private InputEvents() {}

    // Flag to suppress the subsequent charTyped('f') from appending to the
    // search field after FILL_SEARCH_KEY has set its value.
    private static boolean fillSearchHandled = false;

    @SubscribeEvent
    public static void onCharTypedPre(ScreenEvent.CharacterTyped.Pre event) {
        if (fillSearchHandled) {
            fillSearchHandled = false;
            if (event.getCodePoint() == 'f' || event.getCodePoint() == 'F') {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (!ModKeybindings.FILL_SEARCH_KEY.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return;

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return;

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) return;

        var first = emiStacks.getFirst();

        boolean alt = Screen.hasAltDown();
        var text = alt ? "@" + first.getId().getNamespace() : first.getName().getString();
        if (text.isEmpty()) return;

        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;

        // AE2: PatternAccessTermScreen — has its own search field, extends AEBaseScreen directly
        try {
            var patClass = Class.forName("appeng.client.gui.me.patternaccess.PatternAccessTermScreen");
            if (patClass.isInstance(screen)) {
                var searchField = patClass.getDeclaredField("searchField");
                searchField.setAccessible(true);
                Object fieldObj = searchField.get(screen);
                if (fieldObj != null) {
                    fieldObj.getClass().getMethod("setValue", String.class).invoke(fieldObj, text);
                }
                fillSearchHandled = true;
                event.setCanceled(true);
                return;
            }
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: PatternAccessTermScreen exception: {}", e.getMessage());
        }

        // ExtendedAE (EAEP): GuiWirelessExPAT — wireless pattern access terminal
        try {
            var eaeClass = Class.forName("com.glodblock.github.extendedae.xmod.wt.GuiWirelessExPAT");
            if (eaeClass.isInstance(screen)) {
                java.lang.reflect.Field searchField = null;
                Class<?> cls = screen.getClass();
                while (cls != null && searchField == null) {
                    try {
                        searchField = cls.getDeclaredField("searchField");
                    } catch (NoSuchFieldException ignored) {}
                    cls = cls.getSuperclass();
                }
                if (searchField != null) {
                    searchField.setAccessible(true);
                    Object fieldObj = searchField.get(screen);
                    if (fieldObj != null) {
                        fieldObj.getClass().getMethod("setValue", String.class).invoke(fieldObj, text);
                        fillSearchHandled = true;
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: EAEP GuiWirelessExPAT exception: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }

        // AE2 terminal search field — works for MEStorageScreen and subclasses (PatternEncodingTermScreen etc.)
        try {
            var meStorageClass = Class.forName("appeng.client.gui.me.common.MEStorageScreen");
            if (meStorageClass.isInstance(screen)) {
                var searchField = meStorageClass.getDeclaredField("searchField");
                searchField.setAccessible(true);
                Object fieldObj = searchField.get(screen);
                if (fieldObj != null) {
                    fieldObj.getClass().getMethod("setValue", String.class).invoke(fieldObj, text);
                }
                var setSearch = meStorageClass.getDeclaredMethod("setSearchText", String.class);
                setSearch.setAccessible(true);
                setSearch.invoke(screen, text);
                fillSearchHandled = true;
                event.setCanceled(true);
                return;
            }
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: AE2 terminal search exception: {}", e.getMessage());
        }

        // BD: DimensionsNetGUI search field (via reflection)
        if (BDProxy.isBDNetGUI(screen)) {
            if (BDProxy.setSearchText(screen, text)) {
                fillSearchHandled = true;
                event.setCanceled(true);
            } else {
                ModLogger.warn("FILL_SEARCH_KEY: BD setSearchText failed");
            }
            return;
        }

        // EMI search fallback for any screen with EMI sidebar
        try {
            EmiApi.setSearchText(text);
            fillSearchHandled = true;
            event.setCanceled(true);
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: EMI setSearchText failed: {}", e.getMessage());
        }
    }
}
