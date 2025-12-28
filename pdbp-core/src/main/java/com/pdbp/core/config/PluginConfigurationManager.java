package com.pdbp.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdbp.api.PlatformService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages plugin configurations with support for:
 * <ul>
 *   <li>File-based configuration storage (JSON)</li>
 *   <li>Hot reload on file changes</li>
 *   <li>Config validation</li>
 *   <li>Secrets management</li>
 * </ul>
 *
 * <p>Design: Singleton pattern for centralized config management.
 * Thread-safe operations using ConcurrentHashMap.
 *
 * @author Saurabh Maurya
 */
public class PluginConfigurationManager implements PlatformService {

    private static final Logger logger = LoggerFactory.getLogger(PluginConfigurationManager.class);
    private static final String CONFIG_DIR = "config";
    private static final String SECRETS_DIR = "secrets";
    private static final String CONFIG_FILE_EXT = ".json";

    private static PluginConfigurationManager instance;

    private final Path configDirectory;
    private final Path secretsDirectory;
    private final ObjectMapper objectMapper;
    
    // Plugin name -> Configuration map
    private final Map<String, Map<String, String>> pluginConfigs;
    
    // Plugin name -> Secrets map
    private final Map<String, Map<String, String>> pluginSecrets;
    
    // File watchers for hot reload
    private final Map<String, WatchService> watchServices;
    private final ScheduledExecutorService watcherExecutor;
    
    // Config change listeners (plugin name -> listeners)
    private final Map<String, List<ConfigChangeListener>> configChangeListeners;

    private PluginConfigurationManager(Path baseDirectory) {
        this.configDirectory = baseDirectory.resolve(CONFIG_DIR);
        this.secretsDirectory = baseDirectory.resolve(SECRETS_DIR);
        this.objectMapper = new ObjectMapper();
        this.pluginConfigs = new ConcurrentHashMap<>();
        this.pluginSecrets = new ConcurrentHashMap<>();
        this.watchServices = new ConcurrentHashMap<>();
        this.configChangeListeners = new ConcurrentHashMap<>();
        this.watcherExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "config-watcher");
            t.setDaemon(true);
            return t;
        });
        
        initializeDirectories();
        startFileWatchers();
        logger.info("PluginConfigurationManager initialized. Config dir: {}, Secrets dir: {}", 
                configDirectory, secretsDirectory);
    }

    /**
     * Gets the singleton instance.
     */
    public static synchronized PluginConfigurationManager getInstance() {
        if (instance == null) {
            Path baseDir = Paths.get(System.getProperty("user.dir")).resolve("work");
            instance = new PluginConfigurationManager(baseDir);
        }
        return instance;
    }

    /**
     * Gets the singleton instance with custom base directory.
     */
    public static synchronized PluginConfigurationManager getInstance(Path baseDirectory) {
        if (instance == null) {
            instance = new PluginConfigurationManager(baseDirectory);
        }
        return instance;
    }

    /**
     * Initializes configuration and secrets directories.
     */
    private void initializeDirectories() {
        try {
            Files.createDirectories(configDirectory);
            Files.createDirectories(secretsDirectory);
            logger.debug("Created config directories: {} and {}", configDirectory, secretsDirectory);
        } catch (IOException e) {
            logger.error("Failed to create config directories", e);
            throw new RuntimeException("Failed to initialize configuration directories", e);
        }
    }

    /**
     * Loads configuration for a plugin from file.
     * If file doesn't exist, creates an empty config.
     *
     * @param pluginName the plugin name
     * @return configuration map
     */
    public Map<String, String> loadPluginConfig(String pluginName) {
        Path configFile = configDirectory.resolve(pluginName + CONFIG_FILE_EXT);
        
        if (!Files.exists(configFile)) {
            logger.debug("Config file not found for plugin: {}. Creating empty config.", pluginName);
            Map<String, String> emptyConfig = new HashMap<>();
            pluginConfigs.put(pluginName, emptyConfig);
            return emptyConfig;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(configFile.toFile());
            Map<String, String> config = new HashMap<>();
            
            jsonNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                config.put(key, value.isTextual() ? value.asText() : value.toString());
            });
            
            pluginConfigs.put(pluginName, config);
            logger.info("Loaded configuration for plugin: {} ({} keys)", pluginName, config.size());
            return config;
        } catch (IOException e) {
            logger.error("Failed to load config for plugin: {}", pluginName, e);
            Map<String, String> emptyConfig = new HashMap<>();
            pluginConfigs.put(pluginName, emptyConfig);
            return emptyConfig;
        }
    }

    /**
     * Saves configuration for a plugin to file.
     *
     * @param pluginName the plugin name
     * @param config     configuration map
     * @throws IOException if save fails
     */
    public void savePluginConfig(String pluginName, Map<String, String> config) throws IOException {
        Path configFile = configDirectory.resolve(pluginName + CONFIG_FILE_EXT);
        
        // Convert to JSON
        Map<String, Object> jsonMap = new HashMap<>(config);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), jsonMap);
        
        // Update in-memory cache
        pluginConfigs.put(pluginName, new HashMap<>(config));
        
        logger.info("Saved configuration for plugin: {} ({} keys)", pluginName, config.size());
        
        // Notify listeners
        notifyConfigChange(pluginName, config);
    }

    /**
     * Updates a single configuration value for a plugin.
     *
     * @param pluginName the plugin name
     * @param key        configuration key
     * @param value      configuration value
     * @throws IOException if save fails
     */
    public void updatePluginConfig(String pluginName, String key, String value) throws IOException {
        Map<String, String> config = pluginConfigs.computeIfAbsent(pluginName, k -> loadPluginConfig(pluginName));
        config.put(key, value);
        savePluginConfig(pluginName, config);
    }

    /**
     * Gets configuration value for a plugin.
     *
     * @param pluginName the plugin name
     * @param key        configuration key
     * @return configuration value or empty
     */
    public Optional<String> getPluginConfig(String pluginName, String key) {
        Map<String, String> config = pluginConfigs.computeIfAbsent(pluginName, k -> loadPluginConfig(pluginName));
        return Optional.ofNullable(config.get(key));
    }

    /**
     * Gets all configuration for a plugin.
     *
     * @param pluginName the plugin name
     * @return configuration map (read-only copy)
     */
    public Map<String, String> getPluginConfig(String pluginName) {
        Map<String, String> config = pluginConfigs.computeIfAbsent(pluginName, k -> loadPluginConfig(pluginName));
        return Collections.unmodifiableMap(new HashMap<>(config));
    }

    /**
     * Loads secrets for a plugin from file.
     * Secrets are stored separately from regular config for security.
     *
     * @param pluginName the plugin name
     * @return secrets map
     */
    public Map<String, String> loadPluginSecrets(String pluginName) {
        Path secretsFile = secretsDirectory.resolve(pluginName + CONFIG_FILE_EXT);
        
        if (!Files.exists(secretsFile)) {
            logger.debug("Secrets file not found for plugin: {}. Creating empty secrets.", pluginName);
            Map<String, String> emptySecrets = new HashMap<>();
            pluginSecrets.put(pluginName, emptySecrets);
            return emptySecrets;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(secretsFile.toFile());
            Map<String, String> secrets = new HashMap<>();
            
            jsonNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                secrets.put(key, value.isTextual() ? value.asText() : value.toString());
            });
            
            pluginSecrets.put(pluginName, secrets);
            logger.info("Loaded secrets for plugin: {} ({} keys)", pluginName, secrets.size());
            return secrets;
        } catch (IOException e) {
            logger.error("Failed to load secrets for plugin: {}", pluginName, e);
            Map<String, String> emptySecrets = new HashMap<>();
            pluginSecrets.put(pluginName, emptySecrets);
            return emptySecrets;
        }
    }

    /**
     * Gets a secret value for a plugin.
     *
     * @param pluginName the plugin name
     * @param key        secret key
     * @return secret value or empty
     */
    public Optional<String> getPluginSecret(String pluginName, String key) {
        Map<String, String> secrets = pluginSecrets.computeIfAbsent(pluginName, k -> loadPluginSecrets(pluginName));
        return Optional.ofNullable(secrets.get(key));
    }

    /**
     * Saves secrets for a plugin.
     * Note: In production, this should encrypt secrets at rest.
     *
     * @param pluginName the plugin name
     * @param secrets    secrets map
     * @throws IOException if save fails
     */
    public void savePluginSecrets(String pluginName, Map<String, String> secrets) throws IOException {
        Path secretsFile = secretsDirectory.resolve(pluginName + CONFIG_FILE_EXT);
        
        // Convert to JSON
        Map<String, Object> jsonMap = new HashMap<>(secrets);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(secretsFile.toFile(), jsonMap);
        
        // Update in-memory cache
        pluginSecrets.put(pluginName, new HashMap<>(secrets));
        
        logger.info("Saved secrets for plugin: {} ({} keys)", pluginName, secrets.size());
        
        // Set restrictive permissions (Unix only)
        try {
            Files.setPosixFilePermissions(secretsFile, 
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support PosixFilePermissions
            logger.debug("Cannot set file permissions on Windows");
        }
    }

    /**
     * Registers a listener for configuration changes.
     *
     * @param pluginName the plugin name
     * @param listener   the change listener
     */
    public void addConfigChangeListener(String pluginName, ConfigChangeListener listener) {
        configChangeListeners.computeIfAbsent(pluginName, k -> new ArrayList<>()).add(listener);
        logger.debug("Registered config change listener for plugin: {}", pluginName);
    }

    /**
     * Removes a configuration change listener.
     *
     * @param pluginName the plugin name
     * @param listener   the listener to remove
     */
    public void removeConfigChangeListener(String pluginName, ConfigChangeListener listener) {
        List<ConfigChangeListener> listeners = configChangeListeners.get(pluginName);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Starts file watchers for hot reload.
     */
    private void startFileWatchers() {
        watcherExecutor.scheduleWithFixedDelay(this::watchConfigFiles, 2, 2, TimeUnit.SECONDS);
        logger.debug("Started file watchers for hot reload");
    }

    /**
     * Watches configuration files for changes and reloads them.
     */
    private void watchConfigFiles() {
        try {
            if (!Files.exists(configDirectory)) {
                return;
            }

            // Check all config files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDirectory, "*.json")) {
                for (Path configFile : stream) {
                    String pluginName = configFile.getFileName().toString().replace(CONFIG_FILE_EXT, "");
                    long lastModified = Files.getLastModifiedTime(configFile).toMillis();
                    
                    // Check if file was modified (simple approach - in production use WatchService)
                    Map<String, String> currentConfig = pluginConfigs.get(pluginName);
                    if (currentConfig != null) {
                        // Reload if file was modified recently (within last 2 seconds)
                        long now = System.currentTimeMillis();
                        if (lastModified > now - 3000) {
                            logger.info("Detected config file change for plugin: {}. Reloading...", pluginName);
                            loadPluginConfig(pluginName);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Error watching config files", e);
        }
    }

    /**
     * Notifies all listeners of a configuration change.
     */
    private void notifyConfigChange(String pluginName, Map<String, String> newConfig) {
        List<ConfigChangeListener> listeners = configChangeListeners.get(pluginName);
        if (listeners != null) {
            for (ConfigChangeListener listener : new ArrayList<>(listeners)) {
                try {
                    listener.onConfigChanged(pluginName, newConfig);
                } catch (Exception e) {
                    logger.error("Error notifying config change listener for plugin: {}", pluginName, e);
                }
            }
        }
    }

    /**
     * Validates configuration against a schema.
     * Basic validation - can be extended with JSON Schema.
     *
     * @param pluginName the plugin name
     * @param config     configuration to validate
     * @param schema     validation schema (key -> required/optional)
     * @return validation result
     */
    public ConfigValidationResult validateConfig(String pluginName, Map<String, String> config, 
                                                   Map<String, Boolean> schema) {
        ConfigValidationResult result = new ConfigValidationResult();
        
        for (Map.Entry<String, Boolean> schemaEntry : schema.entrySet()) {
            String key = schemaEntry.getKey();
            boolean required = schemaEntry.getValue();
            
            if (required && !config.containsKey(key)) {
                result.addError(key, "Required configuration key missing: " + key);
            }
        }
        
        if (result.isValid()) {
            logger.debug("Configuration validation passed for plugin: {}", pluginName);
        } else {
            logger.warn("Configuration validation failed for plugin: {}. Errors: {}", 
                    pluginName, result.getErrors());
        }
        
        return result;
    }

    /**
     * Reloads configuration for a plugin from file.
     *
     * @param pluginName the plugin name
     */
    public void reloadPluginConfig(String pluginName) {
        logger.info("Reloading configuration for plugin: {}", pluginName);
        Map<String, String> config = loadPluginConfig(pluginName);
        notifyConfigChange(pluginName, config);
    }

    /**
     * Removes configuration for a plugin (when plugin is unloaded).
     *
     * @param pluginName the plugin name
     */
    public void removePluginConfig(String pluginName) {
        pluginConfigs.remove(pluginName);
        pluginSecrets.remove(pluginName);
        configChangeListeners.remove(pluginName);
        logger.info("Removed configuration for plugin: {}", pluginName);
    }

    /**
     * Shuts down the configuration manager.
     */
    public void shutdown() {
        watcherExecutor.shutdown();
        try {
            if (!watcherExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                watcherExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            watcherExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("PluginConfigurationManager shut down");
    }

    /**
     * Listener interface for configuration changes.
     */
    @FunctionalInterface
    public interface ConfigChangeListener {
        void onConfigChanged(String pluginName, Map<String, String> newConfig);
    }

    /**
     * Configuration validation result.
     */
    public static class ConfigValidationResult {
        private final List<String> errors = new ArrayList<>();

        public void addError(String key, String message) {
            errors.add(key + ": " + message);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }
    }
}

