#!/bin/bash

# Mai deploy script — build, copy to server, and restart

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$PROJECT_DIR/mc-server"
PLUGINS_DIR="$SERVER_DIR/plugins"
JAR_NAME="Mai-GIT.jar"

# Build
echo "Building..."
mvn -f "$PROJECT_DIR/pom.xml" clean package -q

# Copy JAR to plugins
echo "Copying $JAR_NAME to plugins..."
cp "$PROJECT_DIR/target/$JAR_NAME" "$PLUGINS_DIR/$JAR_NAME"

# Stop server if running (only kill the server.jar process, not the Minecraft client)
SERVER_PID=$(pgrep -f "server\.jar" 2>/dev/null || true)
if [ -n "$SERVER_PID" ]; then
    echo "Stopping server (PID $SERVER_PID)..."
    kill $SERVER_PID 2>/dev/null || true
    for pid in $SERVER_PID; do
        while kill -0 "$pid" 2>/dev/null; do sleep 1; done
    done
    echo "Server stopped."
fi

# Start server in screen session
SCREEN_NAME="mc"

# Kill existing screen session if present
screen -S "$SCREEN_NAME" -X quit 2>/dev/null || true

echo "Starting server in screen session '$SCREEN_NAME'..."
screen -dmS "$SCREEN_NAME" bash -c "cd '$SERVER_DIR' && java -Xmx2G -jar server.jar --nogui"
echo "Server started. Attach with: screen -r $SCREEN_NAME"
