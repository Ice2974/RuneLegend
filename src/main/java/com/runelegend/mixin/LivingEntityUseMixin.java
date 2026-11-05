package com.runelegend.mixin;

import com.runelegend.RuneLegendServer;
import com.runelegend.combat.GuardItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityUseMixin {
    @Shadow @Final protected static EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS;

    @Inject(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;useItem:Lnet/minecraft/world/item/ItemStack;",
            opcode = Opcodes.PUTFIELD), cancellable = true, allow = 1)
    private void runelegend$checkStamina(InteractionHand hand, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        var stack = player.getItemInHand(hand);
        if (!GuardItems.canBlock(stack)) return;
        var server = RuneLegendServer.find(player.level().getServer());
        if (server == null) return;
        if (!server.stamina().tryStart(player)) {
            var data = player.getEntityData();
            data.set(DATA_LIVING_ENTITY_FLAGS, data.get(DATA_LIVING_ENTITY_FLAGS), true);
            ci.cancel();
        } else if (player.isSprinting() && !GuardItems.canSprint(stack)) {
            player.setSprinting(false);
        }
    }

    @Inject(method = "stopUsingItem()V", at = @At("HEAD"))
    private void runelegend$recordUseEnd(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            var server = RuneLegendServer.find(player.level().getServer());
            if (server != null) server.stamina().stopped(player);
        }
    }

    @Inject(method = "updatingUsingItem()V", at = @At("HEAD"))
    private void runelegend$checkStackBeforeVanillaRebind(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && GuardItems.using(player)) {
            var server = RuneLegendServer.find(player.level().getServer());
            if (server != null) server.stamina().validateUse(player);
        }
    }

    @Inject(method = "setSprinting(Z)V", at = @At("HEAD"), cancellable = true)
    private void runelegend$respectUseEffects(boolean sprinting, CallbackInfo ci) {
        if (sprinting && (Object) this instanceof ServerPlayer player
                && GuardItems.using(player) && !GuardItems.canSprint(player.getUseItem())) ci.cancel();
    }
}
