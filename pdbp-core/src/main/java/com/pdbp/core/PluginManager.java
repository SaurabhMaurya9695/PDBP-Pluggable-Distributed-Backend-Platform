package com.pdbp.core;

import com.pdbp.api.Plugin;
import com.pdbp.api.PluginContext;
import com.pdbp.api.PluginException;
import com.pdbp.api.PluginState;
import com.pdbp.api.PlatformService;
import com.pdbp.core.metrics.MetricsCollector;
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

    /**
     * Internal wrapper to track plugin state and metadata.
     */
    private static class PluginWrapper {

        private final Plugin plugin;
        private PluginState state;
        private final PluginContext context;

        PluginWrapper(Plugin plugin, PluginContext context) {
            this.plugin = plugin;
            this.context = context;
            this.state = PluginState.LOADED;
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
    }

    /**
     * Creates a new PluginManager.
     */
    public PluginManager() {
        this.plugins = new ConcurrentHashMap<>();
        this.classLoaders = new ConcurrentHashMap<>();
        logger.info("PluginManager initialized");
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

            logger.debug("Using JAR file: {}", jarFile.toAbsolutePath());

            // Use the classloader that loaded PluginManager (has access to pdbp-api)
            ClassLoader parentClassLoader = getParentClassLoader();
            logger.debug("Using parent ClassLoader: {} for plugin: {}", parentClassLoader.getClass().getName(),
                    pluginName);

            PluginClassLoader classLoader = new PluginClassLoader(pluginName, jarFile, parentClassLoader);

            try {
                // Load and validate plugin class
                Class<?> pluginClass = classLoader.loadClass(className);
                logger.debug("Loaded plugin class: {}", className);

                if (!Plugin.class.isAssignableFrom(pluginClass)) {
                    throw new PluginException("Class " + className + " does not implement Plugin interface");
                }

                // Instantiate and register plugin
                @SuppressWarnings("unchecked") Class<? extends Plugin> pluginType =
                        (Class<? extends Plugin>) pluginClass;
                Plugin plugin = pluginType.getDeclaredConstructor().newInstance();
                PluginContext context = createPluginContext(pluginName, plugin);

                plugins.put(pluginName, new PluginWrapper(plugin, context));
                classLoaders.put(pluginName, classLoader);

                long duration = System.currentTimeMillis() - startTime;
                MetricsCollector.getInstance().recordPluginInstalled(pluginName, duration);
                logger.info("Plugin installed successfully: {} v{} ({}ms)", plugin.getName(), plugin.getVersion(),
                        duration);
                return plugin;
            } catch (ClassNotFoundException e) {
                closeClassLoader(classLoader);
                throw new PluginException("Plugin class not found: " + className + " in JAR: " + jarFile, e);
            } catch (NoSuchMethodException e) {
                closeClassLoader(classLoader);
                throw new PluginException("Plugin class " + className + " must have a public no-arg constructor", e);
            } catch (PluginException e) {
                closeClassLoader(classLoader);
                throw e;
            } catch (Exception e) {
                closeClassLoader(classLoader);
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
        executeLifecycleOperation(pluginName, PluginState.LOADED, () -> {
            logger.info("Initializing plugin: {}", pluginName);
            wrapper.getPlugin().init(wrapper.getContext());
        }, PluginState.INITIALIZED, "initialize");
    }

    /**
     * Starts a plugin.
     *
     * @param pluginName the name of the plugin
     * @throws PluginException if startup fails
     */
    public void startPlugin(String pluginName) throws PluginException {
        PluginWrapper wrapper = getPluginWrapper(pluginName);
        if (wrapper.getState() != PluginState.INITIALIZED && wrapper.getState() != PluginState.STOPPED) {
            throw new PluginException("Plugin must be INITIALIZED or STOPPED, current: " + wrapper.getState());
        }

        executeLifecycleOperation(pluginName, null, () -> {
            logger.info("Starting plugin: {}", pluginName);
            wrapper.getPlugin().start();
        }, PluginState.STARTED, "start");
    }

    /**
     * Stops a plugin.
     *
     * @param pluginName the name of the plugin
     * @throws PluginException if shutdown fails
     */
    public void stopPlugin(String pluginName) throws PluginException {
        PluginWrapper wrapper = getPluginWrapper(pluginName);
        executeLifecycleOperation(pluginName, PluginState.STARTED, () -> {
            logger.info("Stopping plugin: {}", pluginName);
            wrapper.getPlugin().stop();
        }, PluginState.STOPPED, "stop");
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
     * @throws PluginException if operation fails
     */
    private void executeLifecycleOperation(String pluginName, PluginState requiredState, LifecycleOperation operation,
            PluginState targetState, String operationName) throws PluginException {
        PluginWrapper wrapper = getPluginWrapper(pluginName);

        if (requiredState != null && wrapper.getState() != requiredState) {
            throw new PluginException("Plugin must be in " + requiredState + " state, current: " + wrapper.getState());
        }

        try {
            operation.execute();
            wrapper.setState(targetState);
            logger.info("Plugin {}: {}", operationName + "ed", pluginName);
        } catch (PluginException e) {
            wrapper.setState(PluginState.FAILED);
            MetricsCollector.getInstance().recordPluginError(pluginName, operationName);
            throw e;
        } catch (Exception e) {
            wrapper.setState(PluginState.FAILED);
            MetricsCollector.getInstance().recordPluginError(pluginName, operationName);
            throw new PluginException("Failed to " + operationName + " plugin: " + pluginName, e);
        }
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
     * Simple implementation of PluginContext.
     */
    private static class SimplePluginContext implements PluginContext, PlatformService {

        private final String pluginName;
        private final Plugin plugin;
        private final org.slf4j.Logger logger;
        private final Map<String, String> config;
        private final PluginManager pluginManager;

        SimplePluginContext(String pluginName, Plugin plugin, PluginManager pluginManager) {
            this.pluginName = pluginName;
            this.plugin = plugin;
            this.pluginManager = pluginManager;
            this.logger = LoggerFactory.getLogger("plugin." + pluginName);
            this.config = new HashMap<>();
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
            return Optional.ofNullable(config.get(key));
        }

        @Override
        public Map<String, String> getConfig() {
            return new HashMap<>(config);
        }

        @Override
        public Optional<String> getSecret(String key) {
            // TODO: Implement secret management
            return Optional.empty();
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

