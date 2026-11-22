/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.data;

/** A player's numbers: one match's while it's played, or their totals from the database. */
public final class PlayerStats {
    public int kills;
    public int deaths;
    /** Flags captured. */
    public int captures;
    public int wins;
    public int losses;
    public int games;
    public int assists;
    /** Coins earned (in a match), or the balance (totals from the stats table). */
    public int coins;
    // the detailed numbers (kept per map and mode since 1.4)
    public int wools;
    public int hillSeconds;
    public int bowKills;
    public int voidKills;
    public int flagPickups;
    public int bestStreak;
    public int longestShot;
    public int playSeconds;
    public int coinsEarned;

    /** Kills per death, with deaths counted as at least one. */
    public double kdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    /** Wins out of games, 0-100. */
    public double winRate() {
        return games == 0 ? 0 : 100.0 * wins / games;
    }

    /** Adds another set of numbers onto this one (bests are kept, not added). */
    public PlayerStats add(PlayerStats other) {
        kills += other.kills;
        deaths += other.deaths;
        captures += other.captures;
        wins += other.wins;
        losses += other.losses;
        games += other.games;
        assists += other.assists;
        wools += other.wools;
        hillSeconds += other.hillSeconds;
        bowKills += other.bowKills;
        voidKills += other.voidKills;
        flagPickups += other.flagPickups;
        bestStreak = Math.max(bestStreak, other.bestStreak);
        longestShot = Math.max(longestShot, other.longestShot);
        playSeconds += other.playSeconds;
        coinsEarned += other.coinsEarned;
        return this;
    }
}
