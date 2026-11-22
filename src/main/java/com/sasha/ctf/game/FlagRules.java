/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Capture the Flag and 4-Team CTF. Each team's flag is a banner on its stand. Touch an enemy flag to take
 * it (it goes on your head and you glow); bring it to your own stand while your own flag is there to
 * capture it. A carrier who dies drops the flag: the owners touch it to send it home, enemies touch it to
 * take it, and it goes home by itself after a while.
 */
final class FlagRules implements Rules {

    enum State { HOME, CARRIED, DROPPED }

    final class Flag {
        final TeamColor team;
        final BlockPos home;
        BlockData homeBlock;
        State state = State.HOME;
        UUID carrier;
        /** What the carrier wore on their head before the banner went there. */
        ItemStack carrierHelmet;
        BlockPos dropped;
        BlockData droppedBlock;
        long droppedAt;

        Flag(TeamColor team, BlockPos home) {
            this.team = team;
            this.home = home;
        }

        /** Where the banner stands right now (home or dropped), or null while carried. */
        BlockPos at() {
            return switch (state) {
                case HOME -> home;
                case DROPPED -> dropped;
                case CARRIED -> null;
            };
        }
    }

    private final FractalCTF plugin;
    private final Match match;
    private final Map<TeamColor, Flag> flags = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Integer> captures = new EnumMap<>(TeamColor.class);

    FlagRules(FractalCTF plugin, Match match) {
        this.plugin = plugin;
        this.match = match;
        for (TeamColor team : match.mode().teams()) {
            flags.put(team, new Flag(team, match.map().teams.get(team).flag));
            captures.put(team, 0);
        }
    }

    @Override
    public void start() {
        for (Flag flag : flags.values()) {
            Block block = flag.home.block(match.world());
            flag.homeBlock = block.getBlockData();
            block.setType(flag.team.banner(), false);
        }
    }

    @Override
    public void tick() {
        long returnAfter = plugin.ctfConfig().flagReturn() * 1000L;
        for (Flag flag : flags.values()) {
            if (flag.state == State.DROPPED && System.currentTimeMillis() - flag.droppedAt > returnAfter) {
                sendHome(flag);
                match.broadcast("flag.returned-auto", Messages.comp("flag", flagName(flag.team)));
            }
            if (flag.state == State.CARRIED) {
                Player carrier = plugin.getServer().getPlayer(flag.carrier);
                if (carrier != null) {
                    plugin.messages().actionBar(carrier, "flag.carrying", Messages.comp("flag", flagName(flag.team)));
                    match.world().spawnParticle(Particle.DUST, carrier.getLocation().add(0, 2.3, 0), 6, 0.2, 0.2, 0.2,
                            new Particle.DustOptions(flag.team.leather(), 1.4f));
                }
            }
            BlockPos at = flag.at();
            if (at != null) {
                match.world().spawnParticle(Particle.DUST, at.center(match.world()).add(0, 1.2, 0), 4, 0.3, 0.6, 0.3,
                        new Particle.DustOptions(flag.team.leather(), 1.2f));
            }
        }
    }

    @Override
    public void onMove(Player player, Location to) {
        TeamColor team = match.teamOf(player.getUniqueId());
        if (team == null) return;
        Flag carried = carriedBy(player.getUniqueId());
        for (Flag flag : flags.values()) {
            BlockPos at = flag.at();
            if (at == null || !at.touching(to)) continue;
            if (flag.team == team) {
                if (flag.state == State.DROPPED) {
                    sendHome(flag);
                    match.broadcast("flag.returned", Messages.comp("player", name(player, team)), Messages.comp("flag", flagName(flag.team)));
                    sound(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f);
                } else if (flag.state == State.HOME && carried != null) {
                    capture(player, team, carried);
                    return;
                }
            } else if (carried == null && flag.state != State.CARRIED) {
                pickUp(player, team, flag);
                return;
            }
        }
    }

    private void pickUp(Player player, TeamColor team, Flag flag) {
        restoreBlock(flag);
        flag.state = State.CARRIED;
        flag.carrier = player.getUniqueId();
        ItemStack helmet = player.getInventory().getHelmet();
        flag.carrierHelmet = helmet == null ? null : helmet.clone();
        player.getInventory().setHelmet(Items.tag(ItemStack.of(flag.team.banner()), "flag"));
        refresh(player);
        player.setGlowing(true);
        match.broadcast("flag.taken", Messages.comp("player", name(player, team)), Messages.comp("flag", flagName(flag.team)));
        sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f);
        for (Player owner : match.online(flag.team)) {
            plugin.messages().title(owner, "flag.your-flag-taken", 0, 30, 10, Messages.comp("player", name(player, team)));
        }
    }

    private void capture(Player player, TeamColor team, Flag carried) {
        int total = captures.merge(team, 1, Integer::sum);
        match.stats(player.getUniqueId()).captures++;
        sendHome(carried);
        match.award(player, plugin.ctfConfig().coins().objective(), "capture", Messages.comp("team", Match.teamName(carried.team)));
        match.broadcast("flag.captured", Messages.comp("player", name(player, team)), Messages.comp("flag", flagName(carried.team)),
                Messages.arg("captures", total), Messages.arg("limit", match.map().captureLimit));
        for (Player everyone : match.world().getPlayers()) {
            plugin.messages().title(everyone, "flag.capture-title", 0, 40, 10, Messages.comp("team", Match.teamName(team)),
                    Messages.arg("captures", total), Messages.arg("limit", match.map().captureLimit));
        }
        sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
        match.firework(player.getLocation(), team);
        if (total >= match.map().captureLimit) match.finish(team, null);
    }

    @Override
    public void onDeathOrQuit(Player player, Location where) {
        Flag flag = carriedBy(player.getUniqueId());
        if (flag == null) return;
        player.setGlowing(false);
        flag.carrierHelmet = null; // they respawn with a fresh kit
        BlockPos drop = dropSpot(where);
        if (drop == null) {
            sendHome(flag);
            match.broadcast("flag.returned-auto", Messages.comp("flag", flagName(flag.team)));
            return;
        }
        Block block = drop.block(match.world());
        flag.droppedBlock = block.getBlockData();
        block.setType(flag.team.banner(), false);
        flag.state = State.DROPPED;
        flag.dropped = drop;
        flag.droppedAt = System.currentTimeMillis();
        flag.carrier = null;
        TeamColor team = match.teamOf(player.getUniqueId());
        match.broadcast("flag.dropped", Messages.comp("player", name(player, team)), Messages.comp("flag", flagName(flag.team)),
                Messages.arg("seconds", plugin.ctfConfig().flagReturn()));
    }

    /** The nearest air block above solid ground at or just below where the carrier died; null if there's none (void, lava). */
    private BlockPos dropSpot(Location where) {
        if (where.getY() < match.map().voidY + 2) return null;
        Block block = where.getBlock();
        for (int i = 0; i < 12 && block.getY() > match.world().getMinHeight(); i++) {
            Block below = block.getRelative(0, -1, 0);
            if (block.getType().isAir() && below.getType().isSolid()) {
                BlockPos pos = BlockPos.of(block);
                for (Flag other : flags.values()) if (pos.equals(other.at())) return null;
                return pos;
            }
            if (block.isLiquid()) return null;
            block = below;
        }
        return null;
    }

    private void sendHome(Flag flag) {
        if (flag.state == State.CARRIED) {
            Player carrier = plugin.getServer().getPlayer(flag.carrier);
            if (carrier != null && match.world().equals(carrier.getWorld()) && !carrier.isDead()) {
                carrier.setGlowing(false);
                carrier.getInventory().setHelmet(flag.carrierHelmet);
                refresh(carrier);
            }
            flag.carrierHelmet = null;
        }
        restoreBlock(flag);
        flag.state = State.HOME;
        flag.carrier = null;
        flag.dropped = null;
        flag.home.block(match.world()).setType(flag.team.banner(), false);
    }

    /** Puts back whatever the banner replaced where it stands now. */
    private void restoreBlock(Flag flag) {
        if (flag.state == State.HOME) {
            flag.home.block(match.world()).setBlockData(flag.homeBlock == null ? Material.AIR.createBlockData() : flag.homeBlock, false);
        } else if (flag.state == State.DROPPED && flag.dropped != null) {
            flag.dropped.block(match.world()).setBlockData(flag.droppedBlock == null ? Material.AIR.createBlockData() : flag.droppedBlock, false);
        }
    }

    Flag carriedBy(UUID player) {
        for (Flag flag : flags.values()) if (flag.state == State.CARRIED && player.equals(flag.carrier)) return flag;
        return null;
    }

    /** Whether a block is a flag banner (home or dropped): it can't be broken. */
    boolean isFlag(Block block) {
        BlockPos pos = BlockPos.of(block);
        for (Flag flag : flags.values()) if (pos.equals(flag.at())) return true;
        return false;
    }

    @Override
    public Result onBreak(Player player, Block block) {
        return isFlag(block) ? Result.DENY : Result.PASS;
    }

    @Override
    public List<BlockPos> objectiveBlocks() {
        List<BlockPos> blocks = new ArrayList<>();
        for (Flag flag : flags.values()) blocks.add(flag.home);
        return blocks;
    }

    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        for (Flag flag : flags.values()) {
            String status = switch (flag.state) {
                case HOME -> "flag.status-home";
                case CARRIED -> "flag.status-carried";
                case DROPPED -> "flag.status-dropped";
            };
            lines.add(plugin.messages().component("flag.sidebar-line", Messages.comp("team", Match.teamName(flag.team)),
                    Messages.arg("captures", captures.get(flag.team)), Messages.arg("limit", match.map().captureLimit),
                    Messages.comp("status", plugin.messages().component(status))));
        }
        return lines;
    }

    @Override
    public String scoreLine() {
        List<String> parts = new ArrayList<>();
        for (TeamColor team : flags.keySet()) parts.add("<" + team.id() + ">" + team.display() + " " + captures.get(team) + "</" + team.id() + ">");
        return String.join(" <dark_gray>-</dark_gray> ", parts);
    }

    @Override
    public TeamColor leader() {
        TeamColor best = null;
        int bestScore = -1;
        boolean tie = false;
        for (Map.Entry<TeamColor, Integer> entry : captures.entrySet()) {
            if (entry.getValue() > bestScore) {
                best = entry.getKey();
                bestScore = entry.getValue();
                tie = false;
            } else if (entry.getValue() == bestScore) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    private Component flagName(TeamColor team) {
        return plugin.messages().component("flag.name", Messages.comp("team", Match.teamName(team)));
    }

    private static Component name(Player player, TeamColor team) {
        return team == null ? player.name() : Component.text(player.getName(), team.text());
    }

    /** Re-sends the player's inventory and armour next tick, so a changed helmet always shows up right. */
    private void refresh(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.getInventory().setArmorContents(player.getInventory().getArmorContents());
            player.updateInventory();
        });
    }

    private void sound(Sound sound, float pitch) {
        for (Player player : match.world().getPlayers()) player.playSound(player.getLocation(), sound, 1f, pitch);
    }
}
