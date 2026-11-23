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
        /** The label over the flag: whose it is and where it is. */
        org.bukkit.entity.TextDisplay label;
        /** A thin beam of the team's colour up from the flag (or its carrier), seen from across the map. */
        org.bukkit.entity.BlockDisplay beam;
        /** An invisible marker that puts the flag on everyone's locator bar (the dots over the XP bar). */
        org.bukkit.entity.ArmorStand waypoint;

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

    private org.bukkit.scheduler.BukkitTask follow;

    @Override
    public void start() {
        for (Flag flag : flags.values()) {
            Block block = flag.home.block(match.world());
            flag.homeBlock = block.getBlockData();
            placeBanner(flag, flag.home);
        }
        // The beams and locator-bar markers follow the flags (and their carriers) smoothly.
        follow = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (match.phase() == Match.Phase.OVER || plugin.getServer().getWorld(match.world().getUID()) == null) {
                stopMarkers();
                return;
            }
            for (Flag flag : flags.values()) marker(flag);
        }, 1L, 2L);
    }

    /** The middle of the map, from the flags: each banner faces it, so it is seen face-on by whoever comes for it. */
    private Location middle() {
        double x = 0, z = 0;
        for (Flag flag : flags.values()) {
            x += flag.home.x() + 0.5;
            z += flag.home.z() + 0.5;
        }
        return new Location(match.world(), x / flags.size(), 0, z / flags.size());
    }

    private static final org.bukkit.block.BlockFace[] ROTATIONS = {
            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.SOUTH_SOUTH_WEST, org.bukkit.block.BlockFace.SOUTH_WEST, org.bukkit.block.BlockFace.WEST_SOUTH_WEST,
            org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.WEST_NORTH_WEST, org.bukkit.block.BlockFace.NORTH_WEST, org.bukkit.block.BlockFace.NORTH_NORTH_WEST,
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.NORTH_NORTH_EAST, org.bukkit.block.BlockFace.NORTH_EAST, org.bukkit.block.BlockFace.EAST_NORTH_EAST,
            org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.EAST_SOUTH_EAST, org.bukkit.block.BlockFace.SOUTH_EAST, org.bukkit.block.BlockFace.SOUTH_SOUTH_EAST};

    /** Which way a banner at {@code pos} faces to look at the middle of the map (to the nearest of its 16 turns). */
    static org.bukkit.block.BlockFace facing(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX, dz = toZ - fromZ;
        if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) return org.bukkit.block.BlockFace.SOUTH;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));            // Minecraft's yaw: 0 south, 90 west
        int index = (int) Math.round(yaw / 22.5);
        return ROTATIONS[((index % 16) + 16) % 16];
    }

    /** Puts the team's banner at a spot, turned to face the middle of the map. */
    private void placeBanner(Flag flag, BlockPos pos) {
        Block block = pos.block(match.world());
        BlockData data = flag.team.banner().createBlockData();
        if (data instanceof org.bukkit.block.data.Rotatable rotatable) {
            Location mid = middle();
            rotatable.setRotation(facing(pos.x() + 0.5, pos.z() + 0.5, mid.getX(), mid.getZ()));
        }
        block.setBlockData(data, false);
    }

    /** Where a flag is right now: its banner, or its carrier. */
    private Location whereNow(Flag flag) {
        if (flag.state == State.CARRIED) {
            Player carrier = plugin.getServer().getPlayer(flag.carrier);
            if (carrier != null && carrier.getWorld().equals(match.world())) return carrier.getLocation().add(0, 2.1, 0);
        }
        BlockPos at = flag.at() == null ? flag.home : flag.at();
        return at.center(match.world()).add(0, 0.6, 0);
    }

    /** Keeps the flag's beam and its locator-bar marker where the flag is. */
    private void marker(Flag flag) {
        org.bukkit.World world = match.world();
        Location at = whereNow(flag);
        if (plugin.getConfig().getBoolean("game.flag-beam", true)) {
            Location beamAt = at.clone();
            beamAt.setYaw(0);
            beamAt.setPitch(0);
            if (flag.beam == null || !flag.beam.isValid()) {
                flag.beam = world.spawn(beamAt, org.bukkit.entity.BlockDisplay.class, display -> {
                    display.setPersistent(false);
                    display.setBlock(Material.valueOf(flag.team.wool().name().replace("_WOOL", "_STAINED_GLASS")).createBlockData());
                    float height = (float) plugin.getConfig().getDouble("game.flag-beam-height", 48);
                    display.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-0.12f, 1.4f, -0.12f),
                            new org.joml.Quaternionf(), new org.joml.Vector3f(0.24f, height, 0.24f), new org.joml.Quaternionf()));
                    display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                    display.setViewRange(8f);
                    display.setTeleportDuration(2);
                    display.setGlowing(true);
                    display.setGlowColorOverride(flag.team.leather());
                });
            } else if (flag.beam.getLocation().distanceSquared(beamAt) > 0.0025) {
                flag.beam.teleport(beamAt);
            }
        }
        if (plugin.getConfig().getBoolean("game.flag-locator", true)) {
            if (flag.waypoint == null || !flag.waypoint.isValid()) {
                flag.waypoint = world.spawn(at, org.bukkit.entity.ArmorStand.class, stand -> {
                    stand.setPersistent(false);
                    stand.setInvisible(true);
                    stand.setMarker(true);
                    stand.setGravity(false);
                    stand.setInvulnerable(true);
                    stand.setSilent(true);
                    stand.addScoreboardTag("ctf-flag-waypoint");
                    var range = stand.getAttribute(org.bukkit.attribute.Attribute.WAYPOINT_TRANSMIT_RANGE);
                    if (range != null) range.setBaseValue(60_000_000);
                });
                // the dot in the team's colour
                String color = switch (flag.team) {
                    case RED -> "red";
                    case BLUE -> "blue";
                    case GREEN -> "green";
                    case YELLOW -> "yellow";
                };
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        "waypoint modify " + flag.waypoint.getUniqueId() + " color " + color);
            } else if (flag.waypoint.getLocation().distanceSquared(at) > 0.04) {
                flag.waypoint.teleport(at);
            }
        }
    }

    private void stopMarkers() {
        if (follow != null) follow.cancel();
        follow = null;
        for (Flag flag : flags.values()) {
            if (flag.beam != null) flag.beam.remove();
            if (flag.waypoint != null) flag.waypoint.remove();
            flag.beam = null;
            flag.waypoint = null;
        }
    }

    /** Keeps each flag's label over where the flag is (over its stand while it is carried), saying what is going on. */
    private void label(Flag flag) {
        org.bukkit.World world = match.world();
        BlockPos at = flag.at() == null ? flag.home : flag.at();
        Location where = at.center(world).add(0, 2.2, 0);
        if (flag.label == null || !flag.label.isValid()) {
            flag.label = world.spawn(where, org.bukkit.entity.TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                display.setShadowed(true);
                display.setBackgroundColor(org.bukkit.Color.fromARGB(90, 0, 0, 0));
                display.setLineWidth(400);
                display.setViewRange(1.2f);
                display.setTeleportDuration(5);
            });
        } else if (flag.label.getLocation().distanceSquared(where) > 0.01) {
            flag.label.teleport(where);
        }
        var messages = plugin.messages();
        net.kyori.adventure.text.Component title = messages.component("flag.label.title", Messages.comp("team", Match.teamName(flag.team)));
        net.kyori.adventure.text.Component status = switch (flag.state) {
            case HOME -> messages.component("flag.label.home");
            case CARRIED -> {
                Player carrier = plugin.getServer().getPlayer(flag.carrier);
                TeamColor team = carrier == null ? null : match.teamOf(carrier.getUniqueId());
                yield messages.component("flag.label.carried", Messages.comp("player", carrier == null
                        ? net.kyori.adventure.text.Component.text("?") : name(carrier, team)));
            }
            case DROPPED -> messages.component("flag.label.dropped", Messages.arg("seconds",
                    Math.max(0, plugin.ctfConfig().flagReturn() - (System.currentTimeMillis() - flag.droppedAt) / 1000)));
        };
        net.kyori.adventure.text.Component text = title.append(net.kyori.adventure.text.Component.newline()).append(status);
        if (!text.equals(flag.label.text())) flag.label.text(text);
    }

    @Override
    public void tick() {
        long returnAfter = plugin.ctfConfig().flagReturn() * 1000L;
        for (Flag flag : flags.values()) label(flag);
        // Players aren't on the locator bar in CTF: its dots are the flags.
        if (plugin.getConfig().getBoolean("game.flag-locator", true)) {
            for (Player player : match.world().getPlayers()) {
                var range = player.getAttribute(org.bukkit.attribute.Attribute.WAYPOINT_TRANSMIT_RANGE);
                if (range != null && range.getBaseValue() != 0) range.setBaseValue(0);
            }
        }
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
        match.stats(player.getUniqueId()).flagPickups++;
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
            plugin.messages().send(owner, "flag.your-flag-stolen", Messages.comp("player", name(player, team)));
            owner.playSound(owner.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.6f);
        }
        label(flag);
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
        match.captureFireworks(player.getLocation(), team);
        for (Flag each : flags.values()) label(each);
        if (total >= match.map().captureLimit) {
            for (Flag each : flags.values()) if (each.label != null) each.label.remove();
            stopMarkers();
            match.finish(team, null);
        }
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
        placeBanner(flag, drop);
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
        placeBanner(flag, flag.home);
    }

    /** Puts back whatever the banner replaced where it stands now. */
    private void restoreBlock(Flag flag) {
        if (flag.state == State.HOME) {
            flag.home.block(match.world()).setBlockData(flag.homeBlock == null ? Material.AIR.createBlockData() : flag.homeBlock, false);
        } else if (flag.state == State.DROPPED && flag.dropped != null) {
            flag.dropped.block(match.world()).setBlockData(flag.droppedBlock == null ? Material.AIR.createBlockData() : flag.droppedBlock, false);
        }
    }

    @Override
    public boolean carrying(Player player) {
        return carriedBy(player.getUniqueId()) != null;
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
