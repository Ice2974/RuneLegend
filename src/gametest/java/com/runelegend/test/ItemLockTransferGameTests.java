package com.runelegend.test;

import com.mojang.authlib.GameProfile;
import com.runelegend.RuneLegendServer;
import com.runelegend.item.ItemLockPolicy;
import io.netty.channel.ChannelFutureListener;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ItemLockTransferGameTests {
    @GameTest
    public void recipeCountsAndSearchSkipLockedStacksWithoutChangingGlobalEligibility(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            var inventory = player.getInventory();
            var recipe = planksRecipe(player);
            var locked = locked(Items.OAK_LOG, 3);
            inventory.setItem(0, locked.copy());
            inventory.setItem(1, new ItemStack(Items.OAK_LOG, 2));
            var contents = new StackedItemContents();
            inventory.fillStackedContents(contents);
            test.assertValueEqual(contents.getBiggestCraftableStack(recipe.value(), null), 2,
                    "Recipe availability counts only ordinary ingredients");
            test.assertValueEqual(inventory.findSlotMatchingCraftingIngredient(locked.typeHolder(), ItemStack.EMPTY), 1,
                    "The material search skips the locked first candidate and finds the next stack");
            test.assertTrue(Inventory.isUsableForCrafting(locked),
                    "The shared static vanilla predicate remains unchanged outside a player's recipe transfer");

            player.inventoryMenu.handlePlacement(true, false, recipe, player.level(), inventory);
            same(test, inventory.getItem(0), locked, "Actual placement preserves the locked stack");
            test.assertTrue(inventory.getItem(1).isEmpty(), "Actual placement consumes the available ordinary logs");
            test.assertValueEqual(inputCount(player), 2, "The crafting grid receives both ordinary logs");
            test.assertTrue(player.inventoryMenu.getInputGridSlots().stream()
                    .noneMatch(slot -> ItemLockPolicy.isLocked(slot.getItem())), "No locked ingredients enter the grid");
            test.succeed();
        }
    }

    @GameTest
    public void recipePacketsRespectLocksAndCreativeModeChanges(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            var inventory = player.getInventory();
            var recipe = planksRecipe(player);
            var locked = locked(Items.OAK_LOG, 3);
            inventory.setItem(0, locked.copy());
            placeRecipe(player, recipe);
            same(test, inventory.getItem(0), locked, "The recipe packet cannot consume locked-only materials");
            test.assertValueEqual(inputCount(player), 0, "Locked-only materials do not fill the grid");

            inventory.setItem(1, new ItemStack(Items.OAK_LOG, 2));
            placeRecipe(player, recipe);
            same(test, inventory.getItem(0), locked, "The packet skips an earlier locked material stack");
            test.assertTrue(inventory.getItem(1).isEmpty(), "The packet uses the later ordinary material stack");
            test.assertValueEqual(inputCount(player), 2, "The normal request fills the actual inventory menu");

            clearGrid(player);
            inventory.clearContent();
            inventory.setItem(0, locked.copy());
            setGameMode(test, player, GameType.CREATIVE);
            var contents = new StackedItemContents();
            inventory.fillStackedContents(contents);
            test.assertValueEqual(contents.getBiggestCraftableStack(recipe.value(), null), 3,
                    "Creative materials are included in recipe counting");
            placeRecipe(player, recipe);
            test.assertValueEqual(inputCount(player), 3, "Creative recipe placement can move all tagged ingredients");
            test.assertTrue(inventory.getItem(0).isEmpty(), "Creative placement retains vanilla transfer semantics");

            clearGrid(player);
            inventory.setItem(0, locked.copy());
            setGameMode(test, player, GameType.ADVENTURE);
            placeRecipe(player, recipe);
            same(test, inventory.getItem(0), locked, "Switching out of creative re-enables protection immediately");
            test.assertValueEqual(inputCount(player), 0, "Adventure also excludes locked recipe materials");
            test.succeed();
        }
    }

    @GameTest
    public void tradeFillingSkipsEarlierLockedCandidatesAndAllowsCreative(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            var inventory = player.getInventory();
            var menu = openMerchant(player, false);
            var locked = locked(Items.EMERALD, 4);
            // MerchantMenu scans inventory slots 9..35 before the hotbar.
            inventory.setItem(9, locked.copy());
            inventory.setItem(10, new ItemStack(Items.EMERALD, 3));
            player.connection.handleSelectTrade(new ServerboundSelectTradePacket(0));
            same(test, inventory.getItem(9), locked, "Trade selection does not shrink the locked first candidate");
            test.assertTrue(inventory.getItem(10).isEmpty(), "Trade selection keeps searching for ordinary payment");
            same(test, menu.getSlot(0).getItem(), new ItemStack(Items.EMERALD, 3), "Ordinary payment reaches the real menu");
            test.assertTrue(menu.getSlot(2).getItem().is(Items.APPLE), "The ordinary trade is ready to complete");

            menu.getSlot(0).set(ItemStack.EMPTY);
            inventory.clearContent();
            inventory.setItem(9, locked.copy());
            setGameMode(test, player, GameType.CREATIVE);
            menu.tryMoveItems(0);
            same(test, menu.getSlot(0).getItem(), locked, "Creative auto-filling can use a tagged payment");
            test.assertTrue(inventory.getItem(9).isEmpty(), "Creative keeps the vanilla transfer, not a copy");
            test.succeed();
        }
    }

    @GameTest
    public void lockedSecondPaymentAtomicallyRejectsMenuAndPacketTransfers(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            var menu = openMerchant(player, true);
            menu.setSelectionHint(0);
            menu.getSlot(0).set(new ItemStack(Items.EMERALD, 2));
            menu.getSlot(1).set(locked(Items.DIAMOND, 1));
            test.assertTrue(menu.getSlot(2).getItem().is(Items.APPLE), "The original offer is selected");
            var before = snapshot(menu);
            menu.tryMoveItems(1);
            unchanged(test, menu, before, "Direct transfer checks payment two before returning payment one");

            fixture.connection().sent.clear();
            player.connection.handleSelectTrade(new ServerboundSelectTradePacket(1));
            unchanged(test, menu, before, "Rejected trade packet preserves both payments, inventory and selected result");
            test.assertTrue(fixture.connection().sent.stream().anyMatch(packet ->
                            packet instanceof ClientboundContainerSetContentPacket content
                                    && content.containerId() == menu.containerId
                                    && ItemStack.listMatches(content.items(), before)),
                    "The rejected packet forces a complete authoritative menu correction");

            menu.getSlot(1).getItem().remove(DataComponents.CUSTOM_DATA);
            player.connection.handleSelectTrade(new ServerboundSelectTradePacket(1));
            test.assertTrue(menu.getSlot(2).getItem().is(Items.BREAD), "Removing the tag allows ordinary selection and filling");

            menu.getSlot(0).set(new ItemStack(Items.EMERALD, 2));
            menu.getSlot(1).set(locked(Items.DIAMOND, 1));
            menu.setSelectionHint(0);
            setGameMode(test, player, GameType.CREATIVE);
            player.connection.handleSelectTrade(new ServerboundSelectTradePacket(1));
            test.assertTrue(menu.getSlot(2).getItem().is(Items.BREAD), "Creative selection can return a tagged second payment");
            test.succeed();
        }
    }

    @GameTest
    public void usePacketsProtectBothEquipmentEndsAndBothHands(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            for (var hand : InteractionHand.values()) {
                for (boolean lockSource : new boolean[]{true, false}) {
                    player.getInventory().clearContent();
                    var held = lockSource ? locked(Items.IRON_HELMET, 1) : new ItemStack(Items.IRON_HELMET);
                    var worn = lockSource ? new ItemStack(Items.DIAMOND_HELMET) : locked(Items.DIAMOND_HELMET, 1);
                    player.setItemInHand(hand, held.copy());
                    player.setItemSlot(EquipmentSlot.HEAD, worn.copy());
                    fixture.connection().sent.clear();
                    use(player, hand);
                    same(test, player.getItemInHand(hand), held, "Rejected equipment use preserves the source hand");
                    same(test, player.getItemBySlot(EquipmentSlot.HEAD), worn, "Rejected equipment use preserves the destination");
                    assertInventoryCorrection(test, fixture, hand == InteractionHand.MAIN_HAND
                            ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND, held);
                    assertInventoryCorrection(test, fixture, equipmentIndex(EquipmentSlot.HEAD), worn);
                }
            }

            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            use(player, InteractionHand.MAIN_HAND);
            same(test, player.getItemBySlot(EquipmentSlot.HEAD), new ItemStack(Items.IRON_HELMET), "Ordinary armor equips normally");
            same(test, player.getMainHandItem(), new ItemStack(Items.DIAMOND_HELMET), "Ordinary replaced armor returns to the hand");

            setGameMode(test, player, GameType.CREATIVE);
            var held = locked(Items.IRON_HELMET, 1);
            var worn = locked(Items.DIAMOND_HELMET, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
            player.setItemSlot(EquipmentSlot.HEAD, worn.copy());
            use(player, InteractionHand.MAIN_HAND);
            same(test, player.getItemBySlot(EquipmentSlot.HEAD), held, "Creative can equip a tagged source over a tagged target");
            same(test, player.getMainHandItem(), worn, "Creative retains vanilla replacement behavior");
            test.succeed();
        }
    }

    @GameTest
    public void entityEquipmentPacketsProtectSourceAndResynchronizeTarget(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            var target = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
            target.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            var held = interactableHelmet(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
            fixture.connection().sent.clear();
            interact(player, target);
            same(test, player.getMainHandItem(), held, "Entity equipment cannot split a locked hand stack");
            test.assertTrue(target.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "The target remains unequipped");
            assertInventoryCorrection(test, fixture, player.getInventory().getSelectedSlot(), held);
            assertEquipmentCorrection(test, fixture, target, EquipmentSlot.HEAD, ItemStack.EMPTY);

            var ordinary = interactableHelmet(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ordinary.copy());
            interact(player, target);
            same(test, target.getItemBySlot(EquipmentSlot.HEAD), ordinary, "Ordinary entity equipment still works");
            test.assertTrue(player.getMainHandItem().isEmpty(), "Survival consumes the ordinary equipment source");

            target.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            setGameMode(test, player, GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
            interact(player, target);
            same(test, target.getItemBySlot(EquipmentSlot.HEAD), held, "Creative can equip a target with a tagged item");
            test.succeed();
        }
    }

    @GameTest
    public void ineligibleEquipmentKeepsVanillaPass(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            var target = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
            var held = interactableHelmet(true);
            held.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                    .setAllowedEntities(EntityTypes.WOLF).setEquipOnInteract(true).build());
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            fixture.connection().sent.clear();
            test.assertTrue(held.interactLivingEntity(player, target, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                    "An incompatible target still returns PASS before lock checks");
            test.assertTrue(held.get(DataComponents.EQUIPPABLE).swapWithEquipmentSlot(held, player) == InteractionResult.PASS,
                    "An incompatible self-equipment slot still returns PASS");

            held = interactableHelmet(true);
            target.setItemSlot(EquipmentSlot.HEAD, locked(Items.DIAMOND_HELMET, 1));
            test.assertTrue(held.interactLivingEntity(player, target, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                    "Equip-on-target keeps vanilla PASS for an already occupied target");
            test.assertTrue(fixture.connection().sent.isEmpty(), "Ineligible equipment does not send unnecessary corrections");
            test.succeed();
        }
    }

    @GameTest
    public void armorStandPacketsProtectBothEndsAndAllowCreative(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            var stand = test.spawn(EntityTypes.ARMOR_STAND, 2, 2, 2);
            for (boolean lockSource : new boolean[]{true, false}) {
                var held = lockSource ? locked(Items.IRON_HELMET, 1) : new ItemStack(Items.IRON_HELMET);
                var worn = lockSource ? new ItemStack(Items.DIAMOND_HELMET) : locked(Items.DIAMOND_HELMET, 1);
                player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
                stand.setItemSlot(EquipmentSlot.HEAD, worn.copy());
                fixture.connection().sent.clear();
                interact(player, stand);
                same(test, player.getMainHandItem(), held, "The stand cannot move a protected hand stack");
                same(test, stand.getItemBySlot(EquipmentSlot.HEAD), worn, "The stand cannot replace protected equipment");
                assertInventoryCorrection(test, fixture, player.getInventory().getSelectedSlot(), held);
                assertEquipmentCorrection(test, fixture, stand, EquipmentSlot.HEAD, worn);
            }

            var worn = locked(Items.DIAMOND_HELMET, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            stand.setItemSlot(EquipmentSlot.HEAD, worn.copy());
            interact(player, stand);
            test.assertTrue(player.getMainHandItem().isEmpty(), "Empty-hand pickup cannot take locked stand equipment");
            same(test, stand.getItemBySlot(EquipmentSlot.HEAD), worn, "Locked stand equipment stays in its slot");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET));
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            interact(player, stand);
            test.assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "Ordinary stand equipment can swap");
            test.assertTrue(player.getMainHandItem().is(Items.DIAMOND_HELMET), "The old ordinary equipment returns to the player");

            setGameMode(test, player, GameType.CREATIVE);
            var held = locked(Items.IRON_HELMET, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
            stand.setItemSlot(EquipmentSlot.HEAD, worn.copy());
            interact(player, stand);
            same(test, stand.getItemBySlot(EquipmentSlot.HEAD), held, "Creative stand swaps permit both tagged ends");
            same(test, player.getMainHandItem(), worn, "Creative stand swap preserves the previous equipment");
            test.succeed();
        }
    }

    @GameTest
    public void normalLockedItemUseAndFrameDeliveryRemainAllowed(GameTestHelper test) {
        try (var fixture = fixture(test)) {
            var player = fixture.player();
            player.getFoodData().setFoodLevel(10);
            player.setItemInHand(InteractionHand.MAIN_HAND, locked(Items.BREAD, 1));
            use(player, InteractionHand.MAIN_HAND);
            test.assertTrue(player.isUsingItem(), "A locked edible still starts its vanilla use action");
            player.stopUsingItem();

            var frame = test.spawn(EntityTypes.ITEM_FRAME, 2, 2, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, locked(Items.PAPER, 2));
            interact(player, frame);
            same(test, frame.getItem(), locked(Items.PAPER, 1), "Item-frame delivery remains an allowed ordinary use");
            same(test, player.getMainHandItem(), locked(Items.PAPER, 1), "The ordinary use consumes exactly one item");
            test.succeed();
        }
    }

    private static Fixture fixture(GameTestHelper test) {
        var server = test.getLevel().getServer();
        // makeMockServerPlayer captures its initial GameType in a gameMode() override.
        // Keep the real dynamic gameMode() for tests that switch the same player's mode.
        var player = new ServerPlayer(server, test.getLevel(),
                new GameProfile(UUID.randomUUID(), "item-lock-player"), ClientInformation.createDefault()) {
            @Override public boolean isClientAuthoritative() { return false; }
        };
        var connection = new RecordingConnection();
        new ServerGamePacketListenerImpl(server, connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        setGameMode(test, player, GameType.SURVIVAL);
        player.initInventoryMenu();
        RuneLegendServer.find(server).stamina().join(player);
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.setPos(test.absoluteVec(new Vec3(2.5, 2, 1.5)));
        connection.sent.clear();
        return new Fixture(player, connection);
    }

    private static void setGameMode(GameTestHelper test, ServerPlayer player, GameType mode) {
        player.setGameMode(mode);
        test.assertValueEqual(player.gameMode(), mode, "The fixture exposes the current server game mode");
        test.assertTrue(player.isCreative() == (mode == GameType.CREATIVE),
                "Creative status follows the current mode rather than the fixture's initial mode");
    }

    private static ItemStack locked(Item item, int count) {
        var marker = new CompoundTag();
        marker.putBoolean("runelegend:item_lock", true);
        var stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return stack;
    }

    private static ItemStack interactableHelmet(boolean locked) {
        var stack = locked ? locked(Items.IRON_HELMET, 1) : new ItemStack(Items.IRON_HELMET);
        stack.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setEquipOnInteract(true).build());
        return stack;
    }

    private static RecipeHolder<?> planksRecipe(ServerPlayer player) {
        return player.level().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.withDefaultNamespace("oak_planks"))).orElseThrow();
    }

    private static void placeRecipe(ServerPlayer player, RecipeHolder<?> recipe) {
        player.getRecipeBook().add(recipe.id());
        var displays = new ArrayList<RecipeDisplayEntry>();
        player.level().getServer().getRecipeManager().listDisplaysForRecipe(recipe.id(), displays::add);
        player.connection.handlePlaceRecipe(new ServerboundPlaceRecipePacket(player.containerMenu.containerId,
                displays.getFirst().id(), true));
    }

    private static int inputCount(ServerPlayer player) {
        return player.inventoryMenu.getInputGridSlots().stream().mapToInt(slot -> slot.getItem().getCount()).sum();
    }

    private static void clearGrid(ServerPlayer player) {
        for (var slot : player.inventoryMenu.getInputGridSlots()) slot.set(ItemStack.EMPTY);
    }

    private static MerchantMenu openMerchant(ServerPlayer player, boolean twoPayments) {
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new MerchantMenu(id, inventory),
                Component.literal("Item Lock transfer test")));
        var menu = (MerchantMenu) player.containerMenu;
        var offers = new MerchantOffers();
        var second = twoPayments ? Optional.of(new ItemCost(Items.DIAMOND)) : Optional.<ItemCost>empty();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), second, new ItemStack(Items.APPLE), 64, 0, 0));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), second, new ItemStack(Items.BREAD), 64, 0, 0));
        menu.setOffers(offers);
        return menu;
    }

    private static void use(ServerPlayer player, InteractionHand hand) {
        player.connection.handleUseItem(new ServerboundUseItemPacket(hand, 0, player.getYRot(), player.getXRot()));
    }

    private static void interact(ServerPlayer player, Entity target) {
        player.connection.handleInteract(new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND,
                new Vec3(0, 1.75, 0), false));
    }

    private static List<ItemStack> snapshot(AbstractContainerMenu menu) {
        return menu.slots.stream().map(Slot::getItem).map(ItemStack::copy).toList();
    }

    private static void unchanged(GameTestHelper test, AbstractContainerMenu menu, List<ItemStack> before, String message) {
        test.assertTrue(ItemStack.listMatches(snapshot(menu), before) && menu.getCarried().isEmpty(), message);
    }

    private static void same(GameTestHelper test, ItemStack actual, ItemStack expected, String message) {
        test.assertTrue(ItemStack.matches(actual, expected), message);
    }

    private static int equipmentIndex(EquipmentSlot slot) {
        return Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet().stream()
                .filter(entry -> entry.getValue() == slot).findFirst().orElseThrow().getIntKey();
    }

    private static void assertInventoryCorrection(GameTestHelper test, Fixture fixture, int slot, ItemStack expected) {
        test.assertTrue(fixture.connection().sent.stream().anyMatch(packet ->
                        packet instanceof ClientboundSetPlayerInventoryPacket update
                                && update.slot() == slot && ItemStack.matches(update.contents(), expected)),
                "A forced vanilla inventory packet restores the authoritative stack in slot " + slot);
    }

    private static void assertEquipmentCorrection(GameTestHelper test, Fixture fixture, Entity target,
                                                  EquipmentSlot slot, ItemStack expected) {
        test.assertTrue(fixture.connection().sent.stream().anyMatch(packet ->
                        packet instanceof ClientboundSetEquipmentPacket update && update.getEntity() == target.getId()
                                && update.getSlots().stream().anyMatch(entry -> entry.getFirst() == slot
                                && ItemStack.matches(entry.getSecond(), expected))),
                "A forced vanilla entity equipment packet restores the unchanged target");
    }

    private record Fixture(ServerPlayer player, RecordingConnection connection) implements AutoCloseable {
        @Override public void close() {
            player.doCloseContainer();
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    private static final class RecordingConnection extends Connection {
        private final List<Packet<?>> sent = new ArrayList<>();

        private RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
            sent.add(packet);
        }
    }
}
