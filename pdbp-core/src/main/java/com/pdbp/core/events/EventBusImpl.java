package com.pdbp.core.events;

import com.pdbp.api.Event;
import com.pdbp.api.EventBus;
import com.pdbp.api.EventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory implementation of EventBus.
 *
 * <p>Features:
 * <ul>
 *   <li>Asynchronous event processing</li>
 *   <li>Type-based event routing</li>
 *   <li>Wildcard subscriptions (subscribe to all events)</li>
 *   <li>Thread-safe operations</li>
 * </ul>
 *
 * <p>Design: Singleton pattern for centralized event bus.
 *
 * @author Saurabh Maurya
 */
public class EventBusImpl implements EventBus {

    private static final Logger logger = LoggerFactory.getLogger(EventBusImpl.class);
    private static final String WILDCARD_SUBSCRIPTION = "*";

    private static EventBusImpl instance;

    // Event type -> List of handlers
    private final Map<String, List<Subscription>> subscriptionsByType;

    // All-event subscribers (wildcard)
    private final List<Subscription> wildcardSubscriptions;

    // Subscription ID -> Subscription (for unsubscribe)
    private final Map<String, Subscription> subscriptionsById;

    // Executor for asynchronous event processing
    private final ExecutorService executor;

    // Subscription ID generator
    private final AtomicInteger subscriptionIdGenerator;

    private EventBusImpl() {
        this.subscriptionsByType = new ConcurrentHashMap<>();
        this.wildcardSubscriptions = new ArrayList<>();
        this.subscriptionsById = new ConcurrentHashMap<>();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "event-bus-worker");
            t.setDaemon(true);
            return t;
        });
        this.subscriptionIdGenerator = new AtomicInteger(0);
        logger.info("EventBus initialized");
    }

    /**
     * Gets the singleton instance.
     */
    public static synchronized EventBusImpl getInstance() {
        if (instance == null) {
            instance = new EventBusImpl();
        }
        return instance;
    }

    @Override
    public void publish(Event event) {
        if (event == null) {
            logger.warn("Attempted to publish null event");
            return;
        }

        logger.info("Publishing event: type={}, source={}, timestamp={}", event.getType(), event.getSource(),
                event.getTimestamp());

        // Notify type-specific subscribers
        List<Subscription> typeSubscriptions = subscriptionsByType.get(event.getType());
        if (typeSubscriptions != null) {
            for (Subscription subscription : new ArrayList<>(typeSubscriptions)) {
                dispatchEvent(event, subscription);
            }
        }

        // Notify wildcard subscribers (subscribe to all events)
        for (Subscription subscription : new ArrayList<>(wildcardSubscriptions)) {
            dispatchEvent(event, subscription);
        }
    }

    /**
     * Dispatches an event to a subscriber asynchronously.
     */
    private void dispatchEvent(Event event, Subscription subscription) {
        executor.submit(() -> {
            try {
                subscription.getHandler().handleEvent(event);
                logger.debug("Event {} handled by subscription {}", event.getType(), subscription.getId());
            } catch (Exception e) {
                logger.error("Error handling event {} by subscription {}", event.getType(), subscription.getId(), e);
                // TODO: Add to dead letter queue in future
            }
        });
    }

    @Override
    public String subscribe(String eventType, EventHandler handler) {
        if (eventType == null || handler == null) {
            throw new IllegalArgumentException("Event type and handler cannot be null");
        }

        String subscriptionId = "sub-" + subscriptionIdGenerator.incrementAndGet();
        Subscription subscription = new Subscription(subscriptionId, eventType, handler);

        if (WILDCARD_SUBSCRIPTION.equals(eventType)) {
            synchronized (wildcardSubscriptions) {
                wildcardSubscriptions.add(subscription);
            }
        } else {
            subscriptionsByType.computeIfAbsent(eventType, k -> new ArrayList<>()).add(subscription);
        }

        subscriptionsById.put(subscriptionId, subscription);
        logger.info("Subscribed to event type: {} (subscription: {})", eventType, subscriptionId);
        return subscriptionId;
    }

    @Override
    public String subscribe(EventHandler handler) {
        return subscribe(WILDCARD_SUBSCRIPTION, handler);
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        Subscription subscription = subscriptionsById.remove(subscriptionId);
        if (subscription == null) {
            logger.warn("Subscription not found: {}", subscriptionId);
            return;
        }

        if (WILDCARD_SUBSCRIPTION.equals(subscription.getEventType())) {
            synchronized (wildcardSubscriptions) {
                wildcardSubscriptions.remove(subscription);
            }
        } else {
            List<Subscription> subscriptions = subscriptionsByType.get(subscription.getEventType());
            if (subscriptions != null) {
                subscriptions.remove(subscription);
                if (subscriptions.isEmpty()) {
                    subscriptionsByType.remove(subscription.getEventType());
                }
            }
        }

        logger.info("Unsubscribed: {}", subscriptionId);
    }

    @Override
    public int getSubscriptionCount() {
        int count = wildcardSubscriptions.size();
        for (List<Subscription> subscriptions : subscriptionsByType.values()) {
            count += subscriptions.size();
        }
        return count;
    }

    /**
     * Shuts down the event bus.
     */
    public void shutdown() {
        executor.shutdown();
        logger.info("EventBus shut down");
    }

    /**
     * Internal class to track subscriptions.
     */
    private static class Subscription {

        private final String id;
        private final String eventType;
        private final EventHandler handler;

        Subscription(String id, String eventType, EventHandler handler) {
            this.id = id;
            this.eventType = eventType;
            this.handler = handler;
        }

        String getId() {
            return id;
        }

        String getEventType() {
            return eventType;
        }

        EventHandler getHandler() {
            return handler;
        }
    }
}

