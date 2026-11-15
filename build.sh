#!/bin/sh
set -eu

projectRoot=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
sourceDirectory="$projectRoot/src"
buildDirectory="$projectRoot/.build"
classesDirectory="$buildDirectory/classes"
sourcesList="$buildDirectory/sources.list"
jarPath="$projectRoot/dragonet.jar"
mainClass="br.redhot.dragonet.Main"
lanternaJar="$projectRoot/lib/lanterna-3.1.2.jar"

if [ ! -d "$sourceDirectory" ]; then
    printf '[FAIL] source directory not found: %s\n' "$sourceDirectory" >&2
    printf '       Check that you are running build.sh from the repository root\n' >&2
    exit 1
fi

if [ ! -f "$lanternaJar" ]; then
    printf '[FAIL] Lanterna dependency not found: %s\n' "$lanternaJar" >&2
    printf '       Run: curl -fsSLo lib/lanterna-3.1.2.jar \\\n' >&2
    printf '            https://repo1.maven.org/maven2/com/googlecode/lanterna/lanterna/3.1.2/lanterna-3.1.2.jar\n' >&2
    exit 1
fi

# Guard: the wrong Lanterna Screen import collapses the whole build with
# hundreds of cascading "cannot find symbol" errors. Catch it up front.
if grep -rn '^import com\.googlecode\.lanterna\.Screen;' "$sourceDirectory" > /dev/null 2>&1; then
    printf '[FAIL] wrong Screen import (must be com.googlecode.lanterna.screen.Screen):\n' >&2
    grep -rn '^import com\.googlecode\.lanterna\.Screen;' "$sourceDirectory" >&2
    exit 1
fi

sourceCount=$(find "$sourceDirectory" -name '*.java' -type f | wc -l | tr -d ' ')

if [ "$sourceCount" -eq 0 ]; then
    printf '[warn] no .java sources under %s — nothing to compile\n' "$sourceDirectory" >&2
    exit 0
fi

rm -rf "$classesDirectory"
mkdir -p "$classesDirectory"

printf '[info] compiling %s source file(s)...\n' "$sourceCount"

find "$sourceDirectory" -name '*.java' -type f > "$sourcesList"

javac -encoding UTF-8 -Xlint:all \
      -cp "$lanternaJar" \
      -d "$classesDirectory" \
      @"$sourcesList"

printf '[info] bundling Lanterna into dragonet.jar\n'
( cd "$classesDirectory" && jar --extract --file "$lanternaJar" )

printf '[info] building jar: %s\n' "$jarPath"
jar --create --file "$jarPath" --main-class "$mainClass" -C "$classesDirectory" .

printf '[ OK ] dragonet.jar ready\n'
