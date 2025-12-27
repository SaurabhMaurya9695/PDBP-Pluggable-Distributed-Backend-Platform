package com.pdbp.core.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects metrics for the PDBP platform.
 *
 * <p>Thread-safe metrics collection for:
 * <ul>
 *   <li>Plugin lifecycle events</li>
 *   <li>Plugin performance metrics</li>
 *   <li>System metrics</li>
 *   <li>API request metrics</li>
 * </ul>
 *
 * @author Saurabh Maurya
 */
public class MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);
    private static volatile MetricsCollector instance;

    // Plugin metrics
    private final Map<String, PluginMetrics> pluginMetrics = new ConcurrentHashMap<>();
    private final LongAdder totalPluginsInstalled = new LongAdder();
    private final LongAdder totalPluginsStarted = new LongAdder();
    private final LongAdder totalPluginsStopped = new LongAdder();
    private final LongAdder totalPluginsUnloaded = new LongAdder();
    private final LongAdder totalPluginErrors = new LongAdder();

    // System metrics
    private final AtomicLong serverStartTime = new AtomicLong(System.currentTimeMillis());
    private final LongAdder totalApiRequests = new LongAdder();
    private final LongAdder totalApiErrors = new LongAdder();
    private final Map<String, LongAdder> apiEndpointCounts = new ConcurrentHashMap<>();

    // Performance metrics
    private final Map<String, LongAdder> pluginOperationDurations = new ConcurrentHashMap<>();

    private MetricsCollector() {
        logger.debug("MetricsCollector initialized");
    }

    /**
     * Gets the singleton instance.
     */
    public static MetricsCollector getInstance() {
        if (instance == null) {
            synchronized (MetricsCollector.class) {
                if (instance == null) {
                    instance = new MetricsCollector();
                }
            }
        }
        return instance;
    }

    // Plugin lifecycle metrics
    public void recordPluginInstalled(String pluginName, long durationMs) {
        totalPluginsInstalled.increment();
        getPluginMetrics(pluginName).recordInstall(durationMs);
        logger.debug("Recorded plugin install: {} ({}ms)", pluginName, durationMs);
    }

    public void recordPluginStarted(String pluginName, long durationMs) {
        totalPluginsStarted.increment();
        getPluginMetrics(pluginName).recordStart(durationMs);
        logger.debug("Recorded plugin start: {} ({}ms)", pluginName, durationMs);
    }

    public void recordPluginStopped(String pluginName, long durationMs) {
        totalPluginsStopped.increment();
        getPluginMetrics(pluginName).recordStop(durationMs);
        logger.debug("Recorded plugin stop: {} ({}ms)", pluginName, durationMs);
    }

    public void recordPluginUnloaded(String pluginName) {
        totalPluginsUnloaded.increment();
        pluginMetrics.remove(pluginName);
        logger.debug("Recorded plugin unload: {}", pluginName);
    }

    public void recordPluginError(String pluginName, String operation) {
        totalPluginErrors.increment();
        getPluginMetrics(pluginName).recordError(operation);
        logger.debug("Recorded plugin error: {} - {}", pluginName, operation);
    }

    // API metrics
    public void recordApiRequest(String endpoint) {
        totalApiRequests.increment();
        apiEndpointCounts.computeIfAbsent(endpoint, k -> new LongAdder()).increment();
    }

    public void recordApiError(String endpoint) {
        totalApiErrors.increment();
    }

    // Performance metrics
    public void recordOperationDuration(String operation, long durationMs) {
        pluginOperationDurations.computeIfAbsent(operation, k -> new LongAdder()).add(durationMs);
    }

    // Getters for metrics
    public Map<String, PluginMetrics> getPluginMetrics() {
        return new ConcurrentHashMap<>(pluginMetrics);
    }

    public long getTotalPluginsInstalled() {
        return totalPluginsInstalled.sum();
    }

    public long getTotalPluginsStarted() {
        return totalPluginsStarted.sum();
    }

    public long getTotalPluginsStopped() {
        return totalPluginsStopped.sum();
    }

    public long getTotalPluginsUnloaded() {
        return totalPluginsUnloaded.sum();
    }

    public long getTotalPluginErrors() {
        return totalPluginErrors.sum();
    }

    public long getServerUptime() {
        return System.currentTimeMillis() - serverStartTime.get();
    }

    public long getTotalApiRequests() {
        return totalApiRequests.sum();
    }

    public long getTotalApiErrors() {
        return totalApiErrors.sum();
    }

    public Map<String, Long> getApiEndpointCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        apiEndpointCounts.forEach((endpoint, adder) -> result.put(endpoint, adder.sum()));
        return result;
    }

    public Map<String, Long> getOperationDurations() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        pluginOperationDurations.forEach((operation, adder) -> result.put(operation, adder.sum()));
        return result;
    }

    /**
     * Gets or creates plugin metrics.
     */
    private PluginMetrics getPluginMetrics(String pluginName) {
        return pluginMetrics.computeIfAbsent(pluginName, k -> new PluginMetrics(pluginName));
    }

    /**
     * Plugin-specific metrics.
     */
    public static class PluginMetrics {

        private final String pluginName;
        private final AtomicLong installCount = new AtomicLong(0);
        private final AtomicLong startCount = new AtomicLong(0);
        private final AtomicLong stopCount = new AtomicLong(0);
        private final LongAdder totalInstallDuration = new LongAdder();
        private final LongAdder totalStartDuration = new LongAdder();
        private final LongAdder totalStopDuration = new LongAdder();
        private final LongAdder errorCount = new LongAdder();
        private final AtomicLong lastInstallTime = new AtomicLong(0);
        private final AtomicLong lastStartTime = new AtomicLong(0);
        private final AtomicLong lastStopTime = new AtomicLong(0);

        PluginMetrics(String pluginName) {
            this.pluginName = pluginName;
        }

        void recordInstall(long durationMs) {
            installCount.incrementAndGet();
            totalInstallDuration.add(durationMs);
            lastInstallTime.set(System.currentTimeMillis());
        }

        void recordStart(long durationMs) {
            startCount.incrementAndGet();
            totalStartDuration.add(durationMs);
            lastStartTime.set(System.currentTimeMillis());
        }

        void recordStop(long durationMs) {
            stopCount.incrementAndGet();
            totalStopDuration.add(durationMs);
            lastStopTime.set(System.currentTimeMillis());
        }

        void recordError(String operation) {
            errorCount.increment();
        }

        public String getPluginName() {
            return pluginName;
        }

        public long getInstallCount() {
            return installCount.get();
        }

        public long getStartCount() {
            return startCount.get();
        }

        public long getStopCount() {
            return stopCount.get();
        }

        public long getTotalInstallDuration() {
            return totalInstallDuration.sum();
        }

        public long getTotalStartDuration() {
            return totalStartDuration.sum();
        }

        public long getTotalStopDuration() {
            return totalStopDuration.sum();
        }

        public double getAverageInstallDuration() {
            long count = installCount.get();
            return count > 0 ? (double) totalInstallDuration.sum() / count : 0;
        }

        public double getAverageStartDuration() {
            long count = startCount.get();
            return count > 0 ? (double) totalStartDuration.sum() / count : 0;
        }

        public double getAverageStopDuration() {
            long count = stopCount.get();
            return count > 0 ? (double) totalStopDuration.sum() / count : 0;
        }

        public long getErrorCount() {
            return errorCount.sum();
        }

        public long getLastInstallTime() {
            return lastInstallTime.get();
        }

        public long getLastStartTime() {
            return lastStartTime.get();
        }

        public long getLastStopTime() {
            return lastStopTime.get();
        }
    }
}

