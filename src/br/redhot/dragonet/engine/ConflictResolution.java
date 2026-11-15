package br.redhot.dragonet.engine;

/** What to do when a paste target already exists. */
public enum ConflictResolution {
    OVERWRITE,
    SKIP,
    RENAME,
    ABORT,
    OVERWRITE_ALL,
    SKIP_ALL
}
