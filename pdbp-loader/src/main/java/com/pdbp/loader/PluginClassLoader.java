package com.pdbp.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Custom ClassLoader for loading plugin classes with isolation support.
 *
 * @author Saurabh Maurya
 */
public class PluginClassLoader extends URLClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(PluginClassLoader.class);

    private final String pluginName;
    private final Path pluginJarPath;
    private final LoadingStrategy loadingStrategy;

    /**
     * Loading strategy for class resolution.
     */
    public enum LoadingStrategy {
        /**
         * Parent-first: Try parent ClassLoader first, then this ClassLoader.
         * Use when plugins should use platform versions of classes.
         */
        PARENT_FIRST,

        /**
         * Child-first: Try this ClassLoader first, then parent.
         * Use when plugins need their own versions of classes.
         */
        CHILD_FIRST
    }

    /**
     * Creates a new PluginClassLoader with specified strategy.
     *
     * @param pluginName      the name of the plugin
     * @param pluginJarPath   path to the plugin JAR file
     * @param parent          the parent ClassLoader
     * @param loadingStrategy the loading strategy to use
     * @throws IOException if the JAR file cannot be accessed
     */
    public PluginClassLoader(String pluginName, Path pluginJarPath, ClassLoader parent, LoadingStrategy loadingStrategy)
            throws IOException {
        super(new URL[] { pluginJarPath.toUri().toURL() }, parent);
        this.pluginName = pluginName;
        this.pluginJarPath = pluginJarPath;
        this.loadingStrategy = loadingStrategy;
        logger.debug("Created PluginClassLoader for plugin: {} from: {}", pluginName, pluginJarPath);
    }

    /**
     * Creates a PluginClassLoader with parent-first strategy (default).
     *
     * @param pluginName    the name of the plugin
     * @param pluginJarPath path to the plugin JAR file
     * @param parent        the parent ClassLoader
     * @throws IOException if the JAR file cannot be accessed
     */
    public PluginClassLoader(String pluginName, Path pluginJarPath, ClassLoader parent) throws IOException {
        this(pluginName, pluginJarPath, parent, LoadingStrategy.PARENT_FIRST);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check if class is already loaded (cached)
        Class<?> cachedClass = findLoadedClass(name);
        if (cachedClass != null) {
            if (resolve) {
                resolveClass(cachedClass);
            }
            return cachedClass;
        }

        // Apply loading strategy
        return loadingStrategy == LoadingStrategy.CHILD_FIRST ? loadClassChildFirst(name, resolve)
                : loadClassParentFirst(name, resolve);
    }

    /**
     * Loads class using child-first strategy (plugin first, then parent).
     */
    private Class<?> loadClassChildFirst(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return loadFromPlugin(name, resolve);
        } catch (ClassNotFoundException e) {
            // Not found in plugin, delegate to parent
            return loadFromParent(name, resolve);
        }
    }

    /**
     * Loads class using parent-first strategy (parent first, then plugin).
     */
    private Class<?> loadClassParentFirst(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return loadFromParent(name, resolve);
        } catch (ClassNotFoundException e) {
            // Not found in parent, try plugin
            return loadFromPlugin(name, resolve);
        }
    }

    /**
     * Loads class from parent ClassLoader.
     */
    private Class<?> loadFromParent(String name, boolean resolve) throws ClassNotFoundException {
        return super.loadClass(name, resolve);
    }

    /**
     * Loads class from plugin JAR.
     */
    private Class<?> loadFromPlugin(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = findClass(name);
        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }

    /**
     * Gets the plugin name.
     *
     * @return plugin name
     */
    public String getPluginName() {
        return pluginName;
    }

    /**
     * Gets the plugin JAR path.
     *
     * @return plugin JAR path
     */
    public Path getPluginJarPath() {
        return pluginJarPath;
    }

    /**
     * Gets the loading strategy.
     *
     * @return loading strategy
     */
    public LoadingStrategy getLoadingStrategy() {
        return loadingStrategy;
    }

    @Override
    public String toString() {
        return String.format("PluginClassLoader[plugin=%s, strategy=%s, jar=%s]", pluginName, loadingStrategy,
                pluginJarPath.getFileName());
    }
}

