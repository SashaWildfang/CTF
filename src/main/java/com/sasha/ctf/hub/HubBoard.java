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
        player.setScoreboard(board);
        update(player);
    }

    public void forget(Player player) {
        boards.remove(player.getUniqueId());
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    public void update(Player player) {
        Sidebar sidebar = boards.get(player.getUniqueId());
        if (sidebar == null) return;
        Messages messages = plugin.messages();
        int online = Bukkit.getOnlinePlayers().size();
        int min = plugin.ctfConfig().minPlayers();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(messages.component("hub.sidebar.players", Messages.arg("players", online)));
        lines.add(hub.paused() && hub.phase() == HubManager.Phase.WAITING ? messages.component("hub.sidebar.paused") : switch (hub.phase()) {
            case WAITING -> messages.component("hub.sidebar.waiting", Messages.arg("needed", Math.max(1, min - online)));
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
        } else if (!hub.options().isEmpty()) {
            lines.add(messages.component("hub.sidebar.vote-header"));
            Integer mine = hub.voteOf(player.getUniqueId());
            for (int i = 0; i < hub.options().size(); i++) {
                VoteOption option = hub.options().get(i);
                lines.add(messages.component(mine != null && mine == i ? "hub.sidebar.vote-line-mine" : "hub.sidebar.vote-line",
                        Messages.arg("number", i + 1), Messages.arg("map", option.mapName()), Messages.arg("votes", hub.votesFor(i)),
                        Messages.arg("rating", hub.ratingText(option.mapId()))));
            }
        } else {
            lines.add(messages.component("hub.sidebar.no-maps"));
        }
        lines.add(Component.text(" "));
        lines.add(messages.component("hub.sidebar.kit", Messages.arg("kit", plugin.kits().of(player.getUniqueId()).display())));
        lines.add(messages.component("hub.sidebar.coins", Messages.arg("coins", plugin.kits().coins(player.getUniqueId()))));
        lines.add(Component.text("  "));
        lines.add(messages.component("hub.sidebar.footer"));
        sidebar.lines(lines);
    }
}
