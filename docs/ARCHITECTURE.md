# PDBP Architecture Documentation

## Overview

PDBP (Pluggable Distributed Backend Platform) is a runtime platform that allows plugins to be installed, started, stopped, and unloaded at runtime without restarting the platform.

## High-Level Architecture (HLD)

```
┌─────────────────────────────────────────────────┐
│           PDBPServer (Entry Point)              │
│  - Initializes services                        │
│  - Starts REST API server                      │
│  - Manages lifecycle                           │
└──────────────┬──────────────────────────────────┘
               │
    ┌──────────▼──────────┐
    │  PluginController   │
    │  (REST API Layer)   │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │   PluginManager     │
    │  (Core Engine)      │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │ PluginClassLoader   │
    │ (Isolation Layer)    │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │    Plugin (JAR)     │
    │  (Runtime Loaded)   │
    └─────────────────────┘
```

## Low-Level Design (LLD)

### Layer Architecture

```
┌─────────────────────────────────────────┐
│         Presentation Layer               │
│  - PluginController (REST API)          │
│  - DTOs (Data Transfer Objects)          │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         Service Layer                   │
│  - PluginManager (Lifecycle)            │
│  - PluginDiscoveryService (Discovery)   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         Infrastructure Layer            │
│  - PluginClassLoader (Class Loading)    │
│  - Plugin Context (Dependency Injection)│
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│         Domain Layer                    │
│  - Plugin Interface                     │
│  - PluginState                          │
│  - PluginException                      │
└─────────────────────────────────────────┘
```

## Module Structure

### pdbp-api
**Purpose**: Core plugin API interfaces and contracts

**Key Components**:
- `Plugin` - Core plugin interface
- `PluginState` - Lifecycle states
- `PluginContext` - Dependency injection context
- `PluginException` - Exception handling

**Design Principles**:
- Interface Segregation
- Dependency Inversion
- Single Responsibility

### pdbp-loader
**Purpose**: ClassLoader implementation for plugin isolation

**Key Components**:
- `PluginClassLoader` - Custom ClassLoader extending URLClassLoader
- Loading strategies (PARENT_FIRST, CHILD_FIRST)

**Design Principles**:
- Strategy Pattern for loading strategies
- Open/Closed Principle
- Single Responsibility

### pdbp-core
**Purpose**: Runtime engine and plugin management

**Key Components**:
- `PluginManager` - Core plugin lifecycle manager
- `PluginDiscoveryService` - Plugin discovery and scanning

**Design Principles**:
- Factory Pattern for plugin creation
- State Pattern for lifecycle management
- Thread Safety (ConcurrentHashMap)
- Service Layer pattern

### pdbp-admin
**Purpose**: REST API and admin interface

**Key Components**:
- `PDBPServer` - Main entry point
- `PluginController` - REST API controller
- DTOs for API communication

**Design Principles**:
- RESTful API design
- Controller-Service separation
- DTO pattern for API contracts

### plugins/example-plugin
**Purpose**: Example plugin implementation

**Key Components**:
- `ExamplePlugin` - Demonstrates plugin implementation

## Design Patterns Used

### 1. Strategy Pattern
- **Location**: `PluginClassLoader.LoadingStrategy`
- **Purpose**: Different class loading strategies (parent-first vs child-first)

### 2. Factory Pattern
- **Location**: `PluginManager.createPluginContext()`
- **Purpose**: Creates plugin instances and contexts

### 3. State Pattern
- **Location**: `PluginState` enum and state transitions
- **Purpose**: Manages plugin lifecycle states

### 4. Dependency Injection
- **Location**: `PluginContext` interface
- **Purpose**: Plugins receive dependencies rather than creating them

### 5. Service Layer Pattern
- **Location**: `PluginDiscoveryService`, `PluginManager`
- **Purpose**: Separation of business logic from presentation

### 6. DTO Pattern
- **Location**: `PluginInfoDTO`, `PluginInstallRequest`
- **Purpose**: Data transfer between layers

## Plugin Lifecycle

```
INSTALLED → LOADED → INITIALIZED → STARTED → STOPPED → UNLOADED
    ↓         ↓          ↓           ↓         ↓
  FAILED    FAILED    FAILED      FAILED   FAILED
```

### State Transitions

1. **INSTALLED**: Plugin JAR discovered
2. **LOADED**: Classes loaded into memory via separate ClassLoader
3. **INITIALIZED**: `init()` called, plugin ready
4. **STARTED**: `start()` called, plugin active
5. **STOPPED**: `stop()` called, plugin paused
6. **UNLOADED**: `destroy()` called, plugin removed
7. **FAILED**: Error occurred at any stage

## ClassLoader Isolation

Each plugin gets its own `PluginClassLoader` instance:
- Classes loaded by different ClassLoaders are different types
- Prevents class conflicts between plugins
- Allows plugins to use different versions of dependencies
- Parent ClassLoader (System ClassLoader) loads platform classes

## Thread Safety

- `PluginManager` uses `ConcurrentHashMap` for thread-safe operations
- Plugin state changes are synchronized
- Multiple threads can safely interact with PluginManager
- REST API handles concurrent requests

## REST API Flow

### Install Plugin Flow:
```
1. API Request → PluginController
2. PluginController → PluginManager.installPlugin()
3. PluginManager creates PluginClassLoader (separate ClassLoader)
4. PluginManager loads plugin class from JAR
5. PluginManager creates plugin instance
6. PluginManager calls init()
7. PluginManager stores plugin in registry
8. API Response with plugin info
```

### Start Plugin Flow:
```
1. API Request → PluginController
2. PluginController → PluginManager.startPlugin()
3. PluginManager validates state
4. PluginManager calls plugin.start()
5. PluginManager updates state to STARTED
6. API Response with updated state
```

## Design Principles Applied

1. **Single Responsibility**: Each class has one clear purpose
2. **Open/Closed**: Extensible without modification
3. **Liskov Substitution**: Plugin implementations are substitutable
4. **Interface Segregation**: Focused, minimal interfaces
5. **Dependency Inversion**: Depend on abstractions, not concretions
6. **Separation of Concerns**: Clear layer boundaries
7. **Clean Architecture**: Dependency rule (inner layers don't know outer layers)

## Future Enhancements

- SPI (Service Provider Interface) support
- Plugin dependencies
- Configuration management
- Event system
- Observability
- Self-healing capabilities
- Plugin marketplace
- Remote plugin loading
