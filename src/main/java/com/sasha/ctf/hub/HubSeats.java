/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The lobby's benches: right-click a wooden stair with an empty hand to sit on it, sneak to stand up. (Wooden
 * stairs only: those are the benches and chairs; the stone ones are steps and mouldings.) One sitter a stair.
 * hub.sit-on-stairs: false in config.yml turns it off.
 */
public final class HubSeats implements Listener {

    private static final String TAG = "ctfseat";

    private final FractalCTF plugin;
    /** Which stair each seat stands on, by the seat's id. */
    private final Map<UUID, Block> seats = new HashMap<>();

    HubSeats(FractalCTF plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Entity entity : Bukkit.getWorlds().get(0).getEntities()) if (entity.getScoreboardTags().contains(TAG)) entity.remove();
    }

    void stop() {
        for (UUID id : seats.keySet()) {
            Entity seat = Bukkit.getEntity(id);
            if (seat != null) seat.remove();
        }
        seats.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSit(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!plugin.getConfig().getBoolean("hub.sit-on-stairs", true)) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (event.getItem() != null || player.isSneaking() || player.isInsideVehicle()) return;
        if (!Tag.WOODEN_STAIRS.isTagged(block.getType()) || !(block.getBlockData() instanceof Stairs stairs)) return;
        if (stairs.getHalf() != Bisected.Half.BOTTOM || !block.getRelative(BlockFace.UP).isPassable()) return;
        if (seats.containsValue(block) || player.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) > 16) return;
        event.setCancelled(true);
        BlockFace front = stairs.getFacing().getOppositeFace();      // a stair's facing is its back
        Location at = block.getLocation().add(0.5 + front.getModX() * 0.2, 0.5, 0.5 + front.getModZ() * 0.2);
        at.setYaw(switch (front) {
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        });
        BlockDisplay seat = block.getWorld().spawn(at, BlockDisplay.class, made -> {
            made.setPersistent(false);
            made.addScoreboardTag(TAG);
        });
        seats.put(seat.getUniqueId(), block);
        seat.addPassenger(player);
    }

    /** Standing up: the seat goes, and they are put on the stair instead of inside it. */
    @EventHandler
    public void onStand(EntityDismountEvent event) {
        Entity seat = event.getDismounted();
        Block block = seats.remove(seat.getUniqueId());
        if (block == null) return;
        Location up = seat.getLocation().add(0, 0.05, 0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            seat.remove();
            if (event.getEntity() instanceof Player player && player.isOnline()) {
                up.setYaw(player.getLocation().getYaw());
                up.setPitch(player.getLocation().getPitch());
                plugin.hub().move(player, up);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Entity seat = event.getPlayer().getVehicle();
        if (seat != null && seats.remove(seat.getUniqueId()) != null) seat.remove();
    }
}
