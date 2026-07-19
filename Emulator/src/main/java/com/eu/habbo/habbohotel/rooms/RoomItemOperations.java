package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionMultiHeight;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveWallItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WallItemUpdateComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurniturePickedUpEvent;

import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Set;

final class RoomItemOperations {

    private final Room room;

    RoomItemOperations(Room room) {
        this.room = room;
    }

    void pickUpItem(HabboItem item, Habbo picker) {
        if (item == null) {
            return;
        }

        boolean trackedBuildersClubItem =
                BuildersClubRoomSupport.isTrackedItem(item.getId());

        if (Emulator.getPluginManager().isRegistered(
                FurniturePickedUpEvent.class,
                true)) {
            Event furniturePickedUpEvent =
                    new FurniturePickedUpEvent(item, picker);
            Emulator.getPluginManager().fireEvent(furniturePickedUpEvent);

            if (furniturePickedUpEvent.isCancelled()) {
                return;
            }
        }

        this.room.removeHabboItem(item.getId());
        item.onPickUp(this.room);
        item.setRoomId(0);
        item.needsUpdate(true);

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.sendComposer(
                    new RemoveFloorItemComposer(item).compose());

            Set<RoomTile> updatedTiles = new HashSet<>();
            Rectangle rectangle = RoomLayout.getRectangle(
                    item.getX(),
                    item.getY(),
                    item.getBaseItem().getWidth(),
                    item.getBaseItem().getLength(),
                    item.getRotation());

            for (short x = (short) rectangle.x;
                 x < rectangle.x + rectangle.getWidth();
                 x++) {
                for (short y = (short) rectangle.y;
                     y < rectangle.y + rectangle.getHeight();
                     y++) {
                    double stackHeight =
                            this.room.getStackHeight(x, y, false);
                    RoomTile tile = this.room.currentLayout().getTile(x, y);

                    if (tile != null) {
                        tile.setStackHeight(stackHeight);
                        updatedTiles.add(tile);
                    }
                }
            }
            this.room.sendComposer(
                    new UpdateStackHeightComposer(
                            this.room,
                            updatedTiles).compose());
            this.room.updateTiles(updatedTiles);
            for (RoomTile tile : updatedTiles) {
                this.room.updateHabbosAt(tile.x, tile.y);
                this.room.updateBotsAt(tile.x, tile.y);
            }
        } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            this.room.sendComposer(
                    new RemoveWallItemComposer(item).compose());
        }

        if (trackedBuildersClubItem) {
            Emulator.getGameEnvironment()
                    .getItemManager()
                    .deleteItem(item);
            return;
        }

        Habbo owner = picker != null
                && picker.getHabboInfo().getId() == item.getUserId()
                ? picker
                : Emulator.getGameServer()
                        .getGameClientManager()
                        .getHabbo(item.getUserId());
        if (owner != null) {
            owner.getInventory().getItemsComponent().addItem(item);
            owner.getClient().sendResponse(
                    new AddHabboItemComposer(item));
            owner.getClient().sendResponse(
                    new InventoryRefreshComposer());
        }
        Emulator.getThreading().run(item);
    }

    void updateItem(HabboItem item) {
        if (!this.room.isLoaded()
                || item == null
                || item.getRoomId() != this.room.getId()
                || item.getBaseItem() == null) {
            return;
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.sendComposer(
                    new FloorItemUpdateComposer(item).compose());
            this.room.updateTiles(this.room.getLayout()
                    .getTilesAt(
                            this.room.currentLayout().getTile(
                                    item.getX(),
                                    item.getY()),
                            item.getBaseItem().getWidth(),
                            item.getBaseItem().getLength(),
                            item.getRotation()));

            if (RoomAreaHideSupport.isControllerItem(item)) {
                RoomAreaHideSupport.sendState(this.room, item);
            }
        } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            this.room.sendComposer(
                    new WallItemUpdateComposer(item).compose());
        }
    }

    void updateItemState(HabboItem item) {
        if (item == null) {
            return;
        }

        if (RoomAreaHideSupport.isControllerItem(item)) {
            this.updateItem(item);
            return;
        }

        if (!item.isLimited()) {
            this.room.sendComposer(new ItemStateComposer(item).compose());
        } else {
            this.room.sendComposer(
                    new FloorItemUpdateComposer(item).compose());
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (this.room.currentLayout() == null) {
                return;
            }

            this.room.updateTiles(this.room.getLayout()
                    .getTilesAt(
                            this.room.currentLayout().getTile(
                                    item.getX(),
                                    item.getY()),
                            item.getBaseItem().getWidth(),
                            item.getBaseItem().getLength(),
                            item.getRotation()));

            if (item instanceof InteractionMultiHeight multiHeight) {
                multiHeight.updateUnitsOnItem(this.room);
            }
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR
                && (RoomConfInvisSupport.isControllerItem(item)
                || RoomConfInvisSupport.isTarget(item))) {
            RoomConfInvisSupport.sendState(this.room);
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR
                && RoomHanditemBlockSupport.isControllerItem(item)) {
            RoomHanditemBlockSupport.sendState(this.room);
        }
    }
}
