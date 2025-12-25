# PDBP - Pluggable Distributed Backend Platform

A backend runtime where features are **NOT hard-coded**, but **installed, upgraded, enabled, disabled, and healed at runtime** — without restarting the platform.

## 🏗️ Architecture

```
                ┌───────────────┐
                │  PluginManager│
                │  (Core Engine) │
                └──────┬────────┘
                       │
        ┌──────────────▼──────────────┐
        │     PluginClassLoader        │
        │  (Isolated Class Loading)    │
        └──────┬──────────────┬────────┘
               │              │
       ┌───────▼──────┐  ┌────▼─────────┐
       │ Plugin A     │  │ Plugin B     │
       │ (JAR)        │  │ (JAR)       │
       └──────────────┘  └──────────────┘
```

## 📦 Project Structure

```
pdbp/
├── pdbp-api/              # Core plugin API interfaces
├── pdbp-loader/           # ClassLoader & plugin loading
├── pdbp-core/             # Runtime engine & plugin manager
└── plugins/
    └── example-plugin/    # Example plugin
```

## 🚀 Getting Started

### Prerequisites

- Java 8 or higher
- Maven 3.6+

### Build

```bash
mvn clean install
```

### Run the Server

```bash
cd pdbp-admin
mvn exec:java -Dexec.mainClass="com.pdbp.admin.PDBPServer"
```

Or use the packaged JAR:
```bash
cd pdbp-admin
mvn package
java -jar target/pdbp-admin-1.0-SNAPSHOT.jar
```

The server will start on `http://localhost:8080`

### Build Example Plugin

```bash
./build-plugin.sh
```

This builds the example plugin and copies it to the `plugins/` directory.

## 🔌 Plugin Development

### Creating a Plugin

1. Implement `Plugin` interface:

```java
public class MyPlugin implements Plugin {
    @Override
    public String getName() {
        return "my-plugin";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public void init(PluginContext ctx) throws PluginException {
        // Initialize plugin
    }
    
    @Override
    public void start() throws PluginException {
        // Start plugin operations
    }
    
    @Override
    public void stop() throws PluginException {
        // Stop plugin operations
    }
    
    @Override
    public void destroy() {
        // Cleanup resources
    }
    
    @Override
    public PluginState getState() {
        return state;
    }
}
```

2. Package as JAR with plugin classes
3. Install via PluginManager

## 📚 Design Principles

- **Single Responsibility**: Each class has one clear purpose
- **Dependency Inversion**: Plugins depend on abstractions
- **Interface Segregation**: Focused, minimal interfaces
- **Open/Closed**: Extensible without modification
- **Strategy Pattern**: Different loading strategies
- **Factory Pattern**: Plugin instance creation
- **State Pattern**: Plugin lifecycle management

## 🎯 Features

- ✅ Plugin interface with lifecycle
- ✅ Custom ClassLoader with isolation
- ✅ Plugin lifecycle management (install/init/start/stop/unload)
- ✅ Thread-safe plugin manager
- ✅ REST API for runtime plugin management
- ✅ Plugin discovery service
- ✅ Single entry point (PDBPServer)
- ✅ Example plugin

## 🌐 REST API

The platform exposes a REST API for managing plugins at runtime:

- `GET /health` - Health check
- `GET /api/plugins` - List all plugins
- `GET /api/plugins/:name` - Get plugin info
- `GET /api/plugins/discover` - Discover plugins in directory
- `POST /api/plugins/install` - Install a plugin
- `POST /api/plugins/:name/start` - Start a plugin
- `POST /api/plugins/:name/stop` - Stop a plugin
- `DELETE /api/plugins/:name` - Unload a plugin

See [API Documentation](docs/API.md) for details.

## 📖 Documentation

- [Architecture Overview](docs/architecture.md) (coming soon)
- [Plugin Development Guide](docs/plugin-development.md) (coming soon)

## 📝 License

[To be determined]

---

**Status**: 🚧 Phase 1 Complete - Basic Plugin System

