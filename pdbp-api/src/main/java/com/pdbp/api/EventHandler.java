package com.pdbp.api;

/**
 * Handler interface for processing events.
 *
 * @author Saurabh Maurya
 */
@FunctionalInterface
public interface EventHandler {

    /**
     * Handles an event.
     *
     * <p>This method is called asynchronously by the event bus
     * when an event matching the subscription is published.
     *
     * @param event the event to handle
     */
    void handleEvent(Event event);
}

