package com.pdbp.core.healing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker pattern implementation for plugins.
 *
 * <p>Prevents cascading failures by "opening" the circuit when failure
 * threshold is exceeded, and allowing recovery attempts after timeout.
 *
 * <p>States:
 * <ul>
 *   <li>CLOSED: Normal operation, requests pass through</li>
 *   <li>OPEN: Too many failures, requests blocked</li>
 *   <li>HALF_OPEN: Testing if plugin recovered</li>
 * </ul>
 *
 * @author Saurabh Maurya
 */
public class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Circuit open, blocking requests
        HALF_OPEN  // Testing recovery
    }

    private final String pluginName;
    private final int failureThreshold;
    private final long timeoutMs;
    private final long halfOpenTimeoutMs;

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastStateChangeTime = new AtomicLong(System.currentTimeMillis());

    /**
     * Creates a circuit breaker.
     *
     * @param pluginName       the plugin name
     * @param failureThreshold number of failures before opening circuit
     * @param timeoutMs        time to wait before attempting recovery (half-open)
     */
    public CircuitBreaker(String pluginName, int failureThreshold, long timeoutMs) {
        this.pluginName = pluginName;
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
        this.halfOpenTimeoutMs = timeoutMs / 2; // Half-open timeout is half of full timeout
    }

    /**
     * Records a successful operation.
     */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            successCount.incrementAndGet();
            // If we get enough successes, close the circuit
            if (successCount.get() >= 2) {
                close();
            }
        } else if (state == State.CLOSED) {
            // Reset failure count on success
            failureCount.set(0);
        }
    }

    /**
     * Records a failed operation.
     */
    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        failureCount.incrementAndGet();

        if (state == State.CLOSED) {
            if (failureCount.get() >= failureThreshold) {
                open();
            }
        } else if (state == State.HALF_OPEN) {
            // Failure during half-open, open again
            open();
        }
    }

    /**
     * Checks if the circuit allows the operation.
     *
     * @return true if operation should proceed, false if blocked
     */
    public boolean allowRequest() {
        long now = System.currentTimeMillis();

        if (state == State.CLOSED) {
            return true;
        } else if (state == State.OPEN) {
            // Check if timeout has passed, transition to half-open
            if (now - lastStateChangeTime.get() >= timeoutMs) {
                halfOpen();
                return true; // Allow one request to test
            }
            return false; // Still in timeout, block requests
        } else { // HALF_OPEN
            // Allow requests in half-open state
            return true;
        }
    }

    /**
     * Opens the circuit.
     */
    private void open() {
        state = State.OPEN;
        lastStateChangeTime.set(System.currentTimeMillis());
        successCount.set(0);
        logger.warn("Circuit breaker OPENED for plugin: {} (failures: {})", pluginName, failureCount.get());
    }

    /**
     * Closes the circuit (normal operation).
     */
    private void close() {
        state = State.CLOSED;
        lastStateChangeTime.set(System.currentTimeMillis());
        failureCount.set(0);
        successCount.set(0);
        logger.info("Circuit breaker CLOSED for plugin: {}", pluginName);
    }

    /**
     * Transitions to half-open state (testing recovery).
     */
    private void halfOpen() {
        state = State.HALF_OPEN;
        lastStateChangeTime.set(System.currentTimeMillis());
        successCount.set(0);
        logger.info("Circuit breaker HALF_OPEN for plugin: {} (testing recovery)", pluginName);
    }

    /**
     * Gets the current state.
     */
    public State getState() {
        return state;
    }

    /**
     * Gets the failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Resets the circuit breaker.
     */
    public void reset() {
        close();
        logger.info("Circuit breaker RESET for plugin: {}", pluginName);
    }
}

