package com.runelegend.test;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.runelegend.RuneLegendServer;
import com.runelegend.item.ItemLockPolicy;
import io.netty.channel.ChannelFutureListener;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class ItemLockGameTests {
    private static final String LOCK = "{\"runelegend:item_lock\":1b}";
    private static final int OUTSIDE = AbstractContainerMenu.SLOT_CLICKED_OUTSIDE;

    @GameTest
    public void tagTypesAreStrictAndExtraDataSurvivesRealClicks(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            test.assertTrue(!ItemLockPolicy.isLocked(ItemStack.EMPTY), "An empty stack is never locked");
            var cases = List.of(
                    new TagCase(null, false), new TagCase("{}", false), new TagCase(LOCK, true),
                    new TagCase("{\"runelegend:item_lock\":0b}", false),
                    new TagCase("{\"runelegend:item_lock\":2b}", false),
                    new TagCase("{\"runelegend:item_lock\":1}", false),
                    new TagCase("{\"runelegend:item_lock\":1L}", false),
                    new TagCase("{\"runelegend:item_lock\":1.0f}", false),
                    new TagCase("{\"runelegend:item_lock\":\"1b\"}", false),
                    new TagCase("{\"runelegend:item_lock\":\"true\"}", false),
                    new TagCase("{runelegend:{item_lock:1b}}", false),
                    new TagCase("{\"runelegend:item_lock\":1b,other:{value:7},name:\"keep me\"}", true));
            int slot = inventorySlot(f, 9);
            for (var entry : cases) {
                f.clearContents();
                var stack = parse(f.player, "minecraft:paper" + (entry.data == null ? ""
                        : "[minecraft:custom_data=" + entry.data + "]"), 7);
                var expected = stack.copy();
                test.assertValueEqual(ItemLockPolicy.isLocked(stack), entry.locked, "Tag contract: " + entry.data);
                stackEquals(test, stack, expected, "Reading the tag never rewrites its components");
                f.player.getInventory().setItem(9, stack);
                var before = snapshot(f);
                click(f, slot, 0, ContainerInput.PICKUP);
                if (entry.locked) {
                    unchanged(test, f, before, "Locked tag " + entry.data);
                    assertMenuCorrection(test, f, before, "Locked tag correction");
                } else {
                    stackEquals(test, f.menu().getCarried(), expected, "An unlocked tag allows a real pickup");
                    stackEquals(test, f.player.getInventory().getItem(9), ItemStack.EMPTY, "Pickup clears its source");
                }
            }

            f.clearContents();
            var bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS,
                    BundleContents.EMPTY.copyWithContents(Stream.of(locked(f.player, "paper", 3))));
            var expectedBundle = bundle.copy();
            f.player.getInventory().setItem(9, bundle);
            click(f, slot, 0, ContainerInput.PICKUP);
            stackEquals(test, f.menu().getCarried(), expectedBundle, "Nested locked content does not lock its bundle");
            test.assertTrue(f.player.getInventory().getItem(9).isEmpty(), "The bundle actually moved");
            test.succeed();
        }
    }

    @GameTest
    public void pickupChecksBothStacksAndOutsideDrops(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            f.openChest(new SimpleContainer(27));
            for (int button : new int[]{0, 1}) {
                f.clearContents();
                f.menu().getSlot(0).set(locked(f.player, "paper", 7));
                var before = snapshot(f);
                click(f, 0, button, ContainerInput.PICKUP);
                unchanged(test, f, before, "Neither pickup button can split a locked source");

                f.menu().setCarried(new ItemStack(Items.DIAMOND, 3));
                before = snapshot(f);
                click(f, 0, button, ContainerInput.PICKUP);
                unchanged(test, f, before, "An ordinary cursor cannot replace a locked slot");

                f.clearContents();
                f.menu().getSlot(0).set(new ItemStack(Items.DIAMOND, 3));
                f.menu().setCarried(locked(f.player, "paper", 7));
                for (int target : new int[]{0, 1, OUTSIDE}) {
                    before = snapshot(f);
                    click(f, target, button, ContainerInput.PICKUP);
                    unchanged(test, f, before, "A locked cursor cannot swap, place, split, or drop outside");
                }

                f.clearContents();
                var ordinary = new ItemStack(Items.PAPER, 5);
                f.menu().getSlot(0).set(ordinary.copy());
                click(f, 0, button, ContainerInput.PICKUP);
                int taken = button == 0 ? 5 : 3;
                stackEquals(test, f.menu().getCarried(), ordinary.copyWithCount(taken), "Ordinary pickup succeeds");
                stackEquals(test, f.menu().getSlot(0).getItem(), ordinary.copyWithCount(5 - taken),
                        "Ordinary source retains the correct count");

                f.menu().setCarried(ordinary.copy());
                int droppedBefore = f.droppedCount(ordinary);
                click(f, OUTSIDE, button, ContainerInput.PICKUP);
                int dropped = button == 0 ? 5 : 1;
                stackEquals(test, f.menu().getCarried(), ordinary.copyWithCount(5 - dropped),
                        "Ordinary outside click removes the expected count");
                test.assertValueEqual(f.droppedCount(ordinary), droppedBefore + dropped, "Outside click creates the drops");
            }
            test.succeed();
        }
    }

    @GameTest
    public void shiftTransfersAreProtectedInBothDirections(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            f.openChest(new SimpleContainer(27));
            var protectedStack = locked(f.player, "paper", 7);
            var ordinary = new ItemStack(Items.PAPER, 4);
            f.menu().getSlot(0).set(protectedStack.copy());
            f.menu().getSlot(1).set(ordinary.copy());
            var before = snapshot(f);
            click(f, 0, 0, ContainerInput.QUICK_MOVE);
            unchanged(test, f, before, "Shift cannot remove a locked chest stack");
            click(f, 1, 0, ContainerInput.QUICK_MOVE);
            stackEquals(test, f.menu().getSlot(0).getItem(), protectedStack, "Nearby locked stack remains intact");
            test.assertTrue(f.menu().getSlot(1).getItem().isEmpty(), "Ordinary chest source is emptied");
            test.assertValueEqual(inventoryCount(f, ordinary), 4, "Ordinary chest contents reach the inventory");

            f.clearContents();
            f.player.getInventory().setItem(9, protectedStack.copy());
            f.player.getInventory().setItem(10, ordinary.copy());
            before = snapshot(f);
            click(f, inventorySlot(f, 9), 0, ContainerInput.QUICK_MOVE);
            unchanged(test, f, before, "Shift cannot send locked inventory contents to a chest");
            click(f, inventorySlot(f, 10), 0, ContainerInput.QUICK_MOVE);
            stackEquals(test, f.player.getInventory().getItem(9), protectedStack, "Inventory lock remains at its index");
            test.assertTrue(f.player.getInventory().getItem(10).isEmpty(), "Ordinary inventory source is emptied");
            stackEquals(test, f.menu().getSlot(0).getItem(), ordinary, "Ordinary contents reach the chest");
            test.succeed();
        }
    }

    @GameTest
    public void numberKeysAndOffhandSwapCheckBothEnds(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            f.openChest(new SimpleContainer(27));
            for (int target : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, Inventory.SLOT_OFFHAND}) {
                f.clearContents();
                var protectedStack = locked(f.player, "paper", 7);
                var ordinary = new ItemStack(Items.DIAMOND, 3);
                f.menu().getSlot(0).set(protectedStack.copy());
                f.player.getInventory().setItem(target, ordinary.copy());
                var before = snapshot(f);
                click(f, 0, target, ContainerInput.SWAP);
                unchanged(test, f, before, "SWAP protects the clicked source for button " + target);

                f.menu().getSlot(0).set(ordinary.copy());
                f.player.getInventory().setItem(target, protectedStack.copy());
                before = snapshot(f);
                click(f, 0, target, ContainerInput.SWAP);
                unchanged(test, f, before, "SWAP protects the inventory destination for button " + target);

                var other = new ItemStack(Items.EMERALD, 2);
                f.player.getInventory().setItem(target, other.copy());
                click(f, 0, target, ContainerInput.SWAP);
                stackEquals(test, f.menu().getSlot(0).getItem(), other, "Ordinary SWAP replaces the clicked slot");
                stackEquals(test, f.player.getInventory().getItem(target), ordinary, "Ordinary SWAP replaces its other end");
                test.assertTrue(f.menu().getCarried().isEmpty(), "SWAP does not put anything on the cursor");
            }
            test.succeed();
        }
    }

    @GameTest
    public void containerThrowChecksSingleAndWholeStacks(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            f.openChest(new SimpleContainer(27));
            for (int button : new int[]{0, 1}) {
                f.clearContents();
                f.menu().getSlot(0).set(locked(f.player, "paper", 7));
                var before = snapshot(f);
                click(f, 0, button, ContainerInput.THROW);
                unchanged(test, f, before, "Container THROW cannot remove any locked items");
                assertMenuCorrection(test, f, before, "Container THROW corrects the predicted source and cursor");

                var ordinary = new ItemStack(Items.PAPER, 5);
                f.menu().getSlot(0).set(ordinary.copy());
                int droppedBefore = f.droppedCount(ordinary);
                click(f, 0, button, ContainerInput.THROW);
                int dropped = button == 0 ? 1 : 5;
                stackEquals(test, f.menu().getSlot(0).getItem(), ordinary.copyWithCount(5 - dropped),
                        "Ordinary THROW removes the correct count");
                test.assertValueEqual(f.droppedCount(ordinary), droppedBefore + dropped, "Ordinary THROW creates item entities");
            }
            test.succeed();
        }
    }

    @GameTest
    public void repeatedShiftAndThrowRecheckReplenishedSource(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            for (var input : List.of(ContainerInput.QUICK_MOVE, ContainerInput.THROW)) {
                for (boolean lockRefill : new boolean[]{true, false}) {
                    f.clearContents();
                    var container = new RefillingContainer();
                    f.openChest(container);
                    var first = new ItemStack(Items.PAPER, 2);
                    var refill = lockRefill ? locked(f.player, "paper", 3) : new ItemStack(Items.PAPER, 3);
                    container.setItem(0, first.copy());
                    container.refill = refill.copy();
                    int droppedBefore = f.droppedCount(first);
                    click(f, 0, input == ContainerInput.THROW ? 1 : 0, input);
                    test.assertTrue(container.refill.isEmpty(), "The real vanilla operation reached the refill callback");
                    stackEquals(test, container.getItem(0), lockRefill ? refill : ItemStack.EMPTY,
                            "Every iteration rechecks the same item ID with newly added components");
                    int moved = lockRefill ? 2 : 5;
                    test.assertValueEqual(inventoryCount(f, first), input == ContainerInput.QUICK_MOVE ? moved : 0,
                            "Only allowed stacks enter the inventory during repeated transfer");
                    test.assertValueEqual(f.droppedCount(first) - droppedBefore, input == ContainerInput.THROW ? moved : 0,
                            "Only allowed stacks become drops during repeated THROW");
                    if (lockRefill) {
                        test.assertValueEqual(inventoryCount(f, refill), 0, "Refilled locked components never reach inventory");
                        test.assertValueEqual(f.droppedCount(refill), 0, "Refilled locked components never become drops");
                    }
                    test.assertTrue(f.menu().getCarried().isEmpty(), "Batch operations leave the cursor empty");
                }
            }
            test.succeed();
        }
    }

    @GameTest
    public void draggingSkipsLockedCandidatesAndRechecksAtEnd(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            f.openChest(new SimpleContainer(27));
            for (int type : new int[]{AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE,
                    AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY}) {
                f.clearContents();
                var locked = locked(f.player, "paper", 2);
                f.menu().getSlot(1).set(locked.copy());
                f.menu().setCarried(new ItemStack(Items.PAPER, 8));
                drag(f, OUTSIDE, AbstractContainerMenu.QUICKCRAFT_HEADER_START, type);
                drag(f, 0, AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, type);
                drag(f, 1, AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, type);
                drag(f, 2, AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, type);
                drag(f, OUTSIDE, AbstractContainerMenu.QUICKCRAFT_HEADER_END, type);
                assertDragResult(test, f, locked, type, "Ignoring a locked candidate preserves the other candidates");

                f.clearContents();
                f.menu().setCarried(new ItemStack(Items.PAPER, 8));
                drag(f, OUTSIDE, AbstractContainerMenu.QUICKCRAFT_HEADER_START, type);
                for (int slot : new int[]{0, 1, 2}) {
                    drag(f, slot, AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, type);
                }
                // Model a command or another viewer inserting a locked stack after candidate selection.
                f.menu().getSlot(1).set(locked.copy());
                drag(f, OUTSIDE, AbstractContainerMenu.QUICKCRAFT_HEADER_END, type);
                assertDragResult(test, f, locked, type, "END prunes newly locked slots before dividing the cursor");
            }
            test.succeed();
        }
    }

    @GameTest
    public void rejectedDragAndDynamicCursorLockRestoreVanillaState(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            f.openChest(new SimpleContainer(27));
            f.menu().setCarried(locked(f.player, "paper", 8));
            for (int stage : new int[]{0, 1, 2}) {
                var before = snapshot(f);
                drag(f, stage == 1 ? 0 : OUTSIDE, stage, 0);
                unchanged(test, f, before, "A locked cursor cannot start, continue, or finish a drag");
            }

            f.menu().setCarried(new ItemStack(Items.PAPER, 8));
            drag(f, OUTSIDE, 0, 0);
            drag(f, 0, 1, 0);
            drag(f, 1, 1, 0);
            f.menu().setCarried(locked(f.player, "paper", 8));
            var before = snapshot(f);
            drag(f, OUTSIDE, 2, 0);
            unchanged(test, f, before, "A newly locked cursor cancels the pending distribution");

            f.menu().setCarried(new ItemStack(Items.PAPER, 6));
            drag(f, OUTSIDE, 0, 0);
            drag(f, 0, 1, 0);
            drag(f, 1, 1, 0);
            drag(f, OUTSIDE, 2, 0);
            stackEquals(test, f.menu().getSlot(0).getItem(), new ItemStack(Items.PAPER, 3), "A fresh drag works after refusal");
            stackEquals(test, f.menu().getSlot(1).getItem(), new ItemStack(Items.PAPER, 3), "Both fresh candidates receive items");
            test.assertTrue(f.menu().getCarried().isEmpty(), "The fresh drag conserves all six items");

            f.clearContents();
            f.menu().setCarried(new ItemStack(Items.PAPER, 2));
            drag(f, OUTSIDE, 0, 0);
            drag(f, 0, 1, 0);
            f.menu().getSlot(1).set(locked(f.player, "diamond", 1));
            before = snapshot(f);
            click(f, 1, 0, ContainerInput.PICKUP);
            unchanged(test, f, before, "A prohibited non-drag input cancels the pending drag");
            click(f, 2, 0, ContainerInput.PICKUP);
            stackEquals(test, f.menu().getSlot(2).getItem(), new ItemStack(Items.PAPER, 2),
                    "The next ordinary click is executed, not swallowed by stale quickcraft status");
            test.assertTrue(f.menu().getSlot(0).getItem().isEmpty() && f.menu().getCarried().isEmpty(),
                    "No stale candidate receives items");
            test.succeed();
        }
    }

    @GameTest
    public void doubleClickCollectsOrdinaryStacksAndCloneKeepsVanillaPermissions(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            f.openChest(new SimpleContainer(27));
            for (int button : new int[]{0, 1}) {
                f.clearContents();
                var locked = locked(f.player, "paper", 4);
                f.menu().getSlot(2).set(locked.copy());
                f.menu().setCarried(locked.copyWithCount(1));
                var before = snapshot(f);
                click(f, 0, button, ContainerInput.PICKUP_ALL);
                unchanged(test, f, before, "A locked cursor cannot collect even matching locked components");

                f.menu().setCarried(new ItemStack(Items.PAPER, 1));
                f.menu().getSlot(1).set(new ItemStack(Items.PAPER, 3));
                f.menu().getSlot(3).set(new ItemStack(Items.PAPER, 5));
                click(f, 0, button, ContainerInput.PICKUP_ALL);
                stackEquals(test, f.menu().getCarried(), new ItemStack(Items.PAPER, 9), "Double click gathers ordinary candidates");
                stackEquals(test, f.menu().getSlot(2).getItem(), locked, "Unclicked locked candidate stays intact");
                test.assertTrue(f.menu().getSlot(1).getItem().isEmpty() && f.menu().getSlot(3).getItem().isEmpty(),
                        "Both ordinary candidates really moved");
            }
            for (boolean tagged : new boolean[]{false, true}) {
                f.clearContents();
                f.menu().getSlot(0).set(tagged ? locked(f.player, "paper", 3) : new ItemStack(Items.PAPER, 3));
                var before = snapshot(f);
                click(f, 0, 2, ContainerInput.CLONE);
                unchanged(test, f, before, "Survival CLONE remains disallowed by vanilla");
            }
            test.succeed();
        }
    }

    @GameTest
    public void forgedPredictionAndStaleStateIdsReceiveAuthoritativeContents(GameTestHelper test) {
        for (var mode : List.of(GameType.SURVIVAL, GameType.ADVENTURE)) {
            try (var f = fixture(test, mode)) {
                f.openChest(new SimpleContainer(27));
                for (boolean stale : new boolean[]{false, true}) {
                    for (boolean forgeSlots : new boolean[]{false, true}) {
                        f.clearContents();
                        f.menu().getSlot(0).set(locked(f.player, "paper", 7));
                        f.menu().setCarried(new ItemStack(Items.DIAMOND, 3));
                        f.menu().sendAllDataToRemote();
                        f.connection.packets.clear();
                        var before = snapshot(f);
                        int state = f.menu().getStateId();
                        var changed = new Int2ObjectOpenHashMap<HashedStack>();
                        if (forgeSlots) changed.put(0, HashedStack.EMPTY);
                        f.player.connection.handleContainerClick(new ServerboundContainerClickPacket(
                                f.menu().containerId, stale ? (state + 32767) & 32767 : state,
                                (short) 0, (byte) 0, ContainerInput.PICKUP, changed, HashedStack.EMPTY));
                        unchanged(test, f, before, "The server ignores fabricated predictions in " + mode);
                        assertMenuCorrection(test, f, before, "A rejected request forcibly synchronizes even unchanged contents");
                        test.assertTrue(f.menu().getStateId() != state, "The authoritative correction advances the menu state ID");

                        f.connection.packets.clear();
                        click(f, 1, 0, ContainerInput.PICKUP);
                        stackEquals(test, f.menu().getSlot(1).getItem(), new ItemStack(Items.DIAMOND, 3),
                                "A legal request still executes after a rejected prediction");
                        test.assertTrue(f.menu().getCarried().isEmpty(), "The legal request empties the cursor");
                        test.assertTrue(!f.connection.packets.isEmpty(), "Rejecting a request never leaves remote updates suppressed");
                    }
                }
            }
        }
        test.succeed();
    }

    @GameTest
    public void worldDropAndHandSwapPacketsValidateAndCorrectBothHands(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            // A chest menu omits the offhand slot: corrections must target the player inventory directly.
            f.openChest(new SimpleContainer(27));
            f.player.getInventory().setSelectedSlot(3);
            for (var action : List.of(ServerboundPlayerActionPacket.Action.DROP_ITEM,
                    ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) {
                f.clearContents();
                var locked = locked(f.player, "paper", 7);
                f.player.getInventory().setItem(3, locked.copy());
                var before = snapshot(f);
                playerAction(f, action);
                unchanged(test, f, before, "World Q refuses " + action);
                assertInventoryCorrection(test, f, 3, locked);

                var ordinary = new ItemStack(Items.PAPER, 5);
                f.player.getInventory().setItem(3, ordinary.copy());
                int droppedBefore = f.droppedCount(ordinary);
                playerAction(f, action);
                int dropped = action == ServerboundPlayerActionPacket.Action.DROP_ITEM ? 1 : 5;
                stackEquals(test, f.player.getMainHandItem(), ordinary.copyWithCount(5 - dropped), "World Q permits ordinary items");
                test.assertValueEqual(f.droppedCount(ordinary), droppedBefore + dropped, "World Q creates the ordinary drops");
            }
            for (boolean lockedMain : new boolean[]{true, false}) {
                f.clearContents();
                var locked = locked(f.player, "paper", 7);
                var ordinary = new ItemStack(Items.DIAMOND, 3);
                var main = lockedMain ? locked : ordinary;
                var offhand = lockedMain ? ordinary : locked;
                f.player.getInventory().setItem(3, main.copy());
                f.player.getInventory().setItem(Inventory.SLOT_OFFHAND, offhand.copy());
                var before = snapshot(f);
                playerAction(f, ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
                unchanged(test, f, before, "World F checks both hands");
                assertInventoryCorrection(test, f, 3, main);
                assertInventoryCorrection(test, f, Inventory.SLOT_OFFHAND, offhand);
            }
            var main = new ItemStack(Items.PAPER, 5);
            var offhand = new ItemStack(Items.DIAMOND, 3);
            f.player.getInventory().setItem(3, main.copy());
            f.player.getInventory().setItem(Inventory.SLOT_OFFHAND, offhand.copy());
            playerAction(f, ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            stackEquals(test, f.player.getMainHandItem(), offhand, "Ordinary F swaps into the selected main hand");
            stackEquals(test, f.player.getOffhandItem(), main, "Ordinary F swaps into the offhand");
            test.succeed();
        }
    }

    @GameTest
    public void creativeModeCanMoveCloneDropAndSwapTaggedItems(GameTestHelper test) {
        // GameTestHelper's mock gameMode() is immutable, so use a genuinely creative fixture.
        try (var f = fixture(test, GameType.CREATIVE)) {
            test.assertTrue(f.player.isCreative(), "Creative exemption is tested with the actual player mode");
            f.openChest(new SimpleContainer(27));
            var locked = locked(f.player, "paper", 7);
            f.menu().getSlot(0).set(locked.copy());
            click(f, 0, 0, ContainerInput.PICKUP);
            stackEquals(test, f.menu().getCarried(), locked, "Creative can pick up a tagged stack");
            test.assertTrue(f.menu().getSlot(0).getItem().isEmpty(), "Creative source is actually emptied");
            click(f, 1, 0, ContainerInput.PICKUP);
            stackEquals(test, f.menu().getSlot(1).getItem(), locked, "Creative can place the tagged cursor");
            for (boolean tagged : new boolean[]{true, false}) {
                var source = tagged ? locked : new ItemStack(Items.PAPER, 7);
                f.menu().getSlot(2).set(source.copy());
                f.menu().setCarried(ItemStack.EMPTY);
                click(f, 2, 2, ContainerInput.CLONE);
                stackEquals(test, f.menu().getSlot(2).getItem(), source, "Creative CLONE keeps the source");
                stackEquals(test, f.menu().getCarried(), source.copyWithCount(source.getMaxStackSize()),
                        "Creative CLONE preserves components and fills the cursor");
            }
            f.clearContents();
            f.player.getInventory().setItem(0, locked.copy());
            playerAction(f, ServerboundPlayerActionPacket.Action.DROP_ITEM);
            stackEquals(test, f.player.getMainHandItem(), locked.copyWithCount(6), "Creative Q may drop tagged items");
            test.assertValueEqual(f.droppedCount(locked), 1, "Creative Q creates a tagged drop");
            playerAction(f, ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
            test.assertTrue(f.player.getMainHandItem().isEmpty(), "Creative F empties the main hand");
            stackEquals(test, f.player.getOffhandItem(), locked.copyWithCount(6), "Creative F moves the tagged stack");
            test.succeed();
        }
    }

    @GameTest
    public void bothRoundStatesAndSharedContainerPlayersStayIndependent(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        boolean wasRunning = server.game().getRules().running();
        try (var first = fixture(test, GameType.SURVIVAL); var other = fixture(test, GameType.SURVIVAL)) {
            var chest = new SimpleContainer(27);
            first.openChest(chest);
            other.openChest(chest);
            for (boolean running : new boolean[]{false, true}) {
                if (running) server.game().startGame(); else server.game().stopGame();
                first.clearContents();
                other.clearContents();
                var locked = locked(first.player, "paper", 7);
                var ordinary = new ItemStack(Items.DIAMOND, 3);
                chest.setItem(0, locked.copy());
                chest.setItem(1, ordinary.copy());
                var before = snapshot(first);
                click(first, 0, 0, ContainerInput.PICKUP);
                unchanged(test, first, before, "First viewer is protected in round running=" + running);
                before = snapshot(other);
                click(other, 0, 0, ContainerInput.QUICK_MOVE);
                unchanged(test, other, before, "Second viewer independently sees the same lock");
                click(other, 1, 0, ContainerInput.PICKUP);
                stackEquals(test, other.menu().getCarried(), ordinary, "Another player's ordinary operation succeeds");
                test.assertTrue(first.menu().getCarried().isEmpty(), "Other player's cursor remains independent");
                stackEquals(test, chest.getItem(0), locked, "Shared locked contents stay in their slot");

                first.player.getInventory().setItem(0, locked.copy());
                before = snapshot(first);
                playerAction(first, ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS);
                unchanged(test, first, before, "World drop is protected in both round states");
                first.player.getInventory().setItem(0, ordinary.copy());
                playerAction(first, ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND);
                stackEquals(test, first.player.getOffhandItem(), ordinary, "Ordinary hand swaps work in both round states");
            }
            test.succeed();
        } finally {
            if (wasRunning) server.game().startGame(); else server.game().stopGame();
        }
    }

    @GameTest
    public void pickPacketsProtectSourceAndDestinationButAllowSelection(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            test.setBlock(2, 1, 2, Blocks.STONE);
            var position = test.absolutePos(new BlockPos(2, 1, 2));
            var locked = locked(f.player, "paper", 7);
            fillHotbar(f);
            f.player.getInventory().setItem(0, locked.copy());
            var stone = new ItemStack(Items.STONE, 4);
            f.player.getInventory().setItem(9, stone.copy());
            test.assertValueEqual(f.player.getInventory().getSuitableHotbarSlot(), 0, "Vanilla would overwrite the locked target");
            var before = snapshot(f);
            f.connection.packets.clear();
            f.player.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(position, false));
            unchanged(test, f, before, "Block pick cannot overwrite a locked hotbar slot");
            assertInventoryCorrection(test, f, 0, locked);
            assertInventoryCorrection(test, f, 9, stone);
            assertHeldSlotCorrection(test, f, 0);

            var ordinary = new ItemStack(Items.PAPER, 7);
            f.player.getInventory().setItem(0, ordinary.copy());
            f.player.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(position, false));
            stackEquals(test, f.player.getInventory().getItem(0), stone, "Ordinary block pick moves the full source stack");
            stackEquals(test, f.player.getInventory().getItem(9), ordinary, "Ordinary block pick preserves the displaced stack");

            f.player.getInventory().setItem(0, locked.copy());
            f.player.getInventory().setItem(4, stone.copy());
            before = snapshot(f);
            f.player.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(position, false));
            test.assertValueEqual(f.player.getInventory().getSelectedSlot(), 4, "Pure hotbar selection is allowed despite a locked old hand");
            stackListEquals(test, inventoryCopies(f), before.inventory, "Selecting a hotbar slot does not move inventory contents");

            f.clearContents();
            fillHotbar(f);
            f.player.getInventory().setItem(9, locked.copy());
            var frame = test.spawn(EntityTypes.ITEM_FRAME, 2, 2, 3);
            try {
                frame.setItem(locked.copyWithCount(1));
                before = snapshot(f);
                f.connection.packets.clear();
                f.player.connection.handlePickItemFromEntity(new ServerboundPickItemFromEntityPacket(frame.getId(), false));
                unchanged(test, f, before, "Entity pick cannot relocate a matching locked source");
                assertInventoryCorrection(test, f, 9, locked);
                assertHeldSlotCorrection(test, f, 0);

                f.player.getInventory().setItem(9, ordinary.copy());
                frame.setItem(ordinary.copyWithCount(1));
                f.player.connection.handlePickItemFromEntity(new ServerboundPickItemFromEntityPacket(frame.getId(), false));
                stackEquals(test, f.player.getInventory().getItem(0), ordinary, "Ordinary entity pick actually moves its source");
                stackEquals(test, f.player.getInventory().getItem(9), new ItemStack(Items.BRICKS), "Entity pick preserves its target stack");
            } finally {
                frame.discard();
            }
            test.succeed();
        }
    }

    @GameTest
    public void recipeRequestCannotClearLockedCraftingInput(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            var recipe = unlockRecipe(f, "oak_planks");
            var locked = locked(f.player, "diamond", 2);
            f.menu().getSlot(1).set(locked.copy());
            f.player.getInventory().setItem(9, new ItemStack(Items.OAK_LOG, 4));
            var before = snapshot(f);
            placeRecipe(f, recipe);
            unchanged(test, f, before, "Recipe placement cannot return a locked input to inventory");
            assertMenuCorrection(test, f, before, "Rejected recipe request corrects the menu");

            var ordinary = new ItemStack(Items.DIAMOND, 2);
            f.menu().getSlot(1).set(ordinary.copy());
            placeRecipe(f, recipe);
            var menu = f.player.inventoryMenu;
            int logs = menu.getInputGridSlots().stream().mapToInt(slot -> slot.getItem().is(Items.OAK_LOG)
                    ? slot.getItem().getCount() : 0).sum();
            test.assertValueEqual(logs, 1, "A legal recipe request really fills the crafting grid");
            test.assertValueEqual(inventoryCount(f, new ItemStack(Items.OAK_LOG)), 3, "Crafting placement takes one ordinary ingredient");
            test.assertValueEqual(inventoryCount(f, ordinary), 2, "Ordinary existing input is returned without loss");
            stackEquals(test, menu.getResultSlot().getItem(), new ItemStack(Items.OAK_PLANKS, 4), "Vanilla recomputes the recipe result");
            test.succeed();
        }
    }

    @GameTest
    public void furnaceRecipeProtectsInputAndResultButAllowsLockedFuel(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            f.open((id, inventory, player) -> new FurnaceMenu(id, inventory, new SimpleContainer(3), new SimpleContainerData(4)));
            var recipe = unlockRecipe(f, "iron_ingot_from_smelting_raw_iron");
            f.player.getInventory().setItem(9, new ItemStack(Items.RAW_IRON, 4));
            for (int slot : new int[]{0, 2}) {
                f.menu().getSlot(slot).set(locked(f.player, slot == 0 ? "raw_iron" : "iron_ingot", 2));
                var before = snapshot(f);
                placeRecipe(f, recipe);
                unchanged(test, f, before, "Furnace placement protects every slot it would clear");
                assertMenuCorrection(test, f, before, "Rejected furnace request corrects the menu");
                f.menu().getSlot(slot).set(ItemStack.EMPTY);
            }
            var fuel = locked(f.player, "coal", 8);
            f.menu().getSlot(1).set(fuel.copy());
            placeRecipe(f, recipe);
            stackEquals(test, f.menu().getSlot(0).getItem(), new ItemStack(Items.RAW_IRON), "Locked fuel does not block legal recipe filling");
            stackEquals(test, f.menu().getSlot(1).getItem(), fuel, "Recipe filling never moves or edits the locked fuel");
            test.assertTrue(f.menu().getSlot(2).getItem().isEmpty(), "Recipe filling does not invent a smelted output");
            test.assertValueEqual(inventoryCount(f, new ItemStack(Items.RAW_IRON)), 3, "Furnace recipe filling conserves ordinary ingredients");
            test.succeed();
        }
    }

    @GameTest
    public void closingReturnsLockedCursorAndTemporaryInputsAndMayDropWhenFull(GameTestHelper test) {
        try (var f = fixture(test, GameType.SURVIVAL)) {
            test.setBlock(2, 1, 2, Blocks.CRAFTING_TABLE);
            var position = test.absolutePos(new BlockPos(2, 1, 2));
            f.open((id, inventory, player) -> new CraftingMenu(id, inventory,
                    ContainerLevelAccess.create(test.getLevel(), position)));
            var menu = f.menu();
            var input = locked(f.player, "paper", 7);
            var cursor = locked(f.player, "diamond", 3);
            menu.getSlot(1).set(input.copy());
            menu.setCarried(cursor.copy());
            closeMenu(f);
            test.assertTrue(f.menu() == f.player.inventoryMenu, "Close packet returns to the inventory menu");
            test.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "Temporary storage is cleaned up");
            test.assertValueEqual(inventoryCount(f, input), 7, "Locked temporary input is returned by vanilla cleanup");
            test.assertValueEqual(inventoryCount(f, cursor), 3, "Locked cursor is returned by vanilla cleanup");
            test.assertValueEqual(f.droppedCount(input) + f.droppedCount(cursor), 0, "Room in inventory prevents cleanup drops");

            f.clearContents();
            f.openChest(new SimpleContainer(27));
            for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
                f.player.getInventory().setItem(index, new ItemStack(Items.COBBLESTONE, 64));
            }
            menu = f.menu();
            menu.setCarried(cursor.copy());
            closeMenu(f);
            test.assertTrue(menu.getCarried().isEmpty(), "A full inventory still clears the abandoned cursor");
            test.assertValueEqual(f.droppedCount(cursor), 3, "Full-inventory cleanup retains vanilla tagged-item drops");
            test.assertValueEqual(inventoryCount(f, new ItemStack(Items.COBBLESTONE)), Inventory.INVENTORY_SIZE * 64,
                    "Cleanup does not overwrite the full inventory");
            test.succeed();
        }
    }

    @GameTest
    public void deathRetainsVanillaDropsForLockedItems(GameTestHelper test) {
        var rules = test.getLevel().getGameRules();
        boolean keepInventory = rules.get(GameRules.KEEP_INVENTORY);
        rules.set(GameRules.KEEP_INVENTORY, false, test.getLevel().getServer());
        try (var f = fixture(test, GameType.SURVIVAL)) {
            var locked = locked(f.player, "paper", 7);
            var offhand = locked(f.player, "diamond", 3);
            var ordinary = new ItemStack(Items.EMERALD, 2);
            f.player.getInventory().setItem(0, locked.copy());
            f.player.getInventory().setItem(Inventory.SLOT_OFFHAND, offhand.copy());
            f.player.getInventory().setItem(9, ordinary.copy());
            f.player.setHealth(0);
            f.player.die(f.player.damageSources().generic());
            test.assertTrue(inventoryCopies(f).stream().allMatch(ItemStack::isEmpty), "Death clears inventory including tagged stacks");
            test.assertValueEqual(f.droppedCount(locked), 7, "Death drops preserve main-hand custom data and quantity");
            test.assertValueEqual(f.droppedCount(offhand), 3, "Death drops preserve offhand custom data and quantity");
            test.assertValueEqual(f.droppedCount(ordinary), 2, "Ordinary death drops remain unchanged");
            test.succeed();
        } finally {
            rules.set(GameRules.KEEP_INVENTORY, keepInventory, test.getLevel().getServer());
        }
    }

    private static Fixture fixture(GameTestHelper test, GameType mode) {
        var player = (ServerPlayer) test.makeMockServerPlayer(mode);
        var connection = new RecordingConnection();
        new ServerGamePacketListenerImpl(test.getLevel().getServer(), connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setPos(test.absoluteVec(new Vec3(2.5, 2, 2.5)));
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.initInventoryMenu();
        RuneLegendServer.find(test.getLevel().getServer()).stamina().join(player);
        connection.packets.clear();
        return new Fixture(player, connection);
    }

    private static ItemStack parse(ServerPlayer player, String item, int count) {
        try {
            return new ItemParser(player.level().registryAccess()).parse(new StringReader(item)).createItemStack(count);
        } catch (CommandSyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    private static ItemStack locked(ServerPlayer player, String item, int count) {
        return parse(player, "minecraft:" + item + "[minecraft:custom_data=" + LOCK + "]", count);
    }

    private static int inventorySlot(Fixture f, int inventoryIndex) {
        return f.menu().findSlot(f.player.getInventory(), inventoryIndex).orElseThrow();
    }

    private static void click(Fixture f, int slot, int button, ContainerInput input) {
        f.connection.packets.clear();
        // Deliberately omit client predictions. The server must execute against real stacks, not this map.
        f.player.connection.handleContainerClick(new ServerboundContainerClickPacket(f.menu().containerId,
                f.menu().getStateId(), (short) slot, (byte) button, input,
                new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));
    }

    private static void drag(Fixture f, int slot, int stage, int type) {
        click(f, slot, AbstractContainerMenu.getQuickcraftMask(stage, type), ContainerInput.QUICK_CRAFT);
    }

    private static void playerAction(Fixture f, ServerboundPlayerActionPacket.Action action) {
        f.connection.packets.clear();
        f.player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN));
    }

    private static void closeMenu(Fixture f) {
        f.player.connection.handleContainerClose(new ServerboundContainerClosePacket(f.menu().containerId));
    }

    private static RecipeDisplayId unlockRecipe(Fixture f, String path) {
        var recipes = f.player.level().getServer().getRecipeManager();
        var recipe = recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(path))).orElseThrow();
        f.player.awardRecipes(List.of(recipe));
        var displays = new ArrayList<RecipeDisplayEntry>();
        recipes.listDisplaysForRecipe(recipe.id(), displays::add);
        return displays.getFirst().id();
    }

    private static void placeRecipe(Fixture f, RecipeDisplayId recipe) {
        f.connection.packets.clear();
        f.player.connection.handlePlaceRecipe(new ServerboundPlaceRecipePacket(f.menu().containerId, recipe, false));
    }

    private static void fillHotbar(Fixture f) {
        f.player.getInventory().setSelectedSlot(0);
        for (int index = 0; index < Inventory.getSelectionSize(); index++) {
            f.player.getInventory().setItem(index, new ItemStack(Items.BRICKS));
        }
    }

    private static void assertDragResult(GameTestHelper test, Fixture f, ItemStack locked, int type, String message) {
        int amount = type == AbstractContainerMenu.QUICKCRAFT_TYPE_CHARITABLE ? 4 : 1;
        stackEquals(test, f.menu().getSlot(0).getItem(), new ItemStack(Items.PAPER, amount), message + ": first slot");
        stackEquals(test, f.menu().getSlot(1).getItem(), locked, message + ": locked slot");
        stackEquals(test, f.menu().getSlot(2).getItem(), new ItemStack(Items.PAPER, amount), message + ": second slot");
        stackEquals(test, f.menu().getCarried(), new ItemStack(Items.PAPER, 8 - 2 * amount), message + ": cursor remainder");
    }

    private static int inventoryCount(Fixture f, ItemStack template) {
        return inventoryCopies(f).stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, template))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static List<ItemStack> inventoryCopies(Fixture f) {
        var result = new ArrayList<ItemStack>();
        for (int index = 0; index < f.player.getInventory().getContainerSize(); index++) {
            result.add(f.player.getInventory().getItem(index).copy());
        }
        return result;
    }

    private static Snapshot snapshot(Fixture f) {
        var drops = new HashMap<UUID, ItemStack>();
        f.drops().forEach(entity -> drops.put(entity.getUUID(), entity.getItem().copy()));
        return new Snapshot(f.menu().slots.stream().map(slot -> slot.getItem().copy()).toList(),
                inventoryCopies(f), f.menu().getCarried().copy(), f.player.getInventory().getSelectedSlot(), drops);
    }

    private static void unchanged(GameTestHelper test, Fixture f, Snapshot before, String message) {
        var after = snapshot(f);
        stackListEquals(test, after.slots, before.slots, message + ": all menu slots");
        stackListEquals(test, after.inventory, before.inventory, message + ": all inventory slots including equipment");
        stackEquals(test, after.carried, before.carried, message + ": cursor");
        test.assertValueEqual(after.selected, before.selected, message + ": selected hotbar index");
        test.assertValueEqual(after.drops.keySet(), before.drops.keySet(), message + ": no new or removed drop entities");
        before.drops.forEach((id, stack) -> stackEquals(test, after.drops.get(id), stack, message + ": dropped components and count"));
    }

    private static void assertMenuCorrection(GameTestHelper test, Fixture f, Snapshot expected, String message) {
        var packets = f.connection.packets.stream().filter(ClientboundContainerSetContentPacket.class::isInstance)
                .map(ClientboundContainerSetContentPacket.class::cast)
                .filter(packet -> packet.containerId() == f.menu().containerId).toList();
        test.assertTrue(!packets.isEmpty(), message + ": a full correction packet was sent");
        var packet = packets.getLast();
        stackListEquals(test, packet.items(), expected.slots, message + ": authoritative slot contents");
        stackEquals(test, packet.carriedItem(), expected.carried, message + ": authoritative cursor contents");
        test.assertValueEqual(packet.stateId(), f.menu().getStateId(), message + ": authoritative state ID");
    }

    private static void assertInventoryCorrection(GameTestHelper test, Fixture f, int slot, ItemStack expected) {
        test.assertTrue(f.connection.packets.stream().anyMatch(packet -> packet instanceof ClientboundSetPlayerInventoryPacket inventory
                        && inventory.slot() == slot && ItemStack.matches(inventory.contents(), expected)),
                "A forced inventory packet corrects slot " + slot + " with its real components and count");
    }

    private static void assertHeldSlotCorrection(GameTestHelper test, Fixture f, int expected) {
        test.assertTrue(f.connection.packets.stream().anyMatch(packet -> packet instanceof ClientboundSetHeldSlotPacket held
                        && held.slot() == expected), "Pick sends the authoritative selected hotbar index");
    }

    private static void stackListEquals(GameTestHelper test, List<ItemStack> actual, List<ItemStack> expected, String message) {
        test.assertValueEqual(actual.size(), expected.size(), message + ": slot count");
        for (int index = 0; index < expected.size(); index++) {
            stackEquals(test, actual.get(index), expected.get(index), message + " [" + index + "]");
        }
    }

    private static void stackEquals(GameTestHelper test, ItemStack actual, ItemStack expected, String message) {
        test.assertTrue(ItemStack.matches(actual, expected), message + ": expected " + expected + ", actual " + actual);
    }

    private record TagCase(String data, boolean locked) {}

    private record Snapshot(List<ItemStack> slots, List<ItemStack> inventory, ItemStack carried,
                            int selected, Map<UUID, ItemStack> drops) {}

    private record Fixture(ServerPlayer player, RecordingConnection connection) implements AutoCloseable {
        AbstractContainerMenu menu() { return player.containerMenu; }

        void open(MenuConstructor constructor) {
            if (player.openMenu(new SimpleMenuProvider(constructor, Component.literal("Item Lock test"))).isEmpty()) {
                throw new AssertionError("Failed to open a real vanilla menu");
            }
            connection.packets.clear();
        }

        void openChest(SimpleContainer container) {
            open((id, inventory, owner) -> ChestMenu.threeRows(id, inventory, container));
        }

        void clearContents() {
            menu().setCarried(ItemStack.EMPTY);
            player.getInventory().clearContent();
            menu().slots.forEach(slot -> slot.set(ItemStack.EMPTY));
            connection.packets.clear();
        }

        List<ItemEntity> drops() {
            return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4));
        }

        int droppedCount(ItemStack template) {
            return drops().stream().map(ItemEntity::getItem)
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, template)).mapToInt(ItemStack::getCount).sum();
        }

        @Override public void close() {
            // These mock players are not on the online tick list. Clean their joined stamina explicitly.
            RuneLegendServer.find(player.level().getServer()).leave(player);
            clearContents();
            player.doCloseContainer();
            drops().forEach(ItemEntity::discard);
        }
    }

    private static final class RecordingConnection extends Connection {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection() { super(PacketFlow.SERVERBOUND); }

        // 26.3 ServerCommonPacketListenerImpl.send delegates to this exact overload.
        @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
            packets.add(packet);
        }
    }

    /** Only the storage refills; ChestMenu and its repeated QUICK_MOVE/THROW loops remain vanilla. */
    private static final class RefillingContainer extends SimpleContainer {
        private ItemStack refill = ItemStack.EMPTY;

        private RefillingContainer() { super(27); }

        @Override public void setItem(int slot, ItemStack stack) {
            if (slot == 0 && stack.isEmpty() && !refill.isEmpty()) {
                var next = refill;
                refill = ItemStack.EMPTY;
                super.setItem(slot, next);
            } else {
                super.setItem(slot, stack);
            }
        }
    }
}
