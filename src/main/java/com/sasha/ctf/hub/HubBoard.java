/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.MatchRow;
import com.sasha.ctf.model.VoteOption;
import com.sasha.ctf.util.Sidebar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Each hub player's sidebar: players, countdown, the vote (or the match score) and their kit. */
public final class HubBoard {

    private final FractalCTF plugin;
    private final HubManager hub;
    private final Map<UUID, Sidebar> boards = new HashMap<>();

    HubBoard(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    public void show(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), new Sidebar(board, plugin.messages().component("hub.sidebar.title")));
        scoreboards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        for (Player other : Bukkit.getOnlinePlayers()) {             // everyone's tag on the newcomer's board
            Tag tag = tags.get(other.getUniqueId());
            if (tag != null) apply(board, other, tag);
        }
        refreshTag(player);
        update(player);
    }

    public void forget(Player player) {
        boards.remove(player.getUniqueId());
        scoreboards.remove(player.getUniqueId());
        tags.remove(player.getUniqueId());
        for (Scoreboard board : scoreboards.values()) {
            org.bukkit.scoreboard.Team team = board.getTeam(teamName(player));
            if (team != null) team.unregister();
            org.bukkit.scoreboard.Objective below = board.getObjective(BELOW);
            if (below != null) below.getScore(player.getName()).resetScore();
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshTag(player);
            update(player);
        }
    }

    // ---------------------------------------------------------------- over every head: rank, name in the rank's colour, kit

    private static final String BELOW = "ctfkit";

    /** What stands over one player's head: the rank before the name, the name's colour, the kit line under it. */
    private record Tag(Component prefix, net.kyori.adventure.text.format.NamedTextColor colour, Component kit) {}

    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
    private final Map<UUID, Tag> tags = new HashMap<>();

    private static String teamName(Player player) {
        return "ctf" + player.getUniqueId().toString().replace("-", "").substring(0, 13);
    }

    /** Works out a player's tag again and, if it has changed, puts it on everyone's board. */
    public void refreshTag(Player player) {
        Messages messages = plugin.messages();
        String rank = plugin.chat().prefix(player);
        Component prefix = rank.isEmpty() ? Component.empty() : messages.parse(rank + " ");
        var colour = net.kyori.adventure.text.format.NamedTextColor.nearestTo(plugin.chat().rankColour(player, rank));
        com.sasha.ctf.kit.Kit chosen = plugin.kits().selected(player.getUniqueId());
        Component kit = chosen == null ? messages.component("hub.tag.kit-none") : messages.component("hub.tag.kit", Messages.arg("kit", chosen.display()));
        Tag tag = new Tag(prefix, colour, kit);
        if (tag.equals(tags.put(player.getUniqueId(), tag))) return;
        for (Scoreboard board : scoreboards.values()) apply(board, player, tag);
    }

    private void apply(Scoreboard board, Player player, Tag tag) {
        org.bukkit.scoreboard.Team team = board.getTeam(teamName(player));
        if (team == null) team = board.registerNewTeam(teamName(player));
        team.prefix(tag.prefix());
        team.color(tag.colour());
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
        org.bukkit.scoreboard.Objective below = board.getObjective(BELOW);
        if (below == null) {
            below = board.registerNewObjective(BELOW, org.bukkit.scoreboard.Criteria.DUMMY, Component.empty());
            below.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.BELOW_NAME);
            below.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }
        org.bukkit.scoreboard.Score score = below.getScore(player.getName());
        score.setScore(0);
        score.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.fixed(tag.kit()));
    }

    public void update(Player player) {
        Sidebar sidebar = boards.get(player.getUniqueId());
        if (sidebar == null) return;
        Messages messages = plugin.messages();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(hub.paused() && hub.phase() == HubManager.Phase.WAITING ? messages.component("hub.sidebar.paused") : switch (hub.phase()) {
            case WAITING -> messages.component("hub.sidebar.waiting");
            case COUNTDOWN -> messages.component("hub.sidebar.countdown", Messages.arg("seconds", Math.max(0, hub.secondsLeft())));
            case STARTING -> messages.component("hub.sidebar.starting");
            case IN_MATCH -> messages.component("hub.sidebar.in-match");
        });
        lines.add(Component.empty());
        if (hub.phase() == HubManager.Phase.IN_MATCH || hub.phase() == HubManager.Phase.STARTING) {
            MatchRow row = hub.row();
            lines.add(messages.component("hub.sidebar.match-map", Messages.arg("map", hub.mapName(row.map()))));
            if (row.mode() != null) lines.add(messages.component("hub.sidebar.match-mode", Messages.arg("mode", row.mode().display())));
            if (row.score() != null && !row.score().isBlank()) lines.add(messages.parse(row.score()));
        } else if (hub.locked() && hub.chosen() != null) {          // the vote has closed: what is about to be played
            lines.add(messages.component("hub.sidebar.match-map", Messages.arg("map", hub.chosen().mapName())));
            lines.add(messages.component("hub.sidebar.match-mode", Messages.arg("mode", hub.chosen().mode().display())));
            lines.add(messages.component("hub.sidebar.gate-open"));
        } else if (!hub.options().isEmpty()) {
            lines.add(messages.component("hub.sidebar.vote-header"));
            Integer mine = hub.voteOf(player.getUniqueId());
            for (int i = 0; i < hub.options().size(); i++) {
                VoteOption option = hub.options().get(i);
                lines.add(messages.component(mine != null && mine == i ? "hub.sidebar.vote-line-mine" : "hub.sidebar.vote-line",
                        Messages.arg("number", i + 1), Messages.arg("map", option.mapName()), Messages.arg("votes", hub.votesFor(i))));
            }
        } else {
            lines.add(messages.component("hub.sidebar.no-maps"));
        }
        lines.add(Component.text(" "));
        com.sasha.ctf.kit.Kit chosen = plugin.kits().selected(player.getUniqueId());
        lines.add(chosen == null ? messages.component("hub.sidebar.kit-none") : messages.component("hub.sidebar.kit", Messages.arg("kit", chosen.display())));
        lines.add(messages.component("hub.sidebar.coins", Messages.arg("coins", plugin.kits().coins(player.getUniqueId()))));
        lines.add(Component.text("  "));
        lines.add(messages.component("hub.sidebar.footer"));
        sidebar.lines(lines);
    }
}
