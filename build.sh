#!/bin/sh
set -eu

projectRoot=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
sourceDirectory="$projectRoot/src"
buildDirectory="$projectRoot/.build"
classesDirectory="$buildDirectory/classes"
sourcesList="$buildDirectory/sources.list"
jarPath="$projectRoot/dragonet.jar"
mainClass="br.redhot.dragonet.Main"

if [ ! -d "$sourceDirectory" ]; then
    printf '[FAIL] source directory not found: %s\n' "$sourceDirectory" >&2
    printf '       Check that you are running build.sh from the repository root\n' >&2
    exit 1
fi

sourceCount=$(find "$sourceDirectory" -name '*.java' -type f | wc -l | tr -d ' ')

if [ "$sourceCount" -eq 0 ]; then
    printf '[warn] no .java sources under %s — nothing to compile\n' "$sourceDirectory" >&2
    printf '       Next step: add src/br/redhot/dragonet/Main.java\n' >&2
    exit 0
fi

rm -rf "$classesDirectory"
mkdir -p "$classesDirectory"

printf '[info] compiling %s source file(s)...\n' "$sourceCount"

find "$sourceDirectory" -name '*.java' -type f > "$sourcesList"

javac -encoding UTF-8 -Xlint:all -d "$classesDirectory" @"$sourcesList"

printf '[info] building jar: %s\n' "$jarPath"
jar --create --file "$jarPath" --main-class "$mainClass" -C "$classesDirectory" .

printf '[ OK ] dragonet.jar ready\n'