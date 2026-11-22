/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

/**
 * The sidebar on a scoreboard. Each line is its own entry ("l0", "l1"...) shown through its display name,
 * so identical lines (blank spacers) never collide, also on Bedrock.
 */
public final class Sidebar {

    private static final int MAX = 15;
    private static final net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer PLAIN =
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();

    private final Objective objective;
    private int shown;

    public Sidebar(Scoreboard board, Component title) {
        Objective old = board.getObjective("ctf_sidebar");
        if (old != null) old.unregister();
        this.objective = board.registerNewObjective("ctf_sidebar", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
    }

    public void title(Component title) {
        objective.displayName(title);
    }

    public void lines(List<Component> lines) {
        int count = Math.min(MAX, lines.size());
        for (int i = 0; i < count; i++) {
            Score score = objective.getScore("l" + i);
            score.setScore(count - i);
            Component line = lines.get(i);
            // Bedrock drops blank lines: give each spacer its own invisible colour codes instead
            if (PLAIN.serialize(line).isBlank()) line = Component.text("\u00a7" + Integer.toHexString(i % 16) + "\u00a7r" + (i >= 16 ? "\u00a7r" : ""));
            score.customName(line);
        }
        for (int i = count; i < shown; i++) objective.getScoreboard().resetScores("l" + i);
        shown = count;
    }
}
