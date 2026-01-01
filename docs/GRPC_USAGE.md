# gRPC Usage Guide

## Overview

PDBP includes gRPC support for high-performance plugin-to-plugin and plugin-to-platform communication. This guide shows you how to use gRPC in your plugins.

## Architecture

```
┌─────────────┐         gRPC          ┌─────────────┐
│   Plugin A  │ ────────────────────> │   Plugin B  │
│  (Client)   │                        │  (Server)   │
└─────────────┘                        └─────────────┘
       │                                       │
       │                                       │
       └───────────────┐       ┌───────────────┘
                       │       │
                  ┌────▼───────▼────┐
                  │  gRPC Server    │
                  │  (Port 9090)    │
                  └─────────────────┘
```

## Quick Start

### 1. Access gRPC Service from Plugin

```java
public class MyPlugin implements Plugin {
    private GrpcService.GrpcClient grpcClient;
    
    @Override
    public void init(PluginContext context) throws PluginException {
        // Get gRPC service from context
        Optional<GrpcService> grpcServiceOpt = context.getService(GrpcService.class);
        if (grpcServiceOpt.isPresent()) {
            // Create client (connects to localhost:9090 by default)
            grpcClient = grpcServiceOpt.get().createClient();
        }
    }
    
    @Override
    public void start() throws PluginException {
        if (grpcClient != null) {
            try {
                // Call another plugin
                Map<String, String> params = new HashMap<>();
                params.put("key", "value");
                String response = grpcClient.callPlugin("target-plugin", "method", params);
                logger.info("gRPC response: {}", response);
            } catch (GrpcService.GrpcException e) {
                logger.error("gRPC call failed", e);
            }
        }
    }
    
    @Override
    public void stop() throws PluginException {
        if (grpcClient != null) {
            grpcClient.close();
        }
    }
}
```

### 2. Health Check

```java
// Check if another plugin is healthy
boolean isHealthy = grpcClient.healthCheck("target-plugin");
if (isHealthy) {
    logger.info("Target plugin is healthy");
}
```

## Streaming Examples

### Server Streaming (Receive multiple responses)

```java
// This would be implemented in the gRPC client wrapper
// Currently available via direct GrpcClient usage
```

### Client Streaming (Send multiple requests)

```java
// This would be implemented in the gRPC client wrapper
// Currently available via direct GrpcClient usage
```

### Bidirectional Streaming

```java
// This would be implemented in the gRPC client wrapper
// Currently available via direct GrpcClient usage
```

## Testing gRPC

### 1. Start the Server

```bash
cd pdbp-admin
mvn exec:java -Dexec.mainClass="com.pdbp.admin.PDBPServer"
```

The gRPC server will start on port 9090 (or as configured).

### 2. Test with gRPC Client

You can use any gRPC client tool or create a test client.

## Configuration

- **gRPC Server Port**: Set via `-Dpdbp.grpc.port=9090` (default: 9090)
- **gRPC Client Host**: Default is `localhost`
- **gRPC Client Port**: Default is `9090`

## Error Handling

gRPC operations throw `GrpcService.GrpcException` which wraps the underlying gRPC errors:

```java
try {
    String response = grpcClient.callPlugin("plugin", "method", params);
} catch (GrpcService.GrpcException e) {
    logger.error("gRPC error: {}", e.getMessage());
    // Check status code if needed
}
```

## Best Practices

1. **Always close clients**: Use try-with-resources or call `close()` in `stop()` method
2. **Handle errors gracefully**: gRPC calls can fail due to network issues
3. **Check plugin health**: Use `healthCheck()` before making calls
4. **Use appropriate streaming type**: Choose unary for simple calls, streaming for data transfer

## Advanced: Direct gRPC Client Access

If you need access to advanced features (streaming), you can access the underlying client:

```java
// This requires direct dependency on pdbp-grpc module
import com.pdbp.grpc.client.GrpcClient;

GrpcClient client = new GrpcClient("localhost", 9090);
// Use streaming methods...
client.close();
```

