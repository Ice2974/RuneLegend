package com.runelegend.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;

/** Rules for player-initiated transfers, independent of round state and vanilla item use. */
public final class ItemLockPolicy {
    private static final CompoundTag LOCK_MARKER = new CompoundTag();

    static {
        LOCK_MARKER.putBoolean("runelegend:item_lock", true);
    }

    private ItemLockPolicy() {}

    public static boolean isLocked(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.matchedBy(LOCK_MARKER);
    }

    public static boolean isProtected(Player player, ItemStack stack) {
        return !player.isCreative() && isLocked(stack);
    }

    /** Called before prediction/packet processing and again at the shared vanilla execution point. */
    public static boolean allowContainerInput(Player player, AbstractContainerMenu menu, int slotId,
                                              int button, ContainerInput input) {
        if (player.isCreative()) return true;
        var drag = (ItemLockMenuAccess) menu;
        if (input == ContainerInput.QUICK_CRAFT) {
            if (isLocked(menu.getCarried())) {
                drag.runelegend$resetQuickCraft();
                return false;
            }
            int stage = AbstractContainerMenu.getQuickcraftHeader(button);
            if (stage == AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE) {
                // Ignore just this candidate; the remaining drag sequence must stay usable.
                return validSlot(menu, slotId) && !isLocked(menu.getSlot(slotId).getItem());
            }
            if (stage == AbstractContainerMenu.QUICKCRAFT_HEADER_END) {
                // A command or another viewer may have changed a candidate after it was added.
                drag.runelegend$quickCraftSlots().removeIf(slot -> isLocked(slot.getItem()));
            }
            return true;
        }

        boolean lockedSlot = validSlot(menu, slotId) && isLocked(menu.getSlot(slotId).getItem());
        boolean blocked = switch (input) {
            case PICKUP -> lockedSlot || isLocked(menu.getCarried());
            case QUICK_MOVE -> lockedSlot || (slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE
                    && isLocked(menu.getCarried()));
            case SWAP -> lockedSlot || ((Inventory.isHotbarSlot(button) || button == Inventory.SLOT_OFFHAND)
                    && isLocked(player.getInventory().getItem(button)));
            case THROW -> lockedSlot;
            case PICKUP_ALL -> isLocked(menu.getCarried());
            // Non-creative cloning is already rejected by vanilla.
            case CLONE, QUICK_CRAFT -> false;
        };
        if (blocked) drag.runelegend$resetQuickCraft();
        return !blocked;
    }

    public static boolean blocksSwapHands(Player player) {
        return isProtected(player, player.getMainHandItem()) || isProtected(player, player.getOffhandItem());
    }

    public static boolean blocksRecipePlacement(Player player, AbstractContainerMenu menu) {
        if (player.isCreative()) return false;
        if (menu instanceof AbstractCraftingMenu crafting) {
            return crafting.getInputGridSlots().stream().anyMatch(slot -> isLocked(slot.getItem()));
        }
        if (menu instanceof AbstractFurnaceMenu) {
            return isLocked(menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem())
                    || isLocked(menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).getItem());
        }
        return false;
    }

    public static boolean blocksTradeSelection(Player player, AbstractContainerMenu menu) {
        return menu instanceof MerchantMenu && !player.isCreative()
                && (isLocked(menu.getSlot(0).getItem()) || isLocked(menu.getSlot(1).getItem()));
    }

    private static boolean validSlot(AbstractContainerMenu menu, int slotId) {
        return slotId >= 0 && slotId < menu.slots.size();
    }
}
