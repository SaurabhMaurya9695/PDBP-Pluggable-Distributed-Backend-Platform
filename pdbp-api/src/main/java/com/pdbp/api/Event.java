package com.pdbp.api;

import java.util.Map;

/**
 * Represents an event in the PDBP event system.
 *
 * <p>Events are the primary mechanism for plugin-to-plugin communication.
 * They enable loose coupling and asynchronous processing.
 *
 * @author Saurabh Maurya
 */
public interface Event {

    /**
     * @return event type/name (e.g., "PaymentProcessed", "UserCreated")
     */
    String getType();

    /**
     * @return event source (plugin name that published the event)
     */
    String getSource();

    /**
     * @return timestamp when event was created (milliseconds since epoch)
     */
    long getTimestamp();

    /**
     * @return event payload/data as key-value pairs
     */
    Map<String, Object> getPayload();

    /**
     * Gets a payload value by key.
     *
     * @param key the key
     * @return value if present, otherwise null
     */
    default Object getPayload(String key) {
        return getPayload().get(key);
    }

    /**
     * Gets a payload value as a specific type.
     *
     * @param key  the key
     * @param type the expected type
     * @param <T>  the type
     * @return value if present and of correct type, otherwise null
     */
    @SuppressWarnings("unchecked")
    default <T> T getPayload(String key, Class<T> type) {
        Object value = getPayload(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
}

