package com.runelegend.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.runelegend.client.input.GuardInputController;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
abstract class KeyMappingGuardMixin {
    @Shadow protected InputConstants.Key key;
    @Shadow public abstract boolean isDown();

    @Inject(method = "setDown", at = @At("HEAD"))
    private void runelegend$useEdge(boolean down, CallbackInfo ci) {
        if (isDown() != down) GuardInputController.INSTANCE.key(Minecraft.getInstance(), (KeyMapping) (Object) this, key, down);
    }
}
