/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Picking a team in the match. Everyone arrives as a spectator with the team helmet; right-clicking it (or /jointeam)
 * opens this menu. Auto Select comes first and is for everyone; choosing a team yourself is a rank perk
 * (ctf.pickteam). Whoever hasn't picked when the match starts watches it instead: that keeps players who are away
 * from keyboard off the teams. During the match, spectators join from the same menu.
 */
public final class TeamPicker {

    static final String TAG = "team-pick";

    private final FractalCTF plugin;

    TeamPicker(FractalCTF plugin) {
        this.plugin = plugin;
    }

    /** The team helmet: right-click to open the menu. */
    ItemStack item() {
        ItemStack helmet = Items.tag(Items.named(Material.LEATHER_HELMET, plugin.messages().component("team.item.name"),
                plugin.messages().lines("team.item.lore")), TAG);
        helmet.editMeta(LeatherArmorMeta.class, meta -> {
            meta.setColor(org.bukkit.Color.fromRGB(0xB266FF));
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_DYE, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            meta.setEnchantmentGlintOverride(true);
        });
        // Can't be worn: right-clicking it put it on the head (and it vanished when taken off)
        helmet.unsetData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE);
        return helmet;
    }

    void give(Player player) {
        player.getInventory().setItem(4, null);                     // where it used to be
        player.getInventory().setItem(1, item());                   // next to the compass
        player.getInventory().setHeldItemSlot(1);
    }

    /** The menu: Auto Select, then each team with who is on it. */
    public void open(Player viewer, Match match) {
        Messages messages = plugin.messages();
        if (match.phase() == Match.Phase.OVER) {
            messages.send(viewer, "team.no-match");
            return;
        }
        Menu menu = new Menu(3, messages.component("team.menu-title"));
        TeamColor mine = match.teamOf(viewer.getUniqueId());
        boolean watching = match.spectating(viewer.getUniqueId());
        menu.set(10, Items.named(Material.NETHER_STAR, messages.component("team.menu-auto"), messages.lines("team.menu-auto-lore")),
                clicker -> pick(clicker, match, null));
        List<TeamColor> teams = match.mode().teams();
        int[] slots = teams.size() <= 2 ? new int[] {13, 15} : new int[] {13, 14, 15, 16};
        boolean perk = viewer.hasPermission("ctf.pickteam");
        for (int i = 0; i < teams.size() && i < slots.length; i++) {
            TeamColor team = teams.get(i);
            List<Player> members = match.online(team);
            members.removeIf(p -> match.spectating(p.getUniqueId()));
            List<Component> lore = new ArrayList<>();
            lore.add(messages.component("team.menu-count", Messages.arg("count", members.size())));
            lore.add(Component.empty());
            lore.add(messages.component(mine == team && !watching ? "team.menu-yours" : perk ? "team.menu-click" : "team.menu-locked"));
            ItemStack wool = Items.named(team.wool(), messages.component("team.menu-team", Messages.comp("team", Match.teamName(team))), lore);
            if (mine == team && !watching) wool.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            menu.set(slots[i], wool, clicker -> pick(clicker, match, team));
        }
        menu.fill().open(viewer);
    }

    /** Joins a team, or with {@code team} null the one that needs players. Choosing a team needs the perk. */
    public void pick(Player player, Match match, TeamColor team) {
        Messages messages = plugin.messages();
        if (team != null && !player.hasPermission("ctf.pickteam")) {
            player.closeInventory();
            messages.send(player, "perk.pick-team");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (team != null && !match.mode().teams().contains(team)) {
            messages.send(player, "team.not-in-mode", Messages.comp("team", Match.teamName(team)));
            return;
        }
        player.closeInventory();
        match.choose(player, team);
    }
}
