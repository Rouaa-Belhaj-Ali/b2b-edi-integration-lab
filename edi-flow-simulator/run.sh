#!/bin/sh
# Needs a JDK (javac), version 17 or newer. Run from the project root.
mkdir -p bin && javac -d bin src/b2blab/*.java && java -cp bin b2blab.Main "$@"
