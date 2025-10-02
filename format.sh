#!/bin/bash
# Format Java code using Spotless with Red Hat style
# This uses the same eclipse-formatter.xml as VS Code

./gradlew spotlessApply
