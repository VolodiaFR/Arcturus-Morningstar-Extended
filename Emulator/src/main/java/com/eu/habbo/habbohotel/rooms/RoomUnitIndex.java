package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.Habbo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.concurrent.ConcurrentHashMap;

final class RoomUnitIndex {
    private final ConcurrentHashMap<Integer, Habbo> habbos = new ConcurrentHashMap<>(3);
    private final Int2ObjectMap<Habbo> queue =
            Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private final Int2ObjectMap<Bot> bots =
            Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private final Int2ObjectMap<Pet> pets =
            Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private volatile int unitCounter;

    ConcurrentHashMap<Integer, Habbo> habbos() {
        return habbos;
    }

    Int2ObjectMap<Habbo> queue() {
        return queue;
    }

    Int2ObjectMap<Bot> bots() {
        return bots;
    }

    Int2ObjectMap<Pet> pets() {
        return pets;
    }

    int unitCounter() {
        return unitCounter;
    }

    int nextUnitId() {
        return unitCounter++;
    }

    void incrementUnitId() {
        unitCounter++;
    }

    void resetUnitCounter() {
        unitCounter = 0;
    }
}
