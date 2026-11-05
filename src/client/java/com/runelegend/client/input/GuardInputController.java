package com.runelegend.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.runelegend.client.network.ClientNetworking;
import com.runelegend.combat.GuardItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;

/** Prevents vanilla's held-use retry from acting as a new press after a guard attempt. */
public final class GuardInputController {
    public static final GuardInputController INSTANCE = new GuardInputController();
    private InputConstants.Key binding;
    private boolean attempted;
    private boolean releasePending;

    private GuardInputController() {}

    public boolean allowAttempt(Minecraft client) {
        if (!client.options.keyUse.isDown() || attempted) return false;
        if (releasePending) {
            sendRelease(client);
            releasePending = false;
        }
        attempted = true;
        return true;
    }

    public void key(Minecraft client, KeyMapping mapping, InputConstants.Key key, boolean down) {
        if (client == null || client.options == null || mapping != client.options.keyUse) return;
        if (down && !attempted) binding = key;
        if (!down && attempted && acceptsRelease(client) && !bindingStillHeld()) observeRelease(client);
    }

    public void tick(Minecraft client) {
        if (attempted && !client.options.keyUse.isDown() && acceptsRelease(client) && !bindingStillHeld()) {
            observeRelease(client);
        }
    }

    private void observeRelease(Minecraft client) {
        attempted = false;
        // A release that arrives during world transfer may be ignored. Repeat it before the
        // next vanilla use request; both actions are ordered and release is idempotent.
        releasePending = true;
        sendRelease(client);
    }

    private static void sendRelease(Minecraft client) {
        if (client.player != null && client.gameMode != null && ClientNetworking.active()
                && (!client.player.isUsingItem() || GuardItems.using(client.player))) {
            client.gameMode.releaseUsingItem(client.player);
        }
    }

    private static boolean acceptsRelease(Minecraft client) {
        return client.isWindowActive() && client.gui.screen() == null && client.gui.overlay() == null;
    }

    private boolean bindingStillHeld() {
        if (binding == null) return false;
        if (binding.getType() == InputConstants.Type.MOUSE) {
            int button = binding.getValue();
            return button > 0 && (SDLMouse.SDL_GetMouseState(null, null) & (1 << (button - 1))) != 0;
        }
        if (binding.getType() == InputConstants.Type.KEYBOARD) return InputConstants.isKeyDown(binding.getValue());
        return false;
    }

    public void clear() {
        attempted = false;
        releasePending = false;
        binding = null;
    }
}
