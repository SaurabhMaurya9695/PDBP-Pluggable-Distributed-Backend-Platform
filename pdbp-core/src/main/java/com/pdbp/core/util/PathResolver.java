package com.pdbp.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for resolving file paths, especially for plugin JAR files.
 *
 * <p>Handles path resolution from various locations with proper fallback logic.
 *
 * @author Saurabh Maurya
 */
public final class PathResolver {

    private static final Logger logger = LoggerFactory.getLogger(PathResolver.class);
    private static final String WORK_DIRECTORY = "work";

    private PathResolver() {
        // Utility class - prevent instantiation
    }

    /**
     * Resolves a JAR file path to an absolute path.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If absolute path, use as-is</li>
     *   <li>Resolve from work directory (project root/work)</li>
     *   <li>Resolve from plugin discovery directory</li>
     *   <li>Resolve from project root</li>
     * </ol>
     *
     * @param jarPath            the JAR path (absolute or relative)
     * @param pluginDiscoveryDir optional plugin discovery directory (can be null)
     * @return resolved absolute path
     */
    public static Path resolveJarPath(String jarPath, Path pluginDiscoveryDir) {
        Path path = Paths.get(jarPath);

        // If absolute, use as-is
        if (path.isAbsolute()) {
            logger.debug("JAR path is already absolute: {}", path);
            return path;
        }

        // Priority 1: Resolve from work directory (project root/work)
        Path projectRoot = findProjectRoot();
        Path workDir = projectRoot.resolve(WORK_DIRECTORY).normalize();
        Path workResolved = workDir.resolve(jarPath).normalize();

        if (Files.exists(workResolved)) {
            logger.debug("Resolved JAR from work directory: {}", workResolved);
            return workResolved;
        }

        // Priority 2: Resolve from plugin discovery directory
        if (pluginDiscoveryDir != null) {
            Path resolved = pluginDiscoveryDir.resolve(jarPath).normalize();
            if (Files.exists(resolved)) {
                logger.debug("Resolved JAR from plugin directory: {}", resolved);
                return resolved;
            }
        }

        // Priority 3: Fallback to project root
        Path rootResolved = projectRoot.resolve(jarPath).normalize();
        if (Files.exists(rootResolved)) {
            logger.debug("Resolved JAR from project root: {}", rootResolved);
            return rootResolved;
        }

        // Return work directory path for better error messages
        logger.debug("JAR not found, returning work directory path: {}", workResolved);
        return workResolved;
    }

    /**
     * Finds the project root directory by looking for work directory or root pom.xml.
     *
     * <p>Handles cases where current working directory might be a subdirectory (e.g., pdbp-admin).
     *
     * <p>The project root is identified by:
     * <ol>
     *   <li>Having a "work" directory, OR</li>
     *   <li>Having a pom.xml with &lt;packaging&gt;pom&lt;/packaging&gt; (parent POM)</li>
     * </ol>
     *
     * @return the project root directory
     */
    public static Path findProjectRoot() {
        Path currentDir = Paths.get(System.getProperty("user.dir"));

        // First, check if we're in a subdirectory by looking at parent
        Path parentDir = currentDir.getParent();
        if (parentDir != null) {
            Path parentWorkDir = parentDir.resolve(WORK_DIRECTORY);
            if (Files.exists(parentWorkDir) && Files.isDirectory(parentWorkDir)) {
                logger.debug("Found work directory in parent, using project root: {}", parentDir);
                return parentDir;
            }

            Path parentPom = parentDir.resolve("pom.xml");
            if (Files.exists(parentPom) && isRootPom(parentPom)) {
                logger.debug("Found root pom.xml in parent, using project root: {}", parentDir);
                return parentDir;
            }
        }

        // Check if work directory exists in current directory
        Path workDir = currentDir.resolve(WORK_DIRECTORY);
        if (Files.exists(workDir) && Files.isDirectory(workDir)) {
            logger.debug("Found work directory in current dir, using: {}", currentDir);
            return currentDir;
        }

        // Check if current directory has root pom.xml
        Path pomFile = currentDir.resolve("pom.xml");
        if (Files.exists(pomFile) && isRootPom(pomFile)) {
            logger.debug("Found root pom.xml in current dir, using: {}", currentDir);
            return currentDir;
        }

        // Fallback: return current directory
        logger.warn("Could not determine project root, using current directory: {}", currentDir);
        return currentDir;
    }

    /**
     * Checks if a pom.xml is the root POM (has &lt;packaging&gt;pom&lt;/packaging&gt;).
     *
     * @param pomFile path to pom.xml file
     * @return true if it's a root POM, false otherwise
     */
    private static boolean isRootPom(Path pomFile) {
        try {
            String content = new String(Files.readAllBytes(pomFile), java.nio.charset.StandardCharsets.UTF_8);
            return content.contains("<packaging>pom</packaging>") || content.contains("<modules>");
        } catch (Exception e) {
            logger.debug("Error reading pom.xml to check if root: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the work directory path at project root.
     *
     * @return path to work directory
     */
    public static Path getWorkDirectory() {
        return findProjectRoot().resolve(WORK_DIRECTORY).normalize();
    }
}

