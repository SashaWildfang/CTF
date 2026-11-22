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

import java.util.List;
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
    private final TabList tab;
    /** Players who have the team headings in their tab list. */
    private final Set<UUID> headed = new java.util.HashSet<>();
    private List<TabList.Line> lastLines = List.of();

    public Chat(FractalCTF plugin) {
        this.plugin = plugin;
        this.ranks = hasRanks();
        if (ranks) plugin.getLogger().info("FractalRanks found: rank prefixes in chat and the tab list.");
        this.tab = plugin.ctfConfig().hub() ? null : new TabList(plugin.getLogger());
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

    /** The weight of the player's rank (higher ranks sort higher in the tab list). */
    public int weight(Player player) {
        if (!ranks) return 0;
        try {
            var luckPerms = net.luckperms.api.LuckPermsProvider.get();
            var user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            int best = 0;
            for (var group : user.getInheritedGroups(user.getQueryOptions())) best = Math.max(best, group.getWeight().orElse(0));
            return best;
        } catch (Throwable e) {
            return 0;
        }
    }

    /** Where a player goes in the tab list: by team in a match (in team order), by rank weight within it. */
    private int order(Player player) {
        int weight = Math.max(0, Math.min(7999, weight(player)));
        Match match = plugin.game() == null ? null : plugin.game().match();
        if (match == null || !match.world().equals(player.getWorld())) return weight;
        TeamColor team = match.teamOf(player.getUniqueId());
        if (team == null) return 10000 + 1000 + weight;
        int index = match.mode().teams().indexOf(team);
        return (match.mode().teamCount() - index) * 10000 + 10000 + 1000 + weight;
    }

    /** Updates a player's tab list name and place (on joining, changing team, or a Spy's disguise ending). */
    public void refresh(Player player) {
        player.playerListName(name(player));
        player.setPlayerListOrder(order(player));
    }

    /** The team headings (with player counts) and gaps between the teams. */
    private List<TabList.Line> lines(Match match) {
        List<TabList.Line> lines = new java.util.ArrayList<>();
        int n = match.mode().teamCount();
        for (int i = 0; i < n; i++) {
            TeamColor team = match.mode().teams().get(i);
            int base = (n - i) * 10000 + 10000;
            lines.add(new TabList.Line(lineId("head-" + team.id()), base + 9000, plugin.messages().component("tab.team-heading",
                    Messages.comp("team", Component.text(team.display().toUpperCase(java.util.Locale.ROOT) + " TEAM", team.text())),
                    Messages.arg("players", match.online(team).size()))));
            lines.add(new TabList.Line(lineId("gap-" + team.id()), base + 1, Component.text(" ")));
        }
        int watching = 0;
        for (Player player : match.world().getPlayers()) if (match.teamOf(player.getUniqueId()) == null) watching++;
        lines.add(new TabList.Line(lineId("head-spectators"), 10000 + 9000, watching == 0 ? Component.text(" ")
                : plugin.messages().component("tab.spectator-heading", Messages.arg("players", watching))));
        return lines;
    }

    private static UUID lineId(String key) {
        return UUID.nameUUIDFromBytes(("fractalctf-tab-" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Keeps everyone's team headings in step with the match (and takes them away when it's gone). */
    private void teamHeadings() {
        if (tab == null || !tab.works()) return;
        Match match = plugin.game().match();
        if (match == null) {
            if (!headed.isEmpty()) {
                List<UUID> ids = new java.util.ArrayList<>();
                for (TabList.Line line : lastLines) ids.add(line.id());
                for (Player player : Bukkit.getOnlinePlayers()) if (headed.contains(player.getUniqueId())) tab.hide(player, ids);
                headed.clear();
            }
            return;
        }
        List<TabList.Line> lines = lines(match);
        lastLines = lines;
        headed.removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (headed.add(player.getUniqueId())) tab.show(player, lines);
            else tab.update(player, lines);
            player.setPlayerListOrder(order(player));
        }
    }

    // ---------------------------------------------------------------- chat

    /** No join or leave messages on the CTF servers: players come and go with every match. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) refresh(player);
            headers();
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teamChat.remove(event.getPlayer().getUniqueId());
        headed.remove(event.getPlayer().getUniqueId());
        event.quitMessage(null);
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
            header = messages.component(match.map().authors.isEmpty() ? "tab.game-header-no-author" : "tab.game-header",
                    Messages.arg("map", match.map().name), Messages.arg("mode", match.mode().display()),
                    Messages.arg("authors", String.join(", ", match.map().authors)), Messages.arg("players", online));
            footer = messages.component("tab.game-footer", Messages.parsed("score", match.scoreLine()));
        } else {
            header = messages.component("tab.hub-header", Messages.arg("players", online));
            footer = messages.component("tab.hub-footer");
        }
        for (Player player : Bukkit.getOnlinePlayers()) player.sendPlayerListHeaderAndFooter(header, footer);
        if (match == null) for (Player player : Bukkit.getOnlinePlayers()) player.setPlayerListOrder(order(player));
        teamHeadings();
    }
}
