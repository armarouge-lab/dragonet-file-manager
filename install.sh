#!/bin/sh
set -eu

scriptRoot=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

case "$(uname -s)" in
    Linux*)   packageScript="$scriptRoot/scripts/install-debian.sh" ;;
    FreeBSD*) packageScript="$scriptRoot/scripts/install-freebsd.sh" ;;
    OpenBSD*) packageScript="$scriptRoot/scripts/install-openbsd.sh" ;;
    *)
        printf '[FAIL] unsupported operating system: %s\n' "$(uname -s)" >&2
        printf '       Supported: Debian, FreeBSD, OpenBSD\n' >&2
        exit 1
        ;;
esac

if [ ! -f "$packageScript" ]; then
    printf '[FAIL] package script not found: %s\n' "$packageScript" >&2
    exit 1
fi

printf '[info] detected OS: %s\n' "$(uname -s)"
printf '[info] using package script: %s\n' "$packageScript"

# shellcheck source=/dev/null
. "$packageScript"

printf '[info] PREFIX=%s\n' "$PREFIX"
printf '[info] CONFDIR=%s\n' "$CONFDIR"

if ! command -v java >/dev/null 2>&1; then
    printf '[info] Java not found; installing dependencies...\n'
    install_dependencies
fi

if ! command -v java >/dev/null 2>&1; then
    printf '[FAIL] java is still missing after dependency install\n' >&2
    printf '       Install openjdk-17 (Debian) or openjdk17 (BSD) manually\n' >&2
    exit 1
fi

printf '[info] java: %s\n' "$(java -version 2>&1 | head -n 1)"

if [ ! -f "$scriptRoot/dragonet.jar" ]; then
    printf '[info] dragonet.jar not found; building...\n'
    ( cd "$scriptRoot" && ./build.sh )
fi

if [ ! -f "$scriptRoot/dragonet.jar" ]; then
    printf '[FAIL] build did not produce dragonet.jar\n' >&2
    exit 1
fi

printf '[info] creating directories...\n'
mkdir -p "$PREFIX/lib/dragonet"
mkdir -p "$PREFIX/bin"
mkdir -p "$PREFIX/share/man/man1"
mkdir -p "$CONFDIR"
chmod 700 "$CONFDIR"

printf '[info] installing jar...\n'
install -m 644 "$scriptRoot/dragonet.jar" "$PREFIX/lib/dragonet/dragonet.jar"

printf '[info] installing resources...\n'
install -m 644 "$scriptRoot/resources/dragonet.conf.default" \
    "$CONFDIR/dragonet.conf.default"
install -m 644 "$scriptRoot/resources/theme.conf.default" \
    "$CONFDIR/theme.conf.default"
install -m 644 "$scriptRoot/resources/dragonet.1" \
    "$PREFIX/share/man/man1/dragonet.1"

printf '[info] installing launcher (dragonet, dgn)...\n'
cat > "$PREFIX/bin/dragonet" <<'LAUNCHER'
#!/bin/sh
case "$0" in
    /*) self="$0" ;;
    *)  self="$PWD/$0" ;;
esac
while [ -L "$self" ]; do
    linkTarget=$(readlink "$self")
    case "$linkTarget" in
        /*) self="$linkTarget" ;;
        *)  self="$(dirname "$self")/$linkTarget" ;;
    esac
done
prefix=$(cd "$(dirname "$self")/.." && pwd)
exec java -jar "$prefix/lib/dragonet/dragonet.jar" "$@"
LAUNCHER
chmod 755 "$PREFIX/bin/dragonet"
ln -sf dragonet "$PREFIX/bin/dgn"

printf '[info] running selftest...\n'
if "$PREFIX/bin/dragonet" --selftest; then
    printf '[ OK ] dragonet installed at %s\n' "$PREFIX"
    printf '       Command: dragonet or dgn\n'
    printf '       Config:  %s/dragonet.conf\n' "$CONFDIR"
    printf '       Manual:  man dragonet\n'
else
    printf '[warn] selftest reported issues; the installation is intact\n' >&2
    printf '       Run "%s/bin/dragonet --selftest" to diagnose\n' "$PREFIX" >&2
    exit 0
fi
