package br.redhot.dragonet.trash;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** One entry in the XDG trash. */
public record TrashedItem(
        String uuid,
        Path originalPath,
        Instant deletionDate,
        Path trashFilePath,
        Path trashInfoPath
) {

    public TrashedItem {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(originalPath, "originalPath");
        Objects.requireNonNull(deletionDate, "deletionDate");
        Objects.requireNonNull(trashFilePath, "trashFilePath");
        Objects.requireNonNull(trashInfoPath, "trashInfoPath");
    }
}