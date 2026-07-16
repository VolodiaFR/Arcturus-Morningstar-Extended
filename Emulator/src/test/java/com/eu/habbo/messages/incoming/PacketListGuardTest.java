package com.eu.habbo.messages.incoming;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketListGuardTest {
    @Test
    void acceptsCountsThatFitTheConfiguredAndPacketBounds() {
        assertTrue(PacketListGuard.isValidIntList(0, 0, 50));
        assertTrue(PacketListGuard.isValidIntList(50, 200, 50));
    }

    @Test
    void rejectsNegativeOrOversizedCountsBeforeAllocation() {
        assertFalse(PacketListGuard.isValidIntList(-1, 0, 50));
        assertFalse(PacketListGuard.isValidIntList(Integer.MAX_VALUE, 0, 1_000));
        assertFalse(PacketListGuard.isValidIntList(1_001, 4_004, 1_000));
    }

    @Test
    void rejectsCountsThatExceedTheRemainingPacketBytes() {
        assertFalse(PacketListGuard.isValidIntList(2, 4, 50));
    }
}
