/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.data;

/** A player's numbers: one match's while it's played, or their totals from the database. */
public final class PlayerStats {
    public int kills;
    public int deaths;
    public int captures;
    public int wins;
    public int losses;
    public int games;

    /** Kills per death, with deaths counted as at least one. */
    public double kdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
}
