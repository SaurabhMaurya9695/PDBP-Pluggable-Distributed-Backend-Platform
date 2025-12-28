package com.pdbp.api;

import java.util.Map;

/**
 * Factory for creating Event instances.
 *
 * <p>Plugins should use this factory to create events rather than
 * directly instantiating implementation classes.
 *
 * @author Saurabh Maurya
 */
public final class EventFactory {

    private EventFactory() {
        // Utility class
    }

    /**
     * Creates a new event.
     *
     * @param type    the event type (e.g., "PaymentProcessed")
     * @param source  the event source (typically plugin name)
     * @param payload the event payload (key-value pairs)
     * @return event instance
     */
    public static Event createEvent(String type, String source, Map<String, Object> payload) {
        return new SimpleEventImpl(type, source, payload);
    }

    /**
     * Creates a new event with empty payload.
     *
     * @param type   the event type
     * @param source the event source
     * @return event instance
     */
    public static Event createEvent(String type, String source) {
        return new SimpleEventImpl(type, source, null);
    }

    /**
     * Simple implementation of Event interface.
     * Internal class - plugins should use EventFactory methods.
     */
    private static class SimpleEventImpl implements Event {
        private final String type;
        private final String source;
        private final long timestamp;
        private final Map<String, Object> payload;

        SimpleEventImpl(String type, String source, Map<String, Object> payload) {
            this.type = type;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
            this.payload = payload != null ? new java.util.HashMap<>(payload) : new java.util.HashMap<>();
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
            return new java.util.HashMap<>(payload);
        }
    }
}

