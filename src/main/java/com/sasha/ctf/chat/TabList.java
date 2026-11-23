/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The tab list's made-up rows: headings, info lines and the dark gray empty slots, so the tab list is a full
 * 4 x 20 grid. Paper has no API for these, so they're sent as player-info packets through the server's own
 * classes; if that ever fails (a big Minecraft update), the tab list just shows the real players.
 */
final class TabList {

    /** One made-up row: its id, its place (higher is nearer the top-left), its text and whether it shows. */
    record Slot(UUID id, int order, Component text, boolean listed, String skin) {
        Slot(UUID id, int order, Component text, boolean listed) {
            this(id, order, text, listed, null);
        }
    }

    private final Logger logger;
    private boolean broken;
    private Constructor<?> entryConstructor, updateConstructor, removeConstructor, profileConstructor;
    private Class<?> actionClass;
    private Object survival;
    private Object skin;
    private Class<?> propertyMapClass;
    /** Extra signed skins by name (the teams' colours), from config.yml tab.skins. */
    private final Map<String, Object> skins = new HashMap<>();
    private Method asVanilla;
    /** What each viewer's client has now, so only changes are sent. */
    private final Map<UUID, Map<UUID, Slot>> shown = new HashMap<>();

    TabList(Logger logger, InputStream skinFile) {
        this.logger = logger;
        try {
            Class<?> packet = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> entry = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> profile = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyMap = Class.forName("com.mojang.authlib.properties.PropertyMap");
            Class<?> gameType = Class.forName("net.minecraft.world.level.GameType");
            Class<?> remove = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            for (Constructor<?> c : entry.getDeclaredConstructors()) if (c.getParameterCount() > 5) entryConstructor = c;
            updateConstructor = packet.getConstructor(EnumSet.class, List.class);
            removeConstructor = remove.getConstructor(List.class);
            survival = gameType.getField("SURVIVAL").get(null);
            asVanilla = Class.forName("io.papermc.paper.adventure.PaperAdventure").getMethod("asVanilla", Component.class);
            if (entryConstructor == null) throw new IllegalStateException("no entry constructor");
            profileConstructor = profile.getConstructor(UUID.class, String.class, propertyMap);
            skin = skin(propertyMap, skinFile);
            propertyMapClass = propertyMap;
        } catch (Throwable e) {
            broken = true;
            logger.info("The tab list layout is off on this server version (" + e.getClass().getSimpleName() + ").");
        }
    }

    /** The gray skin (a texture Mojang signed, or clients won't show it). */
    private static Object skin(Class<?> propertyMap, InputStream file) throws Exception {
        List<String> lines = new ArrayList<>();
        if (file != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) if (!line.isBlank() && !line.startsWith("#")) lines.add(line.trim());
            }
        }
        com.google.common.collect.ImmutableMultimap<String, Object> map = com.google.common.collect.ImmutableMultimap.of();
        if (lines.size() >= 2) {
            Class<?> property = Class.forName("com.mojang.authlib.properties.Property");
            Object texture = property.getConstructor(String.class, String.class, String.class).newInstance("textures", lines.get(0), lines.get(1));
            map = com.google.common.collect.ImmutableMultimap.of("textures", texture);
        }
        return propertyMap.getConstructor(com.google.common.collect.Multimap.class).newInstance(map);
    }

    /** A signed skin to use for slots that name it (texture value and Mojang's signature). */
    void addSkin(String name, String value, String signature) {
        if (broken || propertyMapClass == null || value == null || signature == null || value.isBlank() || signature.isBlank()) return;
        try {
            Class<?> property = Class.forName("com.mojang.authlib.properties.Property");
            Object texture = property.getConstructor(String.class, String.class, String.class).newInstance("textures", value.trim(), signature.trim());
            skins.put(name, propertyMapClass.getConstructor(com.google.common.collect.Multimap.class)
                    .newInstance(com.google.common.collect.ImmutableMultimap.of("textures", texture)));
        } catch (Throwable e) {
            logger.warning("Couldn't read the tab skin '" + name + "': " + e);
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

    private Object entry(Slot slot) throws ReflectiveOperationException {
        // No name: made-up rows never show up when players tab-complete names.
        Object profile = profileConstructor.newInstance(slot.id(), "", slot.skin() != null && skins.containsKey(slot.skin()) ? skins.get(slot.skin()) : skin);
        Object[] args = new Object[entryConstructor.getParameterCount()];
        Class<?>[] types = entryConstructor.getParameterTypes();
        // (UUID id, GameProfile profile, boolean listed, int latency, GameType mode, Component name, boolean hat, int order, chat)
        int ints = 0, bools = 0;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == UUID.class) args[i] = slot.id();
            else if (type.getSimpleName().equals("GameProfile")) args[i] = profile;
            else if (type == boolean.class) args[i] = bools++ == 0 ? slot.listed() : Boolean.TRUE; // listed, then hat
            else if (type == int.class) args[i] = ints++ == 0 ? 0 : slot.order(); // latency, then list order
            else if (type.isInstance(survival)) args[i] = survival;
            // never a bare string: a styled component goes out as a compound, which every client (and test bot) reads
            else if (type.getSimpleName().equals("Component")) args[i] = asVanilla.invoke(null, slot.text().colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.WHITE));
            else args[i] = null;
        }
        return entryConstructor.newInstance(args);
    }

    /** Brings a viewer's made-up rows in line with these, sending only what changed. */
    void apply(Player viewer, List<Slot> slots) {
        if (broken) return;
        Map<UUID, Slot> known = shown.computeIfAbsent(viewer.getUniqueId(), id -> new HashMap<>());
        try {
            List<Object> added = new ArrayList<>(), changed = new ArrayList<>();
            for (Slot slot : slots) {
                Slot before = known.get(slot.id());
                if (before == null) added.add(entry(slot));
                else if (!Objects.equals(before.skin(), slot.skin())) {
                    // a new face needs the row taken away and added again
                    send(viewer, removeConstructor.newInstance(new ArrayList<>(List.of(slot.id()))));
                    added.add(entry(slot));
                } else if (before.order() != slot.order() || before.listed() != slot.listed() || !Objects.equals(before.text(), slot.text())) {
                    changed.add(entry(slot));
                } else continue;
                known.put(slot.id(), slot);
            }
            if (!added.isEmpty()) send(viewer, updateConstructor.newInstance(actions("ADD_PLAYER", "UPDATE_LISTED", "UPDATE_LATENCY",
                    "UPDATE_GAME_MODE", "UPDATE_DISPLAY_NAME", "UPDATE_HAT", "UPDATE_LIST_ORDER"), added));
            if (!changed.isEmpty()) send(viewer, updateConstructor.newInstance(actions("UPDATE_LISTED", "UPDATE_DISPLAY_NAME",
                    "UPDATE_LIST_ORDER"), changed));
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** Takes all the made-up rows away from a viewer. */
    void clear(Player viewer) {
        Map<UUID, Slot> known = shown.remove(viewer.getUniqueId());
        if (broken || known == null || known.isEmpty()) return;
        try {
            send(viewer, removeConstructor.newInstance(new ArrayList<>(known.keySet())));
        } catch (Throwable e) {
            fail(e);
        }
    }

    void forget(UUID viewer) {
        shown.remove(viewer);
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
        logger.warning("Tab list layout turned off: " + e);
    }
}
