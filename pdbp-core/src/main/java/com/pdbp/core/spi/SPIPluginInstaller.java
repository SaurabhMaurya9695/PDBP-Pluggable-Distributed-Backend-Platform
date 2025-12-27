package com.pdbp.core.spi;

import com.pdbp.api.Plugin;
import com.pdbp.api.PluginException;
import com.pdbp.core.PluginManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * SPI-based plugin installer.
 *
 * <p>Installs plugins discovered via SPI without requiring explicit
 * class name specification.
 *
 * @author Saurabh Maurya
 */
public class SPIPluginInstaller {

    private static final Logger logger = LoggerFactory.getLogger(SPIPluginInstaller.class);
    private final PluginManager pluginManager;
    private final SPIPluginDiscovery spiDiscovery;

    public SPIPluginInstaller(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        this.spiDiscovery = new SPIPluginDiscovery();
    }

    /**
     * Installs a plugin from a JAR file using SPI discovery.
     *
     * <p>This method:
     * 1. Discovers plugin class names via SPI from the JAR
     * 2. Installs the first discovered plugin
     * 3. Falls back to manual installation if SPI fails
     *
     * @param pluginName name to register the plugin under
     * @param jarPath    path to the plugin JAR file
     * @return the installed plugin instance
     * @throws PluginException if installation fails or no plugin found
     */
    public Plugin installPluginFromJar(String pluginName, Path jarPath) throws PluginException {
        logger.info("Installing plugin via SPI: {} from {}", pluginName, jarPath);

        // Discover plugin classes via SPI
        List<String> pluginClasses = spiDiscovery.discoverPluginClasses(jarPath);

        if (pluginClasses.isEmpty()) {
            throw new PluginException("No plugin classes found via SPI in JAR: " + jarPath
                    + ". Ensure JAR contains META-INF/services/com.pdbp.api.Plugin file.");
        }

        // Use first discovered class
        String className = pluginClasses.get(0);
        logger.info("Discovered plugin class via SPI: {} for plugin: {}", className, pluginName);

        if (pluginClasses.size() > 1) {
            logger.warn("Multiple plugin classes found via SPI in JAR: {}. Using first: {}", jarPath, className);
        }

        // Install using discovered class name
        return pluginManager.installPlugin(pluginName, jarPath.toString(), className);
    }

    /**
     * Installs a plugin from a JAR file using SPI discovery with automatic name detection.
     *
     * <p>The plugin name is derived from the JAR filename.
     *
     * @param jarPath path to the plugin JAR file
     * @return the installed plugin instance
     * @throws PluginException if installation fails
     */
    public Plugin installPluginFromJar(Path jarPath) throws PluginException {
        // Derive plugin name from JAR filename
        String fileName = jarPath.getFileName().toString();
        String pluginName = fileName.substring(0, fileName.length() - 4); // Remove .jar

        return installPluginFromJar(pluginName, jarPath);
    }
}

