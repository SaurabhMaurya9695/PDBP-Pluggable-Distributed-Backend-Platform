package com.pdbp.api;

/**
 * Event bus interface for publishing and subscribing to events.
 *
 * <p>The event bus enables loose coupling between plugins through
 * asynchronous event-driven communication.
 *
 * <p>Design: PlatformService - can be accessed via PluginContext.getService()
 *
 * @author Saurabh Maurya
 */
public interface EventBus extends PlatformService {

    /**
     * Publishes an event to the event bus.
     *
     * <p>All subscribers matching the event type will be notified asynchronously.
     *
     * @param event the event to publish
     */
    void publish(Event event);

    /**
     * Subscribes to events of a specific type.
     *
     * <p>The handler will be called asynchronously for all events matching the type.
     *
     * @param eventType the event type to subscribe to (e.g., "PaymentProcessed")
     * @param handler   the event handler
     * @return subscription ID (can be used to unsubscribe)
     */
    String subscribe(String eventType, EventHandler handler);

    /**
     * Subscribes to all events.
     *
     * <p>The handler will be called for all events published to the bus.
     *
     * @param handler the event handler
     * @return subscription ID (can be used to unsubscribe)
     */
    String subscribe(EventHandler handler);

    /**
     * Unsubscribes from events.
     *
     * @param subscriptionId the subscription ID returned from subscribe()
     */
    void unsubscribe(String subscriptionId);

    /**
     * Gets the number of active subscriptions.
     *
     * @return number of active subscriptions
     */
    int getSubscriptionCount();

    /**
     * Replays events from persistence.
     *
     * <p>All events matching the criteria will be republished to current subscribers.
     *
     * @param eventType optional event type filter (null = all events)
     * @param limit     maximum number of events to replay (0 = all)
     * @return number of events replayed
     */
    int replayEvents(String eventType, int limit);

    /**
     * Gets the number of failed events in the Dead Letter Queue.
     *
     * @return DLQ size
     */
    int getDeadLetterQueueSize();

    /**
     * Gets the total number of failed events (including those removed from DLQ).
     *
     * @return total failed events count
     */
    long getTotalFailedEvents();

    /**
     * Replays failed events from the Dead Letter Queue.
     *
     * @param limit maximum number of events to replay (0 = all)
     * @return number of events replayed
     */
    int replayFailedEvents(int limit);

    /**
     * Clears the Dead Letter Queue.
     */
    void clearDeadLetterQueue();
}

