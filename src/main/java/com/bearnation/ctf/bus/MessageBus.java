package com.bearnation.ctf.bus;

public interface MessageBus {
    void publish(String subject, String message);
    void subscribe(String subject, MessageHandler handler);
    void close();

    @FunctionalInterface
    interface MessageHandler {
        void onMessage(String message);
    }
}
