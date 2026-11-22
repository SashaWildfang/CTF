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
            match.join(player);
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
        if (match == null || player.getGameMode() == GameMode.SPECTATOR || match.teamOf(player.getUniqueId()) == null) return;
        if (event.getTo().getY() < match.map().voidY) {
            if (match.live()) {
                player.setHealth(0);
            } else {
                player.teleport(match.spectatorSpawn());
            }
            return;
        }
        if (match.live() && !player.isDead()) match.rules().onMove(player, event.getTo());
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
        if (!match.live() || match.spawnProtected(player)) event.setCancelled(true);
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
        TeamColor a = match.teamOf(attacker.getUniqueId()), v = match.teamOf(victim.getUniqueId());
        if (a == null || a == v) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Player) {
            double bonus = abilities.bruteForceBonus(attacker);
            if (bonus > 0) event.setDamage(event.getDamage() + bonus);
        }
        if (abilities.disguised(attacker)) abilities.reveal(attacker);
        match.hit(victim, attacker);
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
        if (killer == null && victim.getLastDamageCause() instanceof EntityDamageByEntityEvent cause) {
            killer = abilities.responsible(cause.getDamager());
        }
        match.died(victim, killer);
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
        Location torch = abilities.respawnSpot(player, match);
        event.setRespawnLocation(torch != null ? torch : match.spawn(team));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && match.world().equals(player.getWorld())) {
                match.equip(player, team);
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
        if (!match.live() || match.teamOf(event.getPlayer().getUniqueId()) == null || match.nearObjective(pos)) {
            if (match.live() && match.nearObjective(pos)) plugin.messages().actionBar(event.getPlayer(), "game.too-close");
            event.setCancelled(true);
            return;
        }
        match.markPlaced(pos);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) return;
        if (!match.live() || match.teamOf(event.getPlayer().getUniqueId()) == null) {
            event.setCancelled(true);
            return;
        }
        BlockPos pos = BlockPos.of(event.getBlock());
        switch (match.rules().onBreak(event.getPlayer(), event.getBlock())) {
            case DENY -> event.setCancelled(true);
            case ALLOW -> event.setDropItems(false);
            case PASS -> {
                if (match.placedByPlayer(pos)) {
                    match.unmarkPlaced(pos);
                } else {
                    event.setCancelled(true);
                    plugin.messages().actionBar(event.getPlayer(), "game.cant-break");
                }
            }
        }
    }

    // ---------------------------------------------------------------- items

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (matchOf(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Match match = matchOf(event.getEntity());
        if (match != null && (!match.live() || event.getEntity() instanceof Player player && match.teamOf(player.getUniqueId()) == null)) {
            event.setCancelled(true);
        }
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
