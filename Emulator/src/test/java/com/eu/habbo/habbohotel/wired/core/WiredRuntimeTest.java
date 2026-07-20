package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WiredRuntimeTest {

    @Test
    void ownsStartupAndOrderedShutdown() {
        WiredEngine engine = mock(WiredEngine.class);
        RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
        WiredTickService tickService = mock(WiredTickService.class);
        WiredRuntime runtime = new WiredRuntime(engine, stackIndex, tickService);

        runtime.start();
        assertTrue(runtime.isActive());

        runtime.shutdown();
        assertFalse(runtime.isActive());

        InOrder shutdownOrder = inOrder(tickService, stackIndex, engine);
        shutdownOrder.verify(tickService).start();
        shutdownOrder.verify(tickService).stop();
        shutdownOrder.verify(stackIndex).clearAll();
        shutdownOrder.verify(engine).clearUnseenCache();
        shutdownOrder.verify(engine).clearAllDiagnostics();
        shutdownOrder.verify(engine).clearAllExecutionCaches();
    }

    @Test
    void lifecycleOperationsAreIdempotent() {
        WiredEngine engine = mock(WiredEngine.class);
        RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
        WiredTickService tickService = mock(WiredTickService.class);
        WiredRuntime runtime = new WiredRuntime(engine, stackIndex, tickService);

        runtime.start();
        runtime.start();
        runtime.shutdown();
        runtime.shutdown();

        verify(tickService, times(1)).start();
        verify(tickService, times(1)).stop();
        verify(stackIndex, times(1)).clearAll();
    }
}
