package com.pdbp.api;

/**
 * Core interface for all PDBP plugins.
 * Defines the plugin lifecycle and basic metadata contract.
 * Lifecycle:
 * INSTALLED → LOADED → INITIALIZED → STARTED → STOPPED → UNLOADED
 *
 * @author Saurabh Maurya
 */
public interface Plugin {

    /**
     * @return unique plugin identifier
     */
    String getName();

    /**
     * @return plugin version
     */
    String getVersion();

    /**
     * Initializes the plugin after it is loaded.
     *
     * @param context plugin execution context
     * @throws PluginException if initialization fails
     */
    void init(PluginContext context) throws PluginException;

    /**
     * Starts the plugin.
     *
     * @throws PluginException if startup fails
     */
    void start() throws PluginException;

    /**
     * Stops the plugin gracefully.
     *
     * @throws PluginException if shutdown fails
     */
    void stop() throws PluginException;

    /**
     * Destroys the plugin and releases resources.
     */
    void destroy();

    /**
     * @return current plugin state
     */
    PluginState getState();
}
