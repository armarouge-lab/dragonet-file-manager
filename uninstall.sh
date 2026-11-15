#!/bin/sh
set -eu

scriptRoot=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

case "$(uname -s)" in
    Linux*)   packageScript="$scriptRoot/scripts/install-debian.sh" ;;
    FreeBSD*) packageScript="$scriptRoot/scripts/install-freebsd.sh" ;;
    OpenBSD*) packageScript="$scriptRoot/scripts/install-openbsd.sh" ;;
    *)
        printf '[FAIL] unsupported operating system: %s\n' "$(uname -s)" >&2
        exit 1
        ;;
esac

# shellcheck source=/dev/null
. "$packageScript"

mode="keep"
case "${1:-}" in
    --keep)  mode="keep" ;;
    --purge) mode="purge" ;;
    "")
        ;;
    *)
        printf '[FAIL] unknown option: %s\n' "$1" >&2
        printf '       Usage: uninstall.sh [--keep|--purge]\n' >&2
        exit 64
        ;;
esac

printf '[info] PREFIX=%s\n' "$PREFIX"
printf '[info] CONFDIR=%s\n' "$CONFDIR"
printf '[info] mode=%s\n' "$mode"

removed=0

remove_file() {
    if [ -e "$1" ] || [ -L "$1" ]; then
        rm -f "$1"
        printf '[ OK ] removed: %s\n' "$1"
        removed=$((removed + 1))
    fi
}

remove_file "$PREFIX/bin/dragonet"
remove_file "$PREFIX/bin/dgn"
remove_file "$PREFIX/share/man/man1/dragonet.1"
remove_file "$PREFIX/lib/dragonet/dragonet.jar"

if [ -d "$PREFIX/lib/dragonet" ]; then
    if rmdir "$PREFIX/lib/dragonet" 2>/dev/null; then
        printf '[ OK ] removed empty dir: %s\n' "$PREFIX/lib/dragonet"
    fi
fi

if [ "$mode" = "purge" ]; then
    remove_file "$CONFDIR/dragonet.conf.default"
    remove_file "$CONFDIR/theme.conf.default"
    if [ -d "$CONFDIR" ]; then
        rmdir "$CONFDIR" 2>/dev/null || true
    fi
    stateDir="$HOME/.local/state/redhot/dragonet"
    if [ -d "$stateDir" ]; then
        rm -rf "$stateDir"
        printf '[ OK ] removed state dir: %s\n' "$stateDir"
    fi
    printf '[info] purge mode: config and state removed\n'
else
    printf '[info] keep mode: config kept at %s\n' "$CONFDIR"
    printf '       To remove config/state later, re-run: ./uninstall.sh --purge\n'
fi

if [ "$removed" -eq 0 ]; then
    printf '[warn] nothing was removed; Dragonet may not be installed here\n' >&2
    exit 1
fi

printf '[ OK ] uninstall complete\n'
