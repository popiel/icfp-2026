#!/usr/bin/env bash
# Launches the problem-fetcher, mirroring run.sh.
# Usage: ./fetcher.sh [--once|--watch] [--interval 5m] [--base-url URL] [--problems-dir problems]
set -euo pipefail
CLASSPATH=$(sbt --batch 'export runtime:fullClasspath' 2>/dev/null | tail -1 | sed 's/\\/\//g')
java -classpath "$CLASSPATH" com.wolfskeep.littleman.fetch.FetchMain "$@"