package com.pdbp.grpc.server;

import com.pdbp.api.Plugin;

/**
 * Registry interface for accessing plugins.
 * Allows gRPC service to look up plugins by name.
 *
 * @author Saurabh Maurya
 */
public interface PluginRegistry {
    /**
     * Gets a plugin by name.
     *
     * @param pluginName the plugin name
     * @return the plugin, or null if not found
     */
    Plugin getPlugin(String pluginName);
}

