/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.kit.Kit;
import com.sasha.ctf.model.TeamColor;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import com.sasha.ctf.model.Spot;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The hub's people (clicking a villager only makes it talk). In the armoury every kit is shown on a mannequin wearing the viewer's own skin (each
 * player sees their own), in any of the four team colours; in the camps and around the islands villagers chat: one click and a character
 * says its lines in order, pausing after each to give time to read it, and the four camps' soldiers sometimes end on a shot at the other three.
 * Their names float over them as plain text with no box behind it (the height and the box are in config.yml under hub).
 * They are alive: each turns smoothly to whoever comes near, glances about when alone, nods when spoken to (and shakes its
 * head when it trash-talks), and the ones with a `pace:` in hub-npcs.yml stroll between those points until someone walks up.
 * Their places come from hub-npcs.yml (the plugin's copy unless one is put in the plugin folder).
 */
public final class HubNpcs implements Listener {

    private static final String TAG = "ctfnpc";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private record KitSpot(Kit kit, double x, double y, double z, float yaw, TeamColor team) {}
    private record Npc(String id, String name, String role, String profession, String type, double x, double y, double z, float yaw,
                       List<String> lines, List<double[]> pace) {}

    /** How one character is standing, looking and walking right now. */
    private static final class Actor {
        final Npc npc;
        double x, z;
        float yaw, pitch, home, gazeYaw, gazePitch;
        int leg = 1, wait, gaze, nod;

        Actor(Npc npc) {
            this.npc = npc;
            x = npc.x();
            z = npc.z();
            yaw = home = gazeYaw = npc.yaw();
        }
    }

    private final FractalCTF plugin;
    private final HubManager hub;
    private final Random random = new Random();
    private final List<KitSpot> kitSpots = new ArrayList<>();
    private final List<Npc> npcs = new ArrayList<>();
    private final Map<UUID, Npc> villagers = new HashMap<>();
    private final Map<UUID, Actor> actors = new HashMap<>();
    /** Each player's own kit mannequins. */
    private final Map<UUID, List<Mannequin>> mannequins = new HashMap<>();
    private Location armoury;
    /** Where players arrive (in front of the hub's title), when the config's hub.spawn is "auto". */
    private Location spawn;

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }
    private double armouryRadius = 40;
    private int ticks;

    HubNpcs(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    void start() {
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        World world = Bukkit.getWorlds().get(0);
        for (Entity entity : world.getEntities()) if (entity.getScoreboardTags().contains(TAG)) entity.remove();
        // the old kit stands from 1.3's hub
        for (Entity entity : world.getEntities()) {
            for (String tag : entity.getScoreboardTags()) if (tag.startsWith("ctfkit:")) {
                entity.remove();
                break;
            }
        }
        for (Npc npc : npcs) if (world.isChunkLoaded((int) Math.floor(npc.x()) >> 4, (int) Math.floor(npc.z()) >> 4)) spawn(world, npc);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::animate, 20L, 2L);
    }

    void stop() {
        for (List<Mannequin> list : mannequins.values()) for (Mannequin m : list) m.remove();
        mannequins.clear();
        for (UUID id : villagers.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        for (List<UUID> plates : hints.values()) for (UUID id : plates) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        for (Talk talk : talks.values()) if (talk.wait != null) talk.wait.cancel();
        talks.clear();
    }

    /** hub-npcs.yml has changed (the lobby was rebuilt): everyone is taken down and stood up again from it. */
    void reload() {
        stop();
        villagers.clear();
        actors.clear();
        hints.clear();
        kitSpots.clear();
        npcs.clear();
        load();
        World world = Bukkit.getWorlds().get(0);
        for (Npc npc : npcs) if (world.isChunkLoaded((int) Math.floor(npc.x()) >> 4, (int) Math.floor(npc.z()) >> 4)) spawn(world, npc);
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "hub-npcs.yml");
        YamlConfiguration yaml;
        if (file.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            var resource = plugin.getResource("hub-npcs.yml");
            if (resource == null) return;
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        Spot where = Spot.parse(yaml.getString("spawn"));
        if (where != null) spawn = where.in(Bukkit.getWorlds().get(0));
        ConfigurationSection room = yaml.getConfigurationSection("armoury");
        if (room != null) {
            armoury = new Location(Bukkit.getWorlds().get(0), room.getDouble("x"), room.getDouble("y"), room.getDouble("z"));
            armouryRadius = room.getDouble("radius", 40);
        }
        for (Map<?, ?> raw : yaml.getMapList("kits")) {
            Kit kit = Kit.parse(String.valueOf(raw.get("kit"))).orElse(null);
            if (kit == null) continue;
            kitSpots.add(new KitSpot(kit, num(raw, "x"), num(raw, "y"), num(raw, "z"), (float) num(raw, "yaw"),
                    TeamColor.parse(String.valueOf(raw.get("team"))).orElse(TeamColor.RED)));
        }
        for (Map<?, ?> raw : yaml.getMapList("npcs")) {
            List<String> lines = new ArrayList<>();
            if (raw.get("lines") instanceof List<?> list) for (Object line : list) lines.add(String.valueOf(line));
            List<double[]> pace = new ArrayList<>();                 // "x z; x z": where it strolls when nobody is near
            if (raw.get("pace") != null) {
                for (String point : String.valueOf(raw.get("pace")).split(";")) {
                    String[] xz = point.trim().split("\\s+");
                    try {
                        if (xz.length == 2) pace.add(new double[]{Double.parseDouble(xz[0]), Double.parseDouble(xz[1])});
                    } catch (NumberFormatException ignored) {
                        // a point that isn't two numbers is left out
                    }
                }
            }
            npcs.add(new Npc(String.valueOf(raw.get("id")), String.valueOf(raw.get("name")), String.valueOf(raw.get("role")),
                    String.valueOf(raw.get("profession")), String.valueOf(raw.get("type")), num(raw, "x"), num(raw, "y"), num(raw, "z"),
                    (float) num(raw, "yaw"), lines, pace));
        }
    }

    private static double num(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    // ---------------------------------------------------------------- villagers

    /** Villagers aren't saved with their chunk: they come back whenever it loads. */
    @EventHandler
    public void onChunk(org.bukkit.event.world.ChunkLoadEvent event) {
        var chunk = event.getChunk();
        for (Npc npc : npcs) {
            if (((int) Math.floor(npc.x()) >> 4) != chunk.getX() || ((int) Math.floor(npc.z()) >> 4) != chunk.getZ()) continue;
            if (villagers.containsValue(npc) && villagers.entrySet().stream().anyMatch(e -> e.getValue() == npc && Bukkit.getEntity(e.getKey()) != null)) continue;
            Bukkit.getScheduler().runTask(plugin, () -> {
                villagers.values().removeIf(n -> n == npc);
                actors.values().removeIf(actor -> actor.npc == npc);
                spawn(chunk.getWorld(), npc);
            });
        }
    }

    private void spawn(World world, Npc npc) {
        Location at = new Location(world, npc.x(), npc.y(), npc.z(), npc.yaw(), 0);
        Villager villager = world.spawn(at, Villager.class, v -> {
            v.addScoreboardTag(TAG);
            v.setCustomNameVisible(false);                     // the name is a floating line of text instead (see plates)
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setAware(false);
            v.setPersistent(false);
            v.setCollidable(false);
            v.setGravity(false);
            var profession = Registry.VILLAGER_PROFESSION.get(org.bukkit.NamespacedKey.minecraft(npc.profession().toLowerCase(java.util.Locale.ROOT)));
            if (profession != null) v.setProfession(profession);
            var type = Registry.VILLAGER_TYPE.get(org.bukkit.NamespacedKey.minecraft(npc.type().toLowerCase(java.util.Locale.ROOT)));
            if (type != null) v.setVillagerType(type);
            v.setVillagerLevel(5);
            v.setVillagerExperience(250);
        });
        villagers.put(villager.getUniqueId(), npc);
        actors.put(villager.getUniqueId(), new Actor(npc));
        plates(villager, npc);
    }

    private static final java.util.Set<String> CAMPS = java.util.Set.of("red", "blue", "yellow", "green");

    /** One player's conversation with one character: which line comes next, and the wait before it. */
    private static final class Talk {
        final Npc npc;
        final UUID body;
        final String parting;
        int next;
        org.bukkit.scheduler.BukkitTask wait;

        Talk(Npc npc, UUID body, String parting) {
            this.npc = npc;
            this.body = body;
            this.parting = parting;
        }
    }

    /**
     * Clicking a character starts it talking: it says its lines in order, pausing after each for as long as the
     * line takes to read, so a story arrives one piece at a time. Clicking it again while it talks brings the
     * next line at once. A camp's soldier sometimes ends on a parting shot at the other three camps. Walking
     * away (or clicking someone else) ends it.
     */
    private void talk(Player player, Npc npc, Entity body) {
        Long last = lastTalk.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 400) return; // one click, not one per packet
        lastTalk.put(player.getUniqueId(), System.currentTimeMillis());
        Talk talk = talks.get(player.getUniqueId());
        if (talk != null && talk.wait != null) talk.wait.cancel();
        if (talk == null || talk.npc != npc) {
            if (npc.lines().isEmpty()) return;
            String parting = null;
            int dash = npc.id().indexOf('-');
            if (dash > 0 && CAMPS.contains(npc.id().substring(0, dash)) && random.nextInt(100) < 40) {
                List<String> trash = plugin.messages().list("npc.trash-talk-" + npc.id().substring(0, dash));
                if (!trash.isEmpty()) parting = trash.get(random.nextInt(trash.size()));
            }
            talk = new Talk(npc, body.getUniqueId(), parting);
            talks.put(player.getUniqueId(), talk);
        }
        say(player, talk);
    }

    private void say(Player player, Talk talk) {
        Entity body = Bukkit.getEntity(talk.body);
        int lines = talk.npc.lines().size();
        boolean over = !player.isOnline() || body == null || !body.getWorld().equals(player.getWorld())
                || body.getLocation().distanceSquared(player.getLocation()) > 14 * 14 || talk.next > lines || (talk.next == lines && talk.parting == null);
        if (over) {
            talks.remove(player.getUniqueId(), talk);
            return;
        }
        boolean shot = talk.next == lines;                           // the parting shot
        String line = shot ? talk.parting : talk.npc.lines().get(talk.next);
        talk.next++;
        Actor actor = actors.get(talk.body);
        Location head = body.getLocation().add(0, 2.0, 0);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 0.9f + random.nextFloat() * 0.3f);
        if (shot) {
            if (body instanceof Villager villager) villager.shakeHead();
            player.spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, head, 1);
        } else {
            if (actor != null) actor.nod = 8;                        // a nod as it speaks
            if (talk.next == 1) player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, head, 4, 0.25, 0.15, 0.25);
        }
        // the name without its bold, so the line reads like every other message in chat
        Component name = MINI.deserialize(talk.npc.name().replace("<bold>", "").replace("</bold>", "").replace("<b>", "").replace("</b>", ""));
        player.sendMessage(plugin.messages().component("npc.says", Messages.comp("name", name), Messages.parsed("line", line)));
        if (talk.next > lines || (talk.next == lines && talk.parting == null)) {
            talks.remove(player.getUniqueId(), talk);
            return;
        }
        double fixed = plugin.getConfig().getDouble("hub.npc-line-pause-seconds", 0);
        int letters = line.replaceAll("<[^>]+>", "").length();
        long pause = fixed > 0 ? Math.round(fixed * 20) : Math.max(50, Math.min(130, 24 + Math.round(letters * 0.9)));
        talk.wait = Bukkit.getScheduler().runTaskLater(plugin, () -> say(player, talk), pause);
    }

    private final Map<UUID, Long> lastTalk = new HashMap<>();
    /** Who is being talked to right now, and by whom. */
    private final Map<UUID, Talk> talks = new HashMap<>();
    /** Each villager's floating name and the "Click to talk" line under it. */
    private final Map<UUID, List<UUID>> hints = new HashMap<>();

    /** The name over a character and the hint under it: plain text that faces the viewer, with no box unless the config asks for one. */
    private void plates(Villager villager, Npc npc) {
        double height = plugin.getConfig().getDouble("hub.npc-name-height", 2.6);
        boolean box = plugin.getConfig().getBoolean("hub.npc-name-background", false);
        List<UUID> made = new ArrayList<>();
        made.add(plate(villager.getLocation().add(0, height, 0), MINI.deserialize(npc.name()), 1.0f, box));
        made.add(plate(villager.getLocation().add(0, height - 0.26, 0), plugin.messages().component("npc.click-hint"), 0.8f, box));
        List<UUID> old = hints.put(villager.getUniqueId(), made);
        if (old != null) for (UUID id : old) {
            Entity stale = Bukkit.getEntity(id);
            if (stale != null) stale.remove();
        }
    }

    private UUID plate(Location at, Component text, float scale, boolean box) {
        return at.getWorld().spawn(at, org.bukkit.entity.TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setTeleportDuration(3);                          // glides with a character that walks
            if (box) d.setDefaultBackground(true);
            else d.setBackgroundColor(org.bukkit.Color.fromARGB(0));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
        }).getUniqueId();
    }

    // ---------------------------------------------------------------- kit mannequins

    private void tick() {
        ticks++;
        if (armoury == null || kitSpots.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean near = player.getWorld().equals(armoury.getWorld()) && player.getLocation().distanceSquared(armoury) < armouryRadius * armouryRadius;
            List<Mannequin> mine = mannequins.get(player.getUniqueId());
            if (near && mine == null) {
                mannequins.put(player.getUniqueId(), spawnKits(player));
            } else if (!near && mine != null && player.getLocation().distanceSquared(armoury) > (armouryRadius + 16) * (armouryRadius + 16)) {
                for (Mannequin m : mine) m.remove();
                mannequins.remove(player.getUniqueId());
            } else if (near && ticks % 10 == 0) {
                for (Mannequin m : mine) label(player, m);
            }
        }
    }

    private static float wrap(float degrees) {
        degrees %= 360;
        return degrees >= 180 ? degrees - 360 : degrees < -180 ? degrees + 360 : degrees;
    }

    /**
     * Every other tick: each character eases its head toward whoever is within seven blocks; alone, it glances
     * somewhere new every few seconds, or (with a beat in hub-npcs.yml) walks it, pausing at each end. A nod is
     * played over whatever else it is doing. hub.npc-movement: false in config.yml keeps them on their spots.
     */
    private void animate() {
        boolean stroll = plugin.getConfig().getBoolean("hub.npc-movement", true);
        double height = plugin.getConfig().getDouble("hub.npc-name-height", 2.6);
        var each = actors.entrySet().iterator();
        while (each.hasNext()) {
            var entry = each.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Villager villager) || !villager.isValid()) {
                if (!villagers.containsKey(entry.getKey())) each.remove();
                continue;
            }
            Actor a = entry.getValue();
            Location at = villager.getLocation();
            Player near = null;
            double best = 49;
            for (Player player : at.getWorld().getPlayers()) {
                double d = player.getLocation().distanceSquared(at);
                if (d < best) {
                    best = d;
                    near = player;
                }
            }
            float wantYaw, wantPitch = 0, turn;
            boolean walked = false;
            List<double[]> beat = a.npc.pace();
            if (near != null) {
                var dir = near.getEyeLocation().toVector().subtract(villager.getEyeLocation().toVector());
                wantYaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                wantPitch = (float) Math.toDegrees(-Math.atan2(dir.getY(), Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ())));
                turn = 16;
                a.wait = Math.max(a.wait, 25);                       // and stays a moment after they have gone
                a.home = a.gazeYaw = wantYaw;
            } else if (stroll && beat.size() > 1 && a.wait <= 0) {
                double[] goal = beat.get(a.leg % beat.size());
                double dx = goal[0] - a.x, dz = goal[1] - a.z, far = Math.sqrt(dx * dx + dz * dz);
                if (far < 0.12) {
                    a.leg++;
                    a.wait = 40 + random.nextInt(70);
                    a.home = a.gazeYaw = a.yaw;
                    wantYaw = a.yaw;
                } else {
                    a.x += dx / far * 0.11;
                    a.z += dz / far * 0.11;
                    wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    walked = true;
                }
                turn = 20;
            } else {
                if (a.wait > 0) a.wait--;
                if (--a.gaze <= 0) {                                 // an idle glance
                    a.gaze = 25 + random.nextInt(60);
                    a.gazeYaw = (beat.size() > 1 ? a.home : a.npc.yaw()) + random.nextInt(101) - 50;
                    a.gazePitch = random.nextInt(21) - 8;
                }
                wantYaw = a.gazeYaw;
                wantPitch = a.gazePitch;
                turn = 5;
            }
            a.yaw = wrap(a.yaw + Math.max(-turn, Math.min(turn, wrap(wantYaw - a.yaw))));
            a.pitch += Math.max(-6, Math.min(6, wantPitch - a.pitch));
            float pitch = a.pitch;
            if (a.nod > 0) {
                pitch += 20f * (float) Math.sin(Math.PI * (8 - a.nod) / 8.0);
                a.nod--;
            }
            if (walked) {
                villager.teleport(new Location(at.getWorld(), a.x, a.npc.y(), a.z, a.yaw, pitch));
                List<UUID> plates = hints.get(entry.getKey());
                if (plates != null) for (int i = 0; i < plates.size(); i++) {
                    Entity plate = Bukkit.getEntity(plates.get(i));
                    if (plate != null) plate.teleport(new Location(at.getWorld(), a.x, a.npc.y() + height - 0.26 * i, a.z));
                }
            } else {
                villager.setRotation(a.yaw, pitch);
            }
        }
    }

    private List<Mannequin> spawnKits(Player viewer) {
        List<Mannequin> list = new ArrayList<>();
        ResolvableProfile skin = ResolvableProfile.resolvableProfile(viewer.getPlayerProfile());
        for (KitSpot spot : kitSpots) {
            Location at = new Location(armoury.getWorld(), spot.x(), spot.y(), spot.z(), spot.yaw(), 0);
            Mannequin mannequin = at.getWorld().spawn(at, Mannequin.class, m -> {
                m.setVisibleByDefault(false);
                m.addScoreboardTag(TAG);
                m.addScoreboardTag("ctfkit:" + spot.kit().id());
                m.setProfile(skin);
                m.setImmovable(true);
                m.setGravity(false);
                m.setInvulnerable(true);
                m.setSilent(true);
                m.setCollidable(false);
                m.setPersistent(false);
                Kit.Armour armour = spot.kit().armour(spot.team());
                var equipment = m.getEquipment();
                equipment.setHelmet(armour.helmet());
                equipment.setChestplate(armour.chestplate());
                equipment.setLeggings(armour.leggings());
                equipment.setBoots(armour.boots());
                equipment.setItemInMainHand(hand(spot.kit(), spot.team()));
                if (spot.kit() == Kit.SHIELDER) equipment.setItemInOffHand(ItemStack.of(org.bukkit.Material.SHIELD));
                else if (spot.kit().ability() != Kit.Ability.NONE) equipment.setItemInOffHand(ItemStack.of(spot.kit().ability().material()));
                m.setCustomNameVisible(true);
            });
            viewer.showEntity(plugin, mannequin);
            label(viewer, mannequin);
            list.add(mannequin);
        }
        return list;
    }

    /** What the mannequin holds: the kit's weapon (sword, bow, pickaxe...). */
    private static ItemStack hand(Kit kit, TeamColor team) {
        for (Kit.KitItem item : kit.items(team)) {
            String key = item.key();
            if (key.equals("sword") || key.equals("pickaxe") || key.equals("axe") && kit == Kit.MEDIC || key.equals("bow") && kit.items(team).get(0).key().equals("bow")) {
                return item.item().clone();
            }
        }
        return ItemStack.of(kit.icon());
    }

    /** The kit's name, and under it what it costs (or that it's yours), for this viewer. */
    private void label(Player viewer, Mannequin mannequin) {
        Kit kit = null;
        for (String tag : mannequin.getScoreboardTags()) if (tag.startsWith("ctfkit:")) kit = Kit.parse(tag.substring(7)).orElse(null);
        if (kit == null) return;
        UUID id = viewer.getUniqueId();
        boolean owned = plugin.kits().owns(id, kit);
        boolean chosen = plugin.kits().of(id) == kit;
        int price = plugin.ctfConfig().price(kit);
        NamedTextColor colour = chosen ? NamedTextColor.GREEN : owned ? NamedTextColor.YELLOW : NamedTextColor.GOLD;
        mannequin.customName(Component.text(kit.display(), colour).decoration(TextDecoration.BOLD, true));
        String key = chosen ? "npc.kit-selected" : owned ? (price <= 0 ? "npc.kit-free" : "npc.kit-owned") : "npc.kit-price";
        mannequin.setDescription(plugin.messages().component(key, Messages.arg("price", price)));
    }

    public void refresh(Player viewer) {
        List<Mannequin> mine = mannequins.get(viewer.getUniqueId());
        if (mine != null) for (Mannequin m : mine) label(viewer, m);
    }

    // ---------------------------------------------------------------- clicks

    /** Right-click: a kit mannequin opens its preview; a villager talks (and opens its menu). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity clicked = event.getRightClicked();
        Npc npc = villagers.get(clicked.getUniqueId());
        if (npc != null) {
            event.setCancelled(true);
            talk(event.getPlayer(), npc, clicked);
            return;
        }
        for (String tag : clicked.getScoreboardTags()) {
            if (!tag.startsWith("ctfkit:")) continue;
            event.setCancelled(true);
            Kit.parse(tag.substring(7)).ifPresent(kit -> plugin.kits().preview(event.getPlayer(), kit));
            return;
        }
    }

    /** Left-click: a kit mannequin equips that kit (or offers to buy it). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        Entity hit = event.getEntity();
        if (!hit.getScoreboardTags().contains(TAG)) return;
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) return;
        Npc npc = villagers.get(hit.getUniqueId());
        if (npc != null) {
            talk(player, npc, hit);
            return;
        }
        for (String tag : hit.getScoreboardTags()) {
            if (!tag.startsWith("ctfkit:")) continue;
            Kit.parse(tag.substring(7)).ifPresent(kit -> {
                if (plugin.kits().owns(player.getUniqueId(), kit)) {
                    plugin.kits().choose(player, kit);
                    refresh(player);
                } else {
                    plugin.kits().confirmBuy(player, kit);
                }
            });
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        List<Mannequin> mine = mannequins.remove(event.getPlayer().getUniqueId());
        if (mine != null) for (Mannequin m : mine) m.remove();
        Talk talk = talks.remove(event.getPlayer().getUniqueId());
        if (talk != null && talk.wait != null) talk.wait.cancel();
        lastTalk.remove(event.getPlayer().getUniqueId());
    }
}
