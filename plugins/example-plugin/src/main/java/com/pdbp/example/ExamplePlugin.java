package com.pdbp.example;

import com.pdbp.api.Event;
import com.pdbp.api.EventBus;
import com.pdbp.api.EventFactory;
import com.pdbp.api.Plugin;
import com.pdbp.api.PluginContext;
import com.pdbp.api.PluginException;
import com.pdbp.api.PluginState;

import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example plugin demonstrating the PDBP plugin API.
 *
 * <p>This plugin shows:
 * <ul>
 *   <li>Basic plugin lifecycle implementation</li>
 *   <li>Configuration access</li>
 *   <li>Logging</li>
 *   <li>State management</li>
 *   <li>Background work execution</li>
 * </ul>
 *
 * <p>Design: Follows the Plugin interface contract and demonstrates
 * proper lifecycle management.
 *
 * @author PDBP Team
 * @version 1.0
 */
public class ExamplePlugin implements Plugin {

    private static final String PLUGIN_NAME = "example-plugin";
    private static final String PLUGIN_VERSION = "1.0.0";
    private static final long DEFAULT_WORK_INTERVAL_MS = 5000; // 5 seconds

    private PluginContext context;
    private Logger logger;
    private PluginState state;
    private volatile boolean running;
    private Thread workerThread;
    private final AtomicInteger workCounter = new AtomicInteger(0);
    
    // Event bus for publishing/subscribing to events
    private EventBus eventBus;
    private String allEventsSubscriptionId;
    
    // Configuration values (loaded from config file)
    private long workInterval = DEFAULT_WORK_INTERVAL_MS;
    private boolean enableLogging = true;
    private int maxWorkItems = 100;

    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @Override
    public String getVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public void init(PluginContext context) throws PluginException {
        this.context = context;
        this.logger = context.getLogger();
        this.state = PluginState.INITIALIZED;

        logger.info("Initializing {} v{}", getName(), getVersion());

        // Access configuration
        String greeting = context.getConfig("greeting", "Hello from Example Plugin!");
        String workIntervalStr = context.getConfig("workInterval", "5000");
        String enableLoggingStr = context.getConfig("enableLogging", "true");
        String maxWorkItemsStr = context.getConfig("maxWorkItems", "100");
        String simulateFailureStr = context.getConfig("simulateFailure", "false");
        
        try {
            long workInterval = Long.parseLong(workIntervalStr);
            boolean enableLogging = Boolean.parseBoolean(enableLoggingStr);
            int maxWorkItems = Integer.parseInt(maxWorkItemsStr);
            boolean simulateFailure = Boolean.parseBoolean(simulateFailureStr);
            
            logger.info("Configuration loaded:");
            logger.info("  - greeting: {}", greeting);
            logger.info("  - workInterval: {}ms", workInterval);
            logger.info("  - enableLogging: {}", enableLogging);
            logger.info("  - maxWorkItems: {}", maxWorkItems);
            logger.info("  - simulateFailure: {} (⚠️ Set to 'true' to test self-healing)", simulateFailure);
            
            // Store config values for use in start()
            this.workInterval = workInterval;
            this.enableLogging = enableLogging;
            this.maxWorkItems = maxWorkItems;
        } catch (NumberFormatException e) {
            logger.warn("Invalid configuration value, using defaults", e);
        }

        // Subscribe to EventBus to listen to all events (demonstration)
        Optional<EventBus> eventBusOpt = context.getService(EventBus.class);
        if (eventBusOpt.isPresent()) {
            this.eventBus = eventBusOpt.get();
            // Subscribe to all events to demonstrate event listening
            this.allEventsSubscriptionId = eventBus.subscribe((event) -> {
                logger.info("ExamplePlugin: Received event - type={}, source={}", event.getType(), event.getSource());
            });
            logger.info("ExamplePlugin: Subscribed to EventBus (listening to all events)");
        } else {
            logger.warn("ExamplePlugin: EventBus not available");
        }

        logger.info("Example plugin initialized successfully");
    }

    @Override
    public void start() throws PluginException {
        if (state != PluginState.INITIALIZED && state != PluginState.STOPPED) {
            throw new PluginException("Plugin must be initialized or stopped before starting");
        }

        logger.info("Starting {}...", getName());

        // Start plugin operations
        running = true;
        state = PluginState.STARTED;

        // Publish a custom event to demonstrate event publishing
        if (eventBus != null) {
            try {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("version", getVersion());
                payload.put("workInterval", workInterval);
                
                Event event = EventFactory.createEvent("ExamplePluginStarted", getName(), payload);
                eventBus.publish(event);
                logger.info("ExamplePlugin: Published ExamplePluginStarted event");
            } catch (Exception e) {
                logger.warn("ExamplePlugin: Failed to publish event", e);
            }
        }

        // Start background worker thread
        workerThread = new Thread(this::performWork, "ExamplePlugin-Worker");
        workerThread.setDaemon(true);
        workerThread.start();

        logger.info("{} started successfully - Worker thread started", getName());
    }

    /**
     * Performs background work while the plugin is running.
     */
    private void performWork() {
        logger.info("ExamplePlugin: Worker thread started - Beginning work cycle");

        while (running && workCounter.get() < maxWorkItems) {
            try {
                int count = workCounter.incrementAndGet();
                if (enableLogging) {
                    logger.info("ExamplePlugin: Performing work iteration #{}/{} - Plugin is active and running", 
                            count, maxWorkItems);
                }

                // Simulate some work
                Thread.sleep(workInterval);

            } catch (InterruptedException e) {
                logger.info("ExamplePlugin: Worker thread interrupted - Stopping work");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("ExamplePlugin: Error during work execution", e);
            }
        }

        logger.info("ExamplePlugin: Worker thread stopped - Work cycle completed (total iterations: {})", 
                workCounter.get());
    }

    @Override
    public void stop() throws PluginException {
        if (state != PluginState.STARTED) {
            throw new PluginException("Plugin must be started before stopping");
        }

        logger.info("Stopping {}...", getName());
        logger.info("ExamplePlugin: Initiating graceful shutdown");

        // Signal worker thread to stop
        running = false;

        // Wait for worker thread to finish (with timeout)
        if (workerThread != null && workerThread.isAlive()) {
            try {
                workerThread.interrupt();
                workerThread.join(2000); // Wait up to 2 seconds
                if (workerThread.isAlive()) {
                    logger.warn("ExamplePlugin: Worker thread did not stop gracefully within timeout");
                } else {
                    logger.info("ExamplePlugin: Worker thread stopped gracefully");
                }
            } catch (InterruptedException e) {
                logger.warn("ExamplePlugin: Interrupted while waiting for worker thread to stop");
                Thread.currentThread().interrupt();
            }
        }

        // Unsubscribe from events
        if (eventBus != null && allEventsSubscriptionId != null) {
            try {
                eventBus.unsubscribe(allEventsSubscriptionId);
                logger.info("ExamplePlugin: Unsubscribed from EventBus");
            } catch (Exception e) {
                logger.warn("ExamplePlugin: Error unsubscribing from events", e);
            }
        }

        state = PluginState.STOPPED;
        logger.info("{} stopped successfully", getName());
        logger.info("ExamplePlugin: Total work iterations completed: {}", workCounter.get());
    }

    @Override
    public void destroy() {
        logger.info("Destroying {}...", getName());
        logger.info("ExamplePlugin: Cleaning up resources");

        // Ensure worker thread is stopped
        running = false;
        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
        }

        // Reset state
        workCounter.set(0);
        state = PluginState.UNLOADED;

        logger.info("{} destroyed", getName());
        logger.info("ExamplePlugin: Cleanup completed");
    }

    @Override
    public PluginState getState() {
        return state;
    }

    /**
     * Checks if the plugin is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }
}

