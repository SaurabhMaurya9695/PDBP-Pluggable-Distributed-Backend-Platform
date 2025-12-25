package com.pdbp.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Service for discovering and scanning plugin JAR files.
 *
 * @author Saurabh Maurya
 */
public class PluginDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(PluginDiscoveryService.class);

    private final Path _pluginDirectory;

    /**
     * Creates a PluginDiscoveryService.
     *
     * @param pluginDirectoryPath path to the plugin directory
     */
    public PluginDiscoveryService(String pluginDirectoryPath) {
        this._pluginDirectory = Paths.get(pluginDirectoryPath);
        ensurePluginDirectoryExists();
    }

    /**
     * Creates a PluginDiscoveryService with default plugin directory.
     */
    public PluginDiscoveryService() {
        this("plugins");
    }

    /**
     * Ensures the plugin directory exists, creates it if not.
     */
    private void ensurePluginDirectoryExists() {
        try {
            if (!Files.exists(_pluginDirectory)) {
                Files.createDirectories(_pluginDirectory);
                logger.info("Created plugin directory: {}", _pluginDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create plugin directory: {}", _pluginDirectory, e);
            throw new RuntimeException("Failed to create plugin directory", e);
        }
    }

    /**
     * Discovers all plugin JAR files in the plugin directory.
     *
     * @return list of discovered plugin JAR files
     */
    public List<PluginDescriptor> discoverPlugins() {
        logger.info("Discovering plugins in directory: {}", _pluginDirectory);

        List<PluginDescriptor> plugins = new ArrayList<>();

        if (!Files.exists(_pluginDirectory)) {
            logger.warn("Plugin directory does not exist: {}", _pluginDirectory);
            return plugins;
        }

        try {
            Files.walkFileTree(_pluginDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".jar")) {
                        PluginDescriptor descriptor = createPluginDescriptor(file);
                        if (descriptor != null) {
                            plugins.add(descriptor);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error("Error scanning plugin directory", e);
        }

        logger.info("Discovered {} plugin(s)", plugins.size());
        return plugins;
    }

    /**
     * Creates a plugin descriptor from a JAR file.
     *
     * @param jarPath path to the JAR file
     * @return plugin descriptor, or null if invalid
     */
    private PluginDescriptor createPluginDescriptor(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Manifest manifest = jarFile.getManifest();

            String pluginName = extractPluginName(jarPath, manifest);
            String className = extractPluginClassName(jarPath, manifest);

            if (className == null) {
                logger.warn("Plugin JAR does not contain plugin class info: {}", jarPath);
                return null;
            }

            return new PluginDescriptor(pluginName, jarPath.toString(), className, jarPath.toFile().length(),
                    jarPath.toFile().lastModified());
        } catch (IOException e) {
            logger.error("Error reading plugin JAR: {}", jarPath, e);
            return null;
        }
    }

    /**
     * Extracts plugin name from JAR path or manifest.
     */
    private String extractPluginName(Path jarPath, Manifest manifest) {
        if (manifest != null) {
            String name = manifest.getMainAttributes().getValue("Plugin-Name");
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        // Fallback to filename without extension
        String fileName = jarPath.getFileName().toString();
        return fileName.substring(0, fileName.length() - 4); // Remove .jar
    }

    /**
     * Extracts plugin class name from JAR.
     *
     * <p>Strategy:
     * 1. Check manifest for Plugin-Class attribute
     * 2. Scan JAR for classes implementing Plugin interface
     * 3. Use first found class
     */
    private String extractPluginClassName(Path jarPath, Manifest manifest) {
        // Try manifest first
        if (manifest != null) {
            String className = manifest.getMainAttributes().getValue("Plugin-Class");
            if (className != null && !className.isEmpty()) {
                return className;
            }
        }

        // Scan JAR for Plugin implementations
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return jarFile.stream().filter(entry -> entry.getName().endsWith(".class")).filter(
                            entry -> !entry.getName().contains("$")) // Exclude inner classes
                    .map(entry -> entry.getName().replace("/", ".").replace(".class", "")).filter(
                            className -> !className.startsWith("com.pdbp.api")) // Exclude API classes
                    .findFirst().orElse(null);
        } catch (IOException e) {
            logger.error("Error scanning JAR for plugin class: {}", jarPath, e);
            return null;
        }
    }

    /**
     * Gets the plugin directory path.
     *
     * @return plugin directory path
     */
    public Path getPluginDirectory() {
        return _pluginDirectory;
    }

    /**
     * Plugin descriptor containing metadata about a discovered plugin.
     */
    public static class PluginDescriptor {

        private final String name;
        private final String jarPath;
        private final String className;
        private final long size;
        private final long lastModified;

        public PluginDescriptor(String name, String jarPath, String className, long size, long lastModified) {
            this.name = name;
            this.jarPath = jarPath;
            this.className = className;
            this.size = size;
            this.lastModified = lastModified;
        }

        public String getName() {
            return name;
        }

        public String getJarPath() {
            return jarPath;
        }

        public String getClassName() {
            return className;
        }

        public long getSize() {
            return size;
        }

        public long getLastModified() {
            return lastModified;
        }
    }
}

