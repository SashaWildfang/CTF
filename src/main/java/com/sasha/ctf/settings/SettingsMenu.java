/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.settings;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.settings.PlayerSettings.Setting;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /settings: a row of toggles (and lobby flight for ranks that have it). Locked perks say which rank they need. */
public final class SettingsMenu {

    private final FractalCTF plugin;

    public SettingsMenu(FractalCTF plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Messages messages = plugin.messages();
        Menu menu = new Menu(3, messages.component("settings.title"));
        place(menu, player, 10, Setting.AUTO_JOIN, Material.ENDER_PEARL);
        place(menu, player, 11, Setting.JOIN_AS_SPECTATOR, Material.ENDER_EYE);
        place(menu, player, 12, Setting.HIDE_PLAYERS, Material.GLASS);
        place(menu, player, 14, Setting.TIPS, Material.WRITABLE_BOOK);
        place(menu, player, 15, Setting.SOUNDS, Material.NOTE_BLOCK);
        // lobby flight: the rank's /fly, right here (only in the lobby)
        boolean flyRank = player.hasPermission("ctf.fly");
        boolean flying = plugin.hub() != null && player.getAllowFlight();
        menu.set(16, toggleItem(Material.FEATHER, "fly", flying, flyRank, plugin.hub() != null), clicker -> {
            if (!clicker.hasPermission("ctf.fly")) messages.send(clicker, "perk.fly");
            else if (plugin.hub() == null) messages.send(clicker, "fly.hub-only");
            else plugin.hub().fly(clicker);
            open(clicker);
        });
        menu.set(22, Items.named(Material.BARRIER, messages.component("kits.close"), List.of()), Player::closeInventory);
        menu.fill().open(player);
    }

    private void place(Menu menu, Player player, int slot, Setting setting, Material icon) {
        boolean unlocked = setting.permission == null || player.hasPermission(setting.permission);
        boolean on = plugin.playerSettings().get(player, setting);
        menu.set(slot, toggleItem(icon, setting.key, on, unlocked, true), clicker -> {
            if (setting.permission != null && !clicker.hasPermission(setting.permission)) {
                plugin.messages().send(clicker, "settings.locked", Messages.comp("setting", plugin.messages().component("settings." + setting.key + ".name")));
                clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                return;
            }
            boolean now = !plugin.playerSettings().get(clicker, setting);
            plugin.playerSettings().set(clicker, setting, now);
            if (setting == Setting.HIDE_PLAYERS && plugin.hub() != null) plugin.hub().applyHidden(clicker);
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, now ? 1.4f : 0.9f);
            open(clicker);
        });
    }

    private ItemStack toggleItem(Material icon, String key, boolean on, boolean unlocked, boolean here) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>(messages.lines("settings." + key + ".lore"));
        lore.add(Component.empty());
        if (!unlocked) lore.add(messages.component("settings.state-locked", Messages.arg("rank", messages.raw("settings." + key + ".rank"))));
        else if (!here) lore.add(messages.component("settings.state-lobby-only"));
        else {
            lore.add(messages.component(on ? "settings.state-on" : "settings.state-off"));
            lore.add(messages.component("settings.click"));
        }
        ItemStack item = Items.named(unlocked ? icon : Material.GRAY_DYE, messages.component("settings." + key + ".name"), lore);
        if (on && unlocked) item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }
}
