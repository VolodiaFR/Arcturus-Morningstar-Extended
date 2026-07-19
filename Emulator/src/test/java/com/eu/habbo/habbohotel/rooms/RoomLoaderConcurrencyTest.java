package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomLoaderConcurrencyTest {

    private ThreadPooling originalThreading;
    private PluginManager originalPluginManager;
    private ThreadPooling threading;
    private RoomJdbcTestSupport.InstalledDatabase database;
    private RoomJdbcTestSupport.RecordingDataSource dataSource;

    @BeforeEach
    void installRuntimeBoundaries() throws Exception {
        this.dataSource = new RoomJdbcTestSupport.RecordingDataSource();
        this.database = RoomJdbcTestSupport.install(this.dataSource);
        this.originalThreading = (ThreadPooling) setEmulatorField(
                "threading",
                this.threading = new ThreadPooling(1));
        this.originalPluginManager = (PluginManager) setEmulatorField(
                "pluginManager",
                new EmptyPluginManager());
    }

    @AfterEach
    void restoreRuntimeBoundaries() throws Exception {
        ((ScheduledThreadPoolExecutor) this.threading.getService())
                .setCorePoolSize(8);
        this.threading.shutDown();
        setEmulatorField("threading", this.originalThreading);
        setEmulatorField("pluginManager", this.originalPluginManager);
        this.database.close();
    }

    @Test
    void backgroundLoadCompletesOnTheSameSingleWorkerExecutor() throws Exception {
        Room room = loadableRoom();

        room.startBackgroundLoad();

        try {
            assertTrue(
                    await(room::isLoaded, Duration.ofSeconds(1)),
                    "room load starved while its only worker waited for child tasks");
        } finally {
            ((ScheduledThreadPoolExecutor) this.threading.getService())
                    .setCorePoolSize(8);
            await(() -> !room.isLoadingInProgress(), Duration.ofSeconds(2));
            room.quiesceCycleTask();
        }
    }

    @Test
    void promotionRemainsVisibleAndBlocksRoomPublicationUntilItsQueryCompletes()
            throws Exception {
        CountDownLatch promotionQueryReached = new CountDownLatch(1);
        CountDownLatch allowPromotionQuery = new CountDownLatch(1);
        this.dataSource.rows(sql -> {
            if (sql.contains("FROM room_promotions")) {
                promotionQueryReached.countDown();
                try {
                    assertTrue(allowPromotionQuery.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return List.of();
        });
        Room room = loadableRoom();
        setRoomField(room, "promoted", true);

        room.startBackgroundLoad();

        try {
            assertTrue(promotionQueryReached.await(1, TimeUnit.SECONDS));
            assertTrue(room.getPromotionManager().getPromotedFlag());
            assertFalse(room.isLoaded());

            allowPromotionQuery.countDown();

            assertTrue(await(room::isLoaded, Duration.ofSeconds(2)));
            assertFalse(room.getPromotionManager().getPromotedFlag());
        } finally {
            allowPromotionQuery.countDown();
            finishLoad(room);
        }
    }

    @Test
    void childQueryFailuresStillCompleteTheLegacyLoadAttempt() throws Exception {
        this.dataSource.failQueries(true);
        Room room = loadableRoom();

        room.startBackgroundLoad();

        try {
            assertTrue(await(room::isLoaded, Duration.ofSeconds(2)));
        } finally {
            finishLoad(room);
        }
    }

    private Room loadableRoom() throws Exception {
        Room room = new Room(41, 7);
        setRoomField(room, "preLoaded", true);
        return room;
    }

    private void finishLoad(Room room) throws InterruptedException {
        ((ScheduledThreadPoolExecutor) this.threading.getService())
                .setCorePoolSize(8);
        await(() -> !room.isLoadingInProgress(), Duration.ofSeconds(2));
        room.quiesceCycleTask();
    }

    private static boolean await(
            BooleanSupplier condition,
            Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static Object setEmulatorField(String name, Object value) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        Object original = field.get(null);
        field.set(null, value);
        return original;
    }

    private static void setRoomField(Room room, String name, Object value) throws Exception {
        Field field = Room.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(room, value);
    }

    private static final class EmptyPluginManager extends PluginManager {
        @Override
        public <T extends Event> T fireEvent(T event) {
            return event;
        }
    }
}
