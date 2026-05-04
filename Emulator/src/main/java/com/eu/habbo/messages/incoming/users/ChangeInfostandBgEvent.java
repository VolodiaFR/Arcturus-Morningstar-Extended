package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class ChangeInfostandBgEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int backgroundImage = this.packet.readInt();
        int backgroundStand = this.packet.readInt();
        int backgroundOverlay = this.packet.readInt();
        int backgroundCard = this.packet.bytesAvailable() >= 4 ? this.packet.readInt() : 0;

        this.client.getHabbo().getHabboInfo().setInfostandBg(backgroundImage);
        this.client.getHabbo().getHabboInfo().setInfostandStand(backgroundStand);
        this.client.getHabbo().getHabboInfo().setInfostandOverlay(backgroundOverlay);
        this.client.getHabbo().getHabboInfo().setInfostandCardBg(backgroundCard);
        this.client.getHabbo().getHabboInfo().run();

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
        } else {
            this.client.sendResponse(new RoomUserDataComposer(this.client.getHabbo()));
        }
    }
}