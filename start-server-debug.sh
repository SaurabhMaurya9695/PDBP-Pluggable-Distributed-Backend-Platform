#!/bin/bash

# PDBP Server Startup Script with Remote Debugging Enabled
# This script builds and starts the PDBP server with debug port 5005

set -e

echo "=========================================="
echo "PDBP - Pluggable Distributed Backend Platform"
echo "Starting server with DEBUG mode..."
echo "=========================================="
echo ""

# Get the project root directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Default port
PORT=${1:-8080}
DEBUG_PORT=${2:-5005}

# Check if debug port is already in use
if lsof -Pi :$DEBUG_PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "⚠️  Debug port $DEBUG_PORT is already in use!"
    echo ""
    read -p "Kill process on debug port $DEBUG_PORT? (y/n): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🔪 Killing process on debug port $DEBUG_PORT..."
        lsof -ti:$DEBUG_PORT | xargs kill -9 2>/dev/null
        sleep 1
        echo "✅ Debug port $DEBUG_PORT is now free"
    else
        echo "❌ Exiting. Please free debug port $DEBUG_PORT or use a different port."
        exit 1
    fi
fi

# Check if server port is already in use
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "⚠️  Port $PORT is already in use!"
    echo ""
    read -p "Kill process on port $PORT? (y/n): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🔪 Killing process on port $PORT..."
        lsof -ti:$PORT | xargs kill -9 2>/dev/null
        sleep 1
        echo "✅ Port $PORT is now free"
    else
        echo "❌ Exiting. Please free port $PORT or use a different port."
        exit 1
    fi
fi

# Kill any existing PDBP server processes
echo "🧹 Cleaning up any existing PDBP processes..."
ps aux | grep -i "PDBPServer\|exec:java.*PDBPServer" | grep -v grep | awk '{print $2}' | xargs kill -9 2>/dev/null || true
sleep 1

# Build the project first
echo "📦 Building project..."
mvn clean install -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "✅ Build successful!"
echo ""

# Create logs directory if it doesn't exist
mkdir -p logs

# Start the server with debug enabled
echo "🐛 Starting PDBP Server with DEBUG mode..."
echo "   Server port: $PORT"
echo "   Debug port: $DEBUG_PORT"
echo "   Logs will be written to: logs/pdbp.log"
echo ""
echo "   📌 Attach your debugger to: localhost:$DEBUG_PORT"
echo "   📌 IntelliJ IDEA: Run → Attach to Process → Remote JVM Debug"
echo "   📌 VS Code: Use Java Debugger extension"
echo "   📌 Eclipse: Run → Debug Configurations → Remote Java Application"
echo ""
echo "   To view logs in real-time: tail -f logs/pdbp.log"
echo "   Press Ctrl+C to stop the server"
echo ""
echo "=========================================="
echo ""

# Run from pdbp-admin module with debug JVM arguments
cd pdbp-admin
mvn exec:java \
    -Dexec.mainClass="com.pdbp.admin.PDBPServer" \
    -Dexec.args="--port $PORT" \
    -Dexec.jvmArgs="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$DEBUG_PORT"

