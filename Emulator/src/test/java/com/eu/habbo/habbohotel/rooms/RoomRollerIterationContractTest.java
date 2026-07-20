package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomRollerIterationContractTest {

    @Test
    void firstPartyCycleUsesTheLockedRollerSnapshot() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomRollerManager.java"));

        assertTrue(source.contains("rollerSnapshot().values()"));
        assertFalse(source.contains("getRollers().values()"));
    }
}
