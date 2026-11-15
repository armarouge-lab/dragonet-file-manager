# Dragonet

TUI file manager for the Redhot suite.

- **Language:** Java 17 LTS
- **TUI:** Lanterna 3.1.2
- **Target systems:** Debian, FreeBSD, OpenBSD
- **Command:** `dragonet` (alias `dgn`)

Dragonet navigates the filesystem, organizes files, and dispatches them to other Redhot applications (Imp for media, Devil for text). It runs entirely in the terminal, with no mouse dependency and no graphical stack.

## Installation

Requires Java 17 or newer.

### From source (recommended)

```sh
git clone https://github.com/armarouge-lab/dragonet-file-manager.git
cd dragonet-file-manager
less install.sh
sh install.sh
```

`install.sh` detects the OS, installs the Java runtime if missing, builds `dragonet.jar`, and installs:

- `dragonet` and `dgn` binaries under `$PREFIX/bin`
- The jar under `$PREFIX/lib/dragonet/dragonet.jar`
- The man page under `$PREFIX/share/man/man1/dragonet.1`
- Default config under `$CONFDIR`

When run as root, `$PREFIX` is `/usr/local`. When run as a regular user, `$PREFIX` is `$HOME/.local`.

### Uninstall

```sh
./uninstall.sh           # removes binaries and jar, keeps config
./uninstall.sh --purge   # also removes config and state
```

### Verify

```sh
dragonet --selftest
```

Exit `0` means Java, Lanterna, and the trash directory are all functional. Exit `1` means one of the required checks failed.

## Quick start

Open the TUI in the current directory:

```sh
dgn
```

Open in a specific directory:

```sh
dgn ~/Documents
```

Headless listing:

```sh
dragonet --list ~/Downloads
dragonet --ls-json ~/Downloads
```

## Key bindings

### Navigation

| Key | Action |
| --- | --- |
| `↑` `↓` | Move cursor |
| `Home` `End` | Jump to first / last |
| `PageUp` `PageDown` | Page |
| `Enter` | Open file or enter directory |
| `Backspace` | Parent directory |
| `←` / `→` | Collapse / expand |
| `Tab` | Focus preview pane |

### Operations

| Key | Action |
| --- | --- |
| `Ctrl+C` / `Esc` | Cancel or clear filter |
| `Ctrl+D` | Move to trash |
| `Ctrl+P` | Permanent delete (type `YES`) |
| `Ctrl+R` | Rename |
| `Ctrl+T` | Tag toggle |
| `Ctrl+A` | Tag all in current level |
| `Ctrl+N` | Create new file |
| `Ctrl+O` | Open with custom command |
| `Alt+C` | Copy |
| `Alt+V` | Paste |
| `Alt+X` | Cut |
| `Alt+E` | Edit with `$EDITOR` (bypasses Imp) |
| `Alt+M` | Create directory |
| `Alt+S` | Create symlink |
| `Alt+U` | Undo |

### View

| Key | Action |
| --- | --- |
| `Ctrl+L` | Toggle preview pane |
| `Alt+I` | Cycle metadata level |
| `Alt+H` | Toggle hidden files |
| `Ctrl+W` | Find within current directory |
| `n` / `N` | Next / previous match (while filtering) |

### History and bookmarks

| Key | Action |
| --- | --- |
| `Ctrl+B` / `Alt+←` | Back |
| `Ctrl+F` / `Alt+→` | Forward |
| `Alt+B` | Bookmarks menu |

### Meta

| Key | Action |
| --- | --- |
| `Ctrl+G` | Help |
| `Ctrl+X` | Exit |
| `Alt+Z` | Suspend TUI and open `$SHELL` |

## Command-line reference

```text
dragonet [<path>]                            open the TUI
dragonet --list [<path>]                     list (table)
dragonet --ls-json [<path>]                  list (JSON)
dragonet --cp <src>... <dst>                 copy
dragonet --mv <src>... <dst>                 move
dragonet --rm <path>...                      move to trash
dragonet --rm-permanent --yes <path>...      delete permanently
dragonet --restore <uuid>                    restore from trash
dragonet --trash-list                        list trashed items
dragonet --mkdir <path>...                   create directories
dragonet --touch <path>...                   create empty files
dragonet --dump-config                       print default config
dragonet --selftest                          check environment
dragonet --version                           print version
dragonet --help                              print help
```

Global modifiers: `--debug`, `--dry-run`, `--json`, `--yes`, `--config <path>`.

## Configuration

Configuration lives under `~/.config/redhot/dragonet/dragonet.conf` (user) and `/etc/redhot/dragonet/dragonet.conf` or `/usr/local/etc/redhot/dragonet/dragonet.conf` (system, depending on OS).

To generate a fresh config:

```sh
dragonet --dump-config > ~/.config/redhot/dragonet/dragonet.conf
```

Unknown keys are ignored with a warning. Invalid values abort the app with exit code `4`.

Key highlights:

| Key | Default | Meaning |
| --- | --- | --- |
| `preview` | `off` | Preview pane visibility |
| `show_hidden` | `false` | Show dotfiles |
| `sort` | `name` | Sort key |
| `imp_mode` | `auto` | Imp dispatch (block for text, detach for media) |
| `trash_enabled` | `true` | Move deletes to XDG trash |
| `trash_max_size_gb` | `10` | Trash cap; `0` = unlimited |

## Integration with Imp

When the Imp Media Dispatcher is available on `$PATH`, Dragonet calls `imp <path>` for `Enter` on files. The dispatch mode is chosen automatically:

- **Text files** (`.txt`, `.md`, `.c`, `.json`, …): blocking — the TUI suspends so the editor or pager owns the terminal.
- **Images and binary files**: detached — Imp opens a GUI viewer (`imv`, `zathura`, `mpv`) and the TUI keeps running.

Override with `imp_mode=block` or `imp_mode=detach` in the config.

Without Imp, Dragonet falls back to `$VISUAL` / `$EDITOR` for text and `xdg-open` for everything else.

## Files

| Path | Purpose |
| --- | --- |
| `~/.config/redhot/dragonet/dragonet.conf` | User configuration |
| `~/.config/redhot/dragonet/bookmarks.conf` | Named bookmarks |
| `~/.local/state/redhot/dragonet/session.conf` | Per-session state |
| `~/.local/state/redhot/dragonet/history` | Navigation history (500 entries) |
| `~/.local/state/redhot/dragonet/undo.log` | Undo journal |
| `~/.local/share/Trash/` | XDG trash |

## Building from source

```sh
./build.sh          # produces dragonet.jar
./test.sh           # runs 200+ unit tests
```

`build.sh` uses `javac` and `jar` directly; no Maven, no Gradle.

## Roadmap

- **V1.0.0** (current) — stable release. TUI, headless CLI, Imp integration, undo journal, XDG trash.
- **V1.1** — per-mount trash enumeration, real `EX_CANCELED` on SIGINT, `--trash-empty`, additional themes, plugin-free extensions.

See [`CHANGELOG.md`](CHANGELOG.md) for the full history.

License

BSD-2-Clause. See [LICENSE](LICENSE).