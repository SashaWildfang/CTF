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
 * player sees their own); in the camps and around the islands villagers open menus and chat.
 * Their places come from hub-npcs.yml (the plugin's copy unless one is put in the plugin folder).
 */
public final class HubNpcs implements Listener {

    private static final String TAG = "ctfnpc";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private record KitSpot(Kit kit, double x, double y, double z, float yaw, TeamColor team) {}
    private record Npc(String id, String name, String role, String profession, String type, double x, double y, double z, float yaw,
                       List<String> lines) {}

    private final FractalCTF plugin;
    private final HubManager hub;
    private final Random random = new Random();
    private final List<KitSpot> kitSpots = new ArrayList<>();
    private final List<Npc> npcs = new ArrayList<>();
    private final Map<UUID, Npc> villagers = new HashMap<>();
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
    }

    void stop() {
        for (List<Mannequin> list : mannequins.values()) for (Mannequin m : list) m.remove();
        mannequins.clear();
        for (UUID id : villagers.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        for (UUID id : hints.values()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
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
                    "blue".equals(String.valueOf(raw.get("team"))) ? TeamColor.BLUE : TeamColor.RED));
        }
        for (Map<?, ?> raw : yaml.getMapList("npcs")) {
            List<String> lines = new ArrayList<>();
            if (raw.get("lines") instanceof List<?> list) for (Object line : list) lines.add(String.valueOf(line));
            npcs.add(new Npc(String.valueOf(raw.get("id")), String.valueOf(raw.get("name")), String.valueOf(raw.get("role")),
                    String.valueOf(raw.get("profession")), String.valueOf(raw.get("type")), num(raw, "x"), num(raw, "y"), num(raw, "z"),
                    (float) num(raw, "yaw"), lines));
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
                spawn(chunk.getWorld(), npc);
            });
        }
    }

    private void spawn(World world, Npc npc) {
        Location at = new Location(world, npc.x(), npc.y(), npc.z(), npc.yaw(), 0);
        Villager villager = world.spawn(at, Villager.class, v -> {
            v.addScoreboardTag(TAG);
            v.customName(MINI.deserialize(npc.name()));
            v.setCustomNameVisible(true);
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
        hint(villager);
    }

    /** Clicking a character just gets a line out of them (the camps' soldiers like to trash-talk the other side). */
    private void talk(Player player, Npc npc) {
        Long last = lastTalk.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 600) return; // one line per click, not per packet
        lastTalk.put(player.getUniqueId(), System.currentTimeMillis());
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 0.9f + random.nextFloat() * 0.3f);
        List<String> pool = npc.lines();
        String side = npc.id().startsWith("red-") ? "red" : npc.id().startsWith("blue-") ? "blue" : null;
        if (side != null && random.nextInt(100) < 40) {
            List<String> trash = plugin.messages().list("npc.trash-talk-" + side);
            if (!trash.isEmpty()) pool = trash;
        }
        if (pool.isEmpty()) return;
        String line = pool.get(random.nextInt(pool.size()));
        player.sendMessage(plugin.messages().component("npc.says", Messages.parsed("name", npc.name()), Messages.parsed("line", line)));
    }

    private final Map<UUID, Long> lastTalk = new HashMap<>();
    /** The "Click to talk" line under each villager's name. */
    private final Map<UUID, UUID> hints = new HashMap<>();

    private void hint(Villager villager) {
        Location at = villager.getLocation().add(0, 1.98, 0);
        var display = villager.getWorld().spawn(at, org.bukkit.entity.TextDisplay.class, d -> {
            d.text(plugin.messages().component("npc.click-hint"));
            d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setShadowed(true);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(1.0f, 1.0f, 1.0f), new org.joml.AxisAngle4f()));
        });
        UUID old = hints.put(villager.getUniqueId(), display.getUniqueId());
        if (old != null) {
            Entity stale = Bukkit.getEntity(old);
            if (stale != null) stale.remove();
        }
    }

    // ---------------------------------------------------------------- kit mannequins

    private void tick() {
        ticks++;
        // villagers turn to whoever's closest
        for (UUID id : villagers.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity villager)) continue;
            Player near = null;
            double best = 64;
            for (Player player : villager.getWorld().getPlayers()) {
                double d = player.getLocation().distanceSquared(villager.getLocation());
                if (d < best) {
                    best = d;
                    near = player;
                }
            }
            Npc npc = villagers.get(id);
            float yaw = npc.yaw(), pitch = 0;
            if (near != null) {
                var dir = near.getEyeLocation().toVector().subtract(villager.getEyeLocation().toVector());
                yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                pitch = (float) Math.toDegrees(-Math.atan2(dir.getY(), Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ())));
            }
            villager.setRotation(yaw, pitch);
        }
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
            talk(event.getPlayer(), npc);
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
            talk(player, npc);
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
    }
}
