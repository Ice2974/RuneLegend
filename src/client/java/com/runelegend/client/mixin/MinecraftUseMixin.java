package com.runelegend.client.mixin;

import com.runelegend.client.input.GuardInputController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftUseMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void runelegend$guardOwnsUse(CallbackInfo ci) {
        if (GuardInputController.INSTANCE.suppressUse((Minecraft) (Object) this)) ci.cancel();
    }
}
