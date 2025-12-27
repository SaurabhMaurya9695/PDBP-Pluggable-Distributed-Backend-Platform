package com.pdbp.api;

/**
 * Interface for accessing platform services from plugins.
 *
 * <p>Plugins can use this to access platform capabilities like metrics collection.
 *
 * @author Saurabh Maurya
 */
public interface PlatformService {

    /**
     * Gets the metrics collector instance.
     * Plugins can use this to access platform metrics.
     *
     * @return metrics collector class name (for reflection access)
     */
    default String getMetricsCollectorClassName() {
        return "com.pdbp.core.metrics.MetricsCollector";
    }
}

