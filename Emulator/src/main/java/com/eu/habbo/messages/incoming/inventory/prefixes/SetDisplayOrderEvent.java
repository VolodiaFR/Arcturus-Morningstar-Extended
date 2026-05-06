package com.eu.habbo.messages.incoming.inventory.prefixes;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.inventory.UserVisualSettingsComponent;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class SetDisplayOrderEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getInventory() == null || habbo.getInventory().getUserVisualSettingsComponent() == null) {
            return;
        }

        String displayOrder = UserVisualSettingsComponent.sanitizeDisplayOrder(this.packet.readString());
        habbo.getInventory().getUserVisualSettingsComponent().setDisplayOrder(displayOrder);
        this.client.sendResponse(new UserNickIconsComposer(habbo));

        if (habbo.getHabboInfo().getCurrentRoom() != null) {
            habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
        }
    }
}
