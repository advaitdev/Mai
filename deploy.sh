#!/bin/bash

# Mai deploy script — build, copy to server, and restart

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$PROJECT_DIR/mc-server"
PLUGINS_DIR="$SERVER_DIR/plugins"
JAR_NAME="Mai-GIT.jar"

# Paper 26.2 needs Java 25. Use $JAVA_HOME if set (e.g. /home/advait/jdks/25),
# else whatever `java` is on PATH. Maven picks up JAVA_HOME on its own.
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

# Build
echo "Building..."
mvn -f "$PROJECT_DIR/pom.xml" clean package -q

# Copy JAR to plugins
echo "Copying $JAR_NAME to plugins..."
cp "$PROJECT_DIR/target/$JAR_NAME" "$PLUGINS_DIR/$JAR_NAME"

# Stop the server if running: only a server.jar process running from this
# server's directory (not the Minecraft client, not the 26.3 test server).
SERVER_PID=""
for pid in $(pgrep -f "server\.jar" 2>/dev/null || true); do
    if [ "$(readlink -f /proc/$pid/cwd 2>/dev/null)" = "$(readlink -f "$SERVER_DIR")" ]; then
        SERVER_PID="$SERVER_PID $pid"
    fi
done
if [ -n "$SERVER_PID" ]; then
    echo "Stopping server (PID$SERVER_PID)..."
    kill $SERVER_PID 2>/dev/null || true
    for pid in $SERVER_PID; do
        while kill -0 "$pid" 2>/dev/null; do sleep 1; done
    done
    echo "Server stopped."
fi

SESSION_NAME="mc"
START_CMD="cd '$SERVER_DIR' && $JAVA_BIN -Xmx2G -jar server.jar --nogui"
if command -v screen >/dev/null 2>&1; then
    screen -S "$SESSION_NAME" -X quit 2>/dev/null || true
    echo "Starting server in screen session '$SESSION_NAME'..."
    screen -dmS "$SESSION_NAME" bash -c "$START_CMD"
    echo "Server started. Attach with: screen -r $SESSION_NAME"
else
    # This WSL box has tmux but no screen.
    tmux kill-session -t "$SESSION_NAME" 2>/dev/null || true
    echo "Starting server in tmux session '$SESSION_NAME'..."
    tmux new-session -d -s "$SESSION_NAME" "$START_CMD"
    echo "Server started. Attach with: tmux attach -t $SESSION_NAME (detach: Ctrl-B D)"
fi
