#!/bin/bash

# Script to build and package the example plugin

echo "📦 Building example plugin..."

cd plugins/example-plugin

# Build the plugin
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Plugin build failed!"
    exit 1
fi

# Copy JAR to work directory (where plugins are loaded from)
mkdir -p ../../work
cp target/example-plugin-1.0-SNAPSHOT.jar ../../work/example-plugin.jar

echo "✅ Plugin built and copied to work/example-plugin.jar"
echo ""
echo "You can now install it via API:"
echo "  curl -X POST http://localhost:8080/api/plugins/install \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"pluginName\":\"example-plugin\",\"jarPath\":\"example-plugin.jar\",\"className\":\"com.pdbp.example.ExamplePlugin\"}'"
echo ""
echo "Note: jarPath should be relative to the work directory (just the filename)"

