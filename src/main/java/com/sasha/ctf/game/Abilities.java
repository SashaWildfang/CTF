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

    Abilities(FractalCTF plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        this.coinKey = new NamespacedKey(plugin, "coin");
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
        if (ability == Kit.Ability.NONE || ability == Kit.Ability.TURRET || ability == Kit.Ability.SPAWN_TORCH) return;
        Player player = event.getPlayer();
        event.setCancelled(true);
        Match match = live(player);
        if (match == null) return;
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
            default -> false;
        };
        if (used && ability.cooldownSeconds() > 0) player.setCooldown(ability.material(), ability.cooldownSeconds() * 20);
    }

    private boolean effect(Player player, PotionEffectType type, int amplifier, int ticks, Sound sound) {
        player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
        player.getWorld().playSound(player.getLocation(), sound, 1f, 1.2f);
        return true;
    }

    private boolean bruteForce(Player player) {
        bruteForce.put(player.getUniqueId(), System.currentTimeMillis() + 10_000);
        plugin.messages().actionBar(player, "ability.brute-force");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.2f);
        return true;
    }

    /** Called for every melee hit by a player: Brute Force adds its damage once. */
    double bruteForceBonus(Player attacker) {
        Long until = bruteForce.remove(attacker.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) return 0;
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
        UUID owner = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!sheep.isValid()) return;
            Location at = sheep.getLocation();
            sheep.remove();
            owners.remove(sheep.getUniqueId());
            Player source = Bukkit.getPlayer(owner);
            at.getWorld().createExplosion(at, 2.5f, false, false, source);
        }, 60L);
        return true;
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
        // Undead only go after someone close by; with nobody near they stand where they rose.
        double reach = mob instanceof Zombie ? plugin.getConfig().getInt("kits.undead-range", 10) : 24;
        double best = reach * reach;
        for (Player enemy : enemies(match, team, mob.getLocation(), reach)) {
            double distance = enemy.getLocation().distanceSquared(mob.getLocation());
            if (distance < best) {
                best = distance;
                nearest = enemy;
            }
        }
        mob.setTarget(nearest);
        if (mob instanceof Zombie && mob.hasAI()) mob.setAware(nearest != null);
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
        match.award(attacker, plugin.getConfig().getInt("coins.turret", 2), "turret");
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
        Kit kit = plugin.kits().of(player.getUniqueId());
        if (kit == Kit.FIRE_ARCHER && random.nextDouble() < 0.35) {
            arrow.setFireTicks(200);
        } else if (kit == Kit.SEEKING_ARCHER && arrow instanceof Arrow) {
            seek(match, arrow, match.teamOf(player.getUniqueId()));
        }
    }

    /**
     * Seeking Archer's arrows: once in flight, an arrow locks on to the enemy most in front of it (within
     * kits.seek-range blocks and a wide cone) and keeps that target, aiming where they will be when it gets there.
     * Each tick it turns a little towards them (at most kits.seek-turn-degrees), harder the closer it gets, and its
     * speed is kept up so it doesn't drop short. It can still miss: a sharp dodge, cover, or a target that is too far
     * round to the side.
     */
    private void seek(Match match, AbstractArrow arrow, TeamColor team) {
        final int[] steps = {0};
        final Player[] locked = {null};
        double range = plugin.getConfig().getDouble("kits.seek-range", 24);
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
            Player target = locked[0];
            if (target != null && (!target.isValid() || target.isDead() || match.ghost(target.getUniqueId())
                    || !target.getWorld().equals(here.getWorld()) || target.getLocation().distanceSquared(here) > range * range * 1.6)) {
                target = null;
            }
            if (target == null) {
                double best = Double.MAX_VALUE;
                for (Player enemy : enemies(match, team, here, range)) {
                    Vector to = aimPoint(enemy, here, speed).subtract(here.toVector());
                    double ahead = to.clone().normalize().dot(velocity.clone().normalize());
                    if (ahead < 0.25) continue;                       // behind or far to the side: not this one
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
        bruteForce.remove(player.getUniqueId());
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
