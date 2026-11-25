/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.model.TeamColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/** What makes each mode different: its objective, how points are scored and who's winning. */
interface Rules {

    /** Builds what only this mode needs (wool towers, the hill's beacon) as soon as the map is loaded. */
    default void prepare() {}

    /** Sets the objective up in the fresh world (flags placed, wool coloured...). */
    void start();

    /** Once a second while the match is on. */
    void tick();

    /** A player moved (only called for players on a team who are alive). */
    default void onMove(Player player, Location to) {}

    /**
     * A player is breaking a block. Return {@link Result#ALLOW} or {@link Result#DENY} to decide it here,
     * or {@link Result#PASS} for the normal "only blocks players placed" rule.
     */
    default Result onBreak(Player player, Block block) {
        return Result.PASS;
    }

    /** A player clicked a block; true = handled here (the click does nothing else). */
    default boolean onInteract(Player player, Block block) {
        return false;
    }

    /** Whether a block breaks at the first hit for this player (Destroy the Monument's obsidian). */
    default boolean instantBreak(Player player, Block block) {
        return false;
    }

    /**
     * A player is placing a block. {@link Result#ALLOW} lets it through even near an objective (a wool on its
     * podium), {@link Result#DENY} stops it, {@link Result#PASS} for the normal building rules.
     */
    default Result onPlace(Player player, Block block, org.bukkit.inventory.ItemStack item) {
        return Result.PASS;
    }

    /** Whether the player carries something that a team swap would lose (a flag). */
    default boolean carrying(Player player) {
        return false;
    }

    /** A player died (or left) while on a team: drop what they carry. */
    default void onDeathOrQuit(Player player, Location where) {}

    /** Blocks that are part of the objective and nobody may build on or next to (flag stands, wool). */
    List<com.sasha.ctf.model.BlockPos> objectiveBlocks();

    /** The team lines on the sidebar. */
    List<Component> lines();

    /** A one-line score for the hub, as MiniMessage. */
    /** A block whose break should drop nothing (Capture the Wool: decorative wool in an objective's colour). */
    default boolean noDrops(org.bukkit.block.Block block) {
        return false;
    }

    String scoreLine();

    /** The score for the end screen, one line per team (MiniMessage). By default the score line split up. */
    default java.util.List<String> endLines() {
        return java.util.Arrays.asList(scoreLine().split(" <dark_gray>-</dark_gray> "));
    }

    /** Who's ahead when time runs out; null for a draw. */
    TeamColor leader();

    enum Result { ALLOW, DENY, PASS }
}
