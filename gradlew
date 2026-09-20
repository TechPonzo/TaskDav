#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$DIR/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" ${JAVA_OPTS:--Xmx2048m} -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
