package com.pdbp.core.recovery;

import com.pdbp.api.Plugin;
import com.pdbp.api.PluginContext;
import com.pdbp.api.PluginException;
import com.pdbp.api.PluginState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles plugin recovery operations.
 *
 * @author Saurabh Maurya
 */
public class PluginRecoveryHandler {

    private static final Logger logger = LoggerFactory.getLogger(PluginRecoveryHandler.class);

    /**
     * Recovery context containing plugin and state information.
     */
    public static class RecoveryContext {

        private final Plugin plugin;
        private final PluginContext context;
        private PluginState currentState;
        private PluginState desiredState;

        public RecoveryContext(Plugin plugin, PluginContext context, PluginState currentState,
                PluginState desiredState) {
            this.plugin = plugin;
            this.context = context;
            this.currentState = currentState;
            this.desiredState = desiredState;
        }

        public Plugin getPlugin() {
            return plugin;
        }

        public PluginContext getContext() {
            return context;
        }

        public PluginState getCurrentState() {
            return currentState;
        }

        public void setCurrentState(PluginState currentState) {
            this.currentState = currentState;
        }

        public PluginState getDesiredState() {
            return desiredState;
        }

        public void setDesiredState(PluginState desiredState) {
            this.desiredState = desiredState;
        }
    }

    /**
     * Attempts to recover a plugin to its desired state.
     *
     * @param context        the recovery context
     * @param contextUpdater callback to update context with new config
     * @return true if recovery succeeded, false otherwise
     */
    public boolean recover(RecoveryContext context, ContextUpdater contextUpdater) {
        PluginState desiredState = context.getDesiredState();
        if (desiredState == null) {
            desiredState = PluginState.STARTED; // Default desired state
            context.setDesiredState(desiredState);
        }

        logger.info("Attempting to recover plugin to desired state: {}", desiredState);

        try {
            if (desiredState == PluginState.INITIALIZED) {
                return recoverToInitialized(context, contextUpdater);
            } else if (desiredState == PluginState.STARTED) {
                return recoverToStarted(context, contextUpdater);
            }
        } catch (Exception e) {
            logger.warn("Recovery attempt failed: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Recovers plugin to INITIALIZED state.
     */
    private boolean recoverToInitialized(RecoveryContext context, ContextUpdater contextUpdater)
            throws PluginException {
        PluginContext newContext = contextUpdater.updateContext(context.getPlugin());
        context.getPlugin().init(newContext);
        context.setCurrentState(PluginState.INITIALIZED);
        context.setDesiredState(null);
        logger.info("Plugin recovered to INITIALIZED state");
        return true;
    }

    /**
     * Recovers plugin to STARTED state.
     */
    private boolean recoverToStarted(RecoveryContext context, ContextUpdater contextUpdater) throws PluginException {
        // Ensure plugin is initialized first
        if (context.getCurrentState() == PluginState.LOADED || context.getCurrentState() == PluginState.FAILED) {
            PluginContext newContext = contextUpdater.updateContext(context.getPlugin());
            context.getPlugin().init(newContext);
            context.setCurrentState(PluginState.INITIALIZED);
            logger.info("Plugin initialized during recovery");
        }

        // Start the plugin
        if (context.getCurrentState() == PluginState.INITIALIZED || context.getCurrentState() == PluginState.STOPPED) {
            context.getPlugin().start();
            context.setCurrentState(PluginState.STARTED);
            context.setDesiredState(null);
            logger.info("Plugin recovered to STARTED state");
            return true;
        }

        return false;
    }

    /**
     * Functional interface for updating plugin context.
     */
    @FunctionalInterface
    public interface ContextUpdater {

        PluginContext updateContext(Plugin plugin);
    }
}

