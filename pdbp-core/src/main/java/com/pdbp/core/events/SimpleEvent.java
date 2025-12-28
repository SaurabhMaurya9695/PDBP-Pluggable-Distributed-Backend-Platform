package com.pdbp.core.events;

import com.pdbp.api.Event;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple implementation of Event interface.
 *
 * @author Saurabh Maurya
 */
public class SimpleEvent implements Event {

    private final String type;
    private final String source;
    private final long timestamp;
    private final Map<String, Object> payload;

    public SimpleEvent(String type, String source, Map<String, Object> payload) {
        this.type = type;
        this.source = source;
        this.timestamp = System.currentTimeMillis();
        this.payload = payload != null ? new HashMap<>(payload) : new HashMap<>();
    }

    public SimpleEvent(String type, String source) {
        this(type, source, new HashMap<>());
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public Map<String, Object> getPayload() {
        return new HashMap<>(payload);
    }

    /**
     * Builder for creating events easily.
     */
    public static class Builder {

        private String type;
        private String source;
        private Map<String, Object> payload = new HashMap<>();

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder payload(String key, Object value) {
            this.payload.put(key, value);
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload.putAll(payload);
            return this;
        }

        public SimpleEvent build() {
            return new SimpleEvent(type, source, payload);
        }
    }
}

