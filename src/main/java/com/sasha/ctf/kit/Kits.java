/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.kit;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Who picked which kit. Kept in the database so the choice made in the hub is there on the game server. */
public final class Kits {

    private final FractalCTF plugin;
    private final Map<UUID, Kit> chosen = new ConcurrentHashMap<>();

    public Kits(FractalCTF plugin) {
        this.plugin = plugin;
    }

    public Kit of(UUID player) {
        return chosen.getOrDefault(player, Kit.SOLDIER);
    }

    /** Loads a player's kit choice (off the main thread); {@code then} runs on the main thread after. */
    public void load(UUID player, Runnable then) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().readKit(player).flatMap(Kit::parse).ifPresent(kit -> chosen.put(player, kit));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't load a kit choice", e);
            }
            if (then != null) Bukkit.getScheduler().runTask(plugin, then);
        });
    }

    public void forget(UUID player) {
        chosen.remove(player);
    }

    public void choose(Player player, Kit kit) {
        chosen.put(player.getUniqueId(), kit);
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().saveKit(id, kit.id());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't save a kit choice", e);
            }
        });
        plugin.messages().send(player, plugin.ctfConfig().hub() ? "kits.chosen-hub" : "kits.chosen-game", Messages.arg("kit", kit.display()));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    public void openMenu(Player player) {
        Messages messages = plugin.messages();
        Menu menu = new Menu(3, messages.component("kits.menu-title"));
        Kit current = of(player.getUniqueId());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        Kit[] kits = Kit.values();
        for (int i = 0; i < kits.length; i++) {
            Kit kit = kits[i];
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(kit.description(), NamedTextColor.GRAY));
            lore.add(Component.empty());
            for (String line : kit.contents()) lore.add(Component.text("▪ ", NamedTextColor.DARK_GRAY).append(Component.text(line, NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(kit == current ? messages.component("kits.selected") : messages.component("kits.click-to-pick"));
            ItemStack icon = Items.named(kit.icon(), Component.text(kit.display(), kit == current ? NamedTextColor.GREEN : NamedTextColor.GOLD), lore);
            if (kit == current) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            menu.set(slots[i], icon, clicker -> {
                choose(clicker, kit);
                clicker.closeInventory();
            });
        }
        menu.fill().open(player);
    }
}
