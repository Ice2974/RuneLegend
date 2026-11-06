package com.runelegend.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.test.mixin.ItemLockMerchantScreenAccessor;
import com.runelegend.test.mixin.ItemLockScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Real input and packet observation, with an independent dedicated server inventory as the oracle. */
public final class ItemLockClientGameTest implements FabricClientGameTest {
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final ConcurrentLinkedQueue<Packet<?>> REQUESTS = new ConcurrentLinkedQueue<>();
    private static volatile boolean observing;

    public static void observePacket(Packet<?> packet) {
        if (!observing) return;
        if (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundPlaceRecipePacket
                || packet instanceof ServerboundSelectTradePacket || packet instanceof ServerboundSetCreativeModeSlotPacket
                || packet instanceof ServerboundPlayerActionPacket action && switch (action.getAction()) {
                    case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> true;
                    default -> false;
                }) REQUESTS.add(packet);
    }

    @Override public void runTest(ClientGameTestContext context) {
        restoreGameOptions(context);
        var props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("white-list", "false");
        props.setProperty("difficulty", "peaceful");
        try (var server = context.worldBuilder().createServer(props)) {
            State saved;
            UUID playerId;
            try (var connection = server.connect()) {
                context.waitFor(client -> client.player != null && ClientStateCache.INSTANCE.view() != null);
                connection.waitForChunksRender();
                server.runCommand("gamemode survival @p");
                observing = true;
                var fixture = new Fixture(context, server, connection);
                checkContainerInputs(fixture);
                checkWorldKeys(fixture);
                checkDragging(fixture);
                checkRecipeRequest(fixture);
                checkTradeSelection(fixture);
                checkCreativeTransition(fixture);
                fixture.prepare(Map.of(0, locked(Items.PAPER, 8), 1, stack(Items.STONE, 4)));
                // Persist the exact authoritative stacks before disconnect, including Custom Data.
                server.runOnServer(minecraft -> minecraft.getPlayerList().saveAll());
                saved = server.computeOnServer(minecraft -> State.capture(connection.getServerPlayer()));
                playerId = server.computeOnServer(minecraft -> connection.getServerPlayer().getUUID());
            }
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().isEmpty());
            context.waitFor(client -> client.player == null && ClientStateCache.INSTANCE.view() == null);
            try (var connection = server.connect()) {
                context.waitFor(client -> client.player != null && ClientStateCache.INSTANCE.view() != null);
                connection.waitForChunksRender();
                var fixture = new Fixture(context, server, connection);
                fixture.awaitAgreement();
                if (!server.computeOnServer(minecraft -> playerId.equals(connection.getServerPlayer().getUUID())
                        && saved.matches(State.capture(connection.getServerPlayer())))) {
                    throw new AssertionError("Reconnect did not restore the same player's saved inventory and components");
                }
                checkReconnect(fixture);
                fixture.closeScreen();
            }
        } finally {
            observing = false;
            REQUESTS.clear();
            context.getInput().releaseMouse(LEFT);
            context.getInput().releaseMouse(RIGHT);
            context.getInput().releaseShift();
            context.getInput().releaseControl();
            context.getInput().releaseKey(options -> options.keySwapOffhand);
            context.getInput().releaseKey(options -> options.keyDrop);
            restoreGameOptions(context);
        }
    }

    private static void restoreGameOptions(ClientGameTestContext context) {
        // Release using the old bindings before replacing them, then rebuild vanilla's key lookup table.
        context.getInput().releaseKey(options -> options.keyDrop);
        context.getInput().releaseKey(options -> options.keySwapOffhand);
        context.restoreDefaultGameOptions();
        context.runOnClient(client -> {
            KeyMapping.releaseAll();
            KeyMapping.resetMapping();
        });
    }

    private static void checkContainerInputs(Fixture f) {
        f.prepare(Map.of(9, locked(Items.PAPER, 8), 10, stack(Items.STONE, 4),
                0, stack(Items.STICK, 3), Inventory.SLOT_OFFHAND, stack(Items.FEATHER, 2)));
        f.openInventory();
        f.rejected("left pickup", () -> f.click(9, LEFT));
        f.rejected("right split", () -> f.click(9, RIGHT));
        f.rejected("shift move", () -> f.shiftClick(9));
        f.rejected("number swap source", () -> {
            f.pointAtInventorySlot(9);
            f.context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        });
        f.rejected("GUI offhand swap source", () -> {
            f.pointAtInventorySlot(9);
            f.context.getInput().pressKey(options -> options.keySwapOffhand);
        });
        f.rejected("GUI Q", () -> {
            f.pointAtInventorySlot(9);
            f.context.getInput().pressKey(options -> options.keyDrop);
        });
        f.rejected("GUI Ctrl+Q", () -> f.controlDrop(9));

        f.prepare(Map.of(0, locked(Items.STICK, 3), 9, stack(Items.PAPER, 8),
                Inventory.SLOT_OFFHAND, locked(Items.FEATHER, 2)));
        f.openInventory();
        f.rejected("number swap destination", () -> {
            f.pointAtInventorySlot(9);
            f.context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        });
        f.rejected("GUI offhand swap destination", () -> {
            f.pointAtInventorySlot(9);
            f.context.getInput().pressKey(options -> options.keySwapOffhand);
        });

        f.prepare(Map.of(9, stack(Items.PAPER, 8), 10, stack(Items.STONE, 4),
                0, stack(Items.STICK, 3), Inventory.SLOT_OFFHAND, stack(Items.FEATHER, 2)));
        f.openInventory();
        f.allowed("ordinary pickup", () -> f.click(9, LEFT));
        f.assertCarried(stack(Items.PAPER, 8));
        f.allowed("ordinary placement", () -> f.click(11, LEFT));
        f.allowed("ordinary split", () -> f.click(11, RIGHT));
        f.assertCarried(stack(Items.PAPER, 4));
        f.allowed("ordinary right placement", () -> f.click(12, RIGHT));
        f.allowed("ordinary merge", () -> f.click(11, LEFT));
        f.assertSlot(11, stack(Items.PAPER, 7));
        f.allowed("ordinary shift move", () -> f.shiftClick(10));
        assertContainerRequest(ContainerInput.QUICK_MOVE, 0);
        f.assertSlot(10, ItemStack.EMPTY);
        f.assertCarried(ItemStack.EMPTY);
        f.allowed("ordinary number swap", () -> {
            f.pointAtInventorySlot(11);
            f.context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        });
        assertContainerRequest(ContainerInput.SWAP, 0);
        f.assertSlot(0, stack(Items.PAPER, 7));
        f.assertSlot(11, stack(Items.STICK, 3));
        f.allowed("ordinary GUI Q", () -> {
            f.pointAtInventorySlot(11);
            f.context.getInput().pressKey(options -> options.keyDrop);
        });
        assertContainerRequest(ContainerInput.THROW, 0);
        f.assertSlot(11, stack(Items.STICK, 2));
        f.allowed("ordinary GUI Ctrl+Q", () -> f.controlDrop(12));
        assertContainerRequest(ContainerInput.THROW, 1);
        f.assertSlot(12, ItemStack.EMPTY);
        f.allowed("ordinary GUI offhand swap", () -> {
            f.pointAtInventorySlot(11);
            f.context.getInput().pressKey(options -> options.keySwapOffhand);
        });
        assertContainerRequest(ContainerInput.SWAP, Inventory.SLOT_OFFHAND);
        f.assertSlot(11, stack(Items.FEATHER, 2));
        f.assertSlot(Inventory.SLOT_OFFHAND, stack(Items.STICK, 2));
    }

    private static void checkWorldKeys(Fixture f) {
        f.prepare(Map.of(0, locked(Items.PAPER, 8), Inventory.SLOT_OFFHAND, stack(Items.STONE, 4)));
        f.rejected("world Q", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.rejected("world Ctrl+Q", () -> f.controlDrop(-1));
        f.rejected("world F main hand", () -> f.context.getInput().pressKey(options -> options.keySwapOffhand));
        f.context.runOnClient(client -> {
            client.options.keyDrop.setKey(InputConstants.getKey("key.keyboard.r"));
            client.options.keySwapOffhand.setKey(InputConstants.getKey("key.keyboard.g"));
            KeyMapping.resetMapping();
        });
        f.rejected("rebound drop", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.rejected("rebound swap", () -> f.context.getInput().pressKey(options -> options.keySwapOffhand));

        f.prepare(Map.of(0, stack(Items.STONE, 4), Inventory.SLOT_OFFHAND, locked(Items.PAPER, 8)));
        f.rejected("world F offhand", () -> f.context.getInput().pressKey(options -> options.keySwapOffhand));
        f.allowed("blocked F does not cancel Q in the same tick", () -> {
            f.context.getInput().holdKey(options -> options.keySwapOffhand);
            f.context.getInput().holdKey(options -> options.keyDrop);
            try {
                f.context.waitTick();
            } finally {
                f.context.getInput().releaseKey(options -> options.keySwapOffhand);
                f.context.getInput().releaseKey(options -> options.keyDrop);
            }
        });
        f.assertSlot(0, stack(Items.STONE, 3));
        if (REQUESTS.stream().anyMatch(packet -> packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND)) {
            throw new AssertionError("Blocked simultaneous swap sent a packet");
        }

        f.prepare(Map.of(0, stack(Items.STONE, 4), Inventory.SLOT_OFFHAND, stack(Items.PAPER, 2)));
        f.allowed("ordinary rebound swap", () -> f.context.getInput().pressKey(options -> options.keySwapOffhand));
        f.assertSlot(0, stack(Items.PAPER, 2));
        f.allowed("ordinary rebound drop", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.assertSlot(0, stack(Items.PAPER, 1));
        f.allowed("ordinary rebound whole-stack drop", () -> f.controlDrop(-1));
        if (REQUESTS.stream().noneMatch(packet -> packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) {
            throw new AssertionError("World Ctrl+Q did not send DROP_ALL_ITEMS");
        }
        f.assertSlot(0, ItemStack.EMPTY);
        restoreGameOptions(f.context);
        f.prepare(Map.of(0, stack(Items.PAPER, 4), Inventory.SLOT_OFFHAND, stack(Items.STONE, 2)));
        f.allowed("restored default Q", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.assertSlot(0, stack(Items.PAPER, 3));
        f.allowed("restored default F", () -> f.context.getInput().pressKey(options -> options.keySwapOffhand));
        f.assertSlot(0, stack(Items.STONE, 2));
        f.assertSlot(Inventory.SLOT_OFFHAND, stack(Items.PAPER, 3));
    }

    private static void checkDragging(Fixture f) {
        f.prepare(Map.of(9, stack(Items.PAPER, 12)));
        f.openInventory();
        f.allowed("pick up drag source", () -> f.click(9, LEFT));
        REQUESTS.clear();
        f.beginDrag(10, 11, 12);
        try {
            f.server.runOnServer(server -> {
                var player = f.connection.getServerPlayer();
                player.getInventory().setItem(11, locked(Items.PAPER, 2));
                player.containerMenu.sendAllDataToRemote();
            });
            f.context.waitFor(client -> ItemStack.matches(client.player.getInventory().getItem(11), locked(Items.PAPER, 2)));
            f.context.waitFor(client -> ((ItemLockScreenAccessor) client.gui.screen()).runelegend$quickCraftSlots()
                    .stream().noneMatch(slot -> slot.container == client.player.getInventory() && slot.getContainerSlot() == 11));
            f.context.takeScreenshot("runelegend-item-lock-dynamic-drag");
        } finally {
            f.context.getInput().releaseMouse(LEFT);
        }
        f.flush();
        f.assertSlot(10, stack(Items.PAPER, 6));
        f.assertSlot(11, locked(Items.PAPER, 2));
        f.assertSlot(12, stack(Items.PAPER, 6));
        f.assertCarried(ItemStack.EMPTY);
        int lockedSlot = f.context.computeOnClient(client -> inventoryMenuSlot(client, 11));
        if (REQUESTS.stream().anyMatch(packet -> packet instanceof ServerboundContainerClickPacket click
                && click.containerInput() == ContainerInput.QUICK_CRAFT && click.slotNum() == lockedSlot)) {
            throw new AssertionError("Dynamic locked drag candidate was sent to the server");
        }

        f.prepare(Map.of(9, stack(Items.PAPER, 12)));
        f.openInventory();
        f.allowed("pick up future locked cursor", () -> f.click(9, LEFT));
        f.setCarried(locked(Items.PAPER, 12));
        f.rejected("locked cursor drag", () -> f.drag(10, 11));
        f.rejected("locked cursor outside drop", () -> {
            f.pointInsideScreen(-20, -20);
            f.context.getInput().pressMouse(LEFT);
        });
        f.setCarried(stack(Items.PAPER, 12));
        f.allowed("normal drag after rejection", () -> f.drag(10, 11));
        f.assertSlot(10, stack(Items.PAPER, 6));
        f.assertSlot(11, stack(Items.PAPER, 6));
        f.assertCarried(ItemStack.EMPTY);
    }

    private static void checkRecipeRequest(Fixture f) {
        f.prepare(Map.of());
        f.openInventory();
        f.server.runOnServer(server -> {
            var player = f.connection.getServerPlayer();
            player.inventoryMenu.getInputGridSlots().getFirst().set(locked(Items.OAK_PLANKS, 1));
            player.containerMenu.sendAllDataToRemote();
        });
        f.awaitAgreement();
        // Exercise the real request entry point, not the policy. No recipe lookup happens on this blocked path.
        f.rejected("recipe request with locked input", () -> f.context.runOnClient(client ->
                client.gameMode.handlePlaceRecipe(client.player.containerMenu.containerId, new RecipeDisplayId(0), false)));
    }

    private static void checkTradeSelection(Fixture f) {
        f.prepare(Map.of(9, stack(Items.EMERALD, 8), 10, stack(Items.PAPER, 8)));
        f.server.runOnServer(server -> {
            var player = f.connection.getServerPlayer();
            var villager = new Villager(EntityTypes.VILLAGER, player.level());
            villager.setNoAi(true);
            villager.setPos(player.getX() + 2, player.getY(), player.getZ());
            var offers = new MerchantOffers();
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), Optional.of(new ItemCost(Items.PAPER)),
                    stack(Items.APPLE, 1), 20, 0, 0));
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), Optional.of(new ItemCost(Items.PAPER)),
                    stack(Items.BREAD, 1), 20, 0, 0));
            // AbstractVillager.overrideOffers is a no-op; mutate the authoritative offers before opening the menu.
            villager.getOffers().clear();
            villager.getOffers().addAll(offers);
            player.level().addFreshEntity(villager);
            villager.setTradingPlayer(player);
            villager.openTradingScreen(player, Component.literal("Item Lock fixture"), 1);
            if (!(player.containerMenu instanceof MerchantMenu menu) || menu.getOffers().size() != 2) {
                throw new AssertionError("Merchant fixture did not open with both authoritative offers");
            }
        });
        f.context.waitForScreen(MerchantScreen.class);
        f.context.waitFor(client -> ((MerchantMenu) client.player.containerMenu).getOffers().size() == 2);
        f.allowed("ordinary trade selection", () -> f.clickTrade(1));
        f.assertTradeHighlight(1);
        f.server.runOnServer(server -> {
            var menu = f.connection.getServerPlayer().containerMenu;
            menu.getSlot(1).set(locked(Items.PAPER, 1));
            menu.sendAllDataToRemote();
        });
        f.awaitAgreement();
        f.rejected("locked second payment slot protects both payments", () -> f.clickTrade(0));
        f.assertTradeHighlight(1);
        f.server.runOnServer(server -> {
            var menu = f.connection.getServerPlayer().containerMenu;
            menu.getSlot(1).set(stack(Items.PAPER, 1));
            menu.sendAllDataToRemote();
        });
        f.awaitAgreement();
        f.allowed("trade selection after unlock", () -> f.clickTrade(0));
        f.assertTradeHighlight(0);
    }

    private static void checkCreativeTransition(Fixture f) {
        f.prepare(Map.of(0, locked(Items.PAPER, 8), Inventory.SLOT_OFFHAND, locked(Items.STONE, 4)));
        f.server.runCommand("gamemode creative @p");
        f.context.waitFor(client -> client.player.isCreative());
        f.allowed("creative locked drop", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.assertSlot(0, locked(Items.PAPER, 7));
        f.allowed("creative locked swap", () -> f.context.getInput().pressKey(options -> options.keySwapOffhand));
        f.assertSlot(0, locked(Items.STONE, 4));
        f.server.runCommand("gamemode survival @p");
        f.context.waitFor(client -> !client.player.isCreative());
        f.rejected("survival drop after creative", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.rejected("survival swap after creative", () -> f.context.getInput().pressKey(options -> options.keySwapOffhand));
        f.server.runCommand("runelegend game start");
        f.context.waitFor(client -> ClientStateCache.INSTANCE.rules().running());
        f.rejected("running round uses the same lock", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.server.runCommand("runelegend game stop");
    }

    private static void checkReconnect(Fixture f) {
        f.assertSlot(0, locked(Items.PAPER, 8));
        f.assertSlot(1, stack(Items.STONE, 4));
        f.rejected("reconnected locked Q", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.openInventory();
        f.rejected("reconnected locked click", () -> f.click(0, LEFT));
        f.allowed("reconnected ordinary pickup", () -> f.click(1, LEFT));
        f.assertCarried(stack(Items.STONE, 4));
        f.allowed("reconnected ordinary placement", () -> f.click(1, LEFT));
        f.assertSlot(1, stack(Items.STONE, 4));
        f.closeScreen();
        f.context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        f.context.waitFor(client -> client.player.getInventory().getSelectedSlot() == 1);
        f.server.waitFor(minecraft -> f.connection.getServerPlayer().getInventory().getSelectedSlot() == 1);
        f.allowed("reconnected ordinary Q", () -> f.context.getInput().pressKey(options -> options.keyDrop));
        f.assertSlot(1, stack(Items.STONE, 3));
        f.assertSlot(0, locked(Items.PAPER, 8));
    }

    private static ItemStack stack(Item item, int count) { return new ItemStack(item, count); }

    private static void assertContainerRequest(ContainerInput input, int button) {
        if (REQUESTS.stream().noneMatch(packet -> packet instanceof ServerboundContainerClickPacket click
                && click.containerInput() == input && click.buttonNum() == button)) {
            throw new AssertionError("Expected " + input + " button " + button + ", got " + REQUESTS);
        }
    }

    private static ItemStack locked(Item item, int count) {
        var stack = stack(item, count);
        var tag = new CompoundTag();
        tag.putBoolean("runelegend:item_lock", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static int inventoryMenuSlot(Minecraft client, int inventoryIndex) {
        return client.player.containerMenu.slots.stream()
                .filter(slot -> slot.container == client.player.getInventory() && slot.getContainerSlot() == inventoryIndex)
                .findFirst().orElseThrow().index;
    }

    private record State(List<ItemStack> inventory, List<ItemStack> menu, ItemStack carried) {
        static State capture(Player player) {
            var inventory = new ArrayList<ItemStack>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) inventory.add(player.getInventory().getItem(i).copy());
            return new State(inventory, player.containerMenu.slots.stream().map(slot -> slot.getItem().copy()).toList(),
                    player.containerMenu.getCarried().copy());
        }

        boolean matches(State other) {
            return matches(inventory, other.inventory) && matches(menu, other.menu) && ItemStack.matches(carried, other.carried);
        }

        private static boolean matches(List<ItemStack> first, List<ItemStack> second) {
            if (first.size() != second.size()) return false;
            for (int i = 0; i < first.size(); i++) if (!ItemStack.matches(first.get(i), second.get(i))) return false;
            return true;
        }
    }

    private record Fixture(ClientGameTestContext context, TestDedicatedServerContext server,
                           TestDedicatedServerConnection connection) {
        void prepare(Map<Integer, ItemStack> contents) {
            closeScreen();
            server.runOnServer(minecraft -> {
                var player = connection.getServerPlayer();
                player.getInventory().clearContent();
                player.inventoryMenu.getCraftSlots().clearContent();
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
                contents.forEach((index, stack) -> player.getInventory().setItem(index, stack.copy()));
                player.getInventory().setSelectedSlot(0);
                player.connection.send(new ClientboundSetHeldSlotPacket(0));
                player.inventoryMenu.sendAllDataToRemote();
                player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32)).forEach(ItemEntity::discard);
            });
            awaitAgreement();
        }

        void openInventory() {
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
        }

        void closeScreen() {
            if (context.computeOnClient(client -> client.gui.screen() != null)) {
                context.getInput().pressKey(InputConstants.KEY_ESCAPE);
                context.waitFor(client -> client.gui.screen() == null);
                flush();
            }
        }

        void flush() {
            context.waitTick();
            connection.waitForServerboundPackets();
            connection.waitForClientboundPackets();
        }

        void awaitAgreement() {
            flush();
            var expected = server.computeOnServer(minecraft -> State.capture(connection.getServerPlayer()));
            context.waitFor(client -> expected.matches(State.capture(client.player)));
        }

        void rejected(String name, Runnable input) {
            awaitAgreement();
            var before = server.computeOnServer(minecraft -> State.capture(connection.getServerPlayer()));
            int droppedBefore = droppedItems();
            REQUESTS.clear();
            input.run();
            flush();
            if (!REQUESTS.isEmpty()) throw new AssertionError(name + " sent a request: " + REQUESTS);
            if (!context.computeOnClient(client -> before.matches(State.capture(client.player)))
                    || !server.computeOnServer(minecraft -> before.matches(State.capture(connection.getServerPlayer())))
                    || droppedBefore != droppedItems()) throw new AssertionError(name + " changed locked inventory or dropped an item");
        }

        void allowed(String name, Runnable input) {
            awaitAgreement();
            var before = server.computeOnServer(minecraft -> State.capture(connection.getServerPlayer()));
            REQUESTS.clear();
            input.run();
            awaitAgreement();
            if (REQUESTS.isEmpty()) throw new AssertionError(name + " sent no request; before=" + before
                    + ", client=" + context.computeOnClient(client -> State.capture(client.player)));
            if (server.computeOnServer(minecraft -> before.matches(State.capture(connection.getServerPlayer())))) {
                throw new AssertionError(name + " did not change the authoritative inventory");
            }
        }

        int droppedItems() {
            return server.computeOnServer(minecraft -> {
                var player = connection.getServerPlayer();
                return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32))
                        .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            });
        }

        void assertSlot(int index, ItemStack expected) {
            awaitAgreement();
            var actual = server.computeOnServer(minecraft -> connection.getServerPlayer().getInventory().getItem(index).copy());
            if (!ItemStack.matches(expected, actual)) throw new AssertionError("Inventory " + index + ": expected " + expected + ", got " + actual);
        }

        void assertCarried(ItemStack expected) {
            awaitAgreement();
            var actual = server.computeOnServer(minecraft -> connection.getServerPlayer().containerMenu.getCarried().copy());
            if (!ItemStack.matches(expected, actual)) throw new AssertionError("Cursor: expected " + expected + ", got " + actual);
        }

        void setCarried(ItemStack carried) {
            server.runOnServer(minecraft -> {
                var menu = connection.getServerPlayer().containerMenu;
                menu.setCarried(carried.copy());
                menu.sendAllDataToRemote();
            });
            awaitAgreement();
        }

        void pointAtInventorySlot(int index) {
            var point = context.computeOnClient(client -> {
                var slot = client.player.containerMenu.getSlot(inventoryMenuSlot(client, index));
                return screenPoint(client, slot.x + 8, slot.y + 8);
            });
            context.getInput().setCursorPos(point[0], point[1]);
            // Keyboard container actions use the slot cached by extractContents, not the new mouse coordinates.
            context.waitFor(client -> {
                var hovered = ((ItemLockScreenAccessor) client.gui.screen()).runelegend$hoveredSlot();
                return hovered != null && hovered.container == client.player.getInventory() && hovered.getContainerSlot() == index;
            });
        }

        void pointInsideScreen(int x, int y) {
            var point = context.computeOnClient(client -> screenPoint(client, x, y));
            context.getInput().setCursorPos(point[0], point[1]);
            context.waitTick();
        }

        private static double[] screenPoint(Minecraft client, int x, int y) {
            var screen = (AbstractContainerScreen<?>) client.gui.screen();
            var position = (ItemLockScreenAccessor) screen;
            var window = client.getWindow();
            return new double[] {(position.runelegend$leftPos() + x) * (double) window.getScreenWidth() / screen.width,
                    (position.runelegend$topPos() + y) * (double) window.getScreenHeight() / screen.height};
        }

        void click(int index, int button) {
            // Keep independent clicks outside vanilla's double-click window.
            context.waitTicks(6);
            pointAtInventorySlot(index);
            context.getInput().pressMouse(button);
        }

        void shiftClick(int index) {
            context.getInput().holdShift();
            try {
                click(index, LEFT);
            } finally {
                context.getInput().releaseShift();
            }
        }

        void controlDrop(int inventoryIndex) {
            if (inventoryIndex >= 0) pointAtInventorySlot(inventoryIndex);
            context.getInput().holdControl();
            try {
                context.getInput().pressKey(options -> options.keyDrop);
            } finally {
                context.getInput().releaseControl();
            }
        }

        void beginDrag(int... indices) {
            context.waitTicks(6);
            pointAtInventorySlot(indices[0]);
            context.getInput().holdMouse(LEFT);
            // A move while held is required to include the initial slot in vanilla's candidates.
            context.getInput().moveCursor(1, 0);
            context.waitTick();
            for (int i = 1; i < indices.length; i++) pointAtInventorySlot(indices[i]);
        }

        void drag(int... indices) {
            try {
                beginDrag(indices);
            } finally {
                context.getInput().releaseMouse(LEFT);
            }
        }

        void clickTrade(int index) {
            context.waitTicks(6);
            pointInsideScreen(15, 28 + 20 * index);
            context.getInput().pressMouse(LEFT);
        }

        void assertTradeHighlight(int index) {
            int selected = context.computeOnClient(client -> ((ItemLockMerchantScreenAccessor) client.gui.screen()).runelegend$shopItem());
            if (selected != index) throw new AssertionError("Trade highlight: expected " + index + ", got " + selected);
        }
    }
}
