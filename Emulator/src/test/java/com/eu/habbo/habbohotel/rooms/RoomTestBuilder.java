package com.eu.habbo.habbohotel.rooms;

import java.lang.reflect.Field;

final class RoomTestBuilder {

    private final Room room;

    private RoomTestBuilder(int id, int ownerId) {
        this.room = new Room(id, ownerId);
    }

    static RoomTestBuilder room(int id, int ownerId) {
        return new RoomTestBuilder(id, ownerId);
    }

    RoomTestBuilder field(String name, Object value) {
        try {
            Field field = Room.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this.room, value);
            return this;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unknown Room field " + name, exception);
        }
    }

    Room build() {
        return this.room;
    }
}
