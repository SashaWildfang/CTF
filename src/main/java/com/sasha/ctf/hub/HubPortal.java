/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

/**
 * The Match Gate's portal. Closed, it is the wall of glass and crystal the lobby was built with. When a match is
 * about to start it breaks open from the middle outward (every block bursts into its own shards) onto a swirling
 * portal surface, and stays open while there is a match to walk into: stepping through sends you there. When the
 * match is over it seals again. The surface is display entities only: it has no collision and is never saved.
 * Where it is comes from `portal:` in hub-npcs.yml (a box; everything in it that isn't obsidian is the portal).
 * What it is made of is read from the world each time it opens and kept in portal-fill.yml only while it is open,
 * so a restart or a crash while it is open still closes it properly, and a gate rebuilt by hand is taken as it is.
 */
final class HubPortal {

    private final FractalCTF plugin;
    private final HubManager hub;
    private final Random random = new Random();
    /** The portal's blocks, in the order they break: nearest the middle first. */
    private final Map<Location, BlockData> fill = new LinkedHashMap<>();
    private final ArrayDeque<Location> breaking = new ArrayDeque<>();
    /** The swirling surface shown while it is open: one display per block of the portal's middle plane. */
    private final List<org.bukkit.entity.Entity> surface = new ArrayList<>();
    private static final String SURFACE_TAG = "ctfportal";
    private World world;
    private int x1, y1, z1, x2, y2, z2;
    private boolean defined, open;
    private BukkitTask task;
    private int ticks;

    HubPortal(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    void start() {
        load();
        readFill();
        restore();                                                   // stopped while it was open: it starts closed
        for (org.bukkit.entity.Entity stale : world.getEntitiesByClass(org.bukkit.entity.BlockDisplay.class)) {
            if (stale.getScoreboardTags().contains(SURFACE_TAG)) stale.remove();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    void stop() {
        if (task != null) task.cancel();
        seal();
    }

    /** hub-npcs.yml or the world has changed: closed, and where it is read again. */
    void reloadFile() {
        seal();
        load();
    }

    private void clearSurface() {
        for (org.bukkit.entity.Entity entity : surface) entity.remove();
        surface.clear();
    }

    /** One block of the swirling surface, if this block is in the portal's middle plane. */
    private void surface(Location at) {
        boolean alongX = x2 - x1 >= z2 - z1;                         // which way the gate faces
        if (alongX ? at.getBlockZ() != (z1 + z2) / 2 : at.getBlockX() != (x1 + x2) / 2) return;
        BlockData swirl = Bukkit.createBlockData(Material.NETHER_PORTAL, data -> ((org.bukkit.block.data.Orientable) data).setAxis(alongX ? org.bukkit.Axis.X : org.bukkit.Axis.Z));
        surface.add(world.spawn(new Location(world, at.getBlockX(), at.getBlockY(), at.getBlockZ()), org.bukkit.entity.BlockDisplay.class, display -> {
            display.setBlock(swirl);
            display.setPersistent(false);
            display.addScoreboardTag(SURFACE_TAG);
            display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            display.setViewRange(3f);
        }));
    }

    private File file() {
        return new File(plugin.getDataFolder(), "portal-fill.yml");
    }

    private void load() {
        defined = false;
        world = Bukkit.getWorlds().get(0);
        File file = new File(plugin.getDataFolder(), "hub-npcs.yml");
        YamlConfiguration yaml;
        if (file.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            var resource = plugin.getResource("hub-npcs.yml");
            if (resource == null) return;
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        ConfigurationSection box = yaml.getConfigurationSection("portal");
        if (box == null) return;
        x1 = Math.min(box.getInt("x1"), box.getInt("x2"));
        x2 = Math.max(box.getInt("x1"), box.getInt("x2"));
        y1 = Math.min(box.getInt("y1"), box.getInt("y2"));
        y2 = Math.max(box.getInt("y1"), box.getInt("y2"));
        z1 = Math.min(box.getInt("z1"), box.getInt("z2"));
        z2 = Math.max(box.getInt("z1"), box.getInt("z2"));
        defined = true;
    }

    private void readFill() {
        fill.clear();
        if (!defined || !file().isFile()) return;
        for (String line : YamlConfiguration.loadConfiguration(file()).getStringList("blocks")) {
            String[] parts = line.split(";", 2);
            String[] at = parts[0].split(" ");
            try {
                fill.put(new Location(world, Integer.parseInt(at[0]), Integer.parseInt(at[1]), Integer.parseInt(at[2])), Bukkit.createBlockData(parts[1]));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "portal-fill.yml has a line that can't be read: " + line);
            }
        }
    }

    /** Notes what the closed portal is made of, nearest the middle first, and writes it down until it closes again. */
    private void capture() {
        fill.clear();
        double cx = (x1 + x2) / 2.0, cy = (y1 + y2) / 2.0;
        List<Block> blocks = new ArrayList<>();
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) {
            Block block = world.getBlockAt(x, y, z);
            if (portalBlock(block.getType())) blocks.add(block);
        }
        blocks.sort(Comparator.comparingDouble(block -> Math.hypot(block.getX() - cx, (block.getY() - cy) * 0.7)));
        List<String> lines = new ArrayList<>();
        for (Block block : blocks) {
            fill.put(block.getLocation(), block.getBlockData());
            lines.add(block.getX() + " " + block.getY() + " " + block.getZ() + ";" + block.getBlockData().getAsString());
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks", lines);
        try {
            yaml.save(file());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Couldn't write portal-fill.yml", e);
        }
    }

    /**
     * What the portal itself is made of: glass, amethyst and froglights (and anything listed in hub.portal-blocks).
     * Only these break open; the stone of the arch, its frame, lanterns and chains in the box stay where they are.
     */
    private boolean portalBlock(Material type) {
        if (type.isAir() || type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN) return false;
        List<String> listed = plugin.getConfig().getStringList("hub.portal-blocks");
        if (!listed.isEmpty()) return listed.stream().anyMatch(name -> name.equalsIgnoreCase(type.name()) || name.equalsIgnoreCase(type.getKey().getKey()));
        String name = type.name();
        return name.endsWith("STAINED_GLASS") || name.endsWith("STAINED_GLASS_PANE") || type == Material.GLASS || type == Material.GLASS_PANE
                || type == Material.TINTED_GLASS || name.endsWith("_FROGLIGHT") || type == Material.AMETHYST_BLOCK || type == Material.BUDDING_AMETHYST
                || type == Material.NETHER_PORTAL || type == Material.END_GATEWAY || type == Material.MAGENTA_CONCRETE || type == Material.PURPLE_CONCRETE;
    }

    /** Puts every block of the portal back and forgets them: the next opening reads the gate as it then stands. */
    private void restore() {
        for (Map.Entry<Location, BlockData> entry : fill.entrySet()) {
            Block block = entry.getKey().getBlock();
            if (!block.getBlockData().equals(entry.getValue())) block.setBlockData(entry.getValue(), false);
        }
        fill.clear();
        file().delete();
    }

    /** Closed at once and without ceremony (before the lobby is saved or rebuilt). */
    void seal() {
        breaking.clear();
        clearSurface();
        if (open) restore();
        open = false;
    }

    /** Open while there is a match to walk into (or about to be), closed otherwise. */
    void set(boolean want) {
        if (!defined || want == open || !plugin.getConfig().getBoolean("hub.portal-opens", true)) return;
        Location middle = new Location(world, (x1 + x2 + 1) / 2.0, (y1 + y2 + 1) / 2.0, (z1 + z2 + 1) / 2.0);
        if (want) {
            capture();
            if (fill.isEmpty()) return;
            open = true;
            breaking.clear();
            breaking.addAll(fill.keySet());
            world.playSound(middle, Sound.BLOCK_BEACON_ACTIVATE, 3f, 0.7f);
            plugin.messages().broadcast("hub.gate-open");
        } else {
            breaking.clear();
            clearSurface();
            restore();
            open = false;
            world.spawnParticle(Particle.REVERSE_PORTAL, middle, 120, (x2 - x1) / 3.0, (y2 - y1) / 3.0, 0.5, 0.05);
            world.playSound(middle, Sound.BLOCK_BEACON_DEACTIVATE, 3f, 0.8f);
        }
    }

    private void tick() {
        if (!open) return;
        ticks++;
        // the break: a ring of blocks each step, every one bursting into its own shards
        int step = Math.max(4, fill.size() / 18);
        for (int i = 0; i < step && !breaking.isEmpty(); i++) {
            Location at = breaking.poll();
            Block block = at.getBlock();
            BlockData was = fill.get(at);
            if (block.getType().isAir() || was == null) continue;
            block.setType(Material.AIR, false);
            surface(at);
            Location centre = at.clone().add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.BLOCK, centre, 8, 0.3, 0.3, 0.3, was);
            if (i % 3 == 0) world.playSound(centre, i % 2 == 0 ? Sound.BLOCK_GLASS_BREAK : Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.7f + random.nextFloat() * 0.7f);
            if (breaking.isEmpty()) world.playSound(centre, Sound.BLOCK_PORTAL_TRIGGER, 1.5f, 1.2f);       // the last shard: it is open
        }
        if (ticks % 5 == 0) {
            world.spawnParticle(Particle.PORTAL, new Location(world, (x1 + x2 + 1) / 2.0, (y1 + y2 + 1) / 2.0 - 2, (z1 + z2 + 1) / 2.0),
                    60, (x2 - x1) / 3.5, (y2 - y1) / 4.0, 0.6, 0.6);
        }
        if (ticks % 100 == 0) world.playSound(new Location(world, (x1 + x2 + 1) / 2.0, y1 + 2, (z1 + z2 + 1) / 2.0), Sound.BLOCK_PORTAL_AMBIENT, 0.8f, 1f);
        if (!breaking.isEmpty()) return;                             // nobody walks into falling glass
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (at.getX() >= x1 && at.getX() < x2 + 1 && at.getY() >= y1 - 0.5 && at.getY() < y2 + 1 && at.getZ() >= z1 - 0.3 && at.getZ() < z2 + 1.3) hub.enterGate(player);
        }
    }
}
