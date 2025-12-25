#!/bin/bash

# PDBP Server Stop Script
# This script stops any running PDBP server instances

set -e

PORT=${1:-8080}

echo "=========================================="
echo "PDBP - Stopping Server"
echo "=========================================="
echo ""

# Kill process on port
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo "🔪 Killing process on port $PORT..."
    lsof -ti:$PORT | xargs kill -9 2>/dev/null
    sleep 1
    echo "✅ Server stopped on port $PORT"
else
    echo "ℹ️  No server running on port $PORT"
fi

# Kill any PDBP processes by name
echo "🧹 Cleaning up PDBP processes..."
PIDS=$(ps aux | grep -i "PDBPServer\|exec:java.*PDBPServer" | grep -v grep | awk '{print $2}')
if [ -n "$PIDS" ]; then
    echo "$PIDS" | xargs kill -9 2>/dev/null
    echo "✅ Stopped all PDBP processes"
else
    echo "ℹ️  No PDBP processes found"
fi

echo ""
echo "✅ Cleanup complete!"

