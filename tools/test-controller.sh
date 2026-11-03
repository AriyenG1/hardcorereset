#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
hcr_classes=$(mktemp -d)
trap 'rm -rf "$hcr_classes"' EXIT HUP INT TERM
javac -d "$hcr_classes" \
  src/main/java/dev/ariyeng1/hardcorereset/RoundController.java \
  src/main/java/dev/ariyeng1/hardcorereset/RunState.java \
  tests/java/dev/ariyeng1/hardcorereset/LiveResetTest.java
java -cp "$hcr_classes" dev.ariyeng1.hardcorereset.LiveResetTest
