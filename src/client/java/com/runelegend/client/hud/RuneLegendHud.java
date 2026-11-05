package com.runelegend.client.hud;

import com.runelegend.RuneLegendMod;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.combat.GuardItems;
import com.runelegend.combat.StaminaManager;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.Set;

public final class RuneLegendHud {
    private static final Set<String> ICONS = Set.of("sharpened_tip", "soul_siphon", "echo", "immediate_counter");
    private static final int VERTICAL_OFFSET = Integer.getInteger("runelegend.hudOffset", 0);

    private RuneLegendHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, RuneLegendMod.id("hud"), RuneLegendHud::draw);
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        var client = Minecraft.getInstance();
        var cache = ClientStateCache.INSTANCE;
        var view = cache.view();
        if (view == null || client.player == null || client.gui.hud.isHidden() || client.player.isSpectator()) return;
        boolean showStamina = view.stamina() < StaminaManager.MAX || GuardItems.using(client.player);
        var rules = cache.rules();
        int slots = rules != null && rules.running() ? view.tokens().size() : 0;
        if (slots == 0 && !showStamina) return;
        var layout = HudLayout.place(graphics.guiWidth(), graphics.guiHeight(), slots, VERTICAL_OFFSET);
        for (int i = 0; i < slots; i++) {
            var token = view.tokens().get(i);
            int x = layout.first() + i * layout.stride(), y = layout.top();
            int border = (token.flags() & TokenEngine.TRIGGERED) != 0 ? 0xFFFFCC52
                    : (token.flags() & TokenEngine.ACTIVE) != 0 ? 0xFF59DBCE
                    : (token.flags() & TokenEngine.READY) != 0 ? 0xFF90BF9C : 0xFF5A5661;
            int size = HudLayout.SIZE;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
            graphics.fill(x, y, x + size, y + size, 0xFF231D2D);
            String path = token.id().getPath();
            String icon = token.id().getNamespace().equals(RuneLegendMod.MOD_ID) && ICONS.contains(path) ? path : "unknown";
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RuneLegendMod.id("token/" + icon), x, y, size, size);
            if ((token.flags() & TokenEngine.READY) == 0 && (token.flags() & TokenEngine.ACTIVE) == 0) {
                graphics.fill(x, y, x + size, y + size, 0x65000000);
            }
            if ((token.flags() & TokenEngine.COOLDOWN) != 0) {
                long seconds = Math.max(0, (token.cooldownUntil() - cache.serverTick() + 19) / 20);
                graphics.centeredText(client.font, Long.toString(seconds), x + size / 2, y + 4, 0xFFFFFFFF);
            }
        }
        if (showStamina) {
            int percent = view.stamina() * 100 / StaminaManager.MAX;
            var text = Component.translatable("runelegend.hud.stamina", percent);
            graphics.centeredText(client.font, text, layout.center(), layout.staminaTop(), 0xFFFFFFFF);
        }
    }
}
