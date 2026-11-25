/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is in a party with whom, from Core on the proxy (channel fractal:core, message "Party": sent when a player
 * arrives and whenever their party changes). Auto Select puts party members on the same team when that keeps the
 * teams fair. Also notes which staff have network /vanish on ("State"), so only they are hidden while spectating.
 */
public final class Parties implements PluginMessageListener {

    public static final String CHANNEL = "fractal:core";
    private final Map<UUID, Set<UUID>> parties = new ConcurrentHashMap<>();
    /** Staff with Core's network /vanish on (from Core's "State" message: sent on arrival and on every change). */
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    /** Staff with Core's /socialspy on: they also see other teams' team chat. */
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    /** Called with (player, on) when Core's /chat team switches team chat ("TeamChat"); set by the plugin. */
    private java.util.function.BiConsumer<UUID, Boolean> teamChat = (id, on) -> { };

    public void onTeamChat(java.util.function.BiConsumer<UUID, Boolean> handler) {
        teamChat = handler;
    }

    public boolean spying(UUID player) {
        return spies.contains(player);
    }

    /** Whether this player has network /vanish on. */
    public boolean vanished(UUID player) {
        return vanished.contains(player);
    }

    public void register(Plugin plugin) {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    /** The other members of this player's party (empty without one). */
    public Set<UUID> mates(UUID player) {
        Set<UUID> mates = new HashSet<>(parties.getOrDefault(player, Set.of()));
        mates.remove(player);
        return mates;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String kind = in.readUTF();
            if ("State".equals(kind)) {
                UUID who = UUID.fromString(in.readUTF());
                int count = in.readInt();
                boolean on = false, spy = false;
                for (int i = 0; i < count && i < 100; i++) {
                    String toggle = in.readUTF();
                    boolean value = in.readBoolean();
                    if ("vanish".equals(toggle)) on = value;
                    if ("social-spy".equals(toggle)) spy = value;
                }
                if (on) vanished.add(who);
                else vanished.remove(who);
                if (spy) spies.add(who);
                else spies.remove(who);
                return;
            }
            if ("TeamChat".equals(kind)) {
                teamChat.accept(UUID.fromString(in.readUTF()), in.readBoolean());
                return;
            }
            if (!"Party".equals(kind)) return;                         // Core's other messages are for the Lobby
            UUID who = UUID.fromString(in.readUTF());
            int count = in.readInt();
            Set<UUID> members = new HashSet<>();
            for (int i = 0; i < count && i < 100; i++) members.add(UUID.fromString(in.readUTF()));
            if (members.size() <= 1) parties.remove(who);
            else parties.put(who, Set.copyOf(members));
        } catch (IOException | IllegalArgumentException ignored) {
            // not a message we understand
        }
    }
}
