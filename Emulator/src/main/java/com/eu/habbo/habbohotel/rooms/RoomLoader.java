package com.eu.habbo.habbohotel.rooms;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

final class RoomLoader {

    private final Operations operations;
    private final Supplier<Executor> workerExecutor;

    RoomLoader(
            Operations operations,
            Supplier<Executor> workerExecutor) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.workerExecutor = Objects.requireNonNull(
                workerExecutor,
                "workerExecutor");
    }

    void load(long generation) {
        if (!this.operations.prepare(generation)) {
            return;
        }

        try {
            this.operations.initialize();
            this.operations.loadLayout();

            Executor executor = this.workerExecutor.get();
            CompletableFuture<Void> promotion = this.operations.shouldLoadPromotion()
                    ? run(this.operations::loadPromotion, executor)
                    : CompletableFuture.completedFuture(null);
            CompletableFuture<Void> items =
                    run(this.operations::loadItems, executor);
            CompletableFuture<Void> rights =
                    run(this.operations::loadRights, executor);
            CompletableFuture<Void> wordFilter =
                    run(this.operations::loadWordFilter, executor);
            CompletableFuture<Void> bots =
                    run(this.operations::loadBots, executor);
            CompletableFuture<Void> pets =
                    run(this.operations::loadPets, executor);

            try {
                items.join();
            } catch (Exception exception) {
                this.operations.reportFailure(
                        "Error waiting for items to load",
                        exception);
            }

            CompletableFuture<Void> heightmap =
                    run(this.operations::loadHeightmap, executor);
            CompletableFuture<Void> wired =
                    run(this.operations::loadWiredData, executor);

            try {
                CompletableFuture.allOf(
                        promotion,
                        rights,
                        wordFilter,
                        bots,
                        pets,
                        heightmap,
                        wired).join();
            } catch (Exception exception) {
                this.operations.reportFailure(
                        "Error waiting for parallel room data loading",
                        exception);
            }

            this.operations.resetIdleCycles();
        } catch (Exception exception) {
            this.operations.reportFailure(
                    "Caught exception during room load",
                    exception);
        }

        this.operations.finish(generation);
    }

    private static CompletableFuture<Void> run(
            Runnable operation,
            Executor executor) {
        return CompletableFuture.runAsync(operation, executor);
    }

    interface Operations {
        boolean prepare(long generation);

        void initialize();

        void loadLayout();

        boolean shouldLoadPromotion();

        void loadPromotion();

        void loadItems();

        void loadRights();

        void loadWordFilter();

        void loadBots();

        void loadPets();

        void loadHeightmap();

        void loadWiredData();

        void resetIdleCycles();

        void finish(long generation);

        void reportFailure(String message, Exception exception);
    }
}
