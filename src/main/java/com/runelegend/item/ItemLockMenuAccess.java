package com.runelegend.item;

import net.minecraft.world.inventory.Slot;

import java.util.Set;

/** Access to vanilla's per-menu drag state; Item Lock owns no separate drag transaction. */
public interface ItemLockMenuAccess {
    Set<Slot> runelegend$quickCraftSlots();

    void runelegend$resetQuickCraft();
}
