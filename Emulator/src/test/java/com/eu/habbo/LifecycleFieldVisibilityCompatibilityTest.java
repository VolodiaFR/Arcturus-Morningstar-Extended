package com.eu.habbo;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LifecycleFieldVisibilityCompatibilityTest {

    @Test
    void publicLifecycleFlagsRemainFieldsAndPublishAcrossThreads()
            throws Exception {
        assertVolatilePublicField(Emulator.class, "isReady");
        assertVolatilePublicField(Emulator.class, "isShuttingDown");
        assertVolatilePublicField(Emulator.class, "stopped");
    }

    @Test
    void lifecycleFlagsRemainDirectlyWritable() {
        boolean ready = Emulator.isReady;
        boolean shuttingDown = Emulator.isShuttingDown;
        boolean stopped = Emulator.stopped;
        try {
            Emulator.isReady = !ready;
            Emulator.isShuttingDown = !shuttingDown;
            Emulator.stopped = !stopped;

            assertEquals(!ready, Emulator.isReady);
            assertEquals(
                    !shuttingDown, Emulator.isShuttingDown);
            assertEquals(!stopped, Emulator.stopped);
        } finally {
            Emulator.isReady = ready;
            Emulator.isShuttingDown = shuttingDown;
            Emulator.stopped = stopped;
        }
    }

    @Test
    void roomLifecycleReferencesPublishAcrossThreads() throws Exception {
        assertVolatileField(Room.class, "layout");
        assertVolatileField(Room.class, "roomSpecialTypes");
        assertPrivateMutableField("layout", RoomLayout.class);
        assertPrivateMutableField(
                "roomSpecialTypes", RoomSpecialTypes.class);
    }

    private static void assertVolatilePublicField(
            Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertFalse(Modifier.isFinal(field.getModifiers()));
        assertEquals(boolean.class, field.getType());
        assertTrue(Modifier.isVolatile(field.getModifiers()));
    }

    private static void assertVolatileField(
            Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        assertTrue(Modifier.isVolatile(field.getModifiers()));
    }

    private static void assertPrivateMutableField(
            String name, Class<?> type) throws Exception {
        Field field = Room.class.getDeclaredField(name);
        int modifiers = field.getModifiers();
        assertEquals(type, field.getType());
        assertTrue(Modifier.isPrivate(modifiers));
        assertFalse(Modifier.isStatic(modifiers));
        assertFalse(Modifier.isFinal(modifiers));
    }
}
