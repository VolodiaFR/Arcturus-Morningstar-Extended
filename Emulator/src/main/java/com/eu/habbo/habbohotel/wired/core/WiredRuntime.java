package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import java.util.Objects;

/**
 * Owns the wired engine, stack index, and tick-service lifecycle.
 *
 * <p>{@link WiredManager} remains the static compatibility facade used by legacy plugins and existing
 * first-party callers.
 */
final class WiredRuntime {
    private final WiredEngine engine;
    private final RoomWiredStackIndex stackIndex;
    private final WiredTickService tickService;
    private boolean active;

    WiredRuntime(WiredEngine engine, RoomWiredStackIndex stackIndex, WiredTickService tickService) {
        this.engine = Objects.requireNonNull(engine);
        this.stackIndex = Objects.requireNonNull(stackIndex);
        this.tickService = Objects.requireNonNull(tickService);
    }

    synchronized void start() {
        if (active) {
            return;
        }

        tickService.start();
        active = true;
    }

    synchronized void shutdown() {
        if (!active) {
            return;
        }

        tickService.stop();
        stackIndex.clearAll();
        engine.clearUnseenCache();
        engine.clearAllDiagnostics();
        engine.clearAllExecutionCaches();
        active = false;
    }

    synchronized boolean isActive() {
        return active;
    }

    WiredEngine engine() {
        return engine;
    }

    RoomWiredStackIndex stackIndex() {
        return stackIndex;
    }

    WiredTickService tickService() {
        return tickService;
    }
}
