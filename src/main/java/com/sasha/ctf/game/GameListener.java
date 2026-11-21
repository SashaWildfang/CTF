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
import org.bukkit.event.inventory.InventoryClickEvent;
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

/** Everything that happens to players in a match world: joining, fighting, dying, building, the Medkit. */
final class GameListener implements Listener {

    private static final Set<PotionEffectType> HARMFUL = Set.of(PotionEffectType.INSTANT_DAMAGE, PotionEffectType.POISON,
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.WITHER, PotionEffectType.BLINDNESS);
    private static final long MEDKIT_COOLDOWN = 8000;

    private final FractalCTF plugin;
    private final GameManager game;
    private final Map<UUID, Long> medkitUsed = new HashMap<>();

    GameListener(FractalCTF plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
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
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            match.spectate(player);
        } else {
            game.noMatch(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match != null) match.quit(event.getPlayer());
        medkitUsed.remove(event.getPlayer().getUniqueId());
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
                player.teleport(match.spawn(match.teamOf(player.getUniqueId())));
            }
            return;
        }
        if (match.live() && !player.isDead()) match.rules().onMove(player, event.getTo());
    }

    // ---------------------------------------------------------------- fighting

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Match match = matchOf(player);
        if (match == null) return;
        if (!match.live() || match.spawnProtected(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Match match = matchOf(victim);
        if (match == null) return;
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        TeamColor a = match.teamOf(attacker.getUniqueId()), v = match.teamOf(victim.getUniqueId());
        if (a == null || a == v) {
            event.setCancelled(true);
            return;
        }
        match.hit(victim, attacker);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
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
        match.died(victim, victim.getKiller());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Match match = game.match();
        if (match == null) return;
        TeamColor team = match.teamOf(player.getUniqueId());
        if (team == null || match.phase() == Match.Phase.OVER) {
            event.setRespawnLocation(match.spectatorSpawn());
            Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
            return;
        }
        event.setRespawnLocation(match.spawn(team));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && match.world().equals(player.getWorld())) match.equip(player, team);
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
    public void onArmour(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || matchOf(player) == null) return;
        // The team cap (and a carried flag) stay on.
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && event.getRawSlot() == 5) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMedkitPlayer(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) return;
        Player medic = event.getPlayer();
        Match match = matchOf(medic);
        if (match == null || !match.live() || !Kit.MEDKIT.equals(Items.tagOf(medic.getInventory().getItemInMainHand()))) return;
        TeamColor team = match.teamOf(medic.getUniqueId());
        if (team == null || team != match.teamOf(target.getUniqueId())) return;
        event.setCancelled(true);
        heal(medic, target, 8);
    }

    @EventHandler
    public void onMedkitSelf(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player medic = event.getPlayer();
        Match match = matchOf(medic);
        if (match == null || !match.live() || !Kit.MEDKIT.equals(Items.tagOf(event.getItem()))) return;
        event.setCancelled(true);
        heal(medic, medic, 4);
    }

    private void heal(Player medic, Player target, double amount) {
        long now = System.currentTimeMillis();
        long last = medkitUsed.getOrDefault(medic.getUniqueId(), 0L);
        if (now - last < MEDKIT_COOLDOWN) {
            plugin.messages().actionBar(medic, "kits.medkit-cooldown", Messages.arg("seconds", (MEDKIT_COOLDOWN - (now - last)) / 1000 + 1));
            return;
        }
        var max = target.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        if (target.getHealth() >= cap) {
            plugin.messages().actionBar(medic, "kits.medkit-full");
            return;
        }
        medkitUsed.put(medic.getUniqueId(), now);
        target.setHealth(Math.min(cap, target.getHealth() + amount));
        target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2, 0), 5, 0.4, 0.3, 0.4);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        if (!target.equals(medic)) {
            plugin.messages().actionBar(medic, "kits.medkit-healed", Messages.arg("player", target.getName()));
            plugin.messages().actionBar(target, "kits.medkit-healed-by", Messages.arg("player", medic.getName()));
        }
    }
}
