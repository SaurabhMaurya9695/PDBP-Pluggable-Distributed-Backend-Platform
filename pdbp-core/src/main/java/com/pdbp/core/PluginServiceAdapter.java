package com.pdbp.core;

import com.pdbp.api.Plugin;
import com.pdbp.api.PluginException;
import com.pdbp.api.PluginState;
import com.pdbp.controller.PluginService;
import com.pdbp.core.util.PathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter that implements PluginService interface using PluginManager.
 * 
 * <p>This bridges the gap between:
 * <ul>
 *   <li>PDBPC (controllers) - depends on PluginService interface</li>
 *   <li>PDBP (core) - has PluginManager implementation</li>
 * </ul>
 * 
 * <p>Design: Adapter Pattern - adapts PluginManager to PluginService interface.
 *
 * @author Saurabh Maurya
 */
public class PluginServiceAdapter implements PluginService {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginServiceAdapter.class);
    
    private final PluginManager pluginManager;
    private final PluginDiscoveryService discoveryService;
    
    public PluginServiceAdapter(PluginManager pluginManager, PluginDiscoveryService discoveryService) {
        this.pluginManager = pluginManager;
        this.discoveryService = discoveryService;
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
        return new PluginInfo(
            pluginName,
            plugin.getVersion(),
            state != null ? state.name() : "UNKNOWN",
            null
        );
    }
    
    @Override
    public List<PluginDescriptor> discoverPlugins() {
        List<PluginDiscoveryService.PluginDescriptor> descriptors = discoveryService.discoverPlugins();
        return descriptors.stream()
            .map(desc -> new PluginDescriptor(
                desc.getName(),
                desc.getJarPath(),
                desc.getClassName(),
                desc.getSize()
            ))
            .collect(Collectors.toList());
    }
    
    @Override
    public PluginInfo installPlugin(String pluginName, String jarPath, String className) 
            throws PluginServiceException {
        try {
            // Resolve JAR path using utility
            Path resolvedPath = PathResolver.resolveJarPath(jarPath, discoveryService.getPluginDirectory());
            logger.debug("Resolved JAR path for {}: {} (exists: {})", pluginName, resolvedPath, Files.exists(resolvedPath));
            
            // Verify the resolved path exists
            if (!Files.exists(resolvedPath)) {
                Path workDir = PathResolver.getWorkDirectory();
                Path expectedPath = workDir.resolve(Paths.get(jarPath).getFileName());
                logger.error("JAR file not found: {} (expected: {})", resolvedPath, expectedPath);
                throw new PluginService.PluginServiceException(
                    "JAR file not found: " + resolvedPath + 
                    ". Expected in work directory: " + expectedPath +
                    ". Please place plugin JAR files in the 'work' directory at project root.");
            }
            
            // Install and initialize plugin
            Plugin plugin = pluginManager.installPlugin(pluginName, resolvedPath.toString(), className);
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
    
    /**
     * Creates a PluginInfo object from plugin data.
     * 
     * @param pluginName the plugin name
     * @param plugin the plugin instance (can be null)
     * @param jarPath the JAR path (can be null)
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

