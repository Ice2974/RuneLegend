package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.RuneLegendServer;
import com.runelegend.token.TokenEventBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class PlayerAttackMixin {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean runelegend$primaryMelee(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerPlayer attacker) || !(target instanceof LivingEntity)) {
            return original.call(target, source, amount);
        }
        var server = RuneLegendServer.find(attacker.level().getServer());
        if (server == null || !server.game().getRules().running()) return original.call(target, source, amount);
        if (server.guards().active(attacker)) return false;
        var boost = server.tokens().melee(attacker, amount);
        boolean result = TokenEventBridge.melee(attacker, target, source, () -> original.call(target, source, boost.damage()));
        if (result) server.tokens().confirmBoost(attacker, boost.applied());
        return result;
    }
}
