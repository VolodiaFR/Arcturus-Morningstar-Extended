package com.eu.habbo.database.migrations;

import java.util.List;
import java.util.Objects;

public record MigrationReport(
        MigrationMode mode,
        List<Integer> pendingVersions,
        List<Integer> appliedVersions) {
    public MigrationReport {
        mode = Objects.requireNonNull(mode, "mode");
        pendingVersions = List.copyOf(pendingVersions);
        appliedVersions = List.copyOf(appliedVersions);
    }

    public static MigrationReport off() {
        return new MigrationReport(MigrationMode.OFF, List.of(), List.of());
    }
}
