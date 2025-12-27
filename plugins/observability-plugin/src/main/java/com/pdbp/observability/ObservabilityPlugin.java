package com.pdbp.observability;

import com.pdbp.api.*;

import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability plugin that collects and exposes PDBP platform metrics.
 *
 * <p>This plugin demonstrates:
 * <ul>
 *   <li>Accessing platform services via PluginContext</li>
 *   <li>Collecting system-wide metrics</li>
 *   <li>Exposing metrics for monitoring</li>
 *   <li>Real-time metrics aggregation</li>
 * </ul>
 *
 * @author Saurabh Maurya
 */
public class ObservabilityPlugin implements Plugin {

    private static final String PLUGIN_NAME = "observability-plugin";
    private static final String PLUGIN_VERSION = "1.0.0";
    private static final long METRICS_COLLECTION_INTERVAL_MS = 10000; // 10 seconds

    private PluginContext context;
    private Logger logger;
    private PluginState state;
    private volatile boolean running;
    private Thread metricsCollectorThread;
    private Object metricsCollector;
    private final Map<String, Object> collectedMetrics = new HashMap<>();
    private final AtomicLong metricsCollectionCount = new AtomicLong(0);

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

        // Access MetricsCollector via PluginContext service
        try {
            metricsCollector = context.getService(Class.forName("com.pdbp.core.metrics.MetricsCollector")).orElseThrow(
                    () -> new PluginException("MetricsCollector service not available"));

            logger.info("ObservabilityPlugin: Successfully connected to MetricsCollector");
        } catch (ClassNotFoundException e) {
            throw new PluginException("Failed to access MetricsCollector: " + e.getMessage(), e);
        }

        logger.info("ObservabilityPlugin initialized successfully");
    }

    @Override
    public void start() throws PluginException {
        if (state != PluginState.INITIALIZED && state != PluginState.STOPPED) {
            throw new PluginException("Plugin must be initialized or stopped before starting");
        }

        logger.info("Starting {}...", getName());
        logger.info("ObservabilityPlugin: Starting metrics collection");

        running = true;
        state = PluginState.STARTED;

        // Start metrics collection thread
        metricsCollectorThread = new Thread(this::collectMetrics, "ObservabilityPlugin-Collector");
        metricsCollectorThread.setDaemon(true);
        metricsCollectorThread.start();

        logger.info("{} started successfully - Metrics collection active", getName());
    }

    /**
     * Collects metrics from the platform periodically.
     */
    private void collectMetrics() {
        logger.info("ObservabilityPlugin: Metrics collection thread started");

        while (running) {
            try {
                collectPlatformMetrics();
                metricsCollectionCount.incrementAndGet();

                Thread.sleep(METRICS_COLLECTION_INTERVAL_MS);
            } catch (InterruptedException e) {
                logger.info("ObservabilityPlugin: Metrics collection interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("ObservabilityPlugin: Error collecting metrics", e);
            }
        }

        logger.info("ObservabilityPlugin: Metrics collection thread stopped");
    }

    /**
     * Collects metrics from MetricsCollector using reflection.
     */
    private void collectPlatformMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();

            // Collect plugin metrics
            @SuppressWarnings("unchecked") Map<String, Object> pluginMetrics = (Map<String, Object>) invokeMethod(
                    "getPluginMetrics");
            metrics.put("plugins", pluginMetrics);

            // Collect system metrics
            metrics.put("totalPluginsInstalled", invokeMethod("getTotalPluginsInstalled"));
            metrics.put("totalPluginsStarted", invokeMethod("getTotalPluginsStarted"));
            metrics.put("totalPluginsStopped", invokeMethod("getTotalPluginsStopped"));
            metrics.put("totalPluginsUnloaded", invokeMethod("getTotalPluginsUnloaded"));
            metrics.put("totalPluginErrors", invokeMethod("getTotalPluginErrors"));
            metrics.put("serverUptime", invokeMethod("getServerUptime"));
            metrics.put("totalApiRequests", invokeMethod("getTotalApiRequests"));
            metrics.put("totalApiErrors", invokeMethod("getTotalApiErrors"));

            // Collect API endpoint counts
            @SuppressWarnings("unchecked") Map<String, Long> apiEndpoints = (Map<String, Long>) invokeMethod(
                    "getApiEndpointCounts");
            metrics.put("apiEndpoints", apiEndpoints);

            // Collect operation durations
            @SuppressWarnings("unchecked") Map<String, Long> operationDurations = (Map<String, Long>) invokeMethod(
                    "getOperationDurations");
            metrics.put("operationDurations", operationDurations);

            // Store collected metrics
            synchronized (collectedMetrics) {
                collectedMetrics.clear();
                collectedMetrics.putAll(metrics);
                collectedMetrics.put("lastCollectionTime", System.currentTimeMillis());
                collectedMetrics.put("collectionCount", metricsCollectionCount.get());
            }

            logger.debug("ObservabilityPlugin: Collected {} metrics", metrics.size());
        } catch (Exception e) {
            logger.error("ObservabilityPlugin: Error collecting platform metrics", e);
        }
    }

    /**
     * Invokes a method on MetricsCollector using reflection.
     */
    private Object invokeMethod(String methodName) throws Exception {
        Method method = metricsCollector.getClass().getMethod(methodName);
        return method.invoke(metricsCollector);
    }

    /**
     * Gets the collected metrics snapshot.
     */
    public Map<String, Object> getMetrics() {
        synchronized (collectedMetrics) {
            return new HashMap<>(collectedMetrics);
        }
    }

    @Override
    public void stop() throws PluginException {
        if (state != PluginState.STARTED) {
            throw new PluginException("Plugin must be started before stopping");
        }

        logger.info("Stopping {}...", getName());
        logger.info("ObservabilityPlugin: Stopping metrics collection");

        running = false;

        if (metricsCollectorThread != null && metricsCollectorThread.isAlive()) {
            try {
                metricsCollectorThread.interrupt();
                metricsCollectorThread.join(2000);
                logger.info("ObservabilityPlugin: Metrics collection stopped");
            } catch (InterruptedException e) {
                logger.warn("ObservabilityPlugin: Interrupted while stopping");
                Thread.currentThread().interrupt();
            }
        }

        state = PluginState.STOPPED;
        logger.info("{} stopped successfully", getName());
        logger.info("ObservabilityPlugin: Total metrics collections: {}", metricsCollectionCount.get());
    }

    @Override
    public void destroy() {
        logger.info("Destroying {}...", getName());
        logger.info("ObservabilityPlugin: Cleaning up");

        running = false;
        if (metricsCollectorThread != null && metricsCollectorThread.isAlive()) {
            metricsCollectorThread.interrupt();
        }

        synchronized (collectedMetrics) {
            collectedMetrics.clear();
        }
        metricsCollectionCount.set(0);
        state = PluginState.UNLOADED;

        logger.info("{} destroyed", getName());
        logger.info("ObservabilityPlugin: Cleanup completed");
    }

    @Override
    public PluginState getState() {
        return state;
    }
}

