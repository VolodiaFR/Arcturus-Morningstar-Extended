package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.HorsePet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.threading.runnables.HabboItemNewState;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class InteractionObstacle extends HabboItem implements ICycleable {

    private Set<RoomTile> middleTiles;
    private boolean middleTilesCalculated;

    public InteractionObstacle(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
        this.middleTiles = new HashSet<>();
    }

    public InteractionObstacle(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
        this.middleTiles = new HashSet<>();
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        Pet pet = room.getPet(roomUnit);

        if (pet instanceof HorsePet && ((HorsePet) pet).getRider() != null) {
            Habbo rider = ((HorsePet) pet).getRider();
            if (pet.getTask() != null && pet.getTask().equals(PetTasks.RIDE)) {
                if (pet.getRoomUnit().hasStatus(RoomUnitStatus.JUMP)) {
                    pet.getRoomUnit().removeStatus(RoomUnitStatus.JUMP);
                    Emulator.getThreading().run(new HabboItemNewState(this, room, "0"), 2000);
                } else {
                    int state = 0;
                    for (int i = 0; i < 2; i++) {
                        state = Emulator.getRandom().nextInt(4) + 1;

                        if (state == 4)
                            break;
                    }

                    this.setExtradata(state + "");
                    pet.getRoomUnit().setStatus(RoomUnitStatus.JUMP, "0");

                    AchievementManager.progressAchievement(rider, Emulator.getGameEnvironment().getAchievementManager().getAchievement("HorseConsecutiveJumpsCount"));
                    AchievementManager.progressAchievement(rider, Emulator.getGameEnvironment().getAchievementManager().getAchievement("HorseJumping"));
                }

                room.updateItemState(this);
            }
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null) {
            Pet pet = room.getPet(roomUnit);

            if (pet instanceof HorsePet && ((HorsePet) pet).getRider() != null) {
                if (roomUnit.getBodyRotation().getValue() % 2 == 0) {
                    if (this.getRotation() == 2) {
                        if (roomUnit.getBodyRotation().equals(RoomUserRotation.WEST)) {
                            ((HorsePet) pet).getRider().getRoomUnit().setGoalLocation(room.getLayout().getTile((short) (roomUnit.getX() - 3), roomUnit.getY()));
                        } else if (roomUnit.getBodyRotation().equals(RoomUserRotation.EAST)) {
                            ((HorsePet) pet).getRider().getRoomUnit().setGoalLocation(room.getLayout().getTile((short) (roomUnit.getX() + 3), roomUnit.getY()));
                        }
                    } else if (this.getRotation() == 4) {
                        if (roomUnit.getBodyRotation().equals(RoomUserRotation.NORTH)) {
                            ((HorsePet) pet).getRider().getRoomUnit().setGoalLocation(room.getLayout().getTile(roomUnit.getX(), (short) (roomUnit.getY() - 3)));
                        } else if (roomUnit.getBodyRotation().equals(RoomUserRotation.SOUTH)) {
                            ((HorsePet) pet).getRider().getRoomUnit().setGoalLocation(room.getLayout().getTile(roomUnit.getX(), (short) (roomUnit.getY() + 3)));
                        }
                    }
                }
            }
        } else if (habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() instanceof HorsePet) {
            this.setupRiderJump(habbo, (HorsePet) habbo.getHabboInfo().getRiding(), roomUnit, room);
        }
    }

    private void setupRiderJump(Habbo rider, HorsePet horse, RoomUnit riderUnit, Room room) {
        RoomUnit horseUnit = horse.getRoomUnit();

        if (horseUnit == null || horseUnit.hasStatus(RoomUnitStatus.JUMP)) {
            return;
        }

        if (riderUnit.getBodyRotation().getValue() % 2 != 0) {
            return;
        }

        Deque<RoomTile> path = riderUnit.getPath();

        if (path == null || path.isEmpty()) {
            return;
        }

        Deque<RoomTile> jumpPath = new ArrayDeque<>();
        boolean crossesBar = false;

        for (RoomTile tile : path) {
            if (this.isMiddleTile(tile)) {
                crossesBar = true;
                continue;
            }

            jumpPath.add(tile);
        }

        if (!crossesBar || jumpPath.isEmpty()) {
            return;
        }

        riderUnit.setPath(jumpPath);
        Emulator.getThreading().run(() -> this.jumpRider(rider, horse, room), 250);
    }

    private void jumpRider(Habbo rider, HorsePet horse, Room room) {
        if (rider == null || horse == null || room == null
                || this.getRoomId() != room.getId()
                || !rider.isOnline()
                || rider.getHabboInfo() == null
                || rider.getHabboInfo().getCurrentRoom() != room
                || rider.getHabboInfo().getRiding() != horse) {
            return;
        }

        RoomUnit horseUnit = horse.getRoomUnit();

        if (horseUnit == null || horseUnit.hasStatus(RoomUnitStatus.JUMP)) {
            return;
        }

        int state = 0;
        for (int i = 0; i < 2; i++) {
            state = Emulator.getRandom().nextInt(4) + 1;

            if (state == 4)
                break;
        }
        this.setExtradata(state + "");
        horseUnit.setStatus(RoomUnitStatus.JUMP, "0");
        AchievementManager.progressAchievement(rider, Emulator.getGameEnvironment().getAchievementManager().getAchievement("HorseConsecutiveJumpsCount"));
        AchievementManager.progressAchievement(rider, Emulator.getGameEnvironment().getAchievementManager().getAchievement("HorseJumping"));
        room.updateItemState(this);
        Emulator.getThreading().run(new HabboItemNewState(this, room, "0"), 2000);
        Emulator.getThreading().run(() -> {
            if (horseUnit.hasStatus(RoomUnitStatus.JUMP)) {
                horseUnit.removeStatus(RoomUnitStatus.JUMP);
                horseUnit.statusUpdate(true);
                room.sendComposer(new RoomUserStatusComposer(horseUnit).compose());
            }
        }, 2000);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null) {
            Pet pet = room.getPet(roomUnit);

            if (pet instanceof HorsePet && ((HorsePet) pet).getRider() != null) {
                pet.getRoomUnit().removeStatus(RoomUnitStatus.JUMP);
            }
        } else if (habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() instanceof HorsePet) {
            RoomTile next = (objects != null && objects.length > 1 && objects[1] instanceof RoomTile)
                    ? (RoomTile) objects[1] : null;

            boolean stillOnObstacle = false;
            if (next != null) {
                for (HabboItem item : room.getItemsAt(next)) {
                    if (item == this) {
                        stillOnObstacle = true;
                        break;
                    }
                }
            }

            if (!stillOnObstacle) {
                RoomUnit horseUnit = ((HorsePet) habbo.getHabboInfo().getRiding()).getRoomUnit();

                if (horseUnit != null) {
                    horseUnit.removeStatus(RoomUnitStatus.JUMP);
                }
            }
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.calculateMiddleTiles(room);
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        middleTiles.clear();
        this.middleTilesCalculated = false;
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.calculateMiddleTiles(room);
    }

    private void calculateMiddleTiles(Room room) {
        middleTiles.clear();

        if(this.getRotation() == 2) {
            middleTiles.add(room.getLayout().getTile((short)(this.getX() + 1), this.getY()));
            middleTiles.add(room.getLayout().getTile((short)(this.getX() + 1), (short)(this.getY() + 1)));
        }
        else if(this.getRotation() == 4) {
            middleTiles.add(room.getLayout().getTile(this.getX(), (short)(this.getY() + 1)));
            middleTiles.add(room.getLayout().getTile((short)(this.getX() + 1), (short)(this.getY() + 1)));
        }

        middleTiles.remove(null);
        this.middleTilesCalculated = true;
    }

    @Override
    public RoomTileState getOverrideTileState(RoomTile tile, Room room) {
        if(this.isMiddleTile(tile))
            return RoomTileState.BLOCKED;

        return null;
    }

    @Override
    public boolean canOverrideTile(RoomUnit roomUnit, Room room, RoomTile tile) {
        if(!this.isMiddleTile(tile))
            return false;

        Habbo habbo = room.getHabbo(roomUnit);
        if(habbo != null)
            return habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() instanceof HorsePet;

        Pet pet = room.getPet(roomUnit);
        return pet instanceof HorsePet && ((HorsePet) pet).getRider() != null;
    }

    private boolean isMiddleTile(RoomTile tile) {
        if(tile == null)
            return false;

        for(RoomTile middle : this.middleTiles) {
            if(middle != null && middle.x == tile.x && middle.y == tile.y)
                return true;
        }

        return false;
    }

    @Override
    public void cycle(Room room) {
        if(!this.middleTilesCalculated) {
            this.calculateMiddleTiles(room);
        }

        if(this.middleTiles.isEmpty()) {
            return;
        }

        for(RoomTile tile : this.middleTiles) {
            if(tile == null || !tile.hasUnits()) {
                continue;
            }

            for(RoomUnit unit : tile.getUnits()) {
                if(unit.getPath().size() == 0 && !unit.hasStatus(RoomUnitStatus.MOVE)) {
                    RoomUserRotation opposite = unit.getBodyRotation().getOpposite();

                    if(unit.getBodyRotation().getValue() != this.getRotation() && (opposite == null || opposite.getValue() != this.getRotation()))
                        continue;

                    RoomTile tileInfront = room.getLayout().getTileInFront(unit.getCurrentLocation(), unit.getBodyRotation().getValue());
                    if(tileInfront != null && tileInfront.state != RoomTileState.INVALID && tileInfront.state != RoomTileState.BLOCKED && room.getRoomUnitsAt(tileInfront).size() == 0) {
                        unit.setGoalLocation(tileInfront);
                    }
                    else if(opposite != null) {
                        RoomTile tileBehind = room.getLayout().getTileInFront(unit.getCurrentLocation(), opposite.getValue());
                        if(tileBehind != null && tileBehind.state != RoomTileState.INVALID && tileBehind.state != RoomTileState.BLOCKED && room.getRoomUnitsAt(tileBehind).size() == 0) {
                            unit.setGoalLocation(tileBehind);
                        }
                    }
                }
            }
        }
    }
}
