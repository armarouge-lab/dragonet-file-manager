# OpenBSD packaging configuration for Dragonet.
# Sourced by install.sh. Sets PREFIX, CONFDIR, and defines
# install_dependencies().

if [ "$(id -u)" -eq 0 ]; then
    PREFIX=/usr/local
    CONFDIR=/etc/redhot/dragonet
else
    PREFIX="$HOME/.local"
    CONFDIR="$HOME/.config/redhot/dragonet"
fi

install_dependencies() {
    if ! command -v pkg_add >/dev/null 2>&1; then
        printf '[warn] pkg_add not found; install openjdk17 manually\n' >&2
        return 0
    fi
    if [ "$(id -u)" -eq 0 ]; then
        pkg_add openjdk17 || \
            printf '[warn] pkg_add failed; install openjdk17 manually\n' >&2
    else
        if command -v doas >/dev/null 2>&1; then
            doas pkg_add openjdk17 || \
                printf '[warn] doas pkg_add failed; install openjdk17 manually\n' >&2
        else
            printf '[warn] doas not found; install openjdk17 manually as root\n' >&2
        fi
    fi
}
