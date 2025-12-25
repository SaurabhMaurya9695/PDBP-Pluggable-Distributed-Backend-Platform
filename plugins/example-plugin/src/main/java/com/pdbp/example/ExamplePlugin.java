package com.pdbp.example;

import com.pdbp.api.*;

import org.slf4j.Logger;

/**
 * Example plugin demonstrating the PDBP plugin API.
 *
 * <p>This plugin shows:
 * <ul>
 *   <li>Basic plugin lifecycle implementation</li>
 *   <li>Configuration access</li>
 *   <li>Logging</li>
 *   <li>State management</li>
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

    private PluginContext context;
    private Logger logger;
    private PluginState state;
    private boolean running;

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
        logger.info("Configuration greeting: {}", greeting);

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

        logger.info("{} started successfully", getName());
    }

    @Override
    public void stop() throws PluginException {
        if (state != PluginState.STARTED) {
            throw new PluginException("Plugin must be started before stopping");
        }

        logger.info("Stopping {}...", getName());

        // Stop plugin operations
        running = false;
        state = PluginState.STOPPED;

        logger.info("{} stopped", getName());
    }

    @Override
    public void destroy() {
        logger.info("Destroying {}...", getName());

        // Cleanup resources
        running = false;
        state = PluginState.UNLOADED;

        logger.info("{} destroyed", getName());
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

