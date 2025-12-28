package com.pdbp.core.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdbp.api.Event;
import com.pdbp.api.EventFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Persists events to disk for replay and audit purposes.
 *
 * <p>Events are stored as JSON files in the events directory.
 * Each event is appended to a file, allowing for event replay.
 *
 * <p>Design: File-based persistence with JSON format.
 *
 * @author Saurabh Maurya
 */
public class EventPersistence {

    private static final Logger logger = LoggerFactory.getLogger(EventPersistence.class);
    private static final String EVENTS_DIR = "events";
    private static final String EVENTS_FILE = "events.jsonl"; // JSON Lines format

    private final Path eventsFile;
    private final ObjectMapper objectMapper;
    private volatile boolean enabled;

    public EventPersistence(Path baseDirectory) {
        Path eventsDirectory = baseDirectory.resolve(EVENTS_DIR);
        this.eventsFile = eventsDirectory.resolve(EVENTS_FILE);
        this.objectMapper = new ObjectMapper();
        this.enabled = true;

        try {
            Files.createDirectories(eventsDirectory);
            logger.info("EventPersistence initialized. Events directory: {}", eventsDirectory);
        } catch (IOException e) {
            logger.error("Failed to create events directory", e);
            this.enabled = false;
        }
    }

    /**
     * Persists an event to disk.
     *
     * @param event the event to persist
     */
    public void persistEvent(Event event) {
        if (!enabled) {
            return;
        }

        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("type", event.getType());
            eventData.put("source", event.getSource());
            eventData.put("timestamp", event.getTimestamp());
            eventData.put("payload", event.getPayload());

            String jsonLine = objectMapper.writeValueAsString(eventData) + "\n";
            Files.write(eventsFile, jsonLine.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.debug("Persisted event: type={}", event.getType());
        } catch (IOException e) {
            logger.error("Failed to persist event: {}", event.getType(), e);
        }
    }

    /**
     * Loads events from disk.
     *
     * @param limit maximum number of events to load (0 = all)
     * @return list of events
     */
    public List<Event> loadEvents(int limit) {
        if (!Files.exists(eventsFile)) {
            return new ArrayList<>();
        }

        List<Event> events = new ArrayList<>();
        try (Stream<String> lines = Files.lines(eventsFile)) {
            lines.limit(limit > 0 ? limit : Long.MAX_VALUE).forEach(line -> {
                try {
                    JsonNode jsonNode = objectMapper.readTree(line);
                    String type = jsonNode.get("type").asText();
                    String source = jsonNode.get("source").asText();
                    long timestamp = jsonNode.get("timestamp").asLong();

                    @SuppressWarnings("unchecked") Map<String, Object> payload = objectMapper.convertValue(
                            jsonNode.get("payload"), Map.class);

                    // Create event (we can't recreate exact Event instance, so create new one)
                    Event event = EventFactory.createEvent(type, source, payload);
                    events.add(event);
                } catch (Exception e) {
                    logger.warn("Failed to parse event line: {}", line, e);
                }
            });
            logger.info("Loaded {} events from disk", events.size());
        } catch (IOException e) {
            logger.error("Failed to load events from disk", e);
        }
        return events;
    }

    /**
     * Gets the number of events stored on disk.
     *
     * @return event count
     */
    public long getEventCount() {
        if (!Files.exists(eventsFile)) {
            return 0;
        }
        try {
            return Files.lines(eventsFile).count();
        } catch (IOException e) {
            logger.error("Failed to count events", e);
            return 0;
        }
    }

    /**
     * Clears all persisted events.
     */
    public void clear() {
        try {
            if (Files.exists(eventsFile)) {
                Files.delete(eventsFile);
                logger.info("Cleared all persisted events");
            }
        } catch (IOException e) {
            logger.error("Failed to clear persisted events", e);
        }
    }

    /**
     * Enables or disables event persistence.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("Event persistence {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Checks if persistence is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}

