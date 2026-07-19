package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.items.interactions.pets.*;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.*;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.messages.outgoing.rooms.items.*;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.*;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all items/furniture within a room.
 * Handles loading, adding, removing, querying, and picking up items.
 */
public class RoomItemManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomItemManager.class);

    private final Room room;
    private final RoomItemIndex index;
    private final RoomItemOperations operations;
    private final RoomItemOwnershipService ownership;
    private final RoomItemPlacementService placement;
    private final RoomItemRegistry registry;

    // Tile cache for item lookups
    public final ConcurrentHashMap<RoomTile, Set<HabboItem>> tileCache;

    public RoomItemManager(Room room) {
        this.room = room;
        this.index = new RoomItemIndex(room);
        this.operations = new RoomItemOperations(room);
        this.registry = new RoomItemRegistry(room);
        this.ownership =
                new RoomItemOwnershipService(
                        room,
                        this.index,
                        this.registry);
        this.placement =
                new RoomItemPlacementService(
                        room,
                        this.index,
                        this);
        this.tileCache = this.index.tileCache();
    }

    // ==================== LOADING ====================

    /**
     * Loads items from the database.
     */
    public void loadItems(Connection connection) {
        synchronized (this.index.items()) {
            this.index.items().clear();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM items WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.addHabboItem(Emulator.getGameEnvironment().getItemManager().loadHabboItem(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        if (this.itemCount() > Room.MAXIMUM_FURNI) {
            LOGGER.error("Room ID: {} has exceeded the furniture limit ({} > {}).",
                    this.room.getId(), this.itemCount(), Room.MAXIMUM_FURNI);
        }
    }

    /**
     * Loads wired data for items.
     */
    public void loadWiredData(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, wired_data FROM items WHERE room_id = ? AND wired_data<>''")) {
            statement.setInt(1, this.room.getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        HabboItem item = this.getHabboItem(set.getInt("id"));

                        if (item instanceof InteractionWired) {
                            ((InteractionWired) item).loadWiredData(set, this.room);
                        }
                    } catch (SQLException e) {
                        LOGGER.error("Caught SQL exception", e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    // ==================== ITEM RETRIEVAL ====================

    /**
     * Gets an item by ID.
     */
    public HabboItem getHabboItem(int id) {
        return this.index.get(id);
    }

    /**
     * Gets the total item count.
     */
    public int itemCount() {
        return this.index.size();
    }

    /**
     * Gets all floor items.
     */
    public Set<HabboItem> getFloorItems() {
        return this.index.floorItems();
    }

    /**
     * Gets all wall items.
     */
    public Set<HabboItem> getWallItems() {
        return this.index.wallItems();
    }

    /**
     * Gets all post-it notes.
     */
    public Set<HabboItem> getPostItNotes() {
        return this.index.postItNotes();
    }

    /**
     * Gets the room items map.
     */
    public Int2ObjectMap<HabboItem> getRoomItems() {
        return this.index.items();
    }

    // ==================== ITEM POSITION QUERIES ====================

    /**
     * Gets items at a position (deprecated version using int).
     */
    @Deprecated
    public Set<HabboItem> getItemsAt(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            return this.getItemsAt(tile);
        }

        return new HashSet<>(0);
    }

    /**
     * Gets items at a tile.
     */
    public Set<HabboItem> getItemsAt(RoomTile tile) {
        return getItemsAt(tile, false);
    }

    /**
     * Gets items at a tile with option to return on first match.
     */
    public Set<HabboItem> getItemsAt(RoomTile tile, boolean returnOnFirst) {
        return this.index.itemsAt(tile, returnOnFirst);
    }

    /**
     * Gets items at a position above a minimum Z height.
     */
    public Set<HabboItem> getItemsAt(int x, int y, double minZ) {
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (item.getZ() < minZ) {
                continue;
            }

            items.add(item);
        }
        return items;
    }

    /**
     * Gets items of a specific type at a position.
     */
    public Set<HabboItem> getItemsAt(Class<? extends HabboItem> type, int x, int y) {
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (!item.getClass().equals(type)) {
                continue;
            }

            items.add(item);
        }
        return items;
    }

    /**
     * Checks if there are items at a position.
     */
    public boolean hasItemsAt(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile == null) {
            return false;
        }

        return this.getItemsAt(tile, true).size() > 0;
    }

    /**
     * Gets the top item at a position.
     */
    public HabboItem getTopItemAt(int x, int y) {
        return this.getTopItemAt(x, y, null);
    }

    /**
     * Gets the top item at a position excluding a specific item.
     */
    public HabboItem getTopItemAt(int x, int y, HabboItem exclude) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile == null) {
            return null;
        }

        HabboItem highestItem = null;

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (exclude != null && exclude == item) {
                continue;
            }

            if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                    > item.getZ() + Item.getCurrentHeight(item)) {
                continue;
            }

            highestItem = item;
        }

        return highestItem;
    }

    /**
     * Gets the top walkable item at a position, considering underpass.
     * If the topmost item is elevated enough to walk under, returns the highest item at walk surface level instead.
     */
    public HabboItem getWalkableItemAt(int x, int y) {
        HabboItem topItem = this.getTopItemAt(x, y);
        if (topItem == null) {
            return null;
        }

        // If underpass is disabled for this room, just return the top item
        if (!this.room.isAllowUnderpass()) {
            return topItem;
        }

        // If the top item is walkable, just return it
        if (topItem.isWalkable() || topItem.getBaseItem().allowWalk() || topItem.getBaseItem().allowSit() || topItem.getBaseItem().allowLay()) {
            return topItem;
        }

        // Check for underpass: get the walk surface height
        double walkSurface = this.room.getLayout() != null ? this.room.getLayout().getHeightAtSquare(x, y) : 0;
        HabboItem walkSurfaceItem = null;

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (item.isWalkable() || item.getBaseItem().allowWalk() || item.getBaseItem().allowSit() || item.getBaseItem().allowLay()) {
                double itemTop = item.getZ() + Item.getCurrentHeight(item);
                if (itemTop > walkSurface) {
                    walkSurface = itemTop;
                    walkSurfaceItem = item;
                }
            }
        }

        // If there's enough clearance under the top blocking item, return the walk surface item
        if (topItem.getZ() - walkSurface >= RoomLayout.UNDERPASS_HEIGHT) {
            return walkSurfaceItem;
        }

        return topItem;
    }

    /**
     * Gets the top item from a set of tiles.
     */
    public HabboItem getTopItemAt(Set<RoomTile> tiles, HabboItem exclude) {
        HabboItem highestItem = null;
        for (RoomTile tile : tiles) {

            if (tile == null) {
                continue;
            }

            for (HabboItem item : this.getItemsAt(tile.x, tile.y)) {
                if (exclude != null && exclude == item) {
                    continue;
                }

                if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                        > item.getZ() + Item.getCurrentHeight(item)) {
                    continue;
                }

                highestItem = item;
            }
        }

        return highestItem;
    }

    /**
     * Gets the top height at a position including items.
     */
    public double getTopHeightAt(int x, int y) {
        HabboItem item = this.getTopItemAt(x, y);

        if (item != null) {
            return (item.getZ() + Item.getCurrentHeight(item) - (item.getBaseItem().allowSit() ? 1 : 0));
        } else {
            return this.room.getLayout().getHeightAtSquare(x, y);
        }
    }

    /**
     * Gets the lowest chair at a position.
     */
    @Deprecated
    public HabboItem getLowestChair(int x, int y) {
        if (this.room.getLayout() == null) {
            return null;
        }

        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            return this.getLowestChair(tile);
        }

        return null;
    }

    /**
     * Gets the lowest chair at a tile.
     */
    public HabboItem getLowestChair(RoomTile tile) {
        HabboItem lowestChair = null;

        Set<HabboItem> items = this.getItemsAt(tile);
        if (items != null && !items.isEmpty()) {
            for (HabboItem item : items) {

                if (!item.getBaseItem().allowSit()) {
                    continue;
                }

                if (lowestChair != null && lowestChair.getZ() < item.getZ()) {
                    continue;
                }

                lowestChair = item;
            }
        }

        return lowestChair;
    }

    /**
     * Gets the tallest chair at a tile.
     */
    public HabboItem getTallestChair(RoomTile tile) {
        HabboItem lowestChair = null;

        Set<HabboItem> items = this.getItemsAt(tile);
        if (items != null && !items.isEmpty()) {
            for (HabboItem item : items) {

                if (!item.getBaseItem().allowSit()) {
                    continue;
                }

                if (lowestChair != null && lowestChair.getZ() + Item.getCurrentHeight(lowestChair)
                        > item.getZ() + Item.getCurrentHeight(item)) {
                    continue;
                }

                lowestChair = item;
            }
        }

        return lowestChair;
    }

    // ==================== ITEM MANIPULATION ====================

    /**
     * Adds an item to the room.
     */
    public void addHabboItem(HabboItem item) {
        this.ownership.add(item);
    }

    /**
     * Removes an item by ID.
     */
    public void removeHabboItem(int id) {
        this.removeHabboItem(this.getHabboItem(id));
    }

    /**
     * Removes an item from the room.
     */
    public void removeHabboItem(HabboItem item) {
        this.ownership.remove(item);
    }

    // ==================== ITEM UPDATES ====================

    /**
     * Updates an item's display.
     */
    public void updateItem(HabboItem item) {
        this.operations.updateItem(item);
    }

    /**
     * Updates an item's state.
     */
    public void updateItemState(HabboItem item) {
        this.operations.updateItemState(item);
    }

    // ==================== FURNITURE OWNER MANAGEMENT ====================

    /**
     * Gets furniture owner names map.
     */
    public Int2ObjectMap<String> getFurniOwnerNames() {
        return this.index.ownerNames();
    }

    /**
     * Gets furniture owner count map.
     */
    public Int2IntMap getFurniOwnerCount() {
        return this.index.ownerCounts();
    }

    /**
     * Gets the username for a furniture owner.
     */
    public String getFurniOwnerName(int oduserId) {
        return this.index.ownerNames().get(oduserId);
    }

    /**
     * Gets the furniture count for a user.
     */
    public int getUserFurniCount(int userId) {
        return this.index.ownerCounts().get(userId);
    }

    /**
     * Gets the unique furniture count for a user.
     */
    public int getUserUniqueFurniCount(int userId) {
        return this.ownership.uniqueItemCount(userId);
    }

    // ==================== PICKUP AND EJECT ====================

    /**
     * Picks up an item from the room.
     */
    public void pickUpItem(HabboItem item, Habbo picker) {
        this.ownership.pickUp(item, picker);
    }

    /**
     * Ejects all furniture belonging to a user.
     */
    public void ejectUserFurni(int userId) {
        this.ownership.ejectUserFurniture(userId);
    }

    /**
     * Ejects a single user item.
     */
    public void ejectUserItem(HabboItem item) {
        this.ownership.pickUp(item, null);
    }

    /**
     * Ejects all items from the room.
     */
    public void ejectAll() {
        this.ejectAll(null);
    }

    /**
     * Ejects all items from the room except those belonging to the specified Habbo.
     */
    public void ejectAll(Habbo habbo) {
        this.ownership.ejectAll(habbo);
    }

    // ==================== LOCKED TILES ====================

    /**
     * Gets all tiles that are locked by furniture.
     */
    public Set<RoomTile> getLockedTiles() {
        Set<RoomTile> lockedTiles = new HashSet<>();

        synchronized (this.index.items()) {
            for (HabboItem item : this.index.items().values()) {
                if (item.getBaseItem().getType() != FurnitureType.FLOOR) {
                    continue;
                }

                boolean found = false;
                for (RoomTile tile : lockedTiles) {
                    if (tile.x == item.getX() && tile.y == item.getY()) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    if (item.getRotation() == 0 || item.getRotation() == 4) {
                        for (short y = 0; y < item.getBaseItem().getLength(); y++) {
                            for (short x = 0; x < item.getBaseItem().getWidth(); x++) {
                                RoomTile tile = this.room.getLayout().getTile(
                                        (short) (item.getX() + x), (short) (item.getY() + y));

                                if (tile != null) {
                                    lockedTiles.add(tile);
                                }
                            }
                        }
                    } else {
                        for (short y = 0; y < item.getBaseItem().getWidth(); y++) {
                            for (short x = 0; x < item.getBaseItem().getLength(); x++) {
                                RoomTile tile = this.room.getLayout().getTile(
                                        (short) (item.getX() + x), (short) (item.getY() + y));

                                if (tile != null) {
                                    lockedTiles.add(tile);
                                }
                            }
                        }
                    }
                }
            }
        }

        return lockedTiles;
    }

    // ==================== DISPOSAL ====================

    /**
     * Saves all items that need updates to the database.
     */
    public void saveAllPendingItems() {
        List<HabboItem> pendingItems;
        synchronized (this.index.items()) {
            pendingItems = this.index.items().values().stream()
                    .filter(HabboItem::needsUpdate)
                    .toList();
        }

        this.room.savePendingItems(pendingItems);
    }

    /**
     * Clears the item manager state.
     */
    public void clear() {
        this.index.clear();
    }

    /**
     * Disposes the item manager.
     */
    public void dispose() {
        this.clear();
    }

    // ==================== FURNITURE PLACEMENT ====================

    /**
     * Checks if an item has a certain object type at a position.
     */
    public boolean hasObjectTypeAt(Class<?> type, int x, int y) {
        return this.placement.hasObjectTypeAt(type, x, y);
    }

    /**
     * Checks if furniture can be placed at a position.
     */
    public FurnitureMovementError canPlaceFurnitureAt(HabboItem item, Habbo habbo, RoomTile tile, int rotation) {
        return this.placement.canPlaceFurnitureAt(item, habbo, tile, rotation);
    }

    /**
     * Checks if furniture fits at a location.
     */
    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation) {
        return furnitureFitsAt(tile, item, rotation, true);
    }

    /**
     * Checks if furniture fits at a location with unit check option.
     */
    private boolean isStackPlacementBypassItem(HabboItem item) {
        return this.placement.isStackPlacementBypassItem(item);
    }

    private boolean shouldPinStackHelperToFloor(HabboItem item) {
        return this.placement.shouldPinStackHelperToFloor(item);
    }

    private HabboItem findStackHeightHelperAt(RoomTile tile, HabboItem exclude) {
        return this.placement.findStackHeightHelperAt(tile, exclude);
    }

    private double resolveStackWalkHelperHeight(HabboItem item, RoomTile tile, Set<RoomTile> occupiedTiles) {
        return this.placement.resolveStackWalkHelperHeight(item, tile, occupiedTiles);
    }

    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits) {
        return this.placement.furnitureFitsAt(tile, item, rotation, checkForUnits);
    }

    public FurnitureMovementError furnitureFitsAtWithPhysics(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits, WiredMovementPhysics physics) {
        if (physics == null || !physics.isActive()) {
            return furnitureFitsAt(tile, item, rotation, checkForUnits);
        }

        RoomLayout layout = this.room.getLayout();
        if (!layout.fitsOnMap(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation)) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        if (this.isStackPlacementBypassItem(item)) {
            return FurnitureMovementError.NONE;
        }

        Set<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);
        for (RoomTile t : occupiedTiles) {
            if (t.state == RoomTileState.INVALID) {
                return FurnitureMovementError.INVALID_MOVE;
            }

            if (shouldCheckUnits(item, checkForUnits)) {
                FurnitureMovementError unitCollision = this.getPhysicsUnitCollision(t, physics);
                if (unitCollision != FurnitureMovementError.NONE) {
                    return unitCollision;
                }
            }
        }

        if (this.hasBlockingPhysicsFurni(occupiedTiles, item, physics)) {
            return FurnitureMovementError.CANT_STACK;
        }

        java.util.List<Pair<RoomTile, Set<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
        for (RoomTile t : occupiedTiles) {
            tileFurniList.add(Pair.create(t, this.getPhysicsItemsAt(t, item, physics)));

            HabboItem topItem = this.getTopPhysicsItemAt(t.x, t.y, item, physics);
            if (topItem != null && !topItem.getBaseItem().allowStack() && !t.getAllowStack()) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        if (!item.canStackAt(this.room, tileFurniList)) {
            return FurnitureMovementError.CANT_STACK;
        }

        return FurnitureMovementError.NONE;
    }

    /**
     * Places a floor furniture item at a position.
     */
    public FurnitureMovementError placeFloorFurniAt(HabboItem item, RoomTile tile, int rotation, Habbo owner) {
        return this.placement.placeFloorFurniture(item, tile, rotation, owner);
    }

    /**
     * Places a wall furniture item at a position.
     */
    public FurnitureMovementError placeWallFurniAt(HabboItem item, String wallPosition, Habbo owner) {
        return this.placement.placeWallFurniture(item, wallPosition, owner);
    }

    /**
     * Moves furniture to a new position with an explicit Z height.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, double z, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
        if (item == null || tile == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;

        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));

            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }

            pluginHelper = event.hasPluginHelper();
        }

        rotation %= 8;

        boolean magicTile = this.isStackPlacementBypassItem(item);

        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation
        );

        Set<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation()
        );

        if (!pluginHelper) {
            FurnitureMovementError fits = furnitureFitsAt(tile, item, rotation, checkForUnits);
            if (fits != FurnitureMovementError.NONE) {
                return fits;
            }
        }

        int oldRotation = item.getRotation();

        if (oldRotation != rotation) {
            item.setRotation(rotation);

            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event rotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(rotatedEvent);

                if (rotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }
        }

        // Height sanity checks
        if (z > Room.MAXIMUM_FURNI_HEIGHT) {
            return FurnitureMovementError.CANT_STACK;
        }

        // Prevent furni going under the floor
        if (z < layout.getHeightAtSquare(tile.x, tile.y)) {
            return FurnitureMovementError.CANT_STACK;
        }

        // Plugin height override (match your NEW behavior: base + updatedHeight)
        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, z));

            if (event.hasChangedHeight()) {
                z = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(z);

        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + (item.getZ() * 100));
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
        }

        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        // Update wired spatial index + invalidate cache
        if (oldLocation != null) {
            if (item instanceof InteractionWiredTrigger) {
                this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredEffect) {
                this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredCondition) {
                this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredExtra) {
                this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            }
        }

        // Update furniture
        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        // Update old & new tiles
        occupiedTiles.removeAll(oldOccupiedTiles);
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        // Update Habbos/Bots
        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }

        // Preserve your newer "place under" behavior if enabled
        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            Set<RoomTile> newOccupiedTiles = layout.getTilesAt(
                    tile,
                    item.getBaseItem().getWidth(),
                    item.getBaseItem().getLength(),
                    rotation
            );

            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception ignored) { }
                }
            }
        }

        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError moveFurniToWithPhysics(HabboItem item, RoomTile tile, int rotation, double z, Habbo actor, boolean sendUpdates, boolean checkForUnits, WiredMovementPhysics physics) {
        if (physics == null || !physics.isActive()) {
            return moveFurniTo(item, tile, rotation, z, actor, sendUpdates, checkForUnits);
        }

        if (item == null || tile == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;

        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));

            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }

            pluginHelper = event.hasPluginHelper();
        }

        rotation %= 8;

        boolean magicTile = this.isStackPlacementBypassItem(item);

        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation
        );

        Set<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation()
        );

        if (!pluginHelper) {
            FurnitureMovementError fits = furnitureFitsAtWithPhysics(tile, item, rotation, checkForUnits, physics);
            if (fits != FurnitureMovementError.NONE) {
                return fits;
            }
        }

        int oldRotation = item.getRotation();

        if (oldRotation != rotation) {
            item.setRotation(rotation);

            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event rotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(rotatedEvent);

                if (rotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }
        }

        if (z > Room.MAXIMUM_FURNI_HEIGHT) {
            return FurnitureMovementError.CANT_STACK;
        }

        if (z < layout.getHeightAtSquare(tile.x, tile.y)) {
            return FurnitureMovementError.CANT_STACK;
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, z));

            if (event.hasChangedHeight()) {
                z = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(z);

        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + (item.getZ() * 100));
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
        }

        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        if (oldLocation != null) {
            if (item instanceof InteractionWiredTrigger) {
                this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredEffect) {
                this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredCondition) {
                this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredExtra) {
                this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            }
        }

        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        occupiedTiles.removeAll(oldOccupiedTiles);
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }

        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            Set<RoomTile> newOccupiedTiles = layout.getTilesAt(
                    tile,
                    item.getBaseItem().getWidth(),
                    item.getBaseItem().getLength(),
                    rotation
            );

            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return FurnitureMovementError.NONE;
    }

    /**
     * Moves furniture to a new position.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor) {
        return moveFurniTo(item, tile, rotation, actor, true, true);
    }

    /**
     * Moves furniture to a new position with send updates option.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates) {
        return moveFurniTo(item, tile, rotation, actor, sendUpdates, true);
    }

    /**
     * Moves furniture to a new position with full options.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        boolean magicTile = this.isStackPlacementBypassItem(item);

        HabboItem stackHelper = this.findStackHeightHelperAt(tile, item);

        // Check if can be placed at new position
        Set<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);
        Set<RoomTile> newOccupiedTiles = layout.getTilesAt(tile,
                item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        HabboItem topItem = this.getTopItemAt(occupiedTiles, null);

        if (stackHelper == null && !pluginHelper) {
            if (oldLocation != tile) {
                for (RoomTile t : occupiedTiles) {
                    HabboItem tileTopItem = this.getTopItemAt(t.x, t.y);
                    if (!magicTile && ((tileTopItem != null && tileTopItem != item ? (
                            t.state.equals(RoomTileState.INVALID) || !t.getAllowStack()
                            || !tileTopItem.getBaseItem().allowStack())
                            : this.room.calculateTileState(t, item).equals(RoomTileState.INVALID)))) {
                        return FurnitureMovementError.CANT_STACK;
                    }

                    if (!Emulator.getConfig().getBoolean("wired.place.under", false) || (
                            Emulator.getConfig().getBoolean("wired.place.under", false) && !item.isWalkable()
                                    && !item.getBaseItem().allowSit() && !item.getBaseItem().allowLay())) {
                        if (checkForUnits) {
                            if (!magicTile && this.room.hasHabbosAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_HABBOS;
                            }
                            if (!magicTile && this.room.hasBotsAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_BOTS;
                            }
                            if (!magicTile && this.room.hasPetsAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_PETS;
                            }
                        }
                    }
                }
            }

            java.util.List<Pair<RoomTile, Set<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
            for (RoomTile t : occupiedTiles) {
                tileFurniList.add(Pair.create(t, this.getItemsAt(t)));
            }

            if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        Set<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), item.getRotation());

        int oldRotation = item.getRotation();

        if (oldRotation != rotation) {
            item.setRotation(rotation);
            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event furnitureRotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(furnitureRotatedEvent);

                if (furnitureRotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }

            if ((stackHelper == null && topItem != null && topItem != item && !topItem.getBaseItem()
                    .allowStack()) || (topItem != null && topItem != item
                    && topItem.getZ() + Item.getCurrentHeight(topItem) + Item.getCurrentHeight(item)
                    > Room.MAXIMUM_FURNI_HEIGHT)) {
                item.setRotation(oldRotation);
                return FurnitureMovementError.CANT_STACK;
            }
        }

        // Place at new position
        double height;

        if (stackHelper != null) {
            height = stackHelper.getZ();
        } else if (item instanceof InteractionStackWalkHelper) {
            height = this.resolveStackWalkHelperHeight(item, tile, occupiedTiles);
        } else if (item == topItem) {
            height = item.getZ();
        } else if (magicTile) {
            if (topItem == null) {
                height = this.room.getStackHeight(tile.x, tile.y, false, item);
                for (RoomTile til : occupiedTiles) {
                    double sHeight = this.room.getStackHeight(til.x, til.y, false, item);
                    if (sHeight > height) {
                        height = sHeight;
                    }
                }
            } else {
                height = topItem.getZ() + topItem.getBaseItem().getHeight();
            }
        } else {
            height = this.room.getStackHeight(tile.x, tile.y, false, item);
            for (RoomTile til : occupiedTiles) {
                double sHeight = this.room.getStackHeight(til.x, til.y, false, item);
                if (sHeight > height) {
                    height = sHeight;
                }
            }
        }

        boolean cantStack = false;
        boolean pluginHeight = false;

        if (height > Room.MAXIMUM_FURNI_HEIGHT) {
            cantStack = true;
        }
        if (height < layout.getHeightAtSquare(tile.x, tile.y)) {
            cantStack = true;
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
                pluginHeight = true;
            }
        }

        if (!pluginHeight && cantStack) {
            return FurnitureMovementError.CANT_STACK;
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(height);
        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        // Update wired spatial index and invalidate cache when wired items are moved
        if (item instanceof InteractionWiredTrigger) {
            this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredEffect) {
            this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredCondition) {
            this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredExtra) {
            this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        }

        // Update Furniture
        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        // Update old & new tiles
        occupiedTiles.removeAll(oldOccupiedTiles);
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        // Update Habbos at old position
        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }
        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError moveFurniToWithPhysics(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits, WiredMovementPhysics physics) {
        if (physics == null || !physics.isActive()) {
            return moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits);
        }

        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        boolean magicTile = this.isStackPlacementBypassItem(item);

        HabboItem stackHelper = this.findStackHeightHelperAt(tile, item);

        Set<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);
        Set<RoomTile> newOccupiedTiles = layout.getTilesAt(tile,
                item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        HabboItem topItem = this.getTopPhysicsItemAt(occupiedTiles, null, physics);

        if (stackHelper == null && !pluginHelper) {
            if (oldLocation != tile) {
                for (RoomTile t : occupiedTiles) {
                    HabboItem tileTopItem = this.getTopPhysicsItemAt(t.x, t.y, item, physics);
                    if (!magicTile && ((tileTopItem != null && tileTopItem != item ? (
                            t.state.equals(RoomTileState.INVALID) || !t.getAllowStack()
                            || !tileTopItem.getBaseItem().allowStack())
                            : this.room.calculateTileState(t, item).equals(RoomTileState.INVALID)))) {
                        return FurnitureMovementError.CANT_STACK;
                    }

                    if (shouldCheckUnits(item, checkForUnits)) {
                        FurnitureMovementError unitCollision = this.getPhysicsUnitCollision(t, physics);
                        if (!magicTile && unitCollision != FurnitureMovementError.NONE) {
                            return unitCollision;
                        }
                    }
                }
            }

            if (this.hasBlockingPhysicsFurni(occupiedTiles, item, physics)) {
                return FurnitureMovementError.CANT_STACK;
            }

            java.util.List<Pair<RoomTile, Set<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
            for (RoomTile t : occupiedTiles) {
                tileFurniList.add(Pair.create(t, this.getPhysicsItemsAt(t, item, physics)));
            }

            if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        Set<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), item.getRotation());

        int oldRotation = item.getRotation();

        if (oldRotation != rotation) {
            item.setRotation(rotation);
            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event furnitureRotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(furnitureRotatedEvent);

                if (furnitureRotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }

            if ((stackHelper == null && topItem != null && topItem != item && !topItem.getBaseItem()
                    .allowStack()) || (topItem != null && topItem != item
                    && topItem.getZ() + Item.getCurrentHeight(topItem) + Item.getCurrentHeight(item)
                    > Room.MAXIMUM_FURNI_HEIGHT)) {
                item.setRotation(oldRotation);
                return FurnitureMovementError.CANT_STACK;
            }
        }

        double height;

        if (stackHelper != null) {
            height = stackHelper.getZ();
        } else if (item instanceof InteractionStackWalkHelper) {
            height = this.resolveStackWalkHelperHeight(item, tile, occupiedTiles);
        } else if (item == topItem) {
            height = item.getZ();
        } else if (magicTile) {
            if (topItem == null) {
                height = this.getPhysicsStackHeight(tile.x, tile.y, item, physics);
                for (RoomTile til : occupiedTiles) {
                    double sHeight = this.getPhysicsStackHeight(til.x, til.y, item, physics);
                    if (sHeight > height) {
                        height = sHeight;
                    }
                }
            } else {
                height = topItem.getZ() + topItem.getBaseItem().getHeight();
            }
        } else {
            height = this.getPhysicsStackHeight(tile.x, tile.y, item, physics);
            for (RoomTile til : occupiedTiles) {
                double sHeight = this.getPhysicsStackHeight(til.x, til.y, item, physics);
                if (sHeight > height) {
                    height = sHeight;
                }
            }
        }

        boolean cantStack = false;
        boolean pluginHeight = false;

        if (height > Room.MAXIMUM_FURNI_HEIGHT) {
            cantStack = true;
        }
        if (height < layout.getHeightAtSquare(tile.x, tile.y)) {
            cantStack = true;
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
                pluginHeight = true;
            }
        }

        if (!pluginHeight && cantStack) {
            return FurnitureMovementError.CANT_STACK;
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(height);
        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        if (item instanceof InteractionWiredTrigger) {
            this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredEffect) {
            this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredCondition) {
            this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredExtra) {
            this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        }

        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        occupiedTiles.removeAll(oldOccupiedTiles);
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }
        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception e) {
                    }
                }
            }
        }
        return FurnitureMovementError.NONE;
    }

    /**
     * Slides furniture to a new position.
     */
    public FurnitureMovementError slideFurniTo(HabboItem item, RoomTile tile, int rotation) {
        boolean magicTile = this.isStackPlacementBypassItem(item);

        RoomLayout layout = this.room.getLayout();

        // Check if can be placed at new position
        Set<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);

        java.util.List<Pair<RoomTile, Set<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
        for (RoomTile t : occupiedTiles) {
            tileFurniList.add(Pair.create(t, this.getItemsAt(t)));
        }

        if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
            return FurnitureMovementError.CANT_STACK;
        }

        item.setRotation(rotation);

        // Place at new position
        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }
        double offset = this.room.getStackHeight(tile.x, tile.y, false, item) - item.getZ();
        this.room.sendComposer(new FloorItemOnRollerComposer(item, null, tile, offset, this.room).compose());

        // Update Habbos at old position
        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y);
            this.room.updateBotsAt(t.x, t.y);
        }
        return FurnitureMovementError.NONE;
    }

    private boolean shouldCheckUnits(HabboItem item, boolean checkForUnits) {
        if (!checkForUnits) {
            return false;
        }

        if (!Emulator.getConfig().getBoolean("wired.place.under", false)) {
            return true;
        }

        return !item.isWalkable()
                && !item.getBaseItem().allowSit()
                && !item.getBaseItem().allowLay();
    }

    private FurnitureMovementError getPhysicsUnitCollision(RoomTile tile, WiredMovementPhysics physics) {
        for (RoomUnit roomUnit : this.room.getRoomUnits(tile)) {
            if (roomUnit == null) {
                continue;
            }

            switch (roomUnit.getRoomUnitType()) {
                case BOT:
                    return FurnitureMovementError.TILE_HAS_BOTS;
                case PET:
                    return FurnitureMovementError.TILE_HAS_PETS;
                case USER:
                    if (physics == null || !physics.shouldIgnoreUser(roomUnit)) {
                        return FurnitureMovementError.TILE_HAS_HABBOS;
                    }
                    break;
                default:
                    return FurnitureMovementError.TILE_HAS_HABBOS;
            }
        }

        return FurnitureMovementError.NONE;
    }

    private boolean hasBlockingPhysicsFurni(Set<RoomTile> occupiedTiles, HabboItem exclude, WiredMovementPhysics physics) {
        if (physics == null || !physics.hasBlockingFurni()) {
            return false;
        }

        for (RoomTile tile : occupiedTiles) {
            for (HabboItem item : this.getItemsAt(tile)) {
                if (item == null || item == exclude) {
                    continue;
                }

                if (physics.isBlockingFurni(item)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Set<HabboItem> getPhysicsItemsAt(RoomTile tile, HabboItem exclude, WiredMovementPhysics physics) {
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : this.getItemsAt(tile)) {
            if (item == null || item == exclude) {
                continue;
            }

            if (physics != null && physics.shouldIgnoreFurni(item)) {
                continue;
            }

            items.add(item);
        }

        return items;
    }

    private HabboItem getTopPhysicsItemAt(int x, int y, HabboItem exclude, WiredMovementPhysics physics) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile == null) {
            return null;
        }

        HabboItem highestItem = null;

        for (HabboItem item : this.getPhysicsItemsAt(tile, exclude, physics)) {
            if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                    > item.getZ() + Item.getCurrentHeight(item)) {
                continue;
            }

            highestItem = item;
        }

        return highestItem;
    }

    private HabboItem getTopPhysicsItemAt(Set<RoomTile> tiles, HabboItem exclude, WiredMovementPhysics physics) {
        HabboItem highestItem = null;

        for (RoomTile tile : tiles) {
            if (tile == null) {
                continue;
            }

            HabboItem topItem = this.getTopPhysicsItemAt(tile.x, tile.y, exclude, physics);
            if (topItem == null) {
                continue;
            }

            if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                    > topItem.getZ() + Item.getCurrentHeight(topItem)) {
                continue;
            }

            highestItem = topItem;
        }

        return highestItem;
    }

    private double getPhysicsStackHeight(short x, short y, HabboItem exclude, WiredMovementPhysics physics) {
        RoomLayout layout = this.room.getLayout();

        if (x < 0 || y < 0 || layout == null) {
            return 0.0;
        }

        double height = layout.getHeightAtSquare(x, y);

        RoomTile tile = layout.getTile(x, y);
        if (tile == null) {
            return height;
        }

        double helperHeight = Double.NEGATIVE_INFINITY;
        for (HabboItem item : this.getPhysicsItemsAt(tile, exclude, physics)) {
            if (item instanceof InteractionStackHelper || item instanceof InteractionTileWalkMagic || item instanceof InteractionStackWalkHelper) {
                helperHeight = Math.max(helperHeight, item.getZ());
            }
        }

        if (helperHeight != Double.NEGATIVE_INFINITY) {
            return helperHeight;
        }

        HabboItem topItem = this.getTopPhysicsItemAt(x, y, exclude, physics);
        if (topItem != null) {
            return topItem.getZ() + (topItem.getBaseItem().allowSit() ? 0 : Item.getCurrentHeight(topItem));
        }

        return height;
    }
}
