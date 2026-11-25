#!/usr/bin/env bash
# Installs the Paper server jar into the local Maven repository so the fake
# player code (me.advait.mai.body.fake), which extends Minecraft's
# ServerPlayer, compiles. Paper 26.x is unobfuscated: the patched jar the
# bundler extracts is compiled against directly (paperweight is Gradle-only).
# Run once per machine, after the dev server has started once (that extracts
# versions/26.2/paper-26.2.jar).
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=mc-server/versions/26.2/paper-26.2.jar
[ -f "$JAR" ] || { echo "missing $JAR: start mc-server once first"; exit 1; }
mvn -q install:install-file -Dfile="$JAR" -DgroupId=io.papermc.paper -DartifactId=paper-server \
    -Dversion=26.2.build.133-local -Dpackaging=jar
echo "installed io.papermc.paper:paper-server:26.2.build.133-local"
