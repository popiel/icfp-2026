#!/usr/bin/env bash
# Launches the littleman interpreter, mirroring the sibling icfp-2006-ai run.sh.
# Usage: ./run.sh <program.man> [step-cap]
set -euo pipefail
CLASSPATH=$(sbt --batch 'export runtime:fullClasspath' 2>/dev/null | tail -1 | sed 's/\\/\//g')
java -classpath "$CLASSPATH" com.wolfskeep.littleman.Main "$@"