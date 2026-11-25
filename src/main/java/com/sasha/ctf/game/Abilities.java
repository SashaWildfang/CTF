/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.kit.Kit;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The kits' abilities. Active ones are an item the player right-clicks (its cooldown shows on the
 * hotbar); a few are placed (the Engineer's turret, the Redstone Mechanic's torch); the rest are passive
 * (Fire Archer, Seeking Archer). Anything an ability spawns belongs to the player who used it, so its
 * damage counts as theirs for kills, assists and friendly fire.
 */
public final class Abilities implements Listener {

    private static final double BRUTE_FORCE_BONUS = 5;

    private final FractalCTF plugin;
    private final GameManager game;
    private final Random random = new Random();
    private final NamespacedKey coinKey;

    /** Entities spawned by abilities → the player they belong to. */
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Map<UUID, Long> bruteForce = new HashMap<>();
    private final Map<UUID, UUID> turrets = new HashMap<>();
    private final Map<UUID, List<UUID>> undead = new HashMap<>();
    private final Map<UUID, BlockPos> torches = new HashMap<>();
    private final Map<UUID, BukkitTask> disguises = new HashMap<>();
    /** Frost Mage snowballs (they slow what they hit). */
    private final NamespacedKey frostKey;
    /** Trapper: each player's hidden traps, oldest first. */
    private final Map<UUID, List<Trap>> traps = new HashMap<>();
    /** Vampire: when Blood Rush ends (ms). */
    private final Map<UUID, Long> bloodRush = new HashMap<>();
    /** Grappler: no fall damage until then (ms). */
    private final Map<UUID, Long> grappled = new HashMap<>();
    /** Tracker: the flag-carrier readout runs until then (ms). */
    private final Map<UUID, Long> tracking = new HashMap<>();

    /** A hidden trap: the block space it fills, whose it is. */
    private record Trap(Location at, UUID owner, TeamColor team) {}

    Abilities(FractalCTF plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        this.coinKey = new NamespacedKey(plugin, "coin");
        this.frostKey = new NamespacedKey(plugin, "frost");
        Bukkit.getScheduler().runTaskTimer(plugin, this::everyFewTicks, 4L, 4L);
    }

    // ---------------------------------------------------------------- who did it

    /** The player responsible for a hit: themselves, their arrow, their TNT, turret, zombie or sheep. */
    Player responsible(Entity damager) {
        if (damager == null) return null;
        if (damager instanceof Player player) return player;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) return source;
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) return shooter;
            if (projectile.getShooter() instanceof Entity shooter) return responsible(shooter);
        }
        UUID owner = owners.get(damager.getUniqueId());
        return owner == null ? null : Bukkit.getPlayer(owner);
    }

    private Match live(Player player) {
        Match match = game.match();
        if (match == null || !match.live() || !match.world().equals(player.getWorld())) return null;
        return match.teamOf(player.getUniqueId()) == null ? null : match;
    }

    private List<Player> enemies(Match match, TeamColor team, Location around, double radius) {
        List<Player> enemies = new ArrayList<>();
        double limit = radius * radius;
        for (Player player : match.alive()) {
            if (player.isDead() || match.teamOf(player.getUniqueId()) == team) continue;
            if (player.getLocation().distanceSquared(around) <= limit) enemies.add(player);
        }
        return enemies;
    }

    // ---------------------------------------------------------------- using abilities

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Kit.Ability ability = Kit.Ability.of(Items.tagOf(event.getItem()));
        if (ability == Kit.Ability.NONE || ability == Kit.Ability.TURRET || ability == Kit.Ability.SPAWN_TORCH || ability == Kit.Ability.GRAPPLE) return;
        Player player = event.getPlayer();
        event.setCancelled(true);
        Match match = live(player);
        if (match == null) return;
        if (match.warmingUp()) {
            match.warmupNotice(player);
            return;
        }
        if (player.hasCooldown(ability.material())) {
            plugin.messages().actionBar(player, "ability.cooldown", Messages.arg("ability", ability.display()),
                    Messages.arg("seconds", player.getCooldown(ability.material()) / 20 + 1));
            return;
        }
        TeamColor team = match.teamOf(player.getUniqueId());
        boolean used = switch (ability) {
            case BRUTE_FORCE -> bruteForce(player);
            case COIN_TOSS -> coinToss(player);
            case SUPER_BREAKER -> effect(player, PotionEffectType.HASTE, 4, 100, Sound.BLOCK_ANVIL_USE);
            case THROW_TNT -> throwTnt(player);
            case SPEED_BOOST -> effect(player, PotionEffectType.SPEED, 2, 60, Sound.ENTITY_BAT_TAKEOFF);
            case HEALING_ZONE -> healingZone(match, player, team);
            case RAISE_DEAD -> raiseDead(match, player, team);
            case STAR_SPIN -> starSpin(match, player);
            case FIRE_BREATH -> fireBreath(match, player, team);
            case DISGUISE -> disguise(match, player, team);
            case GROUND_SLAM -> groundSlam(match, player, team);
            case LIGHTNING -> lightning(match, player, team);
            case EXPLODING_SHEEP -> event.getClickedBlock() != null && sheep(player, team, event.getClickedBlock(), event.getBlockFace());
            case FROST_NOVA -> frostNova(match, player, team);
            case HUNTERS_SENSE -> huntersSense(match, player, team);
            case SET_TRAP -> setTrap(match, player, team, event.getClickedBlock(), event.getBlockFace());
            case BLOOD_RUSH -> bloodRush(player);
            default -> false;
        };
        if (used && ability.cooldownSeconds() > 0) {
            player.setCooldown(ability.material(), ability.cooldownSeconds() * 20);
            readyLater(player, ability);
        }
    }

    /** When the cooldown is over: "Brute Force is ready!" on the action bar, with a little ding. */
    private void readyLater(Player player, Kit.Ability ability) {
        UUID id = player.getUniqueId();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = org.bukkit.Bukkit.getPlayer(id);
            if (online == null || online.hasCooldown(ability.material()) || live(online) == null) return;
            boolean stillHas = false;
            for (var item : online.getInventory().getContents()) if (item != null && Kit.Ability.of(Items.tagOf(item)) == ability) stillHas = true;
            if (!stillHas) return;
            plugin.messages().actionBar(online, "ability.ready", Messages.arg("ability", ability.display()));
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.6f);
        }, ability.cooldownSeconds() * 20L + 1);
    }

    /** Makes the ability's item shine (Brute Force charged) or stop shining. */
    private void glint(Player player, Kit.Ability ability, boolean on) {
        for (var item : player.getInventory().getContents()) {
            if (item != null && Kit.Ability.of(Items.tagOf(item)) == ability) item.editMeta(meta -> meta.setEnchantmentGlintOverride(on ? Boolean.TRUE : null));
        }
    }

    private boolean effect(Player player, PotionEffectType type, int amplifier, int ticks, Sound sound) {
        player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
        player.getWorld().playSound(player.getLocation(), sound, 1f, 1.2f);
        return true;
    }

    private boolean bruteForce(Player player) {
        bruteForce.put(player.getUniqueId(), Long.MAX_VALUE);          // charged until the next hit
        glint(player, Kit.Ability.BRUTE_FORCE, true);
        plugin.messages().actionBar(player, "ability.brute-force");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.2f);
        return true;
    }

    /** Called for every melee hit by a player: Brute Force adds its damage once. */
    double bruteForceBonus(Player attacker) {
        Long until = bruteForce.remove(attacker.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) return 0;
        glint(attacker, Kit.Ability.BRUTE_FORCE, false);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
        attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4);
        return BRUTE_FORCE_BONUS;
    }

    private boolean coinToss(Player player) {
        Snowball coin = player.launchProjectile(Snowball.class);
        coin.setItem(ItemStack.of(Material.GOLD_NUGGET));
        coin.getPersistentDataContainer().set(coinKey, PersistentDataType.BYTE, (byte) 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.8f);
        return true;
    }

    private boolean throwTnt(Player player) {
        Location eye = player.getEyeLocation();
        TNTPrimed tnt = player.getWorld().spawn(eye.clone().add(eye.getDirection().multiply(0.6)), TNTPrimed.class, primed -> {
            primed.setFuseTicks(Math.max(20, plugin.getConfig().getInt("kits.tnt-fuse-ticks", 60)));
            primed.setSource(player);
            primed.setYield(2.5f);
        });
        tnt.setVelocity(eye.getDirection().multiply(0.85).add(new Vector(0, 0.15, 0)));
        player.getWorld().playSound(eye, Sound.ENTITY_TNT_PRIMED, 1f, 1f);
        return true;
    }

    private boolean healingZone(Match match, Player player, TeamColor team) {
        for (Player mate : match.online(team)) {
            if (mate.isDead() || mate.getLocation().distanceSquared(player.getLocation()) > 25) continue;
            var max = mate.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            mate.setHealth(Math.min(max == null ? 20 : max.getValue(), mate.getHealth() + 6));
            mate.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 0, false, true, true));
            mate.getWorld().spawnParticle(Particle.HEART, mate.getLocation().add(0, 2, 0), 4, 0.4, 0.2, 0.4);
            if (!mate.equals(player)) plugin.messages().actionBar(mate, "ability.healed-by", Messages.arg("player", player.getName()));
        }
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 0.5, 0), 60, 2.5, 0.3, 2.5);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        return true;
    }

    private boolean raiseDead(Match match, Player player, TeamColor team) {
        List<UUID> raised = undead.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        removeAll(raised);
        for (int i = 0; i < 3; i++) {
            Location spot = player.getLocation().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
            Block ground = spot.getBlock().getRelative(0, -1, 0);
            for (int down = 0; down < 4 && !ground.getType().isSolid(); down++) ground = ground.getRelative(0, -1, 0);
            if (!ground.getType().isSolid()) ground = player.getLocation().getBlock().getRelative(0, -1, 0);
            Location surface = new Location(spot.getWorld(), spot.getX(), ground.getY() + 1, spot.getZ(), random.nextFloat() * 360, 0);
            BlockData dirt = ground.getType().isSolid() ? ground.getBlockData() : Material.DIRT.createBlockData();
            // It climbs out of the ground: starts buried, still, and rises over a second and a half.
            Zombie zombie = player.getWorld().spawn(surface.clone().subtract(0, 1.9, 0), Zombie.class, z -> {
                z.setAdult();
                z.setShouldBurnInDay(false);
                z.customName(Component.text(player.getName() + "'s Undead", team.text()));
                z.setCustomNameVisible(true);
                z.getEquipment().setHelmet(Items.leather(Material.LEATHER_HELMET, team.leather()));
                z.getEquipment().setHelmetDropChance(0);
                z.setAI(false);
                z.setGravity(false);
                z.setInvulnerable(true);
                z.setSilent(true);
            });
            owners.put(zombie.getUniqueId(), player.getUniqueId());
            raised.add(zombie.getUniqueId());
            int delay = i * 6;
            for (int step = 1; step <= 15; step++) {
                int at = step;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!zombie.isValid()) return;
                    zombie.teleport(surface.clone().subtract(0, 1.9 - 1.9 * at / 15.0, 0));
                    zombie.getWorld().spawnParticle(Particle.BLOCK, surface.clone().add(0, 0.1, 0), 8, 0.3, 0.05, 0.3, dirt);
                    if (at % 5 == 1) zombie.getWorld().playSound(surface, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.7f);
                }, delay + step * 2L);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!zombie.isValid()) return;
                zombie.teleport(surface);
                zombie.setGravity(true);
                zombie.setInvulnerable(false);
                zombie.setSilent(false);
                zombie.setAI(true);
                zombie.getWorld().playSound(surface, Sound.ENTITY_ZOMBIE_AMBIENT, 1f, 0.8f);
                Match now = game.match();
                if (now != null) retarget(now, zombie, team);
            }, delay + 32L);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 0.8f);
        List<UUID> these = new ArrayList<>(raised);
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeAll(these), 400L);
        return true;
    }

    private boolean starSpin(Match match, Player player) {
        if (carryingFlag(match, player)) {
            plugin.messages().actionBar(player, "ability.not-with-flag");
            return false;
        }
        World world = player.getWorld();
        Location from = player.getLocation();
        for (int attempt = 0; attempt < 25; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = 4 + random.nextDouble() * 5;
            int x = (int) Math.floor(from.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(from.getZ() + Math.sin(angle) * distance);
            for (int dy = 3; dy >= -3; dy--) {
                int y = from.getBlockY() + dy;
                Block feet = world.getBlockAt(x, y, z);
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()
                        && !feet.isLiquid() && y > match.map().voidY + 2) {
                    world.spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3);
                    Location to = new Location(world, x + 0.5, y, z + 0.5, from.getYaw(), from.getPitch());
                    player.teleport(to);
                    world.spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3);
                    world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
                    return true;
                }
            }
        }
        plugin.messages().actionBar(player, "ability.no-room");
        return false;
    }

    private boolean fireBreath(Match match, Player player, TeamColor team) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        for (double d = 0.5; d < 6; d += 0.5) {
            player.getWorld().spawnParticle(Particle.FLAME, eye.clone().add(look.clone().multiply(d)), 4, 0.15 * d / 2, 0.15 * d / 2, 0.15 * d / 2, 0.01);
        }
        for (Player enemy : enemies(match, team, eye, 6)) {
            Vector to = enemy.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 0.01 || to.normalize().dot(look) < 0.8) continue;
            enemy.damage(2, player);
            enemy.setFireTicks(100);
        }
        player.getWorld().playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
        return true;
    }

    private boolean disguise(Match match, Player player, TeamColor team) {
        TeamColor enemy = null;
        for (TeamColor other : match.mode().teams()) if (other != team) {
            enemy = other;
            break;
        }
        if (enemy == null) return false;
        match.showAs(player, enemy);
        recolourLeather(player, enemy);
        plugin.messages().actionBar(player, "ability.disguised", Messages.comp("team", Match.teamName(enemy)));
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.01);
        BukkitTask old = disguises.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> reveal(player), 200L));
        if (old != null) old.cancel();
        return true;
    }

    /** Ends a Spy's disguise (it ran out, they attacked, or they died). */
    void reveal(Player player) {
        BukkitTask task = disguises.remove(player.getUniqueId());
        if (task == null) return;
        task.cancel();
        Match match = game.match();
        if (match == null) return;
        TeamColor team = match.teamOf(player.getUniqueId());
        if (team == null) return;
        match.showAs(player, team);
        recolourLeather(player, team);
        plugin.messages().actionBar(player, "ability.revealed");
    }

    boolean disguised(Player player) {
        return disguises.containsKey(player.getUniqueId());
    }

    private static void recolourLeather(Player player, TeamColor colour) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && piece.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta) {
                piece.editMeta(org.bukkit.inventory.meta.LeatherArmorMeta.class, meta -> meta.setColor(colour.leather()));
            }
        }
        player.getInventory().setArmorContents(player.getInventory().getArmorContents());
    }

    private boolean groundSlam(Match match, Player player, TeamColor team) {
        Location center = player.getLocation();
        for (Player enemy : enemies(match, team, center, 4.5)) {
            Vector away = enemy.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (away.lengthSquared() > 0.01) away.normalize().multiply(0.5);
            enemy.damage(3, player);
            enemy.setVelocity(away.setY(1.1));
        }
        player.getWorld().spawnParticle(Particle.EXPLOSION, center, 6, 1.5, 0.2, 1.5);
        player.getWorld().spawnParticle(Particle.BLOCK, center, 80, 2, 0.1, 2, center.clone().subtract(0, 1, 0).getBlock().getBlockData());
        player.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
        return true;
    }

    private boolean lightning(Match match, Player player, TeamColor team) {
        Block target = player.getTargetBlockExact(40);
        if (target == null) {
            plugin.messages().actionBar(player, "ability.no-target");
            return false;
        }
        Location strike = target.getLocation().add(0.5, 1, 0.5);
        player.getWorld().strikeLightningEffect(strike);
        for (Player enemy : enemies(match, team, strike, 3.5)) {
            enemy.damage(6, player);
            enemy.setFireTicks(60);
        }
        return true;
    }

    private boolean sheep(Player player, TeamColor team, Block clicked, org.bukkit.block.BlockFace face) {
        Location spot = clicked.getRelative(face).getLocation().add(0.5, 0, 0.5);
        Sheep sheep = player.getWorld().spawn(spot, Sheep.class, s -> {
            s.setColor(switch (team) {
                case RED -> DyeColor.RED;
                case BLUE -> DyeColor.BLUE;
                case GREEN -> DyeColor.LIME;
                case YELLOW -> DyeColor.YELLOW;
            });
            s.customName(Component.text("Exploding Sheep", team.text()));
            s.setCustomNameVisible(true);
            s.setAI(false);
        });
        owners.put(sheep.getUniqueId(), player.getUniqueId());
        player.getWorld().playSound(spot, Sound.ENTITY_SHEEP_AMBIENT, 1f, 1.5f);
        player.getWorld().playSound(spot, Sound.ENTITY_CREEPER_PRIMED, 1f, 1f);
        UUID owner = player.getUniqueId();
        DyeColor own = sheep.getColor();
        var scale = sheep.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        // The fuse, so you can see when it goes: a countdown over it, the wool flashing white faster and faster,
        // the sheep swelling, smoke and a tick each second, then a hiss just before it blows.
        final int fuse = 60;
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!sheep.isValid()) {
                task.cancel();
                return;
            }
            int tick = t[0]++;
            Location at = sheep.getLocation();
            if (tick >= fuse) {
                task.cancel();
                sheep.remove();
                owners.remove(sheep.getUniqueId());
                Player source = Bukkit.getPlayer(owner);
                at.getWorld().createExplosion(at, 2.5f, false, false, source);
                return;
            }
            int left = fuse - tick;
            int period = left > 40 ? 10 : left > 20 ? 6 : 3;                // flashes speed up
            sheep.setColor(tick % period < period / 2 ? DyeColor.WHITE : own);
            if (scale != null) scale.setBaseValue(1.0 + 0.45 * tick / fuse);   // swells
            if (tick % 20 == 0) {
                int seconds = (left + 19) / 20;
                sheep.customName(Component.text("💥 " + seconds, seconds <= 1 ? net.kyori.adventure.text.format.NamedTextColor.RED : team.text())
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
                at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f, 1.2f + (3 - seconds) * 0.3f);
            }
            if (left == 10) at.getWorld().playSound(at, Sound.ENTITY_TNT_PRIMED, 1.2f, 1.3f);
            at.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 1.2, 0), left < 20 ? 4 : 1, 0.2, 0.2, 0.2, 0.01);
        }, 0L, 1L);
        return true;
    }

    // ---------------------------------------------------------------- the 1.30 kits

    /** Frost Mage: every enemy within 5 blocks is slowed (Slowness II, 3 s) and frosted over. */
    private boolean frostNova(Match match, Player player, TeamColor team) {
        Location center = player.getLocation();
        int hit = 0;
        for (Player enemy : enemies(match, team, center, 5)) {
            enemy.damage(1, player);
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
            enemy.setFreezeTicks(Math.max(enemy.getFreezeTicks(), 120));     // the frost on the screen (no freeze damage below 140)
            enemy.getWorld().spawnParticle(Particle.SNOWFLAKE, enemy.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
            if (disguised(enemy)) reveal(enemy);
            hit++;
        }
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            center.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(Math.cos(a) * 4.5, 0.3, Math.sin(a) * 4.5), 2, 0.1, 0.1, 0.1, 0.01);
        }
        center.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, center.clone().add(0, 1, 0), 40, 2, 0.4, 2);
        center.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        plugin.messages().actionBar(player, "ability.frost-nova", Messages.arg("count", hit), Messages.arg("enemies", hit == 1 ? "enemy" : "enemies"));
        return true;
    }

    /** Frost Mage snowballs: marked when thrown, they slow what they hit. */
    @EventHandler(ignoreCancelled = true)
    public void onThrow(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball ball) || !(ball.getShooter() instanceof Player player)) return;
        if (ball.getPersistentDataContainer().has(coinKey) || live(player) == null) return;
        if (plugin.kits().current(player.getUniqueId()) == Kit.FROST_MAGE) ball.getPersistentDataContainer().set(frostKey, PersistentDataType.BYTE, (byte) 1);
    }

    /** Tracker: enemies within 30 blocks glow for 6 s (a Spy's disguise drops); the action bar follows your flag's carrier. */
    private boolean huntersSense(Match match, Player player, TeamColor team) {
        int seen = 0;
        for (Player enemy : enemies(match, team, player.getLocation(), 30)) {
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, false, false, true));
            if (disguised(enemy)) reveal(enemy);
            seen++;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.8f, 1.3f);
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation().add(0, 1, 0), 1);
        plugin.messages().actionBar(player, seen == 0 ? "ability.sense-none" : "ability.sense", Messages.arg("count", seen), Messages.arg("enemies", seen == 1 ? "enemy" : "enemies"));
        if (match.rules() instanceof FlagRules flags && flags.carrierOf(team) != null) tracking.put(player.getUniqueId(), System.currentTimeMillis() + 6000);
        return true;
    }

    /** Trapper: a hidden trap on the block clicked (3 at most, the oldest goes); never in a spawn. */
    private boolean setTrap(Match match, Player player, TeamColor team, Block clicked, org.bukkit.block.BlockFace face) {
        if (clicked == null || face != org.bukkit.block.BlockFace.UP || !clicked.getType().isSolid()) {
            plugin.messages().actionBar(player, "ability.trap-ground");
            return false;
        }
        Block space = clicked.getRelative(0, 1, 0);
        if (!space.isPassable() || space.isLiquid() || !space.getRelative(0, 1, 0).isPassable()) {
            plugin.messages().actionBar(player, "ability.trap-ground");
            return false;
        }
        if ("spawn".equals(match.noBuildZone(BlockPos.of(space)))) {
            plugin.messages().actionBar(player, "ability.trap-spawn");
            return false;
        }
        List<Trap> mine = traps.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        mine.removeIf(t -> t.at().getBlock().equals(space));
        if (mine.size() >= 3) mine.remove(0);
        mine.add(new Trap(space.getLocation().add(0.5, 0, 0.5), player.getUniqueId(), team));
        player.getWorld().playSound(space.getLocation(), Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 0.8f);
        plugin.messages().actionBar(player, "ability.trap-set", Messages.arg("count", mine.size()));
        return true;
    }

    /** Vampire: Strength I for 5 s, and lifesteal doubles while it lasts. */
    private boolean bloodRush(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, true, true));
        bloodRush.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
        glint(player, Kit.Ability.BLOOD_RUSH, true);
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null) glint(online, Kit.Ability.BLOOD_RUSH, false);
        }, 100L);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 20), 1.4f));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.6f);
        plugin.messages().actionBar(player, "ability.blood-rush");
        return true;
    }

    /** Vampire lifesteal: melee hits on enemies heal 20% of the damage done (40% in Blood Rush). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLifesteal(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        Match match = live(attacker);
        if (match == null || match.teamOf(victim.getUniqueId()) == match.teamOf(attacker.getUniqueId())) return;
        if (plugin.kits().current(attacker.getUniqueId()) != Kit.VAMPIRE || attacker.isDead()) return;
        Long rush = bloodRush.get(attacker.getUniqueId());
        double share = rush != null && rush > System.currentTimeMillis() ? 0.4 : 0.2;
        double heal = event.getFinalDamage() * share;
        if (heal <= 0) return;
        var max = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        attacker.setHealth(Math.min(max == null ? 20 : max.getValue(), attacker.getHealth() + heal));
        attacker.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1.2, 0), 8, 0.25, 0.3, 0.25,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 20), 1f));
    }

    /** Grappler: reeling in a hook that caught on a block flings you there (no fall damage for a few seconds after). */
    @EventHandler(ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack rod = event.getHand() == null ? player.getInventory().getItemInMainHand() : player.getInventory().getItem(event.getHand());
        if (Kit.Ability.of(Items.tagOf(rod)) != Kit.Ability.GRAPPLE) return;
        Match match = live(player);
        if (match == null || match.warmingUp()) {
            event.setCancelled(true);
            return;
        }
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.FISHING) {
            if (match.rules().carrying(player)) {
                event.setCancelled(true);
                plugin.messages().actionBar(player, "ability.grapple-carrying");
            }
            return;
        }
        var hook = event.getHook();
        // hooked an enemy: reeling in yanks them to you (2 damage, theirs to fall from)
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() instanceof Player target) {
            TeamColor theirs = match.teamOf(target.getUniqueId());
            if (theirs == null || theirs == match.teamOf(player.getUniqueId()) || match.ghost(target.getUniqueId())) return;
            Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
            double distance = pull.length();
            if (distance < 1) return;
            pull.setY(0).normalize().multiply(Math.min(1.6, 0.55 + distance * 0.09)).setY(0.42 + Math.min(0.3, distance * 0.02));
            target.damage(2, player);
            target.setVelocity(pull);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.05);
            plugin.messages().actionBar(target, "ability.grapple-yanked", Messages.arg("player", player.getName()));
            player.setCooldown(Kit.Ability.GRAPPLE.material(), Kit.Ability.GRAPPLE.cooldownSeconds() * 20);
            readyLater(player, Kit.Ability.GRAPPLE);
            return;
        }
        boolean caught = event.getState() == org.bukkit.event.player.PlayerFishEvent.State.IN_GROUND
                || ((event.getState() == org.bukkit.event.player.PlayerFishEvent.State.REEL_IN
                     || event.getState() == org.bukkit.event.player.PlayerFishEvent.State.FAILED_ATTEMPT) && touchesBlock(hook.getLocation()));
        if (!caught || match.rules().carrying(player)) return;
        Location from = player.getLocation(), to = hook.getLocation();
        Vector gap = to.toVector().subtract(from.toVector());
        double t = gap.length();
        if (t < 2) return;
        // the classic grappling hook throw: a little more than straight at it, with a lift that grows with the distance
        Vector velocity = new Vector((1.0 + 0.07 * t) * gap.getX() / t, (1.0 + 0.03 * t) * gap.getY() / t + 0.04 * t, (1.0 + 0.07 * t) * gap.getZ() / t);
        if (velocity.length() > 3.2) velocity.normalize().multiply(3.2);
        player.setVelocity(velocity);
        player.setFallDistance(0);
        grappled.put(player.getUniqueId(), System.currentTimeMillis() + 4000);
        player.getWorld().playSound(from, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        player.getWorld().playSound(from, Sound.ITEM_CROSSBOW_SHOOT, 0.8f, 1.4f);
        player.setCooldown(Kit.Ability.GRAPPLE.material(), Kit.Ability.GRAPPLE.cooldownSeconds() * 20);
        readyLater(player, Kit.Ability.GRAPPLE);
    }

    private static boolean touchesBlock(Location at) {
        for (double dx : new double[] {-0.3, 0, 0.3}) for (double dy : new double[] {-0.3, 0, 0.3}) for (double dz : new double[] {-0.3, 0, 0.3}) {
            if (at.clone().add(dx, dy, dz).getBlock().getType().isSolid()) return true;
        }
        return false;
    }

    /** No fall damage just after a grapple. */
    @EventHandler(ignoreCancelled = true)
    public void onGrappleLanding(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) return;
        Long until = grappled.get(player.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
            grappled.remove(player.getUniqueId());
        }
    }

    /** Every 4 ticks: traps spring on enemies (and show to the trapper's team), the Tracker's readout. */
    private void everyFewTicks() {
        Match match = game.match();
        if (match == null || !match.live()) return;
        ticks++;
        if (!traps.isEmpty()) {
            for (List<Trap> list : traps.values()) {
                list.removeIf(trap -> {
                    for (Player enemy : match.alive()) {
                        TeamColor theirs = match.teamOf(enemy.getUniqueId());
                        if (theirs == null || theirs == trap.team() || match.ghost(enemy.getUniqueId())) continue;
                        Location feet = enemy.getLocation();
                        double dx = feet.getX() - trap.at().getX(), dz = feet.getZ() - trap.at().getZ(), dy = feet.getY() - trap.at().getY();
                        if (dx * dx + dz * dz > 0.81 || dy < -0.2 || dy > 1.2) continue;
                        spring(match, trap, enemy);
                        return true;
                    }
                    return false;
                });
                if (ticks % 5 == 0) for (Trap trap : list) {                // the trapper's team sees a faint mark over it
                    Particle.DustOptions dust = new Particle.DustOptions(trap.team().leather(), 0.7f);
                    for (Player mate : match.online(trap.team())) {
                        if (mate.getWorld().equals(trap.at().getWorld()) && mate.getLocation().distanceSquared(trap.at()) < 24 * 24) {
                            mate.spawnParticle(Particle.DUST, trap.at().clone().add(0, 0.15, 0), 3, 0.2, 0.02, 0.2, dust);
                        }
                    }
                }
            }
            traps.values().removeIf(List::isEmpty);
        }
        if (!tracking.isEmpty()) {
            long now = System.currentTimeMillis();
            tracking.entrySet().removeIf(entry -> {
                Player tracker = Bukkit.getPlayer(entry.getKey());
                if (tracker == null || entry.getValue() < now || !(match.rules() instanceof FlagRules flags)) return true;
                if (ticks % 2 != 0) return false;
                UUID carrierId = flags.carrierOf(match.teamOf(tracker.getUniqueId()));
                Player carrier = carrierId == null ? null : Bukkit.getPlayer(carrierId);
                if (carrier == null || !carrier.getWorld().equals(tracker.getWorld())) return true;
                plugin.messages().actionBar(tracker, "ability.sense-carrier", Messages.arg("player", carrier.getName()),
                        Messages.arg("distance", (int) Math.round(carrier.getLocation().distance(tracker.getLocation()))),
                        Messages.arg("arrow", arrow(tracker, carrier.getLocation())));
                return false;
            });
        }
    }

    private int ticks;

    private void spring(Match match, Trap trap, Player enemy) {
        enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 6, false, true, true));
        enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, true));
        enemy.setVelocity(new Vector(0, Math.min(0, enemy.getVelocity().getY()), 0));
        Player owner = Bukkit.getPlayer(trap.owner());
        if (owner != null && match.teamOf(owner.getUniqueId()) == trap.team()) enemy.damage(3, owner);
        else enemy.damage(3);
        if (disguised(enemy)) reveal(enemy);
        Location at = trap.at();
        at.getWorld().playSound(at, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.2f, 0.7f);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.8f);
        at.getWorld().spawnParticle(Particle.CRIT, at.clone().add(0, 0.5, 0), 25, 0.4, 0.3, 0.4, 0.1);
        plugin.messages().actionBar(enemy, "ability.trapped");
        if (owner != null) plugin.messages().actionBar(owner, "ability.trap-sprung", Messages.arg("player", enemy.getName()));
    }

    /** An arrow pointing from where the player looks toward a place. */
    private static String arrow(Player player, Location to) {
        Location at = player.getLocation();
        double angle = Math.toDegrees(Math.atan2(-(to.getX() - at.getX()), to.getZ() - at.getZ())) - at.getYaw();
        angle = ((angle % 360) + 360 + 22.5) % 360;
        return new String[] {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"}[(int) (angle / 45) % 8];
    }

    // ---------------------------------------------------------------- placed abilities

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Kit.Ability ability = Kit.Ability.of(Items.tagOf(event.getItemInHand()));
        if (ability == Kit.Ability.NONE) return;
        Player player = event.getPlayer();
        Match match = live(player);
        if (match == null) {
            event.setCancelled(true);
            return;
        }
        TeamColor team = match.teamOf(player.getUniqueId());
        switch (ability) {
            case TURRET -> {
                event.setCancelled(true);
                if (turrets.containsKey(player.getUniqueId())) {
                    plugin.messages().actionBar(player, "ability.turret-already");
                    return;
                }
                Location spot = event.getBlockPlaced().getLocation().add(0.5, 0, 0.5);
                Shulker turret = player.getWorld().spawn(spot, Shulker.class, s -> {
                    s.setColor(team == TeamColor.RED ? DyeColor.RED : team == TeamColor.BLUE ? DyeColor.BLUE
                            : team == TeamColor.GREEN ? DyeColor.LIME : DyeColor.YELLOW);
                    s.setCustomNameVisible(true);
                    s.setRemoveWhenFarAway(false);
                });
                turretHits.put(turret.getUniqueId(), turretLife());
                turretName(turret, player.getName(), team);
                owners.put(turret.getUniqueId(), player.getUniqueId());
                turrets.put(player.getUniqueId(), turret.getUniqueId());
                retarget(match, turret, team);
                event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
                player.getWorld().playSound(spot, Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 0.8f);
                plugin.messages().actionBar(player, "ability.turret-placed");
            }
            case SPAWN_TORCH -> {
                BlockPos old = torches.put(player.getUniqueId(), BlockPos.of(event.getBlockPlaced()));
                if (old != null && !old.equals(BlockPos.of(event.getBlockPlaced()))) {
                    Block oldBlock = old.block(match.world());
                    if (oldBlock.getType() == Material.REDSTONE_TORCH || oldBlock.getType() == Material.REDSTONE_WALL_TORCH) {
                        oldBlock.setType(Material.AIR);
                    }
                }
                plugin.messages().actionBar(player, "ability.torch-placed");
                player.getWorld().playSound(event.getBlockPlaced().getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
            }
            default -> event.setCancelled(true); // other ability items are never placed
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BlockPos pos = BlockPos.of(event.getBlock());
        for (Map.Entry<UUID, BlockPos> entry : new HashMap<>(torches).entrySet()) {
            if (!entry.getValue().equals(pos)) continue;
            torches.remove(entry.getKey());
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner != null && !owner.equals(event.getPlayer())) plugin.messages().send(owner, "ability.torch-broken");
        }
    }

    /** Where a Redstone Mechanic respawns while their torch stands; null otherwise. */
    Location respawnSpot(Player player, Match match) {
        BlockPos pos = torches.get(player.getUniqueId());
        if (pos == null) return null;
        Block block = pos.block(match.world());
        if (block.getType() != Material.REDSTONE_TORCH && block.getType() != Material.REDSTONE_WALL_TORCH) {
            torches.remove(player.getUniqueId());
            return null;
        }
        return pos.center(match.world()).add(0, 0.1, 0);
    }

    // ---------------------------------------------------------------- mobs that fight for a team

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        UUID owner = owners.get(event.getEntity().getUniqueId());
        if (owner == null) return;
        Match match = game.match();
        if (match == null) {
            event.setCancelled(true);
            return;
        }
        TeamColor team = match.teamOf(owner);
        LivingEntity target = event.getTarget();
        if (target instanceof Player player && match.teamOf(player.getUniqueId()) != null && match.teamOf(player.getUniqueId()) != team
                && match.alive().contains(player)) {
            return; // an enemy: fine
        }
        event.setCancelled(true);
        if (event.getEntity() instanceof Mob mob) retarget(match, mob, team);
    }

    private void retarget(Match match, Mob mob, TeamColor team) {
        Player nearest = null;
        // Undead hunt: they go after the nearest enemy within kits.undead-hunt-range; with nobody in range they
        // stay with their Necromancer instead of standing still.
        double reach = mob instanceof Zombie ? plugin.getConfig().getInt("kits.undead-hunt-range", 40) : 24;
        double best = reach * reach;
        for (Player enemy : enemies(match, team, mob.getLocation(), reach)) {
            double distance = enemy.getLocation().distanceSquared(mob.getLocation());
            if (distance < best) {
                best = distance;
                nearest = enemy;
            }
        }
        mob.setTarget(nearest);
        if (!(mob instanceof Zombie) || !mob.hasAI()) return;
        mob.setAware(true);
        if (nearest != null) {
            mob.getPathfinder().moveTo(nearest, 1.2);
            return;
        }
        UUID ownerId = owners.get(mob.getUniqueId());
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        if (owner != null && owner.getWorld().equals(mob.getWorld()) && owner.getLocation().distanceSquared(mob.getLocation()) > 36) {
            mob.getPathfinder().moveTo(owner, 1.1);
        }
    }

    /** Every second: undead and turrets keep after the nearest enemy. */
    void tick(Match match) {
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(owners).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid()) {
                owners.remove(entry.getKey());
                continue;
            }
            if (entity instanceof Mob mob && (mob.getTarget() == null || mob.getTarget().isDead())) {
                retarget(match, mob, match.teamOf(entry.getValue()));
            }
        }
    }

    // ---------------------------------------------------------------- explosions and projectiles

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Match match = game.match();
        if (match == null || !match.world().equals(event.getEntity().getWorld())) return;
        // Only blocks players placed can be blown up, never the map.
        event.blockList().removeIf(block -> !match.placedByPlayer(BlockPos.of(block)));
        Location centre = event.getLocation();
        for (Block block : event.blockList()) {
            match.unmarkPlaced(BlockPos.of(block));
            // the block is thrown out of the blast (it breaks up where it lands; nothing is left behind)
            if (block.getType().isAir() || random.nextInt(100) >= 70) continue;
            org.bukkit.entity.FallingBlock thrown = block.getWorld().spawn(block.getLocation().add(0.5, 0.1, 0.5),
                    org.bukkit.entity.FallingBlock.class, falling -> {
                        falling.setBlockData(block.getBlockData());
                        falling.setDropItem(false);
                        falling.setHurtEntities(false);
                        falling.setCancelDrop(true);
                        falling.setPersistent(false);
                        falling.getPersistentDataContainer().set(thrownKey, PersistentDataType.BYTE, (byte) 1);
                    });
            Vector away = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(centre.toVector());
            if (away.lengthSquared() < 0.01) away = new Vector(random.nextDouble() - 0.5, 1, random.nextDouble() - 0.5);
            thrown.setVelocity(away.normalize().multiply(0.35 + random.nextDouble() * 0.35).add(new Vector(0, 0.45 + random.nextDouble() * 0.3, 0)));
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (thrown.isValid()) thrown.remove(); }, 60L);
        }
        event.setYield(0);
    }

    private final org.bukkit.NamespacedKey thrownKey = new org.bukkit.NamespacedKey("fractalctf", "thrown");

    /** A block thrown by a blast doesn't land as a block: it breaks up into dust where it comes down. */
    @EventHandler(ignoreCancelled = true)
    public void onThrownLand(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.FallingBlock falling) || !falling.getPersistentDataContainer().has(thrownKey)) return;
        event.setCancelled(true);
        falling.getWorld().spawnParticle(Particle.BLOCK, falling.getLocation(), 12, 0.25, 0.25, 0.25, falling.getBlockData());
        falling.remove();
    }

    // ---------------------------------------------------------------- the turret's life

    /** Hits each turret can still take. */
    private final Map<UUID, Integer> turretHits = new HashMap<>();

    private int turretLife() {
        return Math.max(1, plugin.getConfig().getInt("kits.turret-hits", 5));
    }

    /** "Sasha's Turret" with a heart per hit it can still take. */
    private void turretName(Entity turret, String owner, TeamColor team) {
        int left = turretHits.getOrDefault(turret.getUniqueId(), turretLife());
        turret.customName(plugin.messages().component("ability.turret-name", Messages.arg("player", owner),
                Messages.comp("team", Component.text(owner, team.text())),
                Messages.parsed("hearts", "<red>" + "❤".repeat(left) + "</red><dark_gray>" + "❤".repeat(Math.max(0, turretLife() - left)) + "</dark_gray>")));
    }

    /** An enemy hit a turret (with anything): one hit fewer; at none it breaks. It never takes real damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTurretHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Shulker turret) || !turretHits.containsKey(turret.getUniqueId())) return;
        event.setCancelled(true);
        Player attacker = responsible(event.getDamager());
        UUID ownerId = owners.get(turret.getUniqueId());
        Match match = game.match();
        if (attacker == null || match == null || ownerId == null || match.teamOf(attacker.getUniqueId()) == match.teamOf(ownerId)) return;
        long now = System.currentTimeMillis();
        if (now - turretLastHit.getOrDefault(turret.getUniqueId(), 0L) < 300) return;   // one hit per swing
        turretLastHit.put(turret.getUniqueId(), now);
        int left = turretHits.merge(turret.getUniqueId(), -1, Integer::sum);
        Player owner = Bukkit.getPlayer(ownerId);
        TeamColor team = match.teamOf(ownerId);
        turret.getWorld().playSound(turret.getLocation(), Sound.ENTITY_SHULKER_HURT, 1f, 1f);
        if (left > 0) {
            turretName(turret, owner == null ? "?" : owner.getName(), team == null ? TeamColor.RED : team);
            return;
        }
        turret.getWorld().spawnParticle(Particle.EXPLOSION, turret.getLocation().add(0, 0.5, 0), 1);
        turret.getWorld().playSound(turret.getLocation(), Sound.ENTITY_SHULKER_DEATH, 1f, 1f);
        turretHits.remove(turret.getUniqueId());
        turrets.values().remove(turret.getUniqueId());
        owners.remove(turret.getUniqueId());
        turret.remove();
        match.award(attacker, plugin.getConfig().getInt("coins.turret", 5), "turret");
        if (owner != null) plugin.messages().actionBar(owner, "ability.turret-destroyed", Messages.arg("player", attacker.getName()));
    }

    private final Map<UUID, Long> turretLastHit = new HashMap<>();

    /** Turrets stay where they're put (shulkers teleport away when hurt). */
    @EventHandler(ignoreCancelled = true)
    public void onTurretTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
        if (event.getEntity() instanceof Shulker && owners.containsKey(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    /** Nothing drops from a turret or undead (no shulker shells). */
    @EventHandler
    public void onSummonDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (!owners.containsKey(event.getEntity().getUniqueId()) && !turretHits.containsKey(event.getEntity().getUniqueId())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /** The Engineer's turret shoots to hurt, not to float people away: its bullets give no levitation. */
    @EventHandler(ignoreCancelled = true)
    public void onLevitate(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        if (event.getNewEffect() == null || event.getNewEffect().getType() != PotionEffectType.LEVITATION) return;
        if (event.getCause() != org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK) return;
        Match match = game.match();
        if (match != null && match.world().equals(event.getEntity().getWorld())) event.setCancelled(true);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (Boolean.getBoolean("ctf.debugfish") && event.getEntity() instanceof org.bukkit.entity.FishHook) plugin.getLogger().info("[fish] hit entity=" + event.getHitEntity() + " block=" + event.getHitBlock() + " cancelled=" + event.isCancelled());
        if (event.getEntity() instanceof Snowball ball && ball.getPersistentDataContainer().has(frostKey)
                && event.getHitEntity() instanceof Player target && ball.getShooter() instanceof Player thrower) {
            Match match = game.match();
            if (match == null || !match.live() || match.teamOf(target.getUniqueId()) == null
                    || match.teamOf(target.getUniqueId()) == match.teamOf(thrower.getUniqueId())) return;
            target.damage(1, thrower);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, true, true));
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), 60));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
            return;
        }
        if (!(event.getEntity() instanceof Snowball coin) || !coin.getPersistentDataContainer().has(coinKey)) return;
        if (!(event.getHitEntity() instanceof Player target) || !(coin.getShooter() instanceof Player thrower)) return;
        Match match = game.match();
        if (match == null || match.teamOf(target.getUniqueId()) == match.teamOf(thrower.getUniqueId())) return;
        target.damage(4, thrower);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof AbstractArrow arrow)) return;
        Match match = live(player);
        if (match == null) return;
        Kit kit = plugin.kits().current(player.getUniqueId());
        if (kit == Kit.FIRE_ARCHER && random.nextDouble() < 0.35) {
            arrow.setFireTicks(200);
        } else if (kit == Kit.SEEKING_ARCHER && arrow instanceof Arrow) {
            seek(match, arrow, match.teamOf(player.getUniqueId()), player);
        }
    }

    /**
     * Seeking Archer's arrows: once in flight, an arrow locks on to the enemy most in front of it (within
     * kits.seek-range blocks and a wide cone) and keeps that target, aiming where they will be when it gets there.
     * Each tick it turns a little towards them (at most kits.seek-turn-degrees), harder the closer it gets, and its
     * speed is kept up so it doesn't drop short. It can still miss: a sharp dodge, cover, or a target that is too far
     * round to the side.
     */
    private void seek(Match match, AbstractArrow arrow, TeamColor team, Player shooter) {
        final int[] steps = {0};
        final Player[] locked = {null};
        // Only a nudge onto someone the archer could actually see when letting go, close to where the arrow is,
        // and only over the first stretch of its flight: no curving round corners onto players miles away.
        double range = plugin.getConfig().getDouble("kits.seek-lock-range", 12);
        double flight = plugin.getConfig().getDouble("kits.seek-max-flight", 32);
        Location launched = arrow.getLocation();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Player enemy : enemies(match, team, shooter.getLocation(), flight + range)) {
            if (shooter.hasLineOfSight(enemy)) seen.add(enemy.getUniqueId());
        }
        double maxTurn = Math.toRadians(plugin.getConfig().getDouble("kits.seek-turn-degrees", 9));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arrow.isValid() || arrow.isInBlock() || arrow.isOnGround() || ++steps[0] > 80) {
                task.cancel();
                return;
            }
            Vector velocity = arrow.getVelocity();
            double speed = velocity.length();
            if (speed < 0.25 || steps[0] < 2) return;                 // leaves the bow straight first
            Location here = arrow.getLocation();
            if (here.distanceSquared(launched) > flight * flight) {    // flown far enough: it's a plain arrow now
                task.cancel();
                return;
            }
            Player target = locked[0];
            if (target != null && (!target.isValid() || target.isDead() || match.ghost(target.getUniqueId())
                    || !target.getWorld().equals(here.getWorld()) || target.getLocation().distanceSquared(here) > range * range * 1.6)) {
                target = null;
            }
            if (target == null) {
                double best = Double.MAX_VALUE;
                for (Player enemy : enemies(match, team, here, range)) {
                    if (!seen.contains(enemy.getUniqueId())) continue; // the archer couldn't see them
                    Vector to = aimPoint(enemy, here, speed).subtract(here.toVector());
                    double ahead = to.clone().normalize().dot(velocity.clone().normalize());
                    if (ahead < 0.5) continue;                        // behind or off to the side: not this one
                    var blocked = here.getWorld().rayTraceBlocks(here, to.clone().normalize(), to.length(),
                            org.bukkit.FluidCollisionMode.NEVER, true);
                    if (blocked != null) continue;                    // a wall in the way
                    double score = to.length() * (1.6 - ahead);       // near and straight ahead first
                    if (score < best) {
                        best = score;
                        target = enemy;
                    }
                }
                locked[0] = target;
            }
            if (target == null) return;
            Vector want = aimPoint(target, here, speed).subtract(here.toVector());
            double distance = want.length();
            want.normalize();
            Vector now = velocity.clone().normalize();
            double angle = now.angle(want);
            if (Double.isNaN(angle) || angle < 1e-4) return;
            // turn harder when close, so a near miss still curves in
            double turn = Math.min(angle, maxTurn * (distance < 6 ? 1.8 : distance < 12 ? 1.3 : 1.0));
            Vector axis = now.getCrossProduct(want);
            if (axis.lengthSquared() < 1e-8) return;
            Vector turned = now.rotateAroundAxis(axis.normalize(), turn);
            arrow.setVelocity(turned.multiply(Math.max(speed, 1.6)));
        }, 1L, 1L);
    }

    /** Where to aim at a player: their chest, led by how they are moving and how long the arrow takes to get there. */
    private static Vector aimPoint(Player target, Location from, double speed) {
        Vector chest = target.getLocation().add(0, 1.0, 0).toVector();
        double ticks = Math.min(12, chest.distance(from.toVector()) / Math.max(0.8, speed));
        Vector moving = target.getVelocity().clone();
        moving.setY(Math.max(-0.2, Math.min(0.2, moving.getY())));
        return chest.add(moving.multiply(ticks));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        // Fire from a teammate's flame arrow or Fire Breath doesn't burn you.
        Match match = game.match();
        if (match == null || !(event.getEntity() instanceof Player target)) return;
        Player source = responsible(event.getCombuster());
        if (source != null && !source.equals(target) && match.teamOf(source.getUniqueId()) == match.teamOf(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) return;
        Match match = game.match();
        if (match != null && carryingFlag(match, event.getPlayer())) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "ability.not-with-flag");
        }
    }

    private static boolean carryingFlag(Match match, Player player) {
        return match.rules() instanceof FlagRules flags && flags.carriedBy(player.getUniqueId()) != null;
    }

    // ---------------------------------------------------------------- cleaning up

    /** A player died or left: their turret and undead go, a disguise ends, Brute Force is lost. */
    void lifeOver(Player player) {
        if (bruteForce.remove(player.getUniqueId()) != null) glint(player, Kit.Ability.BRUTE_FORCE, false);
        traps.remove(player.getUniqueId());                            // a Trapper's traps go when they die
        if (bloodRush.remove(player.getUniqueId()) != null) glint(player, Kit.Ability.BLOOD_RUSH, false);
        tracking.remove(player.getUniqueId());
        grappled.remove(player.getUniqueId());
        reveal(player);
        UUID turret = turrets.remove(player.getUniqueId());
        if (turret != null) remove(turret);
        List<UUID> raised = undead.remove(player.getUniqueId());
        if (raised != null) removeAll(raised);
    }

    /** The match is over: everything spawned by abilities goes. */
    void clear() {
        for (UUID id : new HashSet<>(owners.keySet())) remove(id);
        owners.clear();
        turrets.clear();
        undead.clear();
        torches.clear();
        bruteForce.clear();
        traps.clear();
        bloodRush.clear();
        tracking.clear();
        grappled.clear();
        for (BukkitTask task : disguises.values()) task.cancel();
        disguises.clear();
    }

    private void removeAll(List<UUID> ids) {
        for (UUID id : ids) remove(id);
        ids.clear();
    }

    private void remove(UUID id) {
        owners.remove(id);
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) entity.remove();
    }

    Set<UUID> spawned() {
        return owners.keySet();
    }
}
