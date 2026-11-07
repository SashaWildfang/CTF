package com.bearnation.ctf.bus;

import com.google.gson.JsonObject;

public class Bus {
    /**
     * Wrap a message with type, target, and payload for consistent structure.
     */
    public static JsonObject env(String type, String target, JsonObject payload) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("v", 1);
        wrapper.addProperty("type", type);
        if (target != null) wrapper.addProperty("target", target);
        wrapper.add("payload", payload);
        return wrapper;
    }
}
