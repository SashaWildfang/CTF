/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import java.util.Locale;

/** Numbers as players read them: 4,084 coins, or 1.2M once they're that big. */
public final class Numbers {

    private Numbers() {}

    public static String coins(long value) {
        long abs = Math.abs(value);
        if (abs < 1_000_000) return String.format(Locale.ROOT, "%,d", value);
        String[] units = {"M", "B", "T"};
        double scaled = value / 1_000_000.0;
        int unit = 0;
        while (Math.abs(scaled) >= 1000 && unit < units.length - 1) {
            scaled /= 1000;
            unit++;
        }
        double floored = Math.floor(scaled * 10) / 10; // 1.29M shows as 1.2M, never rounds up past what they have
        String text = floored == Math.floor(floored) ? String.valueOf((long) floored) : String.format(Locale.ROOT, "%.1f", floored);
        return text + units[unit];
    }
}
