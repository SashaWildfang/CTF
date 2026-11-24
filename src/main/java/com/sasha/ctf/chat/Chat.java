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
import java.util.Locale;
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
        this.guard = new ChatGuard(plugin);
        this.ranks = hasRanks();
        if (ranks) plugin.getLogger().info("FractalRanks found: rank prefixes in chat and the tab list.");
        this.tab = new TabList(plugin.getLogger(), plugin.getResource("tab-skin.txt"));
        // Team-coloured heads for the team headings (signed skins, config.yml tab.skins.<team>.value/.signature)
        // Skins from config.yml tab.skins, and any the server's file doesn't have (a config.yml from before 1.14 has
        // none, and Bukkit then hands back an empty section) from the plugin's own copy.
        var config = plugin.getConfig();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (config.getDefaults() != null && config.getDefaults().isConfigurationSection("tab.skins")) {
            names.addAll(config.getDefaults().getConfigurationSection("tab.skins").getKeys(false));
        }
        if (config.contains("tab.skins", true)) names.addAll(config.getConfigurationSection("tab.skins").getKeys(false));
        for (String name : names) {
            String path = "tab.skins." + name;
            var from = config.contains(path + ".value", true) ? config : config.getDefaults();
            if (from != null) tab.addSkin(name, from.getString(path + ".value"), from.getString(path + ".signature"));
        }
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
        return LegacyCodes.toMiniMessage(rawPrefix(player));
    }

    /** The rank prefix exactly as the rank has it (with its & codes), or "". */
    private String rawPrefix(Player player) {
        if (!ranks) return "";
        try {
            var adapter = net.luckperms.api.LuckPermsProvider.get().getPlayerAdapter(Player.class);
            // Rank prefixes that are resource-pack glyphs show as boxes on servers without the pack:
            // chat.group-tags can give each rank group a plain text tag instead.
            var tags = plugin.getConfig().getConfigurationSection("chat.group-tags");
            if (plugin.getConfig().getBoolean("chat.use-group-tags", false) && tags != null) {
                String group = adapter.getUser(player).getPrimaryGroup();
                String tag = tags.getString(group == null ? "" : group.toLowerCase(java.util.Locale.ROOT));
                return tag == null ? "" : tag;
            }
            String prefix = adapter.getMetaData(player).getPrefix();
            return prefix == null ? "" : prefix;
        } catch (Throwable e) {
            return "";
        }
    }

    private static final java.util.regex.Pattern COLOUR_TAG = java.util.regex.Pattern.compile(
            "<(?:colou?r:|c:)?(#[0-9a-fA-F]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white)>");

    /** The colour a prefix leaves the text after it in: its last colour code or colour tag, wherever that stands. */
    private static net.kyori.adventure.text.format.TextColor trailingColour(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String text = LegacyCodes.toMiniMessage(raw);
        var tags = COLOUR_TAG.matcher(text);
        String last = null;
        while (tags.find()) last = tags.group(1);
        if (last == null) return null;
        if (last.startsWith("#")) return net.kyori.adventure.text.format.TextColor.fromHexString(last);
        return net.kyori.adventure.text.format.NamedTextColor.NAMES.value(last.replace("grey", "gray"));
    }

    private TeamColor teamOf(Player player) {
        Match match = plugin.game() == null ? null : plugin.game().match();
        if (match == null || !match.world().equals(player.getWorld())) return null;
        return match.teamOf(player.getUniqueId());
    }

    /** The kit badge glyph (font fractalctf:kits, U+E000 + team * 32 + kit; see tools/kit-badges) and a space. */
    private String kitBadge(Player player, TeamColor team) {
        var kit = plugin.kits().of(player.getUniqueId());
        char glyph = (char) (0xE000 + team.ordinal() * 32 + kit.ordinal());
        return "<font:fractalctf:kits><white>" + glyph + "</white></font> ";
    }

    /** "Rank Name" with the name in the team colour (in a match), or in the rank's colour. */
    public Component name(Player player) {
        TeamColor team = teamOf(player);
        String key = team == null ? "chat.name" : "chat.name-in-team";
        String rank = prefix(player);
        // On a team in a match: the kit's badge (team-coloured, from the kit badge pack) in place of the rank badge
        if (team != null && plugin.getConfig().getBoolean("game.kit-badges", true)) rank = kitBadge(player, team);
        String format = plugin.messages().raw(key).replace("<rankcolor>", "<" + rankColour(player, rank).asHexString() + ">");
        return plugin.messages().parse(colour(format, team), Messages.parsed("rank", rank),
                Messages.arg("player", player.getName()), Messages.arg("tag", team == null ? "" : team.display().substring(0, 1)));
    }

    /**
     * The colour of a player's rank: the rank's "name-color" (or "color") meta if it has one, otherwise the colour
     * its prefix ends on (a prefix that is a picture and then "&c" makes the name red, as in the Lobby), otherwise
     * the last colour of any text in the prefix, otherwise white.
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
        var trailing = trailingColour(rawPrefix(player));
        if (trailing != null) return trailing;
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

    /** The name in the tab list: as in chat, but someone watching a match is just their name in gray. */
    private Component listName(Player player) {
        Match match = plugin.game() == null ? null : plugin.game().match();
        if (match != null && match.world().equals(player.getWorld()) && match.teamOf(player.getUniqueId()) == null) {
            Component name = Component.text(player.getName(), net.kyori.adventure.text.format.NamedTextColor.GRAY);
            return plugin.getConfig().getBoolean("game.kit-badges", true) ? Component.textOfChildren(com.sasha.ctf.game.Match.specBadge(), Component.text(" "), name) : name;
        }
        return name(player);
    }

    /** Updates a player's tab list name (on joining, changing team, or a Spy's disguise ending). */
    public void refresh(Player player) {
        player.playerListName(listName(player));
        layout();
    }

    // ---------------------------------------------------------------- tab layout

    private static final int MAX_ROWS = 20, MAX_COLUMNS = 4, SLOTS = MAX_ROWS * MAX_COLUMNS;
    /** The fewest rows the game draws this many columns with (it only starts another column past 20 rows). */
    private static final int[] FEWEST_ROWS = {0, 1, 11, 14, 16};

    private static int order(int slot) {
        return 1000 - slot;
    }

    private static UUID slotId(int slot) {
        return UUID.nameUUIDFromBytes(("fractalctf-tab-slot-" + slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * The tab list as a grid (it fills a column at a time): each cell is a player or a made-up row with text.
     * It is only as big as what is in it: two columns in a quiet lobby, up to four when it is full. The players'
     * places are the same for everyone; the info column (the last) is each viewer's own.
     */
    private final class Grid {
        final int columns, rows;
        final Component[] text;
        final Player[] players;
        final String[] skins;

        Grid(int columns, int rows) {
            this.columns = Math.max(1, Math.min(MAX_COLUMNS, columns));
            this.rows = Math.max(FEWEST_ROWS[this.columns], Math.min(MAX_ROWS, rows));
            this.text = new Component[this.columns * this.rows];
            this.players = new Player[this.columns * this.rows];
            this.skins = new String[this.columns * this.rows];
        }

        int slot(int column, int row) {
            return column * rows + row;
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
            int first = slot(fromColumn, 0), last = slot(toColumn, rows - 1);
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

    /** A team in the tab list: by kit name (A to Z), then by name. */
    private List<Player> byKit(java.util.Collection<? extends Player> people) {
        List<Player> list = new java.util.ArrayList<>(people);
        list.sort(java.util.Comparator.<Player, String>comparing(p -> plugin.kits().of(p.getUniqueId()).display(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
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

    /**
     * Where everyone sits. In the lobby: the players, in as many columns as they need, then the info column.
     * In a match of two teams: a column each, spectators (if there are any) under the first team, then the
     * info column. With four teams: two teams to a column, a spectators column only while someone is watching,
     * then the info column. {@code infoRows} is how many lines the info column has.
     */
    private Grid places(int infoRows) {
        Messages messages = plugin.messages();
        Match match = plugin.game() == null ? null : plugin.game().match();
        if (match == null) {
            List<Player> everyone = sorted(Bukkit.getOnlinePlayers());
            int cells = everyone.size() + 1;                         // the heading takes one
            int across = cells <= MAX_ROWS ? 1 : cells <= 2 * MAX_ROWS ? 2 : 3;
            Grid grid = new Grid(across + 1, Math.max(infoRows, (cells + across - 1) / across));
            grid.flow(0, across - 1, messages.component("tab.players-heading", Messages.arg("players", everyone.size())), everyone);
            return grid;
        }
        List<TeamColor> teams = match.mode().teams();
        List<Player> watching = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) if (match.teamOf(player.getUniqueId()) == null) watching.add(player);
        Component watchers = messages.component("tab.spectator-heading", Messages.arg("players", watching.size()));
        if (teams.size() <= 2) {
            int most = 0;
            for (TeamColor team : teams) most = Math.max(most, match.online(team).size() + 1);
            int first = teams.isEmpty() ? 0 : match.online(teams.get(0)).size() + 1;
            // under the first team: a gap, the heading, the spectators
            int needed = watching.isEmpty() ? most : Math.max(most, first + 2 + watching.size());
            Grid grid = new Grid(teams.size() + 1, Math.max(infoRows, needed));
            int under = watching.isEmpty() ? 0 : Math.max(2, Math.min(watching.size() + 1, grid.rows - first - 1));
            for (int i = 0; i < teams.size(); i++) {
                teamSection(grid, match, teams.get(i), i, 0, i == 0 && under > 0 ? grid.rows - under - 2 : grid.rows - 1);
            }
            if (under > 0) {
                grid.section(0, grid.rows - under, grid.rows - 1, watchers, sorted(watching));
                grid.skins[grid.slot(0, grid.rows - under)] = "spectators";
            }
            return grid;
        }
        // four teams: two to a column, half the rows each
        int most = 0;
        for (TeamColor team : teams) most = Math.max(most, match.online(team).size() + 1);
        boolean column = !watching.isEmpty();
        Grid grid = new Grid(column ? 4 : 3, Math.max(Math.max(infoRows, 2 * most + 1), column ? watching.size() + 1 : 0));
        int half = grid.rows / 2;
        for (int i = 0; i < teams.size() && i < 4; i++) {
            teamSection(grid, match, teams.get(i), i % 2, i < 2 ? 0 : grid.rows - half, i < 2 ? grid.rows - half - 2 : grid.rows - 1);
        }
        if (column) {
            grid.section(2, 0, grid.rows - 1, watchers, sorted(watching));
            grid.skins[grid.slot(2, 0)] = "spectators";
        }
        return grid;
    }

    private void teamSection(Grid grid, Match match, TeamColor team, int column, int first, int last) {
        Component heading = plugin.messages().component("tab.team-heading",
                Messages.comp("team", Component.text(team.display().toUpperCase(java.util.Locale.ROOT) + " TEAM", team.text())),
                Messages.arg("players", match.online(team).size()));
        grid.section(column, first, Math.max(first + 1, last), heading, byKit(match.online(team)));
        grid.skins[grid.slot(column, first)] = team.id();
    }

    /** The right-hand column, for one viewer: the match (or the lobby) and their own numbers. */
    private List<Component> info(Player viewer) {
        Messages messages = plugin.messages();
        Match match = plugin.game() == null ? null : plugin.game().match();
        List<Component> lines = new java.util.ArrayList<>();
        // In the lobby "None" until a kit is picked (a match then gives the default one)
        String kit = match == null && plugin.kits().selected(viewer.getUniqueId()) == null ? plugin.messages().raw("tab.info.no-kit")
                : plugin.kits().of(viewer.getUniqueId()).display();
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
                        case COUNTDOWN -> hub.votingOpen()
                                ? messages.component("tab.info.status-voting", Messages.arg("seconds", Math.max(0, hub.secondsLeft() - hub.gateSeconds())))
                                : messages.component("tab.info.status-countdown", Messages.arg("seconds", Math.max(0, hub.secondsLeft())));
                        case STARTING -> messages.component("tab.info.status-opening");
                        case IN_MATCH -> messages.component("tab.info.status-playing", Messages.arg("map", hub.mapName(hub.row().map())));
                    };
            String rank = prefix(viewer);
            Component rankName = rank.isBlank() ? messages.component("tab.info.no-rank") : MINI.deserialize(rank);
            String howto = hub == null ? "waiting" : hub.paused() ? "paused" : switch (hub.phase()) {
                case WAITING -> "waiting";
                case COUNTDOWN -> hub.votingOpen() ? "voting" : "gate";
                case STARTING -> "gate";
                case IN_MATCH -> "match";
            };
            for (String line : messages.list("tab.info.lobby")) {
                if (line.strip().equals("<howto>")) {
                    for (String step : messages.list("tab.info.howto." + howto)) lines.add(messages.parse(step));
                    continue;
                }
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
        Map<UUID, List<Component>> infos = new java.util.HashMap<>();
        int infoRows = 0;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            List<Component> info = info(viewer);
            infos.put(viewer.getUniqueId(), info);
            infoRows = Math.max(infoRows, info.size());
        }
        Grid grid = places(Math.min(MAX_ROWS, infoRows));
        int size = grid.columns * grid.rows, infoFrom = grid.slot(grid.columns - 1, 0);
        java.util.Set<UUID> placed = new java.util.HashSet<>();
        for (int slot = 0; slot < size; slot++) {
            Player player = grid.players[slot];
            if (player == null) continue;
            placed.add(player.getUniqueId());
            if (player.getPlayerListOrder() != order(slot)) player.setPlayerListOrder(order(slot));
        }
        Component blank = Component.empty();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            // Anyone without a place is taken off this viewer's list: one name too many would push every row along.
            for (Player other : Bukkit.getOnlinePlayers()) {
                boolean show = placed.contains(other.getUniqueId());
                if (viewer.isListed(other) == show || !viewer.canSee(other)) continue;
                if (show) viewer.listPlayer(other);
                else viewer.unlistPlayer(other);
            }
            List<Component> info = infos.get(viewer.getUniqueId());
            List<TabList.Slot> slots = new java.util.ArrayList<>(SLOTS);
            for (int slot = 0; slot < SLOTS; slot++) {
                if (slot >= size) {                                  // outside the grid as it is now: not shown
                    slots.add(new TabList.Slot(slotId(slot), order(slot), blank, false));
                    continue;
                }
                Component text = grid.text[slot];
                if (slot >= infoFrom) text = slot - infoFrom < info.size() ? info.get(slot - infoFrom) : null;
                slots.add(new TabList.Slot(slotId(slot), order(slot), text == null ? blank : text, grid.players[slot] == null, grid.skins[slot]));
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
        lastChat.remove(event.getPlayer().getUniqueId());
        guard.forget(event.getPlayer().getUniqueId());
        tab.forget(event.getPlayer().getUniqueId());
        event.quitMessage(null);
        Bukkit.getScheduler().runTask(plugin, this::layout);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String checked = guard.check(sender, net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()));
        if (checked == null || cooling(sender)) {
            event.setCancelled(true);
            return;
        }
        event.message(Component.text(checked));
        TeamColor team = teamOf(sender);
        Component name = name(sender);
        boolean toTeam = team != null && teamChat.contains(sender.getUniqueId());
        if (toTeam) {
            // Only teammates (and the console) see it.
            event.viewers().removeIf(viewer -> viewer instanceof Player other && teamOf(other) != team);
        }
        String format = colour(plugin.messages().raw(toTeam ? "chat.team-format" : "chat.format"), team);
        // A GG wave is on (someone bought something on the store): each "gg" comes out in a gradient.
        Component typed = ggWave.active()
                ? ggWave.paint(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()), Component::text)
                : event.message();
        if (plugin.getConfig().getBoolean("chat.signed", false)) {
            event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> MINI.deserialize(format,
                    Messages.comp("name", name), Messages.comp("message", ggWave.active() ? typed : message))));
            return;
        }
        // Sent as plain server messages, not as Minecraft's signed chat: the client checks every signed message
        // against the sender's chat session and shows "Chat validation error" in its place when that check
        // fails (it does after some server switches). A plain message has nothing to check. The event itself
        // goes on with nobody left to send to, so other plugins still see what was said.
        Component line = MINI.deserialize(format, Messages.comp("name", name), Messages.comp("message", typed));
        List<net.kyori.adventure.audience.Audience> viewers = new java.util.ArrayList<>(event.viewers());
        event.viewers().clear();
        for (net.kyori.adventure.audience.Audience viewer : viewers) viewer.sendMessage(line);
    }

    private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
    /** The word filter, anti-spam, /mutechat, /slowchat and /clearchat. */
    private final ChatGuard guard;

    public ChatGuard guard() { return guard; }
    /** GG waves from the proxy (FractalGG). */
    private final GgWave ggWave = new GgWave();

    /** Listens for GG waves from the proxy. */
    public void listenForGgWaves() {
        ggWave.register(plugin);
    }

    /** Whether this player has to wait before chatting again (and tells them so). Ranks with ctf.chat.nocooldown never wait. */
    private boolean cooling(Player sender) {
        long wait = (long) (plugin.getConfig().getDouble("chat.cooldown-seconds", 3) * 1000);
        if (wait <= 0 || sender.hasPermission("ctf.chat.nocooldown")) return false;
        long now = System.currentTimeMillis(), last = lastChat.getOrDefault(sender.getUniqueId(), 0L);
        if (now - last < wait) {
            plugin.messages().send(sender, "chat.cooldown", Messages.arg("seconds", String.format(Locale.ROOT, "%.1f", (wait - (now - last)) / 1000.0)));
            return true;
        }
        lastChat.put(sender.getUniqueId(), now);
        return false;
    }

    /** /t message: one message to the team. */
    public void teamMessage(Player sender, String text) {
        TeamColor team = teamOf(sender);
        if (team == null) {
            plugin.messages().send(sender, "chat.no-team");
            return;
        }
        text = guard.check(sender, text);
        if (text == null || cooling(sender)) return;
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
            footer = null;                                           // each player's own: which team they're on
        } else {
            header = messages.component("tab.hub-header", Messages.arg("players", online));
            footer = messages.component("tab.hub-footer");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            // the tab name follows kit changes in a match (its badge)
            if (match != null) {
                Component listed = listName(player);
                if (!listed.equals(player.playerListName())) player.playerListName(listed);
            }
            Component own = footer;
            if (own == null) {
                TeamColor team = match.spectating(player.getUniqueId()) ? null : match.teamOf(player.getUniqueId());
                Component line = team == null ? messages.component("tab.you-spectate")
                        : messages.component("tab.you-are-on", Messages.comp("team", Component.text(team.display().toUpperCase(java.util.Locale.ROOT), team.text())
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)));
                if (team != null) line = line.colorIfAbsent(team.text());
                own = messages.component("tab.game-footer", Messages.comp("score", line));
            }
            player.sendPlayerListHeaderAndFooter(header, own);
        }
        layout();
    }
}
