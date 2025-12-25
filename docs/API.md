# PDBP REST API Documentation

## Base URL
```
http://localhost:8080
```

## Endpoints

### Health Check
```
GET /health
```
Returns server health status.

**Response:**
```json
{"status":"UP"}
```

### List All Plugins
```
GET /api/plugins
```
Returns list of all installed plugins.

**Response:**
```json
[
  {
    "name": "example-plugin",
    "version": "1.0.0",
    "state": "STARTED",
    "jarPath": null
  }
]
```

### Get Plugin Info
```
GET /api/plugins/:name
```
Returns information about a specific plugin.

**Response:**
```json
{
  "name": "example-plugin",
  "version": "1.0.0",
  "state": "STARTED",
  "jarPath": null
}
```

### Discover Plugins
```
GET /api/plugins/discover
```
Scans the plugin directory and returns discovered JAR files.

**Response:**
```json
[
  {
    "name": "example-plugin",
    "jarPath": "plugins/example-plugin.jar",
    "className": "com.pdbp.example.ExamplePlugin",
    "size": 12345
  }
]
```

### Install Plugin
```
POST /api/plugins/install
Content-Type: application/json
```
Installs and initializes a plugin from a JAR file.

**Request Body:**
```json
{
  "pluginName": "example-plugin",
  "jarPath": "plugins/example-plugin.jar",
  "className": "com.pdbp.example.ExamplePlugin"
}
```

**Response:**
```json
{
  "name": "example-plugin",
  "version": "1.0.0",
  "state": "INITIALIZED",
  "jarPath": "plugins/example-plugin.jar"
}
```

### Start Plugin
```
POST /api/plugins/:name/start
```
Starts a plugin.

**Response:**
```json
{
  "name": "example-plugin",
  "version": "1.0.0",
  "state": "STARTED",
  "jarPath": null
}
```

### Stop Plugin
```
POST /api/plugins/:name/stop
```
Stops a plugin.

**Response:**
```json
{
  "name": "example-plugin",
  "version": "1.0.0",
  "state": "STOPPED",
  "jarPath": null
}
```

### Unload Plugin
```
DELETE /api/plugins/:name
```
Unloads a plugin from memory.

**Response:**
```json
{
  "message": "Plugin unloaded: example-plugin"
}
```

## Example Usage

### Install and Start a Plugin

1. **Discover available plugins:**
```bash
curl http://localhost:8080/api/plugins/discover
```

2. **Install a plugin:**
```bash
curl -X POST http://localhost:8080/api/plugins/install \
  -H "Content-Type: application/json" \
  -d '{
    "pluginName": "example-plugin",
    "jarPath": "plugins/example-plugin.jar",
    "className": "com.pdbp.example.ExamplePlugin"
  }'
```

3. **Start the plugin:**
```bash
curl -X POST http://localhost:8080/api/plugins/example-plugin/start
```

4. **Check plugin status:**
```bash
curl http://localhost:8080/api/plugins/example-plugin
```

5. **Stop the plugin:**
```bash
curl -X POST http://localhost:8080/api/plugins/example-plugin/stop
```

6. **Unload the plugin:**
```bash
curl -X DELETE http://localhost:8080/api/plugins/example-plugin
```

## Error Responses

All errors follow this format:
```json
{
  "error": "Error message here"
}
```

Common HTTP status codes:
- `200` - Success
- `201` - Created (plugin installed)
- `400` - Bad Request (invalid state, missing fields)
- `404` - Not Found (plugin not found)
- `500` - Internal Server Error

