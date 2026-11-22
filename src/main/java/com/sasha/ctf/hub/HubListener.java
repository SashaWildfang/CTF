/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.command.StatsView;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Network;
import com.sasha.ctf.config.Messages;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Hub players: the hotbar items, and nobody gets hurt, hungry or messes with the lobby. */
final class HubListener implements Listener {

    private final FractalCTF plugin;
    private final HubManager hub;
    private final java.util.Map<java.util.UUID, Long> parkour = new java.util.HashMap<>();

    HubListener(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        hub.welcome(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        parkour.remove(event.getPlayer().getUniqueId());
        hub.leave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() == Action.PHYSICAL) return;
        String tag = Items.tagOf(event.getItem());
        if (tag == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        switch (tag) {
            case HubItems.KIT_MENU -> plugin.kits().openMenu(player);
            case HubItems.VOTE -> hub.items().openVote(player);
            case HubItems.JOIN -> hub.joinMatch(player);
            case HubItems.STATS -> StatsView.show(plugin, player, player);
            case HubItems.LOBBY -> Network.send(plugin, player, plugin.ctfConfig().lobbyServer());
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked().getGameMode() == GameMode.CREATIVE) return;
        if (event.getClickedInventory() == event.getWhoClicked().getInventory()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        // the lobby's armour stands and labels: only builders can knock them over
        Player damager = event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player p ? p : null;
        if (damager == null || !builder(damager)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onStand(PlayerArmorStandManipulateEvent event) {
        if (!builder(event.getPlayer())) event.setCancelled(true);
    }

    /** The kit stands in the armoury: right-click one to preview that kit. */
    @EventHandler
    public void onStandClick(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        for (String tag : event.getRightClicked().getScoreboardTags()) {
            if (!tag.startsWith("ctfkit:")) continue;
            event.setCancelled(true);
            com.sasha.ctf.kit.Kit.parse(tag.substring(7)).ifPresent(kit -> plugin.kits().preview(event.getPlayer(), kit));
            return;
        }
    }

    /** The parkour in the hub: stepping on a light pressure plate starts the clock, a heavy one finishes it. */
    private void parkour(Player player, Material plate) {
        if (plate == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            if (!parkour.containsKey(player.getUniqueId())) plugin.messages().send(player, "hub.parkour-start");
            parkour.put(player.getUniqueId(), System.currentTimeMillis());
        } else if (plate == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            Long started = parkour.remove(player.getUniqueId());
            if (started == null) return;
            String time = String.format(java.util.Locale.ROOT, "%.2f", (System.currentTimeMillis() - started) / 1000.0);
            plugin.messages().send(player, "hub.parkour-done", Messages.arg("time", time));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!builder(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!builder(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < plugin.ctfConfig().hubVoidY()) {
            event.getPlayer().teleport(hub.spawn());
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            parkour(event.getPlayer(), event.getTo().getBlock().getType());
        }
    }

    private static boolean builder(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("ctf.admin");
    }
}
