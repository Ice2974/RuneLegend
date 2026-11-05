package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.RuneLegendServer;
import com.runelegend.combat.GuardDirectionResolver;
import com.runelegend.combat.GuardPolicy;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenEventBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingEntityDamageMixin {
    @WrapMethod(method = "hurtServer")
    private boolean runelegend$trackAcceptedHit(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        var server = RuneLegendServer.find(level.getServer());
        if (server == null || !server.game().getRules().running()) return original.call(level, source, amount);
        return TokenEventBridge.trackHit(server, (LivingEntity) (Object) this, level, source, amount,
                () -> original.call(level, source, amount));
    }

    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"), require = 2)
    private void runelegend$guard(LivingEntity victim, ServerLevel level, DamageSource source, float amount, Operation<Void> original) {
        float effective = amount;
        if (victim instanceof ServerPlayer player && amount > 0 && GuardPolicy.guardable(source)
                && !source.is(TokenEngine.TOKEN_DAMAGE)) {
            var server = RuneLegendServer.find(level.getServer());
            if (server != null && server.guards().active(player)
                    && GuardDirectionResolver.canBlock(player, source, TokenEventBridge.explosionCenter(player, source))) {
                effective = amount * GuardPolicy.DAMAGE_MULTIPLIER;
            }
        }
        original.call(victim, level, source, effective);
        TokenEventBridge.applied(victim, source, effective, amount - effective);
    }
}
