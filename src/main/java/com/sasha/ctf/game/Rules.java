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

    /** A player died (or left) while on a team: drop what they carry. */
    default void onDeathOrQuit(Player player, Location where) {}

    /** Blocks that are part of the objective and nobody may build on or next to (flag stands, wool). */
    List<com.sasha.ctf.model.BlockPos> objectiveBlocks();

    /** The team lines on the sidebar. */
    List<Component> lines();

    /** A one-line score for the hub, as MiniMessage. */
    String scoreLine();

    /** Who's ahead when time runs out; null for a draw. */
    TeamColor leader();

    enum Result { ALLOW, DENY, PASS }
}
