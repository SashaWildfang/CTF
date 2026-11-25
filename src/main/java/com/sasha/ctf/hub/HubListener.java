/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
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
            case HubItems.STATS -> hub.stats().open(player, player);
            case HubItems.SETTINGS -> plugin.settingsMenu().open(player);
            case HubItems.LOBBY -> com.sasha.ctf.util.LeaveTimer.toggle(plugin, player, plugin.ctfConfig().lobbyServer(), "the Lobby");
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
            hub.move(event.getPlayer(), hub.spawn());
            return;
        }
    }

    private static boolean builder(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("ctf.admin");
    }
}
