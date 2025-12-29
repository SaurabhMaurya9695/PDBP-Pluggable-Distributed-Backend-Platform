package com.pdbp.core.healing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Self-healing service that automatically recovers from plugin failures.
 *
 * <p>How it works:
 * <ol>
 *   <li>Detects failures when plugins throw exceptions or enter FAILED state</li>
 *   <li>Uses circuit breaker to prevent cascading failures</li>
 *   <li>Automatically restarts failed plugins with exponential backoff</li>
 *   <li>Disables plugin after max retries</li>
 * </ol>
 *
 * <p>Design: Single Responsibility - handles all failure detection and recovery.
 *
 * @author Saurabh Maurya
 */
public class SelfHealingService {

    private static final Logger logger = LoggerFactory.getLogger(SelfHealingService.class);
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 5000; // 5 seconds
    private static final long DEFAULT_MAX_BACKOFF_MS = 60000; // 60 seconds

    // Plugin recovery tracking: pluginName -> retry count
    private final Map<String, RecoveryInfo> recoveryInfo;
    
    // Circuit breakers per plugin (prevents infinite restart loops)
    private final Map<String, CircuitBreaker> circuitBreakers;
    
    // Scheduler for recovery attempts
    private final ScheduledExecutorService scheduler;
    
    // Callback to restart plugin (set by PluginManager)
    private Consumer<String> restartPluginCallback;
    
    // Callback to alert operator (set by PluginManager)
    private Consumer<String> alertCallback;

    // Configuration
    private final int maxRetries;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    /**
     * Creates a self-healing service.
     *
     * @param maxRetries    maximum number of restart attempts (default: 3)
     * @param initialBackoffMs initial backoff time in milliseconds (default: 5000)
     * @param maxBackoffMs  maximum backoff time in milliseconds (default: 60000)
     */
    public SelfHealingService(int maxRetries, long initialBackoffMs, long maxBackoffMs) {
        this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        this.initialBackoffMs = initialBackoffMs > 0 ? initialBackoffMs : DEFAULT_INITIAL_BACKOFF_MS;
        this.maxBackoffMs = maxBackoffMs > 0 ? maxBackoffMs : DEFAULT_MAX_BACKOFF_MS;
        
        this.recoveryInfo = new ConcurrentHashMap<>();
        this.circuitBreakers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "self-healing-worker");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("SelfHealingService initialized: maxRetries={}, backoff={}ms-{}ms", 
                this.maxRetries, this.initialBackoffMs, this.maxBackoffMs);
    }

    /**
     * Registers a plugin for self-healing.
     *
     * @param pluginName the plugin name
     */
    public void registerPlugin(String pluginName) {
        recoveryInfo.put(pluginName, new RecoveryInfo(pluginName));
        circuitBreakers.put(pluginName, new CircuitBreaker(pluginName, 3, 30000)); // 3 failures, 30s timeout
        logger.info("Registered plugin for self-healing: {}", pluginName);
    }

    /**
     * Unregisters a plugin from self-healing.
     *
     * @param pluginName the plugin name
     */
    public void unregisterPlugin(String pluginName) {
        recoveryInfo.remove(pluginName);
        circuitBreakers.remove(pluginName);
        logger.debug("Unregistered plugin from self-healing: {}", pluginName);
    }

    /**
     * Handles plugin failure and initiates recovery.
     *
     * <p>This is called when:
     * <ul>
     *   <li>Plugin throws exception during lifecycle operation</li>
     *   <li>Plugin state transitions to FAILED</li>
     * </ul>
     *
     * @param pluginName the plugin name
     * @param error      the error that occurred (can be null)
     */
    public void handlePluginFailure(String pluginName, Throwable error) {
        RecoveryInfo info = recoveryInfo.get(pluginName);
        if (info == null) {
            logger.warn("Plugin not registered for self-healing: {}", pluginName);
            return;
        }

        CircuitBreaker circuitBreaker = circuitBreakers.get(pluginName);
        if (circuitBreaker != null) {
            circuitBreaker.recordFailure();
        }

        int failureCount = info.incrementFailureCount();
        logger.info("Plugin failure detected: {} (failure count: {})", pluginName, failureCount);

        // Check if we should attempt recovery
        if (failureCount <= maxRetries) {
            scheduleRecovery(pluginName, info);
        } else {
            logger.info("Max retries exceeded for plugin: {}. Disabling plugin.", pluginName);
            disablePlugin(pluginName);
        }
    }

    /**
     * Records a successful operation (resets failure count).
     *
     * @param pluginName the plugin name
     */
    public void recordSuccess(String pluginName) {
        RecoveryInfo info = recoveryInfo.get(pluginName);
        if (info != null) {
            info.reset();
        }
        CircuitBreaker circuitBreaker = circuitBreakers.get(pluginName);
        if (circuitBreaker != null) {
            circuitBreaker.recordSuccess();
        }
    }

    /**
     * Schedules a recovery attempt with exponential backoff.
     */
    private void scheduleRecovery(String pluginName, RecoveryInfo info) {
        long backoffMs = calculateBackoff(info.getFailureCount());
        logger.info("Scheduling recovery for plugin: {} in {}ms (attempt {}/{})", 
                pluginName, backoffMs, info.getFailureCount(), maxRetries);

        scheduler.schedule(() -> {
            try {
                attemptRecovery(pluginName, info);
            } catch (Exception e) {
                logger.error("Error during recovery attempt for plugin: {}", pluginName, e);
            }
        }, backoffMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Attempts to recover a plugin by restarting it.
     */
    private void attemptRecovery(String pluginName, RecoveryInfo info) {
        logger.info("Attempting recovery for plugin: {} (attempt {})", pluginName, info.getFailureCount());

        CircuitBreaker circuitBreaker = circuitBreakers.get(pluginName);
        if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
            logger.info("Circuit breaker is OPEN for plugin: {}, skipping recovery", pluginName);
            return;
        }

        if (restartPluginCallback == null) {
            logger.info("Restart callback not set, cannot recover plugin: {}", pluginName);
            return;
        }

        try {
            // Call restart callback (provided by PluginManager)
            restartPluginCallback.accept(pluginName);
            logger.info("Recovery attempt initiated for plugin: {}", pluginName);
            
            // Note: Success will be recorded when plugin successfully starts
            // Failure will trigger another handlePluginFailure call
        } catch (Exception e) {
            logger.error("Recovery attempt failed for plugin: {}", pluginName, e);
            // Will retry if under max retries
        }
    }

    /**
     * Calculates exponential backoff time.
     * Formula: initialBackoff * 2^(failureCount-1), capped at maxBackoff
     */
    private long calculateBackoff(int failureCount) {
        long backoff = initialBackoffMs * (1L << (failureCount - 1)); // 2^(n-1) * initial
        return Math.min(backoff, maxBackoffMs);
    }

    /**
     * Disables a plugin after max retries.
     */
    private void disablePlugin(String pluginName) {
        logger.error("Disabling plugin after max retries: {}", pluginName);
        if (alertCallback != null) {
            alertCallback.accept(pluginName);
        }
        // Plugin remains in FAILED state, can be manually restarted via API
    }

    /**
     * Sets the callback for restarting plugins.
     * Called by PluginManager to provide restart functionality.
     *
     * @param callback the restart callback
     */
    public void setRestartPluginCallback(Consumer<String> callback) {
        this.restartPluginCallback = callback;
    }

    /**
     * Sets the callback for alerting operators.
     *
     * @param callback the alert callback
     */
    public void setAlertCallback(Consumer<String> callback) {
        this.alertCallback = callback;
    }

    /**
     * Gets the circuit breaker for a plugin.
     *
     * @param pluginName the plugin name
     * @return circuit breaker, or null if not registered
     */
    public CircuitBreaker getCircuitBreaker(String pluginName) {
        return circuitBreakers.get(pluginName);
    }

    /**
     * Shuts down the self-healing service.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("SelfHealingService shut down");
    }

    /**
     * Internal class to track recovery information for a plugin.
     */
    private static class RecoveryInfo {
        private final String pluginName;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile long lastFailureTime;

        RecoveryInfo(String pluginName) {
            this.pluginName = pluginName;
        }

        int incrementFailureCount() {
            lastFailureTime = System.currentTimeMillis();
            return failureCount.incrementAndGet();
        }

        int getFailureCount() {
            return failureCount.get();
        }

        void reset() {
            failureCount.set(0);
        }

        long getLastFailureTime() {
            return lastFailureTime;
        }
    }
}
