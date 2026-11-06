package com.runelegend.test.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.impl.client.gametest.TestInputImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The current test input API constructs SDL input events with a zero modifier mask. */
@Mixin(value = TestInputImpl.class, remap = false)
abstract class ItemLockTestInputMixin {
    @ModifyArg(method = "pressOrReleaseKey(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/platform/InputConstants$Key;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/MouseButtonInfo;<init>(II)V"),
            index = 1, require = 1, allow = 1)
    private static int runelegend$mouseModifiers(int modifiers) {
        return runelegend$heldModifiers(modifiers);
    }

    @ModifyArg(method = "pressOrReleaseKey(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/platform/InputConstants$Key;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/KeyEvent;<init>(III)V"),
            index = 2, require = 1, allow = 1)
    private static int runelegend$keyModifiers(int modifiers) {
        return runelegend$heldModifiers(modifiers);
    }

    @Unique
    private static int runelegend$heldModifiers(int modifiers) {
        // Fabric's InputConstants hook reads the keys held through TestInput, not physical keyboard state.
        if (InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)) {
            modifiers |= InputConstants.MOD_SHIFT;
        }
        if (InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)) {
            modifiers |= InputConstants.MOD_CONTROL;
        }
        return modifiers;
    }
}
