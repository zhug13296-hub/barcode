#!/usr/bin/env sh

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "${DIR}" && pwd -P)

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

warn () {
    printf '%s\n' "$*"
}

set -- \
        -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

exec java $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "$@"
