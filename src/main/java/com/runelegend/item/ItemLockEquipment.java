package com.runelegend.item;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Protection and prediction correction for player-initiated equipment transfers only. */
public final class ItemLockEquipment {
    private ItemLockEquipment() {}

    public static boolean denyTransfer(Player player, LivingEntity target, EquipmentSlot slot, ItemStack source) {
        if (!ItemLockPolicy.isProtected(player, source)
                && !ItemLockPolicy.isProtected(player, target.getItemBySlot(slot))) return false;
        if (player instanceof ServerPlayer serverPlayer) {
            var inventory = player.getInventory();
            // The held item can have been predicted as empty or replaced by the old equipment.
            serverPlayer.connection.send(inventory.createInventoryUpdatePacket(inventory.getSelectedSlot()));
            serverPlayer.connection.send(inventory.createInventoryUpdatePacket(Inventory.SLOT_OFFHAND));
            if (target == player) {
                for (var entry : Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()) {
                    if (entry.getValue() == slot) {
                        serverPlayer.connection.send(inventory.createInventoryUpdatePacket(entry.getIntKey()));
                    }
                }
            } else {
                // An unchanged entity will not necessarily produce a vanilla equipment delta.
                serverPlayer.connection.send(new ClientboundSetEquipmentPacket(target.getId(),
                        List.of(Pair.of(slot, target.getItemBySlot(slot).copy()))));
            }
        }
        return true;
    }
}
