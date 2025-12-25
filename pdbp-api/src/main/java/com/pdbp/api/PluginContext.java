package com.pdbp.api;

import java.util.Map;
import java.util.Optional;

/**
 * Context provided to plugins during initialization.
 * <p>
 * Exposes plugin metadata, configuration, secrets, and platform services.
 * Acts as a read-only access point for injected dependencies.
 *
 * @author Saurabh Maurya
 */
public interface PluginContext {

    /**
     * @return plugin identifier
     */
    String getPluginName();

    /**
     * @return plugin version
     */
    String getPluginVersion();

    /**
     * Returns a configuration value.
     *
     * @param key configuration key
     * @return value if present, otherwise empty
     */
    Optional<String> getConfig(String key);

    /**
     * Returns a configuration value or a default.
     *
     * @param key          configuration key
     * @param defaultValue fallback value
     * @return configuration value or default
     */
    default String getConfig(String key, String defaultValue) {
        return getConfig(key).orElse(defaultValue);
    }

    /**
     * @return all configuration key-value pairs
     */
    Map<String, String> getConfig();

    /**
     * Returns a secret value.
     *
     * @param key secret key
     * @return secret value if present, otherwise empty
     */
    Optional<String> getSecret(String key);

    /**
     * @return plugin-scoped logger
     */
    org.slf4j.Logger getLogger();
}
