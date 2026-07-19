package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomOwnerIndexTest {

    @Test
    void firstPartyRegistrationCachesTheSameRoomAndTracksItsOwner() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(41);
        when(room.getOwnerId()).thenReturn(7);

        manager.registerActiveRoom(room);

        assertSame(room, activeRooms(manager).get(41));
        assertEquals(Set.of(41), roomsByOwner(manager).get(7));
    }

    @Test
    void unloadCandidatesUseTheOwnerIndexAndExcludeOtherOwners() {
        RoomManager manager = new RoomManager(false);
        Room ownedRoom = eligibleRoom(41, 7);
        Room otherRoom = eligibleRoom(42, 8);
        manager.registerActiveRoom(ownedRoom);
        manager.registerActiveRoom(otherRoom);
        clearInvocations(ownedRoom, otherRoom);

        assertEquals(Set.of(ownedRoom), new HashSet<>(manager.roomsToUnloadForOwner(7)));
        verify(ownedRoom).isPublicRoom();
        verify(otherRoom, never()).isPublicRoom();
    }

    @Test
    void unloadCandidatesFindAndReindexRoomsWhosePublicOwnerWasChanged() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room room = eligibleRoom(41, 8);
        Room existingRoom = eligibleRoom(42, 7);
        manager.registerActiveRoom(room);
        manager.registerActiveRoom(existingRoom);

        room.setOwnerId(7);

        List<Room> candidates = manager.roomsToUnloadForOwner(7);

        assertEquals(Set.of(room, existingRoom), new HashSet<>(candidates));
        assertFalse(roomsByOwner(manager).getOrDefault(8, Set.of()).contains(41));
        assertEquals(Set.of(41, 42), roomsByOwner(manager).get(7));
    }

    @Test
    void unloadCandidatesRetainTheLegacyScanAsAFallback() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room room = eligibleRoom(41, 7);
        activeRooms(manager).put(room.getId(), room);

        assertEquals(List.of(room), manager.roomsToUnloadForOwner(7));
        assertEquals(Set.of(41), roomsByOwner(manager).get(7));
    }

    @Test
    void unloadCandidatesPreserveAllLegacyEligibilityGuards() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room publicRoom = eligibleRoom(41, 7);
        Room promotedRoom = eligibleRoom(42, 7);
        Room occupiedRoom = eligibleRoom(43, 7);
        Room publicCategoryRoom = eligibleRoom(44, 7);
        doReturn(true).when(publicRoom).isPublicRoom();
        doReturn(true).when(promotedRoom).isStaffPromotedRoom();
        doReturn(1).when(occupiedRoom).getUserCount();
        doReturn(9).when(publicCategoryRoom).getCategory();

        RoomCategory publicCategory = mock(RoomCategory.class);
        when(publicCategory.isPublic()).thenReturn(true);
        roomCategories(manager).put(9, publicCategory);

        manager.registerActiveRoom(publicRoom);
        manager.registerActiveRoom(promotedRoom);
        manager.registerActiveRoom(occupiedRoom);
        manager.registerActiveRoom(publicCategoryRoom);

        assertTrue(manager.roomsToUnloadForOwner(7).isEmpty());
    }

    @Test
    void unloadKeepsCancellationAndDisposalOrdering() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/rooms/RoomManager.java"));
        int unload = source.indexOf("public void unloadRoomsForHabbo");
        int selection = source.indexOf("roomsToUnloadForOwner(ownerId)", unload);
        int callback = source.indexOf("fireEvent(new RoomUncachedEvent(room))", selection);
        int disposal = source.indexOf("room.dispose()", callback);
        int untrack = source.indexOf("this.untrackRoomOwner(room)", disposal);
        int removal = source.indexOf("this.activeRooms.remove(room.getId())", untrack);

        assertTrue(unload >= 0 && unload < selection);
        assertTrue(selection < callback);
        assertTrue(callback < disposal);
        assertTrue(disposal < untrack);
        assertTrue(untrack < removal);
    }

    private static Room eligibleRoom(int id, int ownerId) {
        Room room = mock(Room.class, CALLS_REAL_METHODS);
        doReturn(id).when(room).getId();
        room.setOwnerId(ownerId);
        doReturn(false).when(room).isPublicRoom();
        doReturn(false).when(room).isStaffPromotedRoom();
        doReturn(0).when(room).getUserCount();
        doReturn(0).when(room).getCategory();
        return room;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Room> activeRooms(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("activeRooms");
        field.setAccessible(true);
        return (Map<Integer, Room>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, RoomCategory> roomCategories(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("roomCategories");
        field.setAccessible(true);
        return (Map<Integer, RoomCategory>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Set<Integer>> roomsByOwner(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("roomsByOwner");
        field.setAccessible(true);
        return (Map<Integer, Set<Integer>>) field.get(manager);
    }
}
