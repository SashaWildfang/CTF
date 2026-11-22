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
import java.util.Map;
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

    public Chat(FractalCTF plugin) {
        this.plugin = plugin;
        this.ranks = hasRanks();
        if (ranks) plugin.getLogger().info("FractalRanks found: rank prefixes in chat and the tab list.");
        this.tab = new TabList(plugin.getLogger(), plugin.getResource("tab-skin.txt"));
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

    /** "[R] Rank Name" in the team colour (in a match), or "Rank Name" with the name in the rank's colour. */
    public Component name(Player player) {
        TeamColor team = teamOf(player);
        String key = team == null ? "chat.name" : "chat.name-team";
        String rank = prefix(player);
        String format = plugin.messages().raw(key).replace("<rankcolor>", "<" + rankColour(player, rank).asHexString() + ">");
        return plugin.messages().parse(colour(format, team), Messages.parsed("rank", rank),
                Messages.arg("player", player.getName()), Messages.arg("tag", team == null ? "" : team.display().substring(0, 1)));
    }

    /**
     * The colour of a player's rank: the rank's "name-color" (or "color") meta if it has one, otherwise the last
     * colour used in its prefix, otherwise white.
     */
    public net.kyori.adventure.text.format.TextColor rankColour(Player player, String prefix) {
        if (ranks) {
            try {
                var meta = net.luckperms.api.LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
                for (String key : new String[] {"name-color", "namecolor", "name-colour", "color", "colour"}) {
                    String value = meta.getMetaValue(key);
                    if (value == null || value.isBlank()) continue;
                    var colour = LegacyCodes.lastColor(value.startsWith("&") || value.startsWith("§") ? value : "&" + value);
                    if (colour == null) colour = net.kyori.adventure.text.format.TextColor.fromHexString(value.trim());
                    if (colour == null) colour = net.kyori.adventure.text.format.NamedTextColor.NAMES.value(value.trim().toLowerCase(java.util.Locale.ROOT));
                    if (colour != null) return colour;
                }
            } catch (Throwable ignored) {
                // no meta: fall back to the prefix
            }
        }
        var last = lastColour(MINI.deserialize(prefix), null, null);
        return last == null ? net.kyori.adventure.text.format.NamedTextColor.WHITE : last;
    }

    /** The colour of the last visible text in a component. */
    private static net.kyori.adventure.text.format.TextColor lastColour(Component component, net.kyori.adventure.text.format.TextColor inherited,
                                                                      net.kyori.adventure.text.format.TextColor found) {
        var colour = component.color() != null ? component.color() : inherited;
        if (component instanceof net.kyori.adventure.text.TextComponent text && !text.content().isBlank() && colour != null) found = colour;
        for (Component child : component.children()) found = lastColour(child, colour, found);
        return found;
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

    /** Updates a player's tab list name (on joining, changing team, or a Spy's disguise ending). */
    public void refresh(Player player) {
        player.playerListName(name(player));
        layout();
    }

    // ---------------------------------------------------------------- tab layout

    private static final int ROWS = 20, COLUMNS = 4, SLOTS = ROWS * COLUMNS;
    /** Players who aren't given a place sort below every slot, so they fall off the end. */
    private static final int OFF_THE_LIST = -1;

    private static int order(int slot) {
        return 1000 - slot;
    }

    private static UUID slotId(int slot) {
        return UUID.nameUUIDFromBytes(("fractalctf-tab-slot-" + slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * The 4 x 20 grid (it fills a column at a time): each cell is a player or a made-up row with text.
     * The players' places are the same for everyone; the info column is each viewer's own.
     */
    private final class Grid {
        final Component[] text = new Component[SLOTS];
        final Player[] players = new Player[SLOTS];

        static int slot(int column, int row) {
            return column * ROWS + row;
        }

        void text(int column, int row, Component line) {
            text[slot(column, row)] = line;
        }

        /** A heading, then players under it from {@code first} to {@code last} row ("+3 more" if they don't fit). */
        void section(int column, int first, int last, Component heading, List<Player> people) {
            text(column, first, heading);
            int room = last - first;
            for (int i = 0; i < people.size() && i < room; i++) {
                if (i == room - 1 && people.size() > room) {
                    text(column, first + 1 + i, plugin.messages().component("tab.more", Messages.arg("players", people.size() - i)));
                    break;
                }
                players[slot(column, first + 1 + i)] = people.get(i);
            }
        }

        /** Players over several columns, a heading at the top of the first. */
        void flow(int fromColumn, int toColumn, Component heading, List<Player> people) {
            int first = slot(fromColumn, 0), last = slot(toColumn, ROWS - 1);
            text[first] = heading;
            int room = last - first;
            for (int i = 0; i < people.size() && i < room; i++) {
                if (i == room - 1 && people.size() > room) {
                    text[first + 1 + i] = plugin.messages().component("tab.more", Messages.arg("players", people.size() - i));
                    break;
                }
                players[first + 1 + i] = people.get(i);
            }
        }
    }

    /** Highest rank first, then by name. */
    private List<Player> sorted(java.util.Collection<? extends Player> people) {
        List<Player> list = new java.util.ArrayList<>(people);
        Map<UUID, Integer> weights = new java.util.HashMap<>();
        for (Player player : list) weights.put(player.getUniqueId(), weight(player));
        list.sort(java.util.Comparator.<Player>comparingInt(p -> -weights.get(p.getUniqueId()))
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Where everyone sits: teams (or the lobby's players) on the left, spectators, then the info column. */
    private Grid places() {
        Grid grid = new Grid();
        Messages messages = plugin.messages();
        Match match = plugin.game() == null ? null : plugin.game().match();
        if (match == null) {
            Component heading = messages.component("tab.players-heading", Messages.arg("players", Bukkit.getOnlinePlayers().size()));
            grid.flow(0, COLUMNS - 2, heading, sorted(Bukkit.getOnlinePlayers()));
            return grid;
        }
        List<TeamColor> teams = match.mode().teams();
        List<Player> watching = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) if (match.teamOf(player.getUniqueId()) == null) watching.add(player);
        if (teams.size() <= 2) {
            for (int i = 0; i < teams.size(); i++) teamSection(grid, match, teams.get(i), i, 0, ROWS - 1);
        } else {
            // four teams: two per column, ten rows each
            for (int i = 0; i < teams.size() && i < 4; i++) teamSection(grid, match, teams.get(i), i % 2, i < 2 ? 0 : ROWS / 2, i < 2 ? ROWS / 2 - 1 : ROWS - 1);
        }
        grid.section(2, 0, ROWS - 1, messages.component("tab.spectator-heading", Messages.arg("players", watching.size())), sorted(watching));
        return grid;
    }

    private void teamSection(Grid grid, Match match, TeamColor team, int column, int first, int last) {
        Component heading = plugin.messages().component("tab.team-heading",
                Messages.comp("team", Component.text(team.display().toUpperCase(java.util.Locale.ROOT) + " TEAM", team.text())),
                Messages.arg("players", match.online(team).size()));
        grid.section(column, first, last, heading, sorted(match.online(team)));
    }

    /** The right-hand column, for one viewer: the match (or the lobby) and their own numbers. */
    private List<Component> info(Player viewer) {
        Messages messages = plugin.messages();
        Match match = plugin.game() == null ? null : plugin.game().match();
        List<Component> lines = new java.util.ArrayList<>();
        String kit = plugin.kits().of(viewer.getUniqueId()).display();
        long coins = plugin.kits().coins(viewer.getUniqueId());
        if (match != null) {
            var mine = match.stats(viewer.getUniqueId());
            TeamColor team = match.teamOf(viewer.getUniqueId());
            Component teamName = team == null ? messages.component("tab.info.no-team") : Component.text(team.display(), team.text());
            for (String line : messages.list("tab.info.game")) {
                if (line.equals("<scores>")) {
                    lines.addAll(match.scoreLines());
                    continue;
                }
                lines.add(messages.parse(line, Messages.arg("map", match.map().name), Messages.arg("mode", match.mode().display()),
                        Messages.arg("time", match.phase() == Match.Phase.PLAYING ? clock(match.secondsLeft()) : "-"),
                        Messages.comp("team", teamName), Messages.arg("kills", mine.kills), Messages.arg("deaths", mine.deaths),
                        Messages.arg("assists", mine.assists), Messages.arg("kit", kit), Messages.arg("coins", coins)));
            }
        } else {
            var hub = plugin.hub();
            Component status = hub == null ? messages.component("tab.info.status-game")
                    : hub.paused() ? messages.component("tab.info.status-paused") : switch (hub.phase()) {
                        case WAITING -> messages.component("tab.info.status-waiting");
                        case COUNTDOWN -> messages.component("tab.info.status-countdown", Messages.arg("seconds", Math.max(0, hub.secondsLeft())));
                        case STARTING -> messages.component("tab.info.status-starting");
                        case IN_MATCH -> messages.component("tab.info.status-playing", Messages.arg("map", hub.mapName(hub.row().map())));
                    };
            String rank = prefix(viewer);
            Component rankName = rank.isBlank() ? messages.component("tab.info.no-rank") : MINI.deserialize(rank);
            for (String line : messages.list("tab.info.lobby")) {
                lines.add(messages.parse(line, Messages.comp("status", status), Messages.comp("rank", rankName), Messages.arg("kit", kit),
                        Messages.arg("coins", coins), Messages.arg("players", Bukkit.getOnlinePlayers().size())));
            }
        }
        return lines;
    }

    private static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Puts everyone in their place and sends each viewer the made-up rows around them. */
    private void layout() {
        if (!tab.works()) {
            for (Player player : Bukkit.getOnlinePlayers()) player.setPlayerListOrder(weight(player));
            return;
        }
        Grid grid = places();
        java.util.Set<UUID> placed = new java.util.HashSet<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            Player player = grid.players[slot];
            if (player == null) continue;
            placed.add(player.getUniqueId());
            if (player.getPlayerListOrder() != order(slot)) player.setPlayerListOrder(order(slot));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!placed.contains(player.getUniqueId()) && player.getPlayerListOrder() != OFF_THE_LIST) player.setPlayerListOrder(OFF_THE_LIST);
        }
        Component blank = Component.empty();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            List<Component> info = info(viewer);
            List<TabList.Slot> slots = new java.util.ArrayList<>(SLOTS);
            for (int slot = 0; slot < SLOTS; slot++) {
                Component text = grid.text[slot];
                if (slot >= Grid.slot(COLUMNS - 1, 0)) {
                    int row = slot - Grid.slot(COLUMNS - 1, 0);
                    text = row < info.size() ? info.get(row) : null;
                }
                boolean player = grid.players[slot] != null;
                slots.add(new TabList.Slot(slotId(slot), order(slot), text == null ? blank : text, !player));
            }
            tab.apply(viewer, slots);
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
        tab.forget(event.getPlayer().getUniqueId());
        event.quitMessage(null);
        Bukkit.getScheduler().runTask(plugin, this::layout);
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
        layout();
    }
}
