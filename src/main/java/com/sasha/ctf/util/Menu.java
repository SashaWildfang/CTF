/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A chest menu: items with click actions; nothing can be taken out. */
public final class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, java.util.function.BiConsumer<Player, org.bukkit.event.inventory.ClickType>> actions = new HashMap<>();

    public Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public Menu set(int slot, ItemStack item, Consumer<Player> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, (player, click) -> action.accept(player));
        else actions.remove(slot);
        return this;
    }

    /** An item whose action depends on the click (left, right, shift...). */
    public Menu setClick(int slot, ItemStack item, java.util.function.BiConsumer<Player, org.bukkit.event.inventory.ClickType> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    /** Panes of one colour in every empty slot. */
    public Menu fill(Material pane) {
        ItemStack filler = Items.named(pane, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, filler);
        }
        return this;
    }

    /** Dark panes in every empty slot. */
    public Menu fill() {
        return fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    public void open(Player player) {
        var view = player.openInventory(inventory);
        // Bedrock's menu header doesn't show styled titles well (Geyser): plain text there
        if (view != null && Bedrock.is(player)) {
            try {
                @SuppressWarnings("deprecation")
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(view.title());
                view.setTitle(plain);
            } catch (RuntimeException ignored) {
                // keep the styled one
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Runs menu clicks and keeps items in place. Register once. */
    public static final class Clicks implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder(false) instanceof Menu menu)) return;
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            var action = menu.actions.get(event.getSlot());
            if (action != null && event.getWhoClicked() instanceof Player player) action.accept(player, event.getClick());
        }

        @EventHandler(ignoreCancelled = true)
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
        }
    }
}
