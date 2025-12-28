package com.pdbp.core;

import com.pdbp.api.Plugin;
import com.pdbp.api.PluginException;
import com.pdbp.api.PluginState;
import com.pdbp.controller.PluginService;
import com.pdbp.core.config.PluginConfigurationManager;
import com.pdbp.core.metrics.MetricsCollector;
import com.pdbp.core.spi.SPIPluginInstaller;
import com.pdbp.core.util.PathResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter that implements PluginService interface using PluginManager.
 *
 * @author Saurabh Maurya
 */
public class PluginServiceAdapter implements PluginService {

    private static final Logger logger = LoggerFactory.getLogger(PluginServiceAdapter.class);

    private final PluginManager pluginManager;
    private final PluginDiscoveryService discoveryService;
    private final SPIPluginInstaller spiInstaller;

    public PluginServiceAdapter(PluginManager pluginManager, PluginDiscoveryService discoveryService) {
        this.pluginManager = pluginManager;
        this.discoveryService = discoveryService;
        this.spiInstaller = new SPIPluginInstaller(pluginManager);
    }

    @Override
    public Set<String> listPlugins() {
        return pluginManager.listPlugins();
    }

    @Override
    public PluginInfo getPluginInfo(String pluginName) {
        Plugin plugin = pluginManager.getPlugin(pluginName);
        if (plugin == null) {
            return null;
        }

        PluginState state = pluginManager.getPluginState(pluginName);
        return new PluginInfo(pluginName, plugin.getVersion(), state != null ? state.name() : "UNKNOWN", null);
    }

    @Override
    public List<PluginDescriptor> discoverPlugins() {
        List<PluginDiscoveryService.PluginDescriptor> descriptors = discoveryService.discoverPlugins();
        return descriptors.stream().map(
                        desc -> new PluginDescriptor(desc.getName(), desc.getJarPath(), desc.getClassName(),
                                desc.getSize()))
                .collect(Collectors.toList());
    }

    @Override
    public PluginInfo installPlugin(String pluginName, String jarPath, String className) throws PluginServiceException {
        try {
            // Resolve JAR path using utility
            Path resolvedPath = PathResolver.resolveJarPath(jarPath, discoveryService.getPluginDirectory());
            logger.debug("Resolved JAR path for {}: {} (exists: {})", pluginName, resolvedPath,
                    Files.exists(resolvedPath));

            // Verify the resolved path exists
            if (!Files.exists(resolvedPath)) {
                Path workDir = PathResolver.getWorkDirectory();
                Path expectedPath = workDir.resolve(Paths.get(jarPath).getFileName());
                logger.error("JAR file not found: {} (expected: {})", resolvedPath, expectedPath);
                throw new PluginService.PluginServiceException(
                        "JAR file not found: " + resolvedPath + ". Expected in work directory: " + expectedPath
                                + ". Please place plugin JAR files in the 'work' directory at project root.");
            }

            Plugin plugin;
            try {
                plugin = spiInstaller.installPluginFromJar(pluginName, resolvedPath);
                logger.info("Successfully installed plugin via SPI: {}", pluginName);
            } catch (PluginException e) {
                throw new PluginService.PluginServiceException("SPI discovery failed for plugin: " + pluginName
                        + ". Please provide className or ensure JAR contains META-INF/services/com.pdbp.api.Plugin "
                        + "file.",
                        e);
            }

            // Initialize plugin
            pluginManager.initPlugin(pluginName);

            // Return plugin info
            return createPluginInfo(pluginName, plugin, resolvedPath.toString());
        } catch (PluginException e) {
            throw new PluginService.PluginServiceException("Failed to install plugin: " + pluginName, e);
        } catch (PluginService.PluginServiceException e) {
            throw e; // Re-throw service exceptions
        } catch (Exception e) {
            throw new PluginService.PluginServiceException("Unexpected error installing plugin: " + pluginName, e);
        }
    }

    @Override
    public PluginInfo startPlugin(String pluginName) throws PluginServiceException {
        try {
            pluginManager.startPlugin(pluginName);
            return createPluginInfo(pluginName, pluginManager.getPlugin(pluginName), null);
        } catch (PluginException e) {
            throw new PluginService.PluginServiceException("Failed to start plugin: " + pluginName, e);
        }
    }

    @Override
    public PluginInfo stopPlugin(String pluginName) throws PluginServiceException {
        try {
            pluginManager.stopPlugin(pluginName);
            return createPluginInfo(pluginName, pluginManager.getPlugin(pluginName), null);
        } catch (PluginException e) {
            throw new PluginService.PluginServiceException("Failed to stop plugin: " + pluginName, e);
        }
    }

    @Override
    public void unloadPlugin(String pluginName) throws PluginServiceException {
        try {
            pluginManager.unloadPlugin(pluginName);
        } catch (PluginException e) {
            throw new PluginService.PluginServiceException("Failed to unload plugin: " + pluginName, e);
        }
    }

    @Override
    public Map<String, Object> getMetrics() {
        MetricsCollector collector = MetricsCollector.getInstance();
        Map<String, Object> metrics = new HashMap<>();

        metrics.put("totalPluginsInstalled", collector.getTotalPluginsInstalled());
        metrics.put("totalPluginsStarted", collector.getTotalPluginsStarted());
        metrics.put("totalPluginsStopped", collector.getTotalPluginsStopped());
        metrics.put("totalPluginsUnloaded", collector.getTotalPluginsUnloaded());
        metrics.put("totalPluginErrors", collector.getTotalPluginErrors());
        metrics.put("serverUptime", collector.getServerUptime());
        metrics.put("totalApiRequests", collector.getTotalApiRequests());
        metrics.put("totalApiErrors", collector.getTotalApiErrors());
        metrics.put("apiEndpoints", collector.getApiEndpointCounts());
        metrics.put("operationDurations", collector.getOperationDurations());

        // Add plugin-specific metrics
        Map<String, Object> pluginMetrics = new HashMap<>();
        collector.getPluginMetrics().forEach((name, pm) -> {
            Map<String, Object> pluginData = new HashMap<>();
            pluginData.put("installCount", pm.getInstallCount());
            pluginData.put("startCount", pm.getStartCount());
            pluginData.put("stopCount", pm.getStopCount());
            pluginData.put("errorCount", pm.getErrorCount());
            pluginData.put("avgInstallDuration", pm.getAverageInstallDuration());
            pluginData.put("avgStartDuration", pm.getAverageStartDuration());
            pluginData.put("avgStopDuration", pm.getAverageStopDuration());
            pluginMetrics.put(name, pluginData);
        });
        metrics.put("plugins", pluginMetrics);

        return metrics;
    }

    @Override
    public void recordApiRequest(String endpoint) {
        MetricsCollector.getInstance().recordApiRequest(endpoint);
    }

    @Override
    public void recordApiError(String endpoint) {
        MetricsCollector.getInstance().recordApiError(endpoint);
    }

    /**
     * Gets plugin configuration.
     *
     * @param pluginName the plugin name
     * @return configuration map, or null if plugin not found
     * @throws PluginServiceException if operation fails
     */
    @Override
    public Map<String, String> getPluginConfig(String pluginName) throws PluginServiceException {
        if (pluginManager.getPlugin(pluginName) == null) {
            throw new PluginService.PluginServiceException("Plugin not found: " + pluginName);
        }
        PluginConfigurationManager configManager = pluginManager.getConfigManager();
        return configManager.getPluginConfig(pluginName);
    }

    /**
     * Updates plugin configuration.
     *
     * @param pluginName the plugin name
     * @param config    configuration map to update
     * @throws PluginServiceException if operation fails
     */
    public void updatePluginConfig(String pluginName, Map<String, String> config) throws PluginServiceException {
        if (pluginManager.getPlugin(pluginName) == null) {
            throw new PluginService.PluginServiceException("Plugin not found: " + pluginName);
        }
        try {
            PluginConfigurationManager configManager = pluginManager.getConfigManager();
            configManager.savePluginConfig(pluginName, config);
            logger.info("Configuration updated for plugin: {}", pluginName);
        } catch (Exception e) {
            throw new PluginService.PluginServiceException("Failed to update configuration for plugin: " + pluginName, e);
        }
    }

    /**
     * Creates a PluginInfo object from plugin data.
     *
     * @param pluginName the plugin name
     * @param plugin     the plugin instance (can be null)
     * @param jarPath    the JAR path (can be null)
     * @return PluginInfo object
     */
    private PluginInfo createPluginInfo(String pluginName, Plugin plugin, String jarPath) {
        PluginState state = pluginManager.getPluginState(pluginName);
        return new PluginInfo(
            pluginName,
            plugin != null ? plugin.getVersion() : "unknown",
            state != null ? state.name() : "UNKNOWN",
            jarPath
        );
    }
}

