/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Whether a player is on Bedrock (through Geyser and Floodgate). Bedrock has no left/right/shift click in menus
 * (a tap is just a click), so anything that depends on the kind of click gets a menu of choices for them instead.
 * Asks Floodgate when it is installed; otherwise a Floodgate UUID (its upper half is zero) or the name prefix gives it away.
 */
public final class Bedrock {

    private static Object api;
    private static Method isFloodgate;
    private static boolean looked;

    private Bedrock() {}

    public static boolean is(Player player) {
        if (player == null) return false;
        UUID id = player.getUniqueId();
        if (!looked) {
            looked = true;
            try {
                Class<?> type = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                api = type.getMethod("getInstance").invoke(null);
                isFloodgate = type.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                api = null;
            }
        }
        if (api != null) {
            try {
                return (Boolean) isFloodgate.invoke(api, id);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // fall through to the signs below
            }
        }
        return id.getMostSignificantBits() == 0 || player.getName().startsWith(".");
    }
}
