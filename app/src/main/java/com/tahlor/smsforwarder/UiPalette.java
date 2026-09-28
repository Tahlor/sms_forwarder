package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.res.Configuration;

final class UiPalette {
    final boolean dark;
    final int background;
    final int surface;
    final int text;
    final int muted;
    final int border;
    final int accent;
    final int accentSoft;
    final int success;
    final int danger;
    final int dangerSoft;

    private UiPalette(boolean dark, int background, int surface, int text, int muted,
                      int border, int accent, int accentSoft, int success,
                      int danger, int dangerSoft) {
        this.dark = dark;
        this.background = background;
        this.surface = surface;
        this.text = text;
        this.muted = muted;
        this.border = border;
        this.accent = accent;
        this.accentSoft = accentSoft;
        this.success = success;
        this.danger = danger;
        this.dangerSoft = dangerSoft;
    }

    static UiPalette from(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean dark = nightMode == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            return new UiPalette(
                    true,
                    0xFF101114,
                    0xFF1A1C20,
                    0xFFF4F4F6,
                    0xFFB5B7BF,
                    0xFF343740,
                    0xFF8EABFF,
                    0xFF202A46,
                    0xFF68D391,
                    0xFFFF8A80,
                    0xFF3A2221);
        }
        return new UiPalette(
                false,
                0xFFF7F7FA,
                0xFFFFFFFF,
                0xFF17171B,
                0xFF6D6D76,
                0xFFE5E5EA,
                0xFF315CF5,
                0xFFEAF0FF,
                0xFF168243,
                0xFFB3261E,
                0xFFFFF0EF);
    }
}
