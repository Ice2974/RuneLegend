package com.runelegend.client.hud;

record HudLayout(int center, int first, int top, int stride, int staminaTop) {
    static final int SIZE = 16;

    static HudLayout place(int width, int height, int slots, int verticalOffset) {
        // 26.3 anchors the vanilla overlay at height - 68; its text starts four pixels above that.
        int center = width / 2;
        int stride = width < 100 ? SIZE + 2 : SIZE + 6;
        int rowWidth = slots == 0 ? 0 : SIZE + (slots - 1) * stride;
        int top = Math.max(14, height - 95 - Math.clamp(verticalOffset, 0, 100));
        return new HudLayout(center, center - rowWidth / 2, top, stride, slots == 0 ? top + 3 : top - 12);
    }
}
