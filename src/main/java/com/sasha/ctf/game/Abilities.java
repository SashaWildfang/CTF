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
            primed.setFuseTicks(30);
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
            Zombie zombie = player.getWorld().spawn(spot, Zombie.class, z -> {
                z.setAdult();
                z.setShouldBurnInDay(false);
                z.customName(Component.text(player.getName() + "'s Undead", team.text()));
                z.setCustomNameVisible(true);
                z.getEquipment().setHelmet(Items.leather(Material.LEATHER_HELMET, team.leather()));
                z.getEquipment().setHelmetDropChance(0);
            });
            owners.put(zombie.getUniqueId(), player.getUniqueId());
            raised.add(zombie.getUniqueId());
            retarget(match, zombie, team);
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
                    s.customName(Component.text(player.getName() + "'s Turret", team.text()));
                    s.setCustomNameVisible(true);
                });
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
        double best = 24 * 24;
        for (Player enemy : enemies(match, team, mob.getLocation(), 24)) {
            double distance = enemy.getLocation().distanceSquared(mob.getLocation());
            if (distance < best) {
                best = distance;
                nearest = enemy;
            }
        }
        mob.setTarget(nearest);
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
        for (Block block : event.blockList()) match.unmarkPlaced(BlockPos.of(block));
        event.setYield(0);
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

    private void seek(Match match, AbstractArrow arrow, TeamColor team) {
        final int[] steps = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arrow.isValid() || arrow.isInBlock() || ++steps[0] > 40) {
                task.cancel();
                return;
            }
            Vector velocity = arrow.getVelocity();
            double speed = velocity.length();
            if (speed < 0.2) return;
            Player best = null;
            double bestDistance = 20 * 20;
            for (Player enemy : enemies(match, team, arrow.getLocation(), 20)) {
                Vector to = enemy.getLocation().add(0, 1.1, 0).toVector().subtract(arrow.getLocation().toVector());
                if (to.clone().normalize().dot(velocity.clone().normalize()) < 0.4) continue;
                if (to.lengthSquared() < bestDistance) {
                    bestDistance = to.lengthSquared();
                    best = enemy;
                }
            }
            if (best == null) return;
            Vector to = best.getLocation().add(0, 1.1, 0).toVector().subtract(arrow.getLocation().toVector()).normalize();
            arrow.setVelocity(velocity.normalize().multiply(0.65).add(to.multiply(0.35)).normalize().multiply(speed));
        }, 2L, 2L);
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
