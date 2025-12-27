package com.pdbp.core.spi;

import com.pdbp.api.Plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * SPI-based plugin discovery service.
 *
 * <p>Discovers plugins using Java's Service Provider Interface (SPI) mechanism.
 * Looks for META-INF/services/com.pdbp.api.Plugin files in JARs that list
 * plugin implementation classes.
 *
 * <p>SPI allows automatic plugin discovery without needing to specify
 * class names manually.
 *
 * @author Saurabh Maurya
 */
public class SPIPluginDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(SPIPluginDiscovery.class);
    private static final String SPI_SERVICE_FILE = "META-INF/services/com.pdbp.api.Plugin";

    /**
     * Discovers plugin implementations from a JAR file using SPI.
     *
     * @param jarPath path to the plugin JAR file
     * @return list of plugin class names found via SPI, empty if none found
     */
    public List<String> discoverPluginClasses(Path jarPath) {
        List<String> pluginClasses = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            // Look for META-INF/services/com.pdbp.api.Plugin file
            JarEntry entry = jarFile.getJarEntry(SPI_SERVICE_FILE);
            if (entry != null) {
                logger.debug("Found SPI service file in JAR: {}", jarPath);
                pluginClasses.addAll(readServiceFile(jarFile, entry));
            } else {
                logger.debug("No SPI service file found in JAR: {}", jarPath);
            }
        } catch (IOException e) {
            logger.error("Error reading JAR for SPI discovery: {}", jarPath, e);
        }

        return pluginClasses;
    }

    /**
     * Reads the SPI service file and extracts plugin class names.
     *
     * @param jarFile the JAR file
     * @param entry   the service file entry
     * @return list of plugin class names
     */
    private List<String> readServiceFile(JarFile jarFile, java.util.jar.JarEntry entry) throws IOException {
        List<String> classes = new ArrayList<>();

        try (InputStream is = jarFile.getInputStream(entry); BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // Remove comments (everything after #)
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }

                // Trim whitespace
                line = line.trim();

                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }

                // Validate class name format
                if (isValidClassName(line)) {
                    classes.add(line);
                    logger.debug("Found SPI plugin class: {}", line);
                } else {
                    logger.warn("Invalid class name in SPI file: {}", line);
                }
            }
        }

        return classes;
    }

    /**
     * Validates that a string is a valid Java class name.
     *
     * @param className the class name to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidClassName(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }

        // Basic validation: should contain only valid Java identifier characters
        // and dots (for package separators)
        return className.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*$");
    }

    /**
     * Discovers all plugin implementations from the classpath using SPI.
     *
     * <p>This method uses Java's ServiceLoader mechanism to find all
     * Plugin implementations available on the classpath.
     *
     * @return list of discovered plugin class names
     */
    public List<String> discoverFromClasspath() {
        List<String> pluginClasses = new ArrayList<>();

        try {
            // Use ServiceLoader to find all Plugin implementations
            java.util.ServiceLoader<Plugin> serviceLoader = java.util.ServiceLoader.load(Plugin.class);

            for (Plugin plugin : serviceLoader) {
                String className = plugin.getClass().getName();
                pluginClasses.add(className);
                logger.debug("Discovered plugin from classpath via SPI: {}", className);
            }
        } catch (Exception e) {
            logger.error("Error discovering plugins from classpath via SPI", e);
        }

        return pluginClasses;
    }

    /**
     * Reads SPI service file from a URL (for classpath resources).
     *
     * @param serviceFileUrl URL to the service file
     * @return list of plugin class names
     */
    public List<String> readServiceFileFromUrl(URL serviceFileUrl) {
        List<String> classes = new ArrayList<>();

        try (InputStream is = serviceFileUrl.openStream(); BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // Remove comments
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }

                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (isValidClassName(line)) {
                    classes.add(line);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading SPI service file from URL: {}", serviceFileUrl, e);
        }

        return classes;
    }
}

