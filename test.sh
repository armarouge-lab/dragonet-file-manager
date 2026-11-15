#!/bin/sh
set -eu

projectRoot=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
testsDirectory="$projectRoot/tests"
buildDirectory="$projectRoot/.build"
classesDirectory="$buildDirectory/classes"
testClassesDirectory="$buildDirectory/test-classes"
sourcesList="$buildDirectory/test-sources.list"
lanternaJar="$projectRoot/lib/lanterna-3.1.2.jar"
junitJar="$projectRoot/lib/junit-platform-console-standalone.jar"

if [ ! -f "$junitJar" ]; then
    printf '[FAIL] JUnit 5 not found: %s\n' "$junitJar" >&2
    printf '       Run: curl -fsSLo lib/junit-platform-console-standalone.jar \\\n' >&2
    printf '            https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar\n' >&2
    exit 1
fi

if [ ! -d "$classesDirectory" ]; then
    printf '[FAIL] classes not built. Run ./build.sh first.\n' >&2
    exit 1
fi

rm -rf "$testClassesDirectory"
mkdir -p "$testClassesDirectory"

printf '[info] compiling tests...\n'
find "$testsDirectory" -name '*.java' -type f > "$sourcesList"

javac -encoding UTF-8 -Xlint:all \
      -cp "$classesDirectory:$lanternaJar:$junitJar" \
      -d "$testClassesDirectory" \
      @"$sourcesList"

printf '[info] running tests...\n'
java -jar "$junitJar" \
     execute \
     --class-path "$classesDirectory:$lanternaJar:$testClassesDirectory" \
     --scan-class-path \
     --details=tree

printf '[ OK ] tests passed\n'