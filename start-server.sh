#!/bin/bash

# PDBP Server Startup Script
# This script builds and starts the PDBP server

set -e

echo "=========================================="
echo "PDBP - Pluggable Distributed Backend Platform"
echo "Starting server..."
echo "=========================================="
echo ""

# Get the project root directory (parent of script location)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Default port
PORT=${1:-8080}

# Check if port is already in use
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "⚠️  Port $PORT is already in use!"
    echo ""
    echo "Options:"
    echo "  1. Kill the process using port $PORT"
    echo "  2. Use a different port: ./start-server.sh --port 9090"
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

# Start the server
echo "🚀 Starting PDBP Server..."
echo "   Logs will be written to: logs/pdbp.log"
echo "   Errors will be written to: logs/pdbp-error.log"
echo ""
echo "   To view logs in real-time: tail -f logs/pdbp.log"
echo "   Press Ctrl+C to stop the server"
echo ""
echo "=========================================="
echo ""

# Run from pdbp-admin module
cd pdbp-admin
mvn exec:java -Dexec.mainClass="com.pdbp.admin.PDBPServer" -Dexec.args="$@"
