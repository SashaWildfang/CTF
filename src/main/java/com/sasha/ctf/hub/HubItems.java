/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.VoteOption;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/** The hub hotbar (kit menu, vote, join match, stats, back to lobby) and the vote menu. */
public final class HubItems {

    public static final String KIT_MENU = "kit-menu";
    public static final String VOTE = "vote";
    public static final String JOIN = "join";
    public static final String STATS = "stats";
    public static final String LOBBY = "lobby";

    private final FractalCTF plugin;
    private final HubManager hub;

    HubItems(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    /** Resets the player for the hub and hands out the items for the current phase. */
    public void give(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) player.setGameMode(GameMode.ADVENTURE);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        inventory.setItem(0, item(Material.BOOK, "items.kit-menu", KIT_MENU));
        if (hub.phase() == HubManager.Phase.WAITING || hub.phase() == HubManager.Phase.COUNTDOWN) {
            inventory.setItem(1, item(Material.PAPER, "items.vote", VOTE));
        }
        if (hub.phase() == HubManager.Phase.IN_MATCH) {
            inventory.setItem(4, item(Material.LIME_DYE, "items.join", JOIN));
        }
        ItemStack stats = item(Material.PLAYER_HEAD, "items.stats", STATS);
        stats.editMeta(org.bukkit.inventory.meta.SkullMeta.class, meta -> meta.setOwningPlayer(player));
        inventory.setItem(7, stats);
        inventory.setItem(8, item(Material.RED_BED, "items.lobby", LOBBY));
    }

    private ItemStack item(Material material, String key, String tag) {
        Messages messages = plugin.messages();
        return Items.tag(Items.named(material, messages.component(key + ".name"), messages.lines(key + ".lore")), tag);
    }

    public void openVote(Player player) {
        Messages messages = plugin.messages();
        List<VoteOption> options = hub.options();
        if (options.isEmpty() || hub.phase() == HubManager.Phase.STARTING || hub.phase() == HubManager.Phase.IN_MATCH) {
            messages.send(player, options.isEmpty() ? "vote.no-maps" : "vote.closed");
            return;
        }
        Menu menu = new Menu(3, messages.component("vote.menu-title"));
        int[] slots = switch (options.size()) {
            case 1 -> new int[] {13};
            case 2 -> new int[] {11, 15};
            case 3 -> new int[] {11, 13, 15};
            case 4 -> new int[] {10, 12, 14, 16};
            default -> new int[] {9 + 2, 9 + 3, 9 + 4, 9 + 5, 9 + 6};
        };
        Integer mine = hub.voteOf(player.getUniqueId());
        for (int i = 0; i < options.size() && i < slots.length; i++) {
            VoteOption option = options.get(i);
            int index = i;
            boolean voted = mine != null && mine == i;
            List<Component> lore = new ArrayList<>(messages.lines("vote.option-lore",
                    Messages.arg("mode", option.mode().display()), Messages.arg("votes", hub.votesFor(i))));
            lore.add(messages.component(voted ? "vote.your-vote" : "vote.click-to-vote"));
            ItemStack icon = Items.named(icon(option), messages.component("vote.option-name",
                    Messages.arg("number", i + 1), Messages.arg("map", option.mapName())), lore);
            icon.setAmount(Math.max(1, Math.min(64, hub.votesFor(i))));
            if (voted) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            menu.set(slots[i], icon, clicker -> {
                hub.vote(clicker, index);
                clicker.closeInventory();
            });
        }
        menu.fill().open(player);
    }

    private static Material icon(VoteOption option) {
        return switch (option.mode()) {
            case CTF -> Material.RED_BANNER;
            case CTF4 -> Material.LIME_BANNER;
            case KOTH -> Material.GOLDEN_HELMET;
            case DTW -> Material.WHITE_WOOL;
        };
    }
}
