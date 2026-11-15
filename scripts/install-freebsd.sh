# FreeBSD packaging configuration for Dragonet.
# Sourced by install.sh. Sets PREFIX, CONFDIR, and defines
# install_dependencies().

if [ "$(id -u)" -eq 0 ]; then
    PREFIX=/usr/local
    CONFDIR=/usr/local/etc/redhot/dragonet
else
    PREFIX="$HOME/.local"
    CONFDIR="$HOME/.config/redhot/dragonet"
fi

install_dependencies() {
    if ! command -v pkg >/dev/null 2>&1; then
        printf '[warn] pkg not found; install openjdk17 manually\n' >&2
        return 0
    fi
    if [ "$(id -u)" -eq 0 ]; then
        pkg install -y openjdk17
    else
        if command -v doas >/dev/null 2>&1; then
            doas pkg install -y openjdk17 || \
                printf '[warn] doas pkg failed; install openjdk17 manually\n' >&2
        elif command -v sudo >/dev/null 2>&1; then
            sudo pkg install -y openjdk17 || \
                printf '[warn] sudo pkg failed; install openjdk17 manually\n' >&2
        else
            printf '[warn] no doas/sudo; install openjdk17 manually\n' >&2
        fi
    fi
}
