package com.pdbp.api;

/**
 * Represents the lifecycle state of a plugin.
 *
 * @author : Saurabh Maurya
 */
public enum PluginState {

    /**
     * Plugin JAR is discovered but not yet loaded into memory.
     */
    INSTALLED,

    /**
     * Plugin classes are loaded into memory but not initialized.
     */
    LOADED,

    /**
     * Plugin is initialized and ready to start.
     */
    INITIALIZED,

    /**
     * Plugin is active and running.
     */
    STARTED,

    /**
     * Plugin is stopped but still in memory (can be restarted).
     */
    STOPPED,

    /**
     * Plugin is unloaded from memory.
     */
    UNLOADED,

    /**
     * Plugin is in an error state.
     */
    FAILED
}

