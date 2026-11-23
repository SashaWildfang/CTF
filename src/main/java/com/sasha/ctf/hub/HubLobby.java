/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.game.VoidGenerator;
import com.sasha.ctf.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Working on the lobby from inside it. /editlobby puts an admin in creative with building allowed (and takes them
 * out again, saving); /savelobby (while editing) saves. Saving writes the world to disk and keeps a copy of it in
 * plugins/FractalCTF/hub-saved. /regenlobby rebuilds the whole lobby without a restart from that saved copy, or
 * from the uploaded template in plugins/FractalCTF/hub-template (a copy of the generated world folder) when that
 * is newer or `original` is asked for: every block that differs is put back, signs and banners with what is on
 * them, the saved labels and display stands are replaced, and hub-npcs.yml is read again.
 */
public final class HubLobby {

    private static final String TEMPLATE_WORLD = "ctf_hub_template";

    private final FractalCTF plugin;
    private final HubManager hub;
    private final Set<UUID> editing = new HashSet<>();
    private boolean regenerating;

    HubLobby(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    /** In edit mode: keeps their inventory, and is left behind when a match starts. */
    public boolean editing(Player player) {
        return editing.contains(player.getUniqueId());
    }

    /** /editlobby: into edit mode, or out of it (which saves). */
    public void edit(Player player) {
        if (editing.remove(player.getUniqueId())) {
            player.setGameMode(GameMode.ADVENTURE);
            hub.items().give(player);
            store(player, "lobby.edit-off");
            return;
        }
        editing.add(player.getUniqueId());
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        plugin.messages().send(player, "lobby.edit-on");
    }

    /** /savelobby: only for someone who is editing. The lobby as it stands becomes the saved lobby. */
    public void save(CommandSender sender) {
        if (!(sender instanceof Player player) || !editing(player)) {
            plugin.messages().send(sender, "lobby.not-editing");
            return;
        }
        store(sender, "lobby.saved");
    }

    private static Path chunks(Path folder) {
        return folder.resolve("dimensions").resolve("minecraft").resolve("overworld");
    }

    /** Where the lobby world's region, entities and poi folders are (the layout differs between Paper versions). */
    private static Path liveChunks(World live) {
        Path folder = live.getWorldFolder().toPath();
        return Files.isDirectory(folder.resolve("region")) ? folder : chunks(folder);
    }

    /**
     * Writes the world to disk and copies its chunks to plugins/FractalCTF/hub-saved: that copy is what /regenlobby
     * puts back from then on. The uploaded hub-template is never written to.
     */
    private void store(CommandSender sender, String message) {
        World live = Bukkit.getWorlds().get(0);
        hub.portal().seal();                                         // never saved standing open
        live.save(true);
        Path from = liveChunks(live);
        Path template = plugin.getDataFolder().toPath().resolve("hub-template");
        Path saved = plugin.getDataFolder().toPath().resolve("hub-saved");
        // Copied here and now, before the server writes to those files again (a few megabytes: it takes a moment).
        try {
            if (!Files.isDirectory(chunks(saved))) {
                if (Files.isDirectory(template)) WorldFiles.copy(template, saved);          // for its level.dat and data
                Files.createDirectories(chunks(saved));
            }
            for (String part : new String[] {"region", "entities", "poi"}) {
                WorldFiles.delete(chunks(saved).resolve(part));
                if (Files.isDirectory(from.resolve(part))) WorldFiles.copy(from.resolve(part), chunks(saved).resolve(part));
            }
            Files.writeString(saved.resolve("saved-at.txt"), java.time.Instant.now().toString());
            plugin.messages().send(sender, message);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Couldn't save the lobby to hub-saved", e);
            plugin.messages().send(sender, "lobby.save-failed");
        }
    }

    private static long newest(Path folder) {
        long newest = 0;
        try (Stream<Path> files = Files.list(chunks(folder).resolve("region"))) {
            for (Path file : (Iterable<Path>) files::iterator) newest = Math.max(newest, Files.getLastModifiedTime(file).toMillis());
        } catch (IOException ignored) {
            // no region files: as old as can be
        }
        return newest;
    }

    /**
     * /regenlobby [original] [confirm]: the lobby rebuilt from the lobby as last saved (hub-saved), or from the
     * uploaded template (hub-template) when that is newer, when nothing was ever saved, or when `original` is asked for.
     */
    public void regenerate(CommandSender sender, String[] args) {
        Messages m = plugin.messages();
        boolean original = false, confirmed = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("original")) original = true;
            if (arg.equalsIgnoreCase("confirm")) confirmed = true;
        }
        Path template = plugin.getDataFolder().toPath().resolve("hub-template");
        Path saved = plugin.getDataFolder().toPath().resolve("hub-saved");
        boolean hasTemplate = Files.isDirectory(chunks(template).resolve("region")), hasSaved = Files.isDirectory(chunks(saved).resolve("region"));
        boolean fromSaved = hasSaved && !original && (!hasTemplate || newest(saved) >= newest(template));
        if (!fromSaved && !hasTemplate) {
            m.send(sender, "lobby.regen-no-template");
            return;
        }
        if (regenerating) {
            m.send(sender, "lobby.regen-busy");
            return;
        }
        if (!confirmed) {
            m.send(sender, fromSaved ? "lobby.regen-confirm-saved" : hasSaved ? "lobby.regen-confirm-drop" : "lobby.regen-confirm-template");
            return;
        }
        Path source = fromSaved ? saved : template;
        Path regionFiles = chunks(source).resolve("region");
        regenerating = true;
        m.broadcast("lobby.regen-start");
        World live = Bukkit.getWorlds().get(0);
        hub.portal().seal();
        Path liveFolder = live.getWorldFolder().toPath();
        Set<Long> regions = new HashSet<>();                         // every region file of the source and of the lobby as it is
        collect(regions, regionFiles);
        collect(regions, liveChunks(live).resolve("region"));
        Path target = Bukkit.getWorldContainer().toPath().resolve(TEMPLATE_WORLD);
        Path dimensions = WorldFiles.dimensionsFolder(liveFolder);
        World stale = Bukkit.getWorld(TEMPLATE_WORLD);
        if (stale != null) Bukkit.unloadWorld(stale, false);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WorldFiles.delete(target);
                if (dimensions != null) WorldFiles.delete(dimensions.resolve(TEMPLATE_WORLD));
                WorldFiles.copy(source, target);
                if (!fromSaved) WorldFiles.delete(saved);            // back to the template: the old saved lobby goes
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Couldn't copy the lobby to rebuild from", e);
                Bukkit.getScheduler().runTask(plugin, () -> fail(sender));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> paste(sender, live, regions));
        });
    }

    private void fail(CommandSender sender) {
        regenerating = false;
        plugin.messages().send(sender, "lobby.regen-failed");
    }

    private static void collect(Set<Long> regions, Path folder) {
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> files = Files.list(folder)) {
            files.forEach(file -> {
                String[] parts = file.getFileName().toString().split("\\.");
                if (parts.length != 4 || !parts[0].equals("r") || !parts[3].equals("mca")) return;
                try {
                    regions.add(((long) Integer.parseInt(parts[1]) << 32) | (Integer.parseInt(parts[2]) & 0xffffffffL));
                } catch (NumberFormatException ignored) {
                    // not a region file
                }
            });
        } catch (IOException ignored) {
            // nothing to rebuild there
        }
    }

    /** Opens the template as a world of its own and copies it over the lobby, a few chunks a tick. */
    private void paste(CommandSender sender, World live, Set<Long> regions) {
        World template = new WorldCreator(TEMPLATE_WORLD).generator(new VoidGenerator()).createWorld();
        if (template == null) {
            fail(sender);
            return;
        }
        template.setAutoSave(false);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (long key : regions) {
            int rx = (int) (key >> 32), rz = (int) key;
            for (int cx = rx << 5; cx < (rx << 5) + 32; cx++) for (int cz = rz << 5; cz < (rz << 5) + 32; cz++) queue.add(new int[]{cx, cz});
        }
        int[] done = new int[3];                                     // blocks put back, signs and the like, entities
        BlockData air = Material.AIR.createBlockData();
        int minY = live.getMinHeight(), sections = (live.getMaxHeight() - minY) >> 4;
        new BukkitRunnable() {
            @Override
            public void run() {
                long until = System.nanoTime() + 25_000_000L;        // half a tick's worth, so the server keeps running
                while (!queue.isEmpty() && System.nanoTime() < until) {
                    int[] at = queue.poll();
                    boolean inTemplate = template.isChunkGenerated(at[0], at[1]);
                    if (!inTemplate && !live.isChunkGenerated(at[0], at[1])) continue;
                    Chunk source = inTemplate ? template.getChunkAt(at[0], at[1]) : null;
                    ChunkSnapshot want = source == null ? null : source.getChunkSnapshot(false, false, false);
                    Chunk chunk = live.getChunkAt(at[0], at[1]);
                    ChunkSnapshot have = chunk.getChunkSnapshot(false, false, false);
                    boolean built = false;
                    for (int s = 0; s < sections; s++) {
                        boolean wantEmpty = want == null || want.isSectionEmpty(s), haveEmpty = have.isSectionEmpty(s);
                        if (wantEmpty && haveEmpty) continue;
                        built = true;
                        for (int y = minY + (s << 4); y < minY + (s << 4) + 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    BlockData a = wantEmpty ? air : want.getBlockData(x, y, z);
                                    BlockData b = haveEmpty ? air : have.getBlockData(x, y, z);
                                    if (a.equals(b)) continue;
                                    chunk.getBlock(x, y, z).setBlockData(a, false);
                                    done[0]++;
                                }
                            }
                        }
                    }
                    if (!built) continue;
                    chunk.addPluginChunkTicket(plugin);              // kept loaded until the entities are swapped
                    if (source == null) continue;
                    source.addPluginChunkTicket(plugin);
                    for (BlockState state : source.getTileEntities()) {      // signs, banners and the rest, with what is on them
                        state.copy(new Location(live, state.getX(), state.getY(), state.getZ())).update(true, false);
                        done[1]++;
                    }
                }
                if (!queue.isEmpty()) return;
                cancel();
                Bukkit.getScheduler().runTaskLater(plugin, () -> finish(sender, live, template, done), 40L);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Swaps the saved labels and display stands, drops the template, reads hub-npcs.yml again. */
    private void finish(CommandSender sender, World live, World template, int[] done) {
        for (Entity entity : live.getEntities()) {
            if (entity instanceof Player || !entity.isPersistent()) continue;        // the plugin's own characters and boards aren't saved ones
            if (entity instanceof ArmorStand || entity instanceof Display || entity instanceof Interaction || entity instanceof Hanging) entity.remove();
        }
        for (Entity entity : template.getEntities()) {
            if (entity instanceof Player) continue;
            Location to = entity.getLocation();
            to.setWorld(live);
            entity.copy(to);
            done[2]++;
        }
        live.removePluginChunkTickets(plugin);
        Path folder = template.getWorldFolder().toPath();
        Path copy = Bukkit.getWorldContainer().toPath().resolve(TEMPLATE_WORLD);
        Bukkit.unloadWorld(template, false);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                WorldFiles.delete(folder);
                WorldFiles.delete(copy);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't delete the lobby template's working copy", e);
            }
        }, 40L);
        live.save();
        hub.reloadFile();
        for (Player player : live.getPlayers()) if (!editing(player)) hub.move(player, hub.spawn());
        regenerating = false;
        plugin.messages().broadcast("lobby.regen-done", Messages.arg("blocks", String.format(java.util.Locale.US, "%,d", done[0])),
                Messages.arg("entities", done[2]));
        plugin.getLogger().info("Lobby rebuilt from hub-template: " + done[0] + " blocks, " + done[1] + " block entities, " + done[2] + " entities.");
    }
}
