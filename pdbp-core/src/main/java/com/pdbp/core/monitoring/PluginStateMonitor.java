package com.pdbp.core.monitoring;

import com.pdbp.api.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors and logs plugin states periodically.
 *
 * @author Saurabh Maurya
 */
public class PluginStateMonitor {

    private static final Logger logger = LoggerFactory.getLogger(PluginStateMonitor.class);
    private static final String MONITOR_INTERVAL_PROPERTY = "pdbp.state.monitor.interval.ms";
    private static final long DEFAULT_INTERVAL_MS = 30000L; // 30 sec

    private final ScheduledExecutorService executor;
    private final long intervalMs;
    private final StateProvider stateProvider;

    /**
     * Functional interface for providing plugin states.
     */
    @FunctionalInterface
    public interface StateProvider {
        Map<String, PluginStateInfo> getPluginStates();
    }

    /**
     * Plugin state information.
     */
    public static class PluginStateInfo {
        private final PluginState currentState;
        private final PluginState desiredState;

        public PluginStateInfo(PluginState currentState, PluginState desiredState) {
            this.currentState = currentState;
            this.desiredState = desiredState;
        }

        public PluginState getCurrentState() {
            return currentState;
        }

        public PluginState getDesiredState() {
            return desiredState;
        }
    }

    public PluginStateMonitor(StateProvider stateProvider) {
        this.stateProvider = stateProvider;
        this.intervalMs = Long.parseLong(System.getProperty(MONITOR_INTERVAL_PROPERTY, String.valueOf(DEFAULT_INTERVAL_MS)));
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "plugin-state-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the state monitoring.
     */
    public void start() {
        executor.scheduleWithFixedDelay(this::logPluginStates, 0, intervalMs, TimeUnit.MILLISECONDS);
        logger.info("Plugin state monitor started interval: {}ms", intervalMs);
    }

    /**
     * Logs current state of all plugins.
     */
    private void logPluginStates() {
        Map<String, PluginStateInfo> states = stateProvider.getPluginStates();
        if (states.isEmpty()) {
            return;
        }

        StringBuilder stateInfo = new StringBuilder("Plugin States: ");
        states.forEach((pluginName, info) -> {
            stateInfo.append(pluginName).append("=").append(info.getCurrentState());
            if (info.getDesiredState() != null) {
                stateInfo.append("(desired:").append(info.getDesiredState()).append(")");
            }
            stateInfo.append(" ");
        });

        logger.info(stateInfo.toString().trim());
    }

    /**
     * Shuts down the monitor.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.debug("Plugin state monitor shut down");
    }
}

