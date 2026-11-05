package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.RuneLegendServer;
import com.runelegend.combat.GuardSourcePosition;
import com.runelegend.token.TokenEventBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityDamageMixin {
    @Inject(method = "applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"))
    private void runelegend$checkCurrentHand(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            var server = RuneLegendServer.find(level.getServer());
            if (server != null) server.stamina().validateUse(player);
        }
    }

    @WrapOperation(method = "applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;getSourcePosition()Lnet/minecraft/world/phys/Vec3;"), allow = 1)
    private Vec3 runelegend$sourcePosition(DamageSource source, Operation<Vec3> original) {
        var position = original.call(source);
        if ((Object) this instanceof ServerPlayer player && player.getUseItem().is(ItemTags.SWORDS)) {
            return GuardSourcePosition.forSword(player, source, position);
        }
        return position;
    }

    @WrapMethod(method = "hurtServer")
    private boolean runelegend$trackAcceptedHit(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        var server = RuneLegendServer.find(level.getServer());
        if (server == null || !server.game().getRules().running()) return original.call(level, source, amount);
        return TokenEventBridge.trackHit(server, (LivingEntity) (Object) this, level, source, amount,
                () -> original.call(level, source, amount));
    }

    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F"), allow = 1)
    private float runelegend$observeBlock(LivingEntity victim, ServerLevel level, DamageSource source, float amount, Operation<Float> original) {
        float blocked = original.call(victim, level, source, amount);
        TokenEventBridge.blocked(victim, source, blocked);
        return blocked;
    }

    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"), require = 2, allow = 2)
    private void runelegend$observeDamage(LivingEntity victim, ServerLevel level, DamageSource source, float amount, Operation<Void> original) {
        original.call(victim, level, source, amount);
        TokenEventBridge.applied(victim, source, amount);
    }
}
