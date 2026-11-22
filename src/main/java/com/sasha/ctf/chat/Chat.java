/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.chat;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.game.Match;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.LegacyCodes;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat and the tab list on both CTF servers: rank prefixes from FractalRanks (LuckPerms), team tags
 * and colours in a match, team chat (/t, /tc), and the tab header and footer.
 */
public final class Chat implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final FractalCTF plugin;
    private final boolean ranks;
    private final Set<UUID> teamChat = ConcurrentHashMap.newKeySet();

    public Chat(FractalCTF plugin) {
        this.plugin = plugin;
        this.ranks = hasRanks();
        if (ranks) plugin.getLogger().info("FractalRanks found: rank prefixes in chat and the tab list.");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::headers, 40L, 40L);
    }

    private static boolean hasRanks() {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            net.luckperms.api.LuckPermsProvider.get();
            return true;
        } catch (Throwable notThere) {
            return false;
        }
    }

    // ---------------------------------------------------------------- names

    /** The player's rank prefix as MiniMessage, or "". */
    public String prefix(Player player) {
        if (!ranks) return "";
        try {
            var adapter = net.luckperms.api.LuckPermsProvider.get().getPlayerAdapter(Player.class);
            // Rank prefixes that are resource-pack glyphs show as boxes on servers without the pack:
            // chat.group-tags can give each rank group a plain text tag instead.
            var tags = plugin.getConfig().getConfigurationSection("chat.group-tags");
            if (plugin.getConfig().getBoolean("chat.use-group-tags", false) && tags != null) {
                String group = adapter.getUser(player).getPrimaryGroup();
                String tag = tags.getString(group == null ? "" : group.toLowerCase(java.util.Locale.ROOT));
                return tag == null ? "" : LegacyCodes.toMiniMessage(tag);
            }
            String prefix = adapter.getMetaData(player).getPrefix();
            return prefix == null ? "" : LegacyCodes.toMiniMessage(prefix);
        } catch (Throwable e) {
            return "";
        }
    }

    private TeamColor teamOf(Player player) {
        Match match = plugin.game() == null ? null : plugin.game().match();
        if (match == null || !match.world().equals(player.getWorld())) return null;
        return match.teamOf(player.getUniqueId());
    }

    /** "[R] Rank Name" in the team colour (in a match), or "Rank Name". */
    public Component name(Player player) {
        TeamColor team = teamOf(player);
        String key = team == null ? "chat.name" : "chat.name-team";
        return plugin.messages().parse(colour(plugin.messages().raw(key), team), Messages.parsed("rank", prefix(player)),
                Messages.arg("player", player.getName()), Messages.arg("tag", team == null ? "" : team.display().substring(0, 1)));
    }

    /** Turns {@code <color>...</color>} in a format into the team's colour (white outside a match). */
    private static String colour(String format, TeamColor team) {
        String name = team == null ? "white" : team.id();
        return format.replace("<color>", "<" + name + ">").replace("</color>", "</" + name + ">");
    }

    /** Updates a player's tab list name (on joining, changing team, or a Spy's disguise ending). */
    public void refresh(Player player) {
        player.playerListName(name(player));
    }

    // ---------------------------------------------------------------- chat

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(plugin.messages().component("chat.join", Messages.comp("name", name(player))));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) refresh(player);
            headers();
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teamChat.remove(event.getPlayer().getUniqueId());
        event.quitMessage(plugin.messages().component("chat.quit", Messages.comp("name", name(event.getPlayer()))));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        TeamColor team = teamOf(sender);
        Component name = name(sender);
        boolean toTeam = team != null && teamChat.contains(sender.getUniqueId());
        if (toTeam) {
            // Only teammates (and the console) see it.
            event.viewers().removeIf(viewer -> viewer instanceof Player other && teamOf(other) != team);
        }
        String format = colour(plugin.messages().raw(toTeam ? "chat.team-format" : "chat.format"), team);
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> MINI.deserialize(format,
                Messages.comp("name", name), Messages.comp("message", message))));
    }

    /** /t message: one message to the team. */
    public void teamMessage(Player sender, String text) {
        TeamColor team = teamOf(sender);
        if (team == null) {
            plugin.messages().send(sender, "chat.no-team");
            return;
        }
        Component message = MINI.deserialize(colour(plugin.messages().raw("chat.team-format"), team), Messages.comp("name", name(sender)),
                Messages.arg("message", text));
        for (Player player : sender.getWorld().getPlayers()) if (teamOf(player) == team) player.sendMessage(message);
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /** /tc: everything typed goes to the team until turned off. */
    public void toggle(Player player) {
        if (teamChat.remove(player.getUniqueId())) {
            plugin.messages().send(player, "chat.team-off");
        } else if (teamOf(player) == null) {
            plugin.messages().send(player, "chat.no-team");
        } else {
            teamChat.add(player.getUniqueId());
            plugin.messages().send(player, "chat.team-on");
        }
    }

    // ---------------------------------------------------------------- tab header and footer

    private void headers() {
        Messages messages = plugin.messages();
        int online = Bukkit.getOnlinePlayers().size();
        Match match = plugin.game() == null ? null : plugin.game().match();
        Component header, footer;
        if (match != null) {
            header = messages.component("tab.game-header", Messages.arg("map", match.map().name), Messages.arg("mode", match.mode().display()),
                    Messages.arg("players", online));
            footer = messages.component("tab.game-footer", Messages.parsed("score", match.scoreLine()));
        } else {
            header = messages.component("tab.hub-header", Messages.arg("players", online));
            footer = messages.component("tab.hub-footer");
        }
        for (Player player : Bukkit.getOnlinePlayers()) player.sendPlayerListHeaderAndFooter(header, footer);
    }
}
