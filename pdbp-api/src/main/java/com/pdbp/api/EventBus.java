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
}

