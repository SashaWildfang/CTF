/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.kit;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The kit layout editor: a chest laid out like the player's inventory (three rows of inventory, then the
 * hotbar) with the kit's items in it. Players move them around and press Save; from then on they spawn
 * with the kit laid out that way. Works with Bedrock too: it's only clicks inside a chest.
 */
final class KitEditor implements Listener {

    /** Top rows 0-26 are inventory slots 9-35, row 27-35 is the hotbar 0-8; the bottom row has the buttons. */
    private static final int HOTBAR_START = 27;
    private static final int BACK = 36, RESET = 40, SAVE = 44;

    private final FractalCTF plugin;
    private final Kits kits;

    KitEditor(FractalCTF plugin, Kits kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    private static final class Session implements InventoryHolder {
        final Kit kit;
        Inventory inventory;

        Session(Kit kit) {
            this.kit = kit;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player player, Kit kit) {
        Messages messages = plugin.messages();
        Session session = new Session(kit);
        Inventory inventory = Bukkit.createInventory(session, 45, messages.component("editor-kit.title", Messages.arg("kit", kit.display())));
        session.inventory = inventory;
        fill(inventory, kit, kits.layout(player.getUniqueId(), kit));
        player.openInventory(inventory);
        messages.send(player, "editor-kit.opened", Messages.arg("kit", kit.display()));
    }

    private void fill(Inventory inventory, Kit kit, Map<String, Integer> layout) {
        for (int i = 0; i < 36; i++) inventory.setItem(i, null);
        for (Map.Entry<Kits.KitSlot, ItemStack> entry : Kits.arrange(kit, TeamColor.RED, layout).entrySet()) {
            inventory.setItem(toTop(entry.getKey().slot()), entry.getValue());
        }
        Messages messages = plugin.messages();
        ItemStack pane = Items.named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 36; i < 45; i++) inventory.setItem(i, pane);
        inventory.setItem(BACK, Items.named(Material.ARROW, messages.component("editor-kit.back"), messages.lines("editor-kit.back-lore")));
        inventory.setItem(RESET, Items.named(Material.WATER_BUCKET, messages.component("editor-kit.reset"), messages.lines("editor-kit.reset-lore")));
        inventory.setItem(SAVE, Items.named(Material.LIME_CONCRETE, messages.component("editor-kit.save"), messages.lines("editor-kit.save-lore")));
    }

    /** Player inventory slot → editor slot. */
    private static int toTop(int slot) {
        return slot < 9 ? HOTBAR_START + slot : slot - 9;
    }

    /** Editor slot → player inventory slot. */
    private static int toPlayer(int top) {
        return top >= HOTBAR_START ? top - HOTBAR_START : top + 9;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Session session)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Only plain moves inside the top part of the editor.
        boolean top = event.getClickedInventory() == event.getView().getTopInventory();
        InventoryAction action = event.getAction();
        if (!top || event.isShiftClick() || action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action.name().startsWith("DROP")) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 36) return; // moving kit items around: allowed
        event.setCancelled(true);
        switch (slot) {
            case SAVE -> save(player, session);
            case RESET -> {
                fill(session.inventory, session.kit, Map.of());
                player.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
            }
            case BACK -> Bukkit.getScheduler().runTask(plugin, () -> kits.openKit(player, session.kit));
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Session)) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= 36) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Session)) return;
        // Whatever was on the cursor belongs to the editor, not the player.
        event.getView().setCursor(null);
        if (event.getPlayer() instanceof Player player && plugin.hub() != null) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.hub().items().give(player));
        }
    }

    private void save(Player player, Session session) {
        Map<String, Integer> layout = new LinkedHashMap<>();
        for (int top = 0; top < 36; top++) {
            String key = Items.layoutKeyOf(session.inventory.getItem(top));
            if (key != null) layout.put(key, toPlayer(top));
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            plugin.messages().send(player, "editor-kit.put-down");
            return;
        }
        kits.saveLayout(player, session.kit, layout);
        plugin.messages().send(player, "editor-kit.saved", Messages.arg("kit", session.kit.display()));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        player.closeInventory();
    }
}
