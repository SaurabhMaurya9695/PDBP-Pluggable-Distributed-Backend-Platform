package com.pdbp.core.events;

import com.pdbp.api.Event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dead Letter Queue (DLQ) for storing failed events.
 *
 * <p>When an event handler throws an exception, the event is stored in the DLQ
 * for later inspection, replay, or manual processing.
 *
 * <p>Design: Thread-safe queue with size limits and metadata tracking.
 *
 * @author Saurabh Maurya
 */
public class DeadLetterQueue {

    private static final Logger logger = LoggerFactory.getLogger(DeadLetterQueue.class);
    private static final int DEFAULT_MAX_SIZE = 1000;

    private final ConcurrentLinkedQueue<FailedEvent> queue;
    private final int maxSize;
    private final AtomicLong totalFailedEvents;

    public DeadLetterQueue() {
        this(DEFAULT_MAX_SIZE);
    }

    public DeadLetterQueue(int maxSize) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.maxSize = maxSize;
        this.totalFailedEvents = new AtomicLong(0);
        logger.info("DeadLetterQueue initialized with max size: {}", maxSize);
    }

    /**
     * Adds a failed event to the DLQ.
     *
     * @param event       the event that failed
     * @param subscriptionId the subscription ID that failed
     * @param error       the exception that occurred
     */
    public void addFailedEvent(Event event, String subscriptionId, Throwable error) {
        if (queue.size() >= maxSize) {
            // Remove oldest event to make room
            FailedEvent removed = queue.poll();
            if (removed != null) {
                logger.warn("DLQ full, removing oldest event: {}", removed.getEvent().getType());
            }
        }

        FailedEvent failedEvent = new FailedEvent(event, subscriptionId, error, System.currentTimeMillis());
        queue.offer(failedEvent);
        totalFailedEvents.incrementAndGet();
        logger.warn("Event added to DLQ: type={}, subscription={}, error={}", 
                event.getType(), subscriptionId, error.getMessage());
    }

    /**
     * Gets all failed events (read-only).
     *
     * @return list of failed events
     */
    public List<FailedEvent> getFailedEvents() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    /**
     * Gets the number of failed events in the queue.
     *
     * @return queue size
     */
    public int size() {
        return queue.size();
    }

    /**
     * Gets the total number of failed events (including those that were removed).
     *
     * @return total count
     */
    public long getTotalFailedEvents() {
        return totalFailedEvents.get();
    }

    /**
     * Clears all failed events from the queue.
     */
    public void clear() {
        int cleared = queue.size();
        queue.clear();
        logger.info("Cleared {} events from DLQ", cleared);
    }

    /**
     * Removes and returns the oldest failed event.
     *
     * @return oldest failed event, or null if queue is empty
     */
    public FailedEvent poll() {
        return queue.poll();
    }

    /**
     * Represents a failed event with metadata.
     */
    public static class FailedEvent {
        private final Event event;
        private final String subscriptionId;
        private final Throwable error;
        private final long failedAt;

        FailedEvent(Event event, String subscriptionId, Throwable error, long failedAt) {
            this.event = event;
            this.subscriptionId = subscriptionId;
            this.error = error;
            this.failedAt = failedAt;
        }

        public Event getEvent() {
            return event;
        }

        public String getSubscriptionId() {
            return subscriptionId;
        }

        public Throwable getError() {
            return error;
        }

        public long getFailedAt() {
            return failedAt;
        }
    }
}

