/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.kit.Kit;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.MatchState;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.Location;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything that happens to players in a match world: joining, fighting, dying, building, armour. */
final class GameListener implements Listener {

    private static final Set<PotionEffectType> HARMFUL = Set.of(PotionEffectType.INSTANT_DAMAGE, PotionEffectType.POISON,
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.WITHER, PotionEffectType.BLINDNESS);

    private final FractalCTF plugin;
    private final GameManager game;
    private final Abilities abilities;

    GameListener(FractalCTF plugin, GameManager game, Abilities abilities) {
        this.plugin = plugin;
        this.game = game;
        this.abilities = abilities;
    }

    /** The match, if this entity is in its world. */
    private Match matchOf(Entity entity) {
        Match match = game.match();
        return match != null && match.world().equals(entity.getWorld()) ? match : null;
    }

    // ---------------------------------------------------------------- joining and leaving

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Match match = game.match();
        if (match != null && (game.state() == MatchState.READY || game.state() == MatchState.PLAYING)) {
            game.arrive(player);
        } else if (match != null && game.state() == MatchState.ENDING) {
            match.attach(player);
            player.teleport(match.spectatorSpawn());
            match.afterMatch(player);
        } else {
            game.noMatch(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Match match = matchOf(event.getPlayer());
        abilities.lifeOver(event.getPlayer());
        if (match != null) match.quit(event.getPlayer());
    }

    // ---------------------------------------------------------------- moving

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Match match = matchOf(player);
        if (match == null || player.getGameMode() == GameMode.SPECTATOR) return;
        if (match.ghost(player.getUniqueId())) {
            // the dead and spectators fly around freely, but not out of the world
            if (event.getTo().getY() < match.map().voidY + 2) {
                Location up = event.getTo().clone();
                up.setY(match.map().voidY + 12);
                event.setTo(up);
            }
            return;
        }
        if (match.teamOf(player.getUniqueId()) == null) return;
        if (match.paused() && match.live() && !player.hasPermission("ctf.admin")) {
            // paused: look around, but stay put
            Location from = event.getFrom(), to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location back = from.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                event.setTo(back);
            }
            return;
        }
        if (event.getTo().getY() < match.map().voidY) {
            if (match.warmingUp()) {                                 // Limbo: falling off just puts you back at base
                player.setFallDistance(0);
                player.teleport(match.spawnOf(player));
                return;
            }
            if (match.live()) {
                voided.add(player.getUniqueId());
                player.setHealth(0);
            } else {
                player.teleport(match.spectatorSpawn());
            }
            return;
        }
        if (match.contested() && !player.isDead()) match.rules().onMove(player, event.getTo());
    }

    // ---------------------------------------------------------------- fighting

    /** Keeps the health under everyone's name up to date. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        healthLater(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        healthLater(event.getEntity());
    }

    private void healthLater(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof Player player)) return;
        Match match = matchOf(player);
        if (match != null) Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) match.showHealth(player); });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Match match = matchOf(player);
        if (match == null) return;
        if (!match.live() || match.paused() || match.ghost(player.getUniqueId()) || match.warmingUp()) {
            event.setCancelled(true);                                // (no damage at all in the warm-up: falls, the void, fire)
            if (match.warmingUp() && event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.setFallDistance(0);
                player.teleport(match.spawnOf(player));
            }
        } else if (match.spawnProtected(player)) {
            event.setCancelled(true);
            // An enemy hit (or shot) someone who just spawned: tell them why it did nothing.
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                Player attacker = abilities.responsible(byEntity.getDamager());
                if (attacker != null && !attacker.equals(player) && match.teamOf(attacker.getUniqueId()) != null
                        && match.teamOf(attacker.getUniqueId()) != match.teamOf(player.getUniqueId())) {
                    spawnProtectedHit(attacker, player);
                }
            }
        }
    }

    private final Map<UUID, Long> protectedNotice = new HashMap<>();

    /** "X just spawned and is protected" in chat and over the hotbar, at most every two seconds. */
    private void spawnProtectedHit(Player attacker, Player victim) {
        long now = System.currentTimeMillis();
        plugin.messages().actionBar(attacker, "game.spawn-protected", Messages.arg("player", victim.getName()));
        if (now - protectedNotice.getOrDefault(attacker.getUniqueId(), 0L) < 2000) return;
        protectedNotice.put(attacker.getUniqueId(), now);
        plugin.messages().send(attacker, "game.spawn-protected-chat", Messages.arg("player", victim.getName()));
        attacker.playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
    }

    /** Arrows and snowballs fly straight through the dead and spectators. */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player hit)) return;
        Match match = matchOf(hit);
        if (match != null && match.ghost(hit.getUniqueId())) event.setCancelled(true);
    }

    /** The dead and spectators can't touch anything: no items, blocks, doors or abilities. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onGhostUse(PlayerInteractEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null || !match.ghost(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND && event.getAction() != Action.PHYSICAL) {
            String tag = Items.tagOf(event.getItem());
            if (tag != null) game.spectatorTools().use(event.getPlayer(), tag);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpectatorHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match != null) Bukkit.getScheduler().runTask(plugin, () -> match.spectatorHeld(event.getPlayer()));
    }

    /** After the match: the Back to Lobby bed. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeaveItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) return;
        Match match = matchOf(event.getPlayer());
        if (match == null || match.phase() != Match.Phase.OVER || match.ghost(event.getPlayer().getUniqueId())) return;
        if (!SpectatorTools.HUB.equals(Items.tagOf(event.getItem()))) return;
        event.setCancelled(true);
        // the match is over: no countdown, straight back
        com.sasha.ctf.util.LeaveTimer.cancel(event.getPlayer());
        com.sasha.ctf.util.Network.send(plugin, event.getPlayer(), plugin.ctfConfig().hubServer());
    }

    /** Things that keep items (chests, barrels, furnaces, hoppers, shulker boxes, ender chests, chest boats...). */
    private static boolean container(org.bukkit.inventory.Inventory inventory) {
        if (inventory.getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) return true;
        var holder = inventory.getHolder(false);
        return holder instanceof org.bukkit.block.Container || holder instanceof org.bukkit.block.DoubleChest
                || holder instanceof org.bukkit.entity.minecart.StorageMinecart || holder instanceof org.bukkit.entity.minecart.HopperMinecart
                || holder instanceof org.bukkit.entity.ChestBoat || holder instanceof org.bukkit.block.DecoratedPot;
    }

    private final java.util.Map<java.util.UUID, Long> lockedNotice = new java.util.HashMap<>();

    /** Chests and other containers on the maps are locked for players (game.lock-containers). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onContainer(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !plugin.getConfig().getBoolean("game.lock-containers", false)) return;
        if (matchOf(player) == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE || !container(event.getInventory())) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - lockedNotice.getOrDefault(player.getUniqueId(), 0L) > 2000) {
            lockedNotice.put(player.getUniqueId(), now);
            plugin.messages().actionBar(player, "game.container-locked");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 0.8f, 1f);
        }
    }

    /** Decorated pots take items with a right-click, without opening anything. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPot(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != org.bukkit.Material.DECORATED_POT || !plugin.getConfig().getBoolean("game.lock-containers", false)) return;
        Player player = event.getPlayer();
        if (matchOf(player) == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    /** The team helmet (spectators and players who haven't picked yet): the team menu. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeamHelmet(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) return;
        if (!TeamPicker.TAG.equals(Items.tagOf(event.getItem()))) return;
        Match match = matchOf(event.getPlayer());
        if (match == null) return;
        event.setCancelled(true);
        game.teamPicker().open(event.getPlayer(), match);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGhostEntity(PlayerInteractEntityEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match != null && match.ghost(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Match match = game.match();
        if (match == null || !match.world().equals(event.getEntity().getWorld())) return;
        Player attacker = abilities.responsible(event.getDamager());
        if (!(event.getEntity() instanceof Player victim)) {
            // A turret, undead or sheep: its own team can't hurt it.
            Player owner = abilities.responsible(event.getEntity());
            if (owner != null && attacker != null && match.teamOf(owner.getUniqueId()) == match.teamOf(attacker.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        if (attacker == null || attacker.equals(victim)) return;
        if (match.ghost(attacker.getUniqueId()) || match.ghost(victim.getUniqueId()) || match.paused()) {
            event.setCancelled(true);
            return;
        }
        TeamColor a = match.teamOf(attacker.getUniqueId()), v = match.teamOf(victim.getUniqueId());
        if (a == null || a == v) {
            event.setCancelled(true);
            return;
        }
        if (abilities.disguised(attacker)) abilities.reveal(attacker);
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true); // 1.8-style combat: no sweeping
            return;
        }
        if (match.spawnProtected(victim)) {
            event.setCancelled(true);
            spawnProtectedHit(attacker, victim);
            return;
        }
        if (event.getDamager() instanceof Player) {
            // Brute Force: the charged hit (after the checks above, so a blocked hit doesn't use it up)
            double bonus = abilities.bruteForceBonus(attacker);
            if (bonus > 0) {
                event.setDamage(event.getDamage() + bonus);
                double dealt = event.getFinalDamage();
                plugin.messages().send(attacker, "ability.brute-force-hit", Messages.arg("player", victim.getName()),
                        Messages.arg("damage", String.format(java.util.Locale.ROOT, "%.1f", dealt / 2.0)),
                        Messages.arg("bonus", String.format(java.util.Locale.ROOT, "%.1f", bonus / 2.0)));
            }
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            match.shot(victim, attacker, attacker.getLocation().distance(victim.getLocation()));
        }
        match.hit(victim, attacker);
        // A bow hit: tell the archer how much health they left (once the damage is in; a kill says so itself).
        if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow && event.getFinalDamage() > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!attacker.isOnline() || victim.isDead() || !victim.isOnline()) return;
                double health = Math.max(0.5, Math.ceil(victim.getHealth() * 2) / 2.0);
                plugin.messages().send(attacker, "game.bow-hit", Messages.comp("victim", net.kyori.adventure.text.Component.text(victim.getName(), match.teamOf(victim.getUniqueId()) == null ? net.kyori.adventure.text.format.NamedTextColor.WHITE : match.teamOf(victim.getUniqueId()).text())),
                        Messages.arg("health", health % 1 == 0 ? String.valueOf((int) health) : String.valueOf(health)));
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        Match match = matchOf(event.getEntity());
        if (match == null || !(event.getPotion().getShooter() instanceof Player thrower)) return;
        TeamColor team = match.teamOf(thrower.getUniqueId());
        boolean harmful = false;
        for (PotionEffect effect : event.getPotion().getEffects()) if (HARMFUL.contains(effect.getType())) harmful = true;
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (!(entity instanceof Player target)) continue;
            boolean teammate = team != null && team == match.teamOf(target.getUniqueId());
            // Harmful potions skip your team (but not you); healing skips the enemy.
            if (harmful && teammate && !target.equals(thrower) || !harmful && !teammate && !target.equals(thrower)) {
                event.setIntensity(target, 0);
            } else if (harmful && !teammate) {
                match.hit(target, thrower);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Match match = matchOf(victim);
        if (match == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
        abilities.lifeOver(victim);
        Player killer = victim.getKiller();
        var last = victim.getLastDamageCause();
        if (killer == null && last instanceof EntityDamageByEntityEvent cause) {
            killer = abilities.responsible(cause.getDamager());
        }
        Match.Cause how = Match.Cause.OTHER;
        double distance = 0;
        boolean undead = last instanceof EntityDamageByEntityEvent byMob && byMob.getDamager() instanceof org.bukkit.entity.Zombie
                && abilities.responsible(byMob.getDamager()) != null;
        if (undead) {
            how = Match.Cause.UNDEAD;
        } else if (voided.remove(victim.getUniqueId())) {
            how = Match.Cause.VOID;
        } else if (last != null) {
            how = switch (last.getCause()) {
                case PROJECTILE -> Match.Cause.ARROW;
                case FALL -> Match.Cause.FALL;
                case VOID -> Match.Cause.VOID;
                case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE -> Match.Cause.FIRE;
                case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> Match.Cause.EXPLOSION;
                case MAGIC, POISON, WITHER, LIGHTNING, DRAGON_BREATH -> Match.Cause.MAGIC;
                case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, THORNS -> Match.Cause.MELEE;
                default -> Match.Cause.OTHER;
            };
            if (how == Match.Cause.ARROW && killer != null) distance = killer.getLocation().distance(victim.getLocation());
        }
        match.died(victim, killer, how, distance);
    }

    private final java.util.Set<java.util.UUID> voided = new java.util.HashSet<>();

    /** Golden apples: no chugging, there's a cooldown after each one. */
    @EventHandler(ignoreCancelled = true)
    public void onEat(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (matchOf(event.getPlayer()) == null) return;
        var type = event.getItem().getType();
        if (type != org.bukkit.Material.GOLDEN_APPLE && type != org.bukkit.Material.ENCHANTED_GOLDEN_APPLE) return;
        Match eating = matchOf(event.getPlayer());
        if (eating.warmingUp()) {
            event.setCancelled(true);
            eating.warmupNotice(event.getPlayer());
            return;
        }
        int ticks = plugin.ctfConfig().appleCooldown() * 20;
        if (ticks <= 0) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.setCooldown(org.bukkit.Material.GOLDEN_APPLE, ticks);
            player.setCooldown(org.bukkit.Material.ENCHANTED_GOLDEN_APPLE, ticks);
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Match match = game.match();
        if (match == null) return;
        TeamColor team = match.teamOf(player.getUniqueId());
        if (team == null || !match.live()) {
            event.setRespawnLocation(match.spectatorSpawn());
            Bukkit.getScheduler().runTask(plugin, () -> match.afterMatch(player));
            return;
        }
        // Watch where you died for a few seconds, then respawn at base (or your torch).
        event.setRespawnLocation(match.ghostSpot(player));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && match.world().equals(player.getWorld())) {
                match.startRespawn(player);
                match.showHealth(player);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (matchOf(event.getEntity()) != null) event.setCancelled(true);
    }

    // ---------------------------------------------------------------- building

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) return;
        BlockPos pos = BlockPos.of(event.getBlock());
        if (!match.live() || match.paused() || match.ghost(event.getPlayer().getUniqueId())
                || match.teamOf(event.getPlayer().getUniqueId()) == null) {
            event.setCancelled(true);
            return;
        }
        if (match.warmingUp()) {
            event.setCancelled(true);
            match.warmupNotice(event.getPlayer());
            return;
        }
        switch (match.rules().onPlace(event.getPlayer(), event.getBlockPlaced(), event.getItemInHand())) {
            case DENY -> {
                event.setCancelled(true);
                return;
            }
            case ALLOW -> {
                return;                                              // an objective (a wool on its podium): not a player block
            }
            case PASS -> { }
        }
        if (match.map().buildHeight != 0 && pos.y() > match.map().buildHeight) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "game.build-height", com.sasha.ctf.config.Messages.arg("height", match.map().buildHeight));
            return;
        }
        String zone = match.noBuildZone(pos);
        if (zone != null) {
            event.setCancelled(true);
            match.zoneNotice(event.getPlayer(), zone);
            return;
        }
        match.markPlaced(pos);
    }

    /** Objective blocks that react to a click (Capture the Wool's wool chests). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onObjectiveClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Match match = matchOf(event.getPlayer());
        if (match == null || !match.live() || match.paused() || match.warmingUp()
                || match.teamOf(event.getPlayer().getUniqueId()) == null || match.ghost(event.getPlayer().getUniqueId())) return;
        if (!match.contested()) return;
        if (match.rules().onInteract(event.getPlayer(), event.getClickedBlock())) event.setCancelled(true);
    }

    /** Destroy the Monument: enemy monument blocks break at the first hit. */
    @EventHandler(ignoreCancelled = true)
    public void onDamageBlock(org.bukkit.event.block.BlockDamageEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null || !match.live() || match.warmingUp()) return;
        if (match.rules().instantBreak(event.getPlayer(), event.getBlock())) event.setInstaBreak(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) return;
        if (!match.live() || match.paused() || match.ghost(event.getPlayer().getUniqueId())
                || match.teamOf(event.getPlayer().getUniqueId()) == null) {
            event.setCancelled(true);
            return;
        }
        BlockPos pos = BlockPos.of(event.getBlock());
        if (match.warmingUp()) {
            event.setCancelled(true);
            match.warmupNotice(event.getPlayer());
            return;
        }
        if (!match.contested() && match.objectiveBlock(pos)) {      // only one team here: the objectives wait for the other
            plugin.messages().actionBar(event.getPlayer(), "game.waiting-for-teams-bar");
            event.setCancelled(true);
            return;
        }
        switch (match.rules().onBreak(event.getPlayer(), event.getBlock())) {
            case DENY -> event.setCancelled(true);
            case ALLOW -> event.setDropItems(false);
            case PASS -> {
                if (match.rules().noDrops(event.getBlock())) event.setDropItems(false);
                String zone = match.noBuildZone(pos);
                if (zone != null) {
                    event.setCancelled(true);
                    match.zoneNotice(event.getPlayer(), zone);
                } else if (match.placedByPlayer(pos)) {
                    match.unmarkPlaced(pos);
                } else if (!plugin.ctfConfig().breakMapBlocks() || UNBREAKABLE.contains(event.getBlock().getType())) {
                    event.setCancelled(true);
                    plugin.messages().actionBar(event.getPlayer(), "game.cant-break");
                }
                // otherwise it breaks and drops like normal
            }
        }
    }

    private static final java.util.Set<org.bukkit.Material> UNBREAKABLE = java.util.EnumSet.of(org.bukkit.Material.BEACON,
            org.bukkit.Material.BARRIER, org.bukkit.Material.BEDROCK, org.bukkit.Material.LIGHT);

    // ---------------------------------------------------------------- items

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (matchOf(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Match match = matchOf(event.getEntity());
        if (match != null && (!match.live() || event.getEntity() instanceof Player player
                && (match.teamOf(player.getUniqueId()) == null || match.ghost(player.getUniqueId())))) {
            event.setCancelled(true);
        }
    }

    /** Not playing right now (watching, respawning, before the start or after the end): their tools stay where they are. */
    private boolean toolsLocked(Player player) {
        Match match = matchOf(player);
        if (match == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return false;
        UUID id = player.getUniqueId();
        return !match.live() || match.teamOf(id) == null || match.spectating(id) || match.ghost(id);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onToolClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !toolsLocked(player)) return;
        // plugin menus (kits, teams, spectator heads) run their own clicks and already keep every item in place
        if (event.getView().getTopInventory().getHolder(false) instanceof com.sasha.ctf.util.Menu) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onToolDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && toolsLocked(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onToolSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (toolsLocked(event.getPlayer())) event.setCancelled(true);
    }

    /**
     * Armour in a map's chests: taking a piece out puts it straight on, and what was worn there goes into the chest in
     * its place (nothing is lost). Not the helmet while carrying a flag (the banner is there).
     */
    /** Warm-up and the countdown: chests, barrels and the like stay shut until the clock starts. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWarmupContainer(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var holder = event.getInventory().getHolder(false);
        if (!(holder instanceof org.bukkit.block.Container) && !(holder instanceof org.bukkit.block.DoubleChest)) return;
        Match match = matchOf(player);
        if (match == null || player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (match.phase() != Match.Phase.COUNTDOWN && !match.warmingUp()) return;
        event.setCancelled(true);
        plugin.messages().actionBar(player, "game.chests-warmup");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChestArmour(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || toolsLocked(player)) return;
        Match match = matchOf(player);
        if (match == null || !match.live() || event.getClickedInventory() == null) return;
        var top = event.getView().getTopInventory();
        if (event.getClickedInventory() != top || top.getHolder(false) instanceof com.sasha.ctf.util.Menu) return;
        if (!(top.getHolder(false) instanceof org.bukkit.block.Container) && !(top.getHolder(false) instanceof org.bukkit.block.DoubleChest)) return;
        ItemStack piece = event.getCurrentItem();
        org.bukkit.inventory.EquipmentSlot slot = armourSlot(piece);
        if (slot == null) return;
        event.setCancelled(true);
        if (slot == org.bukkit.inventory.EquipmentSlot.HEAD && match.rules().carrying(player)) {
            plugin.messages().actionBar(player, "game.armour-flag");
            return;
        }
        ItemStack worn = player.getInventory().getItem(slot);
        ItemStack one = piece.clone();
        one.setAmount(1);
        piece.setAmount(piece.getAmount() - 1);
        ItemStack left = piece.getAmount() > 0 ? piece : null;
        if (left == null) event.getClickedInventory().setItem(event.getSlot(), worn == null || worn.getType().isAir() ? null : worn);
        else if (worn != null && !worn.getType().isAir()) player.getInventory().addItem(worn);      // a stack: keep the old piece
        player.getInventory().setItem(slot, one);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
        plugin.messages().actionBar(player, "game.armour-equipped", Messages.arg("item", com.sasha.ctf.kit.Kit.nice(one.getType())));
    }

    private static org.bukkit.inventory.EquipmentSlot armourSlot(ItemStack item) {
        if (item == null) return null;
        String name = item.getType().name();
        if (name.endsWith("_HELMET")) return org.bukkit.inventory.EquipmentSlot.HEAD;
        if (name.endsWith("_CHESTPLATE")) return org.bukkit.inventory.EquipmentSlot.CHEST;
        if (name.endsWith("_LEGGINGS")) return org.bukkit.inventory.EquipmentSlot.LEGS;
        if (name.endsWith("_BOOTS")) return org.bukkit.inventory.EquipmentSlot.FEET;
        return null;
    }

    /** Armour stays on: kit armour, the team colours and a carried flag can't be taken off or swapped. */
    @EventHandler(ignoreCancelled = true)
    public void onArmour(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || matchOf(player) == null) return;
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
            return;
        }
        // Shift-clicking armour out of the hotbar into the armour slots, or number-key swaps onto them.
        if (event.isShiftClick() && event.getCurrentItem() != null && isArmour(event.getCurrentItem().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmourDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || matchOf(player) == null) return;
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= 5 && slot <= 8) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmourRightClick(PlayerInteractEvent event) {
        // Right-clicking armour in hand swaps it with what's worn.
        if (matchOf(event.getPlayer()) == null || event.getItem() == null) return;
        if (isArmour(event.getItem().getType())) event.setCancelled(true);
    }

    private static boolean isArmour(org.bukkit.Material type) {
        String name = type.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.endsWith("_BANNER") || name.endsWith("_SKULL") || name.endsWith("_HEAD") || type == org.bukkit.Material.ELYTRA;
    }
}
