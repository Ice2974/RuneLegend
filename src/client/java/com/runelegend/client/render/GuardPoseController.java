package com.runelegend.client.render;

import com.runelegend.client.input.GuardInputController;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.combat.GuardPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

public final class GuardPoseController {
    private GuardPoseController() {}

    public static boolean guarding(Avatar avatar) {
        if (!(avatar instanceof Player) || !GuardPolicy.sword(avatar.getMainHandItem())) return false;
        var client = Minecraft.getInstance();
        return avatar == client.player ? GuardInputController.INSTANCE.pose(client) : ClientStateCache.INSTANCE.pose(avatar);
    }
}
