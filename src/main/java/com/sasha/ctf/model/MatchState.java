/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

/**
 * Where the match is, as stored in the shared ctf_match row. The hub moves IDLE to REQUESTED; the game
 * server takes it through LOADING, READY, PLAYING and ENDING and back to IDLE.
 */
public enum MatchState {
    /** No match: the hub is voting. */
    IDLE,
    /** The hub picked a map and the teams; the game server should load it. */
    REQUESTED,
    /** The game server is copying and loading the map. */
    LOADING,
    /** The map is loaded: the hub sends the players over. */
    READY,
    /** The match is on. Hub players can join late. */
    PLAYING,
    /** The match is over: everyone is being sent back to the hub. */
    ENDING
}
