package com.pdbp.admin;

import com.pdbp.admin.service.LogRotationService;
import com.pdbp.core.PluginManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static spark.Spark.stop;

/**
 * Handles server lifecycle operations (startup, shutdown).
 *
 * @author Saurabh Maurya
 */
final class ServerLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(ServerLifecycle.class);
    private final PluginManager pluginManager;
    private LogRotationService logRotationService;

    ServerLifecycle(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    void setLogRotationService(LogRotationService logRotationService) {
        this.logRotationService = logRotationService;
    }

    /**
     * Performs graceful shutdown of the server.
     */
    void shutdown() {
        logger.info("Shutting down PDBP Server...");

        try {
            // Stop log rotation service
            if (logRotationService != null) {
                logRotationService.stop();
            }

            unloadAllPlugins();
            stop();
            logger.info("PDBP Server stopped");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    /**
     * Unloads all installed plugins.
     */
    private void unloadAllPlugins() {
        pluginManager.listPlugins().forEach(pluginName -> {
            try {
                pluginManager.unloadPlugin(pluginName);
            } catch (Exception ex) {
                logger.warn("Failed to unload plugin: {}", pluginName, ex);
            }
        });
    }
}

