package com.runelegend.test.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public interface ItemLockScreenAccessor {
    @Accessor("leftPos") int runelegend$leftPos();
    @Accessor("topPos") int runelegend$topPos();
    @Accessor("hoveredSlot") Slot runelegend$hoveredSlot();
    @Accessor("quickCraftSlots") Set<Slot> runelegend$quickCraftSlots();
}
