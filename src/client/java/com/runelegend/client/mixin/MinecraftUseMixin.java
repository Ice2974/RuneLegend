package com.runelegend.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.runelegend.client.input.GuardInputController;
import com.runelegend.client.network.ClientNetworking;
import com.runelegend.combat.GuardItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
abstract class MinecraftUseMixin {
    @WrapOperation(method = "startUseItem()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"), allow = 1)
    private InteractionResult runelegend$oneGuardAttempt(MultiPlayerGameMode mode, Player player, InteractionHand hand,
                                                       Operation<InteractionResult> original,
                                                       @Share("runelegend$allowed") LocalBooleanRef allowed) {
        if (ClientNetworking.active() && GuardItems.canBlock(player.getItemInHand(hand)) && !allowed.get()) {
            if (!GuardInputController.INSTANCE.allowAttempt((Minecraft) (Object) this)) return InteractionResult.FAIL;
            // Both hands may be tried by vanilla within this one attempt.
            allowed.set(true);
        }
        return original.call(mode, player, hand);
    }
}
