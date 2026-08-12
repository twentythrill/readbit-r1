#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`/"$link"
    fi
done
SAVED="`pwd`"
CDPATH=""
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or 60%, as the default memory limit.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support. $var _must_ be set to true or false.
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the instantiations of java
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" -a "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ $? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

# For Darwin, add options to compiler flags
if $darwin; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Ddock.name=$APP_NAME\" \"-Ddock.icon=$APP_HOME/media/gradle.icns\""
fi

# For Cygwin or MSYS, switch paths to Windows format before running java
if [ "$cygwin" = "true" -o "$msys" = "true" ] ; then
    APP_HOME=`cygpath --path --windows "$APP_HOME"`
    CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
    
    # We build the pattern for arguments to be converted below.
    # The pattern is composed of:
    # - Note the leading space.
    # - The start of a parameter: -D, -X, etc. or a single hyphen/double hyphen
    # - The parameter name and equal sign, or the whole parameter if no value.
    # - The value up to the next space or end of string.
    # - Repeat for each parameter.
    # Note that parameters might contain spaces.
    # To fix this, you must quote the parameter, or use standard escaping.
    # E.g. "-Dfoo=bar baz" or -Dfoo=bar\ baz
    # This pattern will match parameter values that contain spaces, as long as they are quoted.
    
    # Escape backslashes in JVM options to prevent them from being treated as escape characters.
    eval JVM_OPTS=($(echo "$JVM_OPTS" | sed 's/\\/\\\\/g'))
    eval GRADLE_OPTS=($(echo "$GRADLE_OPTS" | sed 's/\\/\\\\/g'))
fi

# Escape application args
for arg do
    # The evaluation causes any quotes to be removed, so we escape them.
    # We also have to escape backslashes so they are passed correctly.
    # See https://github.com/gradle/gradle/issues/1836
    # Note: Sed behavior on Windows can be erratic, so we use Java instead.
    # Java is guaranteed to be available.
    # Escape quotes and backslashes
    arg=`echo "$arg" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g'`
    # Surround arguments containing spaces with quotes.
    # Note: We need to use double quotes around $arg for this to work.
    case "$arg" in
        *" "* )
            OUR_ARGS="$OUR_ARGS \"$arg\""
            ;;
        * )
            OUR_ARGS="$OUR_ARGS $arg"
            ;;
    esac
done

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain $OUR_ARGS
