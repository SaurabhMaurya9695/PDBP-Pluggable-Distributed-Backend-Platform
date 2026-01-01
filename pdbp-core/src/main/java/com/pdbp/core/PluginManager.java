package com.pdbp.core;

import com.pdbp.api.EventBus;
import com.pdbp.api.Plugin;
import com.pdbp.api.PluginContext;
import com.pdbp.api.PluginException;
import com.pdbp.api.PluginState;
import com.pdbp.api.PlatformService;
import com.pdbp.core.config.PluginConfigurationManager;
import com.pdbp.core.events.EventBusImpl;
import com.pdbp.core.healing.SelfHealingService;
import com.pdbp.core.metrics.MetricsCollector;
import com.pdbp.core.monitoring.PluginStateMonitor;
import com.pdbp.core.recovery.PluginRecoveryHandler;
import com.pdbp.core.util.PathResolver;
import com.pdbp.loader.PluginClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin Manager - Core component for managing plugin lifecycle.
 *
 * @author Saurabh Maurya
 */
public class PluginManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);

    // Thread-safe storage for plugins
    private final Map<String, PluginWrapper> plugins;

    // Thread-safe storage for ClassLoaders
    private final Map<String, PluginClassLoader> classLoaders;
    
    // Configuration manager
    private final PluginConfigurationManager configManager;
    
    // Event bus
    private final EventBus eventBus;

    private final SelfHealingService selfHealingService;
    private final PluginStateMonitor stateMonitor;
    private final PluginRecoveryHandler recoveryHandler;

    /**
     * Internal wrapper to track plugin state and metadata.
     */
    private static class PluginWrapper {

        private final Plugin plugin;
        private PluginState state;
        private PluginContext context;
        private PluginState desiredState; // Track what state the plugin should be in (for recovery)

        PluginWrapper(Plugin plugin, PluginContext context) {
            this.plugin = plugin;
            this.context = context;
            this.state = PluginState.LOADED;
            this.desiredState = null; // No desired state initially
        }

        Plugin getPlugin() {
            return plugin;
        }

        PluginState getState() {
            return state;
        }

        void setState(PluginState state) {
            this.state = state;
        }

        PluginContext getContext() {
            return context;
        }

        void setContext(PluginContext context) {
            this.context = context;
        }

        PluginState getDesiredState() {
            return desiredState;
        }

        void setDesiredState(PluginState desiredState) {
            this.desiredState = desiredState;
        }
    }

    /**
     * Creates a new PluginManager.
     */
    public PluginManager() {
        this.plugins = new ConcurrentHashMap<>();
        this.classLoaders = new ConcurrentHashMap<>();
        this.configManager = PluginConfigurationManager.getInstance(PathResolver.getWorkDirectory());
        // Set PluginManager reference in config manager for recovery operations
        this.configManager.setPluginManager(this);
        this.eventBus = EventBusImpl.getInstance();
        
        // Initialize self-healing service (automatic failure recovery)
        this.selfHealingService = new SelfHealingService(3, 5000, 60000); // 3 retries, 5s-60s backoff
        setupSelfHealingCallbacks();
        
        // Initialize recovery handler
        this.recoveryHandler = new PluginRecoveryHandler();
        
        // Initialize plugin state monitor
        this.stateMonitor = new PluginStateMonitor(this::getAllPluginStates);
        this.stateMonitor.start();
        
        logger.info("PluginManager initialized with self-healing and state monitoring enabled");
    }

    /**
     * Installs a plugin from a JAR file.
     * The method validates the JAR, creates a plugin classloader, loads and
     * instantiates the plugin, and registers it in the plugin manager.
     * After installation, the plugin is in the LOADED state.
     *
     * @param pluginName name to register the plugin under
     * @param jarPath    path to the plugin JAR file
     * @param className  fully qualified plugin implementation class name
     * @return the installed plugin instance
     * @throws PluginException if installation fails
     */
    public Plugin installPlugin(String pluginName, String jarPath, String className) throws PluginException {

        long startTime = System.currentTimeMillis();
        logger.info("Installing plugin: {} from {}", pluginName, jarPath);

        try {
            if (plugins.containsKey(pluginName)) {
                throw new PluginException("Plugin already installed: " + pluginName);
            }

            // Resolve JAR path (PluginServiceAdapter typically passes absolute paths)
            Path jarFile = Paths.get(jarPath);
            if (!jarFile.isAbsolute()) {
                // If relative, resolve using PathResolver utility
                jarFile = PathResolver.resolveJarPath(jarPath, null);
            }

            // Validate JAR file exists
            if (!Files.exists(jarFile)) {
                Path workDir = PathResolver.getWorkDirectory();
                Path expectedPath = workDir.resolve(Paths.get(jarPath).getFileName());
                logger.error("JAR file not found: {} (expected: {})", jarFile, expectedPath);
                throw new PluginException(
                        "JAR file not found: " + jarFile + ". Expected in work directory: " + expectedPath
                                + ". Please place plugin JAR files in the 'work' directory at project root.");
            }

            // Use the classloader that loaded PluginManager (has access to pdbp-api)
            ClassLoader parentClassLoader = getParentClassLoader();
            PluginClassLoader classLoader = new PluginClassLoader(pluginName, jarFile, parentClassLoader);

            try {
                // Load and validate plugin class
                Class<?> pluginClass = classLoader.loadClass(className);

                if (!Plugin.class.isAssignableFrom(pluginClass)) {
                    throw new PluginException("Class " + className + " does not implement Plugin interface");
                }

                // Instantiate and register plugin
                @SuppressWarnings("unchecked") Class<? extends Plugin> pluginType =
                        (Class<? extends Plugin>) pluginClass;
                Plugin plugin = pluginType.getDeclaredConstructor().newInstance();
                configManager.loadPluginConfig(pluginName);
                PluginContext context = createPluginContext(pluginName, plugin);

                plugins.put(pluginName, new PluginWrapper(plugin, context));
                classLoaders.put(pluginName, classLoader);

                // Register plugin for self-healing
                selfHealingService.registerPlugin(pluginName);
                long duration = System.currentTimeMillis() - startTime;
                MetricsCollector.getInstance().recordPluginInstalled(pluginName, duration);
                
                // Publish plugin installed event
                publishPluginLifecycleEvent("PluginInstalled", pluginName, plugin.getVersion());
                
                logger.info("Plugin '{}' v{} installed successfully ({}ms)", pluginName, plugin.getVersion(), duration);
                return plugin;
            } catch (ClassNotFoundException e) {
                closeClassLoader(classLoader);
                logger.error("Plugin class not found: {} in JAR: {}", className, jarFile);
                throw new PluginException("Plugin class not found: " + className + " in JAR: " + jarFile, e);
            } catch (NoSuchMethodException e) {
                closeClassLoader(classLoader);
                throw new PluginException("Plugin class " + className + " must have a public no-arg constructor", e);
            } catch (PluginException e) {
                closeClassLoader(classLoader);
                throw e;
            } catch (Exception e) {
                closeClassLoader(classLoader);
                logger.error("Failed to instantiate plugin: {}", pluginName, e);
                throw new PluginException("Failed to create plugin instance: " + className + " - " + e.getMessage(), e);
            }
        } catch (PluginException e) {
            throw e; // Re-throw PluginException as-is
        } catch (IOException e) {
            logger.error("Failed to create ClassLoader for plugin: {}", pluginName, e);
            throw new PluginException("Failed to create ClassLoader: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error installing plugin: {}", pluginName, e);
            throw new PluginException("Failed to install plugin: " + pluginName + " - " + e.getMessage(), e);
        }
    }

    /**
     * Initializes a plugin.
     *
     * @param pluginName the name of the plugin
     * @throws PluginException if initialization fails
     */
    public void initPlugin(String pluginName) throws PluginException {
        PluginWrapper wrapper = getPluginWrapper(pluginName);
        logger.info("Initializing plugin: {}", pluginName);
        
        executeLifecycleOperation(pluginName, PluginState.LOADED, () -> {
            wrapper.getPlugin().init(wrapper.getContext());
            // Register config change listener to restart plugin on config changes
            configManager.addConfigChangeListener(pluginName, this::handleConfigChange);
        }, PluginState.INITIALIZED, "initialize");
    }
    
    /**
     * Handles configuration change for a plugin by restarting it.
     *
     * @param pluginName the plugin name
     * @param newConfig  the new configuration (already loaded in config manager)
     */
    private void handleConfigChange(String pluginName, Map<String, String> newConfig) {
        PluginWrapper wrapper = plugins.get(pluginName);
        if (wrapper == null) {
            return;
        }

        PluginState currentState = wrapper.getState();
        if (currentState == PluginState.STARTED) {
            logger.info("Configuration changed for plugin: {}, restarting...", pluginName);
            restartPluginForConfigChange(pluginName, wrapper);
        } 


        else if (currentState == PluginState.FAILED) {
            logger.info("Configuration changed for failed plugin: {}, attempting recovery...", pluginName);
            attemptPluginRecovery(pluginName);
        } 
        // Plugin is not started, config is already updated and will be used on next start
        else {
            logger.debug("Plugin {} is not running, configuration updated (will be applied on next start)", pluginName);
        }
    }

    /**
     * Restarts a plugin for config change.
     * Stops, re-initializes, and starts the plugin with new configuration.
     */
    private void restartPluginForConfigChange(String pluginName, PluginWrapper wrapper) {
        try {
            stopPluginForConfigReload(pluginName, wrapper);
            reinitializePluginWithNewConfig(pluginName, wrapper);
            startPluginAfterConfigReload(pluginName, wrapper);
        } catch (Exception e) {
            logger.error("Error handling config change for plugin: {}", pluginName, e);
            markPluginAsFailed(pluginName, wrapper, PluginState.STARTED);
            selfHealingService.handlePluginFailure(pluginName, null);
        }
    }

    /**
     * Stops plugin for config reload.
     */
    private void stopPluginForConfigReload(String pluginName, PluginWrapper wrapper) throws Exception {
        try {
            wrapper.getPlugin().stop();
            wrapper.setState(PluginState.STOPPED);
            logger.info("Plugin {} stopped for config reload", pluginName);
        } catch (Exception e) {
            logger.error("Failed to stop plugin {} for config reload: {}", pluginName, e.getMessage());
            markPluginAsFailed(pluginName, wrapper, PluginState.STARTED);
            throw e;
        }
    }

    /**
     * Re-initializes plugin with new configuration.
     */
    private void reinitializePluginWithNewConfig(String pluginName, PluginWrapper wrapper) throws Exception {
        try {
            PluginContext newContext = createPluginContext(pluginName, wrapper.getPlugin());
            wrapper.setContext(newContext);
            wrapper.getPlugin().init(newContext);
            wrapper.setState(PluginState.INITIALIZED);
            logger.info("Plugin {} re-initialized with new config", pluginName);
        } catch (Exception e) {
            logger.error("Failed to re-initialize plugin {} after config change: {}", pluginName, e.getMessage());
            markPluginAsFailed(pluginName, wrapper, PluginState.STARTED);
            throw e;
        }
    }

    /**
     * Starts plugin after config reload.
     */
    private void startPluginAfterConfigReload(String pluginName, PluginWrapper wrapper) throws Exception {
        try {
            wrapper.getPlugin().start();
            wrapper.setState(PluginState.STARTED);
            wrapper.setDesiredState(null);
            logger.info("Plugin {} restarted with new configuration", pluginName);
        } catch (Exception e) {
            logger.error("Failed to restart plugin {} after config change: {}", pluginName, e.getMessage());
            markPluginAsFailed(pluginName, wrapper, PluginState.STARTED);
            throw e;
        }
    }

    /**
     * Marks plugin as failed and sets desired state.
     */
    private void markPluginAsFailed(String pluginName, PluginWrapper wrapper, PluginState desiredState) {
        wrapper.setState(PluginState.FAILED);
        wrapper.setDesiredState(desiredState);
    }

    /**
     * Starts a plugin.
     *
     * @param pluginName the name of the plugin
     * @throws PluginException if startup fails
     */
    public void startPlugin(String pluginName) throws PluginException {
        long startTime = System.currentTimeMillis();
        PluginWrapper wrapper = getPluginWrapper(pluginName);
        if (wrapper.getState() != PluginState.INITIALIZED && wrapper.getState() != PluginState.STOPPED) {
            throw new PluginException("Plugin must be INITIALIZED or STOPPED, current: " + wrapper.getState());
        }

        logger.info("Starting plugin: {}", pluginName);
        
        executeLifecycleOperation(pluginName, null, () -> {
            wrapper.getPlugin().start();
            
            // Record success in self-healing (resets failure count)
            selfHealingService.recordSuccess(pluginName);
            
            // Publish plugin started event
            publishPluginLifecycleEvent("PluginStarted", pluginName, wrapper.getPlugin().getVersion());
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Plugin {} started successfully ({}ms)", pluginName, duration);
        }, PluginState.STARTED, "start", startTime);
    }

    /**
     * Stops a plugin.
     *
     * @param pluginName the name of the plugin
     * @throws PluginException if shutdown fails
     */
    public void stopPlugin(String pluginName) throws PluginException {
        long startTime = System.currentTimeMillis();
        PluginWrapper wrapper = getPluginWrapper(pluginName);
        logger.info("Stopping plugin: {}", pluginName);
        executeLifecycleOperation(pluginName, PluginState.STARTED, () -> {
            wrapper.getPlugin().stop();
            // Publish plugin stopped event
            publishPluginLifecycleEvent("PluginStopped", pluginName, wrapper.getPlugin().getVersion());
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Plugin {} stopped successfully ({}ms)", pluginName, duration);
        }, PluginState.STOPPED, "stop", startTime);
    }

    /**
     * Functional interface for lifecycle operations that may throw PluginException.
     */
    @FunctionalInterface
    private interface LifecycleOperation {

        void execute() throws PluginException;
    }

    /**
     * Executes a plugin lifecycle operation with state validation and error handling.
     *
     * @param pluginName    the plugin name
     * @param requiredState the required state (null to skip validation)
     * @param operation     the operation to execute
     * @param targetState   the target state after successful operation
     * @param operationName the operation name for logging
     * @param startTime     optional start time for duration logging (-1 to skip)
     * @throws PluginException if operation fails
     */
    private void executeLifecycleOperation(String pluginName, PluginState requiredState, LifecycleOperation operation,
            PluginState targetState, String operationName, long startTime) throws PluginException {
        PluginWrapper wrapper = getPluginWrapper(pluginName);
        PluginState currentState = wrapper.getState();

        if (requiredState != null && currentState != requiredState) {
            throw new PluginException("Plugin must be in " + requiredState + " state, current: " + currentState);
        }

        try {
            operation.execute();
            wrapper.setState(targetState);
            // Clear desired state on success (plugin reached its target)
            if (targetState == wrapper.getDesiredState()) {
                wrapper.setDesiredState(null);
            }
        } catch (PluginException e) {
            wrapper.setState(PluginState.FAILED);
            // Set desired state to what we were trying to achieve
            wrapper.setDesiredState(targetState);
            MetricsCollector.getInstance().recordPluginError(pluginName, operationName);
            logger.error("Failed to {} plugin {}: {}", operationName, pluginName, e.getMessage());
            
            // Notify self-healing service (will attempt automatic recovery)
            selfHealingService.handlePluginFailure(pluginName, e);
            
            throw e;
        } catch (Exception e) {
            wrapper.setState(PluginState.FAILED);
            // Set desired state to what we were trying to achieve
            wrapper.setDesiredState(targetState);
            MetricsCollector.getInstance().recordPluginError(pluginName, operationName);
            logger.error("Failed to {} plugin {}: {}", operationName, pluginName, e.getMessage(), e);
            
            // Notify self-healing service (will attempt automatic recovery)
            selfHealingService.handlePluginFailure(pluginName, e);
            
            throw new PluginException("Failed to " + operationName + " plugin: " + pluginName, e);
        }
    }

    /**
     * Overload without startTime for operations not directly tied to duration metrics.
     */
    private void executeLifecycleOperation(String pluginName, PluginState requiredState, LifecycleOperation operation,
            PluginState targetState, String operationName) throws PluginException {
        executeLifecycleOperation(pluginName, requiredState, operation, targetState, operationName, -1);
    }

    /**
     * Unloads a plugin.
     *
     * @param pluginName the name of the plugin
     * @throws PluginException if unloading fails
     */
    public void unloadPlugin(String pluginName) throws PluginException {
        PluginWrapper wrapper = plugins.remove(pluginName);
        if (wrapper == null) {
            throw new PluginException("Plugin not found: " + pluginName);
        }

        try {
            // Stop if running
            if (wrapper.getState() == PluginState.STARTED) {
                try {
                    wrapper.getPlugin().stop();
                } catch (Exception e) {
                    logger.warn("Error stopping plugin during unload: {}", pluginName, e);
                }
            }

            // Destroy plugin
            wrapper.getPlugin().destroy();

            // Close ClassLoader
            PluginClassLoader classLoader = classLoaders.remove(pluginName);
            if (classLoader != null) {
                classLoader.close();
            }

            // Remove configuration
            configManager.removePluginConfig(pluginName);
            selfHealingService.unregisterPlugin(pluginName);
            wrapper.setState(PluginState.UNLOADED);
            MetricsCollector.getInstance().recordPluginUnloaded(pluginName);
            logger.info("Plugin unloaded: {}", pluginName);
        } catch (Exception e) {
            throw new PluginException("Failed to unload plugin: " + pluginName, e);
        }
    }

    /**
     * Gets a plugin by name.
     *
     * @param pluginName the name of the plugin
     * @return the plugin, or null if not found
     */
    public Plugin getPlugin(String pluginName) {
        PluginWrapper wrapper = plugins.get(pluginName);
        return wrapper != null ? wrapper.getPlugin() : null;
    }

    /**
     * Gets the state of a plugin.
     *
     * @param pluginName the name of the plugin
     * @return the plugin state, or null if not found
     */
    public PluginState getPluginState(String pluginName) {
        PluginWrapper wrapper = plugins.get(pluginName);
        return wrapper != null ? wrapper.getState() : null;
    }

    /**
     * Lists all installed plugins.
     *
     * @return set of plugin names
     */
    public Set<String> listPlugins() {
        return new HashSet<>(plugins.keySet());
    }

    /**
     * Gets the parent ClassLoader for plugin ClassLoaders.
     * Uses the ClassLoader that loaded PluginManager to ensure access to pdbp-api.
     *
     * @return parent ClassLoader
     */
    private ClassLoader getParentClassLoader() {
        ClassLoader classLoader = PluginManager.class.getClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    /**
     * Safely closes a ClassLoader, ignoring any exceptions.
     *
     * @param classLoader the ClassLoader to close
     */
    private void closeClassLoader(PluginClassLoader classLoader) {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (Exception ignored) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Gets plugin wrapper (internal method).
     */
    private PluginWrapper getPluginWrapper(String pluginName) throws PluginException {
        PluginWrapper wrapper = plugins.get(pluginName);
        if (wrapper == null) {
            Set<String> availablePlugins = plugins.keySet();
            String message = "Plugin not found: " + pluginName;
            if (availablePlugins.isEmpty()) {
                message +=
                        ". No plugins are currently installed. Please install a plugin first using POST "
                                + "/api/plugins/install";
            } else {
                message += ". Available plugins: " + String.join(", ", availablePlugins)
                        + ". Please install the plugin first using POST /api/plugins/install";
            }
            throw new PluginException(message);
        }
        return wrapper;
    }

    /**
     * Creates a plugin context (factory method).
     */
    private PluginContext createPluginContext(String pluginName, Plugin plugin) {
        return new SimplePluginContext(pluginName, plugin, this);
    }

    /**
     * Gets the configuration manager.
     *
     * @return configuration manager instance
     */
    public PluginConfigurationManager getConfigManager() {
        return configManager;
    }

    /**
     * Attempts to recover a failed plugin to its desired state.
     * Called by config watcher or config change handler when plugin is in FAILED state.
     *
     * @param pluginName the plugin name
     * @return true if recovery succeeded, false otherwise
     */
    public boolean attemptPluginRecovery(String pluginName) {
        PluginWrapper wrapper = plugins.get(pluginName);
        if (wrapper == null || wrapper.getState() != PluginState.FAILED) {
            return false;
        }

        PluginState desiredState = wrapper.getDesiredState();
        if (desiredState == null) {
            desiredState = PluginState.STARTED; // Default desired state
            wrapper.setDesiredState(desiredState);
        }

        logger.info("Attempting to recover plugin {} to desired state: {}", pluginName, desiredState);

        PluginRecoveryHandler.RecoveryContext context = new PluginRecoveryHandler.RecoveryContext(
                wrapper.getPlugin(),
                wrapper.getContext(),
                wrapper.getState(),
                desiredState
        );

        boolean recovered = recoveryHandler.recover(context, plugin -> createPluginContext(pluginName, plugin));

        if (recovered) {
            wrapper.setState(context.getCurrentState());
            wrapper.setDesiredState(context.getDesiredState());
            logger.info("Plugin {} recovered successfully", pluginName);
        } else {
            logger.warn("Recovery attempt failed for plugin {}", pluginName);
        }

        return recovered;
    }

    /**
     * Gets the event bus.
     *
     * @return event bus instance
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Sets up self-healing callbacks.
     */
    private void setupSelfHealingCallbacks() {
        // Restart plugin callback
        selfHealingService.setRestartPluginCallback(pluginName -> {
            try {
                PluginWrapper wrapper = plugins.get(pluginName);
                if (wrapper == null || wrapper.getState() != PluginState.FAILED) {
                    return;
                }

                logger.info("Self-healing: Attempting to recover plugin: {}", pluginName);

                // Stop if needed
                if (wrapper.getState() == PluginState.STARTED) {
                    try {
                        wrapper.getPlugin().stop();
                    } catch (Exception e) {
                        logger.warn("Error stopping plugin {} during recovery: {}", pluginName, e.getMessage());
                    }
                }

                // Re-initialize
                try {
                    wrapper.getPlugin().init(wrapper.getContext());
                    wrapper.setState(PluginState.INITIALIZED);
                } catch (Exception e) {
                    logger.error("Error re-initializing plugin {} during recovery: {}", pluginName, e.getMessage());
                    wrapper.setState(PluginState.FAILED);
                    throw e;
                }

                // Start
                try {
                    wrapper.getPlugin().start();
                    wrapper.setState(PluginState.STARTED);

                    // Record success (resets failure count)
                    selfHealingService.recordSuccess(pluginName);

                    logger.info("Self-healing: Successfully recovered plugin: {}", pluginName);
                } catch (Exception e) {
                    logger.error("Error starting plugin {} during recovery: {}", pluginName, e.getMessage());
                    throw e;
                }
            } catch (Exception e) {
                logger.error("Self-healing: Failed to recover plugin {}: {}", pluginName, e.getMessage());
                selfHealingService.handlePluginFailure(pluginName, e);
            }
        });

        // Alert callback
        selfHealingService.setAlertCallback(pluginName -> {
            logger.error("Self-healing: ALERT - Plugin {} requires manual intervention (max retries exceeded)",
                    pluginName);
        });
    }

    /**
     * Gets all plugin states for monitoring.
     */
    private Map<String, PluginStateMonitor.PluginStateInfo> getAllPluginStates() {
        Map<String, PluginStateMonitor.PluginStateInfo> states = new HashMap<>();
        plugins.forEach((name, wrapper) -> {
            PluginStateMonitor.PluginStateInfo info = new PluginStateMonitor.PluginStateInfo(
                    wrapper.getState(),
                    wrapper.getDesiredState()
            );
            states.put(name, info);
        });
        return states;
    }

    /**
     * Shuts down the plugin manager and all services.
     */
    public void shutdown() {
        stateMonitor.shutdown();
        selfHealingService.shutdown();
        logger.info("PluginManager shut down");
    }

    /**
     * Publishes a plugin lifecycle event.
     *
     * @param eventType   the event type (e.g., "PluginInstalled", "PluginStarted")
     * @param pluginName  the plugin name
     * @param pluginVersion the plugin version
     */
    private void publishPluginLifecycleEvent(String eventType, String pluginName, String pluginVersion) {
        try {
            com.pdbp.core.events.SimpleEvent event = new com.pdbp.core.events.SimpleEvent.Builder()
                    .type(eventType)
                    .source("PluginManager")
                    .payload("pluginName", pluginName)
                    .payload("pluginVersion", pluginVersion)
                    .payload("timestamp", System.currentTimeMillis())
                    .build();
            eventBus.publish(event);
            logger.debug("Published lifecycle event: {} for plugin: {}", eventType, pluginName);
        } catch (Exception e) {
            logger.warn("Failed to publish lifecycle event: {} for plugin: {}", eventType, pluginName, e);
        }
    }

    /**
     * Simple implementation of PluginContext.
     */
    private class SimplePluginContext implements PluginContext, PlatformService {

        private final String pluginName;
        private final Plugin plugin;
        private final org.slf4j.Logger logger;

        SimplePluginContext(String pluginName, Plugin plugin, PluginManager pluginManager) {
            this.pluginName = pluginName;
            this.plugin = plugin;
            this.logger = LoggerFactory.getLogger("plugin." + pluginName);
        }

        @Override
        public String getPluginName() {
            return pluginName;
        }

        @Override
        public String getPluginVersion() {
            return plugin.getVersion();
        }

        @Override
        public Optional<String> getConfig(String key) {
            return configManager.getPluginConfig(pluginName, key);
        }

        @Override
        public Map<String, String> getConfig() {
            return configManager.getPluginConfig(pluginName);
        }

        @Override
        public Optional<String> getSecret(String key) {
            return configManager.getPluginSecret(pluginName, key);
        }

        @Override
        public org.slf4j.Logger getLogger() {
            return logger;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getService(Class<T> serviceType) {
            if (serviceType == PlatformService.class) {
                return Optional.of((T) this);
            }
            if (serviceType == EventBus.class) {
                return Optional.of((T) eventBus);
            }
            if (serviceType.getName().equals("com.pdbp.core.metrics.MetricsCollector")) {
                try {
                    return Optional.of((T) MetricsCollector.getInstance());
                } catch (Exception e) {
                    logger.warn("Failed to get MetricsCollector service", e);
                }
            }
            return Optional.empty();
        }
    }
}

