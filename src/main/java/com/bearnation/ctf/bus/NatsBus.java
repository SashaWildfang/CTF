package com.bearnation.ctf.bus;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;

import java.nio.charset.StandardCharsets;

public class NatsBus implements MessageBus {

    private final Connection conn;

    public NatsBus(String url) throws Exception {
        this.conn = Nats.connect(url);
    }

    @Override
    public void publish(String subject, String message) {
        try {
            conn.publish(subject, message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void subscribe(String subject, MessageHandler handler) {
        Dispatcher dispatcher = conn.createDispatcher(msg -> {
            String body = new String(msg.getData(), StandardCharsets.UTF_8);
            try {
                handler.onMessage(body);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        dispatcher.subscribe(subject);
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
