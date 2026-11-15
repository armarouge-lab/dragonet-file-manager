#!/bin/sh
# Dragonet CLI integration tests.
#
# Runs against the installed 'dragonet' binary if available, otherwise
# against the local dragonet.jar (compiled by build.sh). POSIX sh,
# no bashisms, no python3 dependency.
#
# Exit 0 when every check passes. Non-zero (number of failures capped
# at 100) otherwise.

set -eu

scriptRoot=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
projectRoot=$(cd "$scriptRoot/../.." && pwd)

if command -v dragonet >/dev/null 2>&1; then
    dn() { dragonet "$@"; }
    binaryLabel="installed dragonet"
elif [ -f "$projectRoot/dragonet.jar" ]; then
    jarPath="$projectRoot/dragonet.jar"
    dn() { java -jar "$jarPath" "$@"; }
    binaryLabel="java -jar dragonet.jar"
else
    printf '[FAIL] no dragonet binary and no dragonet.jar\n' >&2
    exit 1
fi

printf '[info] testing: %s\n' "$binaryLabel"

workspace=$(mktemp -d "${TMPDIR:-/tmp}/dragonet-int.XXXXXX")
trap 'rm -rf "$workspace"' EXIT INT TERM

pass=0
fail=0

ok() {
    printf '[ OK ] %s\n' "$1"
    pass=$((pass + 1))
}

no() {
    printf '[FAIL] %s\n' "$1"
    fail=$((fail + 1))
}

expect_exit() {
    name=$1
    expected=$2
    shift 2
    set +e
    dn "$@" >/dev/null 2>&1
    actual=$?
    set -e
    if [ "$actual" -eq "$expected" ]; then
        ok "$name (exit $expected)"
    else
        no "$name (want $expected got $actual)"
    fi
}

expect_output() {
    name=$1
    needle=$2
    shift 2
    set +e
    out=$(dn "$@" 2>&1)
    set -e
    case "$out" in
        *"$needle"*) ok "$name" ;;
        *)           no "$name (missing '$needle')" ;;
    esac
}

# ---- meta ----

expect_output "version string"        "dragonet 1.0.0" --version
expect_output "help mentions --list"  "--list"         --help
expect_output "help mentions --cp"    "--cp"           --help
expect_output "help mentions --yes"   "--yes"          --help
expect_exit   "help exits 0"          0                --help
expect_exit   "unknown flag"          64               --bogus-flag

# ---- dump-config ----

expect_output "dump-config version"   "version=1"      --dump-config
expect_output "dump-config theme"     "theme=redhot"   --dump-config
expect_output "dump-config preview"   "preview=off"    --dump-config
expect_output "dump-config imp_mode"  "imp_mode=auto"  --dump-config

# ---- selftest ----

set +e
selftestOut=$(dn --selftest 2>&1)
selftestCode=$?
set -e
case "$selftestOut" in
    *java*) ok "selftest reports java" ;;
    *)      no "selftest reports java" ;;
esac
case "$selftestOut" in
    *lanterna*) ok "selftest reports lanterna" ;;
    *)          no "selftest reports lanterna" ;;
esac
case "$selftestOut" in
    *trash*) ok "selftest reports trash" ;;
    *)       no "selftest reports trash" ;;
esac
case "$selftestCode" in
    0|1) ok "selftest exits 0 or 1 (got $selftestCode)" ;;
    *)   no "selftest unexpected exit $selftestCode" ;;
esac

# ---- list ----

expect_exit   "list empty dir"        0  --list "$workspace"
expect_exit   "list nonexistent dir"  5  --list "/definitely/not/here"

echo "hello" > "$workspace/a.txt"
echo "world" > "$workspace/b.txt"

expect_output "list shows a.txt" "a.txt" --list "$workspace"
expect_output "list shows b.txt" "b.txt" --list "$workspace"

set +e
jsonOut=$(dn --ls-json "$workspace" 2>&1)
set -e
case "$jsonOut" in
    \[*"\""name"\""*) ok "ls-json emits JSON array" ;;
    *)                no "ls-json emits JSON array" ;;
esac

# ---- mkdir / touch ----

dn --mkdir "$workspace/newdir" >/dev/null
if [ -d "$workspace/newdir" ]; then ok "mkdir creates directory"; else no "mkdir creates directory"; fi

dn --touch "$workspace/newfile.txt" >/dev/null
if [ -f "$workspace/newfile.txt" ]; then ok "touch creates file"; else no "touch creates file"; fi

expect_exit "mkdir on protected path" 1 --mkdir /etc/forbidden-test

# ---- cp ----

expect_exit "cp missing source" 5 --cp "$workspace/missing" "$workspace/dst.txt"

cp "$workspace/a.txt" "$workspace/copy-src.txt"
dn --cp "$workspace/copy-src.txt" "$workspace/copy-dst.txt" >/dev/null
if [ -f "$workspace/copy-dst.txt" ]; then ok "cp copies file"; else no "cp copies file"; fi

# dry-run must not copy
dn --cp --dry-run "$workspace/a.txt" "$workspace/dry.txt" >/dev/null 2>&1
if [ ! -f "$workspace/dry.txt" ]; then ok "cp --dry-run does not create"; else no "cp --dry-run does not create"; fi

# ---- mv ----

echo "move-me" > "$workspace/mv-src.txt"
dn --mv "$workspace/mv-src.txt" "$workspace/mv-dst.txt" >/dev/null
if [ -f "$workspace/mv-dst.txt" ] && [ ! -f "$workspace/mv-src.txt" ]; then
    ok "mv renames file"
else
    no "mv renames file"
fi

# ---- trash ----

echo "trash-me" > "$workspace/trashme.txt"
dn --rm "$workspace/trashme.txt" >/dev/null 2>&1
if [ ! -f "$workspace/trashme.txt" ]; then ok "rm moves to trash"; else no "rm moves to trash"; fi

set +e
trashListOut=$(dn --trash-list 2>&1)
set -e
case "$trashListOut" in
    *trashme.txt*) ok "trash-list shows entry" ;;
    *)             no "trash-list shows entry" ;;
esac

# Extract the UUID for restore: first field of the matching line
restoreUuid=$(printf '%s\n' "$trashListOut" | awk '/trashme\.txt/ {print $1; exit}')
if [ -n "$restoreUuid" ]; then
    dn --restore "$restoreUuid" >/dev/null 2>&1
    if [ -f "$workspace/trashme.txt" ]; then
        ok "restore recovers file"
    else
        no "restore recovers file"
    fi
else
    no "restore recovers file (no uuid parsed)"
fi

# ---- rm-permanent ----

echo "perm-me" > "$workspace/perm.txt"

expect_exit "rm-permanent without --yes" 64 --rm-permanent "$workspace/perm.txt"

set +e
dn --rm-permanent --yes "$workspace/perm.txt" >/dev/null 2>&1
set -e
if [ ! -f "$workspace/perm.txt" ]; then
    ok "rm-permanent --yes deletes"
else
    no "rm-permanent --yes deletes"
fi

# ---- summary ----

total=$((pass + fail))
printf '\n[info] %d checks, %d passed, %d failed\n' "$total" "$pass" "$fail"
if [ "$fail" -ne 0 ]; then
    exit "$fail"
fi
printf '[ OK ] integration tests passed\n'
