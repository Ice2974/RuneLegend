package com.runelegend.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.combat.GuardPolicy;
import com.runelegend.combat.StaminaManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.sdl.SDLMouse;

import static com.runelegend.network.RuneLegendPayloads.*;

public final class GuardInputController {
    public static final GuardInputController INSTANCE = new GuardInputController();
    private final ClientStateCache cache = ClientStateCache.INSTANCE;
    private long input;
    private long started = -1;
    private long predictUntil;
    private long resyncAt;
    private long swallowThrough = -1;
    private boolean claimed;
    private boolean awaitingRelease;
    private int slot;
    private ItemStack weapon = ItemStack.EMPTY;
    private Object level;
    private InputConstants.Key boundKey;

    private GuardInputController() {}

    public void key(Minecraft client, KeyMapping mapping, InputConstants.Key key, boolean down) {
        if (client == null || client.options == null || mapping != client.options.keyUse) return;
        boundKey = key;
        if (down) {
            if (claimed || awaitingRelease) return;
            swallowThrough = -1;
            if (!eligible(client) || !ClientPlayNetworking.canSend(GuardStart.TYPE)) return;
            claimed = true;
            slot = client.player.getInventory().getSelectedSlot();
            weapon = client.player.getMainHandItem();
            level = client.level;
            started = ++input;
            predictUntil = cache.tick() + 12;
            ClientPlayNetworking.send(new GuardStart(started, cache.rules().revision(), slot));
        } else if (claimed || awaitingRelease) {
            if (inGame(client) && !physicallyDown(client)) release();
            else cancel();
        }
    }

    public void tick(Minecraft client) {
        cache.tick(client.level);
        if (cache.desynced() && cache.tick() >= resyncAt && ClientPlayNetworking.canSend(StateResync.TYPE)) {
            resyncAt = cache.tick() + 20;
            ClientPlayNetworking.send(new StateResync());
        }
        if (claimed && !stillHoldingWeapon(client)) cancel();
        if (awaitingRelease && inGame(client) && !physicallyDown(client)) release();
        if (pose(client)) client.player.setSprinting(false);
    }

    public boolean suppressUse(Minecraft client) {
        return inGame(client) && (claimed || cache.tick() <= swallowThrough);
    }

    public boolean pose(Minecraft client) {
        if (!claimed || !stillHoldingWeapon(client) || cache.rules() == null || !cache.rules().canGuard()) return false;
        var state = cache.view();
        return state != null && (state.input() >= started ? state.guarding() : cache.tick() < predictUntil);
    }

    private boolean eligible(Minecraft client) {
        if (!inGame(client) || client.player == null || !client.player.isAlive() || client.player.isSpectator()
                || client.player.isPassenger() || client.player.isSleeping() || client.player.isUsingItem()) return false;
        var rules = cache.rules();
        var state = cache.view();
        return rules != null && rules.canGuard() && state != null
                && state.stamina() >= StaminaManager.MIN_TO_START && GuardPolicy.sword(client.player.getMainHandItem());
    }

    private boolean stillHoldingWeapon(Minecraft client) {
        return inGame(client) && client.player != null && client.player.isAlive() && client.level == level
                && client.player.getInventory().getSelectedSlot() == slot && client.player.getMainHandItem() == weapon;
    }

    private boolean inGame(Minecraft client) {
        return client.isWindowActive() && client.gui.screen() == null && client.gui.overlay() == null;
    }

    private boolean physicallyDown(Minecraft client) {
        if (boundKey != null && boundKey.getType() == InputConstants.Type.MOUSE) {
            return boundKey.getValue() > 0 && (SDLMouse.SDL_GetMouseState(null, null) & (1 << (boundKey.getValue() - 1))) != 0;
        }
        if (boundKey != null && boundKey.getType() == InputConstants.Type.KEYBOARD) {
            return InputConstants.isKeyDown(boundKey.getValue());
        }
        return client.options.keyUse.isDown();
    }

    private void cancel() {
        if (!claimed) return;
        claimed = false;
        awaitingRelease = true;
        weapon = ItemStack.EMPTY;
        sendEnd(false);
    }

    private void release() {
        claimed = false;
        awaitingRelease = false;
        weapon = ItemStack.EMPTY;
        swallowThrough = cache.tick();
        sendEnd(true);
    }

    private void sendEnd(boolean released) {
        if (ClientPlayNetworking.canSend(GuardEnd.TYPE)) ClientPlayNetworking.send(new GuardEnd(++input, released));
    }

    public void clear() {
        input = 0;
        started = -1;
        claimed = false;
        awaitingRelease = false;
        swallowThrough = -1;
        resyncAt = 0;
        weapon = ItemStack.EMPTY;
        level = null;
        boundKey = null;
    }
}
