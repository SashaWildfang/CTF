package com.bearnation.ctf.tablist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Objects;

public final class TablistManager {

    public enum Role { RED, BLUE, SPECTATOR }

    private final Scoreboard board;

    // Team names choose order in tablist (lexicographic)
    private static final String TEAM_RED  = "00_red";
    private static final String TEAM_BLUE = "01_blue";
    private static final String TEAM_SPEC = "zz_spec";

    private Component header = Component.empty();
    private final Component footer = Component.text("BEAR NATION - play.mcbearnation.net",
            NamedTextColor.WHITE).decorate(TextDecoration.BOLD);

    public TablistManager() {
        this.board = Bukkit.getScoreboardManager().getMainScoreboard();
        ensureTeams();
    }

    private void ensureTeams() {
        makeTeam(TEAM_RED,  NamedTextColor.RED);
        makeTeam(TEAM_BLUE, NamedTextColor.BLUE);
        makeTeam(TEAM_SPEC, NamedTextColor.AQUA); // light blue spectators
    }

    private Team makeTeam(String name, NamedTextColor color) {
        Team t = board.getTeam(name);
        if (t == null) t = board.registerNewTeam(name);
        t.color(color);
        t.setAllowFriendlyFire(false);
        t.setCanSeeFriendlyInvisibles(true);
        return t;
    }

    /* ---------------- Header / Footer ---------------- */

    /** Example output: "Foliage by Preinstalled" (aqua/gray/aqua) */
    public void setMapHeader(String mapName, String author) {
        this.header = Component.text(mapName, NamedTextColor.AQUA)
                .append(Component.text(" by ", NamedTextColor.GRAY))
                .append(Component.text(author, NamedTextColor.AQUA));
        applyHeaderFooterAll();
    }

    public void applyHeaderFooterAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    public void applyHeaderFooter(Player p) {
        p.sendPlayerListHeaderAndFooter(header, footer);
    }

    /* ---------------- Team / Role handling ---------------- */

    public void setRole(Player p, Role role) {
        removeFromAllTeams(p);
        switch (role) {
            case RED -> addToTeam(p, TEAM_RED, NamedTextColor.RED);
            case BLUE -> addToTeam(p, TEAM_BLUE, NamedTextColor.BLUE);
            case SPECTATOR -> addToTeam(p, TEAM_SPEC, NamedTextColor.AQUA);
        }
    }

    private void addToTeam(Player p, String teamName, NamedTextColor color) {
        Team t = Objects.requireNonNull(board.getTeam(teamName));
        t.addEntry(p.getName());
        p.playerListName(Component.text(p.getName(), color));
        if (p.getScoreboard() != board) p.setScoreboard(board);
    }

    private void removeFromAllTeams(Player p) {
        for (Team t : board.getTeams()) t.removeEntry(p.getName());
    }

    /** Re-applies tab name color & scoreboard after any big shuffle */
    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (board.getTeam(TEAM_SPEC).hasEntry(p.getName())) {
                p.playerListName(Component.text(p.getName(), NamedTextColor.AQUA));
            } else if (board.getTeam(TEAM_RED).hasEntry(p.getName())) {
                p.playerListName(Component.text(p.getName(), NamedTextColor.RED));
            } else if (board.getTeam(TEAM_BLUE).hasEntry(p.getName())) {
                p.playerListName(Component.text(p.getName(), NamedTextColor.BLUE));
            }
            if (p.getScoreboard() != board) p.setScoreboard(board);
        }
    }
}
