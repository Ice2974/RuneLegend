package com.runelegend.client.mixin;

import com.runelegend.client.render.GuardPoseController;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class AvatarGuardPoseMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void runelegend$thirdPersonGuard(Avatar player, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (!GuardPoseController.guarding(player)) return;
        if (player.getMainArm() == HumanoidArm.RIGHT) state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        else state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
    }
}
