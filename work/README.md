# Work Directory

This directory is where **all plugin JAR files** should be placed.

## Purpose

The `work` directory is the **default location** where PDBP looks for plugin JAR files when installing plugins.

## Usage

### Place Plugin JARs Here

All plugin JAR files should be placed directly in this directory:

```
work/
├── example-plugin.jar
├── payment-plugin.jar
├── auth-plugin.jar
└── ...
```

### Install Plugins

When installing a plugin via API, use **relative paths** from the work directory:

```bash
curl -X POST http://localhost:8080/api/plugins/install \
  -H "Content-Type: application/json" \
  -d '{
    "pluginName": "example-plugin",
    "jarPath": "example-plugin.jar",
    "className": "com.pdbp.example.ExamplePlugin"
  }'
```

**Note**: `jarPath` should be just the filename (e.g., `example-plugin.jar`), not `work/example-plugin.jar`.

## Path Resolution

PDBP resolves plugin JAR paths in this order:

1. **Absolute paths**: If you provide an absolute path, it's used as-is
2. **Work directory**: `{project_root}/work/{jarPath}`
3. **Plugin discovery directory**: `{plugin_dir}/{jarPath}` (if different from work)
4. **Current directory**: `{project_root}/{jarPath}` (fallback)

## Building Plugins

The `build-plugin.sh` script automatically copies built plugins to this directory:

```bash
./build-plugin.sh
# This will copy the plugin to: work/example-plugin.jar
```

## Best Practices

1. ✅ **Place all plugin JARs in `work/` directory**
2. ✅ **Use relative paths** (just filename) when installing
3. ✅ **Keep plugin JARs organized** - one JAR per plugin
4. ✅ **Don't commit JARs to git** - add `work/*.jar` to `.gitignore`

## Directory Structure

```
PDBP/
├── work/                    ← Plugin JARs go here
│   ├── example-plugin.jar
│   └── README.md
├── plugins/                 ← Old location (deprecated)
├── pdbp-admin/
├── pdbp-core/
└── ...
```

