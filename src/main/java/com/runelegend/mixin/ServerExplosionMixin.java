package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.token.TokenEventBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
    @WrapOperation(method = "hurtEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean runelegend$exposeCenter(Entity victim, ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        return TokenEventBridge.explosion(victim, source, ((ServerExplosion) (Object) this).center(),
                () -> original.call(victim, level, source, amount));
    }
}
