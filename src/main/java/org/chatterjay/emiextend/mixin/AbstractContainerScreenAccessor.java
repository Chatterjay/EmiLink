package org.chatterjay.emiextend.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    @Nullable
    Slot emilink$getHoveredSlot();

    /** GUI origin of the container, used to place drop targets on the screen. */
    @Accessor("leftPos")
    int emilink$getLeftPos();

    @Accessor("topPos")
    int emilink$getTopPos();
}
