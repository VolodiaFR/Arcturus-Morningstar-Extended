package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WiredManagerLifecycleCompatibilityTest {

    @AfterEach
    void resetManagerState() throws ReflectiveOperationException {
        setStaticField("initialized", false);
        setStaticField("engine", null);
        setStaticField("stackIndex", null);
    }

    @Test
    void shutdownClearsOwnedStateAndRetainsLegacyGetterIdentity() throws ReflectiveOperationException {
        WiredEngine engine = mock(WiredEngine.class);
        RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
        setStaticField("engine", engine);
        setStaticField("stackIndex", stackIndex);
        setStaticField("initialized", true);

        WiredManager.shutdown();

        assertFalse(WiredManager.isEnabled());
        assertSame(engine, WiredManager.getEngine());
        assertSame(stackIndex, WiredManager.getStackIndex());
        verify(stackIndex).clearAll();
        verify(engine).clearUnseenCache();
        verify(engine).clearAllDiagnostics();
        verify(engine).clearAllExecutionCaches();
    }

    private static void setStaticField(String name, Object value) throws ReflectiveOperationException {
        Field field = WiredManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
