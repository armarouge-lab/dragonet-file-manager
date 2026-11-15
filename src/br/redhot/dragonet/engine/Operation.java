package br.redhot.dragonet.engine;

/** A single filesystem operation with a deterministic outcome. */
public interface Operation {
    OperationResult execute();
}