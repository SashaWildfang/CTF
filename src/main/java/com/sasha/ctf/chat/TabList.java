/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Extra lines in the tab list that aren't players: team headings and gaps, so the tab list reads like a
 * scoreboard of teams. Paper has no API for these, so they're sent as player-info packets through the
 * server's own classes; if that ever fails (a big Minecraft update), the headings are just left out.
 */
final class TabList {

    /** One fake line: its id, its place (higher is nearer the top) and its text. */
    record Line(UUID id, int order, Component text) {}

    private final Logger logger;
    private boolean broken;
    private Constructor<?> entryConstructor, updateConstructor, removeConstructor, profileConstructor;
    private Class<?> actionClass;
    private Object survival;
    private Method asVanilla;

    TabList(Logger logger) {
        this.logger = logger;
        try {
            Class<?> packet = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> entry = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> profile = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> gameType = Class.forName("net.minecraft.world.level.GameType");
            Class<?> remove = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            for (Constructor<?> c : entry.getDeclaredConstructors()) if (c.getParameterCount() > 5) entryConstructor = c;
            updateConstructor = packet.getConstructor(EnumSet.class, List.class);
            removeConstructor = remove.getConstructor(List.class);
            profileConstructor = profile.getConstructor(UUID.class, String.class);
            survival = gameType.getField("SURVIVAL").get(null);
            asVanilla = Class.forName("io.papermc.paper.adventure.PaperAdventure").getMethod("asVanilla", Component.class);
            if (entryConstructor == null) throw new IllegalStateException("no entry constructor");
        } catch (Throwable e) {
            broken = true;
            logger.info("Tab list team headings are off on this server version (" + e.getClass().getSimpleName() + ").");
        }
    }

    boolean works() {
        return !broken;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private EnumSet actions(String... names) {
        EnumSet set = EnumSet.noneOf((Class<Enum>) actionClass);
        for (String name : names) set.add(Enum.valueOf((Class<Enum>) actionClass, name));
        return set;
    }

    private Object entry(Line line) throws ReflectiveOperationException {
        String name = " " + Integer.toHexString(line.order() & 0xFFFFFF);
        Object profile = profileConstructor.newInstance(line.id(), name.length() > 16 ? name.substring(0, 16) : name);
        Object[] args = new Object[entryConstructor.getParameterCount()];
        Class<?>[] types = entryConstructor.getParameterTypes();
        // (UUID id, GameProfile profile, boolean listed, int latency, GameType mode, Component name, boolean hat, int order, chat)
        int ints = 0, bools = 0;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == UUID.class) args[i] = line.id();
            else if (type.getSimpleName().equals("GameProfile")) args[i] = profile;
            else if (type == boolean.class) args[i] = bools++ == 0; // listed: yes; hat: no
            else if (type == int.class) args[i] = ints++ == 0 ? 0 : line.order(); // latency, then list order
            else if (type.isInstance(survival)) args[i] = survival;
            else if (type.getSimpleName().equals("Component")) args[i] = asVanilla.invoke(null, line.text());
            else args[i] = null;
        }
        return entryConstructor.newInstance(args);
    }

    /** Adds the lines for a player (or updates their text and place if they're already there). */
    void show(Player player, List<Line> lines) {
        if (broken || lines.isEmpty()) return;
        try {
            List<Object> entries = new ArrayList<>();
            for (Line line : lines) entries.add(entry(line));
            send(player, updateConstructor.newInstance(actions("ADD_PLAYER", "UPDATE_LISTED", "UPDATE_LATENCY", "UPDATE_GAME_MODE",
                    "UPDATE_DISPLAY_NAME", "UPDATE_LIST_ORDER"), entries));
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** Only the text and place, for lines the player already has. */
    void update(Player player, List<Line> lines) {
        if (broken || lines.isEmpty()) return;
        try {
            List<Object> entries = new ArrayList<>();
            for (Line line : lines) entries.add(entry(line));
            send(player, updateConstructor.newInstance(actions("UPDATE_DISPLAY_NAME", "UPDATE_LIST_ORDER"), entries));
        } catch (Throwable e) {
            fail(e);
        }
    }

    void hide(Player player, List<UUID> ids) {
        if (broken || ids.isEmpty()) return;
        try {
            send(player, removeConstructor.newInstance(new ArrayList<>(ids)));
        } catch (Throwable e) {
            fail(e);
        }
    }

    private void send(Player player, Object packet) throws ReflectiveOperationException {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object connection = handle.getClass().getField("connection").get(handle);
        for (Method method : connection.getClass().getMethods()) {
            if (method.getName().equals("send") && method.getParameterCount() == 1) {
                method.invoke(connection, packet);
                return;
            }
        }
        throw new NoSuchMethodException("send");
    }

    private void fail(Throwable e) {
        if (broken) return;
        broken = true;
        logger.warning("Tab list team headings turned off: " + e);
    }
}
