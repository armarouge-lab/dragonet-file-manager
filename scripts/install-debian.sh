# Debian packaging configuration for Dragonet.
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
    if ! command -v apt-get >/dev/null 2>&1; then
        printf '[warn] apt-get not found; install openjdk-17-jdk manually\n' >&2
        return 0
    fi
    if [ "$(id -u)" -eq 0 ]; then
        apt-get install -y openjdk-17-jdk
    else
        if command -v sudo >/dev/null 2>&1; then
            sudo apt-get install -y openjdk-17-jdk || \
                printf '[warn] sudo apt-get failed; install openjdk-17-jdk manually\n' >&2
        else
            printf '[warn] sudo not found; install openjdk-17-jdk manually\n' >&2
        fi
    fi
}
