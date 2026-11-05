package com.runelegend.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.runelegend.client.render.GuardPoseController;
import com.runelegend.combat.GuardPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonGuardMixin {
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void runelegend$guardPose(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState hands,
                                      float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
                                      float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (hand != InteractionHand.MAIN_HAND || player == null || !GuardPolicy.sword(stack) || !GuardPoseController.guarding(player)) return;
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(-0.31F * side, 0.19F, -0.04F);
        pose.rotateDegrees(Axis.YP, 28F * side);
        pose.rotateDegrees(Axis.ZP, -57F * side);
        pose.rotateDegrees(Axis.XP, -24F);
    }
}
