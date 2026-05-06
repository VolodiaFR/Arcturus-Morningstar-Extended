package com.eu.habbo.messages.incoming.inventory.nickicons;

import com.eu.habbo.habbohotel.users.UserNickIcon;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class SetActiveNickIconEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int nickIconId = this.packet.readInt();

        if (nickIconId == 0) {
            this.client.getHabbo().getInventory().getNickIconsComponent().deactivateAll();
            this.client.sendResponse(new UserNickIconsComposer(this.client.getHabbo()));
            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
            }
            return;
        }

        UserNickIcon nickIcon = this.client.getHabbo().getInventory().getNickIconsComponent().getNickIcon(nickIconId);

        if (nickIcon == null) {
            return;
        }

        this.client.getHabbo().getInventory().getNickIconsComponent().setActive(nickIconId);
        this.client.sendResponse(new UserNickIconsComposer(this.client.getHabbo()));
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
        }
    }
}
