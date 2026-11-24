/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.quest;

import org.bukkit.Material;

import java.util.List;

/**
 * One quest or challenge. Quests count {@code goal} of something ({@code objective}) over their day, week or month;
 * challenges are done in a single game. Names and descriptions are in messages.yml (quests.list.ID.name / .description).
 */
public record QuestDef(String id, Period period, String objective, int goal, Material icon) {

    public enum Period { DAILY, WEEKLY, MONTHLY, CHALLENGE }

    /**
     * What quests count (QuestTracker reports these):
     * kills, bow_kills, void_kills, assists, flag_pickups, flag_captures (CTF modes), hill_captures (KOTH),
     * hill_seconds, wools, games, wins, carrier_kills (an enemy carrying a flag), defend_kills (within 25 blocks of
     * your own base), play_minutes, kit_wins (wins with different kits), mode_wins (wins in different modes),
     * streaks (5 kills without dying).
     */
    public static final List<QuestDef> QUESTS = List.of(
            // daily
            new QuestDef("flag_runner", Period.DAILY, "flag_pickups", 4, Material.WHITE_BANNER),
            new QuestDef("special_delivery", Period.DAILY, "flag_captures", 2, Material.RED_BANNER),
            new QuestDef("frontline", Period.DAILY, "kills", 15, Material.IRON_SWORD),
            new QuestDef("sharpshooter", Period.DAILY, "bow_kills", 5, Material.BOW),
            new QuestDef("edge_lord", Period.DAILY, "void_kills", 3, Material.ENDER_PEARL),
            new QuestDef("wingman", Period.DAILY, "assists", 8, Material.SHIELD),
            new QuestDef("carrier_hunter", Period.DAILY, "carrier_kills", 3, Material.TARGET),
            new QuestDef("home_guard", Period.DAILY, "defend_kills", 6, Material.IRON_DOOR),
            new QuestDef("wool_shears", Period.DAILY, "wools", 6, Material.SHEARS),
            new QuestDef("hill_keeper", Period.DAILY, "hill_seconds", 150, Material.BEACON),
            new QuestDef("victory_lap", Period.DAILY, "wins", 2, Material.GOLDEN_APPLE),
            new QuestDef("regular", Period.DAILY, "games", 4, Material.CLOCK),
            new QuestDef("on_a_roll", Period.DAILY, "streaks", 1, Material.BLAZE_POWDER),
            // weekly
            new QuestDef("flag_baron", Period.WEEKLY, "flag_captures", 15, Material.BLUE_BANNER),
            new QuestDef("champion", Period.WEEKLY, "wins", 12, Material.GOLDEN_HELMET),
            new QuestDef("warlord", Period.WEEKLY, "kills", 120, Material.DIAMOND_SWORD),
            new QuestDef("jack_of_all_kits", Period.WEEKLY, "kit_wins", 5, Material.ARMOR_STAND),
            new QuestDef("all_rounder", Period.WEEKLY, "mode_wins", 3, Material.COMPASS),
            new QuestDef("bounty_board", Period.WEEKLY, "carrier_kills", 20, Material.CROSSBOW),
            // monthly
            new QuestDef("legend_of_the_flag", Period.MONTHLY, "flag_captures", 50, Material.YELLOW_BANNER),
            new QuestDef("conqueror", Period.MONTHLY, "wins", 40, Material.NETHERITE_HELMET),
            new QuestDef("marathon", Period.MONTHLY, "play_minutes", 600, Material.RECOVERY_COMPASS),
            new QuestDef("slayer", Period.MONTHLY, "kills", 500, Material.NETHERITE_SWORD));

    /**
     * Challenges: done in one game, once per game. Objectives: hat_trick (3 kills within 15 s), clean_capture (a flag
     * home without being hit while carrying it), speed_capture (a flag home within 25 s of picking it up), flawless
     * (win without dying), carrier_kill, long_shot (a bow kill from 30+ blocks), void_double (2 void kills), survivor
     * (5 kills in one life), first_blood, wool_thief (3 wool), hill_hold (45 s on hills), bodyguard (3 assists in one life).
     * Each day shows two from the "any mode" list and one from the "objective" list.
     */
    public static final List<QuestDef> CHALLENGES_ANY = List.of(
            new QuestDef("hat_trick", Period.CHALLENGE, "hat_trick", 1, Material.FIRE_CHARGE),
            new QuestDef("flawless", Period.CHALLENGE, "flawless", 1, Material.TOTEM_OF_UNDYING),
            new QuestDef("long_shot", Period.CHALLENGE, "long_shot", 1, Material.SPECTRAL_ARROW),
            new QuestDef("void_double", Period.CHALLENGE, "void_double", 1, Material.ENDER_EYE),
            new QuestDef("survivor", Period.CHALLENGE, "survivor", 1, Material.NETHERITE_CHESTPLATE),
            new QuestDef("first_blood", Period.CHALLENGE, "first_blood", 1, Material.REDSTONE),
            new QuestDef("bodyguard", Period.CHALLENGE, "bodyguard", 1, Material.TURTLE_HELMET));
    public static final List<QuestDef> CHALLENGES_OBJECTIVE = List.of(
            new QuestDef("clean_capture", Period.CHALLENGE, "clean_capture", 1, Material.FEATHER),
            new QuestDef("speed_capture", Period.CHALLENGE, "speed_capture", 1, Material.SUGAR),
            new QuestDef("carrier_kill", Period.CHALLENGE, "carrier_kill", 1, Material.TRIDENT),
            new QuestDef("wool_thief", Period.CHALLENGE, "wool_thief", 1, Material.WHITE_WOOL),
            new QuestDef("hill_hold", Period.CHALLENGE, "hill_hold", 1, Material.LIGHTNING_ROD));

    public static QuestDef byId(String id) {
        for (QuestDef q : QUESTS) if (q.id.equals(id)) return q;
        for (QuestDef q : CHALLENGES_ANY) if (q.id.equals(id)) return q;
        for (QuestDef q : CHALLENGES_OBJECTIVE) if (q.id.equals(id)) return q;
        return null;
    }
}
