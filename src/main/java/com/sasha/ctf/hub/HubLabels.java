/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The lobby's floating labels (the title at the spawn, the names over the buildings). They are listed under
 * {@code labels:} in hub-npcs.yml and drawn by the plugin, so changing what one says needs only that file and
 * /regenlobby or a restart, never a new world. Lobby worlds built before 1.10.0 have the labels saved in them:
 * once the file lists labels, those saved ones are taken out so nothing is shown twice. They are told apart by
 * how the lobby builder made them (a line width of 1000), so a text display someone placed by hand is left alone.
 */
final class HubLabels implements Listener {

    private static final String TAG = "ctflabel";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private record Label(double x, double y, double z, float scale, String text) {
        int chunkX() { return (int) Math.floor(x) >> 4; }
        int chunkZ() { return (int) Math.floor(z) >> 4; }
    }

    private final FractalCTF plugin;
    private final List<Label> labels = new ArrayList<>();
    private final Map<Label, UUID> shown = new HashMap<>();
    /** Whether hub-npcs.yml lists the labels (if not, the ones saved in the world are the labels). */
    private boolean listed;

    HubLabels(FractalCTF plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        show();
    }

    void stop() {
        for (UUID id : shown.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        shown.clear();
    }

    /** hub-npcs.yml has changed: the labels are taken down and put up again from it. */
    void reloadFile() {
        stop();
        show();
    }

    private void show() {
        load();
        World world = Bukkit.getWorlds().get(0);
        for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            if (display.getScoreboardTags().contains(TAG) || listed && saved(display)) display.remove();
        }
        for (Label label : labels) if (world.isChunkLoaded(label.chunkX(), label.chunkZ())) spawn(world, label);
    }

    private void load() {
        labels.clear();
        listed = false;
        if (!plugin.getConfig().getBoolean("hub.labels-from-file", true)) return;
        File file = new File(plugin.getDataFolder(), "hub-npcs.yml");
        YamlConfiguration yaml;
        if (file.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            var resource = plugin.getResource("hub-npcs.yml");
            if (resource == null) return;
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        if (!yaml.contains("labels")) return;
        listed = true;
        for (Map<?, ?> raw : yaml.getMapList("labels")) {
            Object text = raw.get("text");
            if (text == null || !(raw.get("x") instanceof Number x) || !(raw.get("y") instanceof Number y) || !(raw.get("z") instanceof Number z)) continue;
            float scale = raw.get("scale") instanceof Number number ? number.floatValue() : 1f;
            labels.add(new Label(x.doubleValue(), y.doubleValue(), z.doubleValue(), scale, String.valueOf(text)));
        }
    }

    /** A label the lobby builder saved into the world. */
    private static boolean saved(Entity entity) {
        return entity instanceof TextDisplay display && display.isPersistent() && display.getLineWidth() == 1000
                && display.getBillboard() == Display.Billboard.CENTER;
    }

    private void spawn(World world, Label label) {
        UUID before = shown.get(label);
        if (before != null && Bukkit.getEntity(before) != null) return;
        shown.put(label, world.spawn(new Location(world, label.x(), label.y(), label.z()), TextDisplay.class, display -> {
            display.text(MINI.deserialize(label.text()));
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.addScoreboardTag(TAG);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setLineWidth(2000);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(0));
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(label.scale(), label.scale(), label.scale()), new AxisAngle4f()));
        }).getUniqueId());
    }

    /** Labels aren't saved with their chunk: they come back whenever it loads. */
    @EventHandler
    public void onChunk(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (world != Bukkit.getWorlds().get(0)) return;
        int x = event.getChunk().getX(), z = event.getChunk().getZ();
        for (Label label : labels) {
            if (label.chunkX() != x || label.chunkZ() != z) continue;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (world.isChunkLoaded(x, z)) spawn(world, label);
            });
        }
    }

    /** A chunk's saved entities have arrived: the builder's old labels among them go. */
    @EventHandler
    public void onEntities(EntitiesLoadEvent event) {
        if (!listed || event.getWorld() != Bukkit.getWorlds().get(0)) return;
        for (Entity entity : event.getEntities()) if (saved(entity)) entity.remove();
    }
}
